package com.example.ui.components

import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.TextSecondary

@Composable
fun VoiceWaveformPlayer(
  durationSeconds: Int,
  mediaUrl: String = "",
  modifier: Modifier = Modifier,
  isOutgoing: Boolean = false
) {
  val context = LocalContext.current
  var isPlaying by remember(mediaUrl) { mutableStateOf(false) }
  var isPrepared by remember(mediaUrl) { mutableStateOf(false) }
  var resolvedDurationSeconds by remember(mediaUrl, durationSeconds) {
    mutableStateOf(durationSeconds.coerceAtLeast(1))
  }
  val mediaPlayer = remember(mediaUrl) { if (mediaUrl.isBlank()) null else MediaPlayer() }

  DisposableEffect(mediaPlayer, mediaUrl) {
    if (mediaPlayer != null && mediaUrl.isNotBlank()) {
      runCatching {
        mediaPlayer.setDataSource(context, Uri.parse(mediaUrl))
        mediaPlayer.setOnPreparedListener { player ->
          isPrepared = true
          val actualSeconds = (player.duration / 1000f).toInt().coerceAtLeast(1)
          resolvedDurationSeconds = actualSeconds
          if (isPlaying) {
            runCatching { player.start() }.onFailure { isPlaying = false }
          }
        }
        mediaPlayer.setOnCompletionListener {
          isPlaying = false
          runCatching { it.seekTo(0) }
        }
        mediaPlayer.setOnErrorListener { _, _, _ ->
          isPlaying = false
          isPrepared = false
          true
        }
        mediaPlayer.prepareAsync()
      }.onFailure {
        isPlaying = false
        isPrepared = false
        runCatching { mediaPlayer.release() }
      }
    }
    onDispose {
      runCatching { mediaPlayer?.stop() }
      runCatching { mediaPlayer?.release() }
      isPlaying = false
      isPrepared = false
    }
  }

  val infiniteTransition = rememberInfiniteTransition(label = "waveform_anim")
  val pulse by infiniteTransition.animateFloat(
    initialValue = 0.3f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "wave_pulse"
  )

  val barRatios = remember { listOf(0.3f, 0.6f, 0.9f, 0.4f, 0.7f, 1f, 0.5f, 0.8f, 0.4f, 0.7f, 0.9f, 0.5f, 0.3f, 0.7f, 0.4f, 0.8f, 0.6f, 0.3f) }

  Row(
    modifier = modifier
      .width(220.dp)
      .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(36.dp)
        .clip(CircleShape)
        .background(if (isOutgoing) Color.White.copy(alpha = 0.25f) else CyanAccent.copy(alpha = 0.25f))
        .clickable(enabled = mediaPlayer != null) {
          if (mediaPlayer != null) {
            if (isPlaying) {
              runCatching { if (isPrepared) mediaPlayer.pause() }
              isPlaying = false
            } else {
              // If preparation is still in progress, mark playback as requested;
              // the prepared listener will start it as soon as the stream is ready.
              isPlaying = true
              if (isPrepared) {
                runCatching { mediaPlayer.start() }.onFailure { isPlaying = false }
              }
            }
          }
        },
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
        contentDescription = if (isPlaying) "Pause voice message" else "Play voice message",
        tint = if (isOutgoing) Color.White else CyanAccent,
        modifier = Modifier.size(20.dp)
      )
    }

    Spacer(modifier = Modifier.width(10.dp))

    Row(
      modifier = Modifier
        .weight(1f)
        .height(28.dp),
      horizontalArrangement = Arrangement.spacedBy(3.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      barRatios.forEachIndexed { index, ratio ->
        val heightMultiplier = if (isPlaying) {
          ((ratio * pulse + (index % 3) * 0.2f)).coerceIn(0.2f, 1f)
        } else ratio
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight(heightMultiplier)
            .clip(RoundedCornerShape(2.dp))
            .background(if (isOutgoing) Color.White.copy(alpha = if (index < barRatios.size / 2) 0.9f else 0.45f) else CyanAccent.copy(alpha = if (index < barRatios.size / 2) 0.9f else 0.4f))
        )
      }
    }

    Spacer(modifier = Modifier.width(8.dp))
    val minutes = resolvedDurationSeconds / 60
    val seconds = resolvedDurationSeconds % 60
    Text(
      text = String.format("%d:%02d", minutes, seconds),
      fontSize = 11.sp,
      color = if (isOutgoing) Color.White.copy(alpha = 0.8f) else TextSecondary
    )
  }
}
