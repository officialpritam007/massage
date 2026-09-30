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
import java.util.concurrent.TimeUnit

object LiquidApi {
  lateinit var context: Context
  private val http = OkHttpClient.Builder().callTimeout(90, TimeUnit.SECONDS).build()
  private var jwt = ""
  private var jwtUntil = 0L
  suspend fun call(action: String, data: Map<String, Any?> = emptyMap()): JSONObject = withContext(Dispatchers.IO) {
    check(BuildConfig.LIQUID_API_URL.startsWith("https://")) { "Backend setup required: configure LIQUID_API_URL in GitHub Actions." }
    val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token ?: error("Please sign in again")
    val payload = JSONObject(data).put("action", action).toString().toRequestBody("application/json".toMediaType())
    val request = Request.Builder().url(BuildConfig.LIQUID_API_URL).header("Authorization", "Bearer $token").post(payload).build()
    http.newCall(request).execute().use { r ->
      val json = JSONObject(r.body?.string().orEmpty().ifBlank { "{}" })
      check(r.isSuccessful) { json.optString("error", "Request failed (${r.code})") }
      json
    }
  }
  private suspend fun session(): String {
    if (System.currentTimeMillis() >= jwtUntil) {
      jwt = call("session").getString("jwt")
      jwtUntil = System.currentTimeMillis() + 10 * 60_000
    }
    return jwt
  }
  fun clear() { jwt = ""; jwtUntil = 0; if (::context.isInitialized) File(context.cacheDir,"private-media").deleteRecursively(); if (::context.isInitialized) { coil.Coil.imageLoader(context).memoryCache?.clear(); coil.Coil.imageLoader(context).diskCache?.clear() } }
  suspend fun upload(uri: Uri, conversationId: String?, progress: (Float) -> Unit = {}): String = withContext(Dispatchers.IO) {
    var mime=context.contentResolver.getType(uri) ?: when(uri.toString().substringAfterLast('.').lowercase()) { "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "m4a" -> "audio/mp4"; "mp4" -> "video/mp4"; else -> "application/octet-stream" }
    val file = File.createTempFile("upload-", ".bin", context.cacheDir)
    try {
      context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "Unable to open this file" }
        file.outputStream().use { output ->
          val buffer = ByteArray(65536); var total = 0L
          while (true) { val n=input.read(buffer); if(n<0) break; total+=n; require(total<=25L*1024*1024) { "Maximum attachment size is 25 MB" };output.write(buffer,0,n) }
        }
      }
      if(mime.startsWith("image/") && android.os.Build.VERSION.SDK_INT>=28) {
        val bitmap=android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(file)) { decoder, info, _ ->
          decoder.allocator=android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
          val ratio=(maxOf(info.size.width,info.size.height).toFloat()/1600f).coerceAtLeast(1f)
          decoder.setTargetSize((info.size.width/ratio).toInt().coerceAtLeast(1),(info.size.height/ratio).toInt().coerceAtLeast(1))
        }
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,86,it) }
        bitmap.recycle();mime="image/jpeg"
      }
      require(file.length()>0) { "Empty file" }
      val begin=call("uploadBegin",mapOf("conversationId" to conversationId))
      val id=begin.getString("fileId"); val owner=begin.getString("ownerId"); val auth=session()

      val suffix=when { mime.startsWith("image/")->"jpg";mime.startsWith("video/")->"mp4";mime.startsWith("audio/")->"m4a";else->"bin" }
      var offset=0L
      file.inputStream().use { input ->
        while(offset<file.length()) {
          kotlinx.coroutines.currentCoroutineContext().ensureActiveCompat()
          val bytes=ByteArray(minOf(5L*1024*1024,file.length()-offset).toInt());var read=0
          while(read<bytes.size){val n=input.read(bytes,read,bytes.size-read);if(n<0)break;read+=n}
          val body=MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("fileId",id)
            .addFormDataPart("permissions[]","read(\"user:$owner\")")
            .addFormDataPart("permissions[]","update(\"user:$owner\")")
            .addFormDataPart("permissions[]","delete(\"user:$owner\")")
            .addFormDataPart("file","$id.$suffix",bytes.toRequestBody(mime.toMediaType())).build()
          val request=Request.Builder().url("${BuildConfig.APPWRITE_ENDPOINT}/storage/buckets/${BuildConfig.APPWRITE_BUCKET_ID}/files")
            .header("X-Appwrite-Project",BuildConfig.APPWRITE_PROJECT_ID).header("X-Appwrite-JWT",auth)
            .header("Content-Range","bytes $offset-${offset+read-1}/${file.length()}")
          if(offset>0) request.header("X-Appwrite-ID",id)
          http.newCall(request.post(body).build()).execute().use { r -> check(r.isSuccessful) { "Upload failed (${r.code}). Check bucket permissions and file limits." } }
          offset+=read;progress(offset.toFloat()/file.length())
        }
      }
      call("uploadFinish",mapOf("fileId" to id)).getString("url")
    } finally { file.delete() }
  }
  suspend fun resolve(url: String): String {
    if (!url.startsWith("appwrite:")) return url
    return call("mediaAccess",mapOf("fileId" to url.removePrefix("appwrite:"))).getString("url")
  }
}
private fun kotlin.coroutines.CoroutineContext.ensureActiveCompat() { this[kotlinx.coroutines.Job]?.let { if (!it.isActive) throw kotlinx.coroutines.CancellationException() } }
