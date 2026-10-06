package com.example

import com.example.data.model.MessageType
import com.example.data.repository.MediaUploadRetryKey
import com.example.data.repository.MediaUploadRetryStore
import org.junit.Assert.*
import org.junit.Test

class MediaUploadRetryTest {
  private val selection = MediaUploadRetryKey("alice", "alice_bob", "content://photos/1", MessageType.IMAGE)
  private data class Attempt(val key: MediaUploadRetryKey, val complete: () -> Unit)

  @Test fun freshEquivalentAttemptCannotRetryThePreviousSendCallback() {
    var oldSends = 0
    var newSends = 0
    val retries = MediaUploadRetryStore<Attempt> { it.key }
    retries.record(Attempt(selection) { oldSends++ })
    // Starting a fresh upload of the retained preview discards the failed attempt.
    retries.remove(selection)
    Attempt(selection) { newSends++ }.complete()
    retries.remove(selection)?.complete?.invoke()
    assertEquals(0, oldSends)
    assertEquals(1, newSends)
    assertNull(retries.pending)
  }

  @Test fun retryIsConsumedOnceAndCannotSendTwice() {
    var sends = 0
    val retries = MediaUploadRetryStore<Attempt> { it.key }
    retries.record(Attempt(selection) { sends++ })
    retries.remove(selection)?.complete?.invoke()
    retries.remove(selection)?.complete?.invoke()
    assertEquals(1, sends)
  }

  @Test fun accountConversationUriAndTypeMustAllMatch() {
    val retries = MediaUploadRetryStore<Attempt> { it.key }
    val original = Attempt(selection) {}
    retries.record(original)
    listOf(
      selection.copy(account = "mallory"),
      selection.copy(conversationId = "alice_charlie"),
      selection.copy(uri = "content://photos/2"),
      selection.copy(type = MessageType.VIDEO)
    ).forEach { unrelated ->
      assertFalse(retries.matches(unrelated))
      assertNull(retries.remove(unrelated))
      assertSame(original, retries.pending)
    }
    assertSame(original, retries.remove(selection))
  }

  @Test fun logoutOrRepositoryCloseDropsTheRetainedCallback() {
    val retries = MediaUploadRetryStore<Attempt> { it.key }
    retries.record(Attempt(selection) { fail("Callback survived session reset") })
    retries.clear()
    assertNull(retries.remove(selection))
  }
}
