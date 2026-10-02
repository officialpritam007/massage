package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.crypto.E2eeCrypto
import com.example.data.local.SecureSnippetStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject

/**
 * Receives data-only FCM messages. If an E2EE envelope is included, the payload is decrypted
 * locally before a notification/snippet is rendered. The plaintext never goes back to Firebase.
 */
class LiquidFirebaseMessagingService : FirebaseMessagingService() {

  override fun onNewToken(token: String) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    if (token.isBlank()) return
    FirebaseFirestore.getInstance().document("users/$uid")
      .update("tokens", FieldValue.arrayUnion(token))
  }

  override fun onMessageReceived(remoteMessage: RemoteMessage) {
    val data = remoteMessage.data
    val current = FirebaseAuth.getInstance().currentUser ?: return
    val conversationId = data["conversation_id"].orEmpty()
    val messageId = data["message_id"].orEmpty()
    val senderId = data["sender_id"].orEmpty()
    if (conversationId.isBlank() || messageId.isBlank() || senderId == current.uid) return

    DeliveryReceiptWorker.enqueue(applicationContext, conversationId, messageId)

    val text = decryptPreview(
      uid = current.uid,
      senderId = senderId,
      conversationId = conversationId,
      messageId = messageId,
      data = data
    ) ?: data["preview"]?.takeIf { it.isNotBlank() }

    if (!text.isNullOrBlank()) {
      SecureSnippetStore.put(applicationContext, current.uid, conversationId, messageId, text)
    }

    val prefs = getSharedPreferences("liquid-private", MODE_PRIVATE)
    if (!prefs.getBoolean("notifications", true)) return
    val showPreview = prefs.getBoolean("notificationPreview", true)
    postNotification(
      conversationId = conversationId,
      messageId = messageId,
      senderName = data["sender_name"].orEmpty().ifBlank { "New message" },
      text = if (showPreview) text.orEmpty().ifBlank { "New message" } else "New message"
    )
  }

  private fun decryptPreview(
    uid: String,
    senderId: String,
    conversationId: String,
    messageId: String,
    data: Map<String, String>
  ): String? {
    val fields = e2eeFields(data) ?: return null
    val raw = E2eeCrypto.decryptText(
      context = applicationContext,
      uid = uid,
      senderId = senderId,
      conversationId = conversationId,
      messageId = messageId,
      fields = fields,
      expectedSenderKeyId = fields["e2eeSenderKeyId"] as? String
    ) ?: return null
    return runCatching { JSONObject(raw).optString("text").takeIf { it.isNotBlank() } }.getOrNull()
  }

  private fun e2eeFields(data: Map<String, String>): Map<String, Any?>? {
    data["e2ee"]?.takeIf { it.isNotBlank() }?.let { raw ->
      return runCatching {
        val json = JSONObject(raw)
        buildMap<String, Any?> {
          json.keys().forEach { key -> put(key, json.get(key)) }
        }
      }.getOrNull()
    }

    val fields = data
      .filterKeys { it.startsWith("e2ee") }
      .mapValues { (key, value) ->
        if (key == "e2eeVersion") value.toIntOrNull() ?: value else value
      }
    return fields.takeIf { it.containsKey("e2eeCiphertext") }
  }

  private fun postNotification(
    conversationId: String,
    messageId: String,
    senderName: String,
    text: String
  ) {
    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return

    val manager = getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      manager.createNotificationChannel(
        NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
          description = "Private message notifications"
          enableVibration(true)
        }
      )
    }

    val intent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
      putExtra("conversation_id", conversationId)
    }
    val pending = PendingIntent.getActivity(
      this,
      conversationId.hashCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(this, CHANNEL_MESSAGES)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle(senderName)
      .setContentText(text)
      .setStyle(NotificationCompat.BigTextStyle().bigText(text))
      .setContentIntent(pending)
      .setAutoCancel(true)
      .setCategory(NotificationCompat.CATEGORY_MESSAGE)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setGroup("conversation:$conversationId")
      .build()

    manager.notify(messageId.hashCode(), notification)
  }

  companion object {
    private const val CHANNEL_MESSAGES = "liquid_messages"
  }
}
