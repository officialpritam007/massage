package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
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
import kotlinx.coroutines.tasks.await

private data class DiagnosticItem(
  val name: String,
  val ok: Boolean,
  val detail: String
)

@Composable
fun DiagnosticsScreen(
  viewModel: LiquidChatViewModel,
  onBackClick: () -> Unit
) {
  val clipboard = LocalClipboardManager.current
  val appError by viewModel.error.collectAsState()
  var running by remember { mutableStateOf(true) }
  var items by remember { mutableStateOf<List<DiagnosticItem>>(emptyList()) }
  var runKey by remember { mutableIntStateOf(0) }

  LaunchedEffect(runKey) {
    running = true
    val result = mutableListOf<DiagnosticItem>()
    val user = FirebaseAuth.getInstance().currentUser

    result += DiagnosticItem(
      "Firebase Auth",
      user != null,
      user?.uid?.let { "Signed in • " + it.take(8) + "…" } ?: "No signed-in Firebase user"
    )

    if (user != null) {
      val firestore = runCatching {
        FirebaseFirestore.getInstance().document("users/" + user.uid).get().await()
      }
      result += DiagnosticItem(
        "Cloud Firestore",
        firestore.isSuccess && firestore.getOrNull()?.exists() == true,
        firestore.exceptionOrNull()?.message
          ?: if (firestore.getOrNull()?.exists() == true) "Profile document readable" else "Profile document missing"
      )
    }

    val cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME.trim()
    val preset = BuildConfig.CLOUDINARY_UPLOAD_PRESET.trim()
    result += DiagnosticItem(
      "Cloudinary direct upload",
      cloudName.isNotBlank() && preset.isNotBlank(),
      if (cloudName.isNotBlank() && preset.isNotBlank()) {
        "Cloud " + cloudName + " • unsigned preset " + preset
      } else {
        "Cloudinary cloud name or unsigned upload preset is missing"
      }
    )

    result += DiagnosticItem(
      "Paid backend",
      true,
      "Not required • Firebase Auth, Firestore and direct Cloudinary"
    )

    val visibleAppError = appError?.takeUnless { message ->
      message.contains("Resource limit for the current billing cycle", ignoreCase = true)
    }
    result += DiagnosticItem(
      "Last app error",
      visibleAppError.isNullOrBlank(),
      visibleAppError ?: "No current core runtime error"
    )

    items = result
    running = false
  }

  LiquidBackground(crystal = true) {
    Column(
      Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      GlassHeader("Diagnostics", subtitle = "Firebase • Firestore • Cloudinary", onBackClick = onBackClick)

      if (running) {
        GlassCard(Modifier.fillMaxWidth()) {
          Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text("Checking services…")
          }
        }
      }

      items.forEach { item ->
        GlassCard(Modifier.fillMaxWidth()) {
          Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
              if (item.ok) Icons.Default.CheckCircle else Icons.Default.Error,
              null,
              tint = if (item.ok) Color(0xFF22B573) else MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
              Text(item.name, style = MaterialTheme.typography.titleSmall)
              Text(
                item.detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
              )
            }
          }
        }
      }

      GlassButton(
        text = "Run checks again",
        onClick = { runKey++ },
        modifier = Modifier.fillMaxWidth(),
        isLoading = running
      )

      GlassButton(
        text = "Copy diagnostics",
        onClick = {
          val report = buildString {
            appendLine("Liquid Chat " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")")
            items.forEach {
              appendLine((if (it.ok) "PASS" else "FAIL") + " | " + it.name + " | " + it.detail)
            }
          }
          clipboard.setText(AnnotatedString(report))
        },
        modifier = Modifier.fillMaxWidth(),
        isPrimary = false,
        enabled = items.isNotEmpty()
      )

      Text(
        "Diagnostics never displays Firebase credentials or Cloudinary API secrets.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
