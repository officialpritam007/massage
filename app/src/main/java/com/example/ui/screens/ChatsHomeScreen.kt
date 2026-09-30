package com.example.ui.screens

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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.HorizontalDivider
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

    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var menu by remember { mutableStateOf<Conversation?>(null) }
    var delete by remember { mutableStateOf<Conversation?>(null) }

    LaunchedEffect(initialTab) {
        tab = initialTab
    }

    val visible = conversations.filter {
        when (tab) {
            "Favorites" -> it.isPinned && !it.isArchived
            "Archived" -> it.isArchived
            else -> !it.isArchived
        }
    }

    LiquidBackground(modifier) {
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
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
            ) {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassAvatar(
                            current.photoUrl,
                            current.displayName,
                            size = 38.dp,
                            onClick = { onNavigateToProfile(current.uid) }
                        )
                        Spacer(Modifier.weight(1f))
                        GlassIconButton(
                            Icons.Default.Palette,
                            "Appearance",
                            onNavigateToAppearance
                        )
                        Spacer(Modifier.width(8.dp))
                        GlassIconButton(
                            Icons.Default.Add,
                            "New message",
                            onNavigateToSearch,
                            tint = Color.White,
                            backgroundColor = Color(0xFF18B96A)
                        )
                    }

                    Text(
                        if (tab == "All") "Chats" else tab,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 20.dp, bottom = 14.dp)
                    )

                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(30.dp),
                        onClick = onNavigateToSearch
                    ) {
                        Row(
                            Modifier.padding(15.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Search people and messages",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }

                if (loading) {
                    item {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }

                items(visible, key = { it.id }) { conversation ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToConversation(conversation.id) }
                            .padding(vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassAvatar(
                            conversation.otherUser.photoUrl,
                            conversation.otherUser.displayName,
                            isOnline = conversation.isOnline && conversation.otherUser.onlineVisible,
                            size = 54.dp,
                            onClick = { onNavigateToProfile(conversation.otherUser.uid) }
                        )
                        Spacer(Modifier.width(13.dp))

                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    conversation.otherUser.displayName,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    if (conversation.lastMessageTime > 0) {
                                        SimpleDateFormat("h:mm a", Locale.getDefault())
                                            .format(Date(conversation.lastMessageTime))
                                    } else {
                                        ""
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(5.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (conversation.isTyping) {
                                        "Typing…"
                                    } else {
                                        conversation.lastMessageText.ifBlank { "Start a conversation" }
                                    },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (conversation.isTyping) {
                                        EmeraldOnline
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                if (conversation.unreadCount > 0) {
                                    GlassBadge(conversation.unreadCount, color = EmeraldOnline)
                                }
                            }
                        }

                        IconButton(
                            onClick = { menu = conversation },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                "Chat actions",
                                modifier = Modifier.size(19.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(
                        Modifier.padding(start = 67.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = .12f)
                    )
                }

                if (!loading && visible.isEmpty()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 64.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No ${if (tab == "All") "chats yet" else tab.lowercase() + " chats"}"
                            )
                            TextButton(onClick = onNavigateToSearch) {
                                Text("Start a new conversation")
                            }
                        }
                    }
                }
            }
        }

        menu?.let { conversation ->
            GlassDialog("Chat actions", { menu = null }) {
                Text(conversation.otherUser.displayName, fontWeight = FontWeight.SemiBold)
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
                Text("This permanently removes the current history from your account. It will not return after restart, sign-in, reinstall or sync. A later new message may start a fresh chat without restoring deleted history.")
                TextButton(onClick = {
                    viewModel.deleteChatForMe(conversation.id)
                    delete = null
                }) {
                    Text("Delete for me")
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
    val reduced = LocalLiquidGlass.current.isReducedMotion

    Box(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        GlassCard(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(36.dp),
            elevation = 8.dp
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(6.dp)) {
                val itemWidth = maxWidth / 4
                val x by animateDpAsState(
                    targetValue = itemWidth * index,
                    animationSpec = if (reduced) {
                        tween(0)
                    } else {
                        spring(dampingRatio = .72f, stiffness = 430f)
                    },
                    label = "tab_glass_pill"
                )

                Box(
                    Modifier
                        .offset(x = x)
                        .width(itemWidth)
                        .height(58.dp)
                        .clip(RoundedCornerShape(29.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = .13f))
                )

                Row(Modifier.fillMaxWidth()) {
                    items.forEachIndexed { itemIndex, item ->
                        Column(
                            Modifier
                                .weight(1f)
                                .height(58.dp)
                                .clip(RoundedCornerShape(29.dp))
                                .clickable(onClick = clicks[itemIndex]),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val color = if (index == itemIndex) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Icon(
                                item.third,
                                item.second,
                                tint = color,
                                modifier = Modifier.size(23.dp)
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(item.second, fontSize = 10.sp, color = color, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
