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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class LiquidFirebaseMessagingService : FirebaseMessagingService() {
  override fun onNewToken(token: String) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val prefs = getSharedPreferences("liquid-private", 0)
    val device = prefs.getString("deviceId", null) ?: return
    FirebaseFirestore.getInstance().document("users/$uid").update("tokens.$device", token)
  }

  override fun onMessageReceived(message: RemoteMessage) {
    if (FirebaseAuth.getInstance().currentUser == null) return
    val prefs = getSharedPreferences("liquid-private", 0)
    val id = message.data["messageId"] ?: message.messageId ?: return

    if (message.data["type"] == "message_deleted") {
      NotificationManagerCompat.from(this).cancel(id.hashCode())
      prefs.edit().remove("notified:$id").apply()
      return
    }

    if (!prefs.getBoolean("notifications", true)) return
    if (Build.VERSION.SDK_INT >= 33 &&
      ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return

    if (prefs.getBoolean("notified:$id", false)) return
    prefs.edit().putBoolean("notified:$id", true).apply()

    val vibrate = message.data["vibration"] != "false"
    val channel = if (vibrate) "messages_v5" else "messages_quiet_v5"
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
      putExtra("conversation_id", message.data["conversationId"])
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
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .build()

    NotificationManagerCompat.from(this).notify(id.hashCode(), notification)
  }
}
