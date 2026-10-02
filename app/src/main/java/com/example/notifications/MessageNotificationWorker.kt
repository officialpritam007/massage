package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.example.MainActivity
import com.example.R
import com.example.data.crypto.E2eeCrypto
import com.example.data.local.SecureSnippetStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

class MessageNotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    val uid = inputData.getString("uid") ?: return@withContext Result.failure()
    val cid = inputData.getString("cid") ?: return@withContext Result.failure()
    val mid = inputData.getString("mid") ?: return@withContext Result.failure()
    val auth = FirebaseAuth.getInstance()
    if (auth.currentUser?.uid != uid) return@withContext Result.success()
    try {
      val db = FirebaseFirestore.getInstance()
      // Resolve identity and payload from authorized records, never an unverified push preview.
      val thread = db.document("conversations/$cid").get(Source.SERVER).await()
      val participants = thread.get("participantIds") as? List<*> ?: return@withContext Result.success()
      if (uid !in participants || (thread.get("deletedFor") as? List<*>)?.contains(uid) == true)
        return@withContext Result.success()
      val message = db.document("conversations/$cid/messages/$mid").get(Source.SERVER).await()
      if (!message.exists()) return@withContext Result.success()
      val sender = message.getString("senderId").orEmpty()
      if (sender == uid || sender !in participants || sender.isBlank()) return@withContext Result.success()
      val profile = db.document("users/$uid").get(Source.SERVER).await()
      if ((profile.get("blockedUserIds") as? List<*>)?.contains(sender) == true) return@withContext Result.success()
      if (message.getBoolean("deletedForEveryone") == true || message.getBoolean("isDeleted") == true ||
        (message.get("hiddenFor") as? List<*>)?.contains(uid) == true) return@withContext Result.success()
      fun millis(value: Any?): Long = when (value) {
        is com.google.firebase.Timestamp -> value.toDate().time
        is Number -> value.toLong()
        else -> 0L
      }
      val cutoff = millis((thread.get("deletedBefore") as? Map<*, *>)?.get(uid))
      val time = millis(message.get("serverCreatedAt")).takeIf { it > 0 } ?: millis(message.get("createdAt"))
      val expiry = millis(message.get("expiresAt"))
      if (time <= cutoff || (expiry > 0 && expiry <= System.currentTimeMillis())) return@withContext Result.success()
      val peer = db.document("directory/$sender").get(Source.SERVER).await()
      val expectedKey = peer.getString("e2eeKeyId").orEmpty()
      val fields = (message.get("e2ee") as? Map<*, *>)?.entries
        ?.filter { it.key is String }?.associate { it.key as String to it.value }
      val raw = if (fields != null && expectedKey.isNotBlank()) E2eeCrypto.decryptText(
        applicationContext, uid, sender, cid, mid, fields, expectedKey
      ) else null
      val text = raw?.let { runCatching { JSONObject(it).optString("text") }.getOrNull() }
        ?.takeIf { it.isNotBlank() }
        ?: if (fields == null) message.getString("text")?.takeIf { it.isNotBlank() } else null
      if (auth.currentUser?.uid != uid) return@withContext Result.success()
      DeliveryReceiptWorker.enqueue(applicationContext, cid, mid)
      if (!text.isNullOrBlank()) SecureSnippetStore.put(applicationContext, uid, cid, mid, text)
      val notifications = profile.get("notifications") as? Map<*, *>
      if (notifications?.get("messages") == false || (thread.get("mutedFor") as? List<*>)?.contains(uid) == true)
        return@withContext Result.success()
      val preview = notifications?.get("showPreview") != false
      postNotification(cid, mid, peer.getString("displayName").orEmpty().ifBlank { "New message" },
        if (preview) text?.take(500) ?: "New message" else "New message")
      Result.success()
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
  }

  private fun postNotification(
    conversationId: String,
    messageId: String,
    senderName: String,
    text: String
  ) {
    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return

    val manager = applicationContext.getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      manager.createNotificationChannel(
        NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
          description = "Private message notifications"
          enableVibration(true)
        }
      )
    }

    val intent = Intent(applicationContext, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
      putExtra("conversation_id", conversationId)
    }
    val pending = PendingIntent.getActivity(
      applicationContext,
      conversationId.hashCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(applicationContext, CHANNEL_MESSAGES)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle(senderName)
      .setContentText(text)
      .setStyle(NotificationCompat.BigTextStyle().bigText(text))
      .setContentIntent(pending)
      .setAutoCancel(true)
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .setCategory(NotificationCompat.CATEGORY_MESSAGE)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setGroup("conversation:$conversationId")
      .build()

    manager.notify(messageId.hashCode(), notification)
  }

  companion object {
    private const val CHANNEL_MESSAGES = "liquid_messages"
    fun enqueue(context: Context, uid: String, cid: String, mid: String) {
      val request = OneTimeWorkRequestBuilder<MessageNotificationWorker>()
        .setInputData(workDataOf("uid" to uid, "cid" to cid, "mid" to mid))
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .build()
      WorkManager.getInstance(context).enqueueUniqueWork("notification:$uid:$cid:$mid", ExistingWorkPolicy.KEEP, request)
    }
  }
}
