package com.example.ui.components

import android.media.MediaPlayer
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.network.LiquidApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.abs
import kotlin.math.sin

private val activeVoicePlayer = MutableStateFlow<MediaPlayer?>(null)

@Composable
fun VoiceWaveformPlayer(
  durationSeconds: Int,
  mediaUrl: String = "",
  waveform: List<Float> = emptyList(),
  modifier: Modifier = Modifier,
  isOutgoing: Boolean = false
) {
  var retry by remember(mediaUrl) { mutableIntStateOf(0) }
  val player = remember(mediaUrl, retry) { MediaPlayer() }
  var ready by remember(mediaUrl, retry) { mutableStateOf(false) }
  var playing by remember(mediaUrl, retry) { mutableStateOf(false) }
  var pos by remember(mediaUrl, retry) { mutableFloatStateOf(0f) }
  var duration by remember(mediaUrl, retry) {
    mutableIntStateOf(durationSeconds.coerceAtLeast(1) * 1000)
  }
  var error by remember(mediaUrl, retry) { mutableStateOf(false) }
  var speedIndex by remember(mediaUrl) { mutableIntStateOf(0) }
  val speeds = remember { listOf(1f, 1.5f, 2f) }
  val speed = speeds[speedIndex]

  val bars = remember(mediaUrl, waveform) {
    val source = if (waveform.isNotEmpty()) waveform else List(38) { index ->
      val seed = abs(mediaUrl.hashCode() % 97) / 97f
      (0.18f + abs(sin(index * .63 + seed * 4.7)).toFloat() * .78f)
    }
    val compact = if (source.size <= 42) source else {
      val step = source.size.toFloat() / 42f
      List(42) { i -> source[(i * step).toInt().coerceIn(source.indices)] }
    }
    val max = compact.maxOrNull()?.coerceAtLeast(.01f) ?: 1f
    compact.map { (it / max).coerceIn(.12f, 1f) }
  }

  LaunchedEffect(mediaUrl, retry) {
    if (mediaUrl.isBlank()) {
      error = true
      return@LaunchedEffect
    }
    runCatching {
      val file = LiquidApi.cachedPrivateMedia(mediaUrl, forceRefresh = retry > 0)
      player.setDataSource(file.absolutePath)
      player.setOnPreparedListener {
        duration = it.duration.coerceAtLeast(1)
        ready = true
      }
      player.setOnCompletionListener {
        playing = false
        pos = 0f
        it.seekTo(0)
      }
      player.setOnErrorListener { _, _, _ ->
        error = true
        playing = false
        true
      }
      player.prepareAsync()
    }.onFailure { if (it is CancellationException) throw it; error = true }
  }

  LaunchedEffect(speed, ready) {
    if (ready && Build.VERSION.SDK_INT >= 23) {
      runCatching {
        val wasPlaying = player.isPlaying
        player.playbackParams = player.playbackParams.setSpeed(speed)
        if (!wasPlaying) player.pause()
      }
    }
  }

  LaunchedEffect(playing) {
    while (playing) {
      pos = runCatching { player.currentPosition.toFloat() }.getOrDefault(pos)
      delay(100)
    }
  }

  val activePlayer by activeVoicePlayer.collectAsState()
  LaunchedEffect(activePlayer) {
    if (activePlayer !== player && playing) { runCatching { player.pause() }; playing = false }
  }
  val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
  DisposableEffect(player, lifecycle) {
    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
      if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
        runCatching { if (player.isPlaying) player.pause() }; playing = false
      }
    }
    lifecycle.addObserver(observer)
    onDispose {
      lifecycle.removeObserver(observer)
      if (activeVoicePlayer.value === player) activeVoicePlayer.value = null
      runCatching { player.release() }
    }
  }

  val active = if (isOutgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
  val inactive = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .28f)
  val fraction = (pos / duration.coerceAtLeast(1)).coerceIn(0f, 1f)

  Column(modifier.widthIn(min = 220.dp, max = 285.dp)) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
      IconButton(
        onClick = {
          if (ready && !error) {
            runCatching {
              if (playing) player.pause() else { activeVoicePlayer.value = player; player.start() }
              playing = !playing
            }.onFailure { error = true; playing = false }
          }
        },
        enabled = ready && !error
      ) {
        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause" else "Play")
      }

      Box(Modifier.weight(1f).height(42.dp)) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp)) {
          if (bars.isEmpty()) return@Canvas
          val gap = 3.dp.toPx()
          val width = ((size.width - gap * (bars.size - 1)) / bars.size).coerceAtLeast(2.dp.toPx())
          bars.forEachIndexed { index, amp ->
            val x = index * (width + gap)
            val h = size.height * amp
            val played = index.toFloat() / bars.size <= fraction
            drawLine(
              color = if (played) active else inactive,
              start = Offset(x + width / 2, (size.height - h) / 2),
              end = Offset(x + width / 2, (size.height + h) / 2),
              strokeWidth = width
            )
          }
        }
        Slider(
          value = pos.coerceIn(0f, duration.toFloat()),
          onValueChange = { pos = it },
          onValueChangeFinished = { if (ready) player.seekTo(pos.toInt()) },
          valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
          enabled = ready && !error,
          colors = SliderDefaults.colors(
            thumbColor = active.copy(alpha = .9f),
            activeTrackColor = Color.Transparent,
            inactiveTrackColor = Color.Transparent,
            disabledActiveTrackColor = Color.Transparent,
            disabledInactiveTrackColor = Color.Transparent
          ),
          modifier = Modifier.fillMaxSize()
        )
      }

      TextButton(
        onClick = { speedIndex = (speedIndex + 1) % speeds.size },
        enabled = ready && Build.VERSION.SDK_INT >= 23,
        contentPadding = PaddingValues(horizontal = 6.dp)
      ) {
        Text("${speed}x", style = MaterialTheme.typography.labelSmall)
      }
    }

    when {
      error -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text("Unable to play recording", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        TextButton(onClick = {
          LiquidApi.invalidateMedia(mediaUrl)
          retry++
        }) {
          Icon(Icons.Default.Refresh, null, Modifier.size(15.dp))
          Text("Retry")
        }
      }
      !ready -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(6.dp))
        Text("Loading audio…", style = MaterialTheme.typography.labelSmall)
      }
      else -> Text(
        "${formatVoiceTime((pos / 1000).toInt())} / ${formatVoiceTime(duration / 1000)}",
        style = MaterialTheme.typography.labelSmall
      )
    }
  }
}

private fun formatVoiceTime(seconds: Int): String =
  "${seconds.coerceAtLeast(0) / 60}:${(seconds.coerceAtLeast(0) % 60).toString().padStart(2, '0')}"
