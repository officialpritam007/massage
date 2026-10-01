package com.example.notifications

import android.content.Context
import androidx.work.*
import com.example.data.network.LiquidApi
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/** Retry transient FCM rejection without resending or duplicating the chat message. */
class NotificationRetryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val uid = inputData.getString("uid") ?: return Result.failure()
    if (FirebaseAuth.getInstance().currentUser?.uid != uid) return Result.success()
    return try {
      LiquidApi.context = applicationContext
      LiquidApi.call("retryNotifications", mapOf("conversationId" to inputData.getString("cid"), "messageId" to inputData.getString("id")))
      Result.success()
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { if (runAttemptCount < 12) Result.retry() else Result.failure() }
  }
  companion object {
    fun enqueue(context: Context, uid: String, cid: String, id: String) {
      val request = OneTimeWorkRequestBuilder<NotificationRetryWorker>()
        .setInputData(workDataOf("uid" to uid, "cid" to cid, "id" to id))
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .addTag("receipts:$uid").build()
      WorkManager.getInstance(context).enqueueUniqueWork("push:$uid:$id", ExistingWorkPolicy.KEEP, request)
    }
  }
}
