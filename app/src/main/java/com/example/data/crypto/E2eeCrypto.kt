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
import java.security.Signature
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

  fun storePendingMediaSecret(
    context: Context,
    uid: String,
    fileId: String,
    secret: MediaSecret
  ) {
    val iv = ByteArray(12).also(random::nextBytes)
    val raw = secret.key + secret.fileIv
    val wrapped = wrapPrivateKey(uid, raw, iv)
    context.getSharedPreferences(STORE, 0).edit()
      .putString("pendingMedia:$uid:$fileId", b64(wrapped))
      .putString("pendingMediaIv:$uid:$fileId", b64(iv))
      .apply()
  }

  fun loadPendingMediaSecret(
    context: Context,
    uid: String,
    fileId: String
  ): MediaSecret? {
    if (uid.isBlank() || fileId.isBlank()) return null
    val prefs = context.getSharedPreferences(STORE, 0)
    val wrapped = prefs.getString("pendingMedia:$uid:$fileId", null) ?: return null
    val iv = prefs.getString("pendingMediaIv:$uid:$fileId", null) ?: return null
    return runCatching {
      val raw = unwrapPrivateKey(uid, unb64(wrapped), unb64(iv))
      require(raw.size == 44) { "Invalid pending media secret" }
      MediaSecret(
        key = raw.copyOfRange(0, 32),
        fileIv = raw.copyOfRange(32, 44)
      )
    }.getOrNull()
  }

  fun deletePendingMediaSecret(context: Context, uid: String, fileId: String) {
    if (uid.isBlank() || fileId.isBlank()) return
    context.getSharedPreferences(STORE, 0).edit()
      .remove("pendingMedia:$uid:$fileId")
      .remove("pendingMediaIv:$uid:$fileId")
      .apply()
  }

  private data class Identity(
    val privateKey: PrivateKey,
    val publicKey: PublicKey,
    val keyId: String
  )

  private val identities = mutableMapOf<String, Identity>()

  fun ensureIdentity(context: Context, uid: String): PublicIdentity {
    val identity = loadOrCreateIdentity(context, uid)
    return PublicIdentity(
      publicKey = b64(identity.publicKey.encoded),
      keyId = identity.keyId
    )
  }

  @Synchronized
  fun deleteIdentity(context: Context, uid: String) {
    identities.remove(uid)
    val prefs = context.getSharedPreferences(STORE, 0)
    val editor = prefs.edit()
      .remove("private:$uid")
      .remove("privateIv:$uid")
      .remove("public:$uid")
    prefs.all.keys
      .filter { it.startsWith("pendingMedia:$uid:") || it.startsWith("pendingMediaIv:$uid:") }
      .forEach { editor.remove(it) }
    editor.apply()

    val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    val alias = wrappingAlias(uid)
    if (ks.containsAlias(alias)) ks.deleteEntry(alias)
  }

  @Synchronized
  fun clearDeviceIdentities(context: Context) {
    identities.clear()
    val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    java.util.Collections.list(ks.aliases()).filter { it.startsWith("liquid_e2ee_wrap_") }.forEach(ks::deleteEntry)
    check(context.getSharedPreferences(STORE, 0).edit().clear().commit()) { "Unable to erase encryption identities" }
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

    val ephemeralEncoded = b64(ephemeral.public.encoded)
    val ciphertextEncoded = b64(ciphertext)
    val contentIvEncoded = b64(contentIv)
    val senderWrappedEncoded = b64(senderWrap.first)
    val senderWrapIvEncoded = b64(senderWrap.second)
    val recipientWrappedEncoded = b64(recipientWrap.first)
    val recipientWrapIvEncoded = b64(recipientWrap.second)
    val senderPublicEncoded = b64(sender.publicKey.encoded)
    val signatureEncoded = b64(
      sign(
        sender.privateKey,
        signatureBytes(
          conversationId = conversationId,
          messageId = messageId,
          ephemeralKey = ephemeralEncoded,
          ciphertext = ciphertextEncoded,
          contentIv = contentIvEncoded,
          senderWrappedKey = senderWrappedEncoded,
          senderWrapIv = senderWrapIvEncoded,
          recipientWrappedKey = recipientWrappedEncoded,
          recipientWrapIv = recipientWrapIvEncoded,
          senderKeyId = sender.keyId,
          recipientKeyId = recipientKeyId,
          fileIv = ""
        )
      )
    )

    return EncryptedPayload(
      mapOf(
        "e2eeVersion" to VERSION,
        "e2eeEphemeralKey" to ephemeralEncoded,
        "e2eeCiphertext" to ciphertextEncoded,
        "e2eeContentIv" to contentIvEncoded,
        "e2eeSenderWrappedKey" to senderWrappedEncoded,
        "e2eeSenderWrapIv" to senderWrapIvEncoded,
        "e2eeRecipientWrappedKey" to recipientWrappedEncoded,
        "e2eeRecipientWrapIv" to recipientWrapIvEncoded,
        "e2eeSenderKeyId" to sender.keyId,
        "e2eeRecipientKeyId" to recipientKeyId,
        "e2eeSenderPublicKey" to senderPublicEncoded,
        "e2eeSignature" to signatureEncoded
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

    val ephemeralEncoded = b64(ephemeral.public.encoded)
    val senderWrappedEncoded = b64(senderWrap.first)
    val senderWrapIvEncoded = b64(senderWrap.second)
    val recipientWrappedEncoded = b64(recipientWrap.first)
    val recipientWrapIvEncoded = b64(recipientWrap.second)
    val fileIvEncoded = b64(secret.fileIv)
    val senderPublicEncoded = b64(sender.publicKey.encoded)
    val signatureEncoded = b64(
      sign(
        sender.privateKey,
        signatureBytes(
          conversationId = conversationId,
          messageId = messageId,
          ephemeralKey = ephemeralEncoded,
          ciphertext = "",
          contentIv = "",
          senderWrappedKey = senderWrappedEncoded,
          senderWrapIv = senderWrapIvEncoded,
          recipientWrappedKey = recipientWrappedEncoded,
          recipientWrapIv = recipientWrapIvEncoded,
          senderKeyId = sender.keyId,
          recipientKeyId = recipientKeyId,
          fileIv = fileIvEncoded
        )
      )
    )

    return mapOf(
      "e2eeVersion" to VERSION,
      "e2eeEphemeralKey" to ephemeralEncoded,
      "e2eeSenderWrappedKey" to senderWrappedEncoded,
      "e2eeSenderWrapIv" to senderWrapIvEncoded,
      "e2eeRecipientWrappedKey" to recipientWrappedEncoded,
      "e2eeRecipientWrapIv" to recipientWrapIvEncoded,
      "e2eeSenderKeyId" to sender.keyId,
      "e2eeRecipientKeyId" to recipientKeyId,
      "e2eeFileIv" to fileIvEncoded,
      "e2eeSenderPublicKey" to senderPublicEncoded,
      "e2eeSignature" to signatureEncoded
    )
  }

  fun unwrapMediaSecret(
    context: Context,
    uid: String,
    senderId: String,
    conversationId: String,
    messageId: String,
    fields: Map<String, Any?>,
    expectedSenderKeyId: String? = null
  ): MediaSecret? {
    if ((fields["e2eeVersion"] as? Number)?.toInt() != VERSION) return null
    return runCatching {
      val identity = loadOrCreateIdentity(context, uid)
      verifySignedPayload(
        conversationId = conversationId,
        messageId = messageId,
        fields = fields,
        fileIv = fields["e2eeFileIv"] as String,
        expectedSenderKeyId = expectedSenderKeyId
      )
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
    fields: Map<String, Any?>,
    expectedSenderKeyId: String? = null
  ): String? {
    if ((fields["e2eeVersion"] as? Number)?.toInt() != VERSION) return null
    return runCatching {
      val identity = loadOrCreateIdentity(context, uid)
      verifySignedPayload(
        conversationId = conversationId,
        messageId = messageId,
        fields = fields,
        fileIv = "",
        expectedSenderKeyId = expectedSenderKeyId
      )
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

  @Synchronized
  private fun loadOrCreateIdentity(context: Context, uid: String): Identity {
    require(uid.isNotBlank()) { "Missing E2EE account identity" }
    identities[uid]?.let { return it }
    val prefs = context.getSharedPreferences(STORE, 0)
    val privateCipher = prefs.getString("private:$uid", null)
    val privateIv = prefs.getString("privateIv:$uid", null)
    val publicEncoded = prefs.getString("public:$uid", null)

    if (listOf(privateCipher, privateIv, publicEncoded).any { !it.isNullOrBlank() }) {
      require(!privateCipher.isNullOrBlank() && !privateIv.isNullOrBlank() && !publicEncoded.isNullOrBlank()) {
        "The saved encryption identity is incomplete. It was preserved; a replacement key was not generated."
      }
    }

    if (!privateCipher.isNullOrBlank() && !privateIv.isNullOrBlank() && !publicEncoded.isNullOrBlank()) {
      // Never silently rotate an existing identity when the Keystore is temporarily unavailable.
      run {
        val rawPrivate = unwrapPrivateKey(
          uid,
          unb64(privateCipher),
          unb64(privateIv)
        )
        val privateKey = KeyFactory.getInstance("EC")
          .generatePrivate(PKCS8EncodedKeySpec(rawPrivate))
        val publicKey = decodePublicKey(publicEncoded)
        return Identity(privateKey, publicKey, keyId(publicKey.encoded)).also { identities[uid] = it }
      }
    }

    val pair = KeyPairGenerator.getInstance("EC").apply {
      initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()
    persistIdentity(context, uid, pair)
    return Identity(pair.private, pair.public, keyId(pair.public.encoded)).also { identities[uid] = it }
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

  private fun sign(privateKey: PrivateKey, bytes: ByteArray): ByteArray {
    val signature = Signature.getInstance("SHA256withECDSA")
    signature.initSign(privateKey, random)
    signature.update(bytes)
    return signature.sign()
  }

  private fun verify(publicKey: PublicKey, bytes: ByteArray, signatureBytes: ByteArray): Boolean {
    val signature = Signature.getInstance("SHA256withECDSA")
    signature.initVerify(publicKey)
    signature.update(bytes)
    return signature.verify(signatureBytes)
  }

  private fun verifySignedPayload(
    conversationId: String,
    messageId: String,
    fields: Map<String, Any?>,
    fileIv: String,
    expectedSenderKeyId: String?
  ) {
    val senderPublic = decodePublicKey(fields["e2eeSenderPublicKey"] as String)
    val senderKeyId = fields["e2eeSenderKeyId"] as String
    require(keyId(senderPublic.encoded) == senderKeyId) { "Sender key fingerprint mismatch" }
    if (!expectedSenderKeyId.isNullOrBlank()) {
      require(senderKeyId == expectedSenderKeyId) { "Contact encryption key changed" }
    }
    val bytes = signatureBytes(
      conversationId = conversationId,
      messageId = messageId,
      ephemeralKey = fields["e2eeEphemeralKey"] as String,
      ciphertext = fields["e2eeCiphertext"] as? String ?: "",
      contentIv = fields["e2eeContentIv"] as? String ?: "",
      senderWrappedKey = fields["e2eeSenderWrappedKey"] as String,
      senderWrapIv = fields["e2eeSenderWrapIv"] as String,
      recipientWrappedKey = fields["e2eeRecipientWrappedKey"] as String,
      recipientWrapIv = fields["e2eeRecipientWrapIv"] as String,
      senderKeyId = senderKeyId,
      recipientKeyId = fields["e2eeRecipientKeyId"] as String,
      fileIv = fileIv
    )
    require(
      verify(senderPublic, bytes, unb64(fields["e2eeSignature"] as String))
    ) { "Encrypted payload signature invalid" }
  }

  private fun signatureBytes(
    conversationId: String,
    messageId: String,
    ephemeralKey: String,
    ciphertext: String,
    contentIv: String,
    senderWrappedKey: String,
    senderWrapIv: String,
    recipientWrappedKey: String,
    recipientWrapIv: String,
    senderKeyId: String,
    recipientKeyId: String,
    fileIv: String
  ): ByteArray = listOf(
    "liquid-e2ee-v1",
    conversationId,
    messageId,
    ephemeralKey,
    ciphertext,
    contentIv,
    senderWrappedKey,
    senderWrapIv,
    recipientWrappedKey,
    recipientWrapIv,
    senderKeyId,
    recipientKeyId,
    fileIv
  ).joinToString("|").toByteArray(Charsets.UTF_8)

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
