package com.example.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object DatabaseKeyManager {
  private const val PREFS = "liquid-room-key"
  private const val WRAPPED = "wrapped-passphrase"
  private const val ALIAS = "liquid_chat_room_wrap_v1"
  private const val TRANSFORMATION = "AES/GCM/NoPadding"

  fun getOrCreatePassphrase(context: Context): ByteArray {
    val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val existing = prefs.getString(WRAPPED, null)
    if (!existing.isNullOrBlank()) {
      unwrap(existing)?.let { if (it.size >= 32) return it }
      // The keystore entry was invalidated; an unreadable cache must never block app startup.
      context.applicationContext.deleteDatabase(LiquidChatDatabase.DATABASE_NAME)
      prefs.edit().remove(WRAPPED).apply()
    }

    val passphrase = ByteArray(32).also(SecureRandom()::nextBytes)
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
    val encrypted = cipher.doFinal(passphrase)
    val payload = ByteArray(cipher.iv.size + encrypted.size)
    cipher.iv.copyInto(payload, 0)
    encrypted.copyInto(payload, cipher.iv.size)
    prefs.edit().putString(WRAPPED, Base64.encodeToString(payload, Base64.NO_WRAP)).commit()
    return passphrase
  }

  private fun unwrap(encoded: String): ByteArray? = runCatching {
    val payload = Base64.decode(encoded, Base64.NO_WRAP)
    require(payload.size > 12)
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(
      Cipher.DECRYPT_MODE,
      wrappingKey(),
      GCMParameterSpec(128, payload.copyOfRange(0, 12))
    )
    cipher.doFinal(payload.copyOfRange(12, payload.size))
  }.getOrNull()

  private fun wrappingKey(): SecretKey {
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    generator.init(
      KeyGenParameterSpec.Builder(
        ALIAS,
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
