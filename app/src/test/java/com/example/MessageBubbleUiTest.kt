package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.data.model.Message
import com.example.ui.screens.MessageBubble
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessageBubbleUiTest {
  @get:Rule val compose = createComposeRule()

  @Test fun longMessagesStartCollapsedAndCanExpand() {
    compose.setContent {
      MaterialTheme {
        Box(Modifier.width(260.dp)) {
          MessageBubble(
            message = Message(id = "long-message", text = "A longer message with several lines. ".repeat(50)),
            isMe = false, onLongClick = {}, onReply = {}, onReplyPreviewClick = {},
            onMedia = {}, onReactionClick = {}, onRetry = {}
          )
        }
      }
    }
    compose.onNodeWithText("Read more").assertIsDisplayed().performClick()
    compose.onNodeWithText("Read less").assertExists()
  }
}
