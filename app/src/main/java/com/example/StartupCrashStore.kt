package com.example

import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter

/** Lightweight startup-crash recorder used before the full UI is available. */
object StartupCrashStore {
  private const val PREFS = "liquid-startup-diagnostics"
  private const val KEY_LAUNCH_AT = "launchAt"
  private const val KEY_STAGE = "stage"
  private const val KEY_CRASH_AT = "crashAt"
  private const val KEY_CRASH_STAGE = "crashStage"
  private const val KEY_MESSAGE = "message"
  private const val KEY_STACK = "stack"
  private const val STARTUP_WINDOW_MS = 30_000L
  private const val RETAIN_MS = 24 * 60 * 60 * 1000L

  @Volatile private var installed = false

  data class CrashInfo(
    val stage: String,
    val message: String,
    val stack: String,
    val at: Long
  )

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun install(context: Context) {
    if (installed) return
    synchronized(this) {
      if (installed) return
      installed = true
      val app = context.applicationContext
      val previous = Thread.getDefaultUncaughtExceptionHandler()
      Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        runCatching { recordIfStartup(app, throwable) }
        if (previous != null) previous.uncaughtException(thread, throwable)
        else kotlin.system.exitProcess(10)
      }
    }
  }

  fun beginLaunch(context: Context) {
    prefs(context).edit()
      .putLong(KEY_LAUNCH_AT, System.currentTimeMillis())
      .putString(KEY_STAGE, "application_onCreate")
      .apply()
  }

  fun markStage(context: Context, stage: String) {
    prefs(context).edit().putString(KEY_STAGE, stage).apply()
  }

  fun markHealthy(context: Context) {
    prefs(context).edit()
      .putString(KEY_STAGE, "healthy")
      .putLong(KEY_LAUNCH_AT, 0L)
      .remove(KEY_CRASH_AT)
      .remove(KEY_CRASH_STAGE)
      .remove(KEY_MESSAGE)
      .remove(KEY_STACK)
      .apply()
  }

  fun record(context: Context, stage: String, throwable: Throwable) {
    val writer = StringWriter()
    throwable.printStackTrace(PrintWriter(writer))
    prefs(context).edit()
      .putLong(KEY_CRASH_AT, System.currentTimeMillis())
      .putString(KEY_CRASH_STAGE, stage)
      .putString(KEY_MESSAGE, "${throwable.javaClass.simpleName}: ${throwable.message.orEmpty()}")
      .putString(KEY_STACK, writer.toString().take(12_000))
      .commit()
  }

  private fun recordIfStartup(context: Context, throwable: Throwable) {
    val p = prefs(context)
    val launchAt = p.getLong(KEY_LAUNCH_AT, 0L)
    val now = System.currentTimeMillis()
    if (launchAt <= 0L || now - launchAt > STARTUP_WINDOW_MS) return
    record(context, p.getString(KEY_STAGE, "startup").orEmpty(), throwable)
  }

  fun recentCrash(context: Context): CrashInfo? {
    val p = prefs(context)
    val at = p.getLong(KEY_CRASH_AT, 0L)
    if (at <= 0L || System.currentTimeMillis() - at > RETAIN_MS) return null
    return CrashInfo(
      stage = p.getString(KEY_CRASH_STAGE, "startup").orEmpty(),
      message = p.getString(KEY_MESSAGE, "Startup failed").orEmpty(),
      stack = p.getString(KEY_STACK, "").orEmpty(),
      at = at
    )
  }

  fun clearCrash(context: Context) {
    prefs(context).edit()
      .remove(KEY_CRASH_AT)
      .remove(KEY_CRASH_STAGE)
      .remove(KEY_MESSAGE)
      .remove(KEY_STACK)
      .apply()
  }
}
