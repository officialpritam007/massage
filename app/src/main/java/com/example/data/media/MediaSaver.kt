package com.example.data.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.data.model.Message
import com.example.data.model.MessageType
import com.example.data.network.LiquidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Saves Appwrite-backed private media into the device gallery/Downloads through MediaStore.
 *
 * Nothing leaves the app cache until the user asks for it (or enables auto-save), and the
 * private Appwrite copy is never turned into a public Firebase Storage object.
 */
suspend fun saveMediaToGallery(context: Context, message: Message): Result<String> =
  withContext(Dispatchers.IO) {
    runCatching {
      require(message.mediaUrl.isNotBlank()) { "No media attached" }
      require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        "Save to gallery needs Android 10 or newer"
      }

      val source: java.io.File = LiquidApi.cachedPrivateMedia(message.mediaUrl)
      val now = System.currentTimeMillis()
      val target = when (message.type) {
        MessageType.IMAGE -> SaveTarget(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/jpeg", "Pictures/Liquid Chat", "jpg")
        MessageType.VIDEO -> SaveTarget(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video/mp4", "Movies/Liquid Chat", "mp4")
        MessageType.VOICE -> SaveTarget(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "audio/mp4", "Download/Liquid Chat", "m4a")
        else -> SaveTarget(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "application/octet-stream", "Download/Liquid Chat", "bin")
      }

      val displayName = "LiquidChat_${now}.${target.extension}"
      val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, target.mime)
        put(MediaStore.MediaColumns.RELATIVE_PATH, target.folder)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
      }
      val resolver = context.contentResolver
      val destination = requireNotNull(resolver.insert(target.collection, values)) {
        "Unable to create a gallery file"
      }
      try {
        resolver.openOutputStream(destination)?.use { output ->
          source.inputStream().use { input -> input.copyTo(output) }
        } ?: error("Unable to open the gallery destination")
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(destination, values, null, null)
      } catch (t: Throwable) {
        resolver.delete(destination, null, null)
        throw t
      }
      displayName
    }
  }

private data class SaveTarget(
  val collection: Uri,
  val mime: String,
  val folder: String,
  val extension: String
)
