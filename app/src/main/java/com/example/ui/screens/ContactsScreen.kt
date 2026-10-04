package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.User
import com.example.ui.components.GlassAvatar
import com.example.ui.components.GlassBottomBar
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassIconButton
import com.example.ui.components.GlassTextField
import com.example.ui.components.LiquidBackground
import com.example.ui.components.liquidRoundedShape
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.viewmodel.LiquidChatViewModel

@Composable
fun ContactsScreen(
  viewModel: LiquidChatViewModel,
  onNavigateToConversation: (String) -> Unit,
  onNavigateToChats: () -> Unit,
  onNavigateToSettings: () -> Unit,
  onNavigateToSearch: () -> Unit,
  onNavigateToProfile: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  LaunchedEffect(Unit) { viewModel.repository.refreshContacts() }
  val users by viewModel.users.collectAsStateWithLifecycle()
  val conversations by viewModel.conversations.collectAsStateWithLifecycle()
  val me by viewModel.currentUser.collectAsStateWithLifecycle()
  val glass = LocalLiquidGlass.current
  var query by rememberSaveable { mutableStateOf("") }

  val contacts = remember(users, conversations, me.uid, query) {
    val merged = (users + conversations.map { it.otherUser })
      .filter { it.uid.isNotBlank() && it.uid != me.uid }
      .distinctBy { it.uid }
      .filter { user ->
        query.isBlank() || user.displayName.contains(query, true) || user.username.contains(query, true)
      }
      .sortedBy { it.displayName.ifBlank { "Contact" }.lowercase() }
    merged
  }
  val grouped = contacts.groupBy {
    it.displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "#"
  }

  val totalUnread = conversations.sumOf { it.unreadCount }

  LiquidBackground(modifier = modifier, crystal = true) {
    Scaffold(
      containerColor = Color.Transparent,
      bottomBar = {
        GlassBottomBar(
          selectedRoute = "contacts",
          onNavigateToChats = onNavigateToChats,
          onNavigateToContacts = {},
          onNavigateToSettings = onNavigateToSettings,
          unreadChatsCount = totalUnread
        )
      }
    ) { padding ->
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
      ) {
        item(key = "contacts-header") {
          Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // iOS WhatsApp Header
            Row(
              Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                "Contacts",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
              )
              GlassIconButton(
                Icons.Default.Search,
                "Search directory",
                onNavigateToSearch,
                size = 44.dp
              )
              Spacer(Modifier.width(8.dp))
              GlassIconButton(
                Icons.Default.Add,
                "New contact",
                onNavigateToSearch,
                size = 46.dp,
                tint = Color.White,
                backgroundColor = glass.accentColor.copy(alpha = .86f)
              )
            }

            // Search input field
            GlassTextField(
              value = query,
              onValueChange = { query = it },
              placeholder = "Search contacts by name or @username",
              modifier = Modifier.fillMaxWidth(),
              leadingIcon = {
                Icon(
                  Icons.Default.Search,
                  contentDescription = null,
                  tint = glass.accentColor,
                  modifier = Modifier.size(22.dp)
                )
              },
              shape = liquidRoundedShape(28f),
              minHeight = 52.dp
            )
          }
        }

        // WhatsApp-style "New Contact" quick action item
        item(key = "new-contact-row") {
          GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = liquidRoundedShape(24f),
            backgroundColor = if (glass.isDark) Color(0xFF142A31).copy(alpha = .72f) else Color.White.copy(alpha = .58f),
            borderColor = Color.White.copy(alpha = if (glass.isDark) .16f else .58f),
            elevation = 2.dp,
            onClick = onNavigateToSearch
          ) {
            Row(
              Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              GlassIconButton(
                Icons.Default.PersonAdd,
                "New contact",
                onNavigateToSearch,
                size = 44.dp,
                tint = Color.White,
                backgroundColor = glass.accentColor.copy(alpha = .85f)
              )
              Spacer(Modifier.width(14.dp))
              Column(Modifier.weight(1f)) {
                Text(
                  "New Contact",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  "Discover and add new people to Liquid Chat",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
              )
            }
          }
        }

        if (contacts.isEmpty()) {
          item(key = "contacts-empty") {
            GlassCard(
              Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
              shape = liquidRoundedShape(28f),
              backgroundColor = if (glass.isDark) Color(0xFF142A31).copy(alpha = .72f) else Color.White.copy(alpha = .60f)
            ) {
              Column(
                Modifier
                  .fillMaxWidth()
                  .padding(vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Text(stringResource(R.string.no_contacts_found), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Search by name or username to start chatting", color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        } else {
          grouped.forEach { (letter, people) ->
            item(key = "letter-$letter") {
              Text(
                letter,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = glass.accentColor,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
              )
            }
            items(people, key = { it.uid }) { user ->
              GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = liquidRoundedShape(26f),
                backgroundColor = if (glass.isDark) Color(0xFF142A31).copy(alpha = .70f) else Color.White.copy(alpha = .56f),
                borderColor = Color.White.copy(alpha = if (glass.isDark) .16f else .58f),
                elevation = 3.dp,
                onClick = {
                  // Direct 1-to-1 conversation launch like iOS WhatsApp
                  val convId = viewModel.getOrCreateConversationId(user.uid)
                  onNavigateToConversation(convId)
                }
              ) {
                Row(
                  Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  GlassAvatar(
                    photoUrl = user.photoUrl,
                    name = user.displayName.ifBlank { "Contact" },
                    size = 52.dp,
                    isOnline = user.isOnline && user.onlineVisible,
                    onClick = { onNavigateToProfile(user.uid) }
                  )
                  Spacer(Modifier.width(14.dp))
                  Column(Modifier.weight(1f)) {
                    Text(
                      user.displayName.ifBlank { "Contact" },
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.onSurface,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = when {
                      user.isOnline && user.onlineVisible -> "Online"
                      user.bio.isNotBlank() -> user.bio
                      user.username.isNotBlank() -> "@${user.username}"
                      else -> "Hey there! I am using Liquid Chat."
                    }
                    Text(
                      subtitle,
                      style = MaterialTheme.typography.bodyMedium,
                      color = if (user.isOnline && user.onlineVisible) glass.accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                  IconButton(
                    onClick = { onNavigateToProfile(user.uid) },
                    modifier = Modifier.size(34.dp)
                  ) {
                    Icon(
                      Icons.Default.ChevronRight,
                      contentDescription = "View profile",
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(22.dp)
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
