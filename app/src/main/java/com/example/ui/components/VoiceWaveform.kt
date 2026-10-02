package com.example.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.AudioFocusRequest
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.data.network.LiquidApi
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin

/**
 * Small process-local coordinator so two visible voice bubbles never play over each other.
 * MediaPlayer audio focus is still handled by Android, but this guarantees Liquid Chat itself
 * has a single active voice message at a time.
 */
private object VoicePlaybackBus {
  val activeKey = mutableStateOf<String?>(null)

  fun activate(key: String) {
    activeKey.value = key
  }

  fun release(key: String) {
    if (activeKey.value == key) activeKey.value = null
  }
}

@Composable
fun VoiceWaveformPlayer(
  durationSeconds: Int,
  mediaUrl: String = "",
  waveform: List<Float> = emptyList(),
  modifier: Modifier = Modifier,
  isOutgoing: Boolean = false
) {
  val context = LocalContext.current
  val ownerKey = remember(mediaUrl) { java.util.UUID.randomUUID().toString() }
  val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
  var retry by remember(mediaUrl) { mutableIntStateOf(0) }
  val player = remember(mediaUrl, retry) {
    MediaPlayer().apply {
      setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
          .build()
      )
    }
  }
  var ready by remember(mediaUrl, retry) { mutableStateOf(false) }
  var playing by remember(mediaUrl, retry) { mutableStateOf(false) }
  var pos by remember(mediaUrl, retry) { mutableFloatStateOf(0f) }
  var duration by remember(mediaUrl, retry) {
    mutableIntStateOf(durationSeconds.coerceAtLeast(1) * 1000)
  }
  var error by remember(mediaUrl, retry) { mutableStateOf(false) }
  var speedIndex by remember(mediaUrl) { mutableIntStateOf(0) }
  val activeKey by VoicePlaybackBus.activeKey
  val speeds = remember { listOf(1f, 1.5f, 2f) }
  val speed = speeds[speedIndex]

  val focusListener = remember(player) { AudioManager.OnAudioFocusChangeListener { change ->
    if (change != AudioManager.AUDIOFOCUS_GAIN) {
      runCatching { player.pause() }
      playing = false
      VoicePlaybackBus.release(ownerKey)
    }
  } }
  val focusRequest = remember(player) {
    if (Build.VERSION.SDK_INT >= 26) AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
      .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
      .setOnAudioFocusChangeListener(focusListener).setWillPauseWhenDucked(true).build() else null
  }
  fun requestFocus(): Boolean = if (Build.VERSION.SDK_INT >= 26 && focusRequest != null) {
    audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
  } else {
    @Suppress("DEPRECATION")
    audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
  }
  fun abandonFocus() {
    if (Build.VERSION.SDK_INT >= 26 && focusRequest != null) audioManager.abandonAudioFocusRequest(focusRequest)
    else { @Suppress("DEPRECATION") audioManager.abandonAudioFocus(focusListener) }
  }
  val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
  DisposableEffect(player, lifecycleOwner) {
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        runCatching { player.pause() }; playing = false; VoicePlaybackBus.release(ownerKey)
      }
    }
    ContextCompat.registerReceiver(context, receiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
      if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
        runCatching { player.pause() }; playing = false; VoicePlaybackBus.release(ownerKey)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      context.unregisterReceiver(receiver)
      abandonFocus()
    }
  }
  LaunchedEffect(playing) { if (!playing) abandonFocus() }

  val bars = remember(mediaUrl, waveform) {
    val source = if (waveform.isNotEmpty()) waveform else List(40) { index ->
      val seed = abs(mediaUrl.hashCode() % 97) / 97f
      (0.18f + abs(sin(index * .63 + seed * 4.7)).toFloat() * .78f)
    }
    val compact = if (source.size <= 44) source else {
      val step = source.size.toFloat() / 44f
      List(44) { i -> source[(i * step).toInt().coerceIn(source.indices)] }
    }
    val max = compact.maxOrNull()?.coerceAtLeast(.01f) ?: 1f
    compact.map { (it / max).coerceIn(.12f, 1f) }
  }

  LaunchedEffect(mediaUrl, retry) {
    ready = false
    error = false
    playing = false
    pos = 0f
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
        runCatching { it.seekTo(0) }
        VoicePlaybackBus.release(ownerKey)
      }
      player.setOnErrorListener { _, _, _ ->
        error = true
        ready = false
        playing = false
        VoicePlaybackBus.release(ownerKey)
        true
      }
      player.prepareAsync()
    }.onFailure {
      error = true
      ready = false
      playing = false
      VoicePlaybackBus.release(ownerKey)
    }
  }

  LaunchedEffect(activeKey) {
    if (activeKey != ownerKey && playing) {
      runCatching { player.pause() }
      playing = false
    }
  }

  LaunchedEffect(speed, ready) {
    if (ready && Build.VERSION.SDK_INT >= 23) {
      runCatching {
        val wasPlaying = player.isPlaying
        player.playbackParams = player.playbackParams.setSpeed(speed)
        if (wasPlaying && !player.isPlaying) player.start()
        if (!wasPlaying && player.isPlaying) player.pause()
      }
    }
  }

  LaunchedEffect(playing) {
    while (playing) {
      pos = runCatching { player.currentPosition.toFloat() }.getOrDefault(pos)
      delay(80)
    }
  }

  DisposableEffect(player) {
    onDispose {
      VoicePlaybackBus.release(ownerKey)
      runCatching { player.release() }
    }
  }

  val active = if (isOutgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
  val inactive = if (isOutgoing) {
    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .28f)
  } else {
    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .28f)
  }
  val fraction = (pos / duration.coerceAtLeast(1)).coerceIn(0f, 1f)
  val playScale by animateFloatAsState(
    targetValue = if (playing) 1.06f else 1f,
    animationSpec = spring(dampingRatio = .7f, stiffness = 520f),
    label = "voice_play_scale"
  )

  Column(modifier.widthIn(min = 195.dp, max = 285.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
        when {
          !ready && !error -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
          else -> IconButton(
            onClick = {
              if (!ready || error) return@IconButton
              if (playing) {
                runCatching { player.pause() }
                playing = false
                VoicePlaybackBus.release(ownerKey)
              } else {
                if (!requestFocus()) return@IconButton
                VoicePlaybackBus.activate(ownerKey)
                runCatching { player.start() }
                  .onSuccess { playing = true }
                  .onFailure { error = true }
              }
            },
            enabled = ready && !error,
            modifier = Modifier.graphicsLayer { scaleX = playScale; scaleY = playScale }
          ) {
            Icon(
              if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
              if (playing) "Pause voice message" else "Play voice message",
              tint = active
            )
          }
        }
      }

      Box(Modifier.weight(1f).height(44.dp)) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 9.dp)) {
          if (bars.isEmpty()) return@Canvas
          val gap = 2.2.dp.toPx()
          val spacing = size.width / bars.size.coerceAtLeast(1)
          val width = (spacing * .55f).coerceAtLeast(.5f)
          bars.forEachIndexed { index, amp ->
            val x = index * spacing
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
          onValueChangeFinished = { if (ready) runCatching { player.seekTo(pos.toInt()) } },
          valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
          enabled = ready && !error,
          colors = SliderDefaults.colors(
            thumbColor = active.copy(alpha = .92f),
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
        contentPadding = PaddingValues(horizontal = 5.dp)
      ) {
        Text(speedLabel(speed), style = MaterialTheme.typography.labelSmall, color = active)
      }
    }

    when {
      error -> Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          "Unable to play recording",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.weight(1f)
        )
        TextButton(
          onClick = {
            LiquidApi.invalidateMedia(mediaUrl)
            retry++
          },
          contentPadding = PaddingValues(horizontal = 6.dp)
        ) {
          Icon(Icons.Default.Refresh, null, Modifier.size(14.dp))
          Spacer(Modifier.width(3.dp))
          Text("Retry")
        }
      }
      else -> Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          formatVoiceTime(if (playing || pos > 0f) (pos / 1000).toInt() else durationSeconds.coerceAtLeast(0)),
          style = MaterialTheme.typography.labelSmall,
          color = if (isOutgoing) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .78f)
          else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        if (ready) {
          Text(
            formatVoiceTime(duration / 1000),
            style = MaterialTheme.typography.labelSmall,
            color = if (isOutgoing) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .70f)
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .82f)
          )
        }
      }
    }
  }
}

private fun speedLabel(speed: Float): String = when (speed) {
  1f -> "1×"
  1.5f -> "1.5×"
  else -> "2×"
}

private fun formatVoiceTime(seconds: Int): String =
  "${seconds.coerceAtLeast(0) / 60}:${(seconds.coerceAtLeast(0) % 60).toString().padStart(2, '0')}"
