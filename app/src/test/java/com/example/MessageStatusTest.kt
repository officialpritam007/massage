package com.example

import com.example.data.model.MessageDeliveryStatus.*
import com.example.data.model.acknowledgedStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageStatusTest {
  @Test fun delayedSendResponsePreservesServerReceipts() {
    assertEquals(READ, acknowledgedStatus(READ))
    assertEquals(DELIVERED, acknowledgedStatus(DELIVERED))
  }
  @Test fun successfulRetryAcknowledgesPendingAndFailedMessages() {
    assertEquals(SENT, acknowledgedStatus(SENDING))
    assertEquals(SENT, acknowledgedStatus(FAILED))
    assertEquals(SENT, acknowledgedStatus(SENT))
  }
}
