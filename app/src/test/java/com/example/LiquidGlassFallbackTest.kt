package com.example

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.ui.components.GlassCard
import com.example.ui.components.LiquidBackground
import com.example.ui.theme.LiquidChatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class LiquidGlassFallbackTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `pre Android 12 glass uses translucent fallback without crashing`() {
    compose.setContent {
      LiquidChatTheme {
        LiquidBackground {
          GlassCard {
            Text("fallback-glass-ok")
          }
        }
      }
    }

    compose.onNodeWithText("fallback-glass-ok").assertExists()
  }
}
