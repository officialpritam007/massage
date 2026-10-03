package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.data.network.LiquidApi
import com.example.ui.screens.ChatsHomeScreen
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
  fun `chats home renders its primary surface`() {
    compose.setContent {
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

    compose.onNodeWithText("Chats").fetchSemanticsNode()
  }

  @Test
  fun `settings renders its grouped settings surface`() {
    compose.setContent {
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

    compose.onNodeWithText("Settings").fetchSemanticsNode()
  }
}
