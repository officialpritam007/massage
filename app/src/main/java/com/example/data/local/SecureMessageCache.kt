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

/** Account-scoped plaintext cache encrypted with an Android Keystore key, never sent to Firestore. */
object SecureMessageCache {
  private const val STORE = "liquid-secure-messages"
  private const val ALIAS = "liquid_message_cache_v1"

  fun put(context: Context, uid: String, id: String, value: String) {
    if (uid.isBlank() || id.isBlank()) return
    if (context.getSharedPreferences("liquid-deletion", 0).getString("account", null) == uid) return
    runCatching {
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.ENCRYPT_MODE, key())
      cipher.updateAAD("$uid|$id".toByteArray())
      val bytes = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
      context.getSharedPreferences(STORE, 0).edit()
        .putString("$uid|$id", Base64.encodeToString(bytes, Base64.NO_WRAP)).apply()
    }
  }

  fun get(context: Context, uid: String, id: String): String? = runCatching {
    val encoded = context.getSharedPreferences(STORE, 0).getString("$uid|$id", null) ?: return null
    val bytes = Base64.decode(encoded, Base64.NO_WRAP)
    if (bytes.size <= 12) return null
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
    cipher.updateAAD("$uid|$id".toByteArray())
    cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
  }.getOrNull()

  fun clear(context: Context, uid: String) {
    val prefs = context.getSharedPreferences(STORE, 0)
    val edit = prefs.edit()
    prefs.all.keys.filter { it.startsWith("$uid|") }.forEach { edit.remove(it) }
    edit.apply()
  }

  @Synchronized
  fun clearDeviceCache(context: Context) {
    check(context.getSharedPreferences(STORE, 0).edit().clear().commit()) { "Unable to erase decrypted message cache" }
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS)
  }

  @Synchronized
  private fun key(): SecretKey {
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
      init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256).build())
    }.generateKey()
  }
}
