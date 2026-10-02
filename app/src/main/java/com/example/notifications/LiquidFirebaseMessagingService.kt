package com.example.notifications

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Keep the FCM callback bounded; WorkManager owns process-safe local decryption. */
class LiquidFirebaseMessagingService : FirebaseMessagingService() {
  override fun onNewToken(token: String) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    if (token.isNotBlank()) FirebaseFirestore.getInstance().document("users/$uid")
      .update("tokens", FieldValue.arrayUnion(token))
  }

  override fun onMessageReceived(remoteMessage: RemoteMessage) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val cid = remoteMessage.data["conversation_id"].orEmpty()
    val mid = remoteMessage.data["message_id"].orEmpty()
    if (cid.isBlank() || mid.isBlank() || '/' in cid || '/' in mid) return
    MessageNotificationWorker.enqueue(applicationContext, uid, cid, mid)
  }
}
