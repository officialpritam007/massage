package com.example

import com.example.notifications.NotificationText
import org.junit.Assert.*
import org.junit.Test

class NotificationContentTest {
  @Test fun hiddenPreviewNeverLeaksSenderTextOrMediaType() {
    assertEquals("New message", NotificationText.body("TEXT", "private message", false))
    assertEquals("New message", NotificationText.body("IMAGE", "private caption", false))
  }

  @Test fun plaintextTextIsShownEvenWhenItMentionsEncryption() {
    assertEquals("My encrypted message arrived", NotificationText.body("TEXT", "My encrypted message arrived", true))
    assertEquals("Encrypted message", NotificationText.body("TEXT", "Encrypted message", true))
    assertEquals(240, NotificationText.body("TEXT", "x".repeat(500), true).length)
  }

  @Test fun missingTextFallsBackToUsefulMediaLabels() {
    assertEquals("New message", NotificationText.body("TEXT", null, true))
    assertEquals("Photo", NotificationText.body("IMAGE", null, true))
    assertEquals("Video", NotificationText.body("VIDEO", "", true))
    assertEquals("Voice message", NotificationText.body("VOICE", "", true))
    assertEquals("Document", NotificationText.body("FILE", null, true))
  }

}
