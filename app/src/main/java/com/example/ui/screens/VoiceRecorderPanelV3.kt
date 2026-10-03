package com.example.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassIconButton
import com.example.ui.components.liquidRoundedShape
import com.example.ui.theme.LocalLiquidGlass

@Composable
fun VoiceRecorderPanelV3(
    elapsedSeconds: Int,
    locked: Boolean,
    paused: Boolean,
    waveform: List<Float>,
    reducedMotion: Boolean,
    onPauseResume: () -> Unit,
    onDiscard: () -> Unit,
    onStopPreview: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalLiquidGlass.current
    val pulse = if (reducedMotion || paused) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "voice_record_pulse")
        val value by transition.animateFloat(
            initialValue = .72f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(520),
                repeatMode = RepeatMode.Reverse
            ),
            label = "voice_record_pulse_value"
        )
        value
    }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = liquidRoundedShape(28f),
        backgroundColor = if (glass.isDark) {
            Color(0xFF101820).copy(alpha = .82f)
        } else {
            Color.White.copy(alpha = .72f)
        },
        elevation = 8.dp
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .animateContentSize(
                    if (reducedMotion) tween(0)
                    else spring(dampingRatio = .76f, stiffness = 360f)
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .graphicsLayer { scaleX = pulse; scaleY = pulse }
                        .background(MaterialTheme.colorScheme.error, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatRecorderTime(elapsedSeconds),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    if (locked) Icons.Default.Lock else Icons.Default.Mic,
                    contentDescription = null,
                    tint = if (locked) glass.accentColor else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    when {
                        paused -> "Paused"
                        locked -> "Recording locked"
                        else -> "Recording"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            RecorderWaveformV3(
                values = waveform,
                paused = paused,
                modifier = Modifier.fillMaxWidth()
            )

            if (!locked) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "← Slide to cancel",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "↑ Slide to lock",
                        style = MaterialTheme.typography.labelMedium,
                        color = glass.accentColor
                    )
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassIconButton(
                        Icons.Default.Delete,
                        "Discard recording",
                        onDiscard,
                        tint = MaterialTheme.colorScheme.error,
                        size = 42.dp
                    )
                    GlassIconButton(
                        if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        if (paused) "Resume recording" else "Pause recording",
                        onPauseResume,
                        size = 42.dp
                    )
                    Spacer(Modifier.weight(1f))
                    GlassIconButton(
                        Icons.Default.Done,
                        "Stop and preview",
                        onStopPreview,
                        size = 42.dp
                    )
                    GlassIconButton(
                        Icons.Default.Send,
                        "Send voice message",
                        onSend,
                        tint = Color.White,
                        backgroundColor = glass.accentColor.copy(alpha = .92f),
                        size = 46.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun RecorderWaveformV3(
    values: List<Float>,
    paused: Boolean,
    modifier: Modifier = Modifier
) {
    val glass = LocalLiquidGlass.current
    val active = if (paused) MaterialTheme.colorScheme.onSurfaceVariant else glass.accentColor
    val bars = values.takeLast(48)

    Canvas(modifier.height(34.dp)) {
        if (bars.isEmpty()) {
            val y = size.height / 2
            drawLine(
                active.copy(alpha = .24f),
                Offset(0f, y),
                Offset(size.width, y),
                strokeWidth = 2.dp.toPx()
            )
            return@Canvas
        }

        val gap = 2.4.dp.toPx()
        val width = ((size.width - gap * (bars.size - 1)) / bars.size).coerceAtLeast(1.6.dp.toPx())
        bars.forEachIndexed { index, amplitude ->
            val amp = amplitude.coerceIn(.08f, 1f)
            val h = (size.height * amp).coerceAtLeast(4.dp.toPx())
            val x = index * (width + gap) + width / 2
            drawLine(
                color = active.copy(alpha = if (paused) .52f else .92f),
                start = Offset(x, (size.height - h) / 2),
                end = Offset(x, (size.height + h) / 2),
                strokeWidth = width
            )
        }
    }
}

private fun formatRecorderTime(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "${safe / 60}:${(safe % 60).toString().padStart(2, '0')}"
}
