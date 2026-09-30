@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.LiquidChatViewModel
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

fun presenceLabel(u:User):String {
 if(u.onlineVisible&&u.isOnline)return "Online"
 if(!u.lastSeenVisible||u.lastSeen<=0)return ""
 val now=Calendar.getInstance();val whenSeen=Calendar.getInstance().apply{timeInMillis=u.lastSeen}
 val time=SimpleDateFormat("h:mm a",Locale.getDefault()).format(Date(u.lastSeen))
 val today=now.get(Calendar.YEAR)==whenSeen.get(Calendar.YEAR)&&now.get(Calendar.DAY_OF_YEAR)==whenSeen.get(Calendar.DAY_OF_YEAR)
 now.add(Calendar.DAY_OF_YEAR,-1)
 val yesterday=now.get(Calendar.YEAR)==whenSeen.get(Calendar.YEAR)&&now.get(Calendar.DAY_OF_YEAR)==whenSeen.get(Calendar.DAY_OF_YEAR)
 return "Last seen "+when{today->"today at $time";yesterday->"yesterday at $time";else->SimpleDateFormat("d MMM yyyy 'at' h:mm a",Locale.getDefault()).format(Date(u.lastSeen))}
}

@Composable
fun ConversationScreen(conversationId:String,viewModel:LiquidChatViewModel,onBackClick:()->Unit,onNavigateToProfile:(String)->Unit,onNavigateToCamera:()->Unit={},modifier:Modifier=Modifier){
 val cs by viewModel.conversations.collectAsState();val map by viewModel.messages.collectAsState();val me by viewModel.currentUser.collectAsState();val upload by viewModel.upload.collectAsState();val blocked by viewModel.blockedUserIds.collectAsState()
 val repo=viewModel.repository;val conv=cs.find{it.id==conversationId};val other=conv?.otherUser?:User(displayName="Contact");val all=map[conversationId].orEmpty()
 val config=LocalLiquidGlass.current;val context=LocalContext.current;val clipboard=LocalClipboardManager.current;val haptic=LocalHapticFeedback.current;val scope=rememberCoroutineScope();val list=rememberLazyListState()
 var text by rememberSaveable(conversationId){mutableStateOf(repo.draft(conversationId))};var reply by remember{mutableStateOf<Message?>(null)};var actions by remember{mutableStateOf<Message?>(null)};var editing by remember{mutableStateOf<Message?>(null)};var editText by remember{mutableStateOf("")};var forward by remember{mutableStateOf<Message?>(null)};var viewer by remember{mutableStateOf<Message?>(null)}
 var deleteForEveryone by remember{mutableStateOf<Message?>(null)}
 var sheet by remember{mutableStateOf(false)};var settings by remember{mutableStateOf(false)};var query by rememberSaveable{mutableStateOf("")};var search by remember{mutableStateOf(false)};var stars by remember{mutableStateOf(false)}
 var tailId by remember(conversationId){mutableStateOf<String?>(null)};var lastRemoteId by remember(conversationId){mutableStateOf<String?>(null)};var typingSeen by remember{mutableLongStateOf(0L)};var initial by remember{mutableStateOf(true)}
 var recording by remember{mutableStateOf(false)};var locked by remember{mutableStateOf(false)};var elapsed by remember{mutableIntStateOf(0)};var recorder by remember{mutableStateOf<MediaRecorder?>(null)};var voiceFile by remember{mutableStateOf<File?>(null)}
 fun stopRecording(send:Boolean){
  val r=recorder;recorder=null;val file=voiceFile;voiceFile=null;val seconds=elapsed
  val ok=runCatching{r?.stop()}.isSuccess;runCatching{r?.release()};recording=false;locked=false
  if(send&&ok&&file!=null&&file.length()>0){repo.uploadChatMedia(conversationId,Uri.fromFile(file),MessageType.VOICE){result->result.onSuccess{url->viewModel.sendMessage(conversationId,"Voice message",MessageType.VOICE,url,voiceDurationSeconds=seconds.coerceAtLeast(1));file.delete()}}}else file?.delete()
 }
 fun startRecording(){if(recording)return
  runCatching{
   val file=File.createTempFile("voice-",".m4a",context.cacheDir)
   val r=if(Build.VERSION.SDK_INT>=31)MediaRecorder(context)else @Suppress("DEPRECATION") MediaRecorder()
   r.setAudioSource(MediaRecorder.AudioSource.MIC);r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);r.setAudioEncodingBitRate(64000);r.setOutputFile(file.absolutePath);r.prepare();r.start();recorder=r;voiceFile=file;recording=true;elapsed=0
  }.onFailure{android.widget.Toast.makeText(context,it.message,android.widget.Toast.LENGTH_LONG).show()}
 }
 val stopCurrent by rememberUpdatedState<(Boolean)->Unit>({send->stopRecording(send)})
 val startCurrent by rememberUpdatedState<()->Unit>({startRecording()})
 val recordingCurrent by rememberUpdatedState(recording)
 val lockedCurrent by rememberUpdatedState(locked)
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){if(it){locked=true;startRecording()}}
 fun requestRecording(){if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)startRecording()else permission.launch(Manifest.permission.RECORD_AUDIO)}
 val requestCurrent by rememberUpdatedState<()->Unit>({requestRecording()})
 fun uploadFile(uri:Uri,type:MessageType){repo.uploadChatMedia(conversationId,uri,type){result->result.onSuccess{url->viewModel.sendMessage(conversationId,when(type){MessageType.IMAGE->"Photo";MessageType.VIDEO->"Video";else->"Document"},type,url)}}}
 val image=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){it?.let{uploadFile(it,MessageType.IMAGE)}}
 val video=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){it?.let{uploadFile(it,MessageType.VIDEO)}}
 val file=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){it?.let{uploadFile(it,MessageType.FILE)}}
 LaunchedEffect(recording){while(recording){delay(1000);elapsed++;if(elapsed>=600)stopRecording(true)}}
 val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current
 DisposableEffect(lifecycle){val observer=androidx.lifecycle.LifecycleEventObserver{_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP){if(recordingCurrent)stopCurrent(false);repo.setTyping(conversationId,false)}};lifecycle.lifecycle.addObserver(observer);onDispose{lifecycle.lifecycle.removeObserver(observer);runCatching{recorder?.release()};voiceFile?.delete();repo.setTyping(conversationId,false)}}
 LaunchedEffect(conversationId){viewModel.observeConversation(conversationId)}
 LaunchedEffect(text){repo.saveDraft(conversationId,text);if(text.isNotBlank()){repo.setTyping(conversationId,true);delay(3000)};repo.setTyping(conversationId,false)}
 LaunchedEffect(conv?.isTyping){if(conv?.isTyping==true){typingSeen=System.currentTimeMillis();tailId=null}}
 LaunchedEffect(all.lastOrNull()?.id,all.size){
  val remote=all.lastOrNull{it.senderId!=me.uid}
  if(!initial&&remote!=null&&remote.type==MessageType.TEXT&&remote.id!=lastRemoteId&&System.currentTimeMillis()-typingSeen<8000)tailId=remote.id
  lastRemoteId=remote?.id
  val nearBottom=list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let{it>=list.layoutInfo.totalItemsCount-4}?:true
  if(initial||nearBottom||all.lastOrNull()?.senderId==me.uid){delay(60);val count=list.layoutInfo.totalItemsCount;if(count>0){if(config.isReducedMotion)list.scrollToItem(count-1)else list.animateScrollToItem(count-1)}}
  initial=false;viewModel.clearUnread(conversationId)
 }
 LaunchedEffect(tailId){if(tailId!=null){delay(1100);tailId=null}}
 val visible=all.filter{(!stars||it.isStarred)&&(query.isBlank()||it.text.contains(query,true))}
 val morph=visible.find{it.id==tailId}
 LiquidBackground(modifier,crystal=conv?.wallpaperIndex!=1){
  Scaffold(containerColor=Color.Transparent,topBar={
   Column(Modifier.statusBarsPadding().padding(horizontal=12.dp,vertical=8.dp)){
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
     GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack,"Back",onBackClick)
     GlassCard(Modifier.weight(1f),shape=RoundedCornerShape(32.dp),onClick={onNavigateToProfile(other.uid)}){Row(Modifier.padding(horizontal=12.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically){GlassAvatar(other.photoUrl,other.displayName,36.dp,conv?.isOnline==true&&other.onlineVisible);Spacer(Modifier.width(9.dp));Column{Text(other.displayName,fontWeight=FontWeight.SemiBold,maxLines=1);val label=presenceLabel(other);if(label.isNotBlank())Text(label,fontSize=10.sp,color=if(other.isOnline)EmeraldOnline else MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2)}}}
     GlassIconButton(Icons.Default.MoreHoriz,"Chat menu",{settings=true})
    }
    AnimatedVisibility(search){GlassTextField(query,{query=it},placeholder="Search this conversation",modifier=Modifier.padding(top=8.dp))}
    all.firstOrNull{it.isPinned&&!it.isDeleted}?.let{p->Text("📌 ${p.text.take(60)}",fontSize=12.sp,modifier=Modifier.padding(8.dp).clickable{actions=p})}
   }
  },bottomBar={Column(Modifier.imePadding().navigationBarsPadding().padding(horizontal=12.dp,vertical=7.dp)){
    AnimatedVisibility(reply!=null){GlassCard(Modifier.fillMaxWidth().padding(bottom=6.dp)){Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Reply to ${reply?.senderName}",color=MaterialTheme.colorScheme.primary,fontSize=12.sp);Text(reply?.text.orEmpty(),maxLines=2,fontSize=13.sp)};IconButton(onClick={reply=null}){Icon(Icons.Default.Close,"Cancel reply")}}}}
    upload?.let{progress->GlassCard(Modifier.fillMaxWidth().padding(bottom=6.dp)){Column(Modifier.padding(12.dp)){Text("Uploading ${(progress*100).toInt()}%",fontSize=12.sp);LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth());TextButton(onClick={repo.cancelUpload()}){Text("Cancel")}}}}
    if(recording)GlassCard(Modifier.fillMaxWidth().padding(bottom=6.dp)){Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically){Text("● ${elapsed/60}:${(elapsed%60).toString().padStart(2,'0')} ${if(locked)"Locked"else"↑ lock  ← cancel"}",Modifier.weight(1f),color=MaterialTheme.colorScheme.error);IconButton(onClick={stopRecording(false)}){Icon(Icons.Default.Delete,"Cancel recording")};IconButton(onClick={stopRecording(true)}){Icon(Icons.Default.Send,"Send voice message")}}}
    GlassCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(32.dp)){
     Row(Modifier.padding(5.dp),verticalAlignment=Alignment.CenterVertically){
      IconButton(onClick={sheet=true},enabled=upload==null){Icon(Icons.Default.Add,"Attach")}
      GlassTextField(text,{if(it.length<=8000)text=it},placeholder=if(other.uid in blocked)"Contact blocked"else"Message…",modifier=Modifier.weight(1f),singleLine=false,maxLines=5)
      if(text.isBlank()){
       IconButton(onClick=onNavigateToCamera,enabled=upload==null&&!recording){Icon(Icons.Default.PhotoCamera,"Camera")}
       var dx by remember{mutableFloatStateOf(0f)};var dy by remember{mutableFloatStateOf(0f)}
       Box(Modifier.size(44.dp).pointerInput(Unit){detectDragGesturesAfterLongPress(onDragStart={dx=0f;dy=0f;locked=false;requestCurrent()},onDragEnd={if(recordingCurrent&&!lockedCurrent)stopCurrent(true)},onDragCancel={if(recordingCurrent&&!lockedCurrent)stopCurrent(false)},onDrag={change,drag->change.consume();dx+=drag.x;dy+=drag.y;if(dx< -100f&&recordingCurrent)stopCurrent(false);if(dy< -100f&&recordingCurrent)locked=true}) .clickable{if(!recording){locked=true;requestRecording()}},contentAlignment=Alignment.Center){Icon(Icons.Default.Mic,"Hold to record; swipe up to lock or left to cancel")}
      }else IconButton(onClick={if(text.isNotBlank()&&other.uid !in blocked){viewModel.sendMessage(conversationId,text.trim(),replyToId=reply?.id,replyToText=reply?.text,replyToSender=reply?.senderName);text="";reply=null}},enabled=other.uid !in blocked){Icon(Icons.Default.Send,"Send",tint=MaterialTheme.colorScheme.primary)}
     }
    }
  }}){padding->
   Box(Modifier.fillMaxSize().padding(padding)){
    LazyColumn(Modifier.fillMaxSize(),state=list,contentPadding=PaddingValues(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
     item{Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){TextButton(onClick={repo.loadOlder(conversationId)}){Text("Load earlier messages")}}}
     items(visible.filterNot{it.id==morph?.id},key={it.id}){m->MessageBubble(m,m.senderId==me.uid,{actions=m},{reply=m;haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)},{viewer=m},{emoji->viewModel.addReaction(conversationId,m.id,emoji)},{repo.retryMessage(conversationId,m.id)})}
     item(key="live-typing-tail"){
      AnimatedVisibility(morph!=null||conv?.isTyping==true,enter=fadeIn()+expandVertically(),exit=fadeOut()+shrinkVertically()){
       MorphingTypingBubble(morph,config.isReducedMotion,{morph?.let{actions=it}})

      }
     }
    }
    val away=list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let{it<list.layoutInfo.totalItemsCount-3}?:false
    if(away)SmallFloatingActionButton(onClick={scope.launch{list.animateScrollToItem((list.layoutInfo.totalItemsCount-1).coerceAtLeast(0))}},modifier=Modifier.align(Alignment.BottomEnd).padding(12.dp)){Icon(Icons.Default.KeyboardArrowDown,"New messages")}
   }
  }
  if(sheet)GlassDialog("Share content",{sheet=false}){
   TextButton(onClick={sheet=false;onNavigateToCamera()}){Text("Camera")};TextButton(onClick={sheet=false;image.launch("image/*")}){Text("Photo gallery")};TextButton(onClick={sheet=false;video.launch("video/*")}){Text("Video gallery")};TextButton(onClick={sheet=false;file.launch("*/*")}){Text("Document")}
  }
  if(settings)GlassDialog("Conversation",{settings=false}){
   TextButton(onClick={search=!search;query="";settings=false}){Text("Search messages")}
   TextButton(onClick={stars=!stars;settings=false}){Text(if(stars)"Show all messages"else"Starred messages")}
   TextButton(onClick={repo.setFavorite(conversationId,conv?.isPinned!=true);settings=false}){Text(if(conv?.isPinned==true)"Remove favorite"else"Add favorite")}
   TextButton(onClick={viewModel.setConversationMuted(conversationId,conv?.isMuted!=true);settings=false}){Text(if(conv?.isMuted==true)"Unmute"else"Mute")}
   Text("Disappearing messages");Row{listOf("Off" to 0L,"24h" to 86400L,"7 days" to 604800L).forEach{(label,seconds)->TextButton(onClick={viewModel.setDisappearingMessages(conversationId,seconds);settings=false}){Text(label)}}}
   TextButton(onClick={viewModel.setConversationWallpaper(conversationId,if(conv?.wallpaperIndex==1)0 else 1);settings=false}){Text("Toggle crystal / plain wallpaper")}
  }
  actions?.let{m->GlassDialog("Message",{actions=null}){
   Row{listOf("❤️","👍","😂","😮","😢","🙏").forEach{emoji->Text(emoji,Modifier.clickable{viewModel.addReaction(conversationId,m.id,emoji);actions=null}.padding(6.dp),fontSize=23.sp)}}
   TextButton(onClick={reply=m;actions=null}){Text("Reply")};TextButton(onClick={clipboard.setText(AnnotatedString(m.text));actions=null}){Text("Copy")}
   TextButton(onClick={repo.starMessage(conversationId,m.id);actions=null}){Text(if(m.isStarred)"Unstar"else"Star")}
   TextButton(onClick={viewModel.pinMessage(conversationId,m.id);actions=null}){Text(if(m.isPinned)"Unpin"else"Pin")}
   TextButton(onClick={forward=m;actions=null}){Text("Forward")}
   if(m.senderId==me.uid&&m.type==MessageType.TEXT&&!m.isDeleted)TextButton(onClick={editing=m;editText=m.text;actions=null}){Text("Edit")}
   TextButton(onClick={repo.hideMessage(conversationId,m.id);actions=null}){Text("Delete for me")}
   if(m.senderId==me.uid)TextButton(onClick={deleteForEveryone=m;actions=null}){Text("Delete for everyone",color=MaterialTheme.colorScheme.error)}
  }}
  deleteForEveryone?.let{m->GlassDialog("Delete for everyone?",{deleteForEveryone=null}){Text("This message will be removed for both people.");GlassButton("Delete",{viewModel.deleteMessage(conversationId,m.id);deleteForEveryone=null})}}
  editing?.let{m->GlassDialog("Edit message",{editing=null}){GlassTextField(editText,{editText=it},singleLine=false,maxLines=6);GlassButton("Save",{viewModel.editMessage(conversationId,m.id,editText);editing=null})}}
  forward?.let{m->GlassDialog("Forward to",{forward=null}){cs.filter{it.id!=conversationId}.forEach{c->TextButton(onClick={if(m.type==MessageType.TEXT){viewModel.sendMessage(c.id,m.text);forward=null}else{repo.forwardMedia(m,c.id);forward=null}}){Text(c.otherUser.displayName)}};if(cs.size<2)Text("Start another conversation first")}}
  viewer?.let{MediaViewer(it){viewer=null}}
 }
}

