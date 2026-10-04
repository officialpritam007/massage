package com.example

import com.example.data.model.Message
import com.example.data.repository.stabilizeDecodedMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageStatePolicyTest {
  private val account = "me"

  @Test
  fun `missing peer identity does not flash unavailable row on first load`() {
    val candidate = Message(id = "m1", senderId = "peer", encryptionUnavailable = true)
    assertNull(stabilizeDecodedMessage(candidate, null, account, peerKeyReady = false))
  }

  @Test
  fun `transient decode failure cannot replace previously decrypted message`() {
    val previous = Message(id = "m1", senderId = "peer", text = "hello")
    val candidate = previous.copy(text = "Message unavailable", encryptionUnavailable = true)
    assertEquals(previous, stabilizeDecodedMessage(candidate, previous, account, peerKeyReady = true))
  }

  @Test
  fun `verified key failure is shown when no stable value exists`() {
    val candidate = Message(id = "m1", senderId = "peer", text = "Message unavailable", encryptionUnavailable = true)
    assertEquals(candidate, stabilizeDecodedMessage(candidate, null, account, peerKeyReady = true))
  }

  @Test
  fun `successfully decoded update replaces old value`() {
    val previous = Message(id = "m1", senderId = "peer", text = "old")
    val candidate = previous.copy(text = "new", encryptionUnavailable = false)
    assertEquals(candidate, stabilizeDecodedMessage(candidate, previous, account, peerKeyReady = true))
  }
}
