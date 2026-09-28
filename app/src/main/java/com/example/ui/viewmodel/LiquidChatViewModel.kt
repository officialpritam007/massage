package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ActiveCallState
import com.example.data.model.AppearanceSettings
import com.example.data.model.CallRecord
import com.example.data.model.CallType
import com.example.data.model.Conversation
import com.example.data.model.Group
import com.example.data.model.Message
import com.example.data.model.MessageType
import com.example.data.model.PrivacySettings
import com.example.data.model.NotificationSettings
import com.example.data.model.StatusType
import com.example.data.model.User
import com.example.data.model.UserStatus
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class LiquidChatViewModel(
  val repository: ChatRepository = ChatRepository()
) : ViewModel() {

  val currentUser: StateFlow<User> = repository.currentUser
  val users: StateFlow<List<User>> = repository.users
  val conversations: StateFlow<List<Conversation>> = repository.conversations
  val messages: StateFlow<Map<String, List<Message>>> = repository.messages
  val groups: StateFlow<List<Group>> = repository.groups
  val statuses: StateFlow<List<UserStatus>> = repository.statuses
  val callRecords: StateFlow<List<CallRecord>> = repository.callRecords
  val activeCall: StateFlow<ActiveCallState?> = repository.activeCall
  val appearance: StateFlow<AppearanceSettings> = repository.appearance
  val privacy: StateFlow<PrivacySettings> = repository.privacy
  val notifications: StateFlow<NotificationSettings> = repository.notifications
  val searchHistory: StateFlow<List<String>> = repository.searchHistory
  val blockedUserIds: StateFlow<Set<String>> = repository.blockedUserIds

  // Search Filter State
  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  val searchResults = combine(
    searchQuery,
    users,
    conversations,
    groups,
    messages
  ) { query, uList, cList, gList, mMaps ->
    if (query.isBlank()) {
      SearchResults()
    } else {
      val trimmed = query.trim()
      val matchedUsers = uList.filter {
        it.displayName.contains(trimmed, ignoreCase = true) ||
          it.username.contains(trimmed, ignoreCase = true) ||
          it.bio.contains(trimmed, ignoreCase = true)
      }
      val matchedConvs = cList.filter {
        it.otherUser.displayName.contains(trimmed, ignoreCase = true) ||
          it.lastMessageText.contains(trimmed, ignoreCase = true)
      }
      val matchedGroups = gList.filter {
        it.name.contains(trimmed, ignoreCase = true) ||
          it.description.contains(trimmed, ignoreCase = true)
      }
      val matchedMsgs = mMaps.values.flatten().filter {
        it.text.contains(trimmed, ignoreCase = true)
      }
      SearchResults(
        users = matchedUsers,
        conversations = matchedConvs,
        groups = matchedGroups,
        messages = matchedMsgs
      )
    }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchResults())

  fun onSearchQueryChanged(newQuery: String) {
    _searchQuery.value = newQuery
    if (newQuery.isNotBlank()) {
      repository.addSearchHistory(newQuery)
    }
  }

  fun clearSearchQuery() {
    _searchQuery.value = ""
  }

  fun clearSearchHistory() {
    repository.clearSearchHistory()
  }

  fun setConversationArchived(conversationId: String, archived: Boolean) {
    repository.setConversationArchived(conversationId, archived)
  }

  // Messaging
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
    voiceDurationSeconds: Int = 0
  ) {
    repository.sendMessage(
      conversationId = conversationId,
      text = text,
      type = type,
      mediaUrl = mediaUrl,
      replyToId = replyToId,
      replyToText = replyToText,
      replyToSender = replyToSender,
      voiceDurationSeconds = voiceDurationSeconds
    )
  }

  fun addReaction(conversationId: String, messageId: String, emoji: String) {
    repository.addReaction(conversationId, messageId, emoji)
  }

  fun deleteMessage(conversationId: String, messageId: String) {
    repository.deleteMessage(conversationId, messageId)
  }

  fun editMessage(conversationId: String, messageId: String, newText: String) {
    repository.editMessage(conversationId, messageId, newText)
  }

  fun pinMessage(conversationId: String, messageId: String) {
    repository.pinMessage(conversationId, messageId)
  }

  fun clearUnread(conversationId: String) {
    repository.clearUnread(conversationId)
  }

  // Groups
  fun createGroup(name: String, description: String, selectedUserIds: List<String>) {
    repository.createGroup(name, description, selectedUserIds)
  }

  fun sendGroupMessage(groupId: String, text: String) {
    repository.sendGroupMessage(groupId, text)
  }

  // Statuses
  fun postStatus(type: StatusType, content: String, bgIndex: Int = 0) {
    repository.postStatus(type, content, bgIndex)
  }

  fun markStatusViewed(statusId: String) {
    repository.markStatusViewed(statusId)
  }

  fun uploadAndPostStatus(uri: Uri, type: StatusType, bgIndex: Int = 0, onResult: (Result<Unit>) -> Unit = {}) =
    repository.uploadAndPostStatus(uri, type, bgIndex, onResult)

  // Calls
  fun startCall(otherUser: User, type: CallType) {
    repository.startCall(otherUser, type)
  }

  fun acceptIncomingCall(context: android.content.Context) = repository.acceptIncomingCall(context)

  fun rejectIncomingCall() = repository.rejectIncomingCall()

  fun initializeWebRtc(context: android.content.Context) = repository.initializeWebRtc(context)

  fun setWebRtcMicrophoneEnabled(enabled: Boolean) = repository.setWebRtcMicrophoneEnabled(enabled)

  fun setWebRtcCameraEnabled(enabled: Boolean) = repository.setWebRtcCameraEnabled(enabled)

  fun toggleMuteCall() {
    repository.toggleMuteCall()
  }

  fun toggleCameraCall() {
    repository.toggleCameraCall()
  }

  fun toggleSpeakerCall() {
    repository.toggleSpeakerCall()
  }

  fun endCall() {
    repository.endCall()
  }

  fun uploadProfilePhoto(uri: Uri, onResult: (Result<String>) -> Unit = {}) =
    repository.uploadProfilePhoto(uri, onResult)

  fun setPresence(isOnline: Boolean) = repository.setPresence(isOnline)

  fun setTyping(conversationId: String, isTyping: Boolean) =
    repository.setTyping(conversationId, isTyping)

  fun isUserLoggedIn(): Boolean = repository.isUserLoggedIn()

  suspend fun signInWithEmail(email: String, pass: String): Result<User> {
    return repository.signInWithEmail(email, pass)
  }

  suspend fun registerWithEmail(
    email: String,
    pass: String,
    fullName: String,
    username: String,
    phoneNumber: String
  ): Result<User> {
    return repository.registerWithEmail(email, pass, fullName, username, phoneNumber)
  }

  fun logout() {
    repository.logout()
  }

  fun updateNotifications(settings: NotificationSettings) {
    repository.updateNotifications(settings)
  }

  fun blockUser(userId: String) { repository.blockUser(userId) }
  fun unblockUser(userId: String) { repository.unblockUser(userId) }

  fun deleteAccount(onResult: (Boolean, String?) -> Unit) {
    viewModelScope.launch {
      val result = repository.deleteAccount()
      onResult(result.isSuccess, result.exceptionOrNull()?.message)
    }
  }

  fun getOrCreateConversationId(otherUid: String): String {
    return repository.getOrCreateConversationId(otherUid)
  }

  fun observeConversation(conversationId: String) {
    repository.observeConversation(conversationId)
  }

  // Profile & Settings
  fun updateProfile(displayName: String, username: String, bio: String, phoneNumber: String) {
    repository.updateProfile(displayName, username, bio, phoneNumber)
  }

  fun updateAppearance(settings: AppearanceSettings) {
    repository.updateAppearance(settings)
  }

  fun updatePrivacy(settings: PrivacySettings) {
    repository.updatePrivacy(settings)
  }

  fun blockUser(userId: String) {
    repository.blockUser(userId)
  }

  fun unblockUser(userId: String) {
    repository.unblockUser(userId)
  }
}

data class SearchResults(
  val users: List<User> = emptyList(),
  val conversations: List<Conversation> = emptyList(),
  val groups: List<Group> = emptyList(),
  val messages: List<Message> = emptyList()
) {
  val isEmpty: Boolean get() = users.isEmpty() && conversations.isEmpty() && groups.isEmpty() && messages.isEmpty()
}
