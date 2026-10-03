package com.example

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.example.data.network.LiquidApi
import com.example.ui.screens.ChatsHomeScreen
import com.example.ui.screens.ConversationScreenV2
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.LiquidChatTheme
import com.example.ui.theme.LiquidGlassConfig
import com.example.ui.viewmodel.LiquidChatViewModel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainScreensSmokeTest {
  @get:Rule
  val compose = createComposeRule()

  private lateinit var viewModel: LiquidChatViewModel

  @Before
  fun setUp() {
    LiquidApi.context = ApplicationProvider.getApplicationContext<Context>()
    viewModel = LiquidChatViewModel()
  }

  @After
  fun tearDown() {
    viewModel.repository.close()
  }

  @Test
  fun `chats home composes without crashing`() {
    compose.setContent {
      Box(Modifier.testTag("chats_screen_host")) {
        LiquidChatTheme(
          glassConfig = LiquidGlassConfig(isGlassEnabled = false, isReducedMotion = true)
        ) {
          ChatsHomeScreen(
            viewModel = viewModel,
            onNavigateToConversation = {},
            onNavigateToSettings = {},
            onNavigateToContacts = {},
            onNavigateToSearch = {},
            onNavigateToAppearance = {},
            onNavigateToProfile = {}
          )
        }
      }
    }

    compose.waitForIdle()
    compose.onNodeWithTag("chats_screen_host", useUnmergedTree = true).fetchSemanticsNode()
  }

  @Test
  fun `conversation screen composes without crashing`() {
    compose.setContent {
      Box(Modifier.testTag("conversation_screen_host")) {
        LiquidChatTheme(
          glassConfig = LiquidGlassConfig(isGlassEnabled = false, isReducedMotion = true)
        ) {
          ConversationScreenV2(
            conversationId = "alice_bob",
            viewModel = viewModel,
            onBackClick = {},
            onNavigateToProfile = {}
          )
        }
      }
    }

    compose.waitForIdle()
    compose.onNodeWithTag("conversation_screen_host", useUnmergedTree = true).fetchSemanticsNode()
  }

  @Test
  fun `settings composes without crashing`() {
    compose.setContent {
      Box(Modifier.testTag("settings_screen_host")) {
        LiquidChatTheme(
          glassConfig = LiquidGlassConfig(isGlassEnabled = false, isReducedMotion = true)
        ) {
          SettingsScreen(
            viewModel = viewModel,
            onBackClick = {},
            onNavigateToAppearance = {},
            onNavigateToDiagnostics = {},
            onNavigateToHomeTab = {},
            onNavigateToProfile = {},
            onNavigateToContacts = {},
            onLogout = {}
          )
        }
      }
    }

    compose.waitForIdle()
    compose.onNodeWithTag("settings_screen_host", useUnmergedTree = true).fetchSemanticsNode()
  }
}
