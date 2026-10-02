package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.network.LiquidApi
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.io.File
import java.util.UUID

class LiquidFirebaseMessagingService : FirebaseMessagingService() {
  override fun onNewToken(token: String) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val prefs = getSharedPreferences("liquid-private", 0)
    val device = prefs.getString("deviceId", null)
      ?: UUID.randomUUID().toString().replace("-", "").also {
        prefs.edit().putString("deviceId", it).apply()
      }

    // set(merge) is intentional: token rotation can happen before the authenticated profile
    // bootstrap finishes on a fresh/legacy account. Firestore rules allow only the owner and
    // only the private client-owned settings fields, so this cannot create public identity data.
    FirebaseFirestore.getInstance().document("users/$uid").set(
      mapOf("tokens" to mapOf(device to token)),
      SetOptions.merge()
    )
  }

  override fun onMessageReceived(message: RemoteMessage) {
    if (FirebaseAuth.getInstance().currentUser == null) return
    val prefs = getSharedPreferences("liquid-private", 0)
    val id = message.data["messageId"] ?: message.messageId ?: return
    val conversationId = message.data["conversationId"].orEmpty()
    val dataType = message.data["type"].orEmpty()

    if (dataType == "message_deleted") {
      NotificationManagerCompat.from(this).cancel(id.hashCode())
      prefs.edit().remove("notified:$id").apply()

      // Remote permanent deletion must invalidate every local media surface. The exact private
      // media reference is deliberately not included in FCM, so clear resolved/download caches.
      runCatching { LiquidApi.clearMediaCachesOnly() }
      runCatching { File(cacheDir, "private-media").deleteRecursively() }
      coil.Coil.imageLoader(this).memoryCache?.clear()
      coil.Coil.imageLoader(this).diskCache?.clear()
      return
    }

    // Delivery is independent from notification visibility. Persist the acknowledgement before
    // checking notification settings/permission, then let WorkManager retry until the backend
    // accepts it. This never writes presence, so a background receive does not make the user Online.
    if (dataType == "message" && conversationId.isNotBlank()) {
      DeliveryReceiptWorker.enqueue(this, conversationId, id)
    }

    if (message.data["silent"] == "true" || !prefs.getBoolean("notifications", true)) return
    if (Build.VERSION.SDK_INT >= 33 &&
      ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return

    // FCM can redeliver the same data message after reconnect/process restart. Keep the local
    // notification idempotent so foreground/background transitions do not create duplicates.
    if (prefs.getBoolean("notified:$id", false)) return
    prefs.edit().putBoolean("notified:$id", true).apply()

    val vibrate = message.data["vibration"] != "false"
    val channel = if (vibrate) "messages_v6" else "messages_quiet_v6"
    if (Build.VERSION.SDK_INT >= 26) {
      getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(
          channel,
          if (vibrate) "Messages" else "Messages without vibration",
          NotificationManager.IMPORTANCE_HIGH
        ).apply { enableVibration(vibrate) }
      )
    }

    val intent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
      putExtra("conversation_id", conversationId)
      putExtra("message_id", id)
    }
    val pending = PendingIntent.getActivity(
      this,
      id.hashCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(this, channel)
      .setSmallIcon(R.drawable.ic_launcher_foreground)
      .setContentTitle(message.data["title"] ?: "Liquid Chat")
      .setContentText(message.data["body"] ?: "New message")
      .setContentIntent(pending)
      .setAutoCancel(true)
      .setGroup("liquid_chat_messages")
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .setOnlyAlertOnce(true)
      .build()

    val summary = NotificationCompat.Builder(this, channel)
      .setSmallIcon(R.drawable.ic_launcher_foreground)
      .setContentTitle("Liquid Chat")
      .setContentText("New messages")
      .setGroup("liquid_chat_messages")
      .setGroupSummary(true)
      .setOnlyAlertOnce(true)
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .build()

    NotificationManagerCompat.from(this).notify(id.hashCode(), notification)
    NotificationManagerCompat.from(this).notify(-7717, summary)
  }
}
