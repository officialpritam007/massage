package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.model.Message
import com.example.data.model.MessageDeliveryStatus
import com.example.ui.screens.MessageBubbleV2
import com.example.ui.theme.LiquidChatTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConversationMessageV2Test {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `failed text bubble stays readable and invokes retry`() {
    var retries = 0
    compose.setContent {
      LiquidChatTheme {
        MessageBubbleV2(
          message = Message(
            id = "m1",
            text = "hello from lightweight bubble",
            status = MessageDeliveryStatus.FAILED
          ),
          isMe = true,
          reduced = true,
          onLongClick = {},
          onReply = {},
          onReplyPreviewClick = {},
          onMedia = {},
          onReaction = {},
          onRetrySend = { retries++ }
        )
      }
    }

    compose.onNodeWithText("hello from lightweight bubble").fetchSemanticsNode()
    compose.onNodeWithText("Failed • tap to retry").performClick()
    compose.runOnIdle { assertEquals(1, retries) }
  }
}
