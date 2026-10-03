package com.example.notifications

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import java.util.UUID

object NotificationTokenStore {
  suspend fun register(context: Context, refreshedToken: String? = null) {
    val account = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val token = refreshedToken ?: FirebaseMessaging.getInstance().token.await()
    val prefs = context.getSharedPreferences("liquid-install", 0)
    val id = prefs.getString("installationId", null) ?: UUID.randomUUID().toString().replace("-", "").also {
      prefs.edit().putString("installationId", it).commit()
    }
    val marker = "registeredFcm:$account:$id"
    if (prefs.getString(marker, null) == token) return
    if (FirebaseAuth.getInstance().currentUser?.uid != account) return
    FirebaseFirestore.getInstance().document("users/$account/devices/$id").set(mapOf(
      "token" to token, "platform" to "android", "updatedAt" to FieldValue.serverTimestamp()
    )).await()
    if (FirebaseAuth.getInstance().currentUser?.uid == account) prefs.edit().putString(marker, token).commit()
  }
}
