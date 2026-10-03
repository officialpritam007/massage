package com.example.ui.screens

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
  val upload by viewModel.upload.collectAsState()
  val me by viewModel.currentUser.collectAsState()
  val privacy by viewModel.privacy.collectAsState()
  val notifications by viewModel.notifications.collectAsState()
  val appearance by viewModel.appearance.collectAsState()
  val blocked by viewModel.blockedUserIds.collectAsState()
  val users by viewModel.users.collectAsState()
  val messages by viewModel.messages.collectAsState()
  val conversations by viewModel.conversations.collectAsState()
  val deletionStatus by viewModel.deletionStatus.collectAsState()
  val deletionPending by viewModel.deletionPending.collectAsState()
  val deletionRunning by viewModel.deletionRunning.collectAsState()
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
        GlassHeader("Settings", subtitle = "Liquid Chat", onBackClick = onBackClick)

        GlassCard(
          modifier = Modifier.fillMaxWidth(),
          shape = androidx.compose.foundation.shape.RoundedCornerShape(30.dp),
          backgroundColor = if (glass.isDark) Color(0xFF142A31).copy(alpha = .76f) else Color.White.copy(alpha = .60f),
          borderColor = Color.White.copy(alpha = if (glass.isDark) .16f else .58f),
          elevation = 6.dp,
          onClick = { onNavigateToProfile(me.uid) }
        ) {
          Row(
            Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(contentAlignment = Alignment.BottomEnd) {
              DustDeleteContainerV2(active = photoDeleting, reduced = glass.isReducedMotion) {
                GlassAvatar(
                  shownPhoto,
                  me.displayName.ifBlank { "Your profile" },
                  66.dp,
                  onClick = { if (!photoDeleting) showOwnPhoto = true }
                )
              }
              GlassIconButton(
                Icons.Default.PhotoCamera,
                "Change photo",
                { photo.launch("image/*") },
                size = 32.dp,
                tint = Color.White,
                backgroundColor = glass.accentColor.copy(alpha = .86f)
              )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
              Text(
                me.displayName.ifBlank { "Your profile" },
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
              )
              Text(
                me.bio.ifBlank { "Hey there! I am using Liquid Chat" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
              )
              Row {
                TextButton(onClick = { dialog = "Profile" }, contentPadding = PaddingValues(0.dp)) {
                  Text("Edit profile", color = glass.accentColor)
                }
                if (shownPhoto.isNotBlank()) {
                  Spacer(Modifier.width(10.dp))
                  TextButton(onClick = ::removePhoto, contentPadding = PaddingValues(0.dp)) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                  }
                }
              }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
          }
        }

        val sentCount = messages.values.sumOf { list -> list.count { it.senderId == me.uid } }
        val activeDays = (((System.currentTimeMillis() - me.createdAt).coerceAtLeast(0L) / 86_400_000L) + 1L).coerceAtMost(999L)
        val contactCount = conversationsCountForSettings(messages, users)

        GlassCard(
          modifier = Modifier.fillMaxWidth(),
          shape = androidx.compose.foundation.shape.RoundedCornerShape(30.dp),
          backgroundColor = if (glass.isDark) Color(0xFF142A31).copy(alpha = .72f) else Color.White.copy(alpha = .56f),
          borderColor = Color.White.copy(alpha = if (glass.isDark) .14f else .54f),
          elevation = 5.dp
        ) {
          Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
          ) {
            SettingsStat(Icons.Default.Whatshot, activeDays.toString(), "Days active")
            SettingsStat(Icons.Default.ChatBubbleOutline, sentCount.toString(), "Loaded sent messages")
            SettingsStat(Icons.Default.People, contactCount.toString(), "Contacts")
          }
        }

        GlassVisualSettingRow(Icons.Default.Person, "Account") { dialog = "Profile" }
        GlassVisualSettingRow(Icons.Default.Lock, "Privacy") { dialog = "Privacy" }
        GlassVisualSettingRow(Icons.Default.ChatBubbleOutline, "Chats") { onNavigateToHomeTab("All") }
        GlassVisualSettingRow(Icons.Default.Notifications, "Notifications") { dialog = "Notifications" }
        GlassVisualSettingRow(Icons.Default.Storage, "Storage and data") {
          scope.launch {
            cacheSize = withContext(Dispatchers.IO) {
              context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            }
            dialog = "Data & Storage"
          }
        }
        GlassVisualSettingRow(Icons.Default.Palette, "Appearance") { onNavigateToAppearance() }
        GlassVisualSettingRow(Icons.Default.HelpOutline, "Help") { dialog = "About & Support" }

        Text(
          "Liquid Chat v" + BuildConfig.VERSION_NAME,
          modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
          textAlign = androidx.compose.ui.text.style.TextAlign.Center,
          color = glass.accentColor.copy(alpha = .82f),
          style = MaterialTheme.typography.bodyMedium
        )

        GlassCard(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)) {
          Column(Modifier.padding(8.dp)) {
            TextButton(onClick = onNavigateToDiagnostics, modifier = Modifier.fillMaxWidth()) {
              Text("Developer diagnostics", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { dialog = "Log out" }, modifier = Modifier.fillMaxWidth()) {
              Text("Log out", color = MaterialTheme.colorScheme.error)
            }
            TextButton(onClick = { dialog = "Delete account" }, modifier = Modifier.fillMaxWidth()) {
              Text("Delete account", color = MaterialTheme.colorScheme.error)
            }
          }
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
            Text("Liquid Chat does not back up app data or E2EE private keys.")
            Text("Uninstalling clears this device's local cache and encryption identity; Firestore chat data is not automatically erased.")
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
            Text("Text messages keep device-bound E2EE. Direct Cloudinary media uses public secure URLs and is not end-to-end encrypted in the free backendless mode.")
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

          "Log out", "Delete account" -> {
            Text("Logging out permanently deletes your account, profile, chats on both sides, and uploaded media. It also erases this device’s keys, downloads and cached data. You cannot undo this or restore your old chats by signing in again.")
            Text("Keep the app installed until deletion is confirmed. Uninstalling alone cannot erase cloud data.")
            Text("Final verification waits 70 minutes after cloud cleanup for old sessions to expire, plus worker scheduling. Check again later if it is pending.")
            if (!googleProvider && !deletionPending) GlassTextField(
              deletionPassword, { deletionPassword = it }, placeholder = "Confirm password",
              visualTransformation = PasswordVisualTransformation()
            )
            deletionStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            deletionError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            GlassButton(
              text = if (deletionPending) "Check deletion" else if (googleProvider) "Verify with Google and delete" else "Delete all data and log out",
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
private fun SettingsStat(icon: ImageVector, value: String, label: String) {
  val config = LocalLiquidGlass.current
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Box(
      Modifier
        .size(38.dp)
        .clip(androidx.compose.foundation.shape.CircleShape)
        .background(config.accentColor.copy(alpha = .18f)),
      contentAlignment = Alignment.Center
    ) {
      Icon(icon, contentDescription = null, tint = config.accentColor, modifier = Modifier.size(20.dp))
    }
    Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun GlassVisualSettingRow(
  icon: ImageVector,
  title: String,
  onClick: () -> Unit
) {
  val config = LocalLiquidGlass.current
  GlassCard(
    modifier = Modifier.fillMaxWidth(),
    shape = androidx.compose.foundation.shape.RoundedCornerShape(27.dp),
    backgroundColor = if (config.isDark) Color(0xFF142A31).copy(alpha = .72f) else Color.White.copy(alpha = .56f),
    borderColor = Color.White.copy(alpha = if (config.isDark) .14f else .54f),
    elevation = 4.dp,
    onClick = onClick
  ) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        Modifier
          .size(48.dp)
          .clip(androidx.compose.foundation.shape.CircleShape)
          .background(Color.White.copy(alpha = if (config.isDark) .13f else .72f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(icon, contentDescription = null, tint = if (config.isDark) Color.White else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(23.dp))
      }
      Spacer(Modifier.width(14.dp))
      Text(
        title,
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.weight(1f)
      )
      Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
    }
  }
}

private fun conversationsCountForSettings(
  messages: Map<String, List<com.example.data.model.Message>>,
  users: List<com.example.data.model.User>
): Int {
  return maxOf(users.count { it.uid.isNotBlank() }, messages.keys.count()).coerceAtLeast(0)
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
    Text(
      title,
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 8.dp)
    )
    GlassCard(Modifier.fillMaxWidth()) {
      Column(Modifier.padding(vertical = 5.dp), content = content)
    }
  }
}

@Composable
private fun SettingRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 13.dp)
  ) {
    Text(title, style = MaterialTheme.typography.bodyLarge)
    if (!subtitle.isNullOrBlank()) {
      Spacer(Modifier.height(2.dp))
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
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
