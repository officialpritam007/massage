package com.example

import com.example.data.DeletionCoordinator
import com.example.data.model.Message
import com.example.ui.screens.BubbleRowIdentity
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChatInteractionTest {
  @Test fun typingAndIncomingMessageKeepTheSameListIdentity() {
    val rows = BubbleRowIdentity()
    val old = Message(id = "old", senderId = "peer")
    rows.rows(listOf(old), false, "me")
    val typingKey = rows.rows(listOf(old), true, "me").last().key
    val mine = Message(id = "mine", senderId = "me")
    assertEquals(typingKey, rows.rows(listOf(old, mine), true, "me").last().key)
    val incoming = Message(id = "new", senderId = "peer")
    assertEquals(typingKey, rows.rows(listOf(old, mine, incoming), false, "me").last().key)
    assertEquals(typingKey, rows.rows(listOf(old, mine, incoming), false, "me").last().key)
  }

  @Test fun failedDeletionRestoresItemAndRetryRunsOnlyAfterAnimation() = runTest {
    DeletionCoordinator.clear()
    var attempts = 0
    launch {
      runCatching { DeletionCoordinator.perform("test") { attempts++; if (attempts == 1) error("offline") } }
    }
    runCurrent()
    assertEquals(DeletionCoordinator.Stage.DISSOLVING, DeletionCoordinator.states.value["test"])
    assertEquals(0, attempts)
    advanceUntilIdle()
    assertEquals(DeletionCoordinator.Stage.FAILED, DeletionCoordinator.states.value["test"])
    launch { DeletionCoordinator.retry("test") }
    advanceUntilIdle()
    assertEquals(2, attempts)
    assertFalse(DeletionCoordinator.states.value.containsKey("test"))
    DeletionCoordinator.clear()
  }
}
