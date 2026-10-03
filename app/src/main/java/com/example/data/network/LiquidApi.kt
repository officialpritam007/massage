package com.example.data.network

import android.content.Context
import android.net.Uri
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.tasks.await
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

/**
 * Zero-backend media client.
 *
 * Firebase Auth + Cloud Firestore own app data. Media is uploaded directly to Cloudinary
 * with an unsigned upload preset; only Cloudinary's public secure_url is stored in Firestore.
 * No Cloudinary API secret is present in the APK.
 */
object LiquidApi {
  lateinit var context: Context

  /** Upload response proofs remain private, and are verified only by the trusted cleaner. */
  suspend fun flushUploadReceipts(account: String) {
    val prefs = context.getSharedPreferences("liquid-cloudinary-assets", 0)
    for ((key, raw) in prefs.all.filterKeys { it.startsWith("receipt:$account:") }) {
      check(FirebaseAuth.getInstance().currentUser?.uid == account) { "Upload owner session changed" }
      val receipt = JSONObject(raw as String)
      withTimeout(20_000L) { FirebaseFirestore.getInstance().document("users/$account/mediaUploads/${receipt.getString("assetId")}").set(mapOf(
        "assetId" to receipt.getString("assetId"),
        "publicId" to receipt.getString("publicId"),
        "version" to receipt.getLong("version"),
        "resourceType" to receipt.getString("resourceType"),
        "secureUrl" to receipt.getString("secureUrl"),
        "signature" to receipt.getString("signature"),
        "createdAt" to FieldValue.serverTimestamp()
      )).await() }
      check(prefs.edit().remove(key).commit()) { "Unable to acknowledge the saved upload receipt" }
    }
  }

  private val http = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(90, TimeUnit.SECONDS)
    .writeTimeout(90, TimeUnit.SECONDS)
    .callTimeout(120, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build()
  private val mediaHttp = http.newBuilder().connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
  private val mediaLocks = ConcurrentHashMap<String, Mutex>()

  fun isSupportedMedia(url: String?) = !url.isNullOrBlank() &&
    (url.startsWith("https://res.cloudinary.com/${BuildConfig.CLOUDINARY_CLOUD_NAME}/") || url.startsWith("file:"))

  private fun mediaId(url: String): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(url.toByteArray()).joinToString("") { "%02x".format(it) }

  fun clearMediaCachesOnly() {
    if (!::context.isInitialized) return
    File(context.cacheDir, "private-media").deleteRecursively()
    coil.Coil.imageLoader(context).memoryCache?.clear()
    coil.Coil.imageLoader(context).diskCache?.clear()
  }

  fun clear() = clearMediaCachesOnly()

  fun invalidateMedia(url: String, purgeCaches: Boolean = true) {
    if (::context.isInitialized && url.isNotBlank()) {
      val id = mediaId(url)
      File(File(context.cacheDir, "private-media"), "$id.bin").delete()
      File(File(context.cacheDir, "private-media"), "${Integer.toHexString(url.hashCode())}.bin").delete()
    }
    if (purgeCaches && ::context.isInitialized) {
      coil.Coil.imageLoader(context).memoryCache?.clear()
      coil.Coil.imageLoader(context).diskCache?.clear()
    }
  }

  private fun localFile(url: String?): File? {
    if (url.isNullOrBlank() || !url.startsWith("file:")) return null
    val path = runCatching { Uri.parse(url).path }.getOrNull().orEmpty()
    return path.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() && it.length() > 0 }
  }

  fun peekCachedPrivateMedia(url: String?): File? {
    localFile(url)?.let { return it }
    if (!::context.isInitialized || url.isNullOrBlank() || url.startsWith("cloudinary:")) return null
    val id = mediaId(url)
    val cached = File(File(context.cacheDir, "private-media"), "$id.bin")
    return cached.takeIf { it.exists() && it.length() > 0 }
      ?: File(File(context.cacheDir, "private-media"), "${Integer.toHexString(url.hashCode())}.bin")
        .takeIf { it.exists() && it.length() > 0 }
  }

  suspend fun cachedPrivateMedia(url: String, forceRefresh: Boolean = false): File = withContext(Dispatchers.IO) {
    localFile(url)?.let { return@withContext it }
    require(::context.isInitialized) { "App context is unavailable" }

    val resolved = resolve(url)
    val id = mediaId(resolved)
    val dir = File(context.cacheDir, "private-media").apply { mkdirs() }
    val cached = File(dir, "$id.bin")
    mediaLocks.getOrPut(id) { Mutex() }.withLock {
      if (!forceRefresh) peekCachedPrivateMedia(url)?.let { return@withLock it }
      val temp = File.createTempFile("$id-", ".tmp", dir)
      try {
        mediaHttp.newCall(Request.Builder().url(resolved).get().build()).execute().use { value ->
          check(value.isSuccessful) { "Media download failed (" + value.code + ")" }
          temp.outputStream().use { output -> value.body?.byteStream()?.use { input -> input.copyTo(output) } }
          check(temp.length() > 0) { "Downloaded media is empty" }
          // The prior cache remains usable until a full replacement is available.
          check(temp.renameTo(cached)) { "Unable to cache media" }
        }
        cached
      } finally {
        temp.delete()
      }
    }
  }

