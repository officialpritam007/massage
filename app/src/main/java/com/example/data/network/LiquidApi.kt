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

  private var jwt = ""
  private var jwtUntil = 0L
  private data class ResolvedMedia(val url: String, val validUntil: Long)
  private val resolvedMedia = ConcurrentHashMap<String, ResolvedMedia>()
  private val mediaSecrets = ConcurrentHashMap<String, E2eeCrypto.MediaSecret>()

  fun registerMediaSecret(url: String, secret: E2eeCrypto.MediaSecret) {
    if (!url.startsWith("appwrite:")) return
    mediaSecrets[url.removePrefix("appwrite:")] = secret
  }

  fun mediaSecret(url: String): E2eeCrypto.MediaSecret? {
    if (!url.startsWith("appwrite:")) return null
    return mediaSecrets[url.removePrefix("appwrite:")]
  }


  suspend fun call(action: String, data: Map<String, Any?> = emptyMap()): JSONObject = withContext(Dispatchers.IO) {
    check(BuildConfig.LIQUID_API_URL.startsWith("https://")) {
      "Backend setup required: configure LIQUID_API_URL in GitHub Actions."
    }
    val user = FirebaseAuth.getInstance().currentUser ?: error("Please sign in again")
    var token = user.getIdToken(false).await().token ?: error("Please sign in again")
    val payloadJson = JSONObject(data).put("action", action).toString()

    fun directRequest(idToken: String): Response {
      return http.newCall(
        Request.Builder()
          .url(BuildConfig.LIQUID_API_URL)
          .header("Authorization", "Bearer $idToken")
          .post(payloadJson.toRequestBody("application/json".toMediaType()))
          .build()
      ).execute()
    }

    fun parseDirect(response: Response): JSONObject {
      response.use { responseValue ->
        val raw = responseValue.body?.string().orEmpty()
        val json = runCatching { JSONObject(raw.ifBlank { "{}" }) }.getOrElse {
          JSONObject().put("error", "Invalid backend response (${responseValue.code})")
        }
        check(responseValue.isSuccessful) {
          json.optString("error", "Request failed (${responseValue.code})")
        }
        return json
      }
    }

    fun executeThroughAppwrite(idToken: String): JSONObject {
      val executionRequest = JSONObject()
        .put("body", payloadJson)
        .put("async", false)
        .put("path", "/")
        .put("method", "POST")
        .put(
          "headers",
          JSONObject()
            .put("Authorization", "Bearer $idToken")
            .put("content-type", "application/json")
        )

      val endpoint = BuildConfig.APPWRITE_ENDPOINT.trimEnd('/') +
        "/functions/firebase-appwrite-bridge/executions"
      http.newCall(
        Request.Builder()
          .url(endpoint)
          .header("X-Appwrite-Project", BuildConfig.APPWRITE_PROJECT_ID)
          .header("Content-Type", "application/json")
          .post(executionRequest.toString().toRequestBody("application/json".toMediaType()))
          .build()
      ).execute().use { responseValue ->
        val raw = responseValue.body?.string().orEmpty()
        val envelope = runCatching { JSONObject(raw.ifBlank { "{}" }) }.getOrElse {
          JSONObject().put("message", "Invalid Appwrite response (${responseValue.code})")
        }
        check(responseValue.isSuccessful) {
          envelope.optString("message", "Backend fallback failed (${responseValue.code})")
        }

        val backendStatus = envelope.optInt("responseStatusCode", 0)
        val backendRaw = envelope.optString("responseBody", "")
        val backendJson = runCatching {
          JSONObject(backendRaw.ifBlank { "{}" })
        }.getOrElse {
          JSONObject().put("error", "Invalid backend response ($backendStatus)")
        }
        check(backendStatus in 200..299) {
          backendJson.optString(
            "error",
            if (backendStatus > 0) "Request failed ($backendStatus)" else "Backend execution failed"
          )
        }
        return backendJson
      }
    }

    var response = directRequest(token)
    if (response.code == 401) {
      response.close()
      token = user.getIdToken(true).await().token ?: error("Please sign in again")
      response = directRequest(token)
    }

    if (response.code == 402) {
      response.close()
      return@withContext executeThroughAppwrite(token)
    }

    parseDirect(response)
  }

  private suspend fun session(): String {
    if (System.currentTimeMillis() >= jwtUntil || jwt.isBlank()) {
      jwt = call("session").getString("jwt")
      jwtUntil = System.currentTimeMillis() + 8 * 60_000
    }
    return jwt
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

  fun clear() {
    jwt = ""
    jwtUntil = 0L
    clearMediaCachesOnly()
  }

  fun invalidateMedia(url: String, purgeCaches: Boolean = true) {
    if (url.startsWith("appwrite:")) {
      val fileId = url.removePrefix("appwrite:")
      resolvedMedia.remove(fileId)
      mediaSecrets.remove(fileId)
      if (::context.isInitialized) File(File(context.cacheDir, "private-media"), "$fileId.bin").delete()
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
    val id = if (url.startsWith("appwrite:")) url.removePrefix("appwrite:") else Integer.toHexString(url.hashCode())
    val cached = File(File(context.cacheDir, "private-media"), "$id.bin")
    return cached.takeIf { it.exists() && it.length() > 0 }
  }

  suspend fun cachedPrivateMedia(url: String, forceRefresh: Boolean = false): File = withContext(Dispatchers.IO) {
    localFile(url)?.let { return@withContext it }
    require(::context.isInitialized) { "App context is unavailable" }
    if (!url.startsWith("appwrite:")) {
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

    val fileId = url.removePrefix("appwrite:")
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

    // File Uris produced by MediaRecorder are not always classified consistently by OEMs.
    // Prefer the actual recording extension so AAC voice notes remain audio/* in Appwrite.
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
            require(total <= 25L * 1024 * 1024) { "Maximum attachment size is 25 MB" }
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
      val begin = call(
        "uploadBegin",
        mapOf(
          "conversationId" to conversationId,
          "encrypted" to encryptedChatMedia,
          "originalMime" to mime
        )
      )
      val id = begin.getString("fileId")
      val owner = begin.getString("ownerId")
      val auth = session()

      val encryptedFile = if (encryptedChatMedia) {
        File.createTempFile("upload-e2ee-", ".bin", context.cacheDir)
      } else {
        null
      }
      val uploadFile: File
      val uploadMime: String
      val suffix: String

      if (encryptedFile != null) {
        val secret = E2eeCrypto.encryptMediaFile(file, encryptedFile, id)
        mediaSecrets[id] = secret
        uploadFile = encryptedFile
        uploadMime = "application/octet-stream"
        suffix = "bin"
      } else {
        uploadFile = file
        uploadMime = mime
        suffix = when {
          mime.startsWith("image/") -> "jpg"
          mime == "audio/aac" -> "aac"
          mime.startsWith("audio/") -> "m4a"
          mime.startsWith("video/") -> "mp4"
          else -> "bin"
        }
      }

      try {
        var offset = 0L
        uploadFile.inputStream().use { input ->
          while (offset < uploadFile.length()) {
            kotlinx.coroutines.currentCoroutineContext().ensureActiveCompat()
            val bytes = ByteArray(
              minOf(5L * 1024 * 1024, uploadFile.length() - offset).toInt()
            )
            var read = 0
            while (read < bytes.size) {
              val n = input.read(bytes, read, bytes.size - read)
              if (n < 0) break
              read += n
            }
            require(read > 0) { "Upload stream ended unexpectedly" }
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
              .addFormDataPart("fileId", id)
              .addFormDataPart("permissions[]", "read(\"user:$owner\")")
              .addFormDataPart("permissions[]", "update(\"user:$owner\")")
              .addFormDataPart("permissions[]", "delete(\"user:$owner\")")
              .addFormDataPart(
                "file",
                "$id.$suffix",
                bytes.copyOf(read).toRequestBody(uploadMime.toMediaType())
              )
              .build()
            val request = Request.Builder()
              .url("${BuildConfig.APPWRITE_ENDPOINT}/storage/buckets/${BuildConfig.APPWRITE_BUCKET_ID}/files")
              .header("X-Appwrite-Project", BuildConfig.APPWRITE_PROJECT_ID)
              .header("X-Appwrite-JWT", auth)
              .header(
                "Content-Range",
                "bytes $offset-${offset + read - 1}/${uploadFile.length()}"
              )
            if (offset > 0) request.header("X-Appwrite-ID", id)
            http.newCall(request.post(body).build()).execute().use { responseValue ->
              check(responseValue.isSuccessful) {
                "Upload failed (${responseValue.code}). Check bucket permissions and file limits."
              }
            }
            offset += read
            progress(offset.toFloat() / uploadFile.length())
          }
        }
        call("uploadFinish", mapOf("fileId" to id)).getString("url")
      } finally {
        encryptedFile?.delete()
      }
    } finally {
      file.delete()
    }
  }

  suspend fun resolve(url: String, forceRefresh: Boolean = false): String {
    if (!url.startsWith("appwrite:")) return url
    val fileId = url.removePrefix("appwrite:")
    val now = System.currentTimeMillis()
    if (!forceRefresh) {
      resolvedMedia[fileId]?.takeIf { it.validUntil > now + 15_000 }?.let { return it.url }
    }
    val resolved = call("mediaAccess", mapOf("fileId" to fileId)).getString("url")
    // Server media tokens are intentionally short-lived; refresh before expiry.
    resolvedMedia[fileId] = ResolvedMedia(resolved, now + 45_000)
    return resolved
  }
}

private fun kotlin.coroutines.CoroutineContext.ensureActiveCompat() {
  this[kotlinx.coroutines.Job]?.let {
    if (!it.isActive) throw kotlinx.coroutines.CancellationException()
  }
}
