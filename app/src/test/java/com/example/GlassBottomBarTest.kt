package com.example

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.ui.components.GlassBottomBar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GlassBottomBarTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `each tab invokes its own navigation and exposes selected state`() {
    val route = mutableStateOf("chats")
    val visits = mutableListOf<String>()
    fun visit(next: String) {
      visits += next
      route.value = next
    }
    compose.setContent {
      MaterialTheme {
        GlassBottomBar(
          selectedRoute = route.value,
          onNavigateToChats = { visit("chats") },
          onNavigateToContacts = { visit("contacts") },
          onNavigateToSettings = { visit("settings") }
        )
      }
    }
    compose.onNodeWithTag("bottom_bar_chats").assertIsSelected()
    compose.onNodeWithTag("bottom_bar_contacts").assertIsNotSelected().performClick().assertIsSelected()
    compose.onNodeWithTag("bottom_bar_settings").performClick().assertIsSelected()
    compose.onNodeWithTag("bottom_bar_contacts").assertIsNotSelected()
    compose.onNodeWithTag("bottom_bar_chats").performClick().assertIsSelected()
    compose.runOnIdle { assertEquals(listOf("contacts", "settings", "chats"), visits) }
  }

  @Test
  fun `unread count belongs to chats and clears when all messages are read`() {
    val unread = mutableStateOf(125)
    compose.setContent {
      MaterialTheme {
        GlassBottomBar(
          selectedRoute = "contacts",
          onNavigateToChats = {},
          onNavigateToContacts = {},
          onNavigateToSettings = {},
          unreadChatsCount = unread.value
        )
      }
    }
    compose.onNodeWithTag("bottom_bar_chats")
      .assertContentDescriptionEquals("Chats, 125 unread messages")
    compose.onNodeWithTag("bottom_bar_contacts").assertContentDescriptionEquals("Contacts")
    compose.onNodeWithTag("bottom_bar_settings").assertContentDescriptionEquals("Settings")
    compose.runOnIdle { unread.value = 0 }
    compose.onNodeWithTag("bottom_bar_chats").assertContentDescriptionEquals("Chats")
  }
}
