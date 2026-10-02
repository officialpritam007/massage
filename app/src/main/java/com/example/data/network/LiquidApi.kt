package com.example.data.network

import android.content.Context
import android.net.Uri
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Zero-backend media client.
 *
 * Firebase Auth + Cloud Firestore own app data. Media is uploaded directly to Cloudinary
 * with an unsigned upload preset; only Cloudinary's public secure_url is stored in Firestore.
 * No Cloudinary API secret is present in the APK.
 */
object LiquidApi {
  lateinit var context: Context

  private val http = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(90, TimeUnit.SECONDS)
    .writeTimeout(90, TimeUnit.SECONDS)
    .callTimeout(120, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build()

  fun clearMediaCachesOnly() {
    if (!::context.isInitialized) return
    File(context.cacheDir, "private-media").deleteRecursively()
    coil.Coil.imageLoader(context).memoryCache?.clear()
    coil.Coil.imageLoader(context).diskCache?.clear()
  }

  fun clear() = clearMediaCachesOnly()

  fun invalidateMedia(url: String, purgeCaches: Boolean = true) {
    if (::context.isInitialized && url.isNotBlank()) {
      val id = Integer.toHexString(url.hashCode())
      File(File(context.cacheDir, "private-media"), "$id.bin").delete()
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
    val id = Integer.toHexString(url.hashCode())
    val cached = File(File(context.cacheDir, "private-media"), "$id.bin")
    return cached.takeIf { it.exists() && it.length() > 0 }
  }

  suspend fun cachedPrivateMedia(url: String, forceRefresh: Boolean = false): File = withContext(Dispatchers.IO) {
    localFile(url)?.let { return@withContext it }
    require(::context.isInitialized) { "App context is unavailable" }

    val resolved = resolve(url)
    val id = Integer.toHexString(resolved.hashCode())
    val dir = File(context.cacheDir, "private-media").apply { mkdirs() }
    val cached = File(dir, "$id.bin")
    if (cached.exists() && cached.length() > 0 && !forceRefresh) return@withContext cached
    if (forceRefresh) cached.delete()

    val response = http.newCall(Request.Builder().url(resolved).get().build()).execute()
    response.use { value ->
      check(value.isSuccessful) { "Media download failed (" + value.code + ")" }
      val temp = File(dir, "$id.tmp")
      temp.outputStream().use { output ->
        value.body?.byteStream()?.use { input -> input.copyTo(output) }
      }
      check(temp.length() > 0) { "Downloaded media is empty" }
      if (cached.exists()) cached.delete()
      check(temp.renameTo(cached)) { "Unable to cache media" }
    }
    cached
  }

  suspend fun upload(
    uri: Uri,
    conversationId: String?,
    progress: (Float) -> Unit = {}
  ): String = withContext(Dispatchers.IO) {
    require(::context.isInitialized) { "App context is unavailable" }

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
