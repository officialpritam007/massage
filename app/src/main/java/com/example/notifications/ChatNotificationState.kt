package com.example.notifications

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class ForegroundMessageEvent(
  val senderId: String,
  val conversationId: String,
  val messageId: String
)

object ChatNotificationState {
  @Volatile var appInForeground: Boolean = false
    private set
  @Volatile var currentActiveChatUserId: String? = null
    private set
  @Volatile var currentConversationId: String? = null
    private set

  private val _foregroundMessages = MutableSharedFlow<ForegroundMessageEvent>(
    extraBufferCapacity = 32,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )
  val foregroundMessages = _foregroundMessages.asSharedFlow()

  fun setAppForeground(foreground: Boolean) {
    appInForeground = foreground
  }

  fun setActiveChat(conversationId: String?, userId: String?) {
    currentConversationId = conversationId?.takeIf { it.isNotBlank() }
    currentActiveChatUserId = userId?.takeIf { it.isNotBlank() }
  }

  fun clearActiveChat(conversationId: String) {
    if (currentConversationId == conversationId) {
      currentConversationId = null
      currentActiveChatUserId = null
    }
  }

  fun shouldSuppressSystemNotification(senderId: String?, conversationId: String): Boolean {
    if (!appInForeground || currentConversationId != conversationId) return false
    val activeUser = currentActiveChatUserId
    return senderId.isNullOrBlank() || activeUser.isNullOrBlank() || senderId == activeUser
  }

  fun dispatchForegroundMessage(senderId: String?, conversationId: String, messageId: String) {
    _foregroundMessages.tryEmit(
      ForegroundMessageEvent(senderId.orEmpty(), conversationId, messageId)
    )
  }
}
