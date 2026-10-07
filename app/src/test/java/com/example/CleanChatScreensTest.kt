package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.network.LiquidApi
import com.example.ui.screens.ChatsHomeScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.theme.LiquidChatTheme
import com.example.ui.theme.LiquidGlassConfig
import com.example.ui.viewmodel.LiquidChatViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CleanChatScreensTest {
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
  fun `home keeps search and new chat reachable without duplicate menu`() {
    var searches = 0
    var newChats = 0
    compose.setContent {
      LiquidChatTheme(glassConfig = LiquidGlassConfig(isGlassEnabled = false, isReducedMotion = true)) {
        ChatsHomeScreen(
          viewModel = viewModel,
          onNavigateToConversation = {},
          onNavigateToSettings = {},
          onNavigateToContacts = { newChats++ },
          onNavigateToSearch = { searches++ },
          onNavigateToProfile = {}
        )
      }
    }

    compose.onNodeWithContentDescription("Chats menu").assertDoesNotExist()
    compose.onNodeWithContentDescription("Search filters").assertDoesNotExist()
    compose.onNodeWithTag("chats_search").performClick()
    compose.onNodeWithContentDescription("New chat").performClick()
    compose.runOnIdle {
      assertEquals(1, searches)
      assertEquals(1, newChats)
    }
  }

  @Test
  fun `home filters expose selection and chats tab restores all conversations`() {
    val selections = mutableListOf<String>()
    compose.setContent {
      LiquidChatTheme(glassConfig = LiquidGlassConfig(isGlassEnabled = false, isReducedMotion = true)) {
        ChatsHomeScreen(
          viewModel = viewModel,
          onNavigateToConversation = {},
          onNavigateToSettings = {},
          onNavigateToContacts = {},
          onNavigateToSearch = {},
          onNavigateToProfile = {},
          onHomeTabSelected = { selections += it }
        )
      }
    }

    compose.onNodeWithText("All").assertIsSelected()
    compose.onNodeWithText("Unread").performClick().assertIsSelected()
    compose.onNodeWithText("All").assertIsNotSelected()
    compose.onNodeWithText("Favorites").performClick().assertIsSelected()
    compose.onNodeWithTag("bottom_bar_chats").performClick()
    compose.onNodeWithText("All").assertIsSelected()
    compose.runOnIdle { assertEquals(listOf("Unread", "Favorites", "All"), selections) }
  }

  @Test
  fun `search input can be cleared and back remains reachable`() {
    var backs = 0
    compose.setContent {
      LiquidChatTheme(glassConfig = LiquidGlassConfig(isGlassEnabled = false, isReducedMotion = true)) {
        SearchScreen(
          viewModel = viewModel,
          onNavigateToConversation = {},
          onNavigateToProfile = {},
          onBackClick = { backs++ }
        )
      }
    }

    compose.onNode(hasSetTextAction()).performTextInput("Alice")
    compose.runOnIdle { assertEquals("Alice", viewModel.searchQuery.value) }
    compose.onNodeWithContentDescription("Clear").performClick()
    compose.runOnIdle { assertEquals("", viewModel.searchQuery.value) }
    compose.onNodeWithTag("search_back_button").performClick()
    compose.runOnIdle { assertEquals(1, backs) }
  }
}
