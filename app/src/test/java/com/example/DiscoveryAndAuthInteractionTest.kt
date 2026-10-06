package com.example

import android.content.Context
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.network.LiquidApi
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ContactsScreen
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
class DiscoveryAndAuthInteractionTest {
  @get:Rule val compose = createComposeRule()
  private lateinit var viewModel: LiquidChatViewModel
  private val appearance = LiquidGlassConfig(isGlassEnabled = false, isReducedMotion = true)

  @Before fun setUp() {
    LiquidApi.context = ApplicationProvider.getApplicationContext<Context>()
    viewModel = LiquidChatViewModel()
  }

  @After fun tearDown() { viewModel.repository.close() }

  private fun input(tag: String): SemanticsNodeInteraction =
    compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))

  @Test fun `registration requires every field and password visibility remains usable`() {
    compose.setContent {
      LiquidChatTheme(glassConfig = appearance) {
        AuthScreen(onAuthenticated = {}, viewModel = viewModel)
      }
    }
    compose.onNodeWithText("New to Liquid Chat? Create account").performScrollTo().performClick()
    val submit = compose.onNode(hasText("Create account") and hasClickAction())
    submit.assertIsNotEnabled()
    input("auth_name").performScrollTo().performTextInput("Nadia")
    input("auth_username").performScrollTo().performTextInput("nadia_7")
    input("auth_email").performScrollTo().performTextInput("nadia@example.com")
    submit.assertIsNotEnabled()
    input("auth_password").performScrollTo().performTextInput("password-123")
    submit.assertIsEnabled()
    compose.onNodeWithContentDescription("Show password").performScrollTo().performClick()
    compose.onNodeWithContentDescription("Hide password").assertIsEnabled()
  }

  @Test fun `contact search clears locally and navigation callbacks stay independent`() {
    val visits = mutableListOf<String>()
    compose.setContent {
      LiquidChatTheme(glassConfig = appearance) {
        ContactsScreen(
          viewModel = viewModel,
          onNavigateToConversation = { visits += "conversation" },
          onNavigateToChats = { visits += "chats" },
          onNavigateToSettings = { visits += "settings" },
          onNavigateToSearch = { visits += "search" },
          onNavigateToProfile = { visits += "profile" }
        )
      }
    }
    input("contacts_search").performTextInput("does-not-match")
    compose.onNodeWithText("No matching contacts").assertExists()
    compose.onNodeWithContentDescription("Clear contact search").performClick()
    input("contacts_search").assertTextEquals("")
    compose.onNodeWithContentDescription("Find people").performClick()
    compose.onNodeWithTag("bottom_bar_chats").performClick()
    compose.runOnIdle { assertEquals(listOf("search", "chats"), visits) }
  }

  @Test fun `search categories preserve query while clearing and back remain functional`() {
    viewModel.onSearchQueryChanged("does-not-match")
    var backCount = 0
    compose.setContent {
      LiquidChatTheme(glassConfig = appearance) {
        SearchScreen(viewModel, {}, {}, { backCount++ })
      }
    }
    compose.onNodeWithText("People").performClick().assertIsSelected()
    compose.runOnIdle { assertEquals("does-not-match", viewModel.searchQuery.value) }
    compose.onNodeWithText("Search everything").performClick()
    compose.onNodeWithText("All").assertIsSelected()
    compose.onNodeWithText("Clear search").performClick()
    compose.runOnIdle { assertEquals("", viewModel.searchQuery.value) }
    compose.onNodeWithTag("search_back_button").performClick()
    compose.runOnIdle { assertEquals(1, backCount) }
  }
}
