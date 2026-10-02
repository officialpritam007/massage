package com.example.domain

import com.example.data.model.MessageDeliveryStatus

/** Failure/retry are local attempt states, not backwards delivery receipts. */
fun reconcileDelivery(previous: MessageDeliveryStatus, next: MessageDeliveryStatus): MessageDeliveryStatus {
    fun rank(s: MessageDeliveryStatus) = when (s) {
        MessageDeliveryStatus.SENT -> 1
        MessageDeliveryStatus.DELIVERED -> 2
        MessageDeliveryStatus.READ -> 3
        else -> 0
    }
    return if (rank(previous) > rank(next)) previous else next
}

/** Loading older rows, edits and receipts must never pull the reader to the bottom. */
fun shouldFollowMessages(initial: Boolean, previousLastId: String?, nextLastId: String?, following: Boolean): Boolean =
    nextLastId != null && (initial || (previousLastId != nextLastId && following))
