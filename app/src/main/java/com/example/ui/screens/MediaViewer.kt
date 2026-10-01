package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.data.media.saveMediaToGallery
import com.example.data.model.Message
import com.example.data.model.MessageType
import com.example.data.network.LiquidApi
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassIconButton
import com.example.ui.components.LiquidBackground
import com.example.ui.components.MediaShimmer
import com.example.ui.components.VoiceWaveformPlayer
import com.example.ui.components.rememberLiquidHaptics
import kotlinx.coroutines.launch

@Composable
fun MediaViewer(message: Message, onClose: () -> Unit) {
    var url by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var video by remember { mutableStateOf<VideoView?>(null) }
    var saving by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptics = rememberLiquidHaptics()
    val scope = rememberCoroutineScope()

    LaunchedEffect(message.mediaUrl) {
        runCatching { LiquidApi.resolve(message.mediaUrl) }
            .onSuccess { url = it }
            .onFailure { error = it.message }
    }
    DisposableEffect(Unit) { onDispose { video?.stopPlayback() } }

    fun save() {
        if (saving) return
        saving = true
        haptics.confirm()
        scope.launch {
            saveMediaToGallery(context, message)
                .onSuccess { Toast.makeText(context, "Saved to gallery", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, it.message ?: "Save failed", Toast.LENGTH_LONG).show() }
            saving = false
        }
    }

    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        LiquidBackground(crystal = false, scrim = true) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = .52f))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // One floating glass bar instead of three separate oversized objects.
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    elevation = 8.dp
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Close media viewer", onClose, size = 40.dp)
                        Spacer(Modifier.size(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                when (message.type) {
                                    MessageType.IMAGE -> "Photo"
                                    MessageType.VIDEO -> "Video"
                                    MessageType.VOICE -> "Voice message"
                                    else -> "Attachment"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Private • stored in your Liquid Chat cache",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        GlassIconButton(Icons.Default.Download, "Save to gallery", { save() }, size = 40.dp)
                        Spacer(Modifier.size(6.dp))
                        GlassIconButton(
                            Icons.Default.OpenInNew,
                            "Open with another app",
                            {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                    .onFailure { error = "No app available to open this attachment" }
                            },
                            size = 40.dp
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                GlassCard(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    shape = RoundedCornerShape(30.dp),
                    backgroundColor = Color.Black.copy(alpha = .40f),
                    elevation = 2.dp
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        when {
                            error != null -> Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(error.orEmpty(), color = Color.White, modifier = Modifier.padding(horizontal = 24.dp))
                                GlassButton("Try again", {
                                    error = null
                                    scope.launch {
                                        runCatching { LiquidApi.resolve(message.mediaUrl, forceRefresh = true) }
                                            .onSuccess { url = it }
                                            .onFailure { error = it.message }
                                    }
                                }, isPrimary = false)
                            }
                            url == null -> MediaShimmer(Modifier.fillMaxSize())
                            message.type == MessageType.IMAGE -> {
                                val state = rememberTransformableState { zoom, pan, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                    offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
                                }
                                AsyncImage(
                                    model = url,
                                    contentDescription = "Photo",
                                    modifier = Modifier.fillMaxSize().transformable(state).graphicsLayer {
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
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                        .onFailure { error = "No app available to open this attachment" }
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
                                    Text("Saving to gallery…", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
