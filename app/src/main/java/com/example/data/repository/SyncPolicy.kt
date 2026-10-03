package com.example.data.repository

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
