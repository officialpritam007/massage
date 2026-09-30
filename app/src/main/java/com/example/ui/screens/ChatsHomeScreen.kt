package com.example.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import com.example.ui.components.GlassAvatar
import com.example.ui.components.GlassBadge
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassDialog
import com.example.ui.components.GlassIconButton
import com.example.ui.components.LiquidBackground
import com.example.ui.components.frostEdges
import com.example.ui.components.lensEdge
import com.example.ui.components.lensHighlight
import com.example.ui.components.rememberLiquidHaptics
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.viewmodel.LiquidChatViewModel
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

    LiquidBackground(modifier, crystal = true) {
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassAvatar(
                            current.photoUrl,
                            current.displayName.ifBlank { "You" },
                            size = 42.dp,
                            onClick = { onNavigateToProfile(current.uid) }
                        )
                        Spacer(Modifier.weight(1f))
                        GlassIconButton(
                            Icons.Default.Palette,
                            "Appearance",
                            onNavigateToAppearance,
                            size = 44.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        GlassIconButton(
                            Icons.Default.Add,
                            "New message",
                            onNavigateToSearch,
                            tint = Color.White,
                            backgroundColor = glass.accentColor.copy(alpha = .82f),
                            size = 44.dp
                        )
                    }

                    Text(
                        if (tab == "All") "Chats" else tab,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 18.dp, bottom = 12.dp)
                    )

                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        backgroundColor = if (glass.isDark) {
                            Color.White.copy(alpha = .055f)
                        } else {
                            Color.White.copy(alpha = .50f)
                        },
                        elevation = 2.dp,
                        onClick = onNavigateToSearch
                    ) {
                        Row(
                            Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(21.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Search people and messages",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                if (loading) {
                    item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                }

                items(visible, key = { it.id }) { conversation ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        backgroundColor = if (glass.isDark) {
                            Color.White.copy(alpha = .040f)
                        } else {
                            Color.White.copy(alpha = .30f)
                        },
                        elevation = 0.dp,
                        onClick = { onNavigateToConversation(conversation.id) }
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlassAvatar(
                                conversation.otherUser.photoUrl,
                                conversation.otherUser.displayName.ifBlank { "Contact" },
                                isOnline = conversation.isOnline && conversation.otherUser.onlineVisible,
                                size = 52.dp,
                                onClick = { onNavigateToProfile(conversation.otherUser.uid) }
                            )
                            Spacer(Modifier.width(12.dp))

                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        conversation.otherUser.displayName.ifBlank { "Contact" },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        if (conversation.lastMessageTime > 0) {
                                            SimpleDateFormat("h:mm a", Locale.getDefault())
                                                .format(Date(conversation.lastMessageTime))
                                        } else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (conversation.isTyping) {
                                            "Typing…"
                                        } else {
                                            conversation.lastMessageText.ifBlank { "Start a conversation" }
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (conversation.isTyping) {
                                            EmeraldOnline
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (conversation.unreadCount > 0) {
                                        Spacer(Modifier.width(8.dp))
                                        GlassBadge(conversation.unreadCount, color = glass.accentColor)
                                    }
                                }
                            }

                            IconButton(
                                onClick = { menu = conversation },
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

                if (!loading && visible.isEmpty()) {
                    item {
                        GlassCard(
                            Modifier.fillMaxWidth().padding(top = 34.dp),
                            shape = RoundedCornerShape(30.dp),
                            backgroundColor = if (glass.isDark) Color.White.copy(alpha = .035f) else Color.White.copy(alpha = .32f),
                            elevation = 0.dp
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 38.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.ChatBubbleOutline,
                                    contentDescription = null,
                                    Modifier.size(38.dp),
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
            GlassDialog("Chat actions", { menu = null }) {
                Text(conversation.otherUser.displayName.ifBlank { "Contact" }, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = {
                    viewModel.repository.setFavorite(conversation.id, !conversation.isPinned)
                    menu = null
                }) { Text(if (conversation.isPinned) "Remove from Favorites" else "Add to Favorites") }
                TextButton(onClick = {
                    viewModel.setConversationArchived(conversation.id, !conversation.isArchived)
                    menu = null
                }) { Text(if (conversation.isArchived) "Unarchive" else "Archive") }
                TextButton(onClick = {
                    viewModel.setConversationMuted(conversation.id, !conversation.isMuted)
                    menu = null
                }) { Text(if (conversation.isMuted) "Unmute" else "Mute") }
                TextButton(onClick = {
                    delete = conversation
                    menu = null
                }) { Text("Delete chat for me", color = MaterialTheme.colorScheme.error) }
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
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        GlassCard(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            backgroundColor = if (config.isDark) Color(0xFF0C1620).copy(alpha = .74f) else Color.White.copy(alpha = .62f),
            elevation = 12.dp
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(5.dp)) {
                val itemWidth = maxWidth / 4
                val x by animateDpAsState(
                    targetValue = itemWidth * index,
                    animationSpec = if (config.isReducedMotion) tween(0) else spring(dampingRatio = .72f, stiffness = 430f),
                    label = "tab_glass_pill"
                )

                // Lensing selection pill: glossy capsule with a specular edge that glides between tabs.
                Box(
                    Modifier
                        .offset(x = x)
                        .width(itemWidth)
                        .height(52.dp)
                        .shadow(4.dp, RoundedCornerShape(26.dp), ambientColor = Color.Black, spotColor = Color.Black.copy(alpha = .35f))
                        .clip(RoundedCornerShape(26.dp))
                        .background(config.accentColor.copy(alpha = if (config.isDark) .20f else .16f))
                        .lensHighlight(dark = config.isDark, strength = 1.2f)
                        .lensEdge(RoundedCornerShape(26.dp), dark = config.isDark, strength = .9f)
                )

                Row(Modifier.fillMaxWidth()) {
                    items.forEachIndexed { itemIndex, item ->
                        val selected = index == itemIndex
                        val iconScale by animateFloatAsState(
                            targetValue = if (selected && !config.isReducedMotion) 1.12f else 1f,
                            animationSpec = spring(dampingRatio = .48f, stiffness = 620f),
                            label = "tab_icon_bounce"
                        )
                        Column(
                            Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(26.dp))
                                .clickable {
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
                                    .size(22.dp)
                                    .graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(item.second, fontSize = 10.sp, color = color, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
