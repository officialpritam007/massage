package com.example.data.repository

/** Local monotonic deadlines. Cached/replayed history must never refresh presence or typing. */
class RealtimeSignals(private val clock: () -> Long) {
  private val activity = mutableMapOf<String, Long>()
  private val typing = mutableMapOf<String, Long>()

  @Synchronized fun onActivity(peerId: String) { activity[peerId] = clock() + 15_000L }
  @Synchronized fun onTyping(threadId: String, peerId: String, active: Boolean) {
    if (active) {
      typing[threadId] = clock() + 3_000L
      onActivity(peerId)
    } else typing.remove(threadId)
  }
  @Synchronized fun isTyping(threadId: String) = clock() < (typing[threadId] ?: 0L)
  @Synchronized fun isActive(peerId: String) = clock() < (activity[peerId] ?: 0L)
  @Synchronized fun offline(peerId: String) { activity.remove(peerId) }
  @Synchronized fun clear() { activity.clear(); typing.clear() }
}
