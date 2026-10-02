package com.example.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small encrypted-at-rest cache for decrypted chat-list/notification snippets.
 * Message plaintext never needs to be written to Firestore or ordinary SharedPreferences.
 */
object SecureSnippetStore {
  private const val STORE = "liquid-secure-snippets"
  private const val KEY_ALIAS = "liquid_chat_snippet_key_v1"
  private const val TRANSFORMATION = "AES/GCM/NoPadding"

  fun put(context: Context, uid: String, conversationId: String, messageId: String, text: String) {
    if (uid.isBlank() || conversationId.isBlank() || messageId.isBlank() || text.isBlank()) return
    val clean = text.trim().take(500)
    runCatching {
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.ENCRYPT_MODE, key())
      val encrypted = cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
      val payload = ByteArray(cipher.iv.size + encrypted.size)
      cipher.iv.copyInto(payload, 0)
      encrypted.copyInto(payload, cipher.iv.size)

      context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        .edit()
        .putString(entry(uid, conversationId, messageId), Base64.encodeToString(payload, Base64.NO_WRAP))
        .putString(last(uid, conversationId), messageId)
        .apply()
    }
  }

  fun get(context: Context, uid: String, conversationId: String, messageId: String): String? {
    if (uid.isBlank() || conversationId.isBlank() || messageId.isBlank()) return null
    return runCatching {
      val encoded = context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        .getString(entry(uid, conversationId, messageId), null)
        ?: return null
      val payload = Base64.decode(encoded, Base64.NO_WRAP)
      if (payload.size <= 12) return null
      val iv = payload.copyOfRange(0, 12)
      val encrypted = payload.copyOfRange(12, payload.size)
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
      cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }.getOrNull()
  }

  fun getLast(context: Context, uid: String, conversationId: String, expectedMessageId: String): String? {
    return get(context, uid, conversationId, expectedMessageId)
  }

  fun clearUser(context: Context, uid: String) {
    if (uid.isBlank()) return
    val prefs = context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)
    val prefix = "$uid|"
    val edit = prefs.edit()
    prefs.all.keys.filter { it.startsWith(prefix) }.forEach(edit::remove)
    edit.apply()
  }

  private fun entry(uid: String, cid: String, mid: String) = "$uid|$cid|$mid"
  private fun last(uid: String, cid: String) = "$uid|$cid|last"

  @Synchronized
  private fun key(): SecretKey {
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    generator.init(
      KeyGenParameterSpec.Builder(
        KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
      )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build()
    )
    return generator.generateKey()
  }
}
