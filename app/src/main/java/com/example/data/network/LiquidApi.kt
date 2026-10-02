package com.example.data.network

import android.content.Context
import android.net.Uri
import com.example.BuildConfig
import com.example.data.crypto.E2eeCrypto
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object LiquidApi {
  lateinit var context: Context
  private val http = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(90, TimeUnit.SECONDS)
    .writeTimeout(90, TimeUnit.SECONDS)
    .callTimeout(120, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build()

  private data class ResolvedMedia(val url: String, val validUntil: Long)
  private val resolvedMedia = ConcurrentHashMap<String, ResolvedMedia>()
  private val mediaSecrets = ConcurrentHashMap<String, E2eeCrypto.MediaSecret>()

  fun registerMediaSecret(url: String, secret: E2eeCrypto.MediaSecret) {
    if (!url.startsWith("cloudinary:")) return
    mediaSecrets[url.removePrefix("cloudinary:")] = secret
  }

  fun mediaSecret(url: String): E2eeCrypto.MediaSecret? {
    if (!url.startsWith("cloudinary:")) return null
    val fileId = url.removePrefix("cloudinary:")
    mediaSecrets[fileId]?.let { return it }
    if (!::context.isInitialized) return null
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val restored = E2eeCrypto.loadPendingMediaSecret(context, uid, fileId) ?: return null
    mediaSecrets[fileId] = restored
    return restored
  }

  fun forgetPendingMediaSecret(url: String) {
    if (!url.startsWith("cloudinary:") || !::context.isInitialized) return
    val fileId = url.removePrefix("cloudinary:")
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    E2eeCrypto.deletePendingMediaSecret(context, uid, fileId)
    mediaSecrets.remove(fileId)
  }


  suspend fun call(action: String, data: Map<String, Any?> = emptyMap()): JSONObject = withContext(Dispatchers.IO) {
    check(BuildConfig.LIQUID_API_URL.startsWith("https://")) {
      "Backend setup required: configure LIQUID_API_URL with the Firebase liquidApi function URL."
    }
    val user = FirebaseAuth.getInstance().currentUser ?: error("Please sign in again")
    var token = user.getIdToken(false).await().token ?: error("Please sign in again")
    val payloadJson = JSONObject(data).put("action", action).toString()

    fun execute(idToken: String): Response = http.newCall(
      Request.Builder()
        .url(BuildConfig.LIQUID_API_URL)
        .header("Authorization", "Bearer $idToken")
        .header("Content-Type", "application/json")
        .post(payloadJson.toRequestBody("application/json".toMediaType()))
        .build()
    ).execute()

    fun parse(response: Response): JSONObject {
      response.use { value ->
        val raw = value.body?.string().orEmpty()
        val json = runCatching { JSONObject(raw.ifBlank { "{}" }) }.getOrElse {
          JSONObject().put("error", "Invalid backend response (${value.code})")
        }
        check(value.isSuccessful) { json.optString("error", "Request failed (${value.code})") }
        return json
      }
    }

    var response = execute(token)
    if (response.code == 401) {
      response.close()
      token = user.getIdToken(true).await().token ?: error("Please sign in again")
      response = execute(token)
    }
    parse(response)
  }
  fun clearMediaCachesOnly() {
    resolvedMedia.clear()
    mediaSecrets.clear()
    if (::context.isInitialized) {
      File(context.cacheDir, "private-media").deleteRecursively()
      coil.Coil.imageLoader(context).memoryCache?.clear()
      coil.Coil.imageLoader(context).diskCache?.clear()
    }
  }

  fun clear() = clearMediaCachesOnly()

  fun invalidateMedia(url: String, purgeCaches: Boolean = true) {
    if (url.startsWith("cloudinary:")) {
      val fileId = url.removePrefix("cloudinary:")
      resolvedMedia.remove(fileId)
      mediaSecrets.remove(fileId)
      if (::context.isInitialized) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        E2eeCrypto.deletePendingMediaSecret(context, uid, fileId)
        File(File(context.cacheDir, "private-media"), "$fileId.bin").delete()
      }
    }
    if (purgeCaches && ::context.isInitialized) {
      // A deleted message must never be resurrected from an already-resolved URL.
      coil.Coil.imageLoader(context).memoryCache?.clear()
      coil.Coil.imageLoader(context).diskCache?.clear()
    }
  }

  private fun localFile(url: String?): File? {
    if (url.isNullOrBlank() || !url.startsWith("file:")) return null
    val path = runCatching { Uri.parse(url).path }.getOrNull().orEmpty()
    return path.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() && it.length() > 0 }
  }

  /** Returns an already-downloaded private media file synchronously so Compose can avoid a spinner. */
  fun peekCachedPrivateMedia(url: String?): File? {
    localFile(url)?.let { return it }
    if (!::context.isInitialized || url.isNullOrBlank()) return null
    val id = if (url.startsWith("cloudinary:")) url.removePrefix("cloudinary:") else Integer.toHexString(url.hashCode())
    val cached = File(File(context.cacheDir, "private-media"), "$id.bin")
    return cached.takeIf { it.exists() && it.length() > 0 }
  }

  suspend fun cachedPrivateMedia(url: String, forceRefresh: Boolean = false): File = withContext(Dispatchers.IO) {
    localFile(url)?.let { return@withContext it }
    require(::context.isInitialized) { "App context is unavailable" }
    if (!url.startsWith("cloudinary:")) {
      val id = Integer.toHexString(url.hashCode())
      val dir = File(context.cacheDir, "private-media").apply { mkdirs() }
      val cached = File(dir, "$id.bin")
      if (cached.exists() && cached.length() > 0 && !forceRefresh) return@withContext cached
      if (forceRefresh) cached.delete()
      val response = http.newCall(Request.Builder().url(url).get().build()).execute()
      response.use { r ->
        check(r.isSuccessful) { "Media download failed (${r.code})" }
        val temp = File(dir, "$id.tmp")
        temp.outputStream().use { output -> r.body?.byteStream()?.use { input -> input.copyTo(output) } }
        check(temp.length() > 0) { "Downloaded media is empty" }
        if (cached.exists()) cached.delete()
        check(temp.renameTo(cached)) { "Unable to cache media" }
      }
      return@withContext cached
    }

    val fileId = url.removePrefix("cloudinary:")
    val dir = File(context.cacheDir, "private-media").apply { mkdirs() }
    val cached = File(dir, "$fileId.bin")
    if (cached.exists() && cached.length() > 0 && !forceRefresh) return@withContext cached
    if (forceRefresh) cached.delete()

    val resolved = resolve(url, forceRefresh = forceRefresh)
    val response = http.newCall(Request.Builder().url(resolved).get().build()).execute()
    response.use { responseValue ->
      check(responseValue.isSuccessful) { "Media download failed (${responseValue.code})" }
      val encryptedTemp = File(dir, "$fileId.download")
      encryptedTemp.outputStream().use { output ->
        responseValue.body?.byteStream()?.use { input -> input.copyTo(output) }
      }
      check(encryptedTemp.length() > 0) { "Downloaded media is empty" }

      val secret = mediaSecrets[fileId]
      if (secret != null) {
        val plainTemp = File(dir, "$fileId.plain.tmp")
        try {
          E2eeCrypto.decryptMediaFile(
            input = encryptedTemp,
            output = plainTemp,
            fileId = fileId,
            secret = secret
          )
          encryptedTemp.delete()
          if (cached.exists()) cached.delete()
          check(plainTemp.renameTo(cached)) { "Unable to cache decrypted media" }
        } catch (t: Throwable) {
          plainTemp.delete()
          encryptedTemp.delete()
          throw IllegalStateException("Encrypted media could not be decrypted", t)
        }
      } else {
        if (cached.exists()) cached.delete()
        check(encryptedTemp.renameTo(cached)) { "Unable to cache media" }
      }
    }
    cached
  }

  suspend fun upload(
    uri: Uri,
    conversationId: String?,
    progress: (Float) -> Unit = {}
  ): String = withContext(Dispatchers.IO) {
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
    if (extension == "aac") mime = "audio/aac"
    if (extension == "m4a" && !mime.startsWith("audio/")) mime = "audio/mp4"

    val file = File.createTempFile("upload-", ".bin", context.cacheDir)
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
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 86, it) }
        bitmap.recycle()
        mime = "image/jpeg"
      }

      require(file.length() > 0) { "Empty file" }
      val encryptedChatMedia = conversationId != null
      val begin = call("uploadBegin", mapOf(
        "conversationId" to conversationId,
        "encrypted" to encryptedChatMedia,
        "originalMime" to mime
      ))
      val id = begin.getString("fileId")
      val uploadUrl = begin.getString("uploadUrl")
      val apiKey = begin.getString("apiKey")
      val timestamp = begin.getLong("timestamp").toString()
      val signature = begin.getString("signature")
      val publicId = begin.getString("publicId")
      val deliveryType = begin.optString("deliveryType", "authenticated")

      val encryptedFile = if (encryptedChatMedia) File.createTempFile("upload-e2ee-", ".bin", context.cacheDir) else null
      val uploadFile: File
      val uploadMime: String
      val filename: String

      if (encryptedFile != null) {
        val secret = E2eeCrypto.encryptMediaFile(file, encryptedFile, id)
        mediaSecrets[id] = secret
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: error("Please sign in again")
        E2eeCrypto.storePendingMediaSecret(context, uid, id, secret)
        uploadFile = encryptedFile
        uploadMime = "application/octet-stream"
        filename = "$id.bin"
      } else {
        uploadFile = file
        uploadMime = mime
        filename = if (mime.startsWith("image/")) "$id.jpg" else "$id.bin"
      }

      try {
        progress(0f)
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
          .addFormDataPart("api_key", apiKey)
          .addFormDataPart("timestamp", timestamp)
          .addFormDataPart("signature", signature)
          .addFormDataPart("public_id", publicId)
          .addFormDataPart("type", deliveryType)
          .addFormDataPart("file", filename, uploadFile.readBytes().toRequestBody(uploadMime.toMediaType()))
          .build()
        val response = http.newCall(Request.Builder().url(uploadUrl).post(body).build()).execute()
        val uploaded = response.use { value ->
          val raw = value.body?.string().orEmpty()
          val json = runCatching { JSONObject(raw.ifBlank { "{}" }) }.getOrElse { JSONObject() }
          check(value.isSuccessful) { json.optJSONObject("error")?.optString("message") ?: "Cloudinary upload failed (${value.code})" }
          json
        }
        progress(1f)
        call("uploadFinish", mapOf(
          "fileId" to id,
          "publicId" to uploaded.optString("public_id"),
          "resourceType" to uploaded.optString("resource_type"),
          "version" to uploaded.optLong("version", 0L)
        )).getString("url")
      } finally {
        encryptedFile?.delete()
      }
    } finally {
      file.delete()
    }
  }
  suspend fun resolve(url: String, forceRefresh: Boolean = false): String {
    if (!url.startsWith("cloudinary:")) return url
    val fileId = url.removePrefix("cloudinary:")
    val now = System.currentTimeMillis()
    if (!forceRefresh) {
      resolvedMedia[fileId]?.takeIf { it.validUntil > now + 15_000 }?.let { return it.url }
    }
    val access = call("mediaAccess", mapOf("fileId" to fileId))
    val resolved = access.getString("url")
    val validFor = access.optLong("validForMs", 45_000L).coerceIn(15_000L, 5 * 60_000L)
    resolvedMedia[fileId] = ResolvedMedia(resolved, now + validFor)
    return resolved
  }
}

