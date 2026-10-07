package com.example

import com.example.data.model.Message
import com.example.data.model.MessageDeliveryStatus
import com.example.data.model.MessageReaction
import com.example.data.repository.MessageExpirySchedule
import com.example.data.repository.preserveMessageDelivery
import org.junit.Assert.*
import org.junit.Test

class MessageSyncPolicyTest {
  private fun message(id: String, status: MessageDeliveryStatus = MessageDeliveryStatus.SENT) =
    Message(id = id, conversationId = "chat", senderId = "sender", createdAt = 1L, status = status)

  private class CountingMessages(private val item: Message, override val size: Int) : AbstractList<Message>() {
    var reads = 0
    override fun get(index: Int): Message { reads++; return item }
  }

  @Test fun `one receipt update does not traverse other cached threads`() {
    val history = CountingMessages(message("older"), 10_000)
    val sent = message("recent")
    val previous = mapOf("history" to history, "chat" to listOf(sent))
    val current = previous + ("chat" to listOf(sent.copy(status = MessageDeliveryStatus.DELIVERED)))

    val result = preserveMessageDelivery(previous, current)

    assertSame(current, result)
    assertSame(history, result["history"])
    assertEquals(0, history.reads)
  }

  @Test fun `delayed delivery regression keeps latest text and reactions`() {
    val read = message("recent", MessageDeliveryStatus.READ)
    val delayed = read.copy(
      text = "Edited text", isEdited = true,
      reactions = listOf(MessageReaction("👍", listOf("peer"))),
      status = MessageDeliveryStatus.SENT
    )

    val result = preserveMessageDelivery(mapOf("chat" to listOf(read)), mapOf("chat" to listOf(delayed)))

    assertEquals(delayed.copy(status = MessageDeliveryStatus.READ), result["chat"]?.single())
  }

  @Test fun `failed sends and explicit retries remain visible`() {
    val sent = message("outgoing", MessageDeliveryStatus.SENT)
    val failure = sent.copy(status = MessageDeliveryStatus.FAILED)
    val failedState = mapOf("chat" to listOf(failure))
    assertSame(failedState, preserveMessageDelivery(mapOf("chat" to listOf(sent)), failedState))

    val retryState = mapOf("chat" to listOf(failure.copy(status = MessageDeliveryStatus.SENDING)))
    assertSame(retryState, preserveMessageDelivery(failedState, retryState))
  }

  @Test fun `deleted messages removed threads and logout are not restored`() {
    val retained = message("keep", MessageDeliveryStatus.READ)
    val previous = mapOf("chat" to listOf(retained, message("deleted")), "removed" to listOf(message("old")))
    val current = mapOf("chat" to listOf(retained))

    assertSame(current, preserveMessageDelivery(previous, current))
    val signedOut = emptyMap<String, List<Message>>()
    assertSame(signedOut, preserveMessageDelivery(current, signedOut))
    val nextAccount = mapOf("new-chat" to listOf(message("new")))
    assertSame(nextAccount, preserveMessageDelivery(signedOut, nextAccount))
  }

  @Test fun `prior account sender cannot promote a different senders message`() {
    val old = message("same-id", MessageDeliveryStatus.READ)
    val current = mapOf("chat" to listOf(old.copy(senderId = "different", status = MessageDeliveryStatus.SENT)))
    assertSame(current, preserveMessageDelivery(mapOf("chat" to listOf(old)), current))
  }

  @Test fun `idle expiry checks inspect unchanged history only once`() {
    val schedule = MessageExpirySchedule()
    val history = CountingMessages(message("regular"), 1_000)
    val state = mapOf("chat" to history)

    assertFalse(schedule.hasExpired(state, 1L))
    val firstReads = history.reads
    repeat(60) { assertFalse(schedule.hasExpired(state, 1_000L + it)) }

    assertEquals(1_000, firstReads)
    assertEquals(firstReads, history.reads)
  }

  @Test fun `disappearing messages expire at their earliest deadline`() {
    val schedule = MessageExpirySchedule()
    val later = message("later").copy(expiresAt = 5_000L)
    val earlier = message("earlier").copy(expiresAt = 3_000L)
    val state = mapOf("chat" to listOf(later, earlier, message("permanent")))

    assertFalse(schedule.hasExpired(state, 2_999L))
    assertTrue(schedule.hasExpired(state, 3_000L))
    val afterRemoval = mapOf("chat" to listOf(later, message("permanent")))
    assertFalse(schedule.hasExpired(afterRemoval, 3_001L))
    assertTrue(schedule.hasExpired(afterRemoval, 5_000L))
    assertFalse(schedule.hasExpired(emptyMap(), 6_000L))
  }

  @Test fun `new messages can bring forward the next expiry`() {
    val schedule = MessageExpirySchedule()
    val state = mapOf("chat" to listOf(message("later").copy(expiresAt = 5_000L)))
    assertFalse(schedule.hasExpired(state, 1_000L))
    val updated = state + ("new-chat" to listOf(message("early").copy(expiresAt = 1_500L)))
    assertTrue(schedule.hasExpired(updated, 1_500L))
  }
}
