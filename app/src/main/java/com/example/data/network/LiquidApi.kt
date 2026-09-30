package com.example.data.network

import android.content.Context
import android.net.Uri
import com.example.BuildConfig
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

  suspend fun call(action: String, data: Map<String, Any?> = emptyMap()): JSONObject = withContext(Dispatchers.IO) {
    check(BuildConfig.LIQUID_API_URL.startsWith("https://")) {
      "Backend setup required: configure LIQUID_API_URL in GitHub Actions."
    }
    val user = FirebaseAuth.getInstance().currentUser ?: error("Please sign in again")
    var token = user.getIdToken(false).await().token ?: error("Please sign in again")

    fun requestWith(idToken: String): Response {
      val payload = JSONObject(data).put("action", action).toString()
        .toRequestBody("application/json".toMediaType())
      return http.newCall(
        Request.Builder()
          .url(BuildConfig.LIQUID_API_URL)
          .header("Authorization", "Bearer $idToken")
          .post(payload)
          .build()
      ).execute()
    }

    var response = requestWith(token)
    if (response.code == 401) {
      response.close()
      token = user.getIdToken(true).await().token ?: error("Please sign in again")
      response = requestWith(token)
    }
    response.use { r ->
      val json = JSONObject(r.body?.string().orEmpty().ifBlank { "{}" })
      check(r.isSuccessful) { json.optString("error", "Request failed (${r.code})") }
      json
    }
  }

  private suspend fun session(): String {
    if (System.currentTimeMillis() >= jwtUntil) {
      jwt = call("session").getString("jwt")
      jwtUntil = System.currentTimeMillis() + 8 * 60_000
    }
    return jwt
  }

  fun clear() {
    jwt = ""
    jwtUntil = 0
    resolvedMedia.clear()
    if (::context.isInitialized) {
      File(context.cacheDir, "private-media").deleteRecursively()
      coil.Coil.imageLoader(context).memoryCache?.clear()
      coil.Coil.imageLoader(context).diskCache?.clear()
    }
  }

  fun invalidateMedia(url: String, purgeCaches: Boolean = true) {
    if (url.startsWith("appwrite:")) resolvedMedia.remove(url.removePrefix("appwrite:"))
    if (purgeCaches && ::context.isInitialized) {
      // A deleted message must never be resurrected from an already-resolved URL.
      coil.Coil.imageLoader(context).memoryCache?.clear()
      coil.Coil.imageLoader(context).diskCache?.clear()
    }
  }

  suspend fun upload(
    uri: Uri,
    conversationId: String?,
    progress: (Float) -> Unit = {}
  ): String = withContext(Dispatchers.IO) {
    var mime = context.contentResolver.getType(uri) ?: when (uri.toString().substringAfterLast('.').lowercase()) {
      "jpg", "jpeg" -> "image/jpeg"
      "png" -> "image/png"
      "webp" -> "image/webp"
      "m4a" -> "audio/mp4"
      "mp4" -> "video/mp4"
      else -> "application/octet-stream"
    }
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
      val begin = call("uploadBegin", mapOf("conversationId" to conversationId))
      val id = begin.getString("fileId")
      val owner = begin.getString("ownerId")
      val auth = session()
      val suffix = when {
        mime.startsWith("image/") -> "jpg"
        mime.startsWith("video/") -> "mp4"
        mime.startsWith("audio/") -> "m4a"
        else -> "bin"
      }

      var offset = 0L
      file.inputStream().use { input ->
        while (offset < file.length()) {
          kotlinx.coroutines.currentCoroutineContext().ensureActiveCompat()
          val bytes = ByteArray(minOf(5L * 1024 * 1024, file.length() - offset).toInt())
          var read = 0
          while (read < bytes.size) {
            val n = input.read(bytes, read, bytes.size - read)
            if (n < 0) break
            read += n
          }
          val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("fileId", id)
            .addFormDataPart("permissions[]", "read(\"user:$owner\")")
            .addFormDataPart("permissions[]", "update(\"user:$owner\")")
            .addFormDataPart("permissions[]", "delete(\"user:$owner\")")
            .addFormDataPart("file", "$id.$suffix", bytes.copyOf(read).toRequestBody(mime.toMediaType()))
            .build()
          val request = Request.Builder()
            .url("${BuildConfig.APPWRITE_ENDPOINT}/storage/buckets/${BuildConfig.APPWRITE_BUCKET_ID}/files")
            .header("X-Appwrite-Project", BuildConfig.APPWRITE_PROJECT_ID)
            .header("X-Appwrite-JWT", auth)
            .header("Content-Range", "bytes $offset-${offset + read - 1}/${file.length()}")
          if (offset > 0) request.header("X-Appwrite-ID", id)
          http.newCall(request.post(body).build()).execute().use { r ->
            check(r.isSuccessful) { "Upload failed (${r.code}). Check bucket permissions and file limits." }
          }
          offset += read
          progress(offset.toFloat() / file.length())
        }
      }
      call("uploadFinish", mapOf("fileId" to id)).getString("url")
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
    // Server tokens live for five minutes; refresh before the actual expiry boundary.
    resolvedMedia[fileId] = ResolvedMedia(resolved, now + 45_000)
    return resolved
  }
}

private fun kotlin.coroutines.CoroutineContext.ensureActiveCompat() {
  this[kotlinx.coroutines.Job]?.let {
    if (!it.isActive) throw kotlinx.coroutines.CancellationException()
  }
}
