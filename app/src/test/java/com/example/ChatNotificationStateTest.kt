package com.example

import com.example.notifications.ChatNotificationState
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatNotificationStateTest {
  @After fun reset() {
    ChatNotificationState.setActiveChat(null, null)
    ChatNotificationState.setAppForeground(false)
  }

  @Test fun suppressesOnlyVerifiedSenderInVisibleChat() {
    ChatNotificationState.setAppForeground(true)
    ChatNotificationState.setActiveChat("thread-a", "alice")

    assertTrue(ChatNotificationState.shouldSuppressSystemNotification("alice", "thread-a"))
    assertFalse(ChatNotificationState.shouldSuppressSystemNotification(null, "thread-a"))
    assertFalse(ChatNotificationState.shouldSuppressSystemNotification("", "thread-a"))
    assertFalse(ChatNotificationState.shouldSuppressSystemNotification("bob", "thread-a"))
    assertFalse(ChatNotificationState.shouldSuppressSystemNotification("alice", "thread-b"))

    ChatNotificationState.setAppForeground(false)
    assertFalse(ChatNotificationState.shouldSuppressSystemNotification("alice", "thread-a"))
  }

  @Test fun unknownActivePeerNeverSuppressesNotifications() {
    ChatNotificationState.setAppForeground(true)
    ChatNotificationState.setActiveChat("thread-a", null)
    assertFalse(ChatNotificationState.shouldSuppressSystemNotification("alice", "thread-a"))
    assertFalse(ChatNotificationState.shouldSuppressSystemNotification(null, "thread-a"))
  }
}
