package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.net.Uri
import com.example.ui.components.GlassAvatar
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassTextField
import com.example.ui.components.LiquidBackground
import com.example.ui.theme.CoralEndCall
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.LiquidChatViewModel

@Composable
fun SettingsScreen(
  viewModel: LiquidChatViewModel,
  onBackClick: () -> Unit,
  onNavigateToAppearance: () -> Unit,
  onLogout: () -> Unit,
  modifier: Modifier = Modifier
) {
  val currentUser by viewModel.currentUser.collectAsState()
  val blockedUserIds by viewModel.blockedUserIds.collectAsState()
  val privacy by viewModel.privacy.collectAsState()

  var showEditProfileDialog by remember { mutableStateOf(false) }
  var showLogoutConfirmDialog by remember { mutableStateOf(false) }
  var showAboutDialog by remember { mutableStateOf(false) }
  var showPrivacyDialog by remember { mutableStateOf(false) }
  var showNotificationsDialog by remember { mutableStateOf(false) }
  var showStorageDialog by remember { mutableStateOf(false) }
  var activePrivacyField by remember { mutableStateOf<String?>(null) }
  var photoUploading by remember { mutableStateOf(false) }

  val profilePhotoPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    uri ?: return@rememberLauncherForActivityResult
    photoUploading = true
    viewModel.uploadProfilePhoto(uri) { photoUploading = false }
  }

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
              .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(
              onClick = onBackClick,
              modifier = Modifier.testTag("settings_back_button")
            ) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }

            Text(
              text = "Settings",
              style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 18.sp
              )
            )
          }
        }
      }
    ) { innerPadding ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // User Profile Summary Card
        GlassCard(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(24.dp)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            GlassAvatar(
              photoUrl = currentUser.photoUrl,
              name = currentUser.displayName,
              size = 64.dp,
              isOnline = currentUser.isOnline,
              modifier = Modifier.clickable(enabled = !photoUploading) {
                profilePhotoPicker.launch("image/*")
              }
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = currentUser.displayName,
                style = MaterialTheme.typography.titleMedium.copy(
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary
                )
              )
              Text(
                text = "@${currentUser.username}",
                style = MaterialTheme.typography.bodySmall.copy(color = CyanAccent)
              )
              Text(
                text = currentUser.bio,
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp)
              )
            }

            IconButton(
              onClick = { showEditProfileDialog = true },
              modifier = Modifier.testTag("edit_profile_button")
            ) {
              Icon(Icons.Default.Edit, contentDescription = "Edit Profile", tint = CyanAccent)
            }
          }
        }

        // Settings Category List
        GlassCard(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(24.dp)
        ) {
          Column(modifier = Modifier.padding(vertical = 8.dp)) {
            SettingsNavigationRow(
              icon = Icons.Default.Palette,
              title = "Appearance & Liquid Glass",
              subtitle = "Themes, blur intensity, refraction, accents",
              onClick = onNavigateToAppearance,
              testTag = "settings_appearance_row"
            )

            SettingsNavigationRow(
              icon = Icons.Default.Lock,
              title = "Privacy & Security",
              subtitle = "Last seen, read receipts, end-to-end encryption",
              onClick = { showPrivacyDialog = true }
            )

            SettingsNavigationRow(
              icon = Icons.Default.Notifications,
              title = "Notifications & Sounds",
              subtitle = "Messages, groups, ringtones, vibration",
              onClick = { showNotificationsDialog = true }
            )

            SettingsNavigationRow(
              icon = Icons.Default.Storage,
              title = "Data & Storage",
              subtitle = "Network usage, auto-download media",
              onClick = { showStorageDialog = true }
            )

            SettingsNavigationRow(
              icon = Icons.Default.Block,
              title = "Blocked Users",
              subtitle = "${blockedUserIds.size} contacts blocked",
              onClick = { }
            )

            SettingsNavigationRow(
              icon = Icons.Default.Info,
              title = "About Liquid Chat",
              subtitle = "Version 2.7.0 • Liquid Glass Protocol",
              onClick = { showAboutDialog = true },
              testTag = "settings_about_row"
            )
          }
        }

        // Logout & Delete Account Actions
        GlassCard(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(24.dp)
        ) {
          Column(modifier = Modifier.padding(8.dp)) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { showLogoutConfirmDialog = true }
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .testTag("logout_button"),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.Logout, contentDescription = null, tint = CoralEndCall)
              Spacer(modifier = Modifier.width(14.dp))
              Text("Log Out of Liquid Chat", color = CoralEndCall, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { showLogoutConfirmDialog = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFEF4444))
              Spacer(modifier = Modifier.width(14.dp))
              Text("Delete Account", color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
          }
        }

        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }

  // Edit Profile Dialog
  if (showEditProfileDialog) {
    var editName by remember { mutableStateOf(currentUser.displayName) }
    var editUsername by remember { mutableStateOf(currentUser.username) }
    var editBio by remember { mutableStateOf(currentUser.bio) }
    var editPhone by remember { mutableStateOf(currentUser.phoneNumber) }

    Dialog(onDismissRequest = { showEditProfileDialog = false }) {
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
          Text("Edit Liquid Profile", style = MaterialTheme.typography.titleLarge.copy(color = TextPrimary, fontWeight = FontWeight.Bold))

          Spacer(modifier = Modifier.height(16.dp))

          GlassTextField(value = editName, onValueChange = { editName = it }, placeholder = "Full Name")
          Spacer(modifier = Modifier.height(10.dp))
          GlassTextField(value = editUsername, onValueChange = { editUsername = it }, placeholder = "Username")
          Spacer(modifier = Modifier.height(10.dp))
          GlassTextField(value = editBio, onValueChange = { editBio = it }, placeholder = "Status Bio")
          Spacer(modifier = Modifier.height(10.dp))
          GlassTextField(value = editPhone, onValueChange = { editPhone = it }, placeholder = "Phone Number")

          Spacer(modifier = Modifier.height(18.dp))

          Row(modifier = Modifier.fillMaxWidth()) {
            GlassButton(text = "Cancel", onClick = { showEditProfileDialog = false }, isPrimary = false, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            GlassButton(
              text = "Save",
              onClick = {
                viewModel.updateProfile(editName, editUsername, editBio, editPhone)
                showEditProfileDialog = false
              },
              isPrimary = true,
              modifier = Modifier.weight(1f)
            )
          }
        }
      }
    }
  }

  if (showPrivacyDialog) {
    AlertDialog(
      onDismissRequest = { showPrivacyDialog = false },
      title = { Text("Privacy & Security", color = TextPrimary, fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          PrivacySelectorRow("Last seen", privacy.lastSeenVisibility) { activePrivacyField = "lastSeen" }
          PrivacySelectorRow("Online status", privacy.onlineVisibility) { activePrivacyField = "online" }
          PrivacySelectorRow("Profile photo", privacy.profilePhotoVisibility) { activePrivacyField = "photo" }
          PrivacySelectorRow("Status updates", privacy.statusVisibility) { activePrivacyField = "status" }
          PrivacySelectorRow("Group invites", privacy.whoCanAddToGroups) { activePrivacyField = "groups" }
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Read receipts", color = TextPrimary, fontWeight = FontWeight.SemiBold)
              Text("Show when messages are read", color = TextMuted, fontSize = 12.sp)
            }
            Switch(
              checked = privacy.readReceipts,
              onCheckedChange = { viewModel.updatePrivacy(privacy.copy(readReceipts = it)) }
            )
          }
          Text(
            "Changes sync to your Liquid Chat profile.",
            color = TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 8.dp)
          )
        }
      },
      confirmButton = { TextButton(onClick = { showPrivacyDialog = false }) { Text("Done", color = CyanAccent) } },
      containerColor = Color(0xFF0F172A)
    )
  }

  activePrivacyField?.let { field ->
    val options = when (field) {
      "status" -> listOf("Everyone", "Contacts Only", "Nobody")
      "groups" -> listOf("Everyone", "Contacts Only")
      else -> listOf("Everyone", "Contacts Only", "Nobody")
    }
    val title = when (field) {
      "lastSeen" -> "Last seen"
      "online" -> "Online status"
      "photo" -> "Profile photo"
      "status" -> "Status updates"
      else -> "Group invites"
    }
    AlertDialog(
      onDismissRequest = { activePrivacyField = null },
      title = { Text(title, color = TextPrimary, fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          options.forEach { option ->
            val selected = when (field) {
              "lastSeen" -> privacy.lastSeenVisibility == option
              "online" -> privacy.onlineVisibility == option
              "photo" -> privacy.profilePhotoVisibility == option
              "status" -> privacy.statusVisibility == option
              else -> privacy.whoCanAddToGroups == option
            }
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  val updated = when (field) {
                    "lastSeen" -> privacy.copy(lastSeenVisibility = option)
                    "online" -> privacy.copy(onlineVisibility = option)
                    "photo" -> privacy.copy(profilePhotoVisibility = option)
                    "status" -> privacy.copy(statusVisibility = option)
                    else -> privacy.copy(whoCanAddToGroups = option)
                  }
                  viewModel.updatePrivacy(updated)
                  activePrivacyField = null
                }
                .padding(vertical = 11.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(option, color = if (selected) CyanAccent else TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
              if (selected) Text("✓", color = CyanAccent, fontWeight = FontWeight.Bold)
            }
          }
        }
      },
      confirmButton = { TextButton(onClick = { activePrivacyField = null }) { Text("Cancel", color = TextSecondary) } },
      containerColor = Color(0xFF0F172A)
    )
  }

  if (showNotificationsDialog) {
    AlertDialog(
      onDismissRequest = { showNotificationsDialog = false },
      title = { Text("Notifications", color = TextPrimary, fontWeight = FontWeight.Bold) },
      text = { Text("Message, group and call notifications are delivered through Firebase Cloud Messaging. Android notification permission can be changed from system settings.", color = TextSecondary) },
      confirmButton = { TextButton(onClick = { showNotificationsDialog = false }) { Text("Close", color = CyanAccent) } },
      containerColor = Color(0xFF0F172A)
    )
  }

  if (showStorageDialog) {
    AlertDialog(
      onDismissRequest = { showStorageDialog = false },
      title = { Text("Data & Storage", color = TextPrimary, fontWeight = FontWeight.Bold) },
      text = { Text("Media is stored in Firebase Storage. You can control Android photo/media permissions and notification permissions from system settings.", color = TextSecondary) },
      confirmButton = { TextButton(onClick = { showStorageDialog = false }) { Text("Close", color = CyanAccent) } },
      containerColor = Color(0xFF0F172A)
    )
  }

  // About Dialog
  if (showAboutDialog) {
    AlertDialog(
      onDismissRequest = { showAboutDialog = false },
      title = { Text("About Liquid Chat", color = TextPrimary, fontWeight = FontWeight.Bold) },
      text = {
        Text(
          text = "Liquid Chat v2.7.0\n\nBuilt with the Liquid Glass UI system for real-time messaging, media sharing, groups, status, privacy controls, and call experiences. Firebase and device-level security features require the project configuration described in the setup guide.",
          color = TextSecondary,
          fontSize = 14.sp
        )
      },
      confirmButton = {
        TextButton(onClick = { showAboutDialog = false }) {
          Text("Close", color = CyanAccent)
        }
      },
      containerColor = Color(0xFF0F172A)
    )
  }

  // Logout Confirm Dialog
  if (showLogoutConfirmDialog) {
    AlertDialog(
      onDismissRequest = { showLogoutConfirmDialog = false },
      title = { Text("Log Out?", color = TextPrimary, fontWeight = FontWeight.Bold) },
      text = { Text("Are you sure you want to log out of Liquid Chat?", color = TextSecondary) },
      confirmButton = {
        TextButton(onClick = {
          showLogoutConfirmDialog = false
          viewModel.logout()
          onLogout()
        }) {
          Text("Log Out", color = CoralEndCall)
        }
      },
      dismissButton = {
        TextButton(onClick = { showLogoutConfirmDialog = false }) {
          Text("Cancel", color = TextSecondary)
        }
      },
      containerColor = Color(0xFF0F172A)
    )
  }
}

@Composable
private fun PrivacySelectorRow(title: String, value: String, onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = 9.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
      Text(value, color = TextMuted, fontSize = 12.sp)
    }
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
  }
}

@Composable
fun SettingsNavigationRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  testTag: String = ""
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 12.dp)
      .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(icon, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(24.dp))
    Spacer(modifier = Modifier.width(16.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
      Text(subtitle, color = TextMuted, fontSize = 12.sp)
    }
    Icon(
      Icons.AutoMirrored.Filled.KeyboardArrowRight,
      contentDescription = null,
      tint = TextMuted,
      modifier = Modifier.size(20.dp)
    )
  }
}
