package com.example

import com.example.data.model.MessageDeliveryStatus.*
import com.example.domain.reconcileDelivery
import com.example.domain.shouldFollowMessages
import org.junit.Assert.*
import org.junit.Test

class ChatBehaviorTest {
    @Test fun failedAttemptIsVisibleAndCanRetry() {
        assertEquals(FAILED, reconcileDelivery(SENDING, FAILED))
        assertEquals(SENDING, reconcileDelivery(FAILED, SENDING))
    }
    @Test fun acknowledgedDeliveryNeverRegresses() {
        assertEquals(READ, reconcileDelivery(READ, SENT))
        assertEquals(DELIVERED, reconcileDelivery(DELIVERED, FAILED))
        assertEquals(SENT, reconcileDelivery(SENDING, SENT))
    }
    @Test fun paginationAndReceiptsNeverScroll() {
        assertFalse(shouldFollowMessages(false, "tail", "tail", true))
    }
    @Test fun newIncomingDoesNotInterruptHistoryReading() {
        assertFalse(shouldFollowMessages(false, "old", "new", false))
        assertTrue(shouldFollowMessages(false, "old", "new", true))
        assertTrue(shouldFollowMessages(true, null, "first", true))
        assertFalse(shouldFollowMessages(true, null, null, true))
    }
}
