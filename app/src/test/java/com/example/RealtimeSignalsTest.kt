package com.example

import com.example.data.repository.RealtimeSignals
import org.junit.Assert.*
import org.junit.Test

class RealtimeSignalsTest {
  @Test fun `typing expires at three seconds without another event`() {
    var now = 100L
    val signals = RealtimeSignals { now }
    signals.onTyping("thread", "peer", true)
    now += 2_999
    assertTrue(signals.isTyping("thread"))
    now++
    assertFalse(signals.isTyping("thread"))
    assertTrue(signals.isActive("peer"))
  }
  @Test fun `fresh event extends only its own thread and explicit stop wins`() {
    var now = 100L
    val signals = RealtimeSignals { now }
    signals.onTyping("a", "peer", true)
    now += 2_000
    signals.onTyping("b", "other", true)
    now += 1_001
    assertFalse(signals.isTyping("a"))
    assertTrue(signals.isTyping("b"))
    signals.onTyping("b", "other", false)
    assertFalse(signals.isTyping("b"))
    signals.offline("other")
    assertFalse(signals.isActive("other"))
    now += 15_000
    assertFalse(signals.isActive("peer"))
  }
}
