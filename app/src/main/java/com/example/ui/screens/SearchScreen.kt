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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassAvatar
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassTextField
import com.example.ui.components.LiquidBackground
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.GlassBorderStroke
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.LiquidChatViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
  viewModel: LiquidChatViewModel,
  onNavigateToConversation: (String) -> Unit,
  onNavigateToGroupChat: (String) -> Unit,
  onNavigateToProfile: (String) -> Unit,
  onBackClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val searchQuery by viewModel.searchQuery.collectAsState()
  val searchResults by viewModel.searchResults.collectAsState()
  val searchHistory by viewModel.searchHistory.collectAsState()

  LiquidBackground(modifier = modifier) {
    Scaffold(
      containerColor = Color.Transparent,
      topBar = {
        GlassCard(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
          elevation = 8.dp
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
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }

            GlassTextField(
              value = searchQuery,
              onValueChange = { viewModel.onSearchQueryChanged(it) },
              placeholder = "Search messages, people, groups...",
              modifier = Modifier.weight(1f),
              leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
              },
              trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                  IconButton(
                    onClick = { viewModel.clearSearchQuery() },
                    modifier = Modifier.size(24.dp)
                  ) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        if (searchQuery.isBlank()) {
          // Recent Searches Chips
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
                    color = TextMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                  )
                )

                Text(
                  text = "Clear All",
                  style = MaterialTheme.typography.labelSmall.copy(color = CyanAccent),
                  modifier = Modifier.clickable { viewModel.clearSearchHistory() }
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
                      .background(Color(0xFF131D35))
                      .border(1.dp, GlassBorderStroke, RoundedCornerShape(16.dp))
                      .clickable { viewModel.onSearchQueryChanged(historyQuery) }
                      .padding(horizontal = 14.dp, vertical = 8.dp)
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(Icons.Default.History, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(historyQuery, color = TextPrimary, fontSize = 13.sp)
                    }
                  }
                }
              }
            }
          }
        } else {
          // Search Results Sections
          if (searchResults.users.isNotEmpty()) {
            item {
              Text(
                text = "PEOPLE & CONTACTS",
                style = MaterialTheme.typography.labelSmall.copy(color = CyanAccent, fontWeight = FontWeight.Bold)
              )
            }
            items(searchResults.users) { user ->
              GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
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
                    Text(user.displayName, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("@${user.username} • ${user.bio}", color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                  }
                  IconButton(
                    onClick = {
                      val convId = viewModel.getOrCreateConversationId(user.uid)
                      onNavigateToConversation(convId)
                    },
                    modifier = Modifier.size(36.dp)
                  ) {
                    Icon(Icons.Default.Chat, contentDescription = "Chat", tint = CyanAccent, modifier = Modifier.size(20.dp))
                  }
                }
              }
            }
          }

          if (searchResults.conversations.isNotEmpty()) {
            item {
              Text(
                text = "CHATS",
                style = MaterialTheme.typography.labelSmall.copy(color = CyanAccent, fontWeight = FontWeight.Bold)
              )
            }
            items(searchResults.conversations) { conv ->
              GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
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
                    Text(conv.otherUser.displayName, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(conv.lastMessageText, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                  }
                }
              }
            }
          }

          if (searchResults.groups.isNotEmpty()) {
            item {
              Text(
                text = "GROUPS",
                style = MaterialTheme.typography.labelSmall.copy(color = CyanAccent, fontWeight = FontWeight.Bold)
              )
            }
            items(searchResults.groups) { group ->
              GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                onClick = { onNavigateToGroupChat(group.id) }
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  GlassAvatar(photoUrl = group.photoUrl, name = group.name, size = 44.dp)
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(group.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("${group.members.size} members • ${group.description}", color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                  }
                }
              }
            }
          }

          if (searchResults.messages.isNotEmpty()) {
            item {
              Text(
                text = "MESSAGES",
                style = MaterialTheme.typography.labelSmall.copy(color = CyanAccent, fontWeight = FontWeight.Bold)
              )
            }
            items(searchResults.messages) { msg ->
              GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                onClick = { onNavigateToConversation(msg.conversationId) }
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(Icons.Default.Chat, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(24.dp))
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(msg.senderName, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(msg.text, color = TextSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                  color = TextMuted,
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
