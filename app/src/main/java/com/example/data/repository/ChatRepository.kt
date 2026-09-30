package com.example.data.repository

import android.net.Uri
import com.example.data.model.*
import com.example.data.network.LiquidApi
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Firebase snapshots are read-only message truth; the authenticated API serializes mutations. */
class ChatRepository(private val scope: CoroutineScope = CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)) {
  private val auth = FirebaseAuth.getInstance()
  private val db = FirebaseFirestore.getInstance()
  private val prefs get() = LiquidApi.context.getSharedPreferences("liquid-private",0)
  private val uid get() = auth.currentUser?.uid.orEmpty()
  private val listeners = mutableListOf<ListenerRegistration>()
  private val messageListeners = mutableMapOf<String,ListenerRegistration>()
  private val presenceListeners = mutableMapOf<String,ListenerRegistration>()
  private var heartbeat: Job? = null
  private var outboxJob: Job? = null
  private var resumed = false
  private val limits = mutableMapOf<String,Long>()

  private val _currentUser=MutableStateFlow(User())
  val currentUser=_currentUser.asStateFlow()
  private val _users=MutableStateFlow<List<User>>(emptyList());val users=_users.asStateFlow()
  private val _conversations=MutableStateFlow<List<Conversation>>(emptyList());val conversations=_conversations.asStateFlow()
  private val _messages=MutableStateFlow<Map<String,List<Message>>>(emptyMap());val messages=_messages.asStateFlow()
  private val _appearance=MutableStateFlow(AppearanceSettings());val appearance=_appearance.asStateFlow()
  private val _privacy=MutableStateFlow(PrivacySettings());val privacy=_privacy.asStateFlow()
  private val _notifications=MutableStateFlow(NotificationSettings());val notifications=_notifications.asStateFlow()
  private val _blockedUserIds=MutableStateFlow<Set<String>>(emptySet());val blockedUserIds=_blockedUserIds.asStateFlow()
  private val _searchHistory=MutableStateFlow<List<String>>(emptyList());val searchHistory=_searchHistory.asStateFlow()
  private val _error=MutableStateFlow<String?>(null);val error=_error.asStateFlow()
  private val _loading=MutableStateFlow(true);val loading=_loading.asStateFlow()
  private val _upload=MutableStateFlow<Float?>(null);val upload=_upload.asStateFlow()
  private var appearanceJob: Job?=null
  private var uploadJob: Job?=null
  private var retryUpload: (() -> Unit)?=null

