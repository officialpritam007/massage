package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableStateOf
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
import org.robolectric.annotation.GraphicsMode

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

  @Test
  fun `reply preview forwards the original message id`() {
    var targetId: String? = null
    compose.setContent {
      LiquidChatTheme {
        MessageBubbleV2(
          message = Message(
            id = "reply",
            text = "My reply",
            replyToId = "original",
            replyToText = "Original message",
            replyToSender = "Contact"
          ),
          isMe = false,
          reduced = true,
          onLongClick = {},
          onReply = {},
          onReplyPreviewClick = { targetId = it },
          onMedia = {},
          onReaction = {},
          onRetrySend = {}
        )
      }
    }

    compose.onNodeWithText("Original message").performClick()
    compose.runOnIdle { assertEquals("original", targetId) }
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun `editing expanded long text resets read more controls`() {
    val message = mutableStateOf(
      Message(id = "long", text = (1..15).joinToString("\n") { "Long message line $it" })
    )
    compose.setContent {
      LiquidChatTheme {
        MessageBubbleV2(
          message = message.value,
          isMe = false,
          reduced = true,
          onLongClick = {},
          onReply = {},
          onReplyPreviewClick = {},
          onMedia = {},
          onReaction = {},
          onRetrySend = {}
        )
      }
    }

    compose.onNodeWithText("Read more").performClick()
    compose.onNodeWithText("Read less").fetchSemanticsNode()
    compose.runOnIdle { message.value = message.value.copy(text = "Edited short text", isEdited = true) }
    compose.onNodeWithText("Edited short text").fetchSemanticsNode()
    compose.onNodeWithText("Read less").assertDoesNotExist()
    compose.onNodeWithText("Read more").assertDoesNotExist()
  }
}
