package com.example.data.repository

import android.net.Uri
import com.example.data.crypto.E2eeCrypto
import com.example.data.model.*
import com.example.data.network.LiquidApi
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.util.UUID

/**
 * Firebase Auth + Cloud Firestore are the realtime source of truth.
 * Client mutations are constrained by Firestore security rules. Cloudinary uploads happen
 * directly from Android and Firestore stores only the returned secure media URL.
 * Snapshot decoding remains tolerant of legacy Number/Timestamp/String data.
 */
class ChatRepository(
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {
  private val auth = FirebaseAuth.getInstance()
  private val db = FirebaseFirestore.getInstance()
  private val prefs get() = LiquidApi.context.getSharedPreferences("liquid-private", 0)
  private val installPrefs get() = LiquidApi.context.getSharedPreferences("liquid-install", 0)
  private val uid get() = auth.currentUser?.uid.orEmpty()

  private val listeners = mutableListOf<ListenerRegistration>()
  private val messageListeners = mutableMapOf<String, ListenerRegistration>()
  private val presenceListeners = mutableMapOf<String, ListenerRegistration>()
  private val historyCursors = mutableMapOf<String, DocumentSnapshot>()
  private val historyPagingStarted = mutableSetOf<String>()
  private val deletedBefore = mutableMapOf<String, Long>()
  private val failed = mutableSetOf<String>()
  private val receipts = mutableSetOf<String>()
  private val lastTyping = mutableMapOf<String, Long>()
  private val legacyDeleteMigrations = mutableSetOf<String>()
  private val deleteTombstones = mutableSetOf<String>()
  // Keeps enough peer identity to compose a fresh message without putting a
  // server-deleted conversation back into the chats list before a new send.
  private val pendingPeers = mutableMapOf<String, User>()
  private val sending = kotlinx.coroutines.sync.Mutex()

  private var heartbeat: Job? = null
  private var outboxJob: Job? = null
  private var appearanceJob: Job? = null
  private var syncRecoveryJob: Job? = null
  private val syncRecoveryAttempts = mutableMapOf<String, Int>()
  private data class QueuedMediaUpload(
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val uri: Uri,
    val type: MessageType,
    val onResult: (Result<String>) -> Unit
  )

  private var uploadJob: Job? = null
  private val uploadQueue = ArrayDeque<QueuedMediaUpload>()
  private var activeUpload: QueuedMediaUpload? = null
  private var failedUpload: QueuedMediaUpload? = null
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
  private val _syncWarning = MutableStateFlow<String?>(null)
  val syncWarning = _syncWarning.asStateFlow()
  private val _loading = MutableStateFlow(true)
  val loading = _loading.asStateFlow()
  private val _upload = MutableStateFlow<Float?>(null)
  val upload = _upload.asStateFlow()
  private val _historyHasOlder = MutableStateFlow<Map<String, Boolean>>(emptyMap())
  val historyHasOlder = _historyHasOlder.asStateFlow()
  private val _historyLoading = MutableStateFlow<Map<String, Boolean>>(emptyMap())
  val historyLoading = _historyLoading.asStateFlow()

  init {
    if (isUserLoggedIn()) {
      scope.launch {
        val ready = runCatching {
          enforceInstallationPrivacy()
          true
        }.getOrElse {
          _error.value = "Secure reinstall cleanup failed: " + friendlyError(it)
          false
        }
        if (ready) {
          startSync()
        } else {
          auth.signOut()
          _loading.value = false
        }
      }
    } else {
      _loading.value = false
    }
  }

  fun clearError() { _error.value = null }
  fun isUserLoggedIn() = auth.currentUser != null

  private fun friendlyError(t: Throwable, fallback: String = "Operation failed"): String {
    val message = t.message.orEmpty()
    return when {
      message.contains("PERMISSION_DENIED", true) || message.contains("insufficient permissions", true) ->
        "Firebase access was denied. Liquid Chat will retry automatically."
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
    _syncWarning.value = "$area: ${friendlyError(t, "could not be loaded")}"
  }

  private fun markSyncHealthy(area: String) {
    syncRecoveryAttempts.remove(area)
    if (_syncWarning.value?.startsWith("$area:") == true) {
      _syncWarning.value = null
    }
  }

  private fun scheduleSyncRecovery(area: String, t: Throwable) {
    reportSnapshotFailure(area, t)
    val permissionLike = t is FirebaseFirestoreException &&
      (t.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
        t.code == FirebaseFirestoreException.Code.UNAUTHENTICATED)
    val attempts = syncRecoveryAttempts[area] ?: 0
    if (!permissionLike || attempts >= 2 || syncRecoveryJob?.isActive == true) return

    val account = uid
    val nextAttempt = attempts + 1
    syncRecoveryAttempts[area] = nextAttempt
    syncRecoveryJob = scope.launch {
      runCatching { auth.currentUser?.getIdToken(true)?.await() }
      delay(750L * nextAttempt)
      if (uid == account && account.isNotBlank()) startSync()
    }
  }

  private inline fun guardSnapshot(area: String, block: () -> Unit) {
    runCatching(block)
      .onSuccess { markSyncHealthy(area) }
      .onFailure { reportSnapshotFailure(area, it) }
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

  private fun installationId(): String {
    return installPrefs.getString("installationId", null)
      ?: UUID.randomUUID().toString().replace("-", "").also { id ->
        installPrefs.edit().putString("installationId", id).apply()
      }
  }

  private suspend fun ensureE2eeIdentityPublished() {
    val account = uid
    if (account.isBlank()) return
    val identity = E2eeCrypto.ensureIdentity(LiquidApi.context, account)
    val fields = mapOf(
      "e2eePublicKey" to identity.publicKey,
      "e2eeKeyId" to identity.keyId
    )
    db.document("users/$account").set(fields, SetOptions.merge()).await()
    db.document("directory/$account").set(fields, SetOptions.merge()).await()
  }

  private suspend fun enforceInstallationPrivacy() {
    val account = uid
    if (account.isBlank()) return
    db.document("users/$account")
      .set(mapOf("installationId" to installationId()), SetOptions.merge())
      .await()
  }

  private fun finishLocalLogout() {
    val account = uid
    stopSync()
    appearanceJob?.cancel()
    uploadJob?.cancel()
    uploadQueue.clear()
    activeUpload = null
    failedUpload = null
    retryUpload = null
    LiquidApi.clear()
    prefs.edit().clear().apply()
    if (account.isNotBlank()) E2eeCrypto.deleteIdentity(LiquidApi.context, account)
    auth.signOut()
    deletedBefore.clear()
    legacyDeleteMigrations.clear()
    deleteTombstones.clear()
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
    _error.value = null
    _syncWarning.value = null
    syncRecoveryAttempts.clear()
    _loading.value = false
  }

  private suspend fun updateProfileDirect(
    displayName: String,
    username: String,
    bio: String = "",
    phoneNumber: String = ""
  ) {
    val account = uid
    require(account.isNotBlank()) { "Please sign in again" }

    val cleanName = displayName.trim().ifBlank { "User" }.take(60)
    val cleanUsername = username.trim().lowercase()
    require(Regex("^[a-z0-9_.]{3,32}$").matches(cleanUsername)) {
      "Username must have 3–32 letters, numbers, dots or underscores"
    }
    val cleanBio = bio.trim().take(160)
    val cleanPhone = phoneNumber.trim().take(30)
    val ownRef = db.document("users/$account")
    val directoryRef = db.document("directory/$account")
    val usernameRef = db.document("usernames/$cleanUsername")
    val email = auth.currentUser?.email.orEmpty()
    val now = System.currentTimeMillis()

    db.runTransaction { tx ->
      val own = tx.get(ownRef)
      val reserved = tx.get(usernameRef)
      val previousUsername = own.safeString("username").trim().lowercase()
      val previousRef = previousUsername
        .takeIf { it.isNotBlank() && it != cleanUsername }
        ?.let { db.document("usernames/$it") }
      val previousReservation = previousRef?.let { tx.get(it) }

      if (reserved.exists() && reserved.safeString("uid") != account) {
        error("Username already taken")
      }

      tx.set(usernameRef, mapOf("uid" to account))
      if (
        previousRef != null &&
        previousReservation?.exists() == true &&
        previousReservation.safeString("uid") == account
      ) {
        tx.delete(previousRef)
      }

      val createdAt = own.safeLong("createdAt", now).takeIf { it > 0L } ?: now
      tx.set(
        ownRef,
        mapOf(
          "uid" to account,
          "displayName" to cleanName,
          "username" to cleanUsername,
          "bio" to cleanBio,
          "email" to email,
          "phoneNumber" to cleanPhone,
          "createdAt" to createdAt
        ),
        SetOptions.merge()
      )
      tx.set(
        directoryRef,
        mapOf(
          "uid" to account,
          "displayName" to cleanName,
          "username" to cleanUsername,
          "bio" to cleanBio,
          "createdAt" to createdAt
        ),
        SetOptions.merge()
      )
    }.await()
  }

  suspend fun registerWithEmail(
    email: String,
    pass: String,
    fullName: String,
    username: String,
    phoneNumber: String
  ): Result<User> = runCatching {
    val created = auth.createUserWithEmailAndPassword(email.trim(), pass).await()
    try {
      updateProfileDirect(
        displayName = fullName,
        username = username,
        phoneNumber = phoneNumber
      )
    } catch (t: Throwable) {
      runCatching { created.user?.delete()?.await() }
      throw t
    }
    db.document("users/$uid")
      .update("installationId", installationId())
      .await()
    auth.currentUser?.sendEmailVerification()?.await()
    startSync()
    _currentUser.value
  }

  suspend fun signInWithEmail(email: String, pass: String): Result<User> = runCatching {
    auth.signInWithEmailAndPassword(email.trim(), pass).await()
    runCatching { enforceInstallationPrivacy() }
      .onFailure { _syncWarning.value = "Session metadata: " + friendlyError(it) }
    startSync()
    _currentUser.value
  }

  suspend fun signInWithGoogleIdToken(idToken: String): Result<User> = runCatching {
    require(idToken.isNotBlank()) { "Google sign-in did not return an ID token" }

    val authResult = auth.signInWithCredential(
      GoogleAuthProvider.getCredential(idToken, null)
    ).await()
    val firebaseUser = authResult.user ?: error("Google sign-in could not load the Firebase user")

    val userRef = db.document("users/${firebaseUser.uid}")
    val existingProfile = userRef.get().await()
    if (!existingProfile.exists()) {
      val emailPrefix = firebaseUser.email
        .orEmpty()
        .substringBefore('@')
        .lowercase()
        .replace(Regex("[^a-z0-9_.]"), "")
        .trim('.', '_')
      val safeBase = emailPrefix.ifBlank { "user" }.take(22)
      val generatedUsername = "${safeBase}_${firebaseUser.uid.take(6).lowercase()}".take(32)

      try {
        updateProfileDirect(
          displayName = firebaseUser.displayName.orEmpty().ifBlank { "User" },
          username = generatedUsername,
          phoneNumber = ""
        )
      } catch (t: Throwable) {
        // A Google account has already been authenticated. Avoid leaving a half-initialized
        // session if profile bootstrap fails.
        auth.signOut()
        throw t
      }
    }

    runCatching { enforceInstallationPrivacy() }
      .onFailure { _syncWarning.value = "Session metadata: " + friendlyError(it) }
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

  fun logout(onResult: (Result<Unit>) -> Unit = {}) {
    setPresence(false)
    LiquidApi.clearMediaCachesOnly()
    finishLocalLogout()
    onResult(Result.success(Unit))
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
    historyCursors.clear()
    historyPagingStarted.clear()
    _historyHasOlder.value = emptyMap()
    _historyLoading.value = emptyMap()
    heartbeat?.cancel()
    outboxJob?.cancel()
    syncRecoveryJob?.cancel()
    syncRecoveryJob = null
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

    scope.launch {
      runCatching { ensureE2eeIdentityPublished() }
        .onFailure { scheduleSyncRecovery("Encryption identity", it) }
    }

    listeners += db.document("users/$account").addSnapshotListener { snapshot, error ->
      if (error != null) {
        scheduleSyncRecovery("Profile", error)
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
        scheduleSyncRecovery("Contacts", error)
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
          scheduleSyncRecovery("Chats", error)
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
          _messages.update { map -> map.filterKeys { it in active } }
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
      lastSeenVisible = snapshot.safeBoolean("lastSeenVisible", true),
      e2eePublicKey = snapshot.safeString("e2eePublicKey"),
      e2eeKeyId = snapshot.safeString("e2eeKeyId")
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
    // Keep a fixed recent-message realtime window. Older pages are loaded with one-shot
    // queries and merged into the same state, so loading history never tears down/restarts
    // the listener and the visible conversation does not flicker or reload.
    messageListeners.keys.filter { it != cid }.toList().forEach { key ->
      messageListeners.remove(key)?.remove()
    }
    presenceListeners.keys.filter { it != cid }.toList().forEach { key ->
      presenceListeners.remove(key)?.remove()
    }
    observePresence(cid)
    if (messageListeners.containsKey(cid)) return

    val recentLimit = 60L
    messageListeners[cid] = db.collection("conversations/$cid/messages")
      .orderBy("createdAt", Query.Direction.DESCENDING)
      .limit(recentLimit + 1)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          _historyLoading.update { it + (cid to false) }
          scheduleSyncRecovery("Messages", error)
          return@addSnapshotListener
        }
        if (snapshot != null) guardSnapshot("Messages") {
          val pageDocuments = snapshot.documents.take(recentLimit.toInt())
          val extraDocument = snapshot.documents.getOrNull(recentLimit.toInt())
          if (cid !in historyPagingStarted) {
            pageDocuments.lastOrNull()?.let { historyCursors[cid] = it }
          }

          val cutoff = deletedBefore[cid] ?: 0L
          val extraCreatedAt = extraDocument?.safeLong("createdAt") ?: 0L
          if (cid !in historyPagingStarted) {
            _historyHasOlder.update {
              it + (cid to (
                extraDocument != null &&
                  (cutoff <= 0L || extraCreatedAt > cutoff)
              ))
            }
          }
          _historyLoading.update { it + (cid to false) }

          val recent = pageDocuments
            .mapNotNull { doc -> runCatching { toMessage(cid, doc) }.getOrNull() }
            .filterNot { message -> (cid + ":" + message.id) in deleteTombstones }
            .sortedBy { it.createdAt }

          val recentIds = recent.asSequence().map { it.id }.toSet()
          val oldestRecentTime = recent.firstOrNull()?.createdAt ?: Long.MAX_VALUE
          val existing = _messages.value[cid].orEmpty()

          // Preserve already-loaded older pages when the fixed recent window shifts because
          // a new realtime message arrived. This prevents old rows disappearing/reappearing.
          val carriedOlder = existing.filter { message ->
            message.status != MessageDeliveryStatus.SENDING &&
              message.status != MessageDeliveryStatus.FAILED &&
              message.id !in recentIds &&
              message.createdAt <= oldestRecentTime &&
              (cid + ":" + message.id) !in deleteTombstones
          }

          val pending = existing.filter {
            it.status == MessageDeliveryStatus.SENDING ||
              it.status == MessageDeliveryStatus.FAILED
          }

          val merged = (carriedOlder + recent + pending)
            .associateBy { it.id }
            .values
            .sortedBy { it.createdAt }

          _messages.update { it + (cid to merged) }
          recent.filter { it.senderId != uid && it.status == MessageDeliveryStatus.SENT }
            .forEach { receipt(cid, it.id, "DELIVERED") }
        }
      }
  }

  fun loadOlder(cid: String) {
    if (_historyLoading.value[cid] == true) return
    if (_historyHasOlder.value[cid] == false) return
    val cursor = historyCursors[cid] ?: run {
      _historyHasOlder.update { it + (cid to false) }
      return
    }

    _historyLoading.update { it + (cid to true) }
    historyPagingStarted += cid

    scope.launch {
      try {
        val pageSize = 60L
        val snapshot = db.collection("conversations/$cid/messages")
          .orderBy("createdAt", Query.Direction.DESCENDING)
          .startAfter(cursor)
          .limit(pageSize + 1)
          .get()
          .await()

        val pageDocuments = snapshot.documents.take(pageSize.toInt())
        val extraDocument = snapshot.documents.getOrNull(pageSize.toInt())
        pageDocuments.lastOrNull()?.let { historyCursors[cid] = it }

        val cutoff = deletedBefore[cid] ?: 0L
        val extraCreatedAt = extraDocument?.safeLong("createdAt") ?: 0L
        val hasOlder = extraDocument != null &&
          (cutoff <= 0L || extraCreatedAt > cutoff)
        _historyHasOlder.update { it + (cid to hasOlder) }

        val older = pageDocuments
          .mapNotNull { doc -> runCatching { toMessage(cid, doc) }.getOrNull() }
          .filterNot { message -> (cid + ":" + message.id) in deleteTombstones }

        val merged = (_messages.value[cid].orEmpty() + older)
          .associateBy { it.id }
          .values
          .sortedBy { it.createdAt }

        _messages.update { it + (cid to merged) }
        older.filter { it.senderId != uid && it.status == MessageDeliveryStatus.SENT }
          .forEach { receipt(cid, it.id, "DELIVERED") }
      } catch (t: Throwable) {
        if (t !is CancellationException) {
          reportSnapshotFailure("History", t)
        }
      } finally {
        _historyLoading.update { it + (cid to false) }
      }
    }
  }

  private fun toMessage(cid: String, snapshot: DocumentSnapshot): Message? {
    if (snapshot.safeBoolean("deletedForEveryone")) return null
    if ((snapshot.get("hiddenFor") as? List<*>)?.contains(uid) == true) return null

    val legacyKey = "hidden:$uid:${snapshot.id}"
    if (prefs.getBoolean(legacyKey, false)) {
      val migration = "$cid:${snapshot.id}"
      if (legacyDeleteMigrations.add(migration)) {
        scope.launch {
          runCatching { hideMessageDirect(cid, snapshot.id) }
            .onSuccess { prefs.edit().remove(legacyKey).apply() }
            .onFailure { legacyDeleteMigrations.remove(migration) }
        }
      }
      return null
    }

    val createdAt = snapshot.safeLong("createdAt")
    val senderId = snapshot.safeString("senderId")
    val expectedSenderKeyId = if (senderId == uid) {
      E2eeCrypto.ensureIdentity(LiquidApi.context, uid).keyId
    } else {
      _users.value.find { it.uid == senderId }?.e2eeKeyId.orEmpty()
    }
    val encryptedMap = (snapshot.get("e2ee") as? Map<*, *>)
      ?.entries
      ?.filter { it.key is String }
      ?.associate { it.key as String to it.value }

    val decrypted = encryptedMap?.let { fields ->
      E2eeCrypto.decryptText(
        context = LiquidApi.context,
        uid = uid,
        senderId = senderId,
        conversationId = cid,
        messageId = snapshot.id,
        fields = fields,
        expectedSenderKeyId = expectedSenderKeyId
      )
    }?.let { raw ->
      runCatching { JSONObject(raw) }.getOrNull()
    }

    val decryptedText = decrypted?.optString("text")
      ?.takeIf { it.isNotBlank() }
    val encryptedUnavailable = encryptedMap != null && decrypted == null

    val cutoff = deletedBefore[cid] ?: 0L
    if (cutoff > 0 && createdAt <= cutoff) return null
    val rawExpires = snapshot.get("expiresAt")
    val expires = if (rawExpires == null) null else anyLong(rawExpires, 0L).takeIf { it > 0L }
    if (expires != null && expires <= System.currentTimeMillis()) return null

    val readableMediaUrl = snapshot.safeString("mediaUrl")

    return Message(
      id = snapshot.id,
      conversationId = cid,
      senderId = senderId,
      senderName = snapshot.safeString("senderName"),
      text = when {
        decryptedText != null -> decryptedText
        encryptedUnavailable -> "🔒 Encrypted message unavailable"
        else -> snapshot.safeString("text")
      },
      type = runCatching { MessageType.valueOf(snapshot.safeString("type", "TEXT")) }.getOrDefault(MessageType.TEXT),
      mediaUrl = readableMediaUrl,
      voiceDurationSeconds = snapshot.safeLong("voiceDurationSeconds").toInt().coerceAtLeast(0),
      waveform = safeWaveform(snapshot.get("waveform")),
      createdAt = createdAt,
      status = runCatching { MessageDeliveryStatus.valueOf(snapshot.safeString("status", "SENT")) }
        .getOrDefault(MessageDeliveryStatus.SENT),
      replyToId = decrypted?.optString("replyToId")
        ?.takeIf { it.isNotBlank() && it != "null" }
        ?: (snapshot.get("replyToId") as? String),
      replyToText = decrypted?.optString("replyToText")
        ?.takeIf { it.isNotBlank() && it != "null" }
        ?: (snapshot.get("replyToText") as? String),
      replyToSender = decrypted?.optString("replyToSender")
        ?.takeIf { it.isNotBlank() && it != "null" }
        ?: (snapshot.get("replyToSender") as? String),
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

  private suspend fun ensureConversationDirect(cid: String, otherUid: String) {
    val ids = listOf(uid, otherUid).sorted()
    require(ids.size == 2 && uid.isNotBlank() && otherUid.isNotBlank() && uid != otherUid) {
      "Invalid contact"
    }
    val ref = db.document("conversations/$cid")
    val snap = ref.get().await()
    if (!snap.exists()) {
      ref.set(
        mapOf(
          "participantIds" to ids,
          "lastMessageId" to "",
          "lastMessageTime" to 0L,
          "lastMessageText" to "",
          "lastMessageSenderId" to "",
          "unreadCounts" to emptyMap<String, Int>(),
          "deletedFor" to emptyList<String>(),
          "deletedBefore" to emptyMap<String, Long>(),
          "hiddenLastFor" to emptyMap<String, String>(),
          "archivedFor" to emptyList<String>(),
          "mutedFor" to emptyList<String>(),
          "favoriteFor" to emptyList<String>(),
          "disappearingSeconds" to 0L
        )
      ).await()
    }
  }

  private suspend fun sendMessageDirect(data: Map<String, Any?>): Boolean {
    val cid = data["conversationId"] as? String ?: error("Missing conversation")
    val id = data["id"] as? String ?: error("Missing message id")
    val otherUid = (data["otherUid"] as? String)
      ?: pendingPeers[cid]?.uid
      ?: _conversations.value.find { it.id == cid }?.otherUser?.uid
      ?: error("Contact unavailable")

    val tombstone = cid + ":" + id
    if (tombstone in deleteTombstones) return false

    ensureConversationDirect(cid, otherUid)

    val cref = db.document("conversations/$cid")
    val mref = db.document("conversations/$cid/messages/$id")
    val now = System.currentTimeMillis()
    val type = (data["type"] as? String) ?: "TEXT"
    val text = (data["text"] as? String).orEmpty().take(8000)
    val mediaUrl = (data["mediaUrl"] as? String).orEmpty()
    val voiceSeconds = (data["voiceDurationSeconds"] as? Number)?.toInt()?.coerceIn(0, 600) ?: 0
    val waveform = (data["waveform"] as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() }
      ?.map { it.coerceIn(.05f, 1f) }?.take(80).orEmpty()

    var recipientPublicKey = ""
    var recipientKeyId = ""
    if (type == "TEXT" || text.isNotBlank()) {
      val recipient = db.document("directory/$otherUid").get().await()
      recipientPublicKey = recipient.safeString("e2eePublicKey")
      recipientKeyId = recipient.safeString("e2eeKeyId")
      require(recipientPublicKey.isNotBlank() && recipientKeyId.isNotBlank()) {
        "This contact must update Liquid Chat before encrypted messaging can start"
      }
    }

    val message = mutableMapOf<String, Any>(
      "senderId" to uid,
      "senderName" to _currentUser.value.displayName.ifBlank { "User" },
      "text" to text,
      "type" to type,
      "mediaUrl" to mediaUrl,
      "voiceDurationSeconds" to voiceSeconds,
      "waveform" to waveform,
      "createdAt" to now,
      "status" to "SENT",
      "isDeleted" to false,
      "deletedForEveryone" to false,
      "hiddenFor" to emptyList<String>(),
      "isEdited" to false,
      "isPinned" to false,
      "reactions" to emptyList<Map<String, Any>>()
    )

    var encryptedText = false
    if (type == "TEXT" || (mediaUrl.isBlank() && text.isNotBlank())) {
      val encrypted = E2eeCrypto.encryptText(
        context = LiquidApi.context,
        uid = uid,
        recipientPublicKeyBase64 = recipientPublicKey,
        recipientKeyId = recipientKeyId,
        conversationId = cid,
        messageId = id,
        plaintextJson = E2eeCrypto.payloadJson(
          text = text,
          replyToId = data["replyToId"] as? String,
          replyToText = (data["replyToText"] as? String)?.take(500),
          replyToSender = (data["replyToSender"] as? String)?.take(60)
        )
      )
      message["text"] = ""
      message["e2ee"] = encrypted.fields
      encryptedText = true
    } else if (mediaUrl.isNotBlank()) {
      val replyId = data["replyToId"] as? String
      val replyTextValue = (data["replyToText"] as? String)?.take(500)
      val replySenderValue = (data["replyToSender"] as? String)?.take(60)
      if (text.isNotBlank() || !replyId.isNullOrBlank()) {
        require(recipientPublicKey.isNotBlank() && recipientKeyId.isNotBlank()) {
          "Contact encryption key unavailable"
        }
        val encryptedMeta = E2eeCrypto.encryptText(
          context = LiquidApi.context,
          uid = uid,
          recipientPublicKeyBase64 = recipientPublicKey,
          recipientKeyId = recipientKeyId,
          conversationId = cid,
          messageId = id,
          plaintextJson = E2eeCrypto.payloadJson(
            text = text,
            replyToId = replyId,
            replyToText = replyTextValue,
            replyToSender = replySenderValue
          )
        )
        message["e2ee"] = encryptedMeta.fields
      }
      message["text"] = ""
    } else {
      (data["replyToId"] as? String)?.takeIf { it.isNotBlank() }?.let {
        message["replyToId"] = it
        message["replyToText"] = (data["replyToText"] as? String).orEmpty().take(500)
        message["replyToSender"] = (data["replyToSender"] as? String).orEmpty().take(60)
      }
    }

    val conversationSnap = cref.get().await()
    val disappearingSeconds = conversationSnap.getLong("disappearingSeconds") ?: 0L
    if (disappearingSeconds > 0L) message["expiresAt"] = now + disappearingSeconds * 1000L

    val created = db.runTransaction { tx ->
      val existing = tx.get(mref)
      if (existing.exists()) return@runTransaction false

      tx.set(mref, message)
      tx.update(
        cref,
        mapOf(
          "lastMessageId" to id,
          "lastMessageText" to when (type) {
            "TEXT" -> if (encryptedText) "Encrypted message" else text.take(500)
            "IMAGE" -> "Photo"
            "VIDEO" -> "Video"
            "VOICE", "AUDIO" -> "Voice message"
            else -> "Document"
          },
          "lastMessageTime" to now,
          "lastMessageSenderId" to uid,
          "unreadCounts.$otherUid" to FieldValue.increment(1),
          "deletedFor" to FieldValue.arrayRemove(uid, otherUid),
          "hiddenLastFor.$uid" to FieldValue.delete(),
          "hiddenLastFor.$otherUid" to FieldValue.delete()
        )
      )
      true
    }.await()
    return created
  }

  private suspend fun hideMessageDirect(cid: String, id: String) {
    val mref = db.document("conversations/$cid/messages/$id")
    val cref = db.document("conversations/$cid")

    db.runTransaction { tx ->
      val message = tx.get(mref)
      if (!message.exists()) return@runTransaction

      val hiddenFor = (message.get("hiddenFor") as? List<*>)
        ?.filterIsInstance<String>()
        .orEmpty()
      if (uid !in hiddenFor) {
        tx.update(mref, "hiddenFor", FieldValue.arrayUnion(uid))
      }

      val conversation = tx.get(cref)
      if (conversation.safeString("lastMessageId") == id) {
        tx.update(cref, "hiddenLastFor.$uid", id)
      }
    }.await()
  }

  private suspend fun refreshConversationSummaryDirect(cid: String) {
    val cref = db.document("conversations/$cid")
    val latest = db.collection("conversations/$cid/messages")
      .orderBy("createdAt", Query.Direction.DESCENDING)
      .limit(1)
      .get()
      .await()
      .documents
      .firstOrNull()

    if (latest == null) {
      cref.update(
        mapOf(
          "lastMessageId" to "",
          "lastMessageText" to "",
          "lastMessageTime" to 0L,
          "lastMessageSenderId" to ""
        )
      ).await()
      return
    }

    val type = latest.safeString("type", "TEXT")
    val preview = when (type) {
      "TEXT" -> if (latest.get("e2ee") is Map<*, *>) "Encrypted message"
        else latest.safeString("text").take(500)
      "IMAGE" -> "Photo"
      "VIDEO" -> "Video"
      "VOICE", "AUDIO" -> "Voice message"
      else -> "Document"
    }
    cref.update(
      mapOf(
        "lastMessageId" to latest.id,
        "lastMessageText" to preview,
        "lastMessageTime" to latest.safeLong("createdAt"),
        "lastMessageSenderId" to latest.safeString("senderId")
      )
    ).await()
  }

  fun getOrCreateConversationId(otherUid: String): String {
    val cid = listOf(uid, otherUid).sorted().joinToString("_")
    pendingPeers[cid] = _users.value.find { user -> user.uid == otherUid }
      ?: User(uid = otherUid, displayName = "Contact")
    runAction {
      ensureConversationDirect(cid, otherUid)
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
    waveform: List<Float> = emptyList()
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
    persist(message)
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

  private fun persist(message: Message) {
    prefs.edit().putString("outbox:$uid:${message.id}", json(message).toString()).apply()
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
        try {
          if ((cid + ":" + id) in deleteTombstones) {
            prefs.edit().remove(key).apply()
            removeLocalMessage(cid, id)
            continue
          }
          val created = try {
            withTimeout(20_000) {
              sendMessageDirect(data)
            }
          } catch (timeout: TimeoutCancellationException) {
            failed += id
            updateLocal(cid, id) { it.copy(status = MessageDeliveryStatus.FAILED) }
            _syncWarning.value = "Message send timed out. Tap the failed message to retry."
            continue
          }
          prefs.edit().remove(key).apply()
          if (!created) {
            removeLocalMessage(cid, id)
          } else {
            updateLocal(cid, id) { it.copy(status = MessageDeliveryStatus.SENT) }
          }
        } catch (e: Exception) {
          if (e is CancellationException) throw e
          if (e !is java.io.IOException) {
            failed += id
            updateLocal(cid, id) { it.copy(status = MessageDeliveryStatus.FAILED) }
            _syncWarning.value = "Message: " + friendlyError(e)
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
    _messages.update { map -> map + (cid to map[cid].orEmpty().map { if (it.id == id) transform(it) else it }) }
  }

  private fun removeLocalMessage(cid: String, id: String) {
    failed -= id
    receipts.removeAll { it.contains(":$id:") }
    // Keep hidden:<uid>:<id> when present. Delete-for-me uses it as a durable
    // local tombstone until the optional backend can migrate it cross-device.
    prefs.edit().remove("outbox:$uid:$id").remove("star:$uid:$id").apply()
    _messages.update { map -> map + (cid to map[cid].orEmpty().filterNot { it.id == id }) }
  }

  fun uploadChatMedia(
    cid: String,
    uri: Uri,
    type: MessageType,
    onResult: (Result<String>) -> Unit = {}
  ) {
    uploadQueue.addLast(
      QueuedMediaUpload(
        conversationId = cid,
        uri = uri,
        type = type,
        onResult = onResult
      )
    )
    pumpMediaUploads()
  }

  private fun pumpMediaUploads() {
    if (activeUpload != null || uploadJob?.isActive == true) return
    val queued = uploadQueue.removeFirstOrNull() ?: run {
      _upload.value = null
      return
    }

    activeUpload = queued
    _upload.value = 0f
    uploadJob = scope.launch {
      val result = runCatching {
        val cid = queued.conversationId
        if (_conversations.value.none { it.id == cid }) {
          val peer = pendingPeers[cid]?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Contact is not ready yet")
          ensureConversationDirect(cid, peer)
        }
        LiquidApi.upload(queued.uri, cid) { progress ->
          scope.launch {
            if (activeUpload?.id == queued.id) _upload.value = progress
          }
        }
      }

      val failure = result.exceptionOrNull()
      if (failure != null && failure !is CancellationException) {
        _error.value = friendlyError(failure)
        failedUpload = queued
        retryUpload = {
          val retry = failedUpload
          if (retry != null && uploadQueue.none { it.id == retry.id } && activeUpload?.id != retry.id) {
            failedUpload = null
            retryUpload = null
            uploadQueue.addFirst(retry)
            pumpMediaUploads()
          }
        }
      } else if (result.isSuccess && failedUpload?.id == queued.id) {
        failedUpload = null
        retryUpload = null
      }

      queued.onResult(result)
      if (activeUpload?.id == queued.id) activeUpload = null
      uploadJob = null
      _upload.value = null
      pumpMediaUploads()
    }
  }

  fun cancelUpload() {
    uploadJob?.cancel(CancellationException("Upload cancelled"))
  }

  fun hasUploadRetry() = retryUpload != null && activeUpload == null
  fun retryUpload() { retryUpload?.invoke() }


  fun uploadProfilePhoto(uri: Uri, onResult: (Result<String>) -> Unit = {}) {
    runAction {
      val result = runCatching {
        val account = uid
        require(account.isNotBlank()) { "Please sign in again" }
        val url = LiquidApi.upload(uri, null)
        db.document("users/$account").set(mapOf("photoUrl" to url), SetOptions.merge()).await()
        db.document("directory/$account").set(mapOf("photoUrl" to url), SetOptions.merge()).await()
        url
      }
      onResult(result)
      result.getOrThrow()
    }
  }

  fun forwardMedia(message: Message, target: String) = runAction {
    require(message.mediaUrl.startsWith("https://res.cloudinary.com/")) { "Media unavailable" }
    sendMessage(
      conversationId = target,
      text = message.text,
      type = message.type,
      mediaUrl = message.mediaUrl,
      voiceDurationSeconds = message.voiceDurationSeconds,
      waveform = message.waveform
    )
  }

  fun addReaction(cid: String, id: String, emoji: String) = runAction {
    val clean = emoji.trim().take(16)
    require(clean.isNotBlank()) { "Invalid reaction" }
    val ref = db.document("conversations/$cid/messages/$id")
    db.runTransaction { tx ->
      val snap = tx.get(ref)
      check(snap.exists()) { "Message unavailable" }

      val next = (snap.get("reactions") as? List<*>).orEmpty().mapNotNull { item ->
        val map = item as? Map<*, *> ?: return@mapNotNull null
        val value = map["emoji"] as? String ?: return@mapNotNull null
        val users = (map["userIds"] as? List<*>)?.filterIsInstance<String>().orEmpty().toMutableSet()
        value to users
      }.toMutableList()

      val index = next.indexOfFirst { it.first == clean }
      if (index >= 0) {
        val users = next[index].second
        if (!users.add(uid)) users.remove(uid)
        if (users.isEmpty()) next.removeAt(index)
      } else if (next.size < 20) {
        next += clean to mutableSetOf(uid)
      }

      tx.update(
        ref,
        "reactions",
        next.take(20).map { (value, users) ->
          mapOf("emoji" to value, "userIds" to users.take(20))
        }
      )
    }.await()
  }

  fun deleteMessageForMe(cid: String, id: String) = runAction {
    val tombstone = cid + ":" + id
    prefs.edit().remove("outbox:$uid:$id").apply()
    deleteTombstones += tombstone
    try {
      hideMessageDirect(cid, id)
      prefs.edit().remove("hidden:$uid:$id").apply()
      removeLocalMessage(cid, id)
    } catch (t: Throwable) {
      deleteTombstones -= tombstone
      throw t
    }
  }

  fun deleteMessageForEveryone(cid: String, id: String) = runAction {
    val tombstone = cid + ":" + id
    prefs.edit().remove("outbox:$uid:$id").apply()
    deleteTombstones += tombstone
    try {
      val ref = db.document("conversations/$cid/messages/$id")
      val snap = ref.get().await()
      if (snap.exists()) {
        check(snap.safeString("senderId") == uid) { "Only the sender can do this" }
        val mediaUrl = snap.safeString("mediaUrl")
        ref.delete().await()
        refreshConversationSummaryDirect(cid)
        if (mediaUrl.isNotBlank()) LiquidApi.invalidateMedia(mediaUrl)
      }
      removeLocalMessage(cid, id)
    } catch (t: Throwable) {
      deleteTombstones -= tombstone
      throw t
    }
  }

  fun deleteMessage(cid: String, id: String) = deleteMessageForEveryone(cid, id)
  fun hideMessage(cid: String, id: String) = deleteMessageForMe(cid, id)
  fun editMessage(cid: String, id: String, text: String) = runAction {
    val clean = text.trim()
    require(clean.isNotBlank() && clean.length <= 8000) { "Invalid edit" }

    val ref = db.document("conversations/$cid/messages/$id")
    val current = ref.get().await()
    check(current.exists()) { "Message unavailable" }
    check(current.safeString("senderId") == uid) { "Only the sender can do this" }

    val conversationRef = db.document("conversations/$cid")
    val conversation = conversationRef.get().await()
    val encrypted = current.get("e2ee") is Map<*, *>

    if (encrypted) {
      val existing = toMessage(cid, current)
        ?: error("Message unavailable")
      val participantIds = (conversation.get("participantIds") as? List<*>)
        ?.filterIsInstance<String>()
        .orEmpty()
      val otherUid = participantIds.firstOrNull { it != uid }
        ?: error("Contact unavailable")
      val recipient = db.document("directory/$otherUid").get().await()
      val recipientPublicKey = recipient.safeString("e2eePublicKey")
      val recipientKeyId = recipient.safeString("e2eeKeyId")
      require(recipientPublicKey.isNotBlank() && recipientKeyId.isNotBlank()) {
        "Contact encryption key unavailable"
      }

      val payload = E2eeCrypto.encryptText(
        context = LiquidApi.context,
        uid = uid,
        recipientPublicKeyBase64 = recipientPublicKey,
        recipientKeyId = recipientKeyId,
        conversationId = cid,
        messageId = id,
        plaintextJson = E2eeCrypto.payloadJson(
          text = clean,
          replyToId = existing.replyToId,
          replyToText = existing.replyToText,
          replyToSender = existing.replyToSender
        )
      )

      ref.update(
        mapOf(
          "text" to "",
          "e2ee" to payload.fields,
          "isEdited" to true
        )
      ).await()
      if (conversation.safeString("lastMessageId") == id) {
        conversationRef.update("lastMessageText", "Encrypted message").await()
      }
    } else {
      // Legacy pre-E2EE messages remain editable without rewriting history.
      ref.update(mapOf("text" to clean, "isEdited" to true)).await()
      if (conversation.safeString("lastMessageId") == id) {
        conversationRef.update("lastMessageText", clean.take(500)).await()
      }
    }
  }

  fun pinMessage(cid: String, id: String) = runAction {
    val ref = db.document("conversations/$cid/messages/$id")
    db.runTransaction { tx ->
      val snap = tx.get(ref)
      check(snap.exists()) { "Message unavailable" }
      tx.update(ref, "isPinned", !snap.safeBoolean("isPinned"))
    }.await()
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
        val ref = db.document("conversations/$cid/messages/$id")
        db.runTransaction { tx ->
          val snap = tx.get(ref)
          if (!snap.exists() || snap.safeString("senderId") == uid) return@runTransaction
          val current = snap.safeString("status", "SENT")
          val rank = mapOf("SENT" to 1, "DELIVERED" to 2, "READ" to 3)
          val target = if (status == "READ" && _privacy.value.readReceipts) "READ" else "DELIVERED"
          if ((rank[target] ?: 0) > (rank[current] ?: 1)) tx.update(ref, "status", target)
        }.await()
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
    val ref = db.document("conversations/$cid")
    when (field) {
      "archivedFor", "mutedFor", "favoriteFor" ->
        ref.update(field, if (value == true) FieldValue.arrayUnion(uid) else FieldValue.arrayRemove(uid)).await()
      "disappearingSeconds" -> {
        val seconds = (value as? Number)?.toLong() ?: 0L
        require(seconds in setOf(0L, 86400L, 604800L, 7776000L)) { "Invalid disappearing timer" }
        ref.update(field, seconds).await()
      }
      "read" -> ref.update("unreadCounts.$uid", 0).await()
      else -> error("Invalid setting")
    }
  }

  fun setConversationArchived(cid: String, value: Boolean) = setting(cid, "archivedFor", value)
  fun setConversationMuted(cid: String, value: Boolean) = setting(cid, "mutedFor", value)
  fun setFavorite(cid: String, value: Boolean) = setting(cid, "favoriteFor", value)

  fun deleteChatForMe(cid: String) = runAction {
    val currentMessages = _messages.value[cid].orEmpty()
    val messageIds = currentMessages.map { it.id }.toSet()
    val mediaUrls = currentMessages.map { it.mediaUrl }.filter { it.isNotBlank() }.distinct()
    val now = System.currentTimeMillis()
    db.document("conversations/$cid").update(
      mapOf(
        "deletedFor" to FieldValue.arrayUnion(uid),
        "deletedBefore.$uid" to now,
        "unreadCounts.$uid" to 0
      )
    ).await()
    mediaUrls.forEach { LiquidApi.invalidateMedia(it) }
    messageListeners.remove(cid)?.remove()
    presenceListeners.remove(cid)?.remove()
    deletedBefore[cid] = now
    _conversations.update { list -> list.filterNot { it.id == cid } }
    _messages.update { map -> map - cid }
    val edit = prefs.edit().remove("draft:$uid:$cid").remove("wallpaper:$uid:$cid")
    messageIds.forEach { id -> edit.remove("star:$uid:$id").remove("outbox:$uid:$id").remove("hidden:$uid:$id") }
    prefs.all.filterKeys { it.startsWith("outbox:$uid:") }.forEach { (key, raw) ->
      runCatching {
        val j = JSONObject(raw as String)
        if (j.optString("conversationId") == cid) edit.remove(key)
      }
    }
    edit.apply()
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
    ).addOnFailureListener { _syncWarning.value = "Presence: " + friendlyError(it) }
  }

  fun draft(cid: String) = prefs.getString("draft:$uid:$cid", "").orEmpty()
  fun saveDraft(cid: String, text: String) { prefs.edit().putString("draft:$uid:$cid", text).apply() }

  suspend fun checkUsernameAvailability(username: String): Result<Boolean> = runCatching {
    val clean = username.trim().lowercase()
    require(Regex("^[a-z0-9_.]{3,32}$").matches(clean)) {
      "Username must have 3–32 letters, numbers, dots or underscores"
    }
    val reserved = db.document("usernames/$clean").get().await()
    !reserved.exists() || reserved.safeString("uid") == uid
  }

  fun updateProfile(displayName: String, username: String, bio: String, phoneNumber: String) = runAction {
    updateProfileDirect(displayName, username, bio, phoneNumber)
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
        .addOnFailureListener { _syncWarning.value = "Settings: " + friendlyError(it) }
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
    val clean = reason.trim().take(500)
    require(id.isNotBlank() && id != uid && clean.length >= 4) { "Please enter a report reason" }
    db.collection("reports").add(
      mapOf(
        "reporterId" to uid,
        "reportedUid" to id,
        "reason" to clean,
        "createdAt" to System.currentTimeMillis()
      )
    ).await()
    _error.value = "Report submitted"
  }

  suspend fun deleteAccount(): Result<Unit> = runCatching {
    val user = auth.currentUser ?: error("Please sign in again")
    val account = uid
    val now = System.currentTimeMillis()
    val token = user.getIdToken(false).await()
    val authAgeMs = now - token.authTimestamp * 1000L
    require(authAgeMs in 0..(5 * 60_000L)) {
      "For security, sign out, sign in again, then retry account deletion."
    }

    val ownRef = db.document("users/$account")
    val own = ownRef.get().await()
    val previousUsername = own.safeString("username").trim().lowercase()

    val conversations = db.collection("conversations")
      .whereArrayContains("participantIds", account)
      .get()
      .await()
    for (conversation in conversations.documents) {
      conversation.reference.update(
        mapOf(
          "deletedFor" to FieldValue.arrayUnion(account),
          "deletedBefore.$account" to now,
          "unreadCounts.$account" to 0
        )
      ).await()
    }

    if (previousUsername.isNotBlank()) {
      val usernameRef = db.document("usernames/$previousUsername")
      val reserved = usernameRef.get().await()
      if (reserved.exists() && reserved.safeString("uid") == account) {
        usernameRef.delete().await()
      }
    }

    db.document("directory/$account").delete().await()
    ownRef.delete().await()
    user.delete().await()
    LiquidApi.clearMediaCachesOnly()
    finishLocalLogout()
  }

  fun addSearchHistory(query: String) {
    if (query.isNotBlank()) {
      _searchHistory.update { (listOf(query) + it.filterNot { old -> old == query }).take(8) }
    }
  }

  fun clearSearchHistory() { _searchHistory.value = emptyList() }
}
