package com.example

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.FrameMetrics
import android.view.Window
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
import java.util.Locale

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
    preferHighestRefreshRate()
    if (BuildConfig.DEBUG) {
      frameMonitor = DebugFrameMonitor(window) { currentDisplay()?.refreshRate ?: 60f }
    }
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
    preferHighestRefreshRate()
    frameMonitor?.start()
  }

  override fun onPause() {
    frameMonitor?.stop()
    super.onPause()
  }

  @Suppress("DEPRECATION")
  private fun currentDisplay(): Display? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display ?: windowManager.defaultDisplay
    else windowManager.defaultDisplay

  private fun preferHighestRefreshRate() {
    val activeDisplay = currentDisplay() ?: return
    val current = activeDisplay.mode
    val best = activeDisplay.supportedModes
      .filter {
        it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight &&
          it.refreshRate.isFinite() && it.refreshRate > 0f
      }
      .maxByOrNull { it.refreshRate }
      ?: current

    window.attributes = window.attributes.apply {
      // Android 14+ supports a seamless refresh-rate hint without pinning the
      // display mode. Older versions use a mode with the current resolution.
      // These are preferences: power saving and thermal limits remain in charge.
      preferredDisplayModeId = if (Build.VERSION.SDK_INT >= 34) 0 else best.modeId
      preferredRefreshRate = best.refreshRate
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    notificationConversation = intent.getStringExtra("conversation_id")
  }
}


/** Window render measurements, not the interval between artificially requested vsync callbacks. */
private class DebugFrameMonitor(
  private val window: Window,
  private val activeRefreshRate: () -> Float
) {
  @Volatile private var listener: Window.OnFrameMetricsAvailableListener? = null
  private var worker: HandlerThread? = null

  fun start() {
    if (listener != null) return
    val thread = HandlerThread("LiquidFrameMetrics").apply { start() }
    worker = thread
    var reportStartedAt = SystemClock.elapsedRealtime()
    val durations = ArrayList<Long>()
    var missedDeadlines = 0
    var estimatedOverBudget = 0
    var droppedReports = 0
    lateinit var sessionListener: Window.OnFrameMetricsAvailableListener
    sessionListener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
      if (listener !== sessionListener) return@OnFrameMetricsAvailableListener
      droppedReports += dropped
      // The platform reuses metrics; consume primitive values in this callback.
      // First draws are startup/layout costs, not animation-jank samples.
      val totalNs = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
      if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L || totalNs <= 0L) {
        return@OnFrameMetricsAvailableListener
      }
      durations.add(totalNs)
      val hz = activeRefreshRate().takeIf { it.isFinite() && it > 0f } ?: 60f
      val deadlineNs = if (Build.VERSION.SDK_INT >= 31) metrics.getMetric(FrameMetrics.DEADLINE) else -1L
      if (deadlineNs > 0L) {
        if (totalNs >= deadlineNs) missedDeadlines++
      } else if (totalNs > 1_000_000_000.0 / hz) {
        estimatedOverBudget++
      }
      val now = SystemClock.elapsedRealtime()
      if (now - reportStartedAt >= 5_000L) {
        val sorted = durations.sorted()
        val p95Ns = sorted[((sorted.size - 1) * .95).toInt()]
        Log.d(
          "LiquidPerf",
          String.format(
            Locale.US,
            "windowFrames=%d display=%.1fHz renderAvg=%.2fms p95=%.2fms worst=%.2fms " +
              "deadlineMisses=%d estimatedOverBudget=%d droppedMetricReports=%d",
            durations.size, hz, durations.average() / 1_000_000.0,
            p95Ns / 1_000_000.0, sorted.last() / 1_000_000.0,
            missedDeadlines, estimatedOverBudget, droppedReports
          )
        )
        durations.clear()
        missedDeadlines = 0
        estimatedOverBudget = 0
        droppedReports = 0
        reportStartedAt = now
      }
    }
    listener = sessionListener
    window.addOnFrameMetricsAvailableListener(sessionListener, Handler(thread.looper))
  }

  fun stop() {
    val registered = listener ?: return
    listener = null
    window.removeOnFrameMetricsAvailableListener(registered)
    worker?.quitSafely()
    worker = null
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
