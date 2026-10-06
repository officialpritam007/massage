package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LiquidChatViewModel(
  val repository: ChatRepository = ChatRepository()
) : ViewModel() {

  val currentUser: StateFlow<User> = repository.currentUser
  val users: StateFlow<List<User>> = repository.users
  val conversations: StateFlow<List<Conversation>> = repository.conversations

  // A cached/server snapshot can occasionally arrive after a newer optimistic/realtime state.
  // Preserve delivery monotonicity in the UI while still accepting every other field update.
  val messages: StateFlow<Map<String, List<Message>>> = repository.messages
    .scan(repository.messages.value) { previous, current ->
      current.mapValues { (conversationId, list) ->
        val oldById = previous[conversationId].orEmpty().associateBy { it.id }
        list.map { message ->
          val old = oldById[message.id]
          if (
            message.status != MessageDeliveryStatus.FAILED &&
            old != null &&
            old.status != MessageDeliveryStatus.FAILED &&
            deliveryRank(old.status) > deliveryRank(message.status)
          ) {
            message.copy(status = old.status)
          } else {
            message
          }
        }
      }
    }
    .stateIn(viewModelScope, SharingStarted.Eagerly, repository.messages.value)

  val appearance: StateFlow<AppearanceSettings> = repository.appearance
  val themeReady: StateFlow<Boolean> = repository.themeReady
  val privacy: StateFlow<PrivacySettings> = repository.privacy
  val notifications: StateFlow<NotificationSettings> = repository.notifications
  val searchHistory: StateFlow<List<String>> = repository.searchHistory
  val blockedUserIds: StateFlow<Set<String>> = repository.blockedUserIds

  val error = repository.error
  val loading = repository.loading
  val upload = repository.upload
  val deletionStatus = repository.deletionStatus
  val deletionPending = repository.deletionPending
  val deletionComplete = repository.deletionComplete
  val deletionRunning = repository.deletionRunning
  override fun onCleared() { repository.close() }

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _messageJump = MutableStateFlow<Pair<String, String>?>(null)
  val messageJump: StateFlow<Pair<String, String>?> = _messageJump.asStateFlow()

  fun requestMessageJump(conversationId: String, messageId: String) {
    _messageJump.value = conversationId to messageId
  }

  fun clearMessageJump(conversationId: String, messageId: String) {
    if (_messageJump.value == (conversationId to messageId)) _messageJump.value = null
  }

  val searchResults = combine(searchQuery, users, conversations, messages) { query, uList, cList, mMaps ->
    if (query.isBlank()) SearchResults()
    else {
      val trimmed = query.trim()
      SearchResults(
        users = uList.filter {
          it.displayName.contains(trimmed, true) || it.username.contains(trimmed, true) || it.bio.contains(trimmed, true)
        },
        conversations = cList.filter {
          it.otherUser.displayName.contains(trimmed, true) || it.lastMessageText.contains(trimmed, true)
        },
        messages = mMaps.values.flatten().filter { it.text.contains(trimmed, true) }
      )
    }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchResults())

  fun onSearchQueryChanged(newQuery: String) {
    _searchQuery.value = newQuery
    if (newQuery.isNotBlank()) repository.addSearchHistory(newQuery)
  }

  fun clearSearchQuery() { _searchQuery.value = "" }
  fun clearSearchHistory() = repository.clearSearchHistory()
  fun setConversationMuted(conversationId: String, muted: Boolean) = repository.setConversationMuted(conversationId, muted)
  fun setDisappearingMessages(conversationId: String, seconds: Long) = repository.setDisappearingMessages(conversationId, seconds)
  fun setConversationWallpaper(conversationId: String, index: Int) = repository.setConversationWallpaper(conversationId, index)
  fun setConversationArchived(conversationId: String, archived: Boolean) = repository.setConversationArchived(conversationId, archived)
  fun deleteChatForMe(conversationId: String) = repository.deleteChatForMe(conversationId)

  fun resetPassword(email: String, onResult: (Result<Unit>) -> Unit) =
    repository.resetPassword(email, onResult)

  fun uploadChatMedia(
    conversationId: String,
    uri: Uri,
    type: MessageType,
    onResult: (Result<String>) -> Unit = {}
  ) = repository.uploadChatMedia(conversationId, uri, type, onResult)

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
  ) = repository.sendMessage(
    conversationId = conversationId,
    text = text,
    type = type,
    mediaUrl = mediaUrl,
    replyToId = replyToId,
    replyToText = replyToText,
    replyToSender = replyToSender,
    voiceDurationSeconds = voiceDurationSeconds,
    waveform = waveform
  )

  fun addReaction(conversationId: String, messageId: String, emoji: String) =
    repository.addReaction(conversationId, messageId, emoji)
  fun deleteMessageForMe(conversationId: String, messageId: String) =
    repository.deleteMessageForMe(conversationId, messageId)
  fun deleteMessageForEveryone(conversationId: String, messageId: String) =
    repository.deleteMessageForEveryone(conversationId, messageId)
  fun deleteMessage(conversationId: String, messageId: String) =
    repository.deleteMessageForEveryone(conversationId, messageId)
  fun editMessage(conversationId: String, messageId: String, newText: String) =
    repository.editMessage(conversationId, messageId, newText)
  fun pinMessage(conversationId: String, messageId: String) = repository.pinMessage(conversationId, messageId)
  fun clearUnread(conversationId: String) = repository.clearUnread(conversationId)

  fun uploadProfilePhoto(uri: Uri, onResult: (Result<String>) -> Unit = {}) =
    repository.uploadProfilePhoto(uri, onResult)
  fun setPresence(isOnline: Boolean) = repository.setPresence(isOnline)
  fun setTyping(conversationId: String, isTyping: Boolean) = repository.setTyping(conversationId, isTyping)
  fun isUserLoggedIn(): Boolean = repository.isUserLoggedIn()

  suspend fun signInWithEmail(email: String, pass: String): Result<User> = repository.signInWithEmail(email, pass)
  suspend fun signInWithGoogleIdToken(idToken: String): Result<User> =
    repository.signInWithGoogleIdToken(idToken)
  suspend fun registerWithEmail(
    email: String,
    pass: String,
    fullName: String,
    username: String,
    phoneNumber: String
  ): Result<User> = repository.registerWithEmail(email, pass, fullName, username, phoneNumber)

  fun logout(password: String = "", googleIdToken: String? = null, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
    repository.logout(password, googleIdToken) { result ->
      onResult(result.isSuccess, result.exceptionOrNull()?.message)
    }
  }
  fun updateNotifications(settings: NotificationSettings) = repository.updateNotifications(settings)

  fun hasGoogleProvider() = repository.hasGoogleProvider()

  fun deleteAccount(password: String = "", googleIdToken: String? = null, onResult: (Boolean, String?) -> Unit) {
    viewModelScope.launch {
      val result = repository.deleteAccount(password, googleIdToken)
      onResult(result.isSuccess, result.exceptionOrNull()?.message)
    }
  }

  suspend fun checkUsernameAvailability(username: String): Result<Boolean> =
    repository.checkUsernameAvailability(username)

  fun getOrCreateConversationId(otherUid: String): String = repository.getOrCreateConversationId(otherUid)
  fun observeConversation(conversationId: String) = repository.observeConversation(conversationId)
  fun updateProfile(displayName: String, username: String, bio: String, phoneNumber: String) =
    repository.updateProfile(displayName, username, bio, phoneNumber)
  fun updateAppearance(settings: AppearanceSettings) = repository.updateAppearance(settings)
  fun updatePrivacy(settings: PrivacySettings) = repository.updatePrivacy(settings)
  fun blockUser(userId: String) = repository.blockUser(userId)
  fun unblockUser(userId: String) = repository.unblockUser(userId)

  private fun deliveryRank(status: MessageDeliveryStatus): Int = when (status) {
    MessageDeliveryStatus.FAILED -> -1
    MessageDeliveryStatus.SENDING -> 0
    MessageDeliveryStatus.SENT -> 1
    MessageDeliveryStatus.DELIVERED -> 2
    MessageDeliveryStatus.READ -> 3
  }
}

data class SearchResults(
  val users: List<User> = emptyList(),
  val conversations: List<Conversation> = emptyList(),
  val messages: List<Message> = emptyList()
) {
  val isEmpty: Boolean get() = users.isEmpty() && conversations.isEmpty() && messages.isEmpty()
}
