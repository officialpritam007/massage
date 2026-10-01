package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassAvatar
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassIconButton
import com.example.ui.components.GlassTextField
import com.example.ui.components.LiquidBackground
import com.example.ui.components.rememberLiquidHaptics
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.GlassBorderStroke
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.viewmodel.LiquidChatViewModel

/** Highlights every case-insensitive match of [query] inside [text]. */
private fun highlight(text: String, query: String, highlightColor: Color): AnnotatedString {
  if (query.isBlank()) return AnnotatedString(text)
  val builder = AnnotatedString.Builder(text)
  var index = text.indexOf(query, 0, ignoreCase = true)
  while (index >= 0) {
    builder.addStyle(
      SpanStyle(background = highlightColor, fontWeight = FontWeight.SemiBold),
      index,
      index + query.length
    )
    index = text.indexOf(query, index + query.length, ignoreCase = true)
  }
  return builder.toAnnotatedString()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
  viewModel: LiquidChatViewModel,
  onNavigateToConversation: (String) -> Unit,
  onNavigateToProfile: (String) -> Unit,
  onBackClick: () -> Unit,
  /** Opens the conversation and scrolls to the exact hit. */
  onOpenMessage: (String, String) -> Unit = { _, _ -> },
  modifier: Modifier = Modifier
) {
  val searchQuery by viewModel.searchQuery.collectAsState()
  val searchResults by viewModel.searchResults.collectAsState()
  val searchHistory by viewModel.searchHistory.collectAsState()
  val glassConfig = LocalLiquidGlass.current
  val haptics = rememberLiquidHaptics()
  val matchTint = glassConfig.accentColor.copy(alpha = if (glassConfig.isDark) .30f else .20f)

  LiquidBackground(modifier = modifier, scrim = true) {
    Scaffold(
      containerColor = Color.Transparent,
      topBar = {
        // Floating search bar, matching the conversation header language.
        GlassCard(
          modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 6.dp),
          shape = RoundedCornerShape(26.dp),
          elevation = 8.dp
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            GlassIconButton(
              Icons.AutoMirrored.Filled.ArrowBack,
              "Back",
              onBackClick,
              size = 38.dp,
              testTag = "search_back_button"
            )
            Spacer(Modifier.width(6.dp))

            GlassTextField(
              value = searchQuery,
              onValueChange = { viewModel.onSearchQueryChanged(it) },
              placeholder = "Search messages and people…",
              modifier = Modifier.weight(1f),
              leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(19.dp))
              },
              trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                  IconButton(
                    onClick = { viewModel.clearSearchQuery() },
                    modifier = Modifier.size(24.dp)
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
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        if (searchQuery.isBlank()) {
          if (searchHistory.isNotEmpty()) {
            item {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = "RECENT SEARCHES",
                  style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.5.sp,
                    letterSpacing = 0.8.sp
                  )
                )

                Text(
                  text = "Clear all",
                  style = MaterialTheme.typography.labelSmall.copy(color = CyanAccent),
                  modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable {
                      haptics.tap()
                      viewModel.clearSearchHistory()
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
            }

            item {
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                searchHistory.forEach { historyQuery ->
                  Box(
                    modifier = Modifier
                      .clip(RoundedCornerShape(16.dp))
                      .background(if (glassConfig.isDark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.62f))
                      .border(1.dp, GlassBorderStroke, RoundedCornerShape(16.dp))
                      .clickable {
                        haptics.tap()
                        viewModel.onSearchQueryChanged(historyQuery)
                      }
                      .padding(horizontal = 13.dp, vertical = 7.dp)
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(historyQuery, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                    }
                  }
                }
              }
            }
          }
        } else {
          if (searchResults.users.isNotEmpty()) {
            item {
              SectionLabel("PEOPLE & CONTACTS")
            }
            items(searchResults.users) { user ->
              GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = 0.dp,
                lensing = false,
                onClick = { onNavigateToProfile(user.uid) }
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  GlassAvatar(photoUrl = user.photoUrl, name = user.displayName, size = 42.dp)
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      remember(user.displayName, searchQuery) { highlight(user.displayName, searchQuery, matchTint) },
                      color = MaterialTheme.colorScheme.onSurface,
                      fontWeight = FontWeight.SemiBold,
                      fontSize = 15.sp,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    Text(
                      "@${user.username} • ${user.bio}",
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      fontSize = 12.sp,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                  IconButton(
                    onClick = {
                      haptics.tap()
                      val convId = viewModel.getOrCreateConversationId(user.uid)
                      onNavigateToConversation(convId)
                    },
                    modifier = Modifier.size(34.dp)
                  ) {
                    Icon(Icons.Default.Chat, contentDescription = "Chat", tint = CyanAccent, modifier = Modifier.size(19.dp))
                  }
                }
              }
            }
          }

          if (searchResults.conversations.isNotEmpty()) {
            item {
              SectionLabel("CHATS")
            }
            items(searchResults.conversations) { conv ->
              GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = 0.dp,
                lensing = false,
                onClick = { onNavigateToConversation(conv.id) }
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  GlassAvatar(photoUrl = conv.otherUser.photoUrl, name = conv.otherUser.displayName, size = 42.dp)
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      conv.otherUser.displayName.ifBlank { "Contact" },
                      color = MaterialTheme.colorScheme.onSurface,
                      fontWeight = FontWeight.SemiBold,
                      fontSize = 15.sp,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    Text(
                      remember(conv.lastMessageText, searchQuery) { highlight(conv.lastMessageText, searchQuery, matchTint) },
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      fontSize = 12.sp,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                }
              }
            }
          }

          if (searchResults.messages.isNotEmpty()) {
            item {
              SectionLabel("MESSAGES")
            }
            items(searchResults.messages) { msg ->
              GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = 0.dp,
                lensing = false,
                onClick = {
                  haptics.tap()
                  onOpenMessage(msg.conversationId, msg.id)
                }
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(Icons.Default.Chat, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(21.dp))
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      msg.senderName.ifBlank { "Message" },
                      color = MaterialTheme.colorScheme.onSurface,
                      fontWeight = FontWeight.SemiBold,
                      fontSize = 14.sp,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    Text(
                      remember(msg.text, searchQuery) { highlight(msg.text, searchQuery, matchTint) },
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      fontSize = 13.sp,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis
                    )
                    Text(
                      "Tap to jump to this message",
                      style = MaterialTheme.typography.labelSmall,
                      color = CyanAccent
                    )
                  }
                }
              }
            }
          }

          if (searchResults.isEmpty) {
            item {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(top = 40.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = "No results found for \"$searchQuery\"",
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun SectionLabel(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelSmall,
    color = CyanAccent,
    fontWeight = FontWeight.SemiBold,
    modifier = Modifier.padding(start = 6.dp, top = 4.dp)
  )
}
