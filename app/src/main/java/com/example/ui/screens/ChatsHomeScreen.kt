package com.example.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Conversation
import com.example.data.repository.deleteChatForMeAwait
import com.example.ui.components.GlassAvatar
import com.example.ui.components.GlassBadge
import com.example.ui.components.GlassBottomBar
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassDialog
import com.example.ui.components.GlassIconButton
import com.example.ui.components.LiquidBackground
import com.example.ui.components.liquidRoundedShape
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.viewmodel.LiquidChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatsHomeScreen(
  viewModel: LiquidChatViewModel,
  onNavigateToConversation: (String) -> Unit,
  onNavigateToSettings: () -> Unit,
  onNavigateToContacts: () -> Unit,
  onNavigateToSearch: () -> Unit,
  onNavigateToAppearance: () -> Unit,
  onNavigateToProfile: (String) -> Unit,
  initialTab: String = "All",
  onHomeTabSelected: (String) -> Unit = {},
  modifier: Modifier = Modifier
) {
  val conversations by viewModel.conversations.collectAsStateWithLifecycle()
  val loading by viewModel.loading.collectAsStateWithLifecycle()
  val current by viewModel.currentUser.collectAsStateWithLifecycle()
  val glass = LocalLiquidGlass.current
  val scope = rememberCoroutineScope()
  var tab by rememberSaveable { mutableStateOf(initialTab) }
  var menu by remember { mutableStateOf<Conversation?>(null) }
  var homeMenu by remember { mutableStateOf(false) }
  var delete by remember { mutableStateOf<Conversation?>(null) }
  var deletingId by remember { mutableStateOf<String?>(null) }
  var deleteRetry by remember { mutableStateOf<Conversation?>(null) }
  LaunchedEffect(initialTab) { tab = initialTab }

  val visible = remember(conversations, tab) {
    conversations.filter {
      when (tab) {
        "Favorites" -> it.isPinned && !it.isArchived
        "Unread" -> it.unreadCount > 0 && !it.isArchived
        "Archived" -> it.isArchived
        else -> !it.isArchived
      }
    }
  }
  val totalUnread = conversations.sumOf { it.unreadCount }
  val unreadChats = conversations.count { it.unreadCount > 0 && !it.isArchived }
  val archivedCount = conversations.count { it.isArchived }

  fun selectTab(next: String) { tab = next; onHomeTabSelected(next) }
  fun deleteChatWithAnimation(conversation: Conversation) {
    if (deletingId != null) return
    deletingId = conversation.id
    deleteRetry = null
    scope.launch {
      delay(if (glass.isReducedMotion) 90 else 520)
      val result = viewModel.repository.deleteChatForMeAwait(conversation.id)
      deletingId = null
      deleteRetry = if (result.isFailure) conversation else null
    }
  }

  LiquidBackground(modifier, crystal = true) {
    Scaffold(
      containerColor = Color.Transparent,
      bottomBar = {
        GlassBottomBar(
          selectedRoute = "chats",
          onNavigateToChats = { selectTab("All") },
          onNavigateToContacts = onNavigateToContacts,
          onNavigateToSettings = onNavigateToSettings,
          unreadChatsCount = totalUnread
        )
      }
    ) { padding ->
      LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        item(key = "messages-header", contentType = "header") {
          Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
              Column(Modifier.weight(1f)) {
                Text("LIQUID CHAT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = glass.accentColor)
                Text("Chats", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Text(
                  if (unreadChats > 0) "$unreadChats ${if (unreadChats == 1) "conversation needs" else "conversations need"} your attention" else "Your people, a little closer.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              GlassIconButton(Icons.Default.MoreHoriz, "Chats menu", { homeMenu = true }, size = 48.dp)
              Spacer(Modifier.width(8.dp))
              GlassIconButton(
                Icons.Default.Add, "New chat", onNavigateToContacts,
                size = 48.dp, tint = MaterialTheme.colorScheme.onPrimary,
                backgroundColor = glass.accentColor
              )
            }
            GlassCard(
              Modifier.fillMaxWidth(), shape = liquidRoundedShape(28f), elevation = 2.dp,
              onClick = onNavigateToSearch
            ) {
              Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text("Search people or messages", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
            Row(
              Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              listOf("All", "Unread", "Favorites").forEach { filter ->
                HomeFilterPill(
                  label = if (filter == "Unread" && unreadChats > 0) "Unread · $unreadChats" else filter,
                  selected = tab == filter,
                  onClick = { selectTab(filter) }
                )
              }
            }
          }
        }

        if (archivedCount > 0 || tab == "Archived") {
          item(key = "archived-row", contentType = "archive") {
            ScreenContentSurface(Modifier.fillMaxWidth(), onClick = { selectTab(if (tab == "Archived") "All" else "Archived") }) {
              Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Inventory2, null, tint = glass.accentColor, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Text(if (tab == "Archived") "Back to all chats" else "Archived", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (tab != "Archived") Text(archivedCount.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
              }
            }
          }
        }
        item(key = "conversation-label", contentType = "section") {
          ScreenSectionLabel(if (tab == "All") "Conversations" else tab, visible.size.toString())
        }
        if (loading) {
          item(key = "loading", contentType = "loading") {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = glass.accentColor, trackColor = glass.accentColor.copy(alpha = .12f))
          }
        }

        items(visible, key = { it.id }, contentType = { "conversation" }) { conversation ->
          val localDraft = viewModel.repository.draft(conversation.id).trim()
          DustDeleteContainerV2(
            active = deletingId == conversation.id,
            reduced = glass.isReducedMotion,
            modifier = Modifier.animateItem().fillMaxWidth()
          ) {
            ScreenContentSurface(
              Modifier.fillMaxWidth(),
              onClick = { if (deletingId != conversation.id) onNavigateToConversation(conversation.id) }
            ) {
              Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassAvatar(
                  conversation.otherUser.photoUrl,
                  conversation.otherUser.displayName.ifBlank { "Contact" },
                  isOnline = conversation.isOnline && conversation.otherUser.onlineVisible,
                  size = 56.dp,
                  onClick = { onNavigateToProfile(conversation.otherUser.uid) }
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      conversation.otherUser.displayName.ifBlank { "Contact" },
                      style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                      maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                      conversationTimestamp(conversation.lastMessageTime),
                      style = MaterialTheme.typography.labelSmall,
                      color = if (conversation.unreadCount > 0) glass.accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      if (localDraft.isNotBlank()) "Draft: $localDraft" else conversation.lastMessageText.ifBlank { "Start a conversation" },
                      maxLines = 1, overflow = TextOverflow.Ellipsis,
                      style = MaterialTheme.typography.bodyMedium,
                      color = when {
                        localDraft.isNotBlank() -> glass.accentColor
                        conversation.unreadCount > 0 -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                      },
                      fontWeight = if (localDraft.isNotBlank() || conversation.unreadCount > 0) FontWeight.Medium else FontWeight.Normal,
                      modifier = Modifier.weight(1f)
                    )
                    if (conversation.isPinned) {
                      Spacer(Modifier.width(6.dp))
                      Icon(Icons.Default.PushPin, "Favorite", Modifier.size(14.dp), tint = glass.accentColor)
                    }
                    if (conversation.isMuted) {
                      Spacer(Modifier.width(6.dp))
                      Icon(Icons.Default.NotificationsOff, "Muted", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (conversation.unreadCount > 0) {
                      Spacer(Modifier.width(8.dp))
                      GlassBadge(conversation.unreadCount, color = glass.accentColor)
                    }
                  }
                }
                IconButton(
                  onClick = { if (deletingId != conversation.id) menu = conversation },
                  modifier = Modifier.size(48.dp)
                ) {
                  Icon(Icons.Default.MoreHoriz, "Actions for ${conversation.otherUser.displayName.ifBlank { "contact" }}", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }
          }
          if (deleteRetry?.id == conversation.id && deletingId != conversation.id) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
              TextButton(onClick = { deleteRetry?.let(::deleteChatWithAnimation) }) {
                Text("Delete failed · Retry", color = MaterialTheme.colorScheme.error)
              }
            }
          }
        }
        if (!loading && visible.isEmpty()) {
          item(key = "empty", contentType = "empty") {
            ScreenEmptyState(
              icon = Icons.Default.ChatBubbleOutline,
              title = when (tab) { "Unread" -> "You're all caught up"; "Favorites" -> "Keep your people close"; "Archived" -> "No archived chats"; else -> "Say your first hello" },
              description = when (tab) { "Unread" -> "New messages will appear here."; "Favorites" -> "Use a chat's menu to add it to Favorites."; "Archived" -> "Chats you archive will appear here."; else -> "Find someone you know and start a conversation." },
              modifier = Modifier.padding(top = 8.dp),
              actionLabel = if (tab == "All") "Start a conversation" else "View all chats",
              onAction = if (tab == "All") onNavigateToContacts else { { selectTab("All") } }
            )
          }
        }
      }
    }

    if (homeMenu) {
      GlassDialog("Your space", { homeMenu = false }) {
        GlassButton("New chat", { homeMenu = false; onNavigateToContacts() }, modifier = Modifier.fillMaxWidth(), icon = Icons.Default.Add)
        GlassButton("Your profile", { homeMenu = false; onNavigateToProfile(current.uid) }, modifier = Modifier.fillMaxWidth(), isPrimary = false)
        GlassButton("Appearance", { homeMenu = false; onNavigateToAppearance() }, modifier = Modifier.fillMaxWidth(), icon = Icons.Default.Palette, isPrimary = false)
      }
    }
    menu?.let { conversation ->
      GlassDialog(conversation.otherUser.displayName.ifBlank { "Chat actions" }, { menu = null }) {
        TextButton(onClick = { viewModel.repository.setFavorite(conversation.id, !conversation.isPinned); menu = null }, modifier = Modifier.fillMaxWidth()) {
          Text(if (conversation.isPinned) "Remove from Favorites" else "Add to Favorites")
        }
        TextButton(onClick = { viewModel.setConversationArchived(conversation.id, !conversation.isArchived); menu = null }, modifier = Modifier.fillMaxWidth()) {
          Text(if (conversation.isArchived) "Unarchive chat" else "Archive chat")
        }
        TextButton(onClick = { viewModel.setConversationMuted(conversation.id, !conversation.isMuted); menu = null }, modifier = Modifier.fillMaxWidth()) {
          Text(if (conversation.isMuted) "Unmute notifications" else "Mute notifications")
        }
        TextButton(onClick = { delete = conversation; menu = null }, modifier = Modifier.fillMaxWidth()) {
          Text("Delete chat for me", color = MaterialTheme.colorScheme.error)
        }
      }
    }
    delete?.let { conversation ->
      GlassDialog("Delete this chat?", { delete = null }) {
        Text("This permanently removes the current history from your account on all your devices. A later message can start a fresh chat without restoring this history.")
        TextButton(onClick = { delete = null; deleteChatWithAnimation(conversation) }, modifier = Modifier.fillMaxWidth()) {
          Text("Delete for me", color = MaterialTheme.colorScheme.error)
        }
        TextButton(onClick = { delete = null }, modifier = Modifier.fillMaxWidth()) { Text("Keep chat") }
      }
    }
  }
}

@Composable
private fun HomeFilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
  val config = LocalLiquidGlass.current
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium) },
    modifier = Modifier.height(48.dp),
    shape = liquidRoundedShape(24f),
    colors = FilterChipDefaults.filterChipColors(
      containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .72f),
      labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
      selectedContainerColor = config.accentColor,
      selectedLabelColor = MaterialTheme.colorScheme.onPrimary
    )
  )
}

private fun conversationTimestamp(timestamp: Long): String {
  if (timestamp <= 0) return ""
  val format = if (DateUtils.isToday(timestamp)) "h:mm a" else "MMM d"
  return SimpleDateFormat(format, Locale.getDefault()).format(Date(timestamp))
}
