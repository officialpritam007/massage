package com.example

import com.example.data.model.AppearanceSettings
import com.example.data.repository.PendingSetting
import com.example.data.repository.PresenceWriteGate
import com.example.data.repository.shouldAutoRetryOutbox
import org.junit.Assert.*
import org.junit.Test

class SyncPolicyTest {
  @Test fun `rapid theme changes survive older profile snapshots and uncommitted echoes`() {
    val state = PendingSetting<AppearanceSettings>()
    val dark = AppearanceSettings(isDarkMode = true)
    val light = dark.copy(isDarkMode = false)
    val blueLight = light.copy(accentColorHex = "#176BFF")
    state.changed(light)
    state.changed(blueLight)
    assertFalse(state.accept(dark))
    assertFalse(state.accept(light))
    assertFalse(state.accept(blueLight, committed = false))
    assertFalse(state.accept(dark))
    assertTrue(state.accept(blueLight))
    assertTrue(state.accept(dark)) // a subsequent committed change from another device
  }

  @Test fun `signing out clears pending preference ownership`() {
    val state = PendingSetting<String>()
    state.changed("account-a")
    state.clear()
    assertTrue(state.accept("account-b"))
  }

  @Test fun `duplicate starts and idle heartbeats cannot amplify presence writes`() {
    val gate = PresenceWriteGate()
    assertTrue(gate.shouldWrite(true, 0))
    repeat(59) { assertFalse(gate.shouldWrite(true, (it + 1) * 1000L)) }
    assertTrue(gate.shouldWrite(true, 60_000))
    assertFalse(gate.shouldWrite(true, 60_001))
    assertTrue(gate.shouldWrite(false, 60_002))
    assertFalse(gate.shouldWrite(false, 600_000))
    assertTrue(gate.shouldWrite(true, 600_001))
    assertTrue(gate.shouldWrite(true, 600_002, force = true)) // explicit privacy change
  }
  @Test fun `transient message transport failures remain auto retryable`() {
    assertTrue(shouldAutoRetryOutbox("UNAVAILABLE"))
    assertTrue(shouldAutoRetryOutbox("DEADLINE_EXCEEDED"))
    assertTrue(shouldAutoRetryOutbox("ABORTED"))
    assertTrue(shouldAutoRetryOutbox(null, isIoFailure = true))
    assertTrue(shouldAutoRetryOutbox(null, timedOut = true))
    assertTrue(shouldAutoRetryOutbox(null, message = "Failed to get document because the client is offline."))
    assertTrue(shouldAutoRetryOutbox(null, message = "Unable to resolve host firestore.googleapis.com"))
    assertTrue(shouldAutoRetryOutbox(null, message = "Connection unavailable"))
    assertFalse(shouldAutoRetryOutbox("PERMISSION_DENIED", message = "Missing or insufficient permissions"))
    assertFalse(shouldAutoRetryOutbox("FAILED_PRECONDITION"))
  }

}
