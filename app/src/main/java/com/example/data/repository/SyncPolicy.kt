package com.example.data.repository

import com.example.data.model.Message
import com.example.data.model.MessageDeliveryStatus

/** Reconcile only changed threads; unchanged cached history keeps its list and row instances. */
internal fun preserveMessageDelivery(
  previous: Map<String, List<Message>>,
  current: Map<String, List<Message>>
): Map<String, List<Message>> {
  if (previous === current) return current
  var reconciled: MutableMap<String, List<Message>>? = null
  current.forEach { (conversationId, messages) ->
    val old = previous[conversationId] ?: return@forEach
    if (old === messages || old.isEmpty() || messages.isEmpty()) return@forEach
    val oldById = old.associateBy { it.id }
    var changed: MutableList<Message>? = null
    messages.forEachIndexed { index, message ->
      val prior = oldById[message.id]
      if (prior != null && prior.senderId == message.senderId &&
        prior.status != MessageDeliveryStatus.FAILED && message.status != MessageDeliveryStatus.FAILED &&
        deliveryRank(prior.status) > deliveryRank(message.status)
      ) {
        val updated = changed ?: messages.toMutableList().also { changed = it }
        updated[index] = message.copy(status = prior.status)
      }
    }
    changed?.let { updated ->
      val result = reconciled ?: current.toMutableMap().also { reconciled = it }
      result[conversationId] = updated
    }
  }
  return reconciled ?: current
}

private fun deliveryRank(status: MessageDeliveryStatus): Int = when (status) {
  MessageDeliveryStatus.FAILED -> -1
  MessageDeliveryStatus.SENDING -> 0
  MessageDeliveryStatus.SENT -> 1
  MessageDeliveryStatus.DELIVERED -> 2
  MessageDeliveryStatus.READ -> 3
}

/** An idle heartbeat need not rebuild all cached history before the next expiry deadline. */
internal class MessageExpirySchedule {
  private var lastMessages: Map<String, List<Message>>? = null
  private var nextExpiry: Long? = null

  fun reset() {
    lastMessages = null
    nextExpiry = null
  }

  fun hasExpired(messages: Map<String, List<Message>>, now: Long): Boolean {
    if (lastMessages !== messages) {
      lastMessages = messages
      nextExpiry = messages.values.asSequence()
        .flatMap { it.asSequence() }
        .mapNotNull { it.expiresAt }
        .minOrNull()
    }
    return nextExpiry?.let { it <= now } ?: false
  }
}

/** A delayed server snapshot must not overwrite a newer unsaved local preference. */
internal class PendingSetting<T> {
  private var pending: T? = null
  fun changed(value: T) { pending = value }
  fun clear() { pending = null }
  fun accept(remote: T, committed: Boolean = true): Boolean {
    val local = pending ?: return true
    if (!committed) return false
    if (local != remote) return false
    pending = null
    return true
  }
}

/** Lifecycle transitions are immediate; duplicate lifecycle/heartbeat writes are suppressed. */
internal class PresenceWriteGate(private val intervalMs: Long = 60_000L) {
  private var lastValue: Boolean? = null
  private var lastWriteAt = 0L
  fun shouldWrite(value: Boolean, now: Long, force: Boolean = false): Boolean {
    if (!force && lastValue == value && (!value || now - lastWriteAt < intervalMs)) return false
    lastValue = value
    lastWriteAt = now
    return true
  }
  fun reset() { lastValue = null; lastWriteAt = 0L }
}


/** Transient transport failures must stay queued instead of becoming permanently failed. */
internal fun shouldAutoRetryOutbox(
  firestoreCodeName: String?,
  isIoFailure: Boolean = false,
  timedOut: Boolean = false,
  message: String = ""
): Boolean {
  if (isIoFailure || timedOut) return true
  if (firestoreCodeName in setOf(
      "UNAVAILABLE",
      "DEADLINE_EXCEEDED",
      "ABORTED",
      "INTERNAL",
      "UNKNOWN",
      "CANCELLED"
    )
  ) return true

  val normalized = message.lowercase()
  if ("unavailable" in normalized || "network" in normalized || "offline" in normalized) return true
  return listOf(
    "connection unavailable",
    "failed to connect",
    "unable to resolve host",
    "host is unresolved",
    "dns",
    "transport closed",
    "channel shutdown",
    "connection reset",
    "timeout",
    "timed out"
  ).any(normalized::contains)
}


/** Presence is optional realtime metadata and must never write from cache-only chat state. */
internal fun shouldAttemptPresenceWrite(
  serverConfirmed: Boolean,
  hasPeer: Boolean,
  locallyBlocked: Boolean
): Boolean = serverConfirmed && hasPeer && !locallyBlocked

/** A blocked/deleted peer can legitimately make the optional presence path unavailable. */
internal fun shouldSilencePresenceFailure(firestoreCodeName: String?): Boolean =
  firestoreCodeName in setOf("PERMISSION_DENIED", "NOT_FOUND")
