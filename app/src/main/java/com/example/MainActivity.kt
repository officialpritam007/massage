package com.example

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Choreographer
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.example.data.network.LiquidApi
import com.example.ui.LiquidChatApp
import com.example.ui.viewmodel.LiquidChatViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
  private var notificationConversation by mutableStateOf<String?>(null)
  private var frameMonitor: DebugFrameMonitor? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    val splashScreen = installSplashScreen()
    super.onCreate(savedInstanceState)
    var themeReadyCheck: () -> Boolean = { false }
    splashScreen.setKeepOnScreenCondition { !themeReadyCheck() }
    LiquidApi.context = applicationContext
    StartupCrashStore.markStage(this, "activity_onCreate")
    enableEdgeToEdge()
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val preferredHz = preferHighestRefreshRate()
    if (BuildConfig.DEBUG) frameMonitor = DebugFrameMonitor(preferredHz)
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

  override fun onResume() {
    super.onResume()
    frameMonitor?.start()
  }

  override fun onPause() {
    frameMonitor?.stop()
    super.onPause()
  }

  private fun preferHighestRefreshRate(): Float {
    val activeDisplay = display ?: return 60f
    val current = activeDisplay.mode
    val best = activeDisplay.supportedModes
      .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
      .maxByOrNull { it.refreshRate }
      ?: current

    window.attributes = window.attributes.apply {
      preferredDisplayModeId = best.modeId
      preferredRefreshRate = best.refreshRate
    }
    return best.refreshRate
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    notificationConversation = intent.getStringExtra("conversation_id")
  }
}


private class DebugFrameMonitor(private val refreshRate: Float) : Choreographer.FrameCallback {
  private var running = false
  private var lastFrameNs = 0L
  private var frameCount = 0
  private var missedFrames = 0
  private var totalFrameMs = 0.0
  private var worstFrameMs = 0.0
  private val budgetMs = 1000.0 / refreshRate.coerceAtLeast(60f)
  private val reportEvery = (refreshRate.coerceAtLeast(60f) * 5f).toInt()

  fun start() {
    if (running) return
    running = true
    lastFrameNs = 0L
    Choreographer.getInstance().postFrameCallback(this)
  }

  fun stop() {
    running = false
    Choreographer.getInstance().removeFrameCallback(this)
    lastFrameNs = 0L
  }

  override fun doFrame(frameTimeNanos: Long) {
    if (!running) return
    if (lastFrameNs != 0L) {
      val frameMs = (frameTimeNanos - lastFrameNs) / 1_000_000.0
      frameCount++
      totalFrameMs += frameMs
      worstFrameMs = maxOf(worstFrameMs, frameMs)
      missedFrames += ((frameMs / budgetMs).toInt() - 1).coerceAtLeast(0)

      if (frameCount >= reportEvery) {
        Log.d(
          "LiquidPerf",
          "target=${"%.0f".format(refreshRate)}Hz avg=${"%.2f".format(totalFrameMs / frameCount)}ms " +
            "worst=${"%.2f".format(worstFrameMs)}ms missedVsync=$missedFrames"
        )
        frameCount = 0
        missedFrames = 0
        totalFrameMs = 0.0
        worstFrameMs = 0.0
      }
    }
    lastFrameNs = frameTimeNanos
    Choreographer.getInstance().postFrameCallback(this)
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