  suspend fun upload(
    uri: Uri,
    conversationId: String?,
    progress: (Float) -> Unit = {}
  ): String = withContext(Dispatchers.IO) {
    require(::context.isInitialized) { "App context is unavailable" }
    val owner = FirebaseAuth.getInstance().currentUser?.uid ?: error("Please sign in before uploading")

    val cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME.trim()
    val preset = BuildConfig.CLOUDINARY_UPLOAD_PRESET.trim()
    check(cloudName.isNotBlank()) { "Cloudinary cloud name is not configured" }
    check(preset.isNotBlank()) { "Cloudinary unsigned upload preset is not configured" }

    val extension = uri.lastPathSegment.orEmpty().substringAfterLast('.', "").lowercase()
    var mime = context.contentResolver.getType(uri)?.lowercase() ?: when (extension) {
      "jpg", "jpeg" -> "image/jpeg"
      "png" -> "image/png"
      "webp" -> "image/webp"
      "aac" -> "audio/aac"
      "m4a" -> "audio/mp4"
      "mp4" -> "video/mp4"
      else -> "application/octet-stream"
    }

    val file = File.createTempFile("cloudinary-upload-", ".bin", context.cacheDir)
    try {
      context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "Unable to open this file" }
        file.outputStream().use { output ->
          val buffer = ByteArray(65536)
          var total = 0L
          while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            require(total <= 9L * 1024 * 1024) { "Maximum attachment size is 9 MB" }
            output.write(buffer, 0, n)
          }
        }
      }

      if (mime.startsWith("image/") && android.os.Build.VERSION.SDK_INT >= 28) {
        val bitmap = android.graphics.ImageDecoder.decodeBitmap(
          android.graphics.ImageDecoder.createSource(file)
        ) { decoder, info, _ ->
          decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
          val ratio = (maxOf(info.size.width, info.size.height).toFloat() / 1600f).coerceAtLeast(1f)
          decoder.setTargetSize(
            (info.size.width / ratio).toInt().coerceAtLeast(1),
            (info.size.height / ratio).toInt().coerceAtLeast(1)
          )
        }
        file.outputStream().use {
          bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 86, it)
        }
        bitmap.recycle()
        mime = "image/jpeg"
      }

      require(file.length() > 0) { "Empty file" }

      progress(0f)
      val filename = when {
        mime.startsWith("image/") -> "image.jpg"
        mime.startsWith("video/") -> "video.mp4"
        mime.startsWith("audio/") -> "voice.m4a"
        else -> "attachment.bin"
      }
      val body = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("upload_preset", preset)
        .addFormDataPart("public_id", "liquid-chat/accounts/$owner/${UUID.randomUUID().toString().replace("-", "")}")
        .addFormDataPart("file", filename, file.asRequestBody(mime.toMediaType()))
        .build()

      val uploadUrl = "https://api.cloudinary.com/v1_1/" + cloudName + "/auto/upload"
      val response = http.newCall(Request.Builder().url(uploadUrl).post(body).build()).execute()
      val uploaded = response.use { value ->
        val raw = value.body?.string().orEmpty()
        val json = runCatching { JSONObject(raw.ifBlank { "{}" }) }.getOrElse { JSONObject() }
        check(value.isSuccessful) {
          json.optJSONObject("error")?.optString("message")
            ?: "Cloudinary upload failed (" + value.code + ")"
        }
        json
      }

      val secureUrl = uploaded.optString("secure_url")
      check(secureUrl.startsWith("https://res.cloudinary.com/" + cloudName + "/")) {
        "Cloudinary did not return a valid secure URL"
      }
      val receipt = JSONObject().put("assetId", uploaded.getString("asset_id"))
        .put("publicId", uploaded.getString("public_id"))
        .put("version", uploaded.getLong("version"))
        .put("resourceType", uploaded.getString("resource_type"))
        .put("secureUrl", secureUrl).put("signature", uploaded.getString("signature"))
      val receiptKey = "receipt:$owner:${receipt.getString("assetId")}"
      check(context.getSharedPreferences("liquid-cloudinary-assets", 0).edit().putString(receiptKey, receipt.toString()).commit()) {
        "Unable to save the private media ownership receipt"
      }
      // Even a cancelled upload must register its ownership before account cleanup
      // can proceed, otherwise an unreferenced upload could be left in the cloud.
      withContext(NonCancellable) { flushUploadReceipts(owner) }
      val returnedId = receipt.getString("publicId")
      check(returnedId.startsWith("liquid-chat/accounts/$owner/") &&
        Regex("^[0-9a-f]{32}(\\.[A-Za-z0-9]{1,12})?$").matches(returnedId.substringAfterLast('/'))) {
        "Media upload configuration needs an update. Contact support before retrying."
      }
      progress(1f)
      secureUrl
    } finally {
      file.delete()
    }
  }

  suspend fun resolve(url: String, forceRefresh: Boolean = false): String {
    if (url.startsWith("cloudinary:")) {
      error("Legacy private media is not supported in free direct-upload mode")
    }
    return url
  }
}
