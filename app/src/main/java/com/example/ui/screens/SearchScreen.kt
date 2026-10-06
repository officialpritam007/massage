package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.GlassAvatar
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassIconButton
import com.example.ui.components.GlassTextField
import com.example.ui.components.LiquidBackground
import com.example.ui.components.liquidRoundedShape
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
  var category by rememberSaveable { mutableStateOf("All") }
  val focus = remember { FocusRequester() }
  val keyboard = LocalSoftwareKeyboardController.current
  val glass = LocalLiquidGlass.current
  LaunchedEffect(Unit) { withFrameNanos { }; focus.requestFocus(); keyboard?.show() }
  val showPeople = category == "All" || category == "People"
  val showChats = category == "All" || category == "Chats"
  val showMessages = category == "All" || category == "Messages"
  val visibleCount = (if (showPeople) searchResults.users.size else 0) +
    (if (showChats) searchResults.conversations.size else 0) +
    (if (showMessages) searchResults.messages.size else 0)

  LiquidBackground(modifier = modifier) {
    Scaffold(
      containerColor = Color.Transparent,
      topBar = {
        Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
          Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBackClick, size = 48.dp, testTag = "search_back_button")
            Spacer(Modifier.width(14.dp))
            Column {
              Text("Search", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
              Text("Find the conversation that matters", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
          GlassCard(Modifier.fillMaxWidth(), shape = liquidRoundedShape(28f), elevation = 2.dp) {
            GlassTextField(
              value = searchQuery, onValueChange = { viewModel.onSearchQueryChanged(it) },
              placeholder = "Name, username or message",
              modifier = Modifier.fillMaxWidth().focusRequester(focus),
              leadingIcon = { Icon(Icons.Default.Search, null, tint = glass.accentColor, modifier = Modifier.size(22.dp)) },
              trailingIcon = {
                if (searchQuery.isNotEmpty()) IconButton(onClick = { viewModel.clearSearchQuery() }, modifier = Modifier.size(48.dp)) {
                  Icon(Icons.Default.Close, "Clear search", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
              },
              shape = liquidRoundedShape(28f), minHeight = 56.dp, verticalPadding = 4.dp,
              testTag = "search_screen_input"
            )
          }
          Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            listOf("All", "People", "Chats", "Messages").forEach { label ->
              FilterChip(
                selected = category == label,
                onClick = { category = label },
                label = { Text(label, fontWeight = if (category == label) FontWeight.SemiBold else FontWeight.Medium) },
                shape = liquidRoundedShape(24f), modifier = Modifier.height(48.dp),
                colors = FilterChipDefaults.filterChipColors(
                  containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .72f),
                  labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                  selectedContainerColor = glass.accentColor,
                  selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
              )
            }
          }
        }
      }
    ) { padding ->
      LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        if (searchQuery.isBlank()) {
          item(key = "search-intro", contentType = "intro") {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("A name. A word. A memory.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
              Text("Search people you know and your chats. Message search includes the history loaded on this device.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
          if (searchHistory.isNotEmpty()) {
            item(key = "history-header", contentType = "section") {
              Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent searches", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { viewModel.clearSearchHistory() }) { Text("Clear all") }
              }
            }
            item(key = "history", contentType = "history") {
              FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                searchHistory.forEach { historyQuery ->
                  AssistChip(
                    onClick = { viewModel.onSearchQueryChanged(historyQuery) },
                    label = { Text(historyQuery, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Default.History, null, modifier = Modifier.size(18.dp)) },
                    shape = liquidRoundedShape(24f), modifier = Modifier.height(48.dp).widthIn(max = 280.dp)
                  )
                }
              }
            }
          }
        } else {
          item(key = "result-count", contentType = "section") {
            ScreenSectionLabel("${if (category == "All") "Search" else category} results", "$visibleCount ${if (visibleCount == 1) "match" else "matches"}")
          }
          if (showPeople && searchResults.users.isNotEmpty()) {
            item(key = "people-label", contentType = "section") { ScreenSectionLabel("People", searchResults.users.size.toString()) }
            items(searchResults.users, key = { "person:${it.uid}" }, contentType = { "person" }) { user ->
              ScreenContentSurface(Modifier.fillMaxWidth(), onClick = { onNavigateToProfile(user.uid) }) {
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                  GlassAvatar(user.photoUrl, user.displayName.ifBlank { "Contact" }, size = 52.dp)
                  Spacer(Modifier.width(14.dp))
                  Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(user.displayName.ifBlank { "Contact" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                      if (user.username.isNotBlank()) "@${user.username}" else user.bio.ifBlank { "View profile" },
                      style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                  }
                  IconButton(onClick = { onNavigateToConversation(viewModel.getOrCreateConversationId(user.uid)) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Chat, "Message ${user.displayName.ifBlank { "contact" }}", tint = glass.accentColor, modifier = Modifier.size(22.dp))
                  }
                }
              }
            }
          }
          if (showChats && searchResults.conversations.isNotEmpty()) {
            item(key = "chats-label", contentType = "section") { ScreenSectionLabel("Chats", searchResults.conversations.size.toString()) }
            items(searchResults.conversations, key = { "chat:${it.id}" }, contentType = { "chat" }) { conversation ->
              ScreenContentSurface(Modifier.fillMaxWidth(), onClick = { onNavigateToConversation(conversation.id) }) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                  GlassAvatar(conversation.otherUser.photoUrl, conversation.otherUser.displayName.ifBlank { "Contact" }, size = 52.dp)
                  Spacer(Modifier.width(14.dp))
                  Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(conversation.otherUser.displayName.ifBlank { "Contact" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(conversation.lastMessageText.ifBlank { "Open conversation" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  }
                }
              }
            }
          }
          if (showMessages && searchResults.messages.isNotEmpty()) {
            item(key = "messages-label", contentType = "section") { ScreenSectionLabel("Messages", searchResults.messages.size.toString()) }
            items(searchResults.messages, key = { "message:${it.conversationId}:${it.id}" }, contentType = { "message" }) { message ->
              ScreenContentSurface(Modifier.fillMaxWidth(), onClick = {
                viewModel.requestMessageJump(message.conversationId, message.id)
                onNavigateToConversation(message.conversationId)
              }) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
                  Box(Modifier.size(44.dp).background(glass.accentColor.copy(alpha = .12f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Chat, null, tint = glass.accentColor, modifier = Modifier.size(22.dp))
                  }
                  Spacer(Modifier.width(14.dp))
                  Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(message.senderName.ifBlank { "Message" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(message.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                  }
                }
              }
            }
          }
          if (visibleCount == 0) {
            item(key = "search-empty", contentType = "empty") {
              ScreenEmptyState(
                Icons.Default.Search, "No matches yet", "Try another name, username or word from a message.",
                actionLabel = if (category == "All") "Clear search" else "Search everything",
                onAction = { if (category == "All") viewModel.clearSearchQuery() else category = "All" }
              )
            }
          }
        }
      }
    }
  }
}
