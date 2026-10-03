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
    val account = inputData.getString("account") ?: return Result.success()
    if (account != user.uid || isStopped) return Result.success()
    val conversationId = inputData.getString("cid") ?: return Result.success()
    val messageId = inputData.getString("mid") ?: return Result.success()
    if (conversationId.isBlank() || messageId.isBlank() || '/' in conversationId || '/' in messageId) return Result.success()

    val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val key = ackKey(account, conversationId, messageId)
    if (!prefs.contains(key)) return Result.success()

    val db = FirebaseFirestore.getInstance()
    return runCatching {
      val ref = db.document("conversations/$conversationId/messages/$messageId")
      db.runTransaction { tx ->
        val snapshot = tx.get(ref)
        if (!snapshot.exists()) return@runTransaction
        if (snapshot.getString("senderId") == user.uid) return@runTransaction
        val current = snapshot.getString("status") ?: "SENT"
        if (current == "SENT") tx.update(ref, "status", "DELIVERED")
      }.await()
    }.fold(
      onSuccess = {
        if (FirebaseAuth.getInstance().currentUser?.uid == account) prefs.edit().remove(key).apply()
        Result.success()
      },
      onFailure = { if (isStopped) Result.success() else Result.retry() }
    )
  }

  companion object {
    private const val PREFS = "liquid-private"
    private const val PREFIX = "deliveryAck:"
    private const val SEPARATOR = "|"

    internal fun ackKey(account: String, conversationId: String, messageId: String) =
      "$PREFIX$account:$conversationId$SEPARATOR$messageId"

    fun enqueue(context: Context, conversationId: String, messageId: String) {
      if (conversationId.isBlank() || messageId.isBlank()) return
      val account = FirebaseAuth.getInstance().currentUser?.uid ?: return

      val appContext = context.applicationContext
      val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      prefs.edit()
        .putLong(ackKey(account, conversationId, messageId), System.currentTimeMillis())
        .apply()

      val request = OneTimeWorkRequestBuilder<DeliveryReceiptWorker>()
        .setInputData(androidx.work.workDataOf(
          "account" to account,
          "cid" to conversationId,
          "mid" to messageId
        ))
        .setConstraints(
          Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        )
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
        .addTag("delivery-receipt:$messageId")
        .addTag("account:$account")
        .build()

      WorkManager.getInstance(appContext).enqueueUniqueWork(
        "delivery-receipt:$account:$conversationId:$messageId",
        ExistingWorkPolicy.KEEP,
        request
      )
    }
  }
}
