package com.example.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.Message
import com.example.data.model.MessageType
import com.example.data.network.LiquidApi
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassIconButton
import com.example.ui.components.LiquidBackground
import com.example.ui.components.VoiceWaveformPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private suspend fun savePrivateMediaToDevice(context: Context, message: Message): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        require(message.mediaUrl.isNotBlank()) { "No media attached" }
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Save to device requires Android 10 or newer"
        }

        val source: File = LiquidApi.cachedPrivateMedia(message.mediaUrl)
        val now = System.currentTimeMillis()
        val (collection, mime, folder, extension) = when (message.type) {
            MessageType.IMAGE -> Quad(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "image/jpeg",
                "Pictures/Liquid Chat",
                "jpg"
            )
            MessageType.VIDEO -> Quad(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                "video/mp4",
                "Movies/Liquid Chat",
                "mp4"
            )
            MessageType.VOICE -> Quad(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "audio/mp4",
                "Download/Liquid Chat",
                "m4a"
            )
            else -> Quad(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "application/octet-stream",
                "Download/Liquid Chat",
                "bin"
            )
        }

        val displayName = "LiquidChat_${now}.${extension}"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val destination = requireNotNull(resolver.insert(collection, values)) {
            "Unable to create media file"
        }
        try {
            resolver.openOutputStream(destination)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Unable to open media destination")
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

private data class Quad(
    val collection: Uri,
    val mime: String,
    val folder: String,
    val extension: String
)

@Composable
fun MediaViewer(message: Message, onClose: () -> Unit) {
    var url by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var video by remember { mutableStateOf<VideoView?>(null) }
    var saving by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(message.mediaUrl) {
        runCatching { LiquidApi.resolve(message.mediaUrl) }
            .onSuccess { url = it }
            .onFailure { error = it.message }
    }
    DisposableEffect(Unit) { onDispose { video?.stopPlayback() } }

    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        LiquidBackground(crystal = false) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = .58f))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassIconButton(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        "Close media viewer",
                        onClose
                    )
                    GlassCard(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Column(Modifier.padding(horizontal = 15.dp, vertical = 10.dp)) {
                            Text("Media", style = MaterialTheme.typography.titleMedium)
                            Text(
                                when (message.type) {
                                    MessageType.IMAGE -> "Private photo"
                                    MessageType.VIDEO -> "Private video"
                                    MessageType.VOICE -> "Voice message"
                                    else -> "Private attachment"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    GlassIconButton(
                        Icons.Default.Download,
                        "Save to device",
                        onClick = {
                            if (!saving) {
                                saving = true
                                scope.launch {
                                    savePrivateMediaToDevice(context, message)
                                        .onSuccess {
                                            Toast.makeText(context, "Saved to device", Toast.LENGTH_SHORT).show()
                                        }
                                        .onFailure {
                                            Toast.makeText(
                                                context,
                                                it.message ?: "Save failed",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    saving = false
                                }
                            }
                        }
                    )
                }

                Spacer(Modifier.height(10.dp))
                GlassCard(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    backgroundColor = Color.Black.copy(alpha = .38f),
                    elevation = 2.dp
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        when {
                            error != null -> {
                                Text(
                                    error.orEmpty(),
                                    color = Color.White,
                                    modifier = Modifier.padding(24.dp)
                                )
                            }
                            url == null -> CircularProgressIndicator()
                            message.type == MessageType.IMAGE -> {
                                val state = rememberTransformableState { zoom, pan, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                    offset = if (scale == 1f) {
                                        androidx.compose.ui.geometry.Offset.Zero
                                    } else {
                                        offset + pan
                                    }
                                }
                                AsyncImage(
                                    model = url,
                                    contentDescription = "Photo",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .transformable(state)
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                            translationX = offset.x
                                            translationY = offset.y
                                        },
                                    contentScale = ContentScale.Fit
                                )
                            }
                            message.type == MessageType.VIDEO -> AndroidView(
                                factory = { ctx ->
                                    VideoView(ctx).apply {
                                        video = this
                                        setVideoURI(Uri.parse(url))
                                        setMediaController(MediaController(ctx).also { it.setAnchorView(this) })
                                        setOnPreparedListener { start() }
                                        setOnErrorListener { _, _, _ ->
                                            error = "Video playback failed"
                                            true
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            message.type == MessageType.VOICE -> VoiceWaveformPlayer(
                                message.voiceDurationSeconds,
                                message.mediaUrl,
                                waveform = message.waveform
                            )
                            else -> GlassButton(
                                "Open attachment",
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }.onFailure {
                                        error = "No app available to open this attachment"
                                    }
                                }
                            )
                        }
                        if (saving) {
                            GlassCard(shape = RoundedCornerShape(999.dp)) {
                                Row(
                                    Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text("Saving…", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
