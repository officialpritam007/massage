package com.example.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.io.File
import javax.crypto.CipherOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object E2eeCrypto {
  private const val STORE = "liquid-e2ee"
  private const val VERSION = 1
  private val random = SecureRandom()

  data class PublicIdentity(
    val publicKey: String,
    val keyId: String
  )

  data class EncryptedPayload(
    val fields: Map<String, Any>
  )

  data class MediaSecret(
    val key: ByteArray,
    val fileIv: ByteArray
  )

  private data class Identity(
    val privateKey: PrivateKey,
    val publicKey: PublicKey,
    val keyId: String
  )

  fun ensureIdentity(context: Context, uid: String): PublicIdentity {
    val identity = loadOrCreateIdentity(context, uid)
    return PublicIdentity(
      publicKey = b64(identity.publicKey.encoded),
      keyId = identity.keyId
    )
  }

  fun deleteIdentity(context: Context, uid: String) {
    context.getSharedPreferences(STORE, 0).edit()
      .remove("private:$uid")
      .remove("privateIv:$uid")
      .remove("public:$uid")
      .apply()

    val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    val alias = wrappingAlias(uid)
    if (ks.containsAlias(alias)) ks.deleteEntry(alias)
  }

  fun encryptText(
    context: Context,
    uid: String,
    recipientPublicKeyBase64: String,
    recipientKeyId: String,
    conversationId: String,
    messageId: String,
    plaintextJson: String
  ): EncryptedPayload {
    val sender = loadOrCreateIdentity(context, uid)
    val recipientPublic = decodePublicKey(recipientPublicKeyBase64)

    val ephemeral = KeyPairGenerator.getInstance("EC").apply {
      initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    val contentKey = ByteArray(32).also(random::nextBytes)
    val contentIv = ByteArray(12).also(random::nextBytes)
    val ciphertext = aesGcmEncrypt(
      contentKey,
      contentIv,
      plaintextJson.toByteArray(Charsets.UTF_8),
      aad(conversationId, messageId)
    )

    val senderWrap = wrapContentKey(
      ephemeral.private,
      sender.publicKey,
      contentKey,
      "$conversationId:$messageId:$uid"
    )
    val recipientWrap = wrapContentKey(
      ephemeral.private,
      recipientPublic,
      contentKey,
      "$conversationId:$messageId:recipient"
    )

    return EncryptedPayload(
      mapOf(
        "e2eeVersion" to VERSION,
        "e2eeEphemeralKey" to b64(ephemeral.public.encoded),
        "e2eeCiphertext" to b64(ciphertext),
        "e2eeContentIv" to b64(contentIv),
        "e2eeSenderWrappedKey" to b64(senderWrap.first),
        "e2eeSenderWrapIv" to b64(senderWrap.second),
        "e2eeRecipientWrappedKey" to b64(recipientWrap.first),
        "e2eeRecipientWrapIv" to b64(recipientWrap.second),
        "e2eeSenderKeyId" to sender.keyId,
        "e2eeRecipientKeyId" to recipientKeyId
      )
    )
  }

  fun encryptMediaFile(
    input: File,
    output: File,
    fileId: String
  ): MediaSecret {
    val key = ByteArray(32).also(random::nextBytes)
    val iv = ByteArray(12).also(random::nextBytes)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
    cipher.updateAAD("media:$fileId:liquid-e2ee-v1".toByteArray(Charsets.UTF_8))

    input.inputStream().use { source ->
      CipherOutputStream(output.outputStream(), cipher).use { target ->
        source.copyTo(target, 64 * 1024)
      }
    }
    return MediaSecret(key, iv)
  }

  fun decryptMediaFile(
    input: File,
    output: File,
    fileId: String,
    secret: MediaSecret
  ) {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(
      Cipher.DECRYPT_MODE,
      SecretKeySpec(secret.key, "AES"),
      GCMParameterSpec(128, secret.fileIv)
    )
    cipher.updateAAD("media:$fileId:liquid-e2ee-v1".toByteArray(Charsets.UTF_8))

    input.inputStream().use { source ->
      CipherOutputStream(output.outputStream(), cipher).use { target ->
        source.copyTo(target, 64 * 1024)
      }
    }
  }

  fun wrapMediaSecret(
    context: Context,
    uid: String,
    recipientPublicKeyBase64: String,
    recipientKeyId: String,
    conversationId: String,
    messageId: String,
    secret: MediaSecret
  ): Map<String, Any> {
    val sender = loadOrCreateIdentity(context, uid)
    val recipientPublic = decodePublicKey(recipientPublicKeyBase64)
    val ephemeral = KeyPairGenerator.getInstance("EC").apply {
      initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    val senderWrap = wrapContentKey(
      ephemeral.private,
      sender.publicKey,
      secret.key,
      "$conversationId:$messageId:media:$uid"
    )
    val recipientWrap = wrapContentKey(
      ephemeral.private,
      recipientPublic,
      secret.key,
      "$conversationId:$messageId:media:recipient"
    )

    return mapOf(
      "e2eeVersion" to VERSION,
      "e2eeEphemeralKey" to b64(ephemeral.public.encoded),
      "e2eeSenderWrappedKey" to b64(senderWrap.first),
      "e2eeSenderWrapIv" to b64(senderWrap.second),
      "e2eeRecipientWrappedKey" to b64(recipientWrap.first),
      "e2eeRecipientWrapIv" to b64(recipientWrap.second),
      "e2eeSenderKeyId" to sender.keyId,
      "e2eeRecipientKeyId" to recipientKeyId,
      "e2eeFileIv" to b64(secret.fileIv)
    )
  }

  fun unwrapMediaSecret(
    context: Context,
    uid: String,
    senderId: String,
    conversationId: String,
    messageId: String,
    fields: Map<String, Any?>
  ): MediaSecret? {
    if ((fields["e2eeVersion"] as? Number)?.toInt() != VERSION) return null
    return runCatching {
      val identity = loadOrCreateIdentity(context, uid)
      val ephemeral = decodePublicKey(fields["e2eeEphemeralKey"] as String)
      val senderCopy = senderId == uid
      val wrappedKey = unb64(
        fields[if (senderCopy) "e2eeSenderWrappedKey" else "e2eeRecipientWrappedKey"] as String
      )
      val wrapIv = unb64(
        fields[if (senderCopy) "e2eeSenderWrapIv" else "e2eeRecipientWrapIv"] as String
      )
      val info = if (senderCopy) {
        "$conversationId:$messageId:media:$uid"
      } else {
        "$conversationId:$messageId:media:recipient"
      }
      val shared = ecdh(identity.privateKey, ephemeral)
      val wrappingKey = hkdfSha256(
        shared,
        null,
        info.toByteArray(Charsets.UTF_8),
        32
      )
      val mediaKey = aesGcmDecrypt(
        wrappingKey,
        wrapIv,
        wrappedKey,
        aadFromInfo(info)
      )
      MediaSecret(
        key = mediaKey,
        fileIv = unb64(fields["e2eeFileIv"] as String)
      )
    }.getOrNull()
  }

  fun decryptText(
    context: Context,
    uid: String,
    senderId: String,
    conversationId: String,
    messageId: String,
    fields: Map<String, Any?>
  ): String? {
    if ((fields["e2eeVersion"] as? Number)?.toInt() != VERSION) return null
    return runCatching {
      val identity = loadOrCreateIdentity(context, uid)
      val ephemeral = decodePublicKey(fields["e2eeEphemeralKey"] as String)
      val senderCopy = senderId == uid
      val wrappedKey = unb64(
        fields[if (senderCopy) "e2eeSenderWrappedKey" else "e2eeRecipientWrappedKey"] as String
      )
      val wrapIv = unb64(
        fields[if (senderCopy) "e2eeSenderWrapIv" else "e2eeRecipientWrapIv"] as String
      )
      val info = if (senderCopy) {
        "$conversationId:$messageId:$uid"
      } else {
        "$conversationId:$messageId:recipient"
      }

      val shared = ecdh(identity.privateKey, ephemeral)
      val wrappingKey = hkdfSha256(
        ikm = shared,
        salt = null,
        info = info.toByteArray(Charsets.UTF_8),
        length = 32
      )
      val contentKey = aesGcmDecrypt(
        wrappingKey,
        wrapIv,
        wrappedKey,
        aadFromInfo(info)
      )

      val plaintext = aesGcmDecrypt(
        contentKey,
        unb64(fields["e2eeContentIv"] as String),
        unb64(fields["e2eeCiphertext"] as String),
        aad(conversationId, messageId)
      )
      plaintext.toString(Charsets.UTF_8)
    }.getOrNull()
  }

  fun payloadJson(
    text: String,
    replyToId: String?,
    replyToText: String?,
    replyToSender: String?
  ): String =
    JSONObject()
      .put("text", text)
      .put("replyToId", replyToId ?: JSONObject.NULL)
      .put("replyToText", replyToText ?: JSONObject.NULL)
      .put("replyToSender", replyToSender ?: JSONObject.NULL)
      .toString()

  private fun loadOrCreateIdentity(context: Context, uid: String): Identity {
    require(uid.isNotBlank()) { "Missing E2EE account identity" }
    val prefs = context.getSharedPreferences(STORE, 0)
    val privateCipher = prefs.getString("private:$uid", null)
    val privateIv = prefs.getString("privateIv:$uid", null)
    val publicEncoded = prefs.getString("public:$uid", null)

    if (!privateCipher.isNullOrBlank() && !privateIv.isNullOrBlank() && !publicEncoded.isNullOrBlank()) {
      runCatching {
        val rawPrivate = unwrapPrivateKey(
          uid,
          unb64(privateCipher),
          unb64(privateIv)
        )
        val privateKey = KeyFactory.getInstance("EC")
          .generatePrivate(PKCS8EncodedKeySpec(rawPrivate))
        val publicKey = decodePublicKey(publicEncoded)
        return Identity(privateKey, publicKey, keyId(publicKey.encoded))
      }
    }

    val pair = KeyPairGenerator.getInstance("EC").apply {
      initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()
    persistIdentity(context, uid, pair)
    return Identity(pair.private, pair.public, keyId(pair.public.encoded))
  }

  private fun persistIdentity(context: Context, uid: String, pair: KeyPair) {
    val iv = ByteArray(12).also(random::nextBytes)
    val wrapped = wrapPrivateKey(uid, pair.private.encoded, iv)
    context.getSharedPreferences(STORE, 0).edit()
      .putString("private:$uid", b64(wrapped))
      .putString("privateIv:$uid", b64(iv))
      .putString("public:$uid", b64(pair.public.encoded))
      .apply()
  }

  private fun wrappingAlias(uid: String) = "liquid_e2ee_wrap_${uid.take(40)}"

  private fun wrappingKey(uid: String): SecretKey {
    val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    val alias = wrappingAlias(uid)
    val existing = ks.getKey(alias, null) as? SecretKey
    if (existing != null) return existing

    val generator = KeyGenerator.getInstance(
      KeyProperties.KEY_ALGORITHM_AES,
      "AndroidKeyStore"
    )
    generator.init(
      KeyGenParameterSpec.Builder(
        alias,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
      )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setRandomizedEncryptionRequired(false)
        .build()
    )
    return generator.generateKey()
  }

  private fun wrapPrivateKey(uid: String, raw: ByteArray, iv: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, wrappingKey(uid), GCMParameterSpec(128, iv))
    return cipher.doFinal(raw)
  }

  private fun unwrapPrivateKey(uid: String, wrapped: ByteArray, iv: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, wrappingKey(uid), GCMParameterSpec(128, iv))
    return cipher.doFinal(wrapped)
  }

  private fun wrapContentKey(
    ephemeralPrivate: PrivateKey,
    targetPublic: PublicKey,
    contentKey: ByteArray,
    info: String
  ): Pair<ByteArray, ByteArray> {
    val shared = ecdh(ephemeralPrivate, targetPublic)
    val wrappingKey = hkdfSha256(
      shared,
      null,
      info.toByteArray(Charsets.UTF_8),
      32
    )
    val iv = ByteArray(12).also(random::nextBytes)
    return aesGcmEncrypt(
      wrappingKey,
      iv,
      contentKey,
      aadFromInfo(info)
    ) to iv
  }

  private fun ecdh(privateKey: PrivateKey, publicKey: PublicKey): ByteArray {
    val agreement = KeyAgreement.getInstance("ECDH")
    agreement.init(privateKey)
    agreement.doPhase(publicKey, true)
    return agreement.generateSecret()
  }

  private fun aesGcmEncrypt(
    key: ByteArray,
    iv: ByteArray,
    plaintext: ByteArray,
    aad: ByteArray
  ): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
    cipher.updateAAD(aad)
    return cipher.doFinal(plaintext)
  }

  private fun aesGcmDecrypt(
    key: ByteArray,
    iv: ByteArray,
    ciphertext: ByteArray,
    aad: ByteArray
  ): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
    cipher.updateAAD(aad)
    return cipher.doFinal(ciphertext)
  }

  private fun hkdfSha256(
    ikm: ByteArray,
    salt: ByteArray?,
    info: ByteArray,
    length: Int
  ): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    val realSalt = salt ?: ByteArray(32)
    mac.init(SecretKeySpec(realSalt, "HmacSHA256"))
    val prk = mac.doFinal(ikm)

    val result = ByteArray(length)
    var previous = ByteArray(0)
    var written = 0
    var counter = 1
    while (written < length) {
      mac.init(SecretKeySpec(prk, "HmacSHA256"))
      mac.update(previous)
      mac.update(info)
      mac.update(counter.toByte())
      previous = mac.doFinal()
      val take = minOf(previous.size, length - written)
      previous.copyInto(result, written, 0, take)
      written += take
      counter += 1
    }
    return result
  }

  private fun decodePublicKey(encoded: String): PublicKey =
    KeyFactory.getInstance("EC")
      .generatePublic(X509EncodedKeySpec(unb64(encoded)))

  private fun keyId(encoded: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
      .digest(encoded)
      .take(12)
      .joinToString("") { "%02x".format(it) }

  private fun aad(conversationId: String, messageId: String): ByteArray =
    "$conversationId:$messageId:liquid-e2ee-v1".toByteArray(Charsets.UTF_8)

  private fun aadFromInfo(info: String): ByteArray =
    "$info:liquid-e2ee-wrap-v1".toByteArray(Charsets.UTF_8)

  private fun b64(value: ByteArray): String =
    Base64.encodeToString(value, Base64.NO_WRAP)

  private fun unb64(value: String): ByteArray =
    Base64.decode(value, Base64.NO_WRAP)
}
