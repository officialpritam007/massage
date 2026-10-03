package com.example.data.local

import android.app.NotificationManager
import android.content.Context
import androidx.work.WorkManager
import com.example.data.crypto.E2eeCrypto
import com.example.data.network.LiquidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.tasks.await
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.installations.FirebaseInstallations

/** Called only after a trusted worker confirms permanent remote deletion. */
object AccountDataWiper {
  suspend fun wipe(context: Context, account: String) = withContext(Dispatchers.IO) {
    val deletion = context.getSharedPreferences("liquid-deletion", 0)
    check(deletion.getString("account", null) == account && deletion.getBoolean("remoteVerified", false)) { "Cloud deletion must be verified before erasing this device" }
    WorkManager.getInstance(context).cancelAllWork().result.get()
    WorkManager.getInstance(context).pruneWork().result.get()
    context.getSystemService(NotificationManager::class.java)?.cancelAll()
    LiquidApi.clear()
    withTimeout(20_000L) { FirebaseMessaging.getInstance().deleteToken().await() }
    withTimeout(20_000L) { FirebaseInstallations.getInstance().delete().await() }
    SecureMessageCache.clearDeviceCache(context)
    E2eeCrypto.clearDeviceIdentities(context)
    LiquidChatDatabase.clearForLogout(context)
    for (store in listOf("liquid-private", "liquid-install", "liquid-cloudinary-assets", "liquid-startup-diagnostics")) {
      check(context.getSharedPreferences(store, 0).edit().clear().commit()) { "Local cleanup could not be saved" }
    }
    for (directory in listOf(context.cacheDir, context.filesDir, context.noBackupFilesDir)) {
      directory.listFiles()?.forEach { file ->
        check(file.deleteRecursively()) { "Local file cleanup failed" }
      }
    }
    // Firestore closes/clears its own database before this runs. WorkManager keeps
    // its SDK database open; cancel/prune removes account jobs without corrupting it.
    (context.externalCacheDirs + context.getExternalFilesDirs(null)).filterNotNull().forEach { dir ->
      dir.listFiles()?.forEach { check(it.deleteRecursively()) { "External app file cleanup failed" } }
    }
    // Keep the verified-deletion marker until every local step succeeds. It lets
    // the next launch resume a wipe after process death or a file-system error.
    check(context.getSharedPreferences("liquid-deletion", 0).edit().clear().commit()) { "Unable to finish local cleanup" }
  }
}
