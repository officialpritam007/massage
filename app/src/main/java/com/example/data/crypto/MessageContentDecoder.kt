package com.example.data.crypto

import android.content.Context
import com.example.data.local.SecureMessageCache
import org.json.JSONObject
import java.security.MessageDigest

/** Shared, device-only decoding for history, conversation previews and notifications. */
object MessageContentDecoder {
  fun revision(fields: Map<String, Any?>): String = MessageDigest.getInstance("SHA-256")
    .digest(fields.toSortedMap().entries.joinToString("") {
      val value = it.value.toString()
      "${it.key.length}:${it.key}${value.length}:$value"
    }.toByteArray())
    .joinToString("") { "%02x".format(it) }

  fun decode(
    context: Context,
    account: String,
    conversationId: String,
    messageId: String,
    senderId: String,
    fields: Map<String, Any?>,
    currentSenderKeyId: String
  ): JSONObject? {
    if (account.isBlank() || conversationId.isBlank() || messageId.isBlank() || senderId.isBlank()) return null
    if (context.getSharedPreferences("liquid-deletion", 0).getString("account", null) == account) return null
    val payloadKey = fields["e2eeSenderKeyId"] as? String ?: return null
    val trusted = context.getSharedPreferences("liquid-install", 0)
      .getStringSet("trustedPeerKeys:$account:$senderId", emptySet()).orEmpty()
    val expected = when {
      senderId != account && payloadKey in trusted -> payloadKey
      currentSenderKeyId.isNotBlank() -> currentSenderKeyId
      else -> return null // Wait for the contact identity instead of trusting an embedded key.
    }
    if (payloadKey != expected) return null
    val id = "payload:$conversationId:$messageId:$senderId:${revision(fields)}"
    val raw = SecureMessageCache.get(context, account, id) ?: E2eeCrypto.decryptText(
      context, account, senderId, conversationId, messageId, fields, expected
    )?.also { SecureMessageCache.put(context, account, id, it) } ?: return null
    return runCatching { JSONObject(raw) }.getOrNull()
  }
}