  private val failed = mutableSetOf<String>()
  private val receipts=mutableSetOf<String>()
  init { if(isUserLoggedIn()) startSync() else _loading.value=false }
  fun clearError(){_error.value=null}
  private fun runAction(block:suspend()->Unit){scope.launch{runCatching {block()}.onFailure {if(it !is CancellationException)_error.value=it.message?:"Operation failed"}}}
  fun isUserLoggedIn()=auth.currentUser!=null
  suspend fun registerWithEmail(email:String,pass:String,fullName:String,username:String,phoneNumber:String):Result<User> = runCatching {
    auth.createUserWithEmailAndPassword(email.trim(),pass).await()
    LiquidApi.call("profile",mapOf("displayName" to fullName,"username" to username,"phoneNumber" to phoneNumber))
    auth.currentUser?.sendEmailVerification()?.await();startSync();_currentUser.value
  }
  suspend fun signInWithEmail(email:String,pass:String):Result<User> = runCatching {
    auth.signInWithEmailAndPassword(email.trim(),pass).await()
    LiquidApi.call("profile");startSync();_currentUser.value
  }
  fun resetPassword(email:String)=runAction {auth.sendPasswordResetEmail(email.trim()).await();_error.value="Password reset email sent"}
  fun verifyEmail()=runAction {auth.currentUser?.sendEmailVerification()?.await();_error.value="Verification email sent"}
  fun logout(){
    setPresence(false)
    val tokenKey=prefs.getString("deviceId","").orEmpty()
    if(uid.isNotBlank()&&tokenKey.isNotBlank())db.document("users/$uid").update("tokens.$tokenKey",FieldValue.delete())
    stopSync();appearanceJob?.cancel();uploadJob?.cancel();retryUpload=null;LiquidApi.clear();prefs.edit().clear().apply();auth.signOut()
    _currentUser.value=User();_users.value=emptyList();_conversations.value=emptyList();_messages.value=emptyMap();_blockedUserIds.value=emptySet();_appearance.value=AppearanceSettings();_privacy.value=PrivacySettings();_searchHistory.value=emptyList()
    // Firestore cache is UID/rules scoped; app outbox and media cache were cleared above.
  }
  fun close(){setPresence(false);stopSync();scope.cancel()}
  private fun stopSync(){receipts.clear();listeners.forEach{it.remove()};listeners.clear();messageListeners.values.forEach{it.remove()};messageListeners.clear();presenceListeners.values.forEach{it.remove()};presenceListeners.clear();heartbeat?.cancel();outboxJob?.cancel()}
  private fun startSync(){
    stopSync();if(uid.isBlank())return;val account=uid;_loading.value=true
    _currentUser.value=User(uid=uid,email=auth.currentUser?.email.orEmpty())
    runAction {LiquidApi.call("profile")}
    val device=prefs.getString("deviceId",null)?:UUID.randomUUID().toString().replace("-","").also {prefs.edit().putString("deviceId",it).apply()}
    FirebaseMessaging.getInstance().token.addOnSuccessListener {if(uid==account)db.document("users/$uid").update("tokens.$device",it)}
    listeners+=db.document("users/$account").addSnapshotListener {s,e->
      if(e!=null){_error.value=e.message;return@addSnapshotListener};if(s==null||!s.exists())return@addSnapshotListener
      _currentUser.value=toUser(s)
      _blockedUserIds.value=(s.get("blockedUserIds") as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty()
      val a=s.get("appearance") as? Map<*,*>
      if(a!=null)_appearance.value=AppearanceSettings(isDarkMode=a["isDarkMode"] as? Boolean?:false,glassIntensity=(a["glassIntensity"] as? Number)?.toFloat()?:0.7f,blurAlpha=(a["blurAlpha"] as? Number)?.toFloat()?:0.7f,cornerRadiusDp=(a["cornerRadiusDp"] as? Number)?.toFloat()?:26f,borderStrength=(a["borderStrength"] as? Number)?.toFloat()?:0.65f,isReducedMotion=a["isReducedMotion"] as? Boolean?:false)
      val p=s.get("privacy") as? Map<*,*>
      if(p!=null)_privacy.value=PrivacySettings(p["lastSeenVisibility"] as? String?:"Everyone",p["onlineVisibility"] as? String?:"Everyone",p["profilePhotoVisibility"] as? String?:"Everyone",p["readReceipts"] as? Boolean?:true)
      val n=s.get("notifications") as? Map<*,*>
      if(n!=null)_notifications.value=NotificationSettings(n["messages"] as? Boolean?:true,n["vibration"] as? Boolean?:true)
      prefs.edit().putBoolean("notifications",_notifications.value.messages).apply()
    }
    listeners+=db.collection("directory").limit(200).addSnapshotListener {s,e->
      if(e!=null){_error.value=e.message;return@addSnapshotListener}
      if(s!=null){_users.value=s.documents.map(::toUser).filter{it.uid!=account};refreshUsers()}
    }
    listeners+=db.collection("conversations").whereArrayContains("participantIds",account).addSnapshotListener {s,e->
      _loading.value=false
      if(e!=null){_error.value=e.message;return@addSnapshotListener}
      if(s!=null){
        if(s.isEmpty&&s.metadata.isFromCache&&_conversations.value.isNotEmpty())return@addSnapshotListener
        _conversations.value=s.documents.mapNotNull(::toConversation).sortedByDescending{it.lastMessageTime}
        val active=_conversations.value.map{it.id}.toSet();messageListeners.keys.filter{it !in active}.toList().forEach{messageListeners.remove(it)?.remove();presenceListeners.remove(it)?.remove()}
        _conversations.value.forEach{c->observeConversation(c.id);observePresence(c.id)}
      }
    }
    restoreOutbox()
    outboxJob=scope.launch {while(isActive&&uid==account){flushOutbox();delay(15000)}}
    heartbeat=scope.launch {var ticks=0;while(isActive&&uid==account){if(resumed&&ticks++%25==0)writePresence(true);refreshUsers();refreshTyping();expireMessages();delay(1000)}}
  }
  private fun toUser(s:DocumentSnapshot):User {
    val own=s.id==uid
    val pubPhoto=s.getString("photoUrl").orEmpty().takeUnless{it.contains("images.unsplash.com")}.orEmpty()
    return User(uid=s.id,displayName=s.getString("displayName")?:"Contact",username=s.getString("username").orEmpty(),email=if(own)s.getString("email").orEmpty() else "",phoneNumber=if(own)s.getString("phoneNumber").orEmpty() else "",photoUrl=pubPhoto,bio=s.getString("bio").orEmpty(),isOnline=s.getBoolean("isOnline")==true&&System.currentTimeMillis()-(s.getLong("heartbeatAt")?:0)<45000,lastSeen=s.getLong("lastSeen")?:0,lastActiveAt=s.getLong("heartbeatAt")?:0,onlineVisible=s.getBoolean("onlineVisible")!=false,lastSeenVisible=s.getBoolean("lastSeenVisible")!=false)
  }
  private fun toConversation(s:DocumentSnapshot):Conversation? {
    val ids=(s.get("participantIds") as? List<*>)?.filterIsInstance<String>().orEmpty();if(ids.size!=2)return null
    if((s.get("deletedFor") as? List<*>)?.contains(uid)==true)return null
    val other=ids.firstOrNull{it!=uid}?:return null
    val user=_users.value.find{it.uid==other}?:User(uid=other,displayName="Contact")
    fun flag(name:String)=(s.get(name) as? List<*>)?.contains(uid)==true
    return Conversation(id=s.id,participantIds=ids,otherUser=user,lastMessageText=s.getString("lastMessageText").orEmpty(),lastMessageTime=s.getLong("lastMessageTime")?:0,lastMessageSenderId=s.getString("lastMessageSenderId").orEmpty(),unreadCount=((s.get("unreadCounts") as? Map<*,*>)?.get(uid) as? Number)?.toInt()?:0,isPinned=flag("favoriteFor"),isMuted=flag("mutedFor"),isArchived=flag("archivedFor"),isOnline=user.isOnline,disappearingSeconds=s.getLong("disappearingSeconds")?:0,wallpaperIndex=prefs.getInt("wallpaper:$uid:${s.id}",0))
  }
  private fun refreshUsers(){_users.update{us->us.map{u->u.copy(isOnline=u.isOnline&&System.currentTimeMillis()-u.lastActiveAt<45000)}};_conversations.update{cs->cs.map {c->val u=_users.value.find{it.uid==c.otherUser.uid}?:c.otherUser;val online=u.isOnline&&System.currentTimeMillis()-u.lastActiveAt<45000;c.copy(otherUser=u.copy(isOnline=online),isOnline=online)}}}
  private fun observePresence(cid:String){if(presenceListeners.containsKey(cid))return
    presenceListeners[cid]=db.collection("conversations/$cid/typing").addSnapshotListener{s,_->
      val other=_conversations.value.find{it.id==cid}?.otherUser?.uid
      val until=s?.documents?.firstOrNull{it.id==other}?.getLong("until")?:0L
      _conversations.update{cs->cs.map{if(it.id==cid)it.copy(typingUntil=until,isTyping=until>System.currentTimeMillis())else it}}
    }
  }
  private fun refreshTyping(){_conversations.update{cs->cs.map{it.copy(isTyping=it.typingUntil>System.currentTimeMillis())}}}
  fun observeConversation(cid:String){if(messageListeners.containsKey(cid))return
    val limit=limits.getOrPut(cid){100}
    messageListeners[cid]=db.collection("conversations/$cid/messages").orderBy("createdAt",Query.Direction.DESCENDING).limit(limit).addSnapshotListener{s,e->
      if(e!=null){_error.value=e.message;return@addSnapshotListener}
      if(s!=null){val list=s.documents.mapNotNull{toMessage(cid,it)}.sortedBy{it.createdAt};val pending=_messages.value[cid].orEmpty().filter{it.status==MessageDeliveryStatus.SENDING||it.status==MessageDeliveryStatus.FAILED};_messages.update{it+(cid to (list+pending.filter{m->list.none{it.id==m.id}}).sortedBy{m->m.createdAt})}
        list.filter{it.senderId!=uid&&it.status==MessageDeliveryStatus.SENT}.forEach{receipt(cid,it.id,"DELIVERED")}
      }
    }
  }
  fun loadOlder(cid:String){limits[cid]=(limits[cid]?:100)+100;messageListeners.remove(cid)?.remove();observeConversation(cid)}
  private fun toMessage(cid:String,s:DocumentSnapshot):Message? {
    val expires=s.getLong("expiresAt");if(expires!=null&&expires<=System.currentTimeMillis())return null
    if(prefs.getBoolean("hidden:$uid:${s.id}",false))return null
    return Message(id=s.id,conversationId=cid,senderId=s.getString("senderId").orEmpty(),senderName=s.getString("senderName").orEmpty(),text=s.getString("text").orEmpty(),type=runCatching{MessageType.valueOf(s.getString("type")?:"TEXT")}.getOrDefault(MessageType.TEXT),mediaUrl=s.getString("mediaUrl").orEmpty(),voiceDurationSeconds=s.getLong("voiceDurationSeconds")?.toInt()?:0,createdAt=s.getLong("createdAt")?:0,status=runCatching{MessageDeliveryStatus.valueOf(s.getString("status")?:"SENT")}.getOrDefault(MessageDeliveryStatus.SENT),replyToId=s.getString("replyToId"),replyToText=s.getString("replyToText"),replyToSender=s.getString("replyToSender"),isEdited=s.getBoolean("isEdited")==true,isDeleted=s.getBoolean("isDeleted")==true,isPinned=s.getBoolean("isPinned")==true,isStarred=prefs.getBoolean("star:$uid:${s.id}",false),expiresAt=expires,reactions=(s.get("reactions") as? List<*>)?.mapNotNull{val r=it as? Map<*,*>?:return@mapNotNull null;MessageReaction(r["emoji"] as? String?:"",(r["userIds"] as? List<*>)?.filterIsInstance<String>().orEmpty())}.orEmpty())
  }
  private fun expireMessages(){val now=System.currentTimeMillis();_messages.update{map->map.mapValues{(_,ms)->ms.filter{it.expiresAt==null||it.expiresAt>now}}}}
  fun getOrCreateConversationId(otherUid:String):String {
    val cid=listOf(uid,otherUid).sorted().joinToString("_")
    if(_conversations.value.none{it.id==cid})_conversations.update{it+Conversation(id=cid,participantIds=listOf(uid,otherUid),otherUser=_users.value.find{it.uid==otherUid}?:User(uid=otherUid,displayName="Contact"))}
    runAction{LiquidApi.call("conversation",mapOf("otherUid" to otherUid));observeConversation(cid);observePresence(cid)};return cid
  }
  fun sendMessage(conversationId:String,text:String,type:MessageType=MessageType.TEXT,mediaUrl:String="",replyToId:String?=null,replyToText:String?=null,replyToSender:String?=null,voiceDurationSeconds:Int=0){
    if(text.isBlank()&&mediaUrl.isBlank())return
    val m=Message(id=UUID.randomUUID().toString(),conversationId=conversationId,senderId=uid,senderName=_currentUser.value.displayName,text=text,type=type,mediaUrl=mediaUrl,replyToId=replyToId,replyToText=replyToText,replyToSender=replyToSender,voiceDurationSeconds=voiceDurationSeconds,status=MessageDeliveryStatus.SENDING)
    _messages.update{it+(conversationId to (it[conversationId].orEmpty()+m))};persist(m);scope.launch{flushOutbox()};setTyping(conversationId,false)
  }
  private fun json(m:Message)=JSONObject(mapOf("otherUid" to _conversations.value.find{it.id==m.conversationId}?.otherUser?.uid,"id" to m.id,"conversationId" to m.conversationId,"senderId" to m.senderId,"text" to m.text,"type" to m.type.name,"mediaUrl" to m.mediaUrl,"voiceDurationSeconds" to m.voiceDurationSeconds,"replyToId" to m.replyToId,"replyToText" to m.replyToText,"replyToSender" to m.replyToSender,"createdAt" to m.createdAt))
  private fun persist(m:Message){prefs.edit().putString("outbox:$uid:${m.id}",json(m).toString()).apply()}
  private fun restoreOutbox(){prefs.all.filterKeys{it.startsWith("outbox:$uid:")}.values.forEach{raw->runCatching{val j=JSONObject(raw as String);val m=Message(id=j.getString("id"),conversationId=j.getString("conversationId"),senderId=uid,text=j.getString("text"),type=MessageType.valueOf(j.getString("type")),mediaUrl=j.optString("mediaUrl"),voiceDurationSeconds=j.optInt("voiceDurationSeconds"),createdAt=j.optLong("createdAt"),status=MessageDeliveryStatus.SENDING);_messages.update{it+(m.conversationId to (it[m.conversationId].orEmpty().filterNot{v->v.id==m.id}+m))}}}}
  private val sending=kotlinx.coroutines.sync.Mutex()
  private suspend fun flushOutbox(){if(uid.isBlank()||!sending.tryLock())return;val account=uid
    try {for((key,raw)in prefs.all.filterKeys{it.startsWith("outbox:$account:")}){if(uid!=account)break
      val j=JSONObject(raw as String);val id=j.getString("id");if(id in failed)continue
      val cid=j.getString("conversationId");val data=j.keys().asSequence().associateWith{j.opt(it).takeUnless{v->v==JSONObject.NULL}}
      try {LiquidApi.call("send",data);prefs.edit().remove(key).apply();updateLocal(cid,id){it.copy(status=MessageDeliveryStatus.SENT)}}catch(e:Exception){if(e is CancellationException)throw e
        // Transport errors remain queued; validation/configuration failures require explicit retry.
        if(e !is java.io.IOException){failed+=id;updateLocal(cid,id){it.copy(status=MessageDeliveryStatus.FAILED)};_error.value=e.message}
      }
    }}finally{sending.unlock()}
  }
  fun retryMessage(cid:String,id:String){failed-=id;updateLocal(cid,id){it.copy(status=MessageDeliveryStatus.SENDING)};scope.launch{flushOutbox()}}
  private fun updateLocal(cid:String,id:String,f:(Message)->Message){_messages.update{map->map+(cid to map[cid].orEmpty().map{if(it.id==id)f(it)else it})}}
  fun uploadChatMedia(cid:String,uri:Uri,type:MessageType,onResult:(Result<String>)->Unit={}){
    retryUpload={uploadChatMedia(cid,uri,type,onResult)};uploadJob?.cancel();_upload.value=0f
    uploadJob=scope.launch{val result=runCatching{LiquidApi.upload(uri,cid){p->scope.launch{_upload.value=p}}};_upload.value=null;if(result.isFailure&&result.exceptionOrNull() !is CancellationException)_error.value=result.exceptionOrNull()?.message;if(result.isSuccess)retryUpload=null;onResult(result)}
  }
  fun cancelUpload(){uploadJob?.cancel();retryUpload=null;_upload.value=null}
  fun hasUploadRetry()=retryUpload!=null && _upload.value==null
  fun retryUpload(){retryUpload?.invoke()}
  fun uploadProfilePhoto(uri:Uri,onResult:(Result<String>)->Unit={}){runAction{val r=runCatching{LiquidApi.upload(uri,null)};onResult(r);r.getOrThrow()}}
  fun forwardMedia(message:Message,target:String)=runAction {
    val result=LiquidApi.call("forward",mapOf("conversationId" to message.conversationId,"messageId" to message.id,"targetId" to target,"id" to UUID.randomUUID().toString()))
  }
  private fun action(name:String,cid:String,id:String,extra:Map<String,Any?> = emptyMap())=runAction{LiquidApi.call(name,mapOf("conversationId" to cid,"messageId" to id)+extra)}
  fun addReaction(cid:String,id:String,emoji:String)=action("react",cid,id,mapOf("emoji" to emoji))
  fun deleteMessage(cid:String,id:String)=action("delete",cid,id)
  fun editMessage(cid:String,id:String,text:String)=action("edit",cid,id,mapOf("text" to text))
  fun pinMessage(cid:String,id:String)=action("pin",cid,id)
  fun hideMessage(cid:String,id:String){prefs.edit().putBoolean("hidden:$uid:$id",true).apply();_messages.update{it+(cid to it[cid].orEmpty().filterNot{m->m.id==id})}}
  fun starMessage(cid:String,id:String){val next=!prefs.getBoolean("star:$uid:$id",false);prefs.edit().putBoolean("star:$uid:$id",next).apply();updateLocal(cid,id){it.copy(isStarred=next)}}

  private fun receipt(cid:String,id:String,status:String){val key="$uid:$id:$status";if(!receipts.add(key))return;scope.launch{runCatching{LiquidApi.call("receipt",mapOf("conversationId" to cid,"messageId" to id,"status" to status))}.onFailure{receipts.remove(key)}}}
  fun clearUnread(cid:String){_messages.value[cid].orEmpty().filter{it.senderId!=uid&&it.status!=MessageDeliveryStatus.READ}.forEach{receipt(cid,it.id,if(_privacy.value.readReceipts)"READ" else "DELIVERED")};if(_conversations.value.find{it.id==cid}?.unreadCount!=0)setting(cid,"read",true)}
  private fun setting(cid:String,field:String,value:Any)=runAction{LiquidApi.call("conversationSetting",mapOf("conversationId" to cid,"field" to field,"value" to value))}
  fun setConversationArchived(cid:String,value:Boolean)=setting(cid,"archivedFor",value)
  fun setConversationMuted(cid:String,value:Boolean)=setting(cid,"mutedFor",value)
  fun setFavorite(cid:String,value:Boolean)=setting(cid,"favoriteFor",value)
  fun deleteChatForMe(cid:String)=setting(cid,"deletedFor",true)
  fun setDisappearingMessages(cid:String,seconds:Long)=setting(cid,"disappearingSeconds",seconds)
  fun setConversationWallpaper(cid:String,index:Int){prefs.edit().putInt("wallpaper:$uid:$cid",index).apply();_conversations.update{it.map{c->if(c.id==cid)c.copy(wallpaperIndex=index)else c}}}
  private val lastTyping=mutableMapOf<String,Long>()
  fun setTyping(cid:String,value:Boolean){if(uid.isBlank())return;val now=System.currentTimeMillis();if(value&&now-(lastTyping[cid]?:0)<2500)return;lastTyping[cid]=if(value)now else 0
    db.document("conversations/$cid/typing/$uid").set(mapOf("until" to if(value)now+6000 else 0L)).addOnFailureListener{/* next heartbeat / timeout clears stale state */}
  }
  fun setPresence(value:Boolean){resumed=value;if(uid.isNotBlank())writePresence(value)}
  private fun writePresence(value:Boolean){val now=System.currentTimeMillis();db.document("directory/$uid").set(mapOf("isOnline" to (value&&_privacy.value.onlineVisibility!="Nobody"),"onlineVisible" to (_privacy.value.onlineVisibility!="Nobody"),"lastSeenVisible" to (_privacy.value.lastSeenVisibility!="Nobody"),"heartbeatAt" to now,"lastSeen" to if(_privacy.value.lastSeenVisibility!="Nobody")now else 0L),SetOptions.merge())}
  fun draft(cid:String)=prefs.getString("draft:$uid:$cid","").orEmpty()
  fun saveDraft(cid:String,text:String){prefs.edit().putString("draft:$uid:$cid",text).apply()}
  fun updateProfile(displayName:String,username:String,bio:String,phoneNumber:String)=runAction{LiquidApi.call("profile",mapOf("displayName" to displayName,"username" to username,"bio" to bio,"phoneNumber" to phoneNumber))}
  fun updateAppearance(a:AppearanceSettings){_appearance.value=a;appearanceJob?.cancel();appearanceJob=scope.launch{delay(500);save("appearance",mapOf("isDarkMode" to a.isDarkMode,"glassIntensity" to a.glassIntensity,"blurAlpha" to a.blurAlpha,"cornerRadiusDp" to a.cornerRadiusDp,"borderStrength" to a.borderStrength,"isReducedMotion" to a.isReducedMotion))}}
  fun updatePrivacy(p:PrivacySettings){_privacy.value=p;save("privacy",mapOf("lastSeenVisibility" to p.lastSeenVisibility,"onlineVisibility" to p.onlineVisibility,"profilePhotoVisibility" to p.profilePhotoVisibility,"readReceipts" to p.readReceipts));writePresence(resumed)}
  fun updateNotifications(n:NotificationSettings){_notifications.value=n;save("notifications",mapOf("messages" to n.messages,"vibration" to n.vibration))}
  private fun save(field:String,value:Any){if(uid.isNotBlank())db.document("users/$uid").set(mapOf(field to value),SetOptions.merge()).addOnFailureListener{_error.value=it.message}}
  fun blockUser(id:String){_blockedUserIds.update{it+id};save("blockedUserIds",_blockedUserIds.value.toList())}
  fun unblockUser(id:String){_blockedUserIds.update{it-id};save("blockedUserIds",_blockedUserIds.value.toList())}
  fun report(id:String,reason:String)=runAction{LiquidApi.call("report",mapOf("otherUid" to id,"reason" to reason));_error.value="Report submitted"}
  suspend fun deleteAccount():Result<Unit> = runCatching {LiquidApi.call("deleteAccount");logout()}
  fun addSearchHistory(q:String){if(q.isNotBlank())_searchHistory.update{(listOf(q)+it.filterNot{old->old==q}).take(8)}}
  fun clearSearchHistory(){_searchHistory.value=emptyList()}
}
