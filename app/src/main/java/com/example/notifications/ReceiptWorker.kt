package com.example.notifications

import android.content.Context
import android.os.Build
import androidx.work.*
import com.example.data.network.LiquidApi
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Persists acknowledgement work across process death; receipt is independent of presence. */
class ReceiptWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val uid = inputData.getString("uid") ?: return Result.failure()
    if (FirebaseAuth.getInstance().currentUser?.uid != uid) return Result.success()
    val cid = inputData.getString("cid") ?: return Result.failure()
    val ids = inputData.getStringArray("ids")?.toList().orEmpty()
    val status = inputData.getString("status") ?: "DELIVERED"
    return try {
      LiquidApi.context = applicationContext
      // A push preview alone is not proof that the full message reached the device.
      val received = ids.filter { id ->
        val doc = FirebaseFirestore.getInstance().document("conversations/$cid/messages/$id").get(Source.SERVER).await()
        doc.exists() && doc.getString("senderId") != uid && doc.get("deletedForEveryone") != true &&
          (doc.get("hiddenFor") as? List<*>)?.contains(uid) != true
      }
      if (received.isNotEmpty()) LiquidApi.call("receipts", mapOf("conversationId" to cid, "messageIds" to received, "status" to status))
      Result.success()
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { if (runAttemptCount < 12) Result.retry() else Result.failure() }
  }

  companion object {
    fun enqueue(context: Context, uid: String, cid: String, ids: List<String>, status: String, urgent: Boolean = false) {
      if (uid.isBlank() || cid.isBlank() || ids.isEmpty()) return
      ids.distinct().chunked(60).forEach { chunk ->
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(chunk.sorted().joinToString(",").toByteArray())
          .take(12).joinToString("") { "%02x".format(it) }
        val request = OneTimeWorkRequestBuilder<ReceiptWorker>()
          .setInputData(workDataOf("uid" to uid, "cid" to cid, "ids" to chunk.toTypedArray(), "status" to status))
          .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
          .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
          .addTag("receipts:$uid")
        if (urgent && Build.VERSION.SDK_INT >= 31) request.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        WorkManager.getInstance(context).enqueueUniqueWork("receipt:$uid:$cid:$status:$fingerprint", ExistingWorkPolicy.KEEP, request.build())
      }
    }
  }
}

/** Only the resumed conversation suppresses its own foreground notification. */
object VisibleConversation {
  @Volatile var id: String? = null
}
