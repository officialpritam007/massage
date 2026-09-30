package com.example.data.repository

import android.net.Uri
import com.example.data.model.*
import com.example.data.network.LiquidApi
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.Date
import java.util.UUID

/**
 * Firebase snapshots are read-only message truth; the authenticated API serializes mutations.
 *
 * Firestore may still contain legacy documents written by older app versions. All snapshot
 * decoding is deliberately tolerant of Number/Timestamp/String variants so one old document
 * cannot crash the process during a cached-session startup.
 */
class ChatRepository(
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {
  private val auth = FirebaseAuth.getInstance()
  private val db = FirebaseFirestore.getInstance()
  private val prefs get() = LiquidApi.context.getSharedPreferences("liquid-private", 0)
  private val uid get() = auth.currentUser?.uid.orEmpty()

  private val listeners = mutableListOf<ListenerRegistration>()
  private val messageListeners = mutableMapOf<String, ListenerRegistration>()
  private val presenceListeners = mutableMapOf<String, ListenerRegistration>()
  private var heartbeat: Job? = null
  private var outboxJob: Job? = null
  private var resumed = false
  private val limits = mutableMapOf<String, Long>()

  private val _currentUser = MutableStateFlow(User())
  val currentUser = _currentUser.asStateFlow()
  private val _users = MutableStateFlow<List<User>>(emptyList())
  val users = _users.asStateFlow()
  private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
  val conversations = _conversations.asStateFlow()
  private val _messages = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
  val messages = _messages.asStateFlow()
  private val _appearance = MutableStateFlow(AppearanceSettings())
  val appearance = _appearance.asStateFlow()
  private val _privacy = MutableStateFlow(PrivacySettings())
  val privacy = _privacy.asStateFlow()
  private val _notifications = MutableStateFlow(NotificationSettings())
  val notifications = _notifications.asStateFlow()
  private val _blockedUserIds = MutableStateFlow<Set<String>>(emptySet())
  val blockedUserIds = _blockedUserIds.asStateFlow()
  private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
  val searchHistory = _searchHistory.asStateFlow()
  private val _error = MutableStateFlow<String?>(null)
  val error = _error.asStateFlow()
  private val _loading = MutableStateFlow(true)
  val loading = _loading.asStateFlow()
  private val _upload = MutableStateFlow<Float?>(null)
  val upload = _upload.asStateFlow()

  private var appearanceJob: Job? = null
  private var uploadJob: Job? = null
  private var retryUpload: (() -> Unit)? = null
  private val failed = mutableSetOf<String>()
  private val receipts = mutableSetOf<String>()
  private val sending = kotlinx.coroutines.sync.Mutex()
  private val lastTyping = mutableMapOf<String, Long>()

  init {
    if (isUserLoggedIn()) startSync() else _loading.value = false
  }

  fun clearError() { _error.value = null }

  private fun reportSnapshotFailure(area: String, t: Throwable) {
    _error.value = "$area could not be loaded. ${t.message ?: "Invalid cached data"}"
  }

  private inline fun guardSnapshot(area: String, block: () -> Unit) {
    runCatching(block).onFailure { reportSnapshotFailure(area, it) }
  }

  private fun anyLong(value: Any?, default: Long = 0L): Long = when (value) {
    is Number -> value.toLong()
    is Timestamp -> value.toDate().time
    is Date -> value.time
    is String -> value.toLongOrNull() ?: default
    else -> default
  }

  private fun anyBoolean(value: Any?, default: Boolean = false): Boolean = when (value) {
    is Boolean -> value
    is Number -> value.toInt() != 0
    is String -> when (value.lowercase()) {
      "true", "1", "yes" -> true
      "false", "0", "no" -> false
      else -> default
    }
    else -> default
  }

  private fun DocumentSnapshot.safeString(name: String, default: String = ""): String =
    when (val value = get(name)) {
      is String -> value
      null -> default
      else -> value.toString()
    }

  private fun DocumentSnapshot.safeLong(name: String, default: Long = 0L): Long =
    anyLong(get(name), default)

  private fun DocumentSnapshot.safeBoolean(name: String, default: Boolean = false): Boolean =
    anyBoolean(get(name), default)

  private fun runAction(block: suspend () -> Unit) {
    scope.launch {
      runCatching { block() }.onFailure {
        if (it !is CancellationException) _error.value = it.message ?: "Operation failed"
      }
    }
  }

  fun isUserLoggedIn() = auth.currentUser != null

  suspend fun registerWithEmail(
    email: String,
    pass: String,
    fullName: String,
    username: String,
    phoneNumber: String
  ): Result<User> = runCatching {
    auth.createUserWithEmailAndPassword(email.trim(), pass).await()
    LiquidApi.call(
      "profile",
      mapOf("displayName" to fullName, "username" to username, "phoneNumber" to phoneNumber)
    )
    auth.currentUser?.sendEmailVerification()?.await()
    startSync()
    _currentUser.value
  }

  suspend fun signInWithEmail(email: String, pass: String): Result<User> = runCatching {
    auth.signInWithEmailAndPassword(email.trim(), pass).await()
    LiquidApi.call("profile")
    startSync()
    _currentUser.value
  }

  fun resetPassword(email: String) = runAction {
    auth.sendPasswordResetEmail(email.trim()).await()
    _error.value = "Password reset email sent"
  }

  fun verifyEmail() = runAction {
    auth.currentUser?.sendEmailVerification()?.await()
    _error.value = "Verification email sent"
  }

  fun logout() {
    setPresence(false)
    val tokenKey = prefs.getString("deviceId", "").orEmpty()
    if (uid.isNotBlank() && tokenKey.isNotBlank()) {
      db.document("users/$uid").update("tokens.$tokenKey", FieldValue.delete())
    }
    stopSync()
    appearanceJob?.cancel()
    uploadJob?.cancel()
    retryUpload = null
    LiquidApi.clear()
    prefs.edit().clear().apply()
    auth.signOut()
    _currentUser.value = User()
    _users.value = emptyList()
    _conversations.value = emptyList()
    _messages.value = emptyMap()
    _blockedUserIds.value = emptySet()
    _appearance.value = AppearanceSettings()
    _privacy.value = PrivacySettings()
    _searchHistory.value = emptyList()
  }

  fun close() {
    setPresence(false)
    stopSync()
    scope.cancel()
  }

  private fun stopSync() {
    receipts.clear()
    listeners.forEach { it.remove() }
    listeners.clear()
    messageListeners.values.forEach { it.remove() }
    messageListeners.clear()
    presenceListeners.values.forEach { it.remove() }
    presenceListeners.clear()
    heartbeat?.cancel()
    outboxJob?.cancel()
  }

  private fun startSync() {
    stopSync()
    if (uid.isBlank()) {
      _loading.value = false
      return
    }
    val account = uid
    _loading.value = true
    _currentUser.value = User(uid = account, email = auth.currentUser?.email.orEmpty())

    // Profile repair is asynchronous, but snapshot parsing below is schema tolerant so legacy
    // data can never kill the process while this request is in flight.
    runAction { LiquidApi.call("profile") }

    val device = prefs.getString("deviceId", null)
      ?: UUID.randomUUID().toString().replace("-", "").also {
        prefs.edit().putString("deviceId", it).apply()
      }

    FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
      if (uid == account) {
        db.document("users/$account").update("tokens.$device", token)
      }
    }

    listeners += db.document("users/$account").addSnapshotListener { snapshot, error ->
      if (error != null) {
        _error.value = error.message
        return@addSnapshotListener
      }
      if (snapshot == null || !snapshot.exists()) return@addSnapshotListener
      guardSnapshot("Profile") {
        _currentUser.value = toUser(snapshot)
        _blockedUserIds.value = (snapshot.get("blockedUserIds") as? List<*>)
          ?.filterIsInstance<String>()?.toSet().orEmpty()

        val a = snapshot.get("appearance") as? Map<*, *>
        if (a != null) {
          _appearance.value = AppearanceSettings(
            isDarkMode = anyBoolean(a["isDarkMode"], false),
            glassIntensity = (a["glassIntensity"] as? Number)?.toFloat() ?: 0.7f,
            blurAlpha = (a["blurAlpha"] as? Number)?.toFloat() ?: 0.7f,
            cornerRadiusDp = (a["cornerRadiusDp"] as? Number)?.toFloat() ?: 26f,
            borderStrength = (a["borderStrength"] as? Number)?.toFloat() ?: 0.65f,
            isReducedMotion = anyBoolean(a["isReducedMotion"], false)
          )
        }

        val p = snapshot.get("privacy") as? Map<*, *>
        if (p != null) {
          _privacy.value = PrivacySettings(
            p["lastSeenVisibility"] as? String ?: "Everyone",
            p["onlineVisibility"] as? String ?: "Everyone",
            p["profilePhotoVisibility"] as? String ?: "Everyone",
            anyBoolean(p["readReceipts"], true)
          )
        }

        val n = snapshot.get("notifications") as? Map<*, *>
        if (n != null) {
          _notifications.value = NotificationSettings(
            anyBoolean(n["messages"], true),
            anyBoolean(n["vibration"], true)
          )
        }
        prefs.edit().putBoolean("notifications", _notifications.value.messages).apply()
      }
    }

    listeners += db.collection("directory").limit(200).addSnapshotListener { snapshot, error ->
      if (error != null) {
        _error.value = error.message
        return@addSnapshotListener
      }
      if (snapshot != null) {
        guardSnapshot("Contacts") {
          _users.value = snapshot.documents.mapNotNull { doc ->
            runCatching { toUser(doc) }.getOrNull()
          }.filter { it.uid != account }
          refreshUsers()
        }
      }
    }

    listeners += db.collection("conversations")
      .whereArrayContains("participantIds", account)
      .addSnapshotListener { snapshot, error ->
        _loading.value = false
        if (error != null) {
          _error.value = error.message
          return@addSnapshotListener
        }
        if (snapshot != null) {
          guardSnapshot("Chats") {
            if (snapshot.isEmpty && snapshot.metadata.isFromCache && _conversations.value.isNotEmpty()) {
              return@guardSnapshot
            }
            _conversations.value = snapshot.documents
              .mapNotNull { doc -> runCatching { toConversation(doc) }.getOrNull() }
              .sortedByDescending { it.lastMessageTime }
            val active = _conversations.value.map { it.id }.toSet()
            messageListeners.keys.filter { it !in active }.toList().forEach {
              messageListeners.remove(it)?.remove()
              presenceListeners.remove(it)?.remove()
            }
            _conversations.value.forEach { conversation ->
              observeConversation(conversation.id)
              observePresence(conversation.id)
            }
          }
        }
      }

    restoreOutbox()
    outboxJob = scope.launch {
      while (isActive && uid == account) {
        flushOutbox()
        delay(15_000)
      }
    }
    heartbeat = scope.launch {
      var ticks = 0
      while (isActive && uid == account) {
        if (resumed && ticks++ % 25 == 0) writePresence(true)
        refreshUsers()
        refreshTyping()
        expireMessages()
        delay(1_000)
      }
    }
  }

  private fun toUser(snapshot: DocumentSnapshot): User {
    val own = snapshot.id == uid
    val heartbeatAt = snapshot.safeLong("heartbeatAt")
    val pubPhoto = snapshot.safeString("photoUrl")
      .takeUnless { it.contains("images.unsplash.com") }
      .orEmpty()
    return User(
      uid = snapshot.id,
      displayName = snapshot.safeString("displayName", "Contact").ifBlank { "Contact" },
      username = snapshot.safeString("username"),
      email = if (own) snapshot.safeString("email") else "",
      phoneNumber = if (own) snapshot.safeString("phoneNumber") else "",
      photoUrl = pubPhoto,
      bio = snapshot.safeString("bio"),
      isOnline = snapshot.safeBoolean("isOnline") &&
        System.currentTimeMillis() - heartbeatAt < 45_000,
      lastSeen = snapshot.safeLong("lastSeen"),
      lastActiveAt = heartbeatAt,
      onlineVisible = snapshot.safeBoolean("onlineVisible", true),
      lastSeenVisible = snapshot.safeBoolean("lastSeenVisible", true)
    )
  }

  private fun toConversation(snapshot: DocumentSnapshot): Conversation? {
    val ids = (snapshot.get("participantIds") as? List<*>)?.filterIsInstance<String>().orEmpty()
    if (ids.size != 2) return null
    if ((snapshot.get("deletedFor") as? List<*>)?.contains(uid) == true) return null
    val other = ids.firstOrNull { it != uid } ?: return null
    val user = _users.value.find { it.uid == other } ?: User(uid = other, displayName = "Contact")
    fun flag(name: String) = (snapshot.get(name) as? List<*>)?.contains(uid) == true
    return Conversation(
      id = snapshot.id,
      participantIds = ids,
      otherUser = user,
      lastMessageText = snapshot.safeString("lastMessageText"),
      lastMessageTime = snapshot.safeLong("lastMessageTime"),
      lastMessageSenderId = snapshot.safeString("lastMessageSenderId"),
      unreadCount = ((snapshot.get("unreadCounts") as? Map<*, *>)?.get(uid) as? Number)?.toInt() ?: 0,
      isPinned = flag("favoriteFor"),
      isMuted = flag("mutedFor"),
      isArchived = flag("archivedFor"),
      isOnline = user.isOnline,
      disappearingSeconds = snapshot.safeLong("disappearingSeconds"),
      wallpaperIndex = prefs.getInt("wallpaper:$uid:${snapshot.id}", 0)
    )
  }

  private fun refreshUsers() {
    _users.update { users ->
      users.map { user ->
        user.copy(isOnline = user.isOnline && System.currentTimeMillis() - user.lastActiveAt < 45_000)
      }
    }
    _conversations.update { conversations ->
      conversations.map { conversation ->
        val user = _users.value.find { it.uid == conversation.otherUser.uid } ?: conversation.otherUser
        val online = user.isOnline && System.currentTimeMillis() - user.lastActiveAt < 45_000
        conversation.copy(otherUser = user.copy(isOnline = online), isOnline = online)
      }
    }
  }

  private fun observePresence(cid: String) {
    if (presenceListeners.containsKey(cid)) return
    presenceListeners[cid] = db.collection("conversations/$cid/typing")
      .addSnapshotListener { snapshot, error ->
        if (error != null) return@addSnapshotListener
        guardSnapshot("Typing") {
          val other = _conversations.value.find { it.id == cid }?.otherUser?.uid
          val until = snapshot?.documents?.firstOrNull { it.id == other }?.safeLong("until") ?: 0L
          _conversations.update { conversations ->
            conversations.map {
              if (it.id == cid) it.copy(
                typingUntil = until,
                isTyping = until > System.currentTimeMillis()
              ) else it
            }
          }
        }
      }
  }

  private fun refreshTyping() {
    _conversations.update { conversations ->
      conversations.map { it.copy(isTyping = it.typingUntil > System.currentTimeMillis()) }
    }
  }

  fun observeConversation(cid: String) {
    if (messageListeners.containsKey(cid)) return
    val limit = limits.getOrPut(cid) { 100 }
    messageListeners[cid] = db.collection("conversations/$cid/messages")
      .orderBy("createdAt", Query.Direction.DESCENDING)
      .limit(limit)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          _error.value = error.message
          return@addSnapshotListener
        }
        if (snapshot != null) {
          guardSnapshot("Messages") {
            val list = snapshot.documents
              .mapNotNull { doc -> runCatching { toMessage(cid, doc) }.getOrNull() }
              .sortedBy { it.createdAt }
            val pending = _messages.value[cid].orEmpty().filter {
              it.status == MessageDeliveryStatus.SENDING || it.status == MessageDeliveryStatus.FAILED
            }
            _messages.update {
              it + (cid to (list + pending.filter { message -> list.none { saved -> saved.id == message.id } })
                .sortedBy { message -> message.createdAt })
            }
            list.filter { it.senderId != uid && it.status == MessageDeliveryStatus.SENT }
              .forEach { receipt(cid, it.id, "DELIVERED") }
          }
        }
      }
  }

  fun loadOlder(cid: String) {
    limits[cid] = (limits[cid] ?: 100) + 100
    messageListeners.remove(cid)?.remove()
    observeConversation(cid)
  }

  private fun toMessage(cid: String, snapshot: DocumentSnapshot): Message? {
    val rawExpires = snapshot.get("expiresAt")
    val expires = if (rawExpires == null) null else anyLong(rawExpires, 0L).takeIf { it > 0L }
    if (expires != null && expires <= System.currentTimeMillis()) return null
    if (prefs.getBoolean("hidden:$uid:${snapshot.id}", false)) return null

    return Message(
      id = snapshot.id,
      conversationId = cid,
      senderId = snapshot.safeString("senderId"),
      senderName = snapshot.safeString("senderName"),
      text = snapshot.safeString("text"),
      type = runCatching {
        MessageType.valueOf(snapshot.safeString("type", "TEXT"))
      }.getOrDefault(MessageType.TEXT),
      mediaUrl = snapshot.safeString("mediaUrl"),
      voiceDurationSeconds = snapshot.safeLong("voiceDurationSeconds").toInt(),
      createdAt = snapshot.safeLong("createdAt"),
      status = runCatching {
        MessageDeliveryStatus.valueOf(snapshot.safeString("status", "SENT"))
      }.getOrDefault(MessageDeliveryStatus.SENT),
      replyToId = snapshot.get("replyToId") as? String,
      replyToText = snapshot.get("replyToText") as? String,
      replyToSender = snapshot.get("replyToSender") as? String,
      isEdited = snapshot.safeBoolean("isEdited"),
      isDeleted = snapshot.safeBoolean("isDeleted"),
      isPinned = snapshot.safeBoolean("isPinned"),
      isStarred = prefs.getBoolean("star:$uid:${snapshot.id}", false),
      expiresAt = expires,
      reactions = (snapshot.get("reactions") as? List<*>)?.mapNotNull {
        val reaction = it as? Map<*, *> ?: return@mapNotNull null
        MessageReaction(
          reaction["emoji"] as? String ?: "",
          (reaction["userIds"] as? List<*>)?.filterIsInstance<String>().orEmpty()
        )
      }.orEmpty()
    )
  }

  private fun expireMessages() {
    val now = System.currentTimeMillis()
    _messages.update { map ->
      map.mapValues { (_, messages) -> messages.filter { it.expiresAt == null || it.expiresAt > now } }
    }
  }

  fun getOrCreateConversationId(otherUid: String): String {
    val cid = listOf(uid, otherUid).sorted().joinToString("_")
    if (_conversations.value.none { it.id == cid }) {
      _conversations.update {
        it + Conversation(
          id = cid,
          participantIds = listOf(uid, otherUid),
          otherUser = _users.value.find { user -> user.uid == otherUid }
            ?: User(uid = otherUid, displayName = "Contact")
        )
      }
    }
    runAction {
      LiquidApi.call("conversation", mapOf("otherUid" to otherUid))
      observeConversation(cid)
      observePresence(cid)
    }
    return cid
  }

  fun sendMessage(
    conversationId: String,
    text: String,
    type: MessageType = MessageType.TEXT,
    mediaUrl: String = "",
    replyToId: String? = null,
    replyToText: String? = null,
    replyToSender: String? = null,
    voiceDurationSeconds: Int = 0
  ) {
    if (text.isBlank() && mediaUrl.isBlank()) return
    val message = Message(
      id = UUID.randomUUID().toString(),
      conversationId = conversationId,
      senderId = uid,
      senderName = _currentUser.value.displayName,
      text = text,
      type = type,
      mediaUrl = mediaUrl,
      replyToId = replyToId,
      replyToText = replyToText,
      replyToSender = replyToSender,
      voiceDurationSeconds = voiceDurationSeconds,
      status = MessageDeliveryStatus.SENDING
    )
    _messages.update { it + (conversationId to (it[conversationId].orEmpty() + message)) }
    persist(message)
    scope.launch { flushOutbox() }
    setTyping(conversationId, false)
  }

  private fun json(message: Message) = JSONObject(
    mapOf(
      "otherUid" to _conversations.value.find { it.id == message.conversationId }?.otherUser?.uid,
      "id" to message.id,
      "conversationId" to message.conversationId,
      "senderId" to message.senderId,
      "text" to message.text,
      "type" to message.type.name,
      "mediaUrl" to message.mediaUrl,
      "voiceDurationSeconds" to message.voiceDurationSeconds,
      "replyToId" to message.replyToId,
      "replyToText" to message.replyToText,
      "replyToSender" to message.replyToSender,
      "createdAt" to message.createdAt
    )
  )

  private fun persist(message: Message) {
    prefs.edit().putString("outbox:$uid:${message.id}", json(message).toString()).apply()
  }

  private fun restoreOutbox() {
    prefs.all.filterKeys { it.startsWith("outbox:$uid:") }.values.forEach { raw ->
      runCatching {
        val json = JSONObject(raw as String)
        val message = Message(
          id = json.getString("id"),
          conversationId = json.getString("conversationId"),
          senderId = uid,
          text = json.getString("text"),
          type = MessageType.valueOf(json.getString("type")),
          mediaUrl = json.optString("mediaUrl"),
          voiceDurationSeconds = json.optInt("voiceDurationSeconds"),
          createdAt = json.optLong("createdAt"),
          status = MessageDeliveryStatus.SENDING
        )
        _messages.update {
          it + (message.conversationId to
            (it[message.conversationId].orEmpty().filterNot { saved -> saved.id == message.id } + message))
        }
      }
    }
  }

  private suspend fun flushOutbox() {
    if (uid.isBlank() || !sending.tryLock()) return
    val account = uid
    try {
      for ((key, raw) in prefs.all.filterKeys { it.startsWith("outbox:$account:") }) {
        if (uid != account) break
        val json = JSONObject(raw as String)
        val id = json.getString("id")
        if (id in failed) continue
        val cid = json.getString("conversationId")
        val data = json.keys().asSequence().associateWith {
          json.opt(it).takeUnless { value -> value == JSONObject.NULL }
        }
        try {
          LiquidApi.call("send", data)
          prefs.edit().remove(key).apply()
          updateLocal(cid, id) { it.copy(status = MessageDeliveryStatus.SENT) }
        } catch (e: Exception) {
          if (e is CancellationException) throw e
          if (e !is java.io.IOException) {
            failed += id
            updateLocal(cid, id) { it.copy(status = MessageDeliveryStatus.FAILED) }
            _error.value = e.message
          }
        }
      }
    } finally {
      sending.unlock()
    }
  }

  fun retryMessage(cid: String, id: String) {
    failed -= id
    updateLocal(cid, id) { it.copy(status = MessageDeliveryStatus.SENDING) }
    scope.launch { flushOutbox() }
  }

  private fun updateLocal(cid: String, id: String, transform: (Message) -> Message) {
    _messages.update { map ->
      map + (cid to map[cid].orEmpty().map { if (it.id == id) transform(it) else it })
    }
  }

  fun uploadChatMedia(
    cid: String,
    uri: Uri,
    type: MessageType,
    onResult: (Result<String>) -> Unit = {}
  ) {
    retryUpload = { uploadChatMedia(cid, uri, type, onResult) }
    uploadJob?.cancel()
    _upload.value = 0f
    uploadJob = scope.launch {
      val result = runCatching {
        LiquidApi.upload(uri, cid) { progress -> scope.launch { _upload.value = progress } }
      }
      _upload.value = null
      if (result.isFailure && result.exceptionOrNull() !is CancellationException) {
        _error.value = result.exceptionOrNull()?.message
      }
      if (result.isSuccess) retryUpload = null
      onResult(result)
    }
  }

  fun cancelUpload() {
    uploadJob?.cancel()
    retryUpload = null
    _upload.value = null
  }

  fun hasUploadRetry() = retryUpload != null && _upload.value == null
  fun retryUpload() { retryUpload?.invoke() }

  fun uploadProfilePhoto(uri: Uri, onResult: (Result<String>) -> Unit = {}) {
    runAction {
      val result = runCatching { LiquidApi.upload(uri, null) }
      onResult(result)
      result.getOrThrow()
    }
  }

  fun forwardMedia(message: Message, target: String) = runAction {
    LiquidApi.call(
      "forward",
      mapOf(
        "conversationId" to message.conversationId,
        "messageId" to message.id,
        "targetId" to target,
        "id" to UUID.randomUUID().toString()
      )
    )
  }

  private fun action(
    name: String,
    cid: String,
    id: String,
    extra: Map<String, Any?> = emptyMap()
  ) = runAction {
    LiquidApi.call(name, mapOf("conversationId" to cid, "messageId" to id) + extra)
  }

  fun addReaction(cid: String, id: String, emoji: String) = action("react", cid, id, mapOf("emoji" to emoji))
  fun deleteMessage(cid: String, id: String) = action("delete", cid, id)
  fun editMessage(cid: String, id: String, text: String) = action("edit", cid, id, mapOf("text" to text))
  fun pinMessage(cid: String, id: String) = action("pin", cid, id)

  fun hideMessage(cid: String, id: String) {
    prefs.edit().putBoolean("hidden:$uid:$id", true).apply()
    _messages.update { it + (cid to it[cid].orEmpty().filterNot { message -> message.id == id }) }
  }

  fun starMessage(cid: String, id: String) {
    val next = !prefs.getBoolean("star:$uid:$id", false)
    prefs.edit().putBoolean("star:$uid:$id", next).apply()
    updateLocal(cid, id) { it.copy(isStarred = next) }
  }

  private fun receipt(cid: String, id: String, status: String) {
    val key = "$uid:$id:$status"
    if (!receipts.add(key)) return
    scope.launch {
      runCatching {
        LiquidApi.call("receipt", mapOf("conversationId" to cid, "messageId" to id, "status" to status))
      }.onFailure { receipts.remove(key) }
    }
  }

  fun clearUnread(cid: String) {
    _messages.value[cid].orEmpty()
      .filter { it.senderId != uid && it.status != MessageDeliveryStatus.READ }
      .forEach { receipt(cid, it.id, if (_privacy.value.readReceipts) "READ" else "DELIVERED") }
    if (_conversations.value.find { it.id == cid }?.unreadCount != 0) setting(cid, "read", true)
  }

  private fun setting(cid: String, field: String, value: Any) = runAction {
    LiquidApi.call(
      "conversationSetting",
      mapOf("conversationId" to cid, "field" to field, "value" to value)
    )
  }

  fun setConversationArchived(cid: String, value: Boolean) = setting(cid, "archivedFor", value)
  fun setConversationMuted(cid: String, value: Boolean) = setting(cid, "mutedFor", value)
  fun setFavorite(cid: String, value: Boolean) = setting(cid, "favoriteFor", value)
  fun deleteChatForMe(cid: String) = setting(cid, "deletedFor", true)
  fun setDisappearingMessages(cid: String, seconds: Long) = setting(cid, "disappearingSeconds", seconds)

  fun setConversationWallpaper(cid: String, index: Int) {
    prefs.edit().putInt("wallpaper:$uid:$cid", index).apply()
    _conversations.update { conversations ->
      conversations.map { if (it.id == cid) it.copy(wallpaperIndex = index) else it }
    }
  }

  fun setTyping(cid: String, value: Boolean) {
    if (uid.isBlank()) return
    val now = System.currentTimeMillis()
    if (value && now - (lastTyping[cid] ?: 0) < 2_500) return
    lastTyping[cid] = if (value) now else 0
    db.document("conversations/$cid/typing/$uid")
      .set(mapOf("until" to if (value) now + 6_000 else 0L))
      .addOnFailureListener { /* timeout clears stale typing state */ }
  }

  fun setPresence(value: Boolean) {
    resumed = value
    if (uid.isNotBlank()) writePresence(value)
  }

  private fun writePresence(value: Boolean) {
    val now = System.currentTimeMillis()
    db.document("directory/$uid").set(
      mapOf(
        "isOnline" to (value && _privacy.value.onlineVisibility != "Nobody"),
        "onlineVisible" to (_privacy.value.onlineVisibility != "Nobody"),
        "lastSeenVisible" to (_privacy.value.lastSeenVisibility != "Nobody"),
        "heartbeatAt" to now,
        "lastSeen" to if (_privacy.value.lastSeenVisibility != "Nobody") now else 0L
      ),
      SetOptions.merge()
    )
  }

  fun draft(cid: String) = prefs.getString("draft:$uid:$cid", "").orEmpty()
  fun saveDraft(cid: String, text: String) { prefs.edit().putString("draft:$uid:$cid", text).apply() }

  fun updateProfile(displayName: String, username: String, bio: String, phoneNumber: String) = runAction {
    LiquidApi.call(
      "profile",
      mapOf(
        "displayName" to displayName,
        "username" to username,
        "bio" to bio,
        "phoneNumber" to phoneNumber
      )
    )
  }

  fun updateAppearance(settings: AppearanceSettings) {
    _appearance.value = settings
    appearanceJob?.cancel()
    appearanceJob = scope.launch {
      delay(500)
      save(
        "appearance",
        mapOf(
          "isDarkMode" to settings.isDarkMode,
          "glassIntensity" to settings.glassIntensity,
          "blurAlpha" to settings.blurAlpha,
          "cornerRadiusDp" to settings.cornerRadiusDp,
          "borderStrength" to settings.borderStrength,
          "isReducedMotion" to settings.isReducedMotion
        )
      )
    }
  }

  fun updatePrivacy(settings: PrivacySettings) {
    _privacy.value = settings
    save(
      "privacy",
      mapOf(
        "lastSeenVisibility" to settings.lastSeenVisibility,
        "onlineVisibility" to settings.onlineVisibility,
        "profilePhotoVisibility" to settings.profilePhotoVisibility,
        "readReceipts" to settings.readReceipts
      )
    )
    writePresence(resumed)
  }

  fun updateNotifications(settings: NotificationSettings) {
    _notifications.value = settings
    save("notifications", mapOf("messages" to settings.messages, "vibration" to settings.vibration))
  }

  private fun save(field: String, value: Any) {
    if (uid.isNotBlank()) {
      db.document("users/$uid").set(mapOf(field to value), SetOptions.merge())
        .addOnFailureListener { _error.value = it.message }
    }
  }

  fun blockUser(id: String) {
    _blockedUserIds.update { it + id }
    save("blockedUserIds", _blockedUserIds.value.toList())
  }

  fun unblockUser(id: String) {
    _blockedUserIds.update { it - id }
    save("blockedUserIds", _blockedUserIds.value.toList())
  }

  fun report(id: String, reason: String) = runAction {
    LiquidApi.call("report", mapOf("otherUid" to id, "reason" to reason))
    _error.value = "Report submitted"
  }

  suspend fun deleteAccount(): Result<Unit> = runCatching {
    LiquidApi.call("deleteAccount")
    logout()
  }

  fun addSearchHistory(query: String) {
    if (query.isNotBlank()) {
      _searchHistory.update { (listOf(query) + it.filterNot { old -> old == query }).take(8) }
    }
  }

  fun clearSearchHistory() { _searchHistory.value = emptyList() }
}
