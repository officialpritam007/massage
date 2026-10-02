package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
  onLogout: () -> Unit
) {
  val upload by viewModel.upload.collectAsState()
  val me by viewModel.currentUser.collectAsState()
  val privacy by viewModel.privacy.collectAsState()
  val notifications by viewModel.notifications.collectAsState()
  val blocked by viewModel.blockedUserIds.collectAsState()
  val users by viewModel.users.collectAsState()
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
  var cacheSize by remember { mutableLongStateOf(0L) }
  var photoDeleting by remember { mutableStateOf(false) }
  var photoDeleteFailed by remember { mutableStateOf(false) }
  var locallyRemovedPhoto by remember { mutableStateOf(false) }
  var pendingPhotoUri by remember { mutableStateOf<android.net.Uri?>(null) }
  var showOwnPhoto by remember { mutableStateOf(false) }

  LaunchedEffect(me.photoUrl) {
    if (me.photoUrl.isNotBlank() && !photoDeleting) locallyRemovedPhoto = false
  }

  val shownPhoto = if (locallyRemovedPhoto) "" else me.photoUrl

  val photo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    if (uri != null) pendingPhotoUri = uri
  }

  var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
  val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
    if (saved) pendingPhotoUri = cameraUri?.let(android.net.Uri::parse)
  }
  fun openPhotoCamera() {
    val dir = java.io.File(context.cacheDir, "profile-camera").apply { mkdirs() }
    val file = java.io.File.createTempFile("photo-", ".jpg", dir)
    val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", file)
    cameraUri = uri.toString()
    runCatching { camera.launch(uri) }.onFailure { android.widget.Toast.makeText(context, "Camera unavailable", android.widget.Toast.LENGTH_SHORT).show() }
  }
  val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    if (granted) openPhotoCamera()
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
        GlassBottomBar(
          selectedRoute = "settings",
          onNavigateToChats = { onNavigateToHomeTab("All") },
          onNavigateToFavorites = { onNavigateToHomeTab("Favorites") },
          onNavigateToArchived = { onNavigateToHomeTab("Archived") },
          onNavigateToSettings = {}
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

        GlassCard(Modifier.fillMaxWidth()) {
          Row(
            Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            DustDeleteContainerV2(active = photoDeleting, reduced = glass.isReducedMotion) {
              GlassAvatar(
                shownPhoto,
                me.displayName,
                66.dp,
                onClick = { if (!photoDeleting) showOwnPhoto = true }
              )
            }
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f)) {
              Text(me.displayName.ifBlank { "Your profile" }, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
              Text(
                me.username.takeIf { it.isNotBlank() }?.let { "@$it" } ?: "Set your username",
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { dialog = "Profile" }, contentPadding = PaddingValues(0.dp)) {
                  Text("Edit profile")
                }
                Spacer(Modifier.width(12.dp))
                TextButton(onClick = { dialog = "Profile photo" }, enabled = !photoDeleting) {
                  Text(if (shownPhoto.isBlank()) "Add photo" else "Change photo")
                }
              }
              if (shownPhoto.isNotBlank()) {
                TextButton(onClick = ::removePhoto, enabled = !photoDeleting, contentPadding = PaddingValues(0.dp)) {
                  Text("Remove photo", color = MaterialTheme.colorScheme.error)
                }
              }
              if (photoDeleteFailed) {
                TextButton(onClick = ::removePhoto, contentPadding = PaddingValues(0.dp)) {
                  Text("Photo delete failed • Retry", color = MaterialTheme.colorScheme.error)
                }
              }
            }
          }
        }

        SettingsSection("Personalize") {
          SettingRow("Appearance & Liquid Glass", "Theme, blur, tint, motion", onNavigateToAppearance)
          SettingRow("Privacy", "Last seen, online, photo, receipts") { dialog = "Privacy" }
          SettingRow("Notifications", "Messages, vibration and previews") { dialog = "Notifications" }
        }

        SettingsSection("Data & tools") {
          SettingRow("Data & Storage", "Cache and attachment limits") {
            scope.launch {
              cacheSize = withContext(Dispatchers.IO) {
                context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
              }
              dialog = "Data & Storage"
            }
          }
          SettingRow("Blocked contacts", "Manage people you blocked") { dialog = "Blocked contacts" }
          SettingRow("Developer Diagnostics", "Firebase, Appwrite, FCM and backend status", onNavigateToDiagnostics)
        }

        SettingsSection("Account & support") {
          SettingRow("Verify email", "Send a fresh verification email") { viewModel.repository.verifyEmail() }
          SettingRow("About & Support", "Version, developer and contact") { dialog = "About & Support" }
        }

        GlassCard(Modifier.fillMaxWidth()) {
          Column(Modifier.padding(8.dp)) {
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
      GlassDialog(dialog, onDismiss = { if (!busy) dialog = "" }) {
        when (dialog) {
          "Profile photo" -> {
            GlassButton("Gallery", { dialog = ""; photo.launch("image/*") }, Modifier.fillMaxWidth())
            GlassButton("Camera", {
              dialog = ""
              if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) openPhotoCamera()
              else cameraPermission.launch(android.Manifest.permission.CAMERA)
            }, Modifier.fillMaxWidth(), isPrimary = false)
          }
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
            Text("Private attachments are stored in Appwrite. Maximum attachment size: 25 MB.")
            Text("Clearing cache never restores messages or chats deleted from your account.")
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
            Text("Liquid Chat 4.0.0")
            Text("Developed by Pritam Pal")
            Text("© 2026 Pritam Pal")
            Text("Firebase authentication + Firestore realtime data + Appwrite private media.")
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
            Text("Pending unsent messages on this device will be removed. Send or retry them before logging out.")
            GlassButton(
              "Log out",
              {
                viewModel.logout()
                onLogout()
                dialog = ""
              },
              modifier = Modifier.fillMaxWidth()
            )
          }

          "Delete account" -> {
            Text("This permanently deletes your account, uploaded files and conversations. Sign in again first if the backend requests recent authentication.")
            GlassButton(
              text = "Permanently delete",
              onClick = {
                busy = true
                viewModel.deleteAccount { ok, _ ->
                  busy = false
                  if (ok) {
                    dialog = ""
                    onLogout()
                  }
                }
              },
              modifier = Modifier.fillMaxWidth(),
              isLoading = busy
            )
          }
        }
      }
    }
  }
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
