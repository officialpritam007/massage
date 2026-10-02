package com.example.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Conversation
import com.example.data.repository.deleteChatForMeAwait
import com.example.ui.components.GlassAvatar
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassBadge
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassDialog
import com.example.ui.components.GlassIconButton
import com.example.ui.components.LiquidBackground
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
    onNavigateToSearch: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToProfile: (String) -> Unit,
    initialTab: String = "All",
    onHomeTabSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val conversations by viewModel.conversations.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val current by viewModel.currentUser.collectAsState()
    val glass = LocalLiquidGlass.current
    val scope = rememberCoroutineScope()

    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var menu by remember { mutableStateOf<Conversation?>(null) }
    var homeMenu by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf<Conversation?>(null) }
    var deletingId by remember { mutableStateOf<String?>(null) }
    var deleteRetry by remember { mutableStateOf<Conversation?>(null) }

    LaunchedEffect(initialTab) { tab = initialTab }

    val visible = conversations.filter {
        when (tab) {
            "Favorites" -> it.isPinned && !it.isArchived
            "Archived" -> it.isArchived
            else -> !it.isArchived
        }
    }

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

    val darkSearch = Color(0xFF0B3A68).copy(alpha = .52f)
    val lightSearch = Color.White.copy(alpha = .52f)
    val darkRow = Color(0xFF101A29).copy(alpha = .72f)
    val lightRow = Color.White.copy(alpha = .48f)

    LiquidBackground(modifier, crystal = true) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                GlassBottomBar(
                    selectedRoute = when (tab) {
                        "Favorites" -> "favorites"
                        "Archived" -> "archived"
                        else -> "chats"
                    },
                    onNavigateToChats = {
                        tab = "All"
                        onHomeTabSelected(tab)
                    },
                    onNavigateToFavorites = {
                        tab = "Favorites"
                        onHomeTabSelected(tab)
                    },
                    onNavigateToArchived = {
                        tab = "Archived"
                        onHomeTabSelected(tab)
                    },
                    onNavigateToSettings = onNavigateToSettings
                )
            }
        ) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                item(key = "messages-header") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .animateContentSize(
                                if (glass.isReducedMotion) tween(0)
                                else spring(dampingRatio = .76f, stiffness = 390f)
                            ),
                        verticalArrangement = Arrangement.spacedBy(13.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlassIconButton(
                                Icons.Default.Settings,
                                "Settings",
                                onNavigateToSettings,
                                size = 48.dp
                            )
                            Box(
                                Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (tab == "All") "Messages" else tab,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            GlassIconButton(
                                Icons.Default.MoreHoriz,
                                "Messages menu",
                                { homeMenu = true },
                                size = 48.dp
                            )
                        }

                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(32.dp),
                            backgroundColor = if (glass.isDark) darkSearch else lightSearch,
                            borderColor = Color.White.copy(alpha = if (glass.isDark) .19f else .62f),
                            elevation = 5.dp,
                            onClick = onNavigateToSearch
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(23.dp)
                                )
                                Spacer(Modifier.width(11.dp))
                                Text(
                                    "Search",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = "Search filters",
                                    tint = glass.accentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                }

                if (loading) {
                    item(key = "loading") {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }

                items(visible, key = { it.id }) { conversation ->
                    val localDraft = viewModel.repository.draft(conversation.id).trim()
                    DustDeleteContainerV2(
                        active = deletingId == conversation.id,
                        reduced = glass.isReducedMotion,
                        modifier = Modifier.animateItem().fillMaxWidth()
                    ) {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(31.dp),
                            backgroundColor = if (glass.isDark) darkRow else lightRow,
                            borderColor = Color.White.copy(alpha = if (glass.isDark) .13f else .55f),
                            elevation = if (glass.isDark) 2.dp else 1.dp,
                            enableBlur = false,
                            onClick = {
                                if (deletingId != conversation.id) {
                                    onNavigateToConversation(conversation.id)
                                }
                            }
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GlassAvatar(
                                    conversation.otherUser.photoUrl,
                                    conversation.otherUser.displayName.ifBlank { "Contact" },
                                    isOnline = conversation.isOnline && conversation.otherUser.onlineVisible,
                                    size = 55.dp,
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
                                                SimpleDateFormat("h:mm a", Locale.getDefault())
                                                    .format(Date(conversation.lastMessageTime))
                                            } else "",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (conversation.unreadCount > 0) {
                                                glass.accentColor
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }

                                    Spacer(Modifier.height(5.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            if (localDraft.isNotBlank()) {
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

                                        if (conversation.isMuted) {
                                            Spacer(Modifier.width(7.dp))
                                            Icon(
                                                Icons.Default.NotificationsOff,
                                                contentDescription = "Muted",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
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

                                Spacer(Modifier.width(3.dp))
                                IconButton(
                                    onClick = {
                                        if (deletingId != conversation.id) menu = conversation
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Default.MoreHoriz,
                                        "Chat actions",
                                        modifier = Modifier.size(19.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (deleteRetry?.id == conversation.id && deletingId != conversation.id) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { deleteRetry?.let(::deleteChatWithAnimation) }) {
                                Text(
                                    "Delete failed • Retry",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                if (!loading && visible.isEmpty()) {
                    item(key = "empty") {
                        GlassCard(
                            Modifier.fillMaxWidth().padding(top = 28.dp),
                            shape = RoundedCornerShape(32.dp),
                            backgroundColor = if (glass.isDark) darkRow else lightRow,
                            elevation = 2.dp,
                            enableBlur = false
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 38.dp),
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
                                TextButton(onClick = onNavigateToSearch) {
                                    Text("Start a new conversation")
                                }
                            }
                        }
                    }
                }
            }
        }

        if (homeMenu) {
            GlassDialog("Messages", { homeMenu = false }) {
                GlassButton(
                    "New message",
                    {
                        homeMenu = false
                        onNavigateToSearch()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Add
                )
                GlassButton(
                    "Your profile",
                    {
                        homeMenu = false
                        onNavigateToProfile(current.uid)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isPrimary = false
                )
                GlassButton(
                    "Appearance",
                    {
                        homeMenu = false
                        onNavigateToAppearance()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Palette,
                    isPrimary = false
                )
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
                    deleteChatWithAnimation(conversation)
                }) {
                    Text("Delete for me", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun GlassBottomBar(
    selectedRoute: String,
    onNavigateToChats: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToArchived: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        Triple("chats", "All", Icons.Default.ChatBubbleOutline),
        Triple("favorites", "Favorites", Icons.Default.StarOutline),
        Triple("archived", "Archived", Icons.Default.Inventory2),
        Triple("settings", "Settings", Icons.Default.Settings)
    )
    val clicks = listOf(
        onNavigateToChats,
        onNavigateToFavorites,
        onNavigateToArchived,
        onNavigateToSettings
    )
    val index = items.indexOfFirst { it.first == selectedRoute }.coerceAtLeast(0)
    val config = LocalLiquidGlass.current

    Box(
        modifier.navigationBarsPadding().padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        GlassCard(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            backgroundColor = if (config.isDark) {
                Color(0xFF091522).copy(alpha = .78f)
            } else {
                Color.White.copy(alpha = .66f)
            },
            borderColor = Color.White.copy(alpha = if (config.isDark) .14f else .56f),
            elevation = 11.dp
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(5.dp)) {
                val itemWidth = maxWidth / 4
                val x by animateDpAsState(
                    targetValue = itemWidth * index,
                    animationSpec = if (config.isReducedMotion) {
                        tween(0)
                    } else {
                        spring(dampingRatio = .68f, stiffness = 390f)
                    },
                    label = "tab_glass_pill"
                )

                Box(
                    Modifier
                        .offset(x = x)
                        .width(itemWidth)
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(
                            config.accentColor.copy(
                                alpha = if (config.isDark) .25f else .17f
                            )
                        )
                )

                Row(Modifier.fillMaxWidth()) {
                    items.forEachIndexed { itemIndex, item ->
                        Column(
                            Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(26.dp))
                                .clickable(onClick = clicks[itemIndex]),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val color = if (index == itemIndex) {
                                config.accentColor
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Icon(
                                item.third,
                                item.second,
                                tint = color,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                item.second,
                                fontSize = 10.sp,
                                color = color,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
