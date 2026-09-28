package com.example.data.repository

import android.net.Uri
import android.util.Log
import com.example.data.calls.FirestoreCallSignaling
import com.example.data.calls.WebRtcCallEngine
import android.content.Context
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import com.example.data.model.ActiveCallState
import com.example.data.model.AppearanceSettings
import com.example.data.model.CallRecord
import com.example.data.model.CallStatus
import com.example.data.model.CallType
import com.example.data.model.Conversation
import com.example.data.model.Group
import com.example.data.model.Message
import com.example.data.model.MessageDeliveryStatus
import com.example.data.model.MessageReaction
import com.example.data.model.MessageType
import com.example.data.model.PrivacySettings
import com.example.data.model.NotificationSettings
import com.example.data.model.StatusType
import com.example.data.model.User
import com.example.data.model.UserStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ChatRepository(
  private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
  private val auth: FirebaseAuth?
    get() = try {
      FirebaseAuth.getInstance()
    } catch (e: Throwable) {
      Log.w("ChatRepository", "FirebaseAuth not initialized: ${e.message}")
      null
    }

  private val firestore: FirebaseFirestore?
    get() = try {
      FirebaseFirestore.getInstance()
    } catch (e: Throwable) {
      Log.w("ChatRepository", "FirebaseFirestore not initialized: ${e.message}")
      null
    }

  private val storage: FirebaseStorage?
    get() = try {
      FirebaseStorage.getInstance()
    } catch (e: Throwable) {
      Log.w("ChatRepository", "FirebaseStorage not initialized: ${e.message}")
      null
    }

  // Active snapshot listeners
  private var usersListener: ListenerRegistration? = null
  private var conversationsListener: ListenerRegistration? = null
  private var currentUserDocListener: ListenerRegistration? = null
  private var statusesListener: ListenerRegistration? = null
  private var incomingCallsListener: ListenerRegistration? = null
  private val messageListeners = mutableMapOf<String, ListenerRegistration>()

  // Current Authenticated User
  private val _currentUser = MutableStateFlow(
    User(
      uid = auth?.currentUser?.uid ?: "usr_guest",
      displayName = auth?.currentUser?.displayName ?: "Liquid User",
      username = auth?.currentUser?.email?.substringBefore("@") ?: "liquid.user",
      email = auth?.currentUser?.email ?: "",
      photoUrl = auth?.currentUser?.photoUrl?.toString() ?: "",
      bio = "Fluid connections in real-time. 🌊",
      isOnline = true
    )
  )
  val currentUser: StateFlow<User> = _currentUser.asStateFlow()

  // Registered / Known Users
  private val _users = MutableStateFlow<List<User>>(emptyList())
  val users: StateFlow<List<User>> = _users.asStateFlow()

  // Conversations
  private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
  val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

  // Messages: Map of conversationId -> List<Message>
  private val _messages = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
  val messages: StateFlow<Map<String, List<Message>>> = _messages.asStateFlow()

  // Groups
  private val _groups = MutableStateFlow<List<Group>>(emptyList())
  val groups: StateFlow<List<Group>> = _groups.asStateFlow()

  // Statuses
  private val _statuses = MutableStateFlow<List<UserStatus>>(emptyList())
  val statuses: StateFlow<List<UserStatus>> = _statuses.asStateFlow()

  // Call Records
  private val _callRecords = MutableStateFlow<List<CallRecord>>(emptyList())
  val callRecords: StateFlow<List<CallRecord>> = _callRecords.asStateFlow()

  // Active Call
  private val _activeCall = MutableStateFlow<ActiveCallState?>(null)
  val activeCall: StateFlow<ActiveCallState?> = _activeCall.asStateFlow()
  private var callTimerJob: Job? = null
  private var webRtcEngine: WebRtcCallEngine? = null
  private val callSignaling = FirestoreCallSignaling()

  // Appearance & Privacy Settings
  private val _appearance = MutableStateFlow(AppearanceSettings())
  val appearance: StateFlow<AppearanceSettings> = _appearance.asStateFlow()

  private val _privacy = MutableStateFlow(PrivacySettings())
  val privacy: StateFlow<PrivacySettings> = _privacy.asStateFlow()

  private val _notifications = MutableStateFlow(NotificationSettings())
  val notifications: StateFlow<NotificationSettings> = _notifications.asStateFlow()

  // Search History
  private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
  val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

  // Blocked Users
  private val _blockedUserIds = MutableStateFlow<Set<String>>(emptySet())
  val blockedUserIds: StateFlow<Set<String>> = _blockedUserIds.asStateFlow()

  init {
    seedLocalBaseData()
    if (isUserLoggedIn()) {
      startFirebaseSync(auth?.currentUser?.uid ?: "")
    }
  }

  fun isUserLoggedIn(): Boolean {
    return auth?.currentUser != null
  }

  // --- Real Authentication Operations ---

  suspend fun registerWithEmail(
    email: String,
    pass: String,
    fullName: String,
    username: String,
    phoneNumber: String
  ): Result<User> {
    val firebaseAuth = auth ?: return Result.failure(IllegalStateException("Firebase Auth unavailable"))
    return try {
      val authResult = firebaseAuth.createUserWithEmailAndPassword(email.trim(), pass).await()
      val firebaseUser = authResult.user ?: throw IllegalStateException("Firebase registration returned null user")
      val uid = firebaseUser.uid

      val cleanUsername = if (username.isNotBlank()) {
        username.trim().lowercase().replace(" ", ".")
      } else {
        email.substringBefore("@").lowercase()
      }

      val cleanName = if (fullName.isNotBlank()) fullName.trim() else cleanUsername.replaceFirstChar { it.uppercase() }

      val newUser = User(
        uid = uid,
        displayName = cleanName,
        username = cleanUsername,
        email = email.trim(),
        phoneNumber = phoneNumber.trim(),
        photoUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=400&q=80",
        bio = "Fluid connections in real-time. 🌊",
        isOnline = true,
        lastSeen = System.currentTimeMillis(),
        createdAt = System.currentTimeMillis()
      )

      // Write to Firestore users/{uid}
      firestore?.let { db ->
        val userDoc = mapOf(
          "uid" to newUser.uid,
          "displayName" to newUser.displayName,
          "username" to newUser.username,
          "email" to newUser.email,
          "phoneNumber" to newUser.phoneNumber,
          "photoUrl" to newUser.photoUrl,
          "bio" to newUser.bio,
          "createdAt" to newUser.createdAt,
          "isOnline" to true,
          "lastSeen" to newUser.lastSeen
        )
        db.collection("users").document(uid).set(userDoc).await()
      }

      _currentUser.value = newUser
      startFirebaseSync(uid)
      Result.success(newUser)
    } catch (e: Exception) {
      Log.e("ChatRepository", "Registration failed", e)
      Result.failure(e)
    }
  }

  suspend fun signInWithEmail(email: String, pass: String): Result<User> {
    val firebaseAuth = auth ?: return Result.failure(IllegalStateException("Firebase Auth unavailable"))
    return try {
      val authResult = firebaseAuth.signInWithEmailAndPassword(email.trim(), pass).await()
      val firebaseUser = authResult.user ?: throw IllegalStateException("Firebase sign in returned null user")
      val uid = firebaseUser.uid

      val now = System.currentTimeMillis()
      var loggedInUser: User? = null

      firestore?.let { db ->
        val userRef = db.collection("users").document(uid)
        val snap = userRef.get().await()

        if (snap.exists()) {
          userRef.update(mapOf("isOnline" to true, "lastSeen" to now))
          loggedInUser = documentToUser(snap)
        } else {
          val uName = email.substringBefore("@").lowercase()
          val dName = firebaseUser.displayName ?: uName.replaceFirstChar { it.uppercase() }
          val created = User(
            uid = uid,
            displayName = dName,
            username = uName,
            email = email.trim(),
            phoneNumber = firebaseUser.phoneNumber ?: "",
            photoUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=400&q=80",
            bio = "Fluid connections in real-time. 🌊",
            isOnline = true,
            lastSeen = now,
            createdAt = now
          )
          userRef.set(
            mapOf(
              "uid" to created.uid,
              "displayName" to created.displayName,
              "username" to created.username,
              "email" to created.email,
              "phoneNumber" to created.phoneNumber,
              "photoUrl" to created.photoUrl,
              "bio" to created.bio,
              "createdAt" to created.createdAt,
              "isOnline" to true,
              "lastSeen" to created.lastSeen
            )
          ).await()
          loggedInUser = created
        }
      }

      val finalUser = loggedInUser ?: User(
        uid = uid,
        displayName = email.substringBefore("@").replaceFirstChar { it.uppercase() },
        username = email.substringBefore("@"),
        email = email,
        isOnline = true
      )

      _currentUser.value = finalUser
      startFirebaseSync(uid)
      Result.success(finalUser)
    } catch (e: Exception) {
      Log.e("ChatRepository", "Sign in failed", e)
      Result.failure(e)
    }
  }

  fun logout() {
    val uid = auth?.currentUser?.uid
    if (uid != null) {
      try {
        firestore?.collection("users")?.document(uid)?.update(
          mapOf(
            "isOnline" to false,
            "lastSeen" to System.currentTimeMillis()
          )
        )
      } catch (e: Exception) {
        Log.e("ChatRepository", "Error updating presence on logout", e)
      }
    }

    stopFirebaseSync()
    auth?.signOut()

    _currentUser.value = User(
      uid = "usr_guest",
      displayName = "Liquid User",
      username = "liquid.user"
    )
    _conversations.value = emptyList()
    _messages.value = emptyMap()
  }

  // --- Real-time Listeners ---

  private fun startFirebaseSync(uid: String) {
    stopFirebaseSync()
    val db = firestore ?: return

    // Register this device for push notifications. The Cloud Function uses this token
    // to notify the recipient when a new message is created.
    try {
      FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
        if (token.isNotBlank()) {
          db.collection("users").document(uid).update("fcmToken", token)
            .addOnFailureListener { e -> Log.w("ChatRepository", "Could not save FCM token", e) }
        }
      }
    } catch (e: Exception) {
      Log.w("ChatRepository", "FCM token registration unavailable", e)
    }

    // 1. Current user doc listener
    currentUserDocListener = db.collection("users").document(uid)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          Log.e("ChatRepository", "Error listening to current user doc", error)
          return@addSnapshotListener
        }
        if (snapshot != null && snapshot.exists()) {
          _currentUser.value = documentToUser(snapshot)
          loadPersistedSettings(snapshot)
        }
      }

    // 2. All users listener
    usersListener = db.collection("users")
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          Log.e("ChatRepository", "Error listening to users collection", error)
          return@addSnapshotListener
        }
        if (snapshot != null) {
          val list = snapshot.documents.map { documentToUser(it) }
            .filter { it.uid != uid }
          if (list.isEmpty()) {
            seedInitialFirestoreContacts()
          } else {
            _users.value = list
            refreshConversationUserModels()
          }
        }
      }

    // 3. Conversations listener where participantIds contains current user uid
    statusesListener = db.collection("statuses")
      .whereGreaterThan("expiresAt", System.currentTimeMillis())
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          Log.e("ChatRepository", "Error listening to statuses", error)
        } else if (snapshot != null) {
          _statuses.value = snapshot.documents.mapNotNull { documentToStatus(it) }
            .sortedByDescending { it.createdAt }
        }
      }

    incomingCallsListener = db.collection("calls")
      .whereEqualTo("receiverId", uid)
      .whereEqualTo("state", "ringing")
      .addSnapshotListener { snapshot, error ->
        if (error != null || snapshot == null) return@addSnapshotListener
        if (_activeCall.value != null) return@addSnapshotListener
        val doc = snapshot.documents.firstOrNull() ?: return@addSnapshotListener
        val callerId = doc.getString("callerId") ?: return@addSnapshotListener
        val caller = _users.value.firstOrNull { it.uid == callerId }
        if (caller != null) {
          val type = if (doc.getString("type") == "video") CallType.VIDEO else CallType.AUDIO
          _activeCall.value = ActiveCallState(
            callId = doc.id,
            user = caller,
            type = type,
            isOutgoing = false,
            isConnected = false
          )
        }
      }

    conversationsListener = db.collection("conversations")
      .whereArrayContains("participantIds", uid)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          Log.e("ChatRepository", "Error listening to conversations", error)
          return@addSnapshotListener
        }
        if (snapshot != null) {
          val convList = snapshot.documents.mapNotNull { doc ->
            documentToConversation(doc, uid)
          }.sortedByDescending { it.lastMessageTime }

          _conversations.value = convList

          convList.forEach { conv ->
            observeConversation(conv.id)
          }
        }
      }
  }

  private fun stopFirebaseSync() {
    currentUserDocListener?.remove()
    currentUserDocListener = null

    usersListener?.remove()
    usersListener = null

    conversationsListener?.remove()
    conversationsListener = null

    incomingCallsListener?.remove()
    incomingCallsListener = null

    messageListeners.values.forEach { it.remove() }
    messageListeners.clear()
  }

  fun observeConversation(conversationId: String) {
    if (messageListeners.containsKey(conversationId)) return
    val db = firestore ?: return

    val listener = db.collection("conversations")
      .document(conversationId)
      .collection("messages")
      .orderBy("createdAt", Query.Direction.ASCENDING)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          Log.e("ChatRepository", "Error listening to messages in $conversationId", error)
          return@addSnapshotListener
        }
        if (snapshot != null) {
          val msgList = snapshot.documents.mapNotNull { doc ->
            documentToMessage(conversationId, doc)
          }
          _messages.update { currentMap ->
            currentMap + (conversationId to msgList)
          }

          // A message reaching this listener means it has been delivered to this device.
          // Read is promoted separately by clearUnread() when the conversation is open.
          val currentUid = auth?.currentUser?.uid ?: _currentUser.value.uid
          msgList.asSequence()
            .filter { it.senderId != currentUid && it.status == MessageDeliveryStatus.SENT }
            .forEach { message -> updateMessageStatus(conversationId, message.id, MessageDeliveryStatus.DELIVERED) }
        }
      }

    messageListeners[conversationId] = listener
  }

  private fun refreshConversationUserModels() {
    val userMap = _users.value.associateBy { it.uid }
    val currentUid = auth?.currentUser?.uid ?: _currentUser.value.uid
    _conversations.update { list ->
      list.map { conv ->
        val otherUid = conv.participantIds.firstOrNull { it != currentUid } ?: ""
        val matchedUser = userMap[otherUid]
        if (matchedUser != null) {
          conv.copy(otherUser = matchedUser, isOnline = matchedUser.isOnline)
        } else conv
      }
    }
  }

  // --- 1-to-1 Deterministic Conversation Helper ---

  fun getOrCreateConversationId(otherUid: String): String {
    val currentUid = auth?.currentUser?.uid ?: _currentUser.value.uid
    val convId = listOf(currentUid, otherUid).sorted().joinToString("_")

    // Optimistically ensure in local list
    val existing = _conversations.value.find { it.id == convId }
    if (existing == null) {
      val matchedUser = _users.value.find { it.uid == otherUid }
        ?: User(uid = otherUid, displayName = "Contact", username = otherUid.take(8))
      val newConv = Conversation(
        id = convId,
        participantIds = listOf(currentUid, otherUid).sorted(),
        otherUser = matchedUser,
        lastMessageText = "",
        lastMessageTime = System.currentTimeMillis(),
        lastMessageSenderId = ""
      )
      _conversations.update { listOf(newConv) + it }
    }

    // Ensure conversation exists in Firestore
    scope.launch {
      try {
        val db = firestore ?: return@launch
        val convRef = db.collection("conversations").document(convId)
        val snap = convRef.get().await()
        if (!snap.exists()) {
          val data = mapOf(
            "participantIds" to listOf(currentUid, otherUid).sorted(),
            "lastMessageText" to "",
            "lastMessageTime" to System.currentTimeMillis(),
            "lastMessageSenderId" to ""
          )
          convRef.set(data, SetOptions.merge()).await()
        }
      } catch (e: Exception) {
        Log.e("ChatRepository", "Error ensuring conversation $convId exists", e)
      }
    }

    observeConversation(convId)
    return convId
  }

  // --- Real Message Actions (writes directly to Cloud Firestore) ---

  fun uploadChatMedia(
    conversationId: String,
    uri: Uri,
    type: MessageType,
    onResult: (Result<String>) -> Unit = {}
  ) {
    val uid = auth?.currentUser?.uid ?: _currentUser.value.uid
    val storageRef = storage?.reference?.child("chats/$conversationId/${UUID.randomUUID()}")
      ?: run {
        onResult(Result.failure(IllegalStateException("Firebase Storage unavailable")))
        return
      }

    scope.launch {
      try {
        val upload = storageRef.putFile(uri).await()
        val url = storageRef.downloadUrl.await().toString()
        onResult(Result.success(url))
      } catch (e: Exception) {
        Log.e("ChatRepository", "Chat media upload failed", e)
        onResult(Result.failure(e))
      }
    }
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
    if (text.isBlank() && mediaUrl.isBlank() && type != MessageType.VOICE) return

    val currentUid = auth?.currentUser?.uid ?: _currentUser.value.uid
    val currentName = _currentUser.value.displayName
    val msgId = "msg_" + UUID.randomUUID().toString().take(12)
    val now = System.currentTimeMillis()

    val displayText = text.ifBlank {
      when (type) {
        MessageType.VOICE -> "Voice note ($voiceDurationSeconds s)"
        MessageType.IMAGE -> "Photo"
        MessageType.VIDEO -> "Video"
        MessageType.AUDIO -> "Audio"
        MessageType.FILE -> "File attachment"
        MessageType.LOCATION -> "Location"
        MessageType.TEXT -> ""
      }
    }

    val newMsg = Message(
      id = msgId,
      conversationId = conversationId,
      senderId = currentUid,
      senderName = currentName,
      text = displayText,
      type = type,
      mediaUrl = mediaUrl,
      createdAt = now,
      status = MessageDeliveryStatus.SENT,
      replyToId = replyToId,
      replyToText = replyToText,
      replyToSender = replyToSender
    )

    // Immediate local optimistic update
    _messages.update { currentMap ->
      val list = currentMap[conversationId].orEmpty() + newMsg
      currentMap + (conversationId to list)
    }

    _conversations.update { list ->
      list.map { conv ->
        if (conv.id == conversationId) {
          conv.copy(
            lastMessageText = displayText,
            lastMessageTime = now,
            lastMessageSenderId = currentUid
          )
        } else conv
      }
    }

    val messageDoc = hashMapOf<String, Any?>(
      "senderId" to currentUid,
      "senderName" to currentName,
      "text" to displayText,
      "type" to type.name,
      "mediaUrl" to mediaUrl,
      "createdAt" to now,
      "status" to MessageDeliveryStatus.SENT.name,
      "replyToId" to replyToId,
      "replyToText" to replyToText,
      "replyToSender" to replyToSender,
      "reactions" to emptyList<Map<String, Any>>(),
      "isEdited" to false,
      "isDeleted" to false,
      "isPinned" to false
    )

    val parts = conversationId.split("_")
    val participantIds = if (parts.size >= 2) listOf(parts[0], parts[1]) else listOf(currentUid)

    val recipientUid = participantIds.firstOrNull { it != currentUid }
    val conversationUpdate = hashMapOf<String, Any?>(
      "participantIds" to participantIds,
      "lastMessageText" to displayText,
      "lastMessageTime" to now,
      "lastMessageSenderId" to currentUid
    )
    if (recipientUid != null) {
      conversationUpdate["unreadCounts.$recipientUid"] = FieldValue.increment(1)
    }

    scope.launch {
      try {
        val db = firestore ?: return@launch
        val convRef = db.collection("conversations").document(conversationId)
        convRef.collection("messages").document(msgId).set(messageDoc).await()
        convRef.set(conversationUpdate, SetOptions.merge()).await()
      } catch (e: Exception) {
        Log.e("ChatRepository", "Error sending message to Firestore", e)
      }
    }
  }

  fun addReaction(conversationId: String, messageId: String, emoji: String) {
    val currentUid = auth?.currentUser?.uid ?: _currentUser.value.uid
    val currentMessages = _messages.value[conversationId].orEmpty()
    val targetMsg = currentMessages.find { it.id == messageId } ?: return

    val existing = targetMsg.reactions.find { it.emoji == emoji }
    val newReactions = if (existing != null) {
      if (existing.userIds.contains(currentUid)) {
        targetMsg.reactions.mapNotNull {
          if (it.emoji == emoji) {
            val rem = it.userIds - currentUid
            if (rem.isEmpty()) null else it.copy(userIds = rem)
          } else it
        }
      } else {
        targetMsg.reactions.map {
          if (it.emoji == emoji) it.copy(userIds = it.userIds + currentUid) else it
        }
      }
    } else {
      targetMsg.reactions + MessageReaction(emoji, listOf(currentUid))
    }

    _messages.update { currentMap ->
      val list = currentMap[conversationId].orEmpty().map {
        if (it.id == messageId) it.copy(reactions = newReactions) else it
      }
      currentMap + (conversationId to list)
    }

    val serialized = newReactions.map {
      mapOf("emoji" to it.emoji, "userIds" to it.userIds)
    }

    scope.launch {
      try {
        firestore?.collection("conversations")
          ?.document(conversationId)
          ?.collection("messages")
          ?.document(messageId)
          ?.update("reactions", serialized)
          ?.await()
      } catch (e: Exception) {
        Log.e("ChatRepository", "Error adding reaction in Firestore", e)
      }
    }
  }

  fun deleteMessage(conversationId: String, messageId: String) {
    _messages.update { currentMap ->
      val list = currentMap[conversationId].orEmpty().map {
        if (it.id == messageId) it.copy(isDeleted = true, text = "This message was deleted") else it
      }
      currentMap + (conversationId to list)
    }

    scope.launch {
      try {
        firestore?.collection("conversations")
          ?.document(conversationId)
          ?.collection("messages")
          ?.document(messageId)
          ?.update(mapOf("isDeleted" to true, "text" to "This message was deleted"))
          ?.await()
      } catch (e: Exception) {
        Log.e("ChatRepository", "Error deleting message in Firestore", e)
      }
    }
  }

  fun editMessage(conversationId: String, messageId: String, newText: String) {
    if (newText.isBlank()) return
    _messages.update { currentMap ->
      val list = currentMap[conversationId].orEmpty().map {
        if (it.id == messageId) it.copy(text = newText, isEdited = true) else it
      }
      currentMap + (conversationId to list)
    }

    scope.launch {
      try {
        firestore?.collection("conversations")
          ?.document(conversationId)
          ?.collection("messages")
          ?.document(messageId)
          ?.update(
            mapOf(
              "text" to newText,
              "isEdited" to true
            )
          )?.await()
      } catch (e: Exception) {
        Log.e("ChatRepository", "Error editing message in Firestore", e)
      }
    }
  }

  fun pinMessage(conversationId: String, messageId: String) {
    val currentMessages = _messages.value[conversationId].orEmpty()
    val target = currentMessages.find { it.id == messageId } ?: return
    val newPinned = !target.isPinned

    _messages.update { currentMap ->
      val list = currentMap[conversationId].orEmpty().map {
        if (it.id == messageId) it.copy(isPinned = newPinned) else it
      }
      currentMap + (conversationId to list)
    }

    scope.launch {
      try {
        firestore?.collection("conversations")
          ?.document(conversationId)
          ?.collection("messages")
          ?.document(messageId)
          ?.update("isPinned", newPinned)
          ?.await()
      } catch (e: Exception) {
        Log.e("ChatRepository", "Error pinning message in Firestore", e)
      }
    }
  }

  fun updateMessageStatus(conversationId: String, messageId: String, status: MessageDeliveryStatus) {
    scope.launch {
      try {
        firestore?.collection("conversations")
          ?.document(conversationId)
          ?.collection("messages")
          ?.document(messageId)
          ?.update("status", status.name)
          ?.await()
      } catch (e: Exception) {
        Log.e("ChatRepository", "Error updating message status", e)
      }
    }
  }

  fun clearUnread(conversationId: String) {
    val currentUid = auth?.currentUser?.uid ?: _currentUser.value.uid
    _conversations.update { list ->
      list.map { if (it.id == conversationId) it.copy(unreadCount = 0) else it }
    }

    val messages = _messages.value[conversationId].orEmpty()
    messages.filter { it.senderId != currentUid && it.status != MessageDeliveryStatus.READ }
      .forEach { updateMessageStatus(conversationId, it.id, MessageDeliveryStatus.READ) }

    scope.launch {
      try {
        firestore?.collection("conversations")
          ?.document(conversationId)
          ?.update("unreadCounts.$currentUid", 0)
          ?.await()
      } catch (e: Exception) {
        Log.e("ChatRepository", "Error clearing unread count", e)
      }
    }
  }

  // --- Document Mappers ---

  private fun loadPersistedSettings(doc: DocumentSnapshot) {
    val blocked = (doc.get("blockedUserIds") as? List<*>)?.filterIsInstance<String>().orEmpty()
    _blockedUserIds.value = blocked.toSet()

    val notificationMap = doc.get("notifications") as? Map<*, *>
    if (notificationMap != null) {
      _notifications.value = NotificationSettings(
        messages = notificationMap["messages"] as? Boolean ?: true,
        calls = notificationMap["calls"] as? Boolean ?: true,
        status = notificationMap["status"] as? Boolean ?: true,
        vibration = notificationMap["vibration"] as? Boolean ?: true
      )
    }

    val appearanceMap = doc.get("appearance") as? Map<*, *>
    if (appearanceMap != null) {
      _appearance.value = AppearanceSettings(
        isDarkMode = appearanceMap["isDarkMode"] as? Boolean ?: false,
        glassIntensity = (appearanceMap["glassIntensity"] as? Number)?.toFloat() ?: 0.85f,
        blurAlpha = (appearanceMap["blurAlpha"] as? Number)?.toFloat() ?: 0.70f,
        accentColorHex = appearanceMap["accentColorHex"] as? String ?: "#176BFF",
        isReducedMotion = appearanceMap["isReducedMotion"] as? Boolean ?: false
      )
    }

    val privacyMap = doc.get("privacy") as? Map<*, *>
    if (privacyMap != null) {
      _privacy.value = PrivacySettings(
        lastSeenVisibility = privacyMap["lastSeenVisibility"] as? String ?: "Everyone",
        onlineVisibility = privacyMap["onlineVisibility"] as? String ?: "Everyone",
        profilePhotoVisibility = privacyMap["profilePhotoVisibility"] as? String ?: "Everyone",
        readReceipts = privacyMap["readReceipts"] as? Boolean ?: true,
        statusVisibility = privacyMap["statusVisibility"] as? String ?: "Contacts Only",
        whoCanAddToGroups = privacyMap["whoCanAddToGroups"] as? String ?: "Everyone"
      )
    }
  }

  private fun documentToUser(doc: DocumentSnapshot): User {
    return User(
      uid = doc.getString("uid") ?: doc.id,
      displayName = doc.getString("displayName") ?: "Liquid Contact",
      username = doc.getString("username") ?: "contact",
      email = doc.getString("email") ?: "",
      phoneNumber = doc.getString("phoneNumber") ?: "",
      photoUrl = doc.getString("photoUrl") ?: "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=400&q=80",
      bio = doc.getString("bio") ?: "Fluid connections in real-time.",
      isOnline = doc.getBoolean("isOnline") ?: false,
      lastSeen = doc.getLong("lastSeen") ?: (doc.get("lastSeen") as? Number)?.toLong() ?: System.currentTimeMillis(),
      createdAt = doc.getLong("createdAt") ?: (doc.get("createdAt") as? Number)?.toLong() ?: System.currentTimeMillis(),
      isVerified = doc.getBoolean("isVerified") ?: false,
      website = doc.getString("website") ?: ""
    )
  }

  private fun documentToConversation(doc: DocumentSnapshot, currentUid: String): Conversation? {
    val participantIdsRaw = doc.get("participantIds") as? List<*> ?: return null
    val participantIds = participantIdsRaw.filterIsInstance<String>()
    val otherUid = participantIds.firstOrNull { it != currentUid } ?: currentUid
    val matchedUser = _users.value.find { it.uid == otherUid }
      ?: User(
        uid = otherUid,
        displayName = "Contact",
        username = otherUid.take(8),
        photoUrl = "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=400&q=80"
      )

    val lastText = doc.getString("lastMessageText") ?: ""
    val lastTime = doc.getLong("lastMessageTime") ?: (doc.get("lastMessageTime") as? Number)?.toLong() ?: System.currentTimeMillis()
    val lastSenderId = doc.getString("lastMessageSenderId") ?: ""
    val unreadCounts = (doc.get("unreadCounts") as? Map<*, *>)
      ?.mapNotNull { (key, value) ->
        val uid = key as? String ?: return@mapNotNull null
        val count = (value as? Number)?.toInt() ?: return@mapNotNull null
        uid to count
      }?.toMap().orEmpty()
    val typingUserIds = (doc.get("typingUserIds") as? List<*>)?.filterIsInstance<String>().orEmpty()
    val isOtherUserTyping = otherUid in typingUserIds

    return Conversation(
      id = doc.id,
      participantIds = participantIds,
      otherUser = matchedUser,
      lastMessageText = lastText,
      lastMessageTime = lastTime,
      lastMessageSenderId = lastSenderId,
      unreadCount = unreadCounts[currentUid] ?: 0,
      isOnline = matchedUser.isOnline,
      isTyping = isOtherUserTyping
    )
  }

  private fun documentToMessage(conversationId: String, doc: DocumentSnapshot): Message? {
    val senderId = doc.getString("senderId") ?: return null
    val senderName = doc.getString("senderName") ?: "User"
    val text = doc.getString("text") ?: ""
    val typeStr = doc.getString("type") ?: MessageType.TEXT.name
    val type = try {
      MessageType.valueOf(typeStr)
    } catch (e: Exception) {
      MessageType.TEXT
    }
    val mediaUrl = doc.getString("mediaUrl") ?: ""
    val createdAt = doc.getLong("createdAt") ?: (doc.get("createdAt") as? Number)?.toLong() ?: System.currentTimeMillis()
    val statusStr = doc.getString("status") ?: MessageDeliveryStatus.SENT.name
    val status = try {
      MessageDeliveryStatus.valueOf(statusStr)
    } catch (e: Exception) {
      MessageDeliveryStatus.SENT
    }

    val replyToId = doc.getString("replyToId")
    val replyToText = doc.getString("replyToText")
    val replyToSender = doc.getString("replyToSender")

    val rawReactions = doc.get("reactions") as? List<*> ?: emptyList<Any>()
    val reactions = rawReactions.mapNotNull { item ->
      val map = item as? Map<*, *> ?: return@mapNotNull null
      val emoji = map["emoji"] as? String ?: return@mapNotNull null
      val uids = (map["userIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
      MessageReaction(emoji, uids)
    }

    val isEdited = doc.getBoolean("isEdited") ?: false
    val isDeleted = doc.getBoolean("isDeleted") ?: false
    val isPinned = doc.getBoolean("isPinned") ?: false

    return Message(
      id = doc.id,
      conversationId = conversationId,
      senderId = senderId,
      senderName = senderName,
      text = text,
      type = type,
      mediaUrl = mediaUrl,
      createdAt = createdAt,
      status = status,
      replyToId = replyToId,
      replyToText = replyToText,
      replyToSender = replyToSender,
      reactions = reactions,
      isEdited = isEdited,
      isDeleted = isDeleted,
      isPinned = isPinned
    )
  }

  // --- Seed Initial Contacts to Firestore ---

  private fun seedInitialFirestoreContacts() {
    val db = firestore ?: return
    scope.launch {
      val contacts = listOf(
        User(
          uid = "usr_elena_rostova",
          displayName = "Elena Rostova",
          username = "elena.design",
          email = "elena@liquidchat.io",
          phoneNumber = "+44 7911 123456",
          photoUrl = "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=400&q=80",
          bio = "Liquid Glass Material & Spatial 3D UI Designer. London / San Francisco.",
          isOnline = true,
          isVerified = true
        ),
        User(
          uid = "usr_leo_sterling",
          displayName = "Leo Sterling",
          username = "leo.sterling",
          email = "leo@liquidchat.io",
          phoneNumber = "+1 (555) 890-1234",
          photoUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=400&q=80",
          bio = "Distributed systems & WebRTC Core Engineer. Coffee & Kotlin.",
          isOnline = true
        ),
        User(
          uid = "usr_sophia_chen",
          displayName = "Sophia Chen",
          username = "sophia.chen",
          email = "sophia@liquidchat.io",
          phoneNumber = "+1 (555) 432-8765",
          photoUrl = "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?auto=format&fit=crop&w=400&q=80",
          bio = "Founder @ CyberSphere • Exploring real-time neural interfaces.",
          isOnline = false,
          lastSeen = System.currentTimeMillis() - 15 * 60 * 1000
        ),
        User(
          uid = "usr_marcus_vance",
          displayName = "Marcus Vance",
          username = "marcus.vance",
          email = "marcus@liquidchat.io",
          phoneNumber = "+1 (555) 789-0123",
          photoUrl = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=400&q=80",
          bio = "Early stage investments in privacy & secure messaging.",
          isOnline = false,
          lastSeen = System.currentTimeMillis() - 2 * 3600 * 1000
        ),
        User(
          uid = "usr_maya_lin",
          displayName = "Maya Lin",
          username = "maya.lin",
          email = "maya@liquidchat.io",
          phoneNumber = "+81 90 1234 5678",
          photoUrl = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=400&q=80",
          bio = "Creative Technologist • Tokyo, Japan.",
          isOnline = true
        )
      )

      for (c in contacts) {
        val docData = mapOf(
          "uid" to c.uid,
          "displayName" to c.displayName,
          "username" to c.username,
          "email" to c.email,
          "phoneNumber" to c.phoneNumber,
          "photoUrl" to c.photoUrl,
          "bio" to c.bio,
          "createdAt" to c.createdAt,
          "isOnline" to c.isOnline,
          "lastSeen" to c.lastSeen,
          "isVerified" to c.isVerified
        )
        try {
          db.collection("users").document(c.uid).set(docData, SetOptions.merge()).await()
        } catch (e: Exception) {
          Log.e("ChatRepository", "Error seeding contact ${c.uid}", e)
        }
      }
    }
  }

  // --- Seed Initial Base Data ---

  private fun seedLocalBaseData() {
    val elena = User(
      uid = "usr_elena_rostova",
      displayName = "Elena Rostova",
      username = "elena.design",
      email = "elena@liquidchat.io",
      photoUrl = "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=400&q=80",
      bio = "Liquid Glass Material & Spatial 3D UI Designer. London / San Francisco.",
      isOnline = true,
      isVerified = true
    )

    val leo = User(
      uid = "usr_leo_sterling",
      displayName = "Leo Sterling",
      username = "leo.sterling",
      email = "leo@liquidchat.io",
      photoUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=400&q=80",
      bio = "Distributed systems & WebRTC Core Engineer. Coffee & Kotlin.",
      isOnline = true
    )

    val sophia = User(
      uid = "usr_sophia_chen",
      displayName = "Sophia Chen",
      username = "sophia.chen",
      email = "sophia@liquidchat.io",
      photoUrl = "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?auto=format&fit=crop&w=400&q=80",
      bio = "Founder @ CyberSphere • Exploring real-time neural interfaces.",
      isOnline = false
    )

    _users.value = listOf(elena, leo, sophia)

    val currentUid = auth?.currentUser?.uid ?: _currentUser.value.uid
    val convElenaId = listOf(currentUid, elena.uid).sorted().joinToString("_")

    val convElena = Conversation(
      id = convElenaId,
      participantIds = listOf(currentUid, elena.uid).sorted(),
      otherUser = elena,
      lastMessageText = "The refraction highlight on the new Liquid Glass cards looks incredible! ✨",
      lastMessageTime = System.currentTimeMillis() - 3 * 60 * 1000,
      lastMessageSenderId = elena.uid,
      unreadCount = 0,
      isPinned = true,
      isOnline = true
    )

    _conversations.value = listOf(convElena)

    val elenaMsgs = listOf(
      Message(
        id = "msg_init_1",
        conversationId = convElenaId,
        senderId = elena.uid,
        senderName = elena.displayName,
        text = "Hey! Did you check out the updated Liquid Glass material tokens?",
        createdAt = System.currentTimeMillis() - 15 * 60 * 1000,
        status = MessageDeliveryStatus.READ
      ),
      Message(
        id = "msg_init_2",
        conversationId = convElenaId,
        senderId = elena.uid,
        senderName = elena.displayName,
        text = "The refraction highlight on the new Liquid Glass cards looks incredible! ✨",
        createdAt = System.currentTimeMillis() - 3 * 60 * 1000,
        status = MessageDeliveryStatus.DELIVERED
      )
    )

    _messages.value = mapOf(convElenaId to elenaMsgs)

    val grpDesign = Group(
      id = "grp_design_lab",
      name = "Liquid Design Lab",
      description = "Core design circle for Liquid Glass interface development.",
      photoUrl = "https://images.unsplash.com/photo-1550745165-9bc0b252726f?auto=format&fit=crop&w=400&q=80",
      adminIds = listOf("usr_elena_rostova"),
      members = listOf(elena, leo),
      lastMessageText = "Frosted modal tokens synced with latest design tokens.",
      lastMessageTime = System.currentTimeMillis() - 20 * 60 * 1000,
      lastMessageSender = "Elena Rostova",
      unreadCount = 2,
      isPinned = true
    )

    _groups.value = listOf(grpDesign)

    val statusElena = UserStatus(
      id = "st_elena_1",
      userId = "usr_elena_rostova",
      userName = "Elena Rostova",
      userPhotoUrl = elena.photoUrl,
      type = StatusType.TEXT,
      content = "Drafting specular highlight shader for Liquid Chat. Real-time glass feels alive! 💧✨",
      backgroundGradientIndex = 0,
      createdAt = System.currentTimeMillis() - 2 * 3600 * 1000,
      viewerNames = listOf("Leo Sterling"),
      isViewedByMe = false
    )

    _statuses.value = listOf(statusElena)

    val call1 = CallRecord(
      id = "call_1",
      otherUser = elena,
      type = CallType.VIDEO,
      status = CallStatus.COMPLETED,
      timestamp = System.currentTimeMillis() - 4 * 3600 * 1000,
      durationSeconds = 480
    )

    _callRecords.value = listOf(call1)
  }

  // --- Group Actions ---

  fun createGroup(name: String, description: String, selectedUserIds: List<String>) {
    val current = _currentUser.value
    val selectedUsers = _users.value.filter { selectedUserIds.contains(it.uid) }
    val newGroup = Group(
      id = "grp_" + UUID.randomUUID().toString().take(8),
      name = name,
      description = description,
      photoUrl = "https://images.unsplash.com/photo-1522071820081-009f0129c71c?auto=format&fit=crop&w=400&q=80",
      adminIds = listOf(current.uid),
      members = listOf(current) + selectedUsers,
      lastMessageText = "Group created by ${current.displayName}",
      lastMessageTime = System.currentTimeMillis(),
      lastMessageSender = "Liquid Chat"
    )

    _groups.update { listOf(newGroup) + it }
  }

  fun sendGroupMessage(groupId: String, text: String) {
    if (text.isBlank()) return
    val current = _currentUser.value
    val newMsg = Message(
      id = "gmsg_" + UUID.randomUUID().toString().take(8),
      conversationId = groupId,
      senderId = current.uid,
      senderName = current.displayName,
      text = text,
      createdAt = System.currentTimeMillis()
    )

    _messages.update { map ->
      val list = map[groupId].orEmpty() + newMsg
      map + (groupId to list)
    }

    _groups.update { list ->
      list.map { grp ->
        if (grp.id == groupId) {
          grp.copy(
            lastMessageText = "${current.displayName}: $text",
            lastMessageTime = newMsg.createdAt,
            lastMessageSender = current.displayName
          )
        } else grp
      }
    }
  }

  // --- Status Actions ---

  fun postStatus(type: StatusType, content: String, bgIndex: Int = 0) {
    val current = _currentUser.value
    val newStatus = UserStatus(
      id = "st_" + UUID.randomUUID().toString().take(8),
      userId = current.uid,
      userName = current.displayName,
      userPhotoUrl = current.photoUrl,
      type = type,
      content = content,
      backgroundGradientIndex = bgIndex,
      createdAt = System.currentTimeMillis(),
      viewerNames = emptyList(),
      isViewedByMe = true
    )
    _statuses.update { listOf(newStatus) + it }
    persistStatus(newStatus)
  }

  fun uploadAndPostStatus(uri: Uri, type: StatusType, bgIndex: Int = 0, onResult: (Result<Unit>) -> Unit = {}) {
    val uid = auth?.currentUser?.uid
    if (uid.isNullOrBlank()) {
      onResult(Result.failure(IllegalStateException("You must be signed in")))
      return
    }
    scope.launch {
      try {
        val id = "st_" + UUID.randomUUID().toString().take(8)
        val ref = storage?.reference?.child("statuses/$uid/$id")
          ?: throw IllegalStateException("Firebase Storage unavailable")
        ref.putFile(uri).await()
        val url = ref.downloadUrl.await().toString()
        val current = _currentUser.value
        val status = UserStatus(
          id = id, userId = uid, userName = current.displayName,
          userPhotoUrl = current.photoUrl, type = type, content = url,
          backgroundGradientIndex = bgIndex, createdAt = System.currentTimeMillis(),
          viewerNames = emptyList(), isViewedByMe = true
        )
        _statuses.update { listOf(status) + it }
        persistStatus(status)
        onResult(Result.success(Unit))
      } catch (e: Exception) {
        Log.e("ChatRepository", "Status media upload failed", e)
        onResult(Result.failure(e))
      }
    }
  }

  private fun persistStatus(status: UserStatus) {
    scope.launch {
      try {
        firestore?.collection("statuses")?.document(status.id)?.set(
          mapOf(
            "id" to status.id, "userId" to status.userId, "userName" to status.userName,
            "userPhotoUrl" to status.userPhotoUrl, "type" to status.type.name,
            "content" to status.content, "backgroundGradientIndex" to status.backgroundGradientIndex,
            "createdAt" to status.createdAt, "expiresAt" to status.expiresAt,
            "viewerNames" to status.viewerNames, "viewedBy" to emptyList<String>()
          )
        )?.await()
      } catch (e: Exception) {
        Log.e("ChatRepository", "Status persistence failed", e)
      }
    }
  }

  private fun documentToStatus(doc: DocumentSnapshot): UserStatus? {
    val type = runCatching { StatusType.valueOf(doc.getString("type") ?: "TEXT") }.getOrDefault(StatusType.TEXT)
    val userId = doc.getString("userId") ?: return null
    return UserStatus(
      id = doc.id, userId = userId, userName = doc.getString("userName") ?: "User",
      userPhotoUrl = doc.getString("userPhotoUrl") ?: "", type = type,
      content = doc.getString("content") ?: "",
      backgroundGradientIndex = (doc.getLong("backgroundGradientIndex") ?: 0L).toInt(),
      createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
      expiresAt = doc.getLong("expiresAt") ?: (System.currentTimeMillis() + 24 * 60 * 60 * 1000),
      viewerNames = (doc.get("viewerNames") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
      isViewedByMe = (doc.get("viewedBy") as? List<*>)?.contains(auth?.currentUser?.uid) == true
    )
  }

  fun markStatusViewed(statusId: String) {
    val uid = auth?.currentUser?.uid
    _statuses.update { list ->
      list.map { if (it.id == statusId) it.copy(isViewedByMe = true) else it }
    }
    if (!uid.isNullOrBlank()) {
      scope.launch {
        try {
          firestore?.collection("statuses")?.document(statusId)?.update(
            "viewedBy", com.google.firebase.firestore.FieldValue.arrayUnion(uid)
          )?.await()
        } catch (e: Exception) {
          Log.w("ChatRepository", "Status view sync failed", e)
        }
      }
    }
  }

  // --- Calling ---

  fun startCall(otherUser: User, type: CallType) {
    val uid = auth?.currentUser?.uid ?: return
    scope.launch {
      try {
        val callId = callSignaling.createCall(uid, otherUser.uid, type.name.lowercase())
        _activeCall.value = ActiveCallState(
          callId = callId,
          user = otherUser,
          type = type,
          isOutgoing = true,
          isConnected = false
        )
        callTimerJob?.cancel()
        callSignaling.observe(callId) { data ->
          when (data["state"]?.toString()) {
            "connected" -> {
              if (_activeCall.value?.isConnected != true) {
                _activeCall.update { it?.copy(isConnected = true) }
                startCallTimer()
              }
            }
            "ended", "rejected", "cancelled" -> endCallLocalOnly()
          }
        }
      } catch (e: Exception) {
        Log.e("ChatRepository", "Call signaling failed", e)
      }
    }
  }

  private fun startCallTimer() {
    callTimerJob?.cancel()
    callTimerJob = scope.launch {
      while (_activeCall.value?.isConnected == true) {
        delay(1000)
        _activeCall.update { it?.copy(durationSeconds = it.durationSeconds + 1) }
      }
    }
  }

  fun initializeWebRtc(context: Context, onOfferReady: ((SessionDescription) -> Unit)? = null) {
    val call = _activeCall.value ?: return
    if (webRtcEngine != null) return
    val engine = runCatching { WebRtcCallEngine(context.applicationContext) }.getOrElse {
      Log.e("ChatRepository", "WebRTC initialization failed", it)
      return
    }
    webRtcEngine = engine
    engine.onLocalIceCandidate = { candidate ->
      scope.launch {
        runCatching { callSignaling.addIceCandidate(call.callId, if (call.isOutgoing) "caller" else "receiver", mapOf(
          "sdpMid" to candidate.sdpMid,
          "sdpMLineIndex" to candidate.sdpMLineIndex,
          "candidate" to candidate.sdp
        )) }
      }
    }
    engine.onConnected = {
      scope.launch { runCatching { callSignaling.markConnected(call.callId) } }
    }
    engine.onDisconnected = { }
    engine.start(
      callType = if (call.type == CallType.VIDEO) "video" else "audio",
      isInitiator = call.isOutgoing,
      onOffer = { desc ->
        scope.launch {
          runCatching { callSignaling.writeOffer(call.callId, mapOf("type" to desc.type.canonicalForm(), "sdp" to desc.description)) }
        }
        onOfferReady?.invoke(desc)
      },
      onAnswer = { desc ->
        scope.launch {
          runCatching { callSignaling.writeAnswer(call.callId, mapOf("type" to desc.type.canonicalForm(), "sdp" to desc.description)) }
        }
      }
    )

    val ownSide = if (call.isOutgoing) "caller" else "receiver"
    callSignaling.observe(call.callId) { data ->
      val answer = data["answer"] as? Map<*, *>
      if (call.isOutgoing && answer != null) {
        val type = answer["type"]?.toString() ?: return@observe
        val sdp = answer["sdp"]?.toString() ?: return@observe
        engine.setRemoteAnswer(SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp))
      }
      if (!call.isOutgoing && data["state"]?.toString() == "accepted") {
        val offer = data["offer"] as? Map<*, *> ?: return@observe
        val type = offer["type"]?.toString() ?: return@observe
        val sdp = offer["sdp"]?.toString() ?: return@observe
        engine.acceptOffer(SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp)) { }
      }
    }
    callSignaling.observeIceCandidates(call.callId) { side, candidate ->
      if (side == ownSide) return@observeIceCandidates
      val sdpMid = candidate["sdpMid"]?.toString()
      val sdpMLineIndex = (candidate["sdpMLineIndex"] as? Number)?.toInt() ?: return@observeIceCandidates
      val sdp = candidate["candidate"]?.toString() ?: return@observeIceCandidates
      engine.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, sdp))
    }
  }

  fun acceptIncomingCall(context: Context) {
    val call = _activeCall.value ?: return
    if (call.isOutgoing) return
    scope.launch {
      runCatching { callSignaling.accept(call.callId) }
      initializeWebRtc(context)
    }
  }

  fun rejectIncomingCall() {
    val call = _activeCall.value ?: return
    if (call.isOutgoing) return
    scope.launch { runCatching { callSignaling.reject(call.callId, auth?.currentUser?.uid ?: "") } }
    endCallLocalOnly()
  }

  fun setWebRtcMicrophoneEnabled(enabled: Boolean) { webRtcEngine?.setMicrophoneEnabled(enabled) }
  fun setWebRtcCameraEnabled(enabled: Boolean) { webRtcEngine?.setCameraEnabled(enabled) }
  fun setWebRtcSpeakerEnabled(enabled: Boolean) { webRtcEngine?.setSpeakerEnabled(enabled) }
  fun webRtcLocalRenderer() = webRtcEngine?.localRenderer
  fun webRtcRemoteRenderer() = webRtcEngine?.remoteRenderer

  fun addWebRtcRemoteIceCandidate(candidate: IceCandidate) { webRtcEngine?.addIceCandidate(candidate) }

  fun toggleMuteCall() {
    _activeCall.update {
      val next = !(it?.isMuted ?: false)
      it?.let { current -> setWebRtcMicrophoneEnabled(!next); current.copy(isMuted = next) }
    }
  }

  fun toggleCameraCall() {
    _activeCall.update {
      val next = !(it?.isCameraOn ?: true)
      it?.let { current -> setWebRtcCameraEnabled(next); current.copy(isCameraOn = next) }
    }
  }

  fun toggleSpeakerCall() {
    _activeCall.update { it?.copy(isSpeakerOn = !(it.isSpeakerOn)) }
  }

  fun endCall() {
    val currentCall = _activeCall.value
    if (currentCall != null) {
      scope.launch {
        runCatching {
          callSignaling.setState(currentCall.callId, "ended", auth?.currentUser?.uid)
        }
      }
    }
    endCallLocalOnly()
  }

  private fun releaseWebRtc() {
    runCatching { webRtcEngine?.release() }
    webRtcEngine = null
  }

  private fun endCallLocalOnly() {
    callTimerJob?.cancel()
    releaseWebRtc()
    val currentCall = _activeCall.value ?: return
    val record = CallRecord(
      id = currentCall.callId,
      otherUser = currentCall.user,
      type = currentCall.type,
      status = if (currentCall.isOutgoing) CallStatus.OUTGOING else CallStatus.INCOMING,
      timestamp = System.currentTimeMillis(),
      durationSeconds = currentCall.durationSeconds
    )
    _callRecords.update { listOf(record) + it }
    _activeCall.value = null
  }

  // --- Profile & Settings ---

  fun updateProfile(displayName: String, username: String, bio: String, phoneNumber: String) {
    val uid = auth?.currentUser?.uid
    _currentUser.update {
      it.copy(
        displayName = displayName,
        username = username,
        bio = bio,
        phoneNumber = phoneNumber
      )
    }

    if (uid != null) {
      scope.launch {
        try {
          firestore?.collection("users")?.document(uid)?.update(
            mapOf(
              "displayName" to displayName,
              "username" to username,
              "bio" to bio,
              "phoneNumber" to phoneNumber
            )
          )?.await()
        } catch (e: Exception) {
          Log.e("ChatRepository", "Error updating profile in Firestore", e)
        }
      }
    }
  }

  fun uploadProfilePhoto(uri: Uri, onResult: (Result<String>) -> Unit = {}) {
    val uid = auth?.currentUser?.uid
    if (uid.isNullOrBlank()) {
      onResult(Result.failure(IllegalStateException("You must be signed in")))
      return
    }
    scope.launch {
      try {
        val ref = storage?.reference?.child("users/$uid/profile.jpg")
          ?: throw IllegalStateException("Firebase Storage unavailable")
        ref.putFile(uri).await()
        val downloadUrl = ref.downloadUrl.await().toString()
        firestore?.collection("users")?.document(uid)?.update("photoUrl", downloadUrl)?.await()
        _currentUser.update { it.copy(photoUrl = downloadUrl) }
        onResult(Result.success(downloadUrl))
      } catch (e: Exception) {
        Log.e("ChatRepository", "Profile photo upload failed", e)
        onResult(Result.failure(e))
      }
    }
  }

  /** Updates the current user's typing state for a conversation.
   *  The state is stored on the conversation document so every participant
   *  receives it through the existing realtime conversation listener.
   */
  fun setTyping(conversationId: String, isTyping: Boolean) {
    val uid = auth?.currentUser?.uid ?: _currentUser.value.uid
    if (uid.isBlank()) return

    // Optimistic local state keeps the UI responsive.
    _conversations.update { list ->
      list.map { conv ->
        if (conv.id == conversationId) conv.copy(isTyping = isTyping) else conv
      }
    }

    scope.launch {
      try {
        val ref = firestore?.collection("conversations")?.document(conversationId)
          ?: return@launch
        val current = ref.get().await().get("typingUserIds") as? List<*>
        val ids = current?.filterIsInstance<String>()?.toMutableSet() ?: mutableSetOf()
        if (isTyping) ids.add(uid) else ids.remove(uid)
        ref.update("typingUserIds", ids.toList()).await()
      } catch (e: Exception) {
        Log.w("ChatRepository", "Typing state update failed", e)
      }
    }
  }

  fun setPresence(isOnline: Boolean) {
    val uid = auth?.currentUser?.uid ?: return
    val now = System.currentTimeMillis()
    _currentUser.update { it.copy(isOnline = isOnline, lastSeen = now) }
    scope.launch {
      try {
        firestore?.collection("users")?.document(uid)?.update(
          mapOf("isOnline" to isOnline, "lastSeen" to now)
        )?.await()
      } catch (e: Exception) {
        Log.w("ChatRepository", "Presence update failed", e)
      }
    }
  }

  fun updateAppearance(newSettings: AppearanceSettings) {
    _appearance.value = newSettings
    val uid = auth?.currentUser?.uid ?: return
    scope.launch {
      runCatching {
        firestore?.collection("users")?.document(uid)?.set(
          mapOf(
            "appearance" to mapOf(
              "isDarkMode" to newSettings.isDarkMode,
              "glassIntensity" to newSettings.glassIntensity,
              "blurAlpha" to newSettings.blurAlpha,
              "accentColorHex" to newSettings.accentColorHex,
              "isReducedMotion" to newSettings.isReducedMotion
            )
          ), SetOptions.merge()
        )?.await()
      }.onFailure { Log.w("ChatRepository", "Appearance sync failed", it) }
    }
  }

  fun updatePrivacy(newSettings: PrivacySettings) {
    _privacy.value = newSettings
    val uid = auth?.currentUser?.uid ?: return
    scope.launch {
      runCatching {
        firestore?.collection("users")?.document(uid)?.set(
          mapOf(
            "privacy" to mapOf(
              "lastSeenVisibility" to newSettings.lastSeenVisibility,
              "onlineVisibility" to newSettings.onlineVisibility,
              "profilePhotoVisibility" to newSettings.profilePhotoVisibility,
              "readReceipts" to newSettings.readReceipts,
              "statusVisibility" to newSettings.statusVisibility,
              "whoCanAddToGroups" to newSettings.whoCanAddToGroups
            )
          ), SetOptions.merge()
        )?.await()
      }.onFailure { Log.w("ChatRepository", "Privacy sync failed", it) }
    }
  }

  fun blockUser(userId: String) {
    _blockedUserIds.update { it + userId }
    persistBlockedUsers()
  }

  fun unblockUser(userId: String) {
    _blockedUserIds.update { it - userId }
    persistBlockedUsers()
  }

  private fun persistBlockedUsers() {
    val uid = auth?.currentUser?.uid ?: return
    scope.launch {
      runCatching {
        firestore?.collection("users")?.document(uid)?.set(
          mapOf("blockedUserIds" to _blockedUserIds.value.toList()), SetOptions.merge()
        )?.await()
      }.onFailure { Log.w("ChatRepository", "Blocked users sync failed", it) }
    }
  }

  fun updateNotifications(newSettings: NotificationSettings) {
    _notifications.value = newSettings
    val uid = auth?.currentUser?.uid ?: return
    scope.launch {
      runCatching {
        firestore?.collection("users")?.document(uid)?.set(
          mapOf("notifications" to mapOf(
            "messages" to newSettings.messages,
            "calls" to newSettings.calls,
            "status" to newSettings.status,
            "vibration" to newSettings.vibration
          )), SetOptions.merge()
        )?.await()
      }.onFailure { Log.w("ChatRepository", "Notification settings sync failed", it) }
    }
  }

  suspend fun deleteAccount(): Result<Unit> {
    val uid = auth?.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
    return try {
      firestore?.collection("users")?.document(uid)?.delete()?.await()
      storage?.reference?.child("users/$uid/profile.jpg")?.delete()?.await()
      auth?.currentUser?.delete()?.await()
      stopFirebaseSync()
      _currentUser.value = User(uid = "usr_guest", displayName = "Liquid User", username = "liquid.user")
      _conversations.value = emptyList()
      _messages.value = emptyMap()
      _blockedUserIds.value = emptySet()
      Result.success(Unit)
    } catch (e: Exception) {
      Log.e("ChatRepository", "Account deletion failed", e)
      Result.failure(e)
    }
  }

  fun addSearchHistory(query: String) {
    if (query.isNotBlank()) {
      _searchHistory.update { (listOf(query) + it.filterNot { q -> q == query }).take(8) }
    }
  }

  fun clearSearchHistory() {
    _searchHistory.value = emptyList()
  }
}
