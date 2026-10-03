package com.example.notifications

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Trusted senders must send data-only IDs, never a notification with encrypted body text. */
class LiquidMessagingService : FirebaseMessagingService() {
  override fun onMessageReceived(message: RemoteMessage) {
    val account = FirebaseAuth.getInstance().currentUser?.uid ?: return
    if (message.data["recipient_id"] != account) return
    val cid = message.data["conversation_id"].orEmpty()
    val id = message.data["message_id"].orEmpty()
    if (cid.isBlank() || id.isBlank() || cid.contains('/') || id.contains('/')) return
    MessageNotificationWorker.enqueue(applicationContext, account, cid, id)
  }

  override fun onNewToken(token: String) {
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
      runCatching { NotificationTokenStore.register(applicationContext, token) }
    }
  }
}
