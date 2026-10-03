package com.example

import com.example.notifications.NotificationText
import com.example.data.crypto.MessageContentDecoder
import org.junit.Assert.*
import org.junit.Test

class NotificationContentTest {
  @Test fun hiddenPreviewNeverLeaksSenderTextOrMediaType() {
    assertEquals("New message", NotificationText.body("TEXT", "private message", false))
    assertEquals("New message", NotificationText.body("IMAGE", "private caption", false))
  }

  @Test fun decryptedTextIsShownEvenWhenItMentionsEncryption() {
    assertEquals("My encrypted message arrived", NotificationText.body("TEXT", "My encrypted message arrived", true))
    assertEquals("Encrypted message", NotificationText.body("TEXT", "Encrypted message", true))
    assertEquals(240, NotificationText.body("TEXT", "x".repeat(500), true).length)
  }

  @Test fun unavailableCiphertextFallsBackToUsefulMediaLabels() {
    assertEquals("New message", NotificationText.body("TEXT", null, true))
    assertEquals("Photo", NotificationText.body("IMAGE", null, true))
    assertEquals("Video", NotificationText.body("VIDEO", "", true))
    assertEquals("Voice message", NotificationText.body("VOICE", "", true))
    assertEquals("Document", NotificationText.body("FILE", null, true))
  }

  @Test fun editingAnyAuthenticatedEnvelopeFieldInvalidatesPreviewCache() {
    val original = linkedMapOf<String, Any?>("e2eeCiphertext" to "old", "e2eeSignature" to "signature", "e2eeSenderKeyId" to "sender")
    assertEquals(MessageContentDecoder.revision(original), MessageContentDecoder.revision(original.entries.reversed().associate { it.toPair() }))
    original.keys.forEach { field ->
      assertNotEquals(MessageContentDecoder.revision(original), MessageContentDecoder.revision(original + (field to "changed")))
    }
  }
}
