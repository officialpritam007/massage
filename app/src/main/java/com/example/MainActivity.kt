package com.example

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.example.data.network.LiquidApi
import com.example.ui.LiquidChatApp
import com.example.ui.viewmodel.LiquidChatViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
  private var notificationConversation by mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    val splashScreen = installSplashScreen()
    super.onCreate(savedInstanceState)
    var themeReadyCheck: () -> Boolean = { false }
    splashScreen.setKeepOnScreenCondition { !themeReadyCheck() }
    LiquidApi.context = applicationContext
    StartupCrashStore.markStage(this, "activity_onCreate")
    enableEdgeToEdge()
    WindowCompat.setDecorFitsSystemWindows(window, false)
    notificationConversation = intent.getStringExtra("conversation_id")

    val existingCrash = StartupCrashStore.recentCrash(this)
    val viewModelResult: Result<LiquidChatViewModel>? = if (existingCrash == null) {
      StartupCrashStore.markStage(this, "viewmodel_create")
      runCatching { ViewModelProvider(this)[LiquidChatViewModel::class.java] }
        .onFailure { StartupCrashStore.record(this, "viewmodel_create", it) }
    } else null

    val startupCrash = existingCrash ?: viewModelResult?.exceptionOrNull()?.let {
      StartupCrashStore.recentCrash(this)
    }
    themeReadyCheck = viewModelResult?.getOrNull()?.let { viewModel ->
      { viewModel.themeReady.value }
    } ?: { true }

    if (startupCrash == null && Build.VERSION.SDK_INT >= 33 &&
      ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
      ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2001)
    }

    setContent {
      when {
        startupCrash != null -> StartupRecoveryScreen(
          crash = startupCrash,
          onRetry = {
            StartupCrashStore.clearCrash(this)
            recreate()
          },
          onCopy = { text ->
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Liquid Chat startup diagnostics", text))
          }
        )

        viewModelResult?.isSuccess == true -> {
          val chatViewModel = viewModelResult.getOrThrow()
          remember {
            StartupCrashStore.markStage(this@MainActivity, "compose_start")
            true
          }
          LaunchedEffect(Unit) {
            delay(5_000)
            StartupCrashStore.markHealthy(this@MainActivity)
          }
          LiquidChatApp(
            notificationConversation = notificationConversation,
            onNotificationHandled = { notificationConversation = null },
            chatViewModel = chatViewModel
          )
        }

        else -> StartupRecoveryScreen(
          crash = StartupCrashStore.CrashInfo(
            stage = "startup",
            message = "Liquid Chat could not initialize.",
            stack = "No startup exception was captured.",
            at = System.currentTimeMillis()
          ),
          onRetry = {
            StartupCrashStore.clearCrash(this)
            recreate()
          },
          onCopy = { text ->
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Liquid Chat startup diagnostics", text))
          }
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    notificationConversation = intent.getStringExtra("conversation_id")
  }
}

@Composable
private fun StartupRecoveryScreen(
  crash: StartupCrashStore.CrashInfo,
  onRetry: () -> Unit,
  onCopy: (String) -> Unit
) {
  val diagnostics = remember(crash) {
    buildString {
      appendLine("Liquid Chat startup recovery")
      appendLine("Stage: ${crash.stage}")
      appendLine("Error: ${crash.message}")
      appendLine()
      append(crash.stack)
    }
  }

  MaterialTheme {
    Surface(Modifier.fillMaxSize()) {
      Column(
        Modifier
          .fillMaxSize()
          .statusBarsPadding()
          .navigationBarsPadding()
          .verticalScroll(rememberScrollState())
          .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Text("Liquid Chat recovery", style = MaterialTheme.typography.headlineMedium)
        Text(
          "The previous startup failed. The app stayed open in recovery mode so the error can be diagnosed instead of repeatedly crashing."
        )
        Text("Stage: ${crash.stage}", style = MaterialTheme.typography.titleMedium)
        Text(crash.message, color = MaterialTheme.colorScheme.error)
        HorizontalDivider()
        Text(
          crash.stack.ifBlank { "No stack trace captured." },
          fontFamily = FontFamily.Monospace,
          style = MaterialTheme.typography.bodySmall
        )
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
          Text("Try normal startup")
        }
        Text("Permanent account deletion is available in Settings after startup succeeds.")
        TextButton(onClick = { onCopy(diagnostics) }, modifier = Modifier.fillMaxWidth()) {
          Text("Copy diagnostics")
        }
      }
    }
  }
}
