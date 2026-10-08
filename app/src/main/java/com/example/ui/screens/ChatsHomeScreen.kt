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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Conversation
import com.example.data.repository.deleteChatForMeAwait
import com.example.ui.components.GlassAvatar
import com.example.ui.components.GlassBadge
import com.example.ui.components.GlassBottomBar
import com.example.ui.components.GlassDialog
import com.example.ui.components.GlassIconButton
import com.example.ui.components.LiquidBackground
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.viewmodel.LiquidChatViewModel
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
    onNavigateToProfile: (String) -> Unit,
    initialTab: String = "All",
    onHomeTabSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val glass = LocalLiquidGlass.current
    val scope = rememberCoroutineScope()

    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var menu by remember { mutableStateOf<Conversation?>(null) }
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

    val totalUnread = remember(conversations) { conversations.sumOf { it.unreadCount } }
    val archivedCount = remember(conversations) { conversations.count { it.isArchived } }
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val timeFormatter = remember(locale) { SimpleDateFormat("h:mm a", locale) }

    fun deleteChat(conversation: Conversation) {
        if (deletingId != null) return
        deletingId = conversation.id
        deleteRetry = null
        scope.launch {
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
                    onNavigateToChats = {
                        tab = "All"
                        onHomeTabSelected(tab)
                    },
                    onNavigateToContacts = onNavigateToContacts,
                    onNavigateToSettings = onNavigateToSettings,
                    unreadChatsCount = totalUnread
                )
            }
        ) { padding ->
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "messages-header", contentType = "header") {
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "YOUR SPACE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = glass.accentColor,
                                    letterSpacing = 1.4.sp
                                )
                                Text(
                                    "Chats",
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            GlassIconButton(
                                Icons.Default.Add,
                                "New chat",
                                onNavigateToContacts,
                                size = 48.dp,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                backgroundColor = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ChatFilter("All", tab == "All") {
                                tab = "All"
                                onHomeTabSelected(tab)
                            }
                            ChatFilter(
                                if (totalUnread > 0) "Unread ($totalUnread)" else "Unread",
                                tab == "Unread"
                            ) {
                                tab = "Unread"
                                onHomeTabSelected(tab)
                            }
                            ChatFilter("Favorites", tab == "Favorites") {
                                tab = "Favorites"
                                onHomeTabSelected(tab)
                            }
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth().testTag("chats_search"),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            onClick = onNavigateToSearch
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 15.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Search chats and messages",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                }

                if (archivedCount > 0 || tab == "Archived") {
                    item(key = "archived-row", contentType = "archive") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = if (glass.isDark) {
                                MaterialTheme.colorScheme.surface.copy(alpha = .72f)
                            } else {
                                MaterialTheme.colorScheme.surface.copy(alpha = .66f)
                            },
                            onClick = {
                                tab = if (tab == "Archived") "All" else "Archived"
                                onHomeTabSelected(tab)
                            }
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Inventory2,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    if (tab == "Archived") "Back to all chats" else "Archived ($archivedCount)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                if (loading) {
                    item(key = "loading", contentType = "loading") {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }

                items(visible, key = { it.id }, contentType = { "conversation" }) { conversation ->
                    val localDraft = viewModel.repository.draft(conversation.id).trim()
                    Surface(
                        modifier = Modifier
                            .then(if (glass.isReducedMotion) Modifier else Modifier.animateItem())
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = if (glass.isDark) {
                            MaterialTheme.colorScheme.surface.copy(alpha = .66f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = .72f)
                        },
                        enabled = deletingId != conversation.id,
                        onClick = { onNavigateToConversation(conversation.id) }
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlassAvatar(
                                conversation.otherUser.photoUrl,
                                conversation.otherUser.displayName.ifBlank { "Contact" },
                                isOnline = conversation.isOnline && conversation.otherUser.onlineVisible,
                                size = 48.dp,
                                onClick = { onNavigateToProfile(conversation.otherUser.uid) }
                            )
                            Spacer(Modifier.width(13.dp))

                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        conversation.otherUser.displayName.ifBlank { "Contact" },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        if (conversation.lastMessageTime > 0) {
                                            remember(conversation.lastMessageTime, timeFormatter) {
                                                timeFormatter.format(Date(conversation.lastMessageTime))
                                            }
                                        } else "",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (conversation.unreadCount > 0) {
                                            glass.accentColor
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }

                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (deletingId == conversation.id) {
                                            "Deleting chat…"
                                        } else if (localDraft.isNotBlank()) {
                                            "Draft: " + localDraft
                                        } else {
                                            conversation.lastMessageText.ifBlank { "Start a conversation" }
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = when {
                                            localDraft.isNotBlank() -> glass.accentColor
                                            conversation.unreadCount > 0 -> MaterialTheme.colorScheme.onSurface
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        fontWeight = if (localDraft.isNotBlank() || conversation.unreadCount > 0) {
                                            FontWeight.Medium
                                        } else {
                                            FontWeight.Normal
                                        },
                                        modifier = Modifier.weight(1f)
                                    )

                                    if (conversation.isPinned) {
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            Icons.Default.PushPin,
                                            contentDescription = "Pinned",
                                            tint = glass.accentColor,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }

                                    if (conversation.isMuted) {
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            Icons.Default.NotificationsOff,
                                            contentDescription = "Muted",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }

                                    if (conversation.unreadCount > 0) {
                                        Spacer(Modifier.width(8.dp))
                                        GlassBadge(
                                            conversation.unreadCount,
                                            color = glass.accentColor
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    if (deletingId != conversation.id) menu = conversation
                                },
                                enabled = deletingId != conversation.id,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.MoreHoriz,
                                    "Chat actions",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (deleteRetry?.id == conversation.id && deletingId != conversation.id) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { deleteRetry?.let(::deleteChat) }) {
                                Text(
                                    "Delete failed • Retry",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                if (!loading && visible.isEmpty()) {
                    item(key = "empty", contentType = "empty") {
                        Surface(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 28.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = .72f)
                        ) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 38.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.ChatBubbleOutline,
                                    null,
                                    Modifier.size(38.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    if (tab == "All") "No chats yet" else "No " + tab.lowercase() + " chats",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                TextButton(onClick = onNavigateToContacts) {
                                    Text("Start a new conversation")
                                }
                            }
                        }
                    }
                }
            }
        }

        menu?.let { conversation ->
            GlassDialog("Chat actions", { menu = null }) {
                Text(
                    conversation.otherUser.displayName.ifBlank { "Contact" },
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = {
                    viewModel.repository.setFavorite(conversation.id, !conversation.isPinned)
                    menu = null
                }) {
                    Text(if (conversation.isPinned) "Remove from Favorites" else "Add to Favorites")
                }
                TextButton(onClick = {
                    viewModel.setConversationArchived(conversation.id, !conversation.isArchived)
                    menu = null
                }) {
                    Text(if (conversation.isArchived) "Unarchive" else "Archive")
                }
                TextButton(onClick = {
                    viewModel.setConversationMuted(conversation.id, !conversation.isMuted)
                    menu = null
                }) {
                    Text(if (conversation.isMuted) "Unmute" else "Mute")
                }
                TextButton(onClick = {
                    delete = conversation
                    menu = null
                }) {
                    Text("Delete chat for me", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        delete?.let { conversation ->
            GlassDialog("Delete this chat?", { delete = null }) {
                Text(
                    "This permanently removes the current history from your account. " +
                        "It will not return after restart, sign-in, reinstall or sync. " +
                        "A later new message may start a fresh chat without restoring deleted history."
                )
                TextButton(onClick = {
                    delete = null
                    deleteChat(conversation)
                }) {
                    Text("Delete for me", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ChatFilter(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}
