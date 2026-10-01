package com.example.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
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
import com.example.data.model.MessageType
import com.example.data.model.User
import com.example.ui.components.GlassActionRow
import com.example.ui.components.GlassAvatar
import com.example.ui.components.GlassBadge
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassDialog
import com.example.ui.components.GlassIconButton
import com.example.ui.components.GlassSheet
import com.example.ui.components.LiquidBackground
import com.example.ui.components.frostEdges
import com.example.ui.components.lensEdge
import com.example.ui.components.lensHighlight
import com.example.ui.components.rememberLiquidHaptics
import com.example.ui.theme.BubbleMetaTextStyle
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.theme.MetaOnBubbleLight
import com.example.ui.viewmodel.LiquidChatViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
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
    val directory by viewModel.users.collectAsState()
    val glass = LocalLiquidGlass.current
    val haptics = rememberLiquidHaptics()

    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var menu by remember { mutableStateOf<Conversation?>(null) }
    var delete by remember { mutableStateOf<Conversation?>(null) }

    LaunchedEffect(initialTab) { tab = initialTab }

    val visible = conversations.filter {
        when (tab) {
            "Favorites" -> it.isPinned && !it.isArchived
            "Archived" -> it.isArchived
            else -> !it.isArchived
        }
    }

    // Directory fallback: only fall back to "Contact" when no profile data exists at all.
    fun identityOf(conversation: Conversation): User {
        val base = conversation.otherUser
        val directoryMatch = directory.firstOrNull { it.uid == base.uid }
        if (base.displayName.isNotBlank() && base.photoUrl.isNotBlank()) return base
        if (directoryMatch == null) return base
        return base.copy(
            displayName = base.displayName.ifBlank { directoryMatch.displayName },
            photoUrl = base.photoUrl.ifBlank { directoryMatch.photoUrl },
            username = base.username.ifBlank { directoryMatch.username }
        )
    }

    LiquidBackground(modifier, crystal = true, scrim = true) {
        Box(
            Modifier
                .fillMaxSize()
                .frostEdges(dark = glass.isDark)
        ) {
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
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    // Avatar, title and actions share one floating glass header.
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(26.dp),
                        backgroundColor = if (glass.isDark) Color(0xFF0C1620).copy(alpha = .70f) else Color.White.copy(alpha = .58f),
                        elevation = 8.dp
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlassAvatar(
                                current.photoUrl,
                                current.displayName.ifBlank { "You" },
                                size = 40.dp,
                                onClick = { onNavigateToProfile(current.uid) }
                            )
                            Spacer(Modifier.width(11.dp))
                            Text(
                                if (tab == "All") "Chats" else tab,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            GlassIconButton(
                                Icons.Default.Palette,
                                "Appearance",
                                onNavigateToAppearance,
                                size = 38.dp
                            )
                            Spacer(Modifier.width(6.dp))
                            GlassIconButton(
                                Icons.Default.Add,
                                "New message",
                                onNavigateToSearch,
                                tint = Color.White,
                                backgroundColor = glass.accentColor.copy(alpha = .86f),
                                size = 38.dp
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))

                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(999.dp),
                        backgroundColor = if (glass.isDark) {
                            Color.White.copy(alpha = .055f)
                        } else {
                            Color.White.copy(alpha = .48f)
                        },
                        elevation = 1.dp,
                        onClick = onNavigateToSearch
                    ) {
                        Row(
                            Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Search people and messages",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }

                if (loading) {
                    item { LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp)) }
                }

                items(visible, key = { it.id }) { conversation ->
                    val peer = identityOf(conversation)
                    val draft = remember(conversation.id, conversation.lastMessageTime) {
                        viewModel.repository.draft(conversation.id)
                    }
                    val subtitle = when {
                        conversation.isTyping -> "Typing…"
                        draft.isNotBlank() -> "Draft: $draft"
                        conversation.lastMessageText.isNotBlank() -> conversation.lastMessageText
                        else -> "Start a conversation"
                    }
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        backgroundColor = if (glass.isDark) {
                            Color.White.copy(alpha = .045f)
                        } else {
                            Color.White.copy(alpha = .34f)
                        },
                        elevation = 0.dp,
                        lensing = false,
                        onClick = { onNavigateToConversation(conversation.id) }
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlassAvatar(
                                peer.photoUrl,
                                peer.displayName.ifBlank { "Contact" },
                                isOnline = conversation.isOnline && peer.onlineVisible,
                                size = 46.dp,
                                onClick = { onNavigateToProfile(peer.uid) }
                            )
                            Spacer(Modifier.width(11.dp))

                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (conversation.isPinned) {
                                        Icon(
                                            Icons.Default.PushPin,
                                            "Pinned",
                                            Modifier.size(11.dp).padding(end = 0.dp),
                                            tint = glass.accentColor
                                        )
                                        Spacer(Modifier.width(5.dp))
                                    }
                                    Text(
                                        peer.displayName.ifBlank { "Contact" },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        if (conversation.lastMessageTime > 0) {
                                            listTime(conversation.lastMessageTime)
                                        } else "",
                                        style = BubbleMetaTextStyle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        subtitle,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = when {
                                            conversation.isTyping -> EmeraldOnline
                                            draft.isNotBlank() -> glass.accentColor
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (conversation.lastMessageSenderId == current.uid && conversation.unreadCount == 0 && !conversation.isTyping) {
                                        Icon(
                                            Icons.Default.Done,
                                            "Sent",
                                            Modifier.size(13.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    if (conversation.unreadCount > 0) {
                                        GlassBadge(conversation.unreadCount, color = glass.accentColor)
                                    }
                                }
                            }

                            IconButton(
                                onClick = { menu = conversation },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.MoreHoriz,
                                    "Chat actions",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (!loading && visible.isEmpty()) {
                    item {
                        GlassCard(
                            Modifier.fillMaxWidth().padding(top = 20.dp),
                            shape = RoundedCornerShape(28.dp),
                            backgroundColor = if (glass.isDark) Color.White.copy(alpha = .035f) else Color.White.copy(alpha = .32f),
                            elevation = 0.dp,
                            lensing = false
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 34.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.ChatBubbleOutline,
                                    contentDescription = null,
                                    Modifier.size(34.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "No ${if (tab == "All") "chats yet" else tab.lowercase() + " chats"}",
                                    style = MaterialTheme.typography.titleMedium
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

        menu?.let { conversation ->
            val peer = identityOf(conversation)
            GlassSheet(peer.displayName.ifBlank { "Contact" }, { menu = null }) {
                GlassActionRow(
                    Icons.Default.Star,
                    if (conversation.isPinned) "Remove from Favorites" else "Add to Favorites"
                ) {
                    haptics.toggle()
                    viewModel.repository.setFavorite(conversation.id, !conversation.isPinned)
                    menu = null
                }
                GlassActionRow(
                    Icons.Default.Inventory2,
                    if (conversation.isArchived) "Unarchive" else "Archive"
                ) {
                    viewModel.setConversationArchived(conversation.id, !conversation.isArchived)
                    menu = null
                }
                GlassActionRow(
                    Icons.Default.Settings,
                    if (conversation.isMuted) "Unmute" else "Mute"
                ) {
                    viewModel.setConversationMuted(conversation.id, !conversation.isMuted)
                    menu = null
                }
                GlassActionRow(Icons.Default.Delete, "Delete chat for me", destructive = true) {
                    delete = conversation
                    menu = null
                }
            }
        }

        delete?.let { conversation ->
            GlassDialog("Delete this chat?", { delete = null }) {
                Text("This permanently removes the current history from your account. It will not return after restart, sign-in, reinstall or sync. A later new message may start a fresh chat without restoring deleted history.")
                TextButton(onClick = {
                    viewModel.deleteChatForMe(conversation.id)
                    delete = null
                }) { Text("Delete for me", color = MaterialTheme.colorScheme.error) }
            }
        }
        }
    }
}

/** Short, calm list timestamps (time today, weekday this week, date beyond that). */
private fun listTime(time: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = time }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(time))
    val daysApart = (now.timeInMillis - time) / 86_400_000L
    return if (daysApart < 7) SimpleDateFormat("EEE", Locale.getDefault()).format(Date(time))
    else SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(time))
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
    val haptics = rememberLiquidHaptics()

    Box(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        GlassCard(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            backgroundColor = if (config.isDark) Color(0xFF0C1620).copy(alpha = .70f) else Color.White.copy(alpha = .58f),
            elevation = 10.dp
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(4.dp)) {
                val itemWidth = maxWidth / 4
                val barHeight = 44.dp
                val x by animateDpAsState(
                    targetValue = itemWidth * index,
                    animationSpec = if (config.isReducedMotion) tween(0) else spring(dampingRatio = .72f, stiffness = 430f),
                    label = "tab_glass_pill"
                )

                // Lensing selection capsule that glides between tabs.
                Box(
                    Modifier
                        .offset(x = x)
                        .width(itemWidth)
                        .height(barHeight)
                        .shadow(3.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black, spotColor = Color.Black.copy(alpha = .30f))
                        .clip(RoundedCornerShape(22.dp))
                        .background(config.accentColor.copy(alpha = if (config.isDark) .20f else .15f))
                        .lensHighlight(dark = config.isDark, strength = 1.2f)
                        .lensEdge(RoundedCornerShape(22.dp), dark = config.isDark, strength = .9f)
                )

                Row(Modifier.fillMaxWidth()) {
                    items.forEachIndexed { itemIndex, item ->
                        val selected = index == itemIndex
                        val iconScale by animateFloatAsState(
                            targetValue = if (selected && !config.isReducedMotion) 1.1f else 1f,
                            animationSpec = spring(dampingRatio = .48f, stiffness = 620f),
                            label = "tab_icon_bounce"
                        )
                        Column(
                            Modifier
                                .weight(1f)
                                .height(barHeight)
                                .clip(RoundedCornerShape(22.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (!selected) haptics.toggle()
                                    clicks[itemIndex]()
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val color = if (selected) config.accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                            Icon(
                                item.third, item.second,
                                tint = color,
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                            )
                            Spacer(Modifier.height(1.dp))
                            Text(item.second, fontSize = 9.5.sp, color = color, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
