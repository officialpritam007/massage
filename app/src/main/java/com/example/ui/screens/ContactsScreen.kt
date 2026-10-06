package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
  val allContacts = remember(users, conversations, me.uid) {
    (users + conversations.map { it.otherUser })
      .filter { it.uid.isNotBlank() && it.uid != me.uid }
      .distinctBy { it.uid }
      .sortedBy { it.displayName.ifBlank { "Contact" }.lowercase() }
  }
  val contacts = remember(allContacts, query) {
    val term = query.trim().removePrefix("@")
    allContacts.filter { term.isBlank() || it.displayName.contains(term, true) || it.username.contains(term, true) }
  }
  val grouped = remember(contacts) {
    contacts.groupBy { it.displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "#" }
  }
  val onlineCount = allContacts.count { it.isOnline && it.onlineVisible }
  val totalUnread = conversations.sumOf { it.unreadCount }

  LiquidBackground(modifier = modifier, crystal = true) {
    Scaffold(
      containerColor = Color.Transparent,
      bottomBar = {
        GlassBottomBar("contacts", onNavigateToChats, {}, onNavigateToSettings, unreadChatsCount = totalUnread)
      }
    ) { padding ->
      LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        item(key = "contacts-header", contentType = "header") {
          Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
              Column(Modifier.weight(1f)) {
                Text("YOUR CIRCLE", style = MaterialTheme.typography.labelSmall, color = glass.accentColor, fontWeight = FontWeight.SemiBold)
                Text("Contacts", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Text(
                  if (onlineCount > 0) "$onlineCount online now" else "Make room for a good conversation.",
                  style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              GlassIconButton(Icons.Default.PersonAdd, "Find people", onNavigateToSearch, size = 48.dp, tint = MaterialTheme.colorScheme.onPrimary, backgroundColor = glass.accentColor)
            }
            GlassTextField(
              value = query, onValueChange = { query = it },
              placeholder = "Name or @username", modifier = Modifier.fillMaxWidth(),
              leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp)) },
              trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }, modifier = Modifier.size(48.dp)) {
                  Icon(Icons.Default.Close, "Clear contact search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              },
              shape = liquidRoundedShape(28f), minHeight = 56.dp,
              testTag = "contacts_search"
            )
          }
        }
        if (query.isBlank()) {
          item(key = "find-people", contentType = "discovery") {
            GlassCard(Modifier.fillMaxWidth().padding(top = 8.dp), shape = liquidRoundedShape(28f), elevation = 2.dp, onClick = onNavigateToSearch) {
              Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PersonAdd, null, tint = glass.accentColor, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text("Find your people", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                  Text("Search by name or username", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
              }
            }
          }
        }
        item(key = "contacts-label", contentType = "section") {
          ScreenSectionLabel(if (query.isBlank()) "People you know" else "Matching contacts", contacts.size.toString())
        }
        if (contacts.isEmpty()) {
          item(key = "contacts-empty", contentType = "empty") {
            ScreenEmptyState(
              Icons.Default.People,
              if (query.isBlank()) "Every conversation starts with hello" else "No matching contacts",
              if (query.isBlank()) "Find a person by name or username to start chatting." else "Try another name or username.",
              actionLabel = if (query.isBlank()) "Find people" else "Clear search",
              onAction = if (query.isBlank()) onNavigateToSearch else { { query = "" } }
            )
          }
        } else {
          grouped.forEach { (letter, people) ->
            item(key = "letter-$letter", contentType = "letter") {
              Text(letter, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = glass.accentColor, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp))
            }
            items(people, key = { it.uid }, contentType = { "contact" }) { user ->
              ScreenContentSurface(
                Modifier.fillMaxWidth(),
                onClick = { onNavigateToConversation(viewModel.getOrCreateConversationId(user.uid)) }
              ) {
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                  GlassAvatar(
                    user.photoUrl, user.displayName.ifBlank { "Contact" }, size = 52.dp,
                    isOnline = user.isOnline && user.onlineVisible,
                    onClick = { onNavigateToProfile(user.uid) }
                  )
                  Spacer(Modifier.width(14.dp))
                  Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(user.displayName.ifBlank { "Contact" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val subtitle = when {
                      user.isOnline && user.onlineVisible -> "Online now"
                      user.username.isNotBlank() -> "@${user.username}"
                      user.bio.isNotBlank() -> user.bio
                      else -> "Tap to start chatting"
                    }
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = if (user.isOnline && user.onlineVisible) glass.accentColor else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  }
                  IconButton(onClick = { onNavigateToProfile(user.uid) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.ChevronRight, "View ${user.displayName.ifBlank { "contact" }}'s profile", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
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
