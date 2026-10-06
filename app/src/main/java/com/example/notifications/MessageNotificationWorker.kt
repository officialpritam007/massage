package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.example.MainActivity
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class MessageNotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val account = inputData.getString("account") ?: return Result.success()
    val cid = inputData.getString("cid") ?: return Result.success()
    val id = inputData.getString("mid") ?: return Result.success()
    fun current() = !isStopped && com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid == account
    if (!current()) return Result.success()
    val db = FirebaseFirestore.getInstance()
    val prefs = applicationContext.getSharedPreferences("liquid-private", 0)
    val done = "notificationDone:$account:$cid:$id"
    // A message ID is immutable for notification purposes. Bail out before any
    // Firestore reads when FCM or WorkManager redelivers the same event.
    if (prefs.contains(done)) return Result.success()
    return try {
      // Read authenticated server truth: a deleted/hidden message must never be
      // redisplayed from stale cache or an untrusted plaintext push payload.
      val conversation = db.document("conversations/$cid").get(Source.SERVER).await()
      val message = db.document("conversations/$cid/messages/$id").get(Source.SERVER).await()
      val participants = conversation.get("participantIds") as? List<*> ?: return Result.success()
      if (!current() || account !in participants || !message.exists()) return Result.success()
      val sender = message.getString("senderId").orEmpty()
      if (sender == account || sender !in participants) return Result.success()
      if (message.get("deletedForEveryone") == true || message.get("isDeleted") == true ||
        (message.get("hiddenFor") as? List<*>)?.contains(account) == true ||
        (conversation.get("deletedFor") as? List<*>)?.contains(account) == true) return Result.success()
      fun millis(value: Any?): Long = when (value) {
        is Number -> value.toLong()
        is com.google.firebase.Timestamp -> value.toDate().time
        is String -> value.toLongOrNull() ?: 0L
        else -> 0L
      }
      val expires = millis(message.get("expiresAt"))
      if (expires > 0 && expires <= System.currentTimeMillis()) return Result.success()
      val cutoff = millis((conversation.get("deletedBefore") as? Map<*, *>)?.get(account))
      if (cutoff > 0 && millis(message.get("createdAt")) <= cutoff) return Result.success()
      DeliveryReceiptWorker.enqueue(applicationContext, cid, id)
      if (message.getString("status") == "READ" || (conversation.get("mutedFor") as? List<*>)?.contains(account) == true) return Result.success()
      val own = db.document("users/$account").get(Source.SERVER).await()
      if ((own.get("blockedUserIds") as? List<*>)?.contains(sender) == true) return Result.success()
      val settings = own.get("notifications") as? Map<*, *>
      if (settings?.get("messages") == false) return Result.success()
      val preview = settings?.get("showPreview") != false
      val peer = db.document("directory/$sender").get(Source.SERVER).await()
      val messageText = message.getString("text")?.take(8000)
      val revision = message.get("updatedAt")?.toString() ?: messageText.orEmpty()
      if (ChatNotificationState.shouldSuppressSystemNotification(sender, cid)) {
        ChatNotificationState.dispatchForegroundMessage(sender, cid, id)
        return Result.success()
      }
      if (prefs.getBoolean("appResumed", false) && prefs.getString("visibleConversation", null) == cid) return Result.success()
      if (!current()) return Result.success()
      if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
      val manager = applicationContext.getSystemService(NotificationManager::class.java)
      val vibrate = settings?.get("vibration") != false
      val channelId = if (Build.VERSION.SDK_INT >= 26) {
        val selected = ensureMessageNotificationChannel(manager, vibrate)
        // Respect an explicitly blocked legacy Messages channel when introducing
        // the no-vibration variant rather than bypassing the user's OS choice.
        if (manager.getNotificationChannel("messages")?.importance == NotificationManager.IMPORTANCE_NONE) {
          return Result.success()
        }
        selected
      } else "messages"
      val intent = Intent(applicationContext, MainActivity::class.java)
        .putExtra("conversation_id", cid)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
      val number = "$account:$cid:$id".hashCode()
      val pending = PendingIntent.getActivity(applicationContext, number, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
      val text = NotificationText.body(message.getString("type") ?: "TEXT", messageText, preview)
      val builder = NotificationCompat.Builder(applicationContext, channelId)
        .setSmallIcon(R.drawable.ic_stat_message)
        .setContentTitle(if (preview) peer.getString("displayName").orEmpty().ifBlank { "Liquid Chat" } else "Liquid Chat")
        .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
      if (Build.VERSION.SDK_INT < 26) {
        // Vibration and sound are independent preferences. setSilent(true)
        // suppresses both, so it cannot implement a vibration-only toggle.
        builder.setDefaults(NotificationCompat.DEFAULT_SOUND or if (vibrate) NotificationCompat.DEFAULT_VIBRATE else 0)
      }
      val notification = builder.build()
      if (current()) {
        NotificationManagerCompat.from(applicationContext).notify(number, notification)
        prefs.edit().putString(done, revision).commit()
      }
      Result.success()
    } catch (t: FirebaseFirestoreException) {
      if (t.code in setOf(FirebaseFirestoreException.Code.PERMISSION_DENIED, FirebaseFirestoreException.Code.UNAUTHENTICATED, FirebaseFirestoreException.Code.NOT_FOUND)) Result.success()
      else Result.retry()
    } catch (t: Exception) {
      if (t is kotlinx.coroutines.CancellationException) throw t
      if (runAttemptCount >= 4) Result.failure() else Result.retry()
    }
  }

  companion object {
    fun enqueue(context: Context, account: String, cid: String, id: String) {
      if (account.isBlank() || cid.isBlank() || id.isBlank() || '/' in cid || '/' in id) return
      val request = OneTimeWorkRequestBuilder<MessageNotificationWorker>()
        .setInputData(workDataOf("account" to account, "cid" to cid, "mid" to id))
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .addTag("account:$account").build()
      WorkManager.getInstance(context).enqueueUniqueWork("notify:$account:$cid:$id", ExistingWorkPolicy.KEEP, request)
    }
  }
}

/** Channel behavior is immutable after creation; Android settings remain authoritative. */
@androidx.annotation.RequiresApi(26)
internal fun ensureMessageNotificationChannel(manager: NotificationManager, vibrate: Boolean): String {
  val regular = manager.getNotificationChannel("messages") ?: NotificationChannel(
    "messages", "Messages", NotificationManager.IMPORTANCE_HIGH
  ).apply {
    enableVibration(true)
    setSound(
      RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
      AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    )
  }.also { manager.createNotificationChannel(it) }
  if (vibrate) return regular.id

  val id = "messages_no_vibration"
  if (manager.getNotificationChannel(id) == null) {
    manager.createNotificationChannel(NotificationChannel(id, "Messages without vibration", regular.importance).apply {
      description = "Messages when vibration is disabled in Liquid Chat"
      enableVibration(false)
      setSound(regular.sound, regular.audioAttributes)
      lockscreenVisibility = regular.lockscreenVisibility
      setShowBadge(regular.canShowBadge())
    })
  }
  return id
}
