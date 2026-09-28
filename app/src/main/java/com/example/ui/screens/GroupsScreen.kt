package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.Group
import com.example.ui.components.GlassAvatar
import com.example.ui.components.GlassBadge
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassTextField
import com.example.ui.components.LiquidBackground
import com.example.ui.theme.AmberPinned
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.TextMuted
import com.example.ui.theme.MaterialTheme.colorScheme.onBackground
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.LiquidChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GroupsScreen(
  viewModel: LiquidChatViewModel,
  onNavigateToGroupChat: (String) -> Unit,
  onNavigateToChats: () -> Unit,
  onNavigateToSettings: () -> Unit,
  onNavigateToSearch: () -> Unit,
  modifier: Modifier = Modifier
) {
  val groups by viewModel.groups.collectAsState()
  val users by viewModel.users.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()

  var showCreateGroupDialog by remember { mutableStateOf(false) }

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
              .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Liquid Groups",
              style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp
              ),
              modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onNavigateToSearch) {
              Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onBackground)
            }
          }
        }
      },
      bottomBar = {
        GlassBottomBar(
          selectedRoute = "groups",
          onNavigateToChats = onNavigateToChats,
          onNavigateToGroups = {},
          onNavigateToSettings = onNavigateToSettings
        )
      },
      floatingActionButton = {
        FloatingActionButton(
          onClick = { showCreateGroupDialog = true },
          containerColor = CyanAccent,
          contentColor = Color.Black,
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier
            .padding(bottom = 72.dp)
            .testTag("create_group_fab")
        ) {
          Icon(Icons.Default.Add, contentDescription = "Create Group")
        }
      }
    ) { innerPadding ->
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        item {
          Text(
            text = "COMMUNITIES & SPACES (${groups.size})",
            style = MaterialTheme.typography.labelSmall.copy(
              color = TextMuted,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp,
              letterSpacing = 1.sp
            ),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
          )
        }

        items(groups, key = { it.id }) { group ->
          GroupRowItem(
            group = group,
            onClick = { onNavigateToGroupChat(group.id) },
            isAdmin = currentUser.uid in group.adminIds,
            onDelete = { viewModel.deleteGroup(group.id) },
            onLeave = { viewModel.leaveGroup(group.id) }
          )
        }
      }
    }
  }

  // Create Group Dialog
  if (showCreateGroupDialog) {
    var groupName by remember { mutableStateOf("") }
    var groupDescription by remember { mutableStateOf("") }
    val selectedMembers = remember { mutableStateListOf<String>() }

    Dialog(onDismissRequest = { showCreateGroupDialog = false }) {
      GlassCard(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        shape = RoundedCornerShape(24.dp)
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = "New Liquid Group",
            style = MaterialTheme.typography.titleLarge.copy(
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onBackground
            )
          )

          Spacer(modifier = Modifier.height(16.dp))

          GlassTextField(
            value = groupName,
            onValueChange = { groupName = it },
            placeholder = "Group Name (e.g. Design Team)",
            testTag = "group_name_input"
          )

          Spacer(modifier = Modifier.height(10.dp))

          GlassTextField(
            value = groupDescription,
            onValueChange = { groupDescription = it },
            placeholder = "Group Purpose & Bio",
            testTag = "group_desc_input"
          )

          Spacer(modifier = Modifier.height(16.dp))

          Text(
            text = "Select Members:",
            style = MaterialTheme.typography.labelMedium.copy(
              color = TextSecondary,
              fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.fillMaxWidth()
          )

          Spacer(modifier = Modifier.height(8.dp))

          Column(
            modifier = Modifier
              .fillMaxWidth()
              .height(160.dp)
          ) {
            users.forEach { user ->
              val isChecked = selectedMembers.contains(user.uid)
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable {
                    if (isChecked) selectedMembers.remove(user.uid) else selectedMembers.add(user.uid)
                  }
                  .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                GlassAvatar(photoUrl = user.photoUrl, name = user.displayName, size = 32.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text = user.displayName,
                  color = MaterialTheme.colorScheme.onBackground,
                  style = MaterialTheme.typography.bodyMedium,
                  modifier = Modifier.weight(1f)
                )
                Checkbox(
                  checked = isChecked,
                  onCheckedChange = { checked ->
                    if (checked) selectedMembers.add(user.uid) else selectedMembers.remove(user.uid)
                  },
                  colors = CheckboxDefaults.colors(checkedColor = CyanAccent, checkmarkColor = Color.Black)
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
          ) {
            GlassButton(
              text = "Cancel",
              onClick = { showCreateGroupDialog = false },
              isPrimary = false,
              modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            GlassButton(
              text = "Create",
              onClick = {
                if (groupName.isNotBlank()) {
                  viewModel.createGroup(groupName, groupDescription, selectedMembers)
                  showCreateGroupDialog = false
                }
              },
              isPrimary = true,
              modifier = Modifier.weight(1f),
              testTag = "confirm_create_group"
            )
          }
        }
      }
    }
  }
}

@Composable
fun GroupRowItem(
  group: Group,
  onClick: () -> Unit,
  isAdmin: Boolean,
  onDelete: () -> Unit,
  onLeave: () -> Unit
) {
  var menuExpanded by remember { mutableStateOf(false) }
  var confirmAction by remember { mutableStateOf<String?>(null) }
  GlassCard(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("group_item_${group.id}"),
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
      GlassAvatar(
        photoUrl = group.photoUrl,
        name = group.name,
        size = 52.dp
      )

      Spacer(modifier = Modifier.width(14.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = group.name,
            style = MaterialTheme.typography.titleMedium.copy(
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onBackground,
              fontSize = 15.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
          )

          if (group.isPinned) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
              imageVector = Icons.Default.PushPin,
              contentDescription = "Pinned",
              tint = AmberPinned,
              modifier = Modifier.size(14.dp)
            )
          }

          if (group.onlyAdminsCanPost) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
              imageVector = Icons.Default.Lock,
              contentDescription = "Admin Only",
              tint = CyanAccent,
              modifier = Modifier.size(14.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
          text = "${group.members.size} members • ${group.lastMessageText}",
          style = MaterialTheme.typography.bodyMedium.copy(
            color = if (group.unreadCount > 0) MaterialTheme.colorScheme.onBackground else TextSecondary,
            fontSize = 12.5.sp
          ),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      Spacer(modifier = Modifier.width(8.dp))

      Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        Text(
          text = timeFormat.format(Date(group.lastMessageTime)),
          style = MaterialTheme.typography.bodySmall.copy(
            color = if (group.unreadCount > 0) CyanAccent else TextMuted,
            fontSize = 11.sp
          )
        )
        GlassBadge(count = group.unreadCount)
      }

      Box {
        IconButton(onClick = { menuExpanded = true }) {
          Icon(Icons.Default.MoreVert, contentDescription = "Group actions", tint = TextMuted)
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
          DropdownMenuItem(
            text = { Text("Leave group") },
            leadingIcon = { Icon(Icons.Default.ExitToApp, contentDescription = null) },
            onClick = { menuExpanded = false; confirmAction = "leave" }
          )
          if (isAdmin) {
            DropdownMenuItem(
              text = { Text("Delete group") },
              leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
              onClick = { menuExpanded = false; confirmAction = "delete" }
            )
          }
        }
      }
    }
  }

  confirmAction?.let { action ->
    Dialog(onDismissRequest = { confirmAction = null }) {
      GlassCard(modifier = Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
          Text(if (action == "delete") "Delete group?" else "Leave group?", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            if (action == "delete") "This removes the group for its members. This action cannot be undone." else "You will leave this group and it will disappear from your groups list.",
            color = TextSecondary
          )
          Spacer(modifier = Modifier.height(18.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text("Cancel", color = TextSecondary, modifier = Modifier.clickable { confirmAction = null }.padding(10.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Confirm", color = CyanAccent, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
              confirmAction = null
              if (action == "delete") onDelete() else onLeave()
            }.padding(10.dp))
          }
        }
      }
    }
  }
}