@Composable private fun MorphingTypingBubble(message:Message?,reduced:Boolean,onLongClick:()->Unit) {
 val dark=LocalLiquidGlass.current.isDark
 Box(Modifier.widthIn(max=300.dp).clip(RoundedCornerShape(22.dp))
   .background(if(dark)Color(0xCC2168AA)else Color(0xBBA4D1FF))
   .border(1.dp,Color.White.copy(alpha=.65f),RoundedCornerShape(22.dp))
   .animateContentSize(if(reduced)tween(0)else spring(dampingRatio=.7f,stiffness=350f))
   .combinedClickable(onClick={},onLongClick=onLongClick)
   .padding(horizontal=16.dp,vertical=12.dp)) {
  if(message==null)TypingDots(reduced)
  else Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
   if(message.type==MessageType.TEXT)Text(message.text,fontSize=15.sp,lineHeight=21.sp)
   else Text(when(message.type){MessageType.IMAGE->"Photo";MessageType.VIDEO->"Video";MessageType.VOICE->"Voice message";else->"Document"})
   Text(SimpleDateFormat("h:mm a",Locale.getDefault()).format(Date(message.createdAt)),fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.align(Alignment.End))
  }
 }
}
@Composable private fun TypingDots(reduced:Boolean){
 Row(horizontalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.padding(vertical=3.dp)) {
  repeat(3){i->
   val y=if(reduced)0f else {val transition=rememberInfiniteTransition(label="typing");val value by transition.animateFloat(0f,-4f,infiniteRepeatable(tween(350,delayMillis=i*110),RepeatMode.Reverse),label="dot");value}
   Box(Modifier.offset(y=y.dp).size(6.dp).clip(CircleShape).background(Color(0xFF3779C5)))
  }
 }
}
@Composable private fun TypingBubble(){
 val reduced=LocalLiquidGlass.current.isReducedMotion
 GlassCard(shape=RoundedCornerShape(22.dp),backgroundColor=Color(0xFF85BFFF).copy(alpha=.60f)){
  Row(Modifier.padding(horizontal=18.dp,vertical=16.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
   repeat(3){i->val transition=rememberInfiniteTransition(label="typing");val y by transition.animateFloat(0f,-4f,infiniteRepeatable(tween(350,delayMillis=i*110),RepeatMode.Reverse),label="dot");Box(Modifier.offset(y=if(reduced)0.dp else y.dp).size(6.dp).clip(CircleShape).background(Color(0xFF3779C5)))}
  }
 }
}
@Composable
fun MessageBubble(message:Message,isMe:Boolean,onLongClick:()->Unit,onReply:()->Unit,onMedia:()->Unit,onReactionClick:(String)->Unit,onRetry:()->Unit){
 val dark=LocalLiquidGlass.current.isDark;val reduced=LocalLiquidGlass.current.isReducedMotion
 var drag by remember{mutableFloatStateOf(0f)}
 val offset by animateFloatAsState(drag,if(reduced)tween(0)else spring(dampingRatio=.7f),label="swipe_reply")
 Row(Modifier.fillMaxWidth(),horizontalArrangement=if(isMe)Arrangement.End else Arrangement.Start){
  Column(Modifier.widthIn(max=300.dp).offset{IntOffset(offset.roundToInt(),0)}.pointerInput(message.id){detectHorizontalDragGestures(onDragEnd={if(drag>65f)onReply();drag=0f},onDragCancel={drag=0f},onHorizontalDrag={change,amount->change.consume();drag=(drag+amount).coerceIn(0f,100f)}) .combinedClickable(onClick={if(message.mediaUrl.isNotBlank())onMedia()},onLongClick=onLongClick)){
   GlassCard(shape=RoundedCornerShape(22.dp),backgroundColor=if(isMe){if(dark)Color(0xCC353D4A)else Color.White.copy(alpha=.82f)}else if(dark)Color(0xBB2168AA)else Color(0xFFA4D1FF).copy(alpha=.72f),elevation=1.dp){
    Column(Modifier.padding(horizontal=13.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
     if(message.replyToText!=null)Text("${message.replyToSender}: ${message.replyToText}",fontSize=11.sp,maxLines=2,color=MaterialTheme.colorScheme.primary)
     if(!message.isDeleted){when(message.type){
      MessageType.IMAGE->PrivateImage(message.mediaUrl,"Photo",Modifier.fillMaxWidth().height(165.dp).clip(RoundedCornerShape(14.dp)))
      MessageType.VIDEO->Text("▶ Video • Tap to play",Modifier.padding(20.dp))
      MessageType.VOICE->VoiceWaveformPlayer(message.voiceDurationSeconds,message.mediaUrl,isOutgoing=isMe)
      MessageType.FILE->Text("▤ Document • Tap to open",Modifier.padding(12.dp))
      else->Unit
     }}
     if(message.type!=MessageType.VOICE||message.isDeleted)Text(message.text,fontSize=15.sp,lineHeight=21.sp,color=MaterialTheme.colorScheme.onSurface)
     Row(Modifier.align(Alignment.End),horizontalArrangement=Arrangement.spacedBy(4.dp)){
      if(message.isStarred)Text("★",fontSize=10.sp)
      if(message.isEdited)Text("edited",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
      Text(SimpleDateFormat("h:mm a",Locale.getDefault()).format(Date(message.createdAt)),fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
      if(isMe){val icon=when(message.status){MessageDeliveryStatus.SENDING->Icons.Default.Schedule;MessageDeliveryStatus.FAILED->Icons.Default.ErrorOutline;MessageDeliveryStatus.SENT->Icons.Default.Done;else->Icons.Default.DoneAll};Icon(icon,message.status.name,Modifier.size(14.dp),tint=if(message.status==MessageDeliveryStatus.READ)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}
     }
     if(message.status==MessageDeliveryStatus.FAILED)Text("Failed • tap to retry",color=MaterialTheme.colorScheme.error,fontSize=12.sp,modifier=Modifier.clickable(onClick=onRetry))
    }
   }
   if(message.reactions.isNotEmpty())Row{message.reactions.forEach{r->Text("${r.emoji} ${r.userIds.size}",Modifier.clickable{onReactionClick(r.emoji)}.padding(4.dp),fontSize=13.sp)}}
  }
 }
}
