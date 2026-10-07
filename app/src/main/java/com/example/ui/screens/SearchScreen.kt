package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassAvatar
import com.example.ui.components.GlassTextField
import com.example.ui.components.LiquidBackground
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.viewmodel.LiquidChatViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
  viewModel: LiquidChatViewModel,
  onNavigateToConversation: (String) -> Unit,
  onNavigateToProfile: (String) -> Unit,
  onBackClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
  val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
  val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
  val focus = remember { FocusRequester() }
  val keyboard = LocalSoftwareKeyboardController.current
  LaunchedEffect(Unit) { withFrameNanos { }; focus.requestFocus(); keyboard?.show() }
  val glassConfig = LocalLiquidGlass.current

  LiquidBackground(modifier = modifier) {
    Scaffold(
      containerColor = Color.Transparent,
      topBar = {
        Surface(
          modifier = Modifier.fillMaxWidth().statusBarsPadding(),
          color = MaterialTheme.colorScheme.surface
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(
              onClick = onBackClick,
              modifier = Modifier.testTag("search_back_button")
            ) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }

            GlassTextField(
              value = searchQuery,
              onValueChange = { viewModel.onSearchQueryChanged(it) },
              placeholder = "Search chats and people",
              modifier = Modifier.weight(1f).focusRequester(focus),
              leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = glassConfig.accentColor, modifier = Modifier.size(20.dp))
              },
              trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                  IconButton(
                    onClick = { viewModel.clearSearchQuery() },
                    modifier = Modifier.size(48.dp)
                  ) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
              },
              testTag = "search_screen_input"
            )
          }
        }
      }
    ) { innerPadding ->
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        if (searchQuery.isBlank()) {
          item(key = "search_hint", contentType = "hint") {
            Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("Search your chats", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
              Text("Search names, usernames, and messages loaded on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
          if (searchHistory.isNotEmpty()) {
            item(key = "search_history_heading", contentType = "heading") {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = "Recent searches",
                  style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                  )
                )

                TextButton(onClick = { viewModel.clearSearchHistory() }) {
                  Text("Clear", color = glassConfig.accentColor)
                }
              }
            }

            item(key = "search_history", contentType = "history") {
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                searchHistory.forEach { historyQuery ->
                  AssistChip(
                    onClick = { viewModel.onSearchQueryChanged(historyQuery) },
                    label = {
                      Text(historyQuery, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                  )
                }
              }
            }
          }
        } else {
          if (searchResults.users.isNotEmpty()) {
            item(key = "people_heading", contentType = "heading") {
              Text(
                text = "People",
                style = MaterialTheme.typography.labelSmall.copy(color = glassConfig.accentColor, fontWeight = FontWeight.Bold)
              )
            }
            items(searchResults.users, key = { "person:${it.uid}" }, contentType = { "person" }) { user ->
              Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                onClick = { onNavigateToProfile(user.uid) }
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  GlassAvatar(photoUrl = user.photoUrl, name = user.displayName, size = 44.dp)
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(user.displayName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(user.username.takeIf { it.isNotBlank() }?.let { "@$it" }.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  }
                  IconButton(
                    onClick = {
                      val convId = viewModel.getOrCreateConversationId(user.uid)
                      onNavigateToConversation(convId)
                    },
                    modifier = Modifier.size(48.dp)
                  ) {
                    Icon(Icons.Default.Chat, contentDescription = "Chat", tint = glassConfig.accentColor, modifier = Modifier.size(20.dp))
                  }
                }
              }
            }
          }

          if (searchResults.conversations.isNotEmpty()) {
            item(key = "chats_heading", contentType = "heading") {
              Text(
                text = "Chats",
                style = MaterialTheme.typography.labelSmall.copy(color = glassConfig.accentColor, fontWeight = FontWeight.Bold)
              )
            }
            items(searchResults.conversations, key = { "chat:${it.id}" }, contentType = { "conversation" }) { conv ->
              Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                onClick = { onNavigateToConversation(conv.id) }
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  GlassAvatar(photoUrl = conv.otherUser.photoUrl, name = conv.otherUser.displayName, size = 44.dp)
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(conv.otherUser.displayName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(conv.lastMessageText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  }
                }
              }
            }
          }

          if (searchResults.messages.isNotEmpty()) {
            item(key = "messages_heading", contentType = "heading") {
              Text(
                text = "Messages",
                style = MaterialTheme.typography.labelSmall.copy(color = glassConfig.accentColor, fontWeight = FontWeight.Bold)
              )
            }
            items(searchResults.messages, key = { "message:${it.conversationId}:${it.id}" }, contentType = { "message" }) { msg ->
              Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                onClick = {
                  viewModel.requestMessageJump(msg.conversationId, msg.id)
                  onNavigateToConversation(msg.conversationId)
                }
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(Icons.Default.Chat, contentDescription = null, tint = glassConfig.accentColor, modifier = Modifier.size(24.dp))
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(msg.senderName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(msg.text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                  }
                }
              }
            }
          }

          if (searchResults.isEmpty) {
            item(key = "search_empty", contentType = "empty") {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(top = 40.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = "No results found for \"$searchQuery\"",
                  color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                  fontSize = 14.sp
                )
              }
            }
          }
        }
      }
    }
  }
}
