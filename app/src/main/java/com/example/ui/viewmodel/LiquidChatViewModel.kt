package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.ChatRepository
import com.example.data.repository.preserveMessageDelivery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
      preserveMessageDelivery(previous, current)
    }
    .flowOn(Dispatchers.Default)
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

  @OptIn(FlowPreview::class)
  private val settledSearchQuery = searchQuery
    .map { it.trim() }
    .debounce { if (it.isEmpty()) 0L else 180L }
    .distinctUntilChanged()

  val searchResults = combine(settledSearchQuery, users, conversations, messages) { query, uList, cList, mMaps ->
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
        messages = mMaps.values.asSequence().flatMap { it.asSequence() }
          .filter { it.text.contains(trimmed, true) }.toList()
      )
    }
  }.flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchResults())

  private var searchHistoryJob: Job? = null

  fun onSearchQueryChanged(newQuery: String) {
    _searchQuery.value = newQuery
    searchHistoryJob?.cancel()
    val searchAccount = currentUser.value.uid
    if (newQuery.isNotBlank()) searchHistoryJob = viewModelScope.launch {
      delay(350L)
      if (searchAccount.isNotBlank() && currentUser.value.uid == searchAccount && !deletionPending.value) {
        repository.addSearchHistory(newQuery.trim())
      }
    }
  }

  fun clearSearchQuery() { onSearchQueryChanged("") }
  fun clearSearchHistory() {
    searchHistoryJob?.cancel()
    repository.clearSearchHistory()
  }
  fun setConversationMuted(conversationId: String, muted: Boolean) = repository.setConversationMuted(conversationId, muted)
  fun setDisappearingMessages(conversationId: String, seconds: Long) = repository.setDisappearingMessages(conversationId, seconds)
  fun setConversationWallpaper(conversationId: String, index: Int) = repository.setConversationWallpaper(conversationId, index)
  fun setConversationArchived(conversationId: String, archived: Boolean) = repository.setConversationArchived(conversationId, archived)
  fun deleteChatForMe(conversationId: String) = repository.deleteChatForMe(conversationId)

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
    clearSearchQuery()
    _messageJump.value = null
    repository.logout(password, googleIdToken) { result ->
      onResult(result.isSuccess, result.exceptionOrNull()?.message)
    }
  }
  fun updateNotifications(settings: NotificationSettings) = repository.updateNotifications(settings)

  fun hasGoogleProvider() = repository.hasGoogleProvider()

  fun deleteAccount(password: String = "", googleIdToken: String? = null, onResult: (Boolean, String?) -> Unit) {
    clearSearchQuery()
    _messageJump.value = null
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

}

data class SearchResults(
  val users: List<User> = emptyList(),
  val conversations: List<Conversation> = emptyList(),
  val messages: List<Message> = emptyList()
) {
  val isEmpty: Boolean get() = users.isEmpty() && conversations.isEmpty() && messages.isEmpty()
}
