package com.example.notifications

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Persists delivery acknowledgements outside the UI process lifecycle.
 * Receiving an FCM data message can therefore promote the sender's message to DELIVERED even
 * when MainActivity has never been opened, without touching online/last-seen presence.
 */
class DeliveryReceiptWorker(
  appContext: Context,
  params: WorkerParameters
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    val user = FirebaseAuth.getInstance().currentUser ?: return Result.success()
    val db = FirebaseFirestore.getInstance()

    val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val keys = prefs.all.keys.filter { it.startsWith(PREFIX) }
    if (keys.isEmpty()) return Result.success()

    var shouldRetry = false
    for (key in keys) {
      val payload = key.removePrefix(PREFIX).split(SEPARATOR, limit = 2)
      if (payload.size != 2 || payload[0].isBlank() || payload[1].isBlank()) {
        prefs.edit().remove(key).apply()
        continue
      }

      val conversationId = payload[0]
      val messageId = payload[1]
      runCatching {
        val ref = db.document("conversations/$conversationId/messages/$messageId")
        db.runTransaction { tx ->
          val snapshot = tx.get(ref)
          if (!snapshot.exists()) return@runTransaction
          if (snapshot.getString("senderId") == user.uid) return@runTransaction
          val current = snapshot.getString("status") ?: "SENT"
          if (current == "SENT") tx.update(ref, "status", "DELIVERED")
        }.await()
      }.onSuccess {
        prefs.edit().remove(key).apply()
      }.onFailure {
        shouldRetry = true
      }
    }

    val stillPending = prefs.all.keys.any { it.startsWith(PREFIX) }
    return if (shouldRetry && stillPending) Result.retry() else Result.success()
  }

  companion object {
    private const val PREFS = "liquid-private"
    private const val PREFIX = "deliveryAck:"
    private const val SEPARATOR = "|"

    fun enqueue(context: Context, conversationId: String, messageId: String) {
      if (conversationId.isBlank() || messageId.isBlank()) return

      val appContext = context.applicationContext
      val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      prefs.edit()
        .putLong("$PREFIX$conversationId$SEPARATOR$messageId", System.currentTimeMillis())
        .apply()

      val request = OneTimeWorkRequestBuilder<DeliveryReceiptWorker>()
        .setConstraints(
          Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        )
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
        .addTag("delivery-receipt:$messageId")
        .build()

      WorkManager.getInstance(appContext).enqueueUniqueWork(
        "delivery-receipt:$conversationId:$messageId",
        ExistingWorkPolicy.KEEP,
        request
      )
    }
  }
}
