package com.example.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.example.BuildConfig
import com.example.R

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Whatshot
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import androidx.compose.ui.unit.dp
import com.example.data.repository.removeProfilePhoto
import com.example.ui.components.*
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.viewmodel.LiquidChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
  viewModel: LiquidChatViewModel,
  onBackClick: () -> Unit,
  onNavigateToAppearance: () -> Unit,
  onNavigateToDiagnostics: () -> Unit,
  onNavigateToHomeTab: (String) -> Unit,
  onNavigateToProfile: (String) -> Unit,
  onNavigateToContacts: () -> Unit,
  onLogout: () -> Unit
) {
  val upload by viewModel.upload.collectAsStateWithLifecycle()
  val me by viewModel.currentUser.collectAsStateWithLifecycle()
  val privacy by viewModel.privacy.collectAsStateWithLifecycle()
  val notifications by viewModel.notifications.collectAsStateWithLifecycle()
  val appearance by viewModel.appearance.collectAsStateWithLifecycle()
  val blocked by viewModel.blockedUserIds.collectAsStateWithLifecycle()
  val users by viewModel.users.collectAsStateWithLifecycle()
  val conversations by viewModel.conversations.collectAsStateWithLifecycle()
  val deletionStatus by viewModel.deletionStatus.collectAsStateWithLifecycle()
  val deletionPending by viewModel.deletionPending.collectAsStateWithLifecycle()
  val deletionRunning by viewModel.deletionRunning.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val glass = LocalLiquidGlass.current

  var dialog by remember { mutableStateOf("") }
  var name by remember(me.displayName) { mutableStateOf(me.displayName) }
  var username by remember(me.username) { mutableStateOf(me.username) }
  var bio by remember(me.bio) { mutableStateOf(me.bio) }
  var phone by remember(me.phoneNumber) { mutableStateOf(me.phoneNumber) }
  var usernameStatus by remember { mutableStateOf<String?>(null) }
  var checkingUsername by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  var deletionPassword by remember { mutableStateOf("") }
  var deletionError by remember { mutableStateOf<String?>(null) }
  var cacheSize by remember { mutableLongStateOf(0L) }
  var photoDeleting by remember { mutableStateOf(false) }
  var photoDeleteFailed by remember { mutableStateOf(false) }
  var locallyRemovedPhoto by remember { mutableStateOf(false) }
  var pendingPhotoUri by remember { mutableStateOf<android.net.Uri?>(null) }
  var showOwnPhoto by remember { mutableStateOf(false) }
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val googleProvider = viewModel.hasGoogleProvider()
  LaunchedEffect(deletionPending) { if (deletionPending) dialog = "Confirm deletion" }

  fun beginPermanentDeletion() {
    if (busy || deletionRunning) return
    busy = true
    deletionError = null
    scope.launch {
      try {
        val googleToken = if (googleProvider && !deletionPending) {
          val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(context.getString(R.string.default_web_client_id))
            .setAutoSelectEnabled(false).build()
          val credential = credentialManager.getCredential(context,
            GetCredentialRequest.Builder().addCredentialOption(option).build()).credential
          check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) { "Google verification did not return a supported credential" }
          GoogleIdTokenCredential.createFrom(credential.data).idToken
        } else null
        viewModel.deleteAccount(deletionPassword, googleToken) { ok, message ->
          busy = false
          deletionPassword = ""
          if (ok) { dialog = ""; onLogout() } else deletionError = message
        }
      } catch (_: GetCredentialCancellationException) {
        busy = false
      } catch (t: Exception) {
        if (t is kotlinx.coroutines.CancellationException) throw t
        busy = false
        deletionError = t.message ?: "Could not verify your account"
      }
    }
  }

  fun beginSessionLogout() {
    if (busy) return
    busy = true
    deletionError = null
    viewModel.logout { ok, message ->
      busy = false
      if (ok) {
        dialog = ""
        onLogout()
      } else {
        deletionError = message ?: "Could not log out"
      }
    }
  }

  LaunchedEffect(me.photoUrl) {
    if (me.photoUrl.isNotBlank() && !photoDeleting) locallyRemovedPhoto = false
  }

  val shownPhoto = if (locallyRemovedPhoto) "" else me.photoUrl

  val photo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    if (uri != null) pendingPhotoUri = uri
  }

  fun removePhoto() {
    if (photoDeleting || shownPhoto.isBlank()) return
    photoDeleting = true
    photoDeleteFailed = false
    scope.launch {
      delay(if (glass.isReducedMotion) 80 else 420)
      val result = viewModel.repository.removeProfilePhoto()
      photoDeleting = false
      if (result.isSuccess) locallyRemovedPhoto = true else photoDeleteFailed = true
    }
  }

  LiquidBackground(crystal = true) {
    Scaffold(
      containerColor = Color.Transparent,
      bottomBar = {
        val totalUnread = conversations.sumOf { it.unreadCount }
        GlassBottomBar(
          selectedRoute = "settings",
          onNavigateToChats = { onNavigateToHomeTab("All") },
          onNavigateToContacts = onNavigateToContacts,
          onNavigateToSettings = {},
          unreadChatsCount = totalUnread
        )
      }
    ) { padding ->
      Column(
        Modifier
          .fillMaxSize()
          .padding(padding)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        GlassHeader("Settings", subtitle = "Your profile, preferences and privacy", onBackClick = onBackClick)

        GlassCard(
          modifier = Modifier.fillMaxWidth(),
          shape = liquidRoundedShape(28f),
          backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = .94f),
          borderColor = MaterialTheme.colorScheme.outline.copy(alpha = .14f),
          elevation = 0.dp,
          enableBackdrop = false
        ) {
          Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(contentAlignment = Alignment.BottomEnd) {
                DustDeleteContainerV2(active = photoDeleting, reduced = glass.isReducedMotion) {
                  GlassAvatar(
                    shownPhoto,
                    me.displayName.ifBlank { "Your profile" },
                    68.dp,
                    onClick = { if (!photoDeleting) showOwnPhoto = true }
                  )
                }
                GlassIconButton(
                  Icons.Default.PhotoCamera,
                  "Change photo",
                  { photo.launch("image/*") },
                  size = 32.dp,
                  tint = MaterialTheme.colorScheme.onPrimary,
                  backgroundColor = glass.accentColor.copy(alpha = .88f)
                )
              }
              Spacer(Modifier.width(14.dp))
              Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                  me.displayName.ifBlank { "Your profile" },
                  style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1
                )
                Text(
                  me.bio.ifBlank { "Hey there! I am using Liquid Chat" },
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 2
                )
                Box(
                  Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
                    .background(glass.accentColor.copy(alpha = .14f))
                    .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                  Text(
                    "@" + me.username.ifBlank { "username" },
                    style = MaterialTheme.typography.labelMedium,
                    color = glass.accentColor
                  )
                }
              }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              ProfileGlassAction(
                text = "Edit profile",
                modifier = Modifier.weight(1f),
                onClick = { dialog = "Profile" }
              )
              ProfileGlassAction(
                text = if (photoDeleting) "Removing…" else "Remove photo",
                modifier = Modifier.weight(1f),
                enabled = shownPhoto.isNotBlank() && !photoDeleting,
                destructive = true,
                onClick = ::removePhoto
              )
            }
            if (photoDeleteFailed) {
              Text(
                "Could not remove the profile photo. Try again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
              )
            }
          }
        }

        GlassCard(
          modifier = Modifier.fillMaxWidth(),
          shape = liquidRoundedShape(24f),
          backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = .94f),
          borderColor = MaterialTheme.colorScheme.outline.copy(alpha = .14f),
          elevation = 0.dp,
          enableBackdrop = false
        ) {
          Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            SettingsStat(Icons.Default.ChatBubbleOutline, conversations.size.toString(), "Chats", glass.accentColor, Modifier.weight(1f))
            SettingsMetricDivider()
            SettingsStat(Icons.Default.Whatshot, conversations.count { it.isPinned }.toString(), "Favorites", Color(0xFFFFB85C), Modifier.weight(1f))
            SettingsMetricDivider()
            SettingsStat(Icons.Default.People, blocked.size.toString(), "Blocked", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
          }
        }

        SettingsSection("ACCOUNT & PRIVACY") {
          ProfessionalSettingItem(Icons.Default.Person, "Account", "Display name, username, phone", Color(0xFF007AFF)) { dialog = "Profile" }
          SettingDivider()
          ProfessionalSettingItem(Icons.Default.Lock, "Privacy", "Last seen, read receipts, online status", Color(0xFF34C759)) { dialog = "Privacy" }
          SettingDivider()
          ProfessionalSettingItem(Icons.Default.ChatBubbleOutline, "Chats", "Wallpapers and conversation shortcuts", Color(0xFF30D158)) { onNavigateToHomeTab("All") }
          SettingDivider()
          ProfessionalSettingItem(Icons.Default.Notifications, "Notifications", "Alerts, vibration and message previews", Color(0xFFFF3B30)) { dialog = "Notifications" }
        }

        SettingsSection("PERSONALIZATION & DATA") {
          ProfessionalSettingItem(
            Icons.Default.Palette,
            "Appearance",
            "Liquid Glass, accent palettes and motion",
            Color(0xFFAF52DE),
            value = if (appearance.isDarkMode) "Dark" else "Light"
          ) { onNavigateToAppearance() }
          SettingDivider()
          ProfessionalSettingItem(Icons.Default.Storage, "Storage and Data", "Cached media and local storage", Color(0xFF32ADE6)) {
            scope.launch {
              cacheSize = withContext(Dispatchers.IO) {
                context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
              }
              dialog = "Data & Storage"
            }
          }
        }

        SettingsSection("SUPPORT") {
          ProfessionalSettingItem(Icons.Default.HelpOutline, "Help & Support", "FAQ and direct developer contact", Color(0xFFFF9500)) { dialog = "About & Support" }
          SettingDivider()
          ProfessionalSettingItem(Icons.Default.DeveloperMode, "Developer Diagnostics", "Realtime sync, cache and quotas", Color(0xFF5856D6)) { onNavigateToDiagnostics() }
        }

        SettingsSection("ACCOUNT ACTIONS") {
          ProfessionalSettingItem(Icons.Default.ExitToApp, "Log Out", "Clear this device session safely", Color(0xFFFF9F0A)) { dialog = "Log out" }
          SettingDivider()
          ProfessionalSettingItem(Icons.Default.DeleteForever, "Delete Account", "Permanently erase account and cloud data", Color(0xFFFF453A), destructive = true) { dialog = "Delete account" }
        }

        Column(
          Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
          Text(
            "Liquid Chat v" + BuildConfig.VERSION_NAME,
            color = glass.accentColor.copy(alpha = .86f),
            style = MaterialTheme.typography.labelLarge
          )
          Text(
            "Your conversations, across your devices",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
          )
        }

        Spacer(Modifier.height(8.dp))
      }
    }

    if (showOwnPhoto) {
      ProfilePhotoViewer(
        photoUrl = shownPhoto,
        displayName = me.displayName.ifBlank { "Your profile" },
        onDismiss = { showOwnPhoto = false }
      )
    }

    pendingPhotoUri?.let { source ->
      ProfilePhotoCropDialog(
        sourceUri = source,
        onDismiss = { pendingPhotoUri = null },
        onCropped = { cropped ->
          pendingPhotoUri = null
          viewModel.uploadProfilePhoto(cropped) { result ->
            cropped.path?.let { path -> runCatching { java.io.File(path).delete() } }
            if (result.isSuccess) {
              locallyRemovedPhoto = false
              photoDeleteFailed = false
            }
          }
        }
      )
    }

    if (dialog.isNotBlank()) {
      GlassDialog(dialog, onDismiss = { if (!busy && !deletionRunning && !deletionPending) { dialog = ""; deletionPassword = ""; deletionError = null } }) {
        when (dialog) {
          "Profile" -> {
            GlassTextField(name, { name = it.take(60) }, placeholder = "Name")
            GlassTextField(
              username,
              {
                username = it.lowercase().take(32)
                usernameStatus = null
              },
              placeholder = "Username"
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
              TextButton(
                enabled = !checkingUsername && username.isNotBlank(),
                onClick = {
                  checkingUsername = true
                  scope.launch {
                    val result = viewModel.checkUsernameAvailability(username)
                    usernameStatus = result.fold(
                      onSuccess = { if (it) "Username is available" else "Username is already taken" },
                      onFailure = { it.message ?: "Could not check username" }
                    )
                    checkingUsername = false
                  }
                }
              ) { Text(if (checkingUsername) "Checking…" else "Check availability") }
              usernameStatus?.let {
                Text(
                  it,
                  style = MaterialTheme.typography.bodySmall,
                  color = if (it.contains("available", ignoreCase = true) && !it.contains("taken", ignoreCase = true))
                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
              }
            }
            GlassTextField(
              bio,
              { bio = it.take(160) },
              placeholder = "About",
              singleLine = false,
              maxLines = 4
            )
            GlassTextField(
              phone,
              { phone = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' || ch == '-' }.take(30) },
              placeholder = "Phone (optional)"
            )
            GlassButton(
              "Save",
              {
                viewModel.updateProfile(name.trim(), username.trim(), bio.trim(), phone.trim())
                dialog = ""
              },
              modifier = Modifier.fillMaxWidth()
            )
          }

          "Privacy" -> {
            Toggle("Show last seen", privacy.lastSeenVisibility != "Nobody") {
              viewModel.updatePrivacy(privacy.copy(lastSeenVisibility = if (it) "Everyone" else "Nobody"))
            }
            Toggle("Show online status", privacy.onlineVisibility != "Nobody") {
              viewModel.updatePrivacy(privacy.copy(onlineVisibility = if (it) "Everyone" else "Nobody"))
            }
            Toggle("Show profile photo", privacy.profilePhotoVisibility != "Nobody") {
              viewModel.updatePrivacy(privacy.copy(profilePhotoVisibility = if (it) "Everyone" else "Nobody"))
            }
            Toggle("Read receipts", privacy.readReceipts) {
              viewModel.updatePrivacy(privacy.copy(readReceipts = it))
            }
          }

          "Notifications" -> {
            Toggle("Message notifications", notifications.messages) {
              viewModel.updateNotifications(notifications.copy(messages = it))
            }
            Toggle("Vibration", notifications.vibration) {
              viewModel.updateNotifications(notifications.copy(vibration = it))
            }
            Toggle("Show message previews", notifications.showPreview) {
              viewModel.updateNotifications(notifications.copy(showPreview = it))
            }
            Text(
              "When previews are off, notifications hide the sender and message text.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          "Data & Storage" -> {
            Text("Temporary cache: ${cacheSize / 1024 / 1024} MB")
            Text("Chat attachments upload directly to Cloudinary; the returned secure URL is saved in Firestore. Maximum attachment size: 9 MB.")
            Text("Liquid Chat keeps a local message cache for fast reopening and offline viewing.")
            Text("Uninstalling clears this device's local cache; Firestore chat data is not automatically erased.")
            Text("Clear cache removes temporary downloads from this device. To permanently erase your account and cloud data, use Delete account or Log out and wait for deletion to finish.")
            if (upload != null) {
              Text("Wait for the current upload to finish before clearing cache.")
            } else {
              GlassButton(
                "Clear temporary cache",
                {
                  scope.launch {
                    withContext(Dispatchers.IO) {
                      context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                    }
                    com.example.data.network.LiquidApi.clear()
                    cacheSize = 0L
                  }
                },
                modifier = Modifier.fillMaxWidth(),
                isPrimary = false
              )
            }
          }

          "Blocked contacts" -> {
            if (blocked.isEmpty()) Text("No blocked contacts")
            blocked.forEach { id ->
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(users.find { it.uid == id }?.displayName ?: "Contact", Modifier.weight(1f))
                TextButton(onClick = { viewModel.unblockUser(id) }) { Text("Unblock") }
              }
            }
          }

          "About & Support" -> {
            Text("Liquid Chat ${BuildConfig.VERSION_NAME}")
            Text("Developed by Pritam Pal")
            Text("© 2026 Pritam Pal")
            Text("Firebase Auth + Cloud Firestore realtime data + direct Cloudinary media hosting.")
            Text("Messages use Firebase Auth + participant-restricted Firestore access. Cloudinary media uses secure HTTPS delivery URLs.")
            TextButton(
              onClick = {
                context.startActivity(
                  android.content.Intent(
                    android.content.Intent.ACTION_SENDTO,
                    android.net.Uri.parse("mailto:officialpritam07@gmail.com")
                  )
                )
              }
            ) { Text("Contact support") }
          }

          "Log out" -> {
            Text("Log out removes this device session, local message cache and notifications. Your Firebase account, conversations and uploaded media remain in the cloud.")
            Text("Signing in again restores your cloud conversations and rebuilds the local cache.")
            deletionError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            GlassButton(
              text = "Log out",
              onClick = { beginSessionLogout() },
              modifier = Modifier.fillMaxWidth(),
              isLoading = busy,
              enabled = !busy
            )
          }

          "Delete account" -> {
            Text("Delete Account permanently removes your profile, one-to-one conversation data and owned app media after trusted cloud cleanup.")
            Text("This cannot be undone. Logging out is available separately if you only want to end this device session.")
            GlassButton(
              text = "Continue",
              onClick = { dialog = "Confirm deletion" },
              modifier = Modifier.fillMaxWidth()
            )
          }

          "Confirm deletion" -> {
            Text("Final confirmation: permanently erase this account and cloud data.")
            Text("Keep the app installed until deletion is confirmed. Uninstalling alone cannot erase cloud data.")
            Text("Final verification can include the old-session expiry window plus cleanup-worker scheduling.")
            if (!googleProvider && !deletionPending) GlassTextField(
              deletionPassword, { deletionPassword = it }, placeholder = "Confirm password",
              visualTransformation = PasswordVisualTransformation()
            )
            deletionStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            deletionError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            GlassButton(
              text = if (deletionPending) "Check deletion" else if (googleProvider) "Verify with Google and delete" else "Permanently delete account",
              onClick = { beginPermanentDeletion() },
              modifier = Modifier.fillMaxWidth(),
              isLoading = busy || deletionRunning,
              enabled = !busy && !deletionRunning
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SettingsStat(
  icon: ImageVector,
  value: String,
  label: String,
  color: Color,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier.padding(horizontal = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Box(
      Modifier
        .size(36.dp)
        .clip(androidx.compose.foundation.shape.RoundedCornerShape(11.dp))
        .background(color.copy(alpha = .16f)),
      contentAlignment = Alignment.Center
    ) {
      Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(19.dp))
    }
    Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun SettingsMetricDivider() {
  Box(
    Modifier
      .width(1.dp)
      .height(32.dp)
      .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .10f))
  )
}

@Composable
private fun ProfileGlassAction(
  text: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  destructive: Boolean = false,
  onClick: () -> Unit
) {
  val glass = LocalLiquidGlass.current
  val foreground = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
  Box(
    modifier
      .defaultMinSize(minHeight = 48.dp)
      .clip(liquidRoundedShape(18f))
      .background(if (glass.isDark) Color.White.copy(alpha = .08f) else Color.White.copy(alpha = .55f))
      .clickable(enabled = enabled, onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 11.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text,
      style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
      color = if (enabled) foreground else foreground.copy(alpha = .35f)
    )
  }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
  val glass = LocalLiquidGlass.current
  Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
    Text(
      title,
      style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 10.dp)
    )
    GlassCard(
      modifier = Modifier.fillMaxWidth(),
      shape = liquidRoundedShape(24f),
      backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = .94f),
      borderColor = MaterialTheme.colorScheme.outline.copy(alpha = .14f),
      elevation = 0.dp,
      enableBackdrop = false
    ) {
      Column(Modifier.padding(vertical = 4.dp), content = content)
    }
  }
}

@Composable
private fun ProfessionalSettingItem(
  icon: ImageVector,
  title: String,
  subtitle: String,
  badgeColor: Color,
  value: String? = null,
  destructive: Boolean = false,
  onClick: () -> Unit
) {
  Row(
    Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      Modifier
        .size(36.dp)
        .clip(androidx.compose.foundation.shape.RoundedCornerShape(11.dp))
        .background(badgeColor),
      contentAlignment = Alignment.Center
    ) {
      Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
    Spacer(Modifier.width(16.dp))
    Column(Modifier.weight(1f)) {
      Text(
        title,
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
        color = if (destructive) Color(0xFFFF453A) else MaterialTheme.colorScheme.onSurface
      )
      Text(
        subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
      )
    }
    if (!value.isNullOrBlank()) {
      Text(
        value,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(Modifier.width(7.dp))
    }
    Icon(
      Icons.Default.ChevronRight,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .72f),
      modifier = Modifier.size(20.dp)
    )
  }
}

@Composable
private fun SettingDivider() {
  HorizontalDivider(
    modifier = Modifier.padding(start = 66.dp, end = 14.dp),
    thickness = .5.dp,
    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .10f)
  )
}

@Composable
private fun Toggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(
    Modifier.fillMaxWidth().padding(vertical = 3.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(title, Modifier.weight(1f))
    Switch(checked = checked, onCheckedChange = onChange)
  }
}
