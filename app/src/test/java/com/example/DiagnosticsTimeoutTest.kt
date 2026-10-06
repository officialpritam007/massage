package com.example

import com.example.ui.screens.boundedDiagnosticCheck
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class DiagnosticsTimeoutTest {
  @Test fun completedServerProbeReturnsItsResult() = runTest {
    assertEquals("server", boundedDiagnosticCheck { "server" }.getOrThrow())
  }

  @Test fun unavailableServerCannotLeaveDiagnosticsSpinningForever() = runTest {
    val result = boundedDiagnosticCheck(timeoutMs = 100) { delay(10_000); "late" }
    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("timed out"))
  }

  @Test fun serviceFailureIsReportedWithoutAFalsePass() = runTest {
    val failure = IllegalStateException("Permission denied")
    val result = boundedDiagnosticCheck<String> { throw failure }
    assertSame(failure, result.exceptionOrNull())
  }

  @Test fun callerCancellationIsPropagatedInsteadOfBecomingAServiceFailure() = runTest {
    try {
      withTimeout(50) {
        boundedDiagnosticCheck(timeoutMs = 1_000) { delay(10_000) }
      }
      fail("The enclosing lifecycle cancellation was swallowed")
    } catch (_: TimeoutCancellationException) {
      // Closing the screen or cancelling its owner must cancel its probes too.
    }
  }
}
