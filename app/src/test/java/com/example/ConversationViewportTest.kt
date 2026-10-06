package com.example

import com.example.ui.screens.messageViewportTargetV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationViewportTest {
  @Test fun `reply points at the message with and without a history loader`() {
    val ids = listOf("one", "reply-target", "three")
    assertEquals(1, messageViewportTargetV2(ids, "reply-target", false)?.index)
    assertEquals(2, messageViewportTargetV2(ids, "reply-target", true)?.index)
  }

  @Test fun `prepending history restores the visible message by id and scroll offset`() {
    val ids = listOf("older-one", "older-two", "anchor", "newer")
    val target = messageViewportTargetV2(ids, "anchor", false, -37)
    assertEquals(2, target?.index)
    assertEquals(37, target?.scrollOffset)
  }

  @Test fun `removed reply target does not scroll to an unrelated item`() {
    assertNull(messageViewportTargetV2(listOf("one", "two"), "deleted", false))
  }
}
