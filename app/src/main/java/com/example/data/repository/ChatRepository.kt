package com.example.data.repository

import android.net.Uri
import com.example.data.DeletionCoordinator
import com.example.notifications.ReceiptWorker
import com.example.notifications.VisibleConversation
import androidx.work.WorkManager
import com.example.data.model.*
import com.example.data.network.LiquidApi
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.util.UUID

/**
 * Firestore snapshots are read-only message truth; authenticated server actions serialize
 * mutations. Snapshot decoding is deliberately tolerant of legacy Number/Timestamp/String data.
 * Permanent deletion is server-backed: message hiddenFor, global tombstones and per-user
 * deletedBefore cutoffs are all respected before anything reaches UI/search/cache.
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
  private val limits = mutableMapOf<String, Long>()
  private val deletedBefore = mutableMapOf<String, Long>()
  private val failed = mutableSetOf<String>()
  private val receipts = mutableSetOf<String>()
  private val lastTyping = mutableMapOf<String, Long>()
  private val legacyDeleteMigrations = mutableSetOf<String>()
  // Keeps enough peer identity to compose a fresh message without putting a
  // server-deleted conversation back into the chats list before a new send.
  private val pendingPeers = mutableMapOf<String, User>()
  private val sending = kotlinx.coroutines.sync.Mutex()

  private var heartbeat: Job? = null
  private var outboxJob: Job? = null
  private var appearanceJob: Job? = null
  private var uploadJob: Job? = null
  private var retryUpload: (() -> Unit)? = null
  private var resumed = false

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

  init {
    if (isUserLoggedIn()) startSync() else _loading.value = false
  }

  fun clearError() { _error.value = null }
  fun isUserLoggedIn() = auth.currentUser != null

  private fun friendlyError(t: Throwable, fallback: String = "Operation failed"): String {
    val message = t.message.orEmpty()
    return when {
      message.contains("PERMISSION_DENIED", true) || message.contains("insufficient permissions", true) ->
        "Sync permission denied. Publish the latest Firestore rules, then reopen Liquid Chat."
      message.contains("UNAVAILABLE", true) || message.contains("network", true) || t is java.io.IOException ->
        "Connection unavailable. Your pending messages will retry when the network returns."
      message.contains("token", true) && message.contains("expired", true) ->
        "Your session needs refreshing. Please sign in again."
      message.contains("File not available", true) || message.contains("Media access denied", true) ->
        "This media is no longer available."
      message.isNotBlank() -> message
      else -> fallback
    }
  }

  private fun reportSnapshotFailure(area: String, t: Throwable) {
    _error.value = "$area: ${friendlyError(t, "could not be loaded")}"
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

  private fun safeWaveform(value: Any?): List<Float> = when (value) {
    is List<*> -> value.mapNotNull { (it as? Number)?.toFloat() }
    else -> emptyList()
  }.map { it.coerceIn(0.05f, 1f) }.take(80)

  private fun jsonWaveform(array: JSONArray?): List<Float> {
    if (array == null) return emptyList()
    return (0 until minOf(array.length(), 80)).mapNotNull { index ->
      runCatching { array.getDouble(index).toFloat().coerceIn(0.05f, 1f) }.getOrNull()
    }
  }

  private fun DocumentSnapshot.safeString(name: String, default: String = ""): String =
    when (val value = get(name)) {
      is String -> value
      null -> default
      else -> value.toString()
    }

  private fun DocumentSnapshot.safeLong(name: String, default: Long = 0L): Long = anyLong(get(name), default)
  private fun DocumentSnapshot.safeBoolean(name: String, default: Boolean = false): Boolean = anyBoolean(get(name), default)

  private fun runAction(block: suspend () -> Unit) {
    scope.launch {
      runCatching { block() }.onFailure {
        if (it !is CancellationException) _error.value = friendlyError(it)
      }
    }
  }

  suspend fun registerWithEmail(
    email: String,
    pass: String,
    fullName: String,
    username: String,
    phoneNumber: String
  ): Result<User> = runCatching {
    auth.createUserWithEmailAndPassword(email.trim(), pass).await()
    LiquidApi.call("profile", mapOf(
      "displayName" to fullName,
      "username" to username,
      "phoneNumber" to phoneNumber
    ))
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
    WorkManager.getInstance(LiquidApi.context).cancelAllWorkByTag("receipts:$uid")
    VisibleConversation.id = null
    DeletionCoordinator.clear()
    setPresence(false)
    val account = uid
    val tokenKey = prefs.getString("deviceId", "").orEmpty()
    if (account.isNotBlank() && tokenKey.isNotBlank()) {
      db.document("users/$account").update("tokens.$tokenKey", FieldValue.delete())
    }
    stopSync()
    appearanceJob?.cancel()
    uploadJob?.cancel()
    retryUpload = null
    LiquidApi.clear()
    java.io.File(LiquidApi.context.filesDir, "voice-drafts").deleteRecursively()
    prefs.edit().clear().apply()
    auth.signOut()
    deletedBefore.clear()
    legacyDeleteMigrations.clear()
    pendingPeers.clear()
    _currentUser.value = User()
    _users.value = emptyList()
    _conversations.value = emptyList()
    _messages.value = emptyMap()
    _blockedUserIds.value = emptySet()
    _appearance.value = AppearanceSettings()
    _privacy.value = PrivacySettings()
    _notifications.value = NotificationSettings()
    _searchHistory.value = emptyList()
    _loading.value = false
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
    val account = uid
    if (account.isBlank()) {
      _loading.value = false
      return
    }
    _loading.value = true
    _currentUser.value = User(uid = account, email = auth.currentUser?.email.orEmpty())
    runAction { LiquidApi.call("profile") }

    val device = prefs.getString("deviceId", null)
      ?: UUID.randomUUID().toString().replace("-", "").also {
        prefs.edit().putString("deviceId", it).apply()
      }

    FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
      if (uid == account) {
        db.document("users/$account")
          .set(mapOf("tokens" to mapOf(device to token)), SetOptions.merge())
          .addOnFailureListener { runAction { LiquidApi.call("profile") } }
      }
    }

    listeners += db.document("users/$account").addSnapshotListener { snapshot, error ->
      if (error != null) {
        _error.value = friendlyError(error)
        return@addSnapshotListener
      }
      if (snapshot == null || !snapshot.exists()) return@addSnapshotListener
      guardSnapshot("Profile") {
        _currentUser.value = toUser(snapshot)
        _blockedUserIds.value = (snapshot.get("blockedUserIds") as? List<*>)
          ?.filterIsInstance<String>()?.toSet().orEmpty()

        (snapshot.get("appearance") as? Map<*, *>)?.let { a ->
          _appearance.value = AppearanceSettings(
            isDarkMode = anyBoolean(a["isDarkMode"], false),
            glassIntensity = (a["glassIntensity"] as? Number)?.toFloat() ?: 0.85f,
            blurAlpha = (a["blurAlpha"] as? Number)?.toFloat() ?: 0.70f,
            cornerRadiusDp = (a["cornerRadiusDp"] as? Number)?.toFloat() ?: 32f,
            borderStrength = (a["borderStrength"] as? Number)?.toFloat() ?: 0.70f,
            accentColorHex = a["accentColorHex"] as? String ?: "#176BFF",
            isReducedMotion = anyBoolean(a["isReducedMotion"], false)
          )
        }

        (snapshot.get("privacy") as? Map<*, *>)?.let { p ->
          _privacy.value = PrivacySettings(
            p["lastSeenVisibility"] as? String ?: "Everyone",
            p["onlineVisibility"] as? String ?: "Everyone",
            p["profilePhotoVisibility"] as? String ?: "Everyone",
            anyBoolean(p["readReceipts"], true)
          )
        }

        (snapshot.get("notifications") as? Map<*, *>)?.let { n ->
          _notifications.value = NotificationSettings(
            messages = anyBoolean(n["messages"], true),
            vibration = anyBoolean(n["vibration"], true),
            showPreview = anyBoolean(n["showPreview"], true)
          )
        }
        prefs.edit().putBoolean("notifications", _notifications.value.messages).apply()
      }
    }

    listeners += db.collection("directory").limit(200).addSnapshotListener { snapshot, error ->
      if (error != null) {
        _error.value = friendlyError(error)
        return@addSnapshotListener
      }
      if (snapshot != null) guardSnapshot("Contacts") {
        _users.value = snapshot.documents.mapNotNull { runCatching { toUser(it) }.getOrNull() }
          .filter { it.uid != account }
        refreshUsers()
      }
    }

    listeners += db.collection("conversations")
      .whereArrayContains("participantIds", account)
      .addSnapshotListener { snapshot, error ->
        _loading.value = false
        if (error != null) {
          _error.value = friendlyError(error)
          return@addSnapshotListener
        }
        if (snapshot != null) guardSnapshot("Chats") {
          if (snapshot.isEmpty && snapshot.metadata.isFromCache && _conversations.value.isNotEmpty()) return@guardSnapshot
          val next = snapshot.documents.mapNotNull { runCatching { toConversation(it) }.getOrNull() }
            .sortedByDescending { it.lastMessageTime }
          _conversations.value = next
          val active = next.map { it.id }.toSet()
          messageListeners.keys.filter { it !in active }.toList().forEach {
            messageListeners.remove(it)?.remove()
            presenceListeners.remove(it)?.remove()
          }
          _messages.update { map -> map.filter { (cid, messages) -> cid in active || messages.any { it.status == MessageDeliveryStatus.SENDING || it.status == MessageDeliveryStatus.FAILED } } }
          // Message + typing listeners are attached only when a conversation is opened.
          // The chat list needs only the lightweight conversation-summary listener.
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
        if (resumed && ticks++ % 60 == 0) writePresence(true)
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
      isOnline = snapshot.safeBoolean("isOnline") && System.currentTimeMillis() - heartbeatAt < 90_000,
      lastSeen = snapshot.safeLong("lastSeen"),
      lastActiveAt = heartbeatAt,
      onlineVisible = snapshot.safeBoolean("onlineVisible", true),
      lastSeenVisible = snapshot.safeBoolean("lastSeenVisible", true)
    )
  }

  private fun toConversation(snapshot: DocumentSnapshot): Conversation? {
    val ids = (snapshot.get("participantIds") as? List<*>)?.filterIsInstance<String>().orEmpty()
    if (ids.size != 2) return null
    val cutoffs = snapshot.get("deletedBefore") as? Map<*, *>
    deletedBefore[snapshot.id] = anyLong(cutoffs?.get(uid), 0L)
    if ((snapshot.get("deletedFor") as? List<*>)?.contains(uid) == true) return null
    val other = ids.firstOrNull { it != uid } ?: return null
    val user = _users.value.find { it.uid == other } ?: User(uid = other, displayName = "Contact")
    pendingPeers[snapshot.id] = user
    fun flag(name: String) = (snapshot.get(name) as? List<*>)?.contains(uid) == true
    val lastId = snapshot.safeString("lastMessageId")
    val hiddenLast = (snapshot.get("hiddenLastFor") as? Map<*, *>)?.get(uid) as? String
    val preview = if (lastId.isNotBlank() && hiddenLast == lastId) "" else snapshot.safeString("lastMessageText")
    return Conversation(
      id = snapshot.id,
      participantIds = ids,
      otherUser = user,
      lastMessageText = preview,
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
      users.map { user -> user.copy(isOnline = user.isOnline && System.currentTimeMillis() - user.lastActiveAt < 90_000) }
    }
    _conversations.update { conversations ->
      conversations.map { conversation ->
        val user = _users.value.find { it.uid == conversation.otherUser.uid } ?: conversation.otherUser
        val online = user.isOnline && System.currentTimeMillis() - user.lastActiveAt < 90_000
        conversation.copy(otherUser = user.copy(isOnline = online), isOnline = online)
      }
    }
  }

  private fun observePresence(cid: String) {
    if (presenceListeners.containsKey(cid)) return
    presenceListeners[cid] = db.collection("conversations/$cid/typing").addSnapshotListener { snapshot, error ->
      if (error != null) return@addSnapshotListener
      guardSnapshot("Typing") {
        val other = _conversations.value.find { it.id == cid }?.otherUser?.uid
        val until = snapshot?.documents?.firstOrNull { it.id == other }?.safeLong("until") ?: 0L
        _conversations.update { conversations ->
          conversations.map {
            if (it.id == cid) it.copy(typingUntil = until, isTyping = until > System.currentTimeMillis()) else it
          }
        }
      }
    }
  }

  private fun refreshTyping() {
    _conversations.update { conversations -> conversations.map { it.copy(isTyping = it.typingUntil > System.currentTimeMillis()) } }
  }

  fun observeConversation(cid: String) {
    // Keep at most one heavy message/typing realtime stream active. Conversation summaries
    // remain realtime through the lightweight chat-list listener.
    messageListeners.keys.filter { it != cid }.toList().forEach { key ->
      messageListeners.remove(key)?.remove()
    }
    presenceListeners.keys.filter { it != cid }.toList().forEach { key ->
      presenceListeners.remove(key)?.remove()
    }
    observePresence(cid)
    if (messageListeners.containsKey(cid)) return
    val limit = limits.getOrPut(cid) { 60 }
    messageListeners[cid] = db.collection("conversations/$cid/messages")
      .orderBy("createdAt", Query.Direction.DESCENDING)
      .limit(limit)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          _error.value = friendlyError(error)
          return@addSnapshotListener
        }
        if (snapshot != null) guardSnapshot("Messages") {
          if (snapshot.isEmpty && snapshot.metadata.isFromCache && _messages.value[cid].orEmpty().isNotEmpty()) return@guardSnapshot
          val list = snapshot.documents
            .mapNotNull { doc -> runCatching { toMessage(cid, doc) }.getOrNull() }
            .sortedWith(compareBy<Message> { it.createdAt }.thenBy { it.id })
          val pending = _messages.value[cid].orEmpty().filter {
            it.status == MessageDeliveryStatus.SENDING || it.status == MessageDeliveryStatus.FAILED
          }.filter { p -> list.none { saved -> saved.id == p.id } }
          _messages.update { it + (cid to (list + pending).sortedWith(compareBy<Message> { m -> m.createdAt }.thenBy { m -> m.id })) }
          list.filter { it.senderId != uid && it.status == MessageDeliveryStatus.SENT }
            .forEach { receipt(cid, it.id, "DELIVERED") }
        }
      }
  }

  fun loadOlder(cid: String) {
    limits[cid] = (limits[cid] ?: 60) + 60
    messageListeners.remove(cid)?.remove()
    observeConversation(cid)
  }

  private fun toMessage(cid: String, snapshot: DocumentSnapshot): Message? {
    if (snapshot.safeBoolean("deletedForEveryone")) return null
    if ((snapshot.get("hiddenFor") as? List<*>)?.contains(uid) == true) return null

    val legacyKey = "hidden:$uid:${snapshot.id}"
    if (prefs.getBoolean(legacyKey, false)) {
      val migration = "$cid:${snapshot.id}"
      if (legacyDeleteMigrations.add(migration)) {
        scope.launch {
          runCatching { LiquidApi.call("deleteForMe", mapOf("conversationId" to cid, "messageId" to snapshot.id)) }
            .onSuccess { prefs.edit().remove(legacyKey).apply() }
            .onFailure { legacyDeleteMigrations.remove(migration) }
        }
      }
      return null
    }

    val createdAt = snapshot.safeLong("createdAt")
    val cutoff = deletedBefore[cid] ?: 0L
    if (cutoff > 0 && createdAt <= cutoff) return null
    val rawExpires = snapshot.get("expiresAt")
    val expires = if (rawExpires == null) null else anyLong(rawExpires, 0L).takeIf { it > 0L }
    if (expires != null && expires <= System.currentTimeMillis()) return null

    return Message(
      id = snapshot.id,
      conversationId = cid,
      senderId = snapshot.safeString("senderId"),
      senderName = snapshot.safeString("senderName"),
      text = snapshot.safeString("text"),
      type = runCatching { MessageType.valueOf(snapshot.safeString("type", "TEXT")) }.getOrDefault(MessageType.TEXT),
      mediaUrl = snapshot.safeString("mediaUrl"),
      voiceDurationSeconds = snapshot.safeLong("voiceDurationSeconds").toInt().coerceAtLeast(0),
      waveform = safeWaveform(snapshot.get("waveform")),
      seenByMe = (snapshot.get("seenBy") as? List<*>)?.contains(uid) == true,
      createdAt = createdAt,
      status = runCatching { MessageDeliveryStatus.valueOf(snapshot.safeString("status", "SENT")) }
        .getOrDefault(MessageDeliveryStatus.SENT),
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
    _messages.update { map -> map.mapValues { (_, messages) -> messages.filter { it.expiresAt == null || it.expiresAt > now } } }
  }

  fun getOrCreateConversationId(otherUid: String): String {
    val cid = listOf(uid, otherUid).sorted().joinToString("_")
    pendingPeers[cid] = _users.value.find { user -> user.uid == otherUid }
      ?: User(uid = otherUid, displayName = "Contact")
    // Do not optimistically insert a conversation into _conversations. If this chat
    // was deleted for the current account, opening the contact must not resurrect
    // the row/history. The authenticated send action will start a fresh chat.
    runAction {
      LiquidApi.call("conversation", mapOf("otherUid" to otherUid))
      observeConversation(cid)
      observePresence(cid)
    }
    return cid
  }

  fun peerForConversation(cid: String): User? = pendingPeers[cid]

  fun sendMessage(
    conversationId: String,
    text: String,
    type: MessageType = MessageType.TEXT,
    mediaUrl: String = "",
    replyToId: String? = null,
    replyToText: String? = null,
    replyToSender: String? = null,
    voiceDurationSeconds: Int = 0,
    waveform: List<Float> = emptyList(),
    localVoicePath: String? = null
  ) {
    if (text.isBlank() && mediaUrl.isBlank()) return
    val cleanWaveform = waveform.map { it.coerceIn(0.05f, 1f) }.take(80)
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
      voiceDurationSeconds = voiceDurationSeconds.coerceAtLeast(0),
      waveform = cleanWaveform,
      status = MessageDeliveryStatus.SENDING
    )
    _messages.update { it + (conversationId to (it[conversationId].orEmpty() + message)) }
    persist(message, localVoicePath)
    scope.launch { flushOutbox() }
    setTyping(conversationId, false)
  }

  private fun json(message: Message) = JSONObject(mapOf(
    "otherUid" to (
      _conversations.value.find { it.id == message.conversationId }?.otherUser?.uid
        ?: pendingPeers[message.conversationId]?.uid
    ),
    "id" to message.id,
    "conversationId" to message.conversationId,
    "senderId" to message.senderId,
    "text" to message.text,
    "type" to message.type.name,
    "mediaUrl" to message.mediaUrl,
    "voiceDurationSeconds" to message.voiceDurationSeconds,
    "waveform" to message.waveform,
    "replyToId" to message.replyToId,
    "replyToText" to message.replyToText,
    "replyToSender" to message.replyToSender,
    "createdAt" to message.createdAt
  ))

  private fun persist(message: Message, localVoicePath: String? = null) {
    prefs.edit().putString("outbox:$uid:${message.id}", json(message).put("localVoicePath", localVoicePath).toString()).apply()
  }

  private fun restoreOutbox() {
    prefs.all.filterKeys { it.startsWith("outbox:$uid:") }.values.forEach { raw ->
      runCatching {
        val j = JSONObject(raw as String)
        val cid = j.getString("conversationId")
        val otherUid = j.optString("otherUid").takeIf { it.isNotBlank() }
        if (_conversations.value.none { it.id == cid }) {
          if (otherUid == null) return@runCatching
          pendingPeers[cid] = _users.value.find { it.uid == otherUid }
            ?: User(uid = otherUid, displayName = "Contact")
        }
        val message = Message(
          id = j.getString("id"),
          conversationId = cid,
          senderId = uid,
          text = j.getString("text"),
          type = MessageType.valueOf(j.getString("type")),
          mediaUrl = j.optString("mediaUrl"),
          voiceDurationSeconds = j.optInt("voiceDurationSeconds"),
          waveform = jsonWaveform(j.optJSONArray("waveform")),
          createdAt = j.optLong("createdAt"),
          status = MessageDeliveryStatus.SENDING
        )
        _messages.update {
          it + (cid to (it[cid].orEmpty().filterNot { saved -> saved.id == message.id } + message))
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
        val j = JSONObject(raw as String)
        val id = j.getString("id")
        if (id in failed) continue
        val cid = j.getString("conversationId")
        val knownConversation = _conversations.value.any { it.id == cid }
        val otherUid = j.optString("otherUid").takeIf { it.isNotBlank() }
          ?: pendingPeers[cid]?.uid?.takeIf { it.isNotBlank() }
        if (!knownConversation && otherUid == null) {
          // Corrupt/legacy outbox entry: there is no safe recipient to send to.
          prefs.edit().remove(key).apply()
          continue
        }
        if (!knownConversation && otherUid != null) {
          pendingPeers[cid] = _users.value.find { it.uid == otherUid }
            ?: User(uid = otherUid, displayName = "Contact")
        }
        val data = j.keys().asSequence().associateWith { j.opt(it).takeUnless { value -> value == JSONObject.NULL } }.toMutableMap()
        if (data["otherUid"] == null && otherUid != null) data["otherUid"] = otherUid
        data.remove("localVoicePath")
        try {
          val response = LiquidApi.call("send", data)
          if (response.optBoolean("notificationPending")) com.example.notifications.NotificationRetryWorker.enqueue(LiquidApi.context, uid, cid, id)
          deleteVoiceSource(j.optString("localVoicePath"))
          prefs.edit().remove(key).apply()
          if (response.optBoolean("tombstoned")) removeLocalMessage(cid, id)
          else updateLocal(cid, id) { it.copy(status = acknowledgedStatus(it.status)) }
        } catch (e: Exception) {
          if (e is CancellationException) throw e
          if (e !is java.io.IOException) {
            failed += id
            updateLocal(cid, id) { it.copy(status = MessageDeliveryStatus.FAILED) }
            _error.value = friendlyError(e)
          }
        }
      }
    } finally {
      sending.unlock()
    }
  }

  private fun deleteVoiceSource(path: String) {
    if (path.isBlank()) return
    val root = java.io.File(LiquidApi.context.filesDir, "voice-drafts").canonicalFile
    val file = java.io.File(path).canonicalFile
    if (file.path.startsWith(root.path + java.io.File.separator)) file.delete()
  }

  fun retryMessage(cid: String, id: String) {
    failed -= id
    updateLocal(cid, id) { it.copy(status = MessageDeliveryStatus.SENDING) }
    scope.launch { flushOutbox() }
  }

  suspend fun removeProfilePhoto(): Result<Unit> = runCatching {
    val old = _currentUser.value.photoUrl
    LiquidApi.call("removeProfilePhoto")
    if (old.isNotBlank()) LiquidApi.invalidateMedia(old)
    _currentUser.update { it.copy(photoUrl = "") }
  }

  private fun updateLocal(cid: String, id: String, transform: (Message) -> Message) {
    _messages.update { map -> map + (cid to map[cid].orEmpty().map { if (it.id == id) transform(it) else it }) }
  }

  private fun removeLocalMessage(cid: String, id: String) {
    failed -= id
    receipts.removeAll { it.contains(":$id:") }
    runCatching { deleteVoiceSource(JSONObject(prefs.getString("outbox:$uid:$id", "{}")!!).optString("localVoicePath")) }
    prefs.edit().remove("outbox:$uid:$id").remove("star:$uid:$id").remove("hidden:$uid:$id").apply()
    _messages.update { map -> map + (cid to map[cid].orEmpty().filterNot { it.id == id }) }
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
        if (_conversations.value.none { it.id == cid }) {
          val peer = pendingPeers[cid]?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Contact is not ready yet")
          // Ensure the server-side conversation document exists before uploadBegin.
          // This does not resurrect deleted history; only a subsequent new send unhides the fresh chat.
          LiquidApi.call("conversation", mapOf("otherUid" to peer))
        }
        LiquidApi.upload(uri, cid, type.name) { progress -> scope.launch { _upload.value = progress } }
      }
      _upload.value = null
      if (result.isFailure && result.exceptionOrNull() !is CancellationException) {
        _error.value = result.exceptionOrNull()?.let { friendlyError(it) }
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
      val result = runCatching { LiquidApi.upload(uri, null, "IMAGE") }
      onResult(result)
      result.getOrThrow()
    }
  }

  fun forwardMedia(message: Message, target: String) = runAction {
    LiquidApi.call("forward", mapOf(
      "conversationId" to message.conversationId,
      "messageId" to message.id,
      "targetId" to target,
      "id" to UUID.randomUUID().toString()
    ))
  }

  private fun action(name: String, cid: String, id: String, extra: Map<String, Any?> = emptyMap()) = runAction {
    LiquidApi.call(name, mapOf("conversationId" to cid, "messageId" to id) + extra)
  }

  fun addReaction(cid: String, id: String, emoji: String) = action("react", cid, id, mapOf("emoji" to emoji))

  fun deleteMessageForMe(cid: String, id: String) = runAction {
    DeletionCoordinator.perform("message:$id") {
    val mediaUrl = _messages.value[cid].orEmpty().firstOrNull { it.id == id }?.mediaUrl.orEmpty()
    LiquidApi.call("deleteForMe", mapOf("conversationId" to cid, "messageId" to id))
    if (mediaUrl.isNotBlank()) LiquidApi.invalidateMedia(mediaUrl)
    removeLocalMessage(cid, id)
    }
  }

  fun deleteMessageForEveryone(cid: String, id: String) = runAction {
    DeletionCoordinator.perform("message:$id") {
    val mediaUrl = _messages.value[cid].orEmpty().firstOrNull { it.id == id }?.mediaUrl.orEmpty()
    LiquidApi.call("deleteForEveryone", mapOf("conversationId" to cid, "messageId" to id))
    if (mediaUrl.isNotBlank()) LiquidApi.invalidateMedia(mediaUrl)
    removeLocalMessage(cid, id)
    }
  }

  fun deleteMessage(cid: String, id: String) = deleteMessageForEveryone(cid, id)
  fun hideMessage(cid: String, id: String) = deleteMessageForMe(cid, id)
  fun editMessage(cid: String, id: String, text: String) = action("edit", cid, id, mapOf("text" to text))
  fun pinMessage(cid: String, id: String) = action("pin", cid, id)

  fun starMessage(cid: String, id: String) {
    val next = !prefs.getBoolean("star:$uid:$id", false)
    prefs.edit().putBoolean("star:$uid:$id", next).apply()
    updateLocal(cid, id) { it.copy(isStarred = next) }
  }

  private fun receipt(cid: String, id: String, status: String) {
    if (!receipts.add("$uid:$id:$status")) return
    ReceiptWorker.enqueue(LiquidApi.context, uid, cid, listOf(id), status)
  }

  fun markVisibleRead(cid: String, ids: List<String>) {
    if (VisibleConversation.id != cid) return
    val unseen = _messages.value[cid].orEmpty().filter { it.id in ids && it.senderId != uid && !it.seenByMe }
      .map { it.id }.filter { receipts.add("$uid:$it:READ") }
    ReceiptWorker.enqueue(LiquidApi.context, uid, cid, unseen, "READ")
  }

  // Kept for call sites outside the conversation; only the visible viewport may mark messages read.
  fun clearUnread(cid: String) = Unit

  private fun setting(cid: String, field: String, value: Any) = runAction {
    LiquidApi.call("conversationSetting", mapOf("conversationId" to cid, "field" to field, "value" to value))
  }

  fun setConversationArchived(cid: String, value: Boolean) = setting(cid, "archivedFor", value)
  fun setConversationMuted(cid: String, value: Boolean) = setting(cid, "mutedFor", value)
  fun setFavorite(cid: String, value: Boolean) = setting(cid, "favoriteFor", value)

  fun deleteChatForMe(cid: String) = runAction {
    DeletionCoordinator.perform("chat:$cid") {
    val currentMessages = _messages.value[cid].orEmpty()
    val messageIds = currentMessages.map { it.id }.toSet()
    val mediaUrls = currentMessages.map { it.mediaUrl }.filter { it.isNotBlank() }.distinct()
    LiquidApi.call("deleteChat", mapOf("conversationId" to cid))
    mediaUrls.forEach { LiquidApi.invalidateMedia(it) }
    messageListeners.remove(cid)?.remove()
    presenceListeners.remove(cid)?.remove()
    deletedBefore[cid] = System.currentTimeMillis()
    _conversations.update { list -> list.filterNot { it.id == cid } }
    _messages.update { map -> map - cid }
    val edit = prefs.edit().remove("draft:$uid:$cid").remove("wallpaper:$uid:$cid")
    messageIds.forEach { id -> edit.remove("star:$uid:$id").remove("outbox:$uid:$id").remove("hidden:$uid:$id") }
    prefs.all.filterKeys { it.startsWith("outbox:$uid:") }.forEach { (key, raw) ->
      runCatching {
        val j = JSONObject(raw as String)
        if (j.optString("conversationId") == cid) { deleteVoiceSource(j.optString("localVoicePath")); edit.remove(key) }
      }
    }
    edit.apply()
    }
  }

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
      .addOnFailureListener { }
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
    ).addOnFailureListener { _error.value = it.message }
  }

  fun draft(cid: String) = prefs.getString("draft:$uid:$cid", "").orEmpty()
  fun saveDraft(cid: String, text: String) { prefs.edit().putString("draft:$uid:$cid", text).apply() }

  suspend fun checkUsernameAvailability(username: String): Result<Boolean> = runCatching {
    LiquidApi.call("usernameCheck", mapOf("username" to username.trim().lowercase())).optBoolean("available", false)
  }

  fun updateProfile(displayName: String, username: String, bio: String, phoneNumber: String) = runAction {
    LiquidApi.call("profile", mapOf(
      "displayName" to displayName,
      "username" to username,
      "bio" to bio,
      "phoneNumber" to phoneNumber
    ))
  }

  fun updateAppearance(settings: AppearanceSettings) {
    _appearance.value = settings
    appearanceJob?.cancel()
    appearanceJob = scope.launch {
      delay(500)
      save("appearance", mapOf(
        "isDarkMode" to settings.isDarkMode,
        "glassIntensity" to settings.glassIntensity,
        "blurAlpha" to settings.blurAlpha,
        "cornerRadiusDp" to settings.cornerRadiusDp,
        "borderStrength" to settings.borderStrength,
        "accentColorHex" to settings.accentColorHex,
        "isReducedMotion" to settings.isReducedMotion
      ))
    }
  }

  fun updatePrivacy(settings: PrivacySettings) {
    _privacy.value = settings
    save("privacy", mapOf(
      "lastSeenVisibility" to settings.lastSeenVisibility,
      "onlineVisibility" to settings.onlineVisibility,
      "profilePhotoVisibility" to settings.profilePhotoVisibility,
      "readReceipts" to settings.readReceipts
    ))
    writePresence(resumed)
  }

  fun updateNotifications(settings: NotificationSettings) {
    _notifications.value = settings
    save("notifications", mapOf(
      "messages" to settings.messages,
      "vibration" to settings.vibration,
      "showPreview" to settings.showPreview
    ))
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
