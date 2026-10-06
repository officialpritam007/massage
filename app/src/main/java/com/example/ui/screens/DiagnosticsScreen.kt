package com.example.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.ui.components.*
import com.example.ui.viewmodel.LiquidChatViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

private enum class DiagnosticStatus { PASS, FAIL, INFO, UNVERIFIED }

private data class DiagnosticItem(
  val name: String,
  val status: DiagnosticStatus,
  val detail: String
)

/** Bound remote probes while preserving lifecycle cancellation when the screen closes. */
internal suspend fun <T> boundedDiagnosticCheck(
  timeoutMs: Long = 10_000L,
  block: suspend () -> T
): Result<T> = try {
  withTimeoutOrNull(timeoutMs) { Result.success(block()) }
    ?: Result.failure(IllegalStateException("Server check timed out after ${timeoutMs / 1000} seconds"))
} catch (cancelled: CancellationException) {
  throw cancelled
} catch (failure: Exception) {
  Result.failure(failure)
}

@Composable
fun DiagnosticsScreen(
  viewModel: LiquidChatViewModel,
  onBackClick: () -> Unit
) {
  val clipboard = LocalClipboardManager.current
  val appError by viewModel.error.collectAsStateWithLifecycle()
  var running by remember { mutableStateOf(true) }
  var items by remember { mutableStateOf<List<DiagnosticItem>>(emptyList()) }
  var runKey by remember { mutableIntStateOf(0) }

  LaunchedEffect(runKey) {
    running = true
    val result = mutableListOf<DiagnosticItem>()
    val user = FirebaseAuth.getInstance().currentUser
    result += DiagnosticItem(
      "Firebase Auth",
      if (user != null) DiagnosticStatus.PASS else DiagnosticStatus.FAIL,
      user?.uid?.let { "Signed in on this device • " + it.take(8) + "…" } ?: "No signed-in Firebase user"
    )

    if (user != null) {
      val (profile, cleanup) = coroutineScope {
        val db = FirebaseFirestore.getInstance()
        val profileCheck = async {
          boundedDiagnosticCheck { db.document("users/" + user.uid).get(Source.SERVER).await() }
        }
        val cleanupCheck = async {
          boundedDiagnosticCheck { db.document("runtime/cleanup").get(Source.SERVER).await() }
        }
        profileCheck.await() to cleanupCheck.await()
      }
      val snapshot = profile.getOrNull()
      val serverReadable = snapshot?.exists() == true && !snapshot.metadata.isFromCache
      result += DiagnosticItem(
        "Cloud Firestore server",
        if (serverReadable) DiagnosticStatus.PASS else DiagnosticStatus.FAIL,
        profile.exceptionOrNull()?.message ?: when {
          snapshot?.metadata?.isFromCache == true -> "Only cached data returned; server availability is unverified"
          serverReadable && snapshot?.metadata?.hasPendingWrites() == true -> "Server reachable; profile has pending local writes"
          serverReadable -> "Profile read verified with the server"
          else -> "Server reached, but the profile document is missing"
        }
      )

      val runtime = cleanup.getOrNull()
      val updatedAt = when (val value = runtime?.get("updatedAt")) {
        is Number -> value.toLong()
        is com.google.firebase.Timestamp -> value.toDate().time
        else -> 0L
      }
      val cleanupReady = runtime?.get("enabled") == true &&
        System.currentTimeMillis() - updatedAt in 0L..3_600_000L
      result += DiagnosticItem(
        "Permanent cloud cleanup",
        if (cleanupReady) DiagnosticStatus.PASS else DiagnosticStatus.UNVERIFIED,
        if (cleanupReady) "Cleaner reports a recent server heartbeat; deletion still requires a completion receipt"
        else cleanup.exceptionOrNull()?.message ?: "No recent cleaner heartbeat. Permanent cloud deletion cannot be confirmed."
      )
    } else {
      result += DiagnosticItem("Cloud Firestore server", DiagnosticStatus.UNVERIFIED, "Sign in to verify authenticated server access")
      result += DiagnosticItem("Permanent cloud cleanup", DiagnosticStatus.UNVERIFIED, "Sign in to check the cleaner heartbeat")
    }

    val cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME.trim()
    val preset = BuildConfig.CLOUDINARY_UPLOAD_PRESET.trim()
    val mediaConfigured = cloudName.isNotBlank() && preset.isNotBlank()
    result += DiagnosticItem(
      "Cloudinary upload configuration",
      if (mediaConfigured) DiagnosticStatus.UNVERIFIED else DiagnosticStatus.FAIL,
      if (mediaConfigured) "Cloud $cloudName • preset $preset configured. Upload acceptance has not been tested by this check."
      else "Cloudinary cloud name or upload preset is missing"
    )
    result += DiagnosticItem(
      "Notifications while the app is closed",
      DiagnosticStatus.UNVERIFIED,
      "The receiver is available; an always-on trusted sender and delivery on a real device have not been verified."
    )
    result += DiagnosticItem(
      "Media privacy",
      DiagnosticStatus.INFO,
      "Uploaded media uses public Cloudinary URLs. Anyone with a URL can open it; messages are not end-to-end encrypted."
    )
    result += DiagnosticItem(
      "Service architecture",
      DiagnosticStatus.INFO,
      "Firebase Auth, Firestore and direct Cloudinary uploads. Provider usage quotas still apply."
    )
    result += DiagnosticItem(
      "Last app error",
      if (appError.isNullOrBlank()) DiagnosticStatus.PASS else DiagnosticStatus.FAIL,
      appError ?: "No current runtime error"
    )
    items = result
    running = false
  }

  LiquidBackground(crystal = true) {
    Column(
      Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
        .verticalScroll(rememberScrollState()).padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      GlassHeader("Diagnostics", subtitle = "Firebase • Firestore • Cloudinary", onBackClick = onBackClick)
      if (running) {
        GlassCard(Modifier.fillMaxWidth()) {
          Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text("Checking the server…")
          }
        }
      }
      items.forEach { item ->
        GlassCard(Modifier.fillMaxWidth()) {
          Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val icon = when (item.status) {
              DiagnosticStatus.PASS -> Icons.Default.CheckCircle
              DiagnosticStatus.FAIL -> Icons.Default.Error
              else -> Icons.Default.Info
            }
            val tint = when (item.status) {
              DiagnosticStatus.PASS -> Color(0xFF22B573)
              DiagnosticStatus.FAIL -> MaterialTheme.colorScheme.error
              else -> MaterialTheme.colorScheme.secondary
            }
            Icon(icon, contentDescription = item.status.name, tint = tint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
              Text(item.name, style = MaterialTheme.typography.titleSmall)
              Text(item.detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
          }
        }
      }
      GlassButton(
        text = "Run checks again", onClick = { runKey++ }, modifier = Modifier.fillMaxWidth(), isLoading = running
      )
      GlassButton(
        text = "Copy diagnostics",
        onClick = {
          val report = buildString {
            appendLine("Liquid Chat " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")")
            items.forEach { appendLine(it.status.name + " | " + it.name + " | " + it.detail) }
          }
          clipboard.setText(AnnotatedString(report))
        },
        modifier = Modifier.fillMaxWidth(), isPrimary = false, enabled = items.isNotEmpty()
      )
      Text(
        "Server checks have a 10-second timeout. Upload and notification delivery require a real-device test.",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
