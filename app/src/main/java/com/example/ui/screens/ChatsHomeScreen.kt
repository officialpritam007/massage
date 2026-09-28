package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Conversation
import com.example.ui.components.GlassAvatar
import com.example.ui.components.GlassBadge
import com.example.ui.components.GlassCard
import com.example.ui.components.LiquidBackground
import com.example.ui.theme.AmberPinned
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.GlassBorderStroke
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.viewmodel.LiquidChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatsHomeScreen(
  viewModel: LiquidChatViewModel,
  onNavigateToConversation: (String) -> Unit,
  onNavigateToGroups: () -> Unit,
  onNavigateToUpdates: () -> Unit,
  onNavigateToCalls: () -> Unit,
  onNavigateToSettings: () -> Unit,
  onNavigateToSearch: () -> Unit,
  onNavigateToAppearance: () -> Unit,
  onNavigateToProfile: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val conversations by viewModel.conversations.collectAsState()
  val glassConfig = LocalLiquidGlass.current
  val statuses by viewModel.statuses.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()

  var selectedFilter by remember { mutableStateOf("All") }
  val filters = listOf("All", "Unread", "Favorites", "Archived", "Groups")

  val filteredConversations = remember(conversations, selectedFilter) {
    when (selectedFilter) {
      "Unread" -> conversations.filter { it.unreadCount > 0 }
      "Favorites" -> conversations.filter { it.isPinned && !it.isArchived }
      "Archived" -> conversations.filter { it.isArchived }
      else -> conversations.filter { !it.isArchived }
    }
  }

  LiquidBackground(modifier = modifier) {
    Scaffold(
      containerColor = Color.Transparent,
      topBar = {
        // Liquid Header
        GlassCard(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
          elevation = 8.dp
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Liquid Logo
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                  Brush.linearGradient(listOf(CyanNeon, ElectricBlue))
                )
                .clickable { onNavigateToProfile(currentUser.uid) },
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.WaterDrop,
                contentDescription = "Liquid Chat",
                tint = Color.Black,
                modifier = Modifier.size(24.dp)
              )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Liquid Chat",
                style = MaterialTheme.typography.titleLarge.copy(
                  fontWeight = FontWeight.ExtraBold,
                  color = TextPrimary,
                  fontSize = 20.sp
                )
              )
              Text(
                text = "Real-time • Liquid Glass UI",
                style = MaterialTheme.typography.bodySmall.copy(
                  color = CyanAccent,
                  fontSize = 11.sp
                )
              )
            }

            // Quick Actions: Search, Appearance, Settings
            IconButton(
              onClick = onNavigateToSearch,
              modifier = Modifier.testTag("home_search_button")
            ) {
              Icon(Icons.Default.Search, contentDescription = "Search", tint = TextPrimary)
            }

            IconButton(
              onClick = onNavigateToAppearance,
              modifier = Modifier.testTag("home_appearance_button")
            ) {
              Icon(Icons.Default.Palette, contentDescription = "Appearance", tint = CyanAccent)
            }

            IconButton(
              onClick = onNavigateToSettings,
              modifier = Modifier.testTag("home_settings_button")
            ) {
              Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextPrimary)
            }
          }
        }
      },
      bottomBar = {
        // Floating Liquid Glass Navigation Bar
        GlassBottomBar(
          selectedRoute = "chats",
          onNavigateToChats = {},
          onNavigateToGroups = onNavigateToGroups,
          onNavigateToUpdates = onNavigateToUpdates,
          onNavigateToCalls = onNavigateToCalls,
          onNavigateToSettings = onNavigateToSettings
        )
      },
      floatingActionButton = {
        FloatingActionButton(
          onClick = {
            // Open conversation with first contact or search
            if (conversations.isNotEmpty()) {
              onNavigateToConversation(conversations.first().id)
            } else {
              onNavigateToSearch()
            }
          },
          containerColor = if (glassConfig.isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.82f),
          contentColor = if (glassConfig.isDark) Color.White else ElectricBlue,
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier
            .padding(bottom = 72.dp)
            .testTag("new_chat_fab")
        ) {
          Icon(Icons.Default.Chat, contentDescription = "New Chat")
        }
      }
    ) { innerPadding ->
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
        contentPadding = PaddingValues(bottom = 90.dp)
      ) {
        // Status Updates / Stories Row
        item {
          Column(modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) {
            Text(
              text = "UPDATES & STORIES",
              style = MaterialTheme.typography.labelSmall.copy(
                color = TextMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp
              ),
              modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

            LazyRow(
              contentPadding = PaddingValues(horizontal = 16.dp),
              horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
              // Add own status
              item {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  modifier = Modifier.clickable { onNavigateToUpdates() }
                ) {
                  Box(
                    modifier = Modifier
                      .size(62.dp)
                      .clip(CircleShape)
                      .border(1.5.dp, GlassBorderStroke, CircleShape)
                      .background(if (glassConfig.isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.70f)),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = Icons.Default.Add,
                      contentDescription = "Add Status",
                      tint = CyanAccent,
                      modifier = Modifier.size(28.dp)
                    )
                  }
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = "My Status",
                    style = MaterialTheme.typography.labelSmall.copy(
                      color = TextSecondary,
                      fontSize = 11.sp
                    )
                  )
                }
              }

              // Statuses from friends
              items(statuses) { status ->
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  modifier = Modifier.clickable { onNavigateToUpdates() }
                ) {
                  val ringBrush = if (!status.isViewedByMe) {
                    Brush.sweepGradient(listOf(CyanNeon, ElectricBlue, CyanAccent, CyanNeon))
                  } else {
                    Brush.linearGradient(listOf(Color.Gray.copy(alpha = 0.5f), Color.DarkGray))
                  }

                  Box(
                    modifier = Modifier
                      .size(62.dp)
                      .clip(CircleShape)
                      .border(2.5.dp, ringBrush, CircleShape)
                      .padding(3.dp),
                    contentAlignment = Alignment.Center
                  ) {
                    GlassAvatar(
                      photoUrl = status.userPhotoUrl,
                      name = status.userName,
                      size = 54.dp
                    )
                  }
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = status.userName.split(" ").firstOrNull() ?: status.userName,
                    style = MaterialTheme.typography.labelSmall.copy(
                      color = TextPrimary,
                      fontSize = 11.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }
            }
          }
        }

        // Filter Chips Row
        item {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            filters.forEach { filter ->
              val isSelected = selectedFilter == filter
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(16.dp))
                  .background(
                    if (isSelected) CyanAccent.copy(alpha = 0.88f) else if (glassConfig.isDark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.72f)
                  )
                  .border(
                    width = 1.dp,
                    color = if (isSelected) Color.Transparent else GlassBorderStroke,
                    shape = RoundedCornerShape(16.dp)
                  )
                  .clickable {
                    if (filter == "Groups") {
                      onNavigateToGroups()
                    } else {
                      selectedFilter = filter
                    }
                  }
                  .padding(horizontal = 14.dp, vertical = 7.dp)
              ) {
                Text(
                  text = filter,
                  style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else TextSecondary,
                    fontSize = 13.sp
                  )
                )
              }
            }
          }
        }

        // Conversation List Header
        item {
          Text(
            text = "CHATS",
            style = MaterialTheme.typography.labelSmall.copy(
              color = TextMuted,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp,
              letterSpacing = 1.sp
            ),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
          )
        }

        // Conversation Items
        items(filteredConversations, key = { it.id }) { conv ->
          ConversationRowItem(
            conversation = conv,
            onClick = { onNavigateToConversation(conv.id) },
            onAvatarClick = { onNavigateToProfile(conv.otherUser.uid) },
            onArchiveToggle = { viewModel.setConversationArchived(conv.id, !conv.isArchived) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
          )
        }

        if (filteredConversations.isEmpty()) {
          item {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 40.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "No messages found in this view",
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

@Composable
fun ConversationRowItem(
  conversation: Conversation,
  onClick: () -> Unit,
  onAvatarClick: () -> Unit,
  onArchiveToggle: () -> Unit,
  modifier: Modifier = Modifier
) {
  val other = conversation.otherUser

  GlassCard(
    modifier = modifier
      .fillMaxWidth()
      .testTag("conversation_item_${conversation.id}"),
    shape = RoundedCornerShape(20.dp),
    elevation = 4.dp,
    onClick = onClick
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Avatar with Online dot
      GlassAvatar(
        photoUrl = other.photoUrl,
        name = other.displayName,
        size = 52.dp,
        isOnline = conversation.isOnline,
        onClick = onAvatarClick
      )

      Spacer(modifier = Modifier.width(14.dp))

      // Center Details: Name + Last Message
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = other.displayName,
            style = MaterialTheme.typography.titleMedium.copy(
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
              fontSize = 15.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
          )

          if (other.isVerified) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
              imageVector = Icons.Default.Verified,
              contentDescription = "Verified",
              tint = CyanAccent,
              modifier = Modifier.size(16.dp)
            )
          }

          if (conversation.isPinned) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
              imageVector = Icons.Default.PushPin,
              contentDescription = "Pinned",
              tint = AmberPinned,
              modifier = Modifier.size(14.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(3.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
          if (conversation.lastMessageSenderId == "usr_current_arjun") {
            Icon(
              imageVector = Icons.Default.DoneAll,
              contentDescription = "Delivered",
              tint = CyanAccent,
              modifier = Modifier
                .size(15.dp)
                .padding(end = 4.dp)
            )
          }

          if (conversation.lastMessageText.contains("Voice message")) {
            Icon(
              imageVector = Icons.Default.Mic,
              contentDescription = null,
              tint = CyanAccent,
              modifier = Modifier
                .size(14.dp)
                .padding(end = 4.dp)
            )
          }

          Text(
            text = if (conversation.isTyping) "Typing..." else conversation.lastMessageText,
            style = MaterialTheme.typography.bodyMedium.copy(
              color = if (conversation.isTyping) CyanAccent else if (conversation.unreadCount > 0) TextPrimary else TextSecondary,
              fontWeight = if (conversation.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
              fontSize = 13.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      Spacer(modifier = Modifier.width(10.dp))

      // Trailing: Time + Unread Badge
      Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        Text(
          text = timeFormat.format(Date(conversation.lastMessageTime)),
          style = MaterialTheme.typography.bodySmall.copy(
            color = if (conversation.unreadCount > 0) CyanAccent else TextMuted,
            fontSize = 11.sp
          )
        )

        GlassBadge(count = conversation.unreadCount)
      }
      IconButton(onClick = onArchiveToggle) {
        Icon(
          imageVector = if (conversation.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
          contentDescription = if (conversation.isArchived) "Unarchive" else "Archive",
          tint = TextMuted,
          modifier = Modifier.size(20.dp)
        )
      }
    }
  }
}

@Composable
fun GlassBottomBar(
  selectedRoute: String,
  onNavigateToChats: () -> Unit,
  onNavigateToGroups: () -> Unit,
  onNavigateToUpdates: () -> Unit,
  onNavigateToCalls: () -> Unit,
  onNavigateToSettings: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 12.dp),
    contentAlignment = Alignment.Center
  ) {
    GlassCard(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(26.dp),
      elevation = 12.dp
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
      ) {
        BottomNavItem(
          icon = Icons.Default.Chat,
          label = "Chats",
          isSelected = selectedRoute == "chats",
          onClick = onNavigateToChats,
          testTag = "nav_chats"
        )
        BottomNavItem(
          icon = Icons.Default.Group,
          label = "Groups",
          isSelected = selectedRoute == "groups",
          onClick = onNavigateToGroups,
          testTag = "nav_groups"
        )
        BottomNavItem(
          icon = Icons.Default.PhotoCamera,
          label = "Updates",
          isSelected = selectedRoute == "updates",
          onClick = onNavigateToUpdates,
          testTag = "nav_updates"
        )
        BottomNavItem(
          icon = Icons.Default.Call,
          label = "Calls",
          isSelected = selectedRoute == "calls",
          onClick = onNavigateToCalls,
          testTag = "nav_calls"
        )
        BottomNavItem(
          icon = Icons.Default.Settings,
          label = "Settings",
          isSelected = selectedRoute == "settings",
          onClick = onNavigateToSettings,
          testTag = "nav_settings"
        )
      }
    }
  }
}

@Composable
fun BottomNavItem(
  icon: ImageVector,
  label: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  testTag: String
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .clip(RoundedCornerShape(14.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 6.dp)
      .testTag(testTag)
  ) {
    Box(
      modifier = Modifier
        .clip(RoundedCornerShape(12.dp))
        .background(if (isSelected) CyanAccent.copy(alpha = 0.2f) else Color.Transparent)
        .padding(horizontal = 12.dp, vertical = 4.dp),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (isSelected) CyanAccent else TextMuted,
        modifier = Modifier.size(22.dp)
      )
    }

    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        color = if (isSelected) CyanAccent else TextMuted
      )
    )
  }
}
