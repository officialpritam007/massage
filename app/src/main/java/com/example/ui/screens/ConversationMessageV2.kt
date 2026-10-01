@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Message
import com.example.data.model.MessageDeliveryStatus
import com.example.data.model.MessageType
import com.example.ui.components.GlassCard
import com.example.ui.components.PrivateImage
import com.example.ui.components.PrivateVideoThumbnail
import com.example.ui.components.VoiceWaveformPlayer
import com.example.ui.theme.LocalLiquidGlass
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DustDeleteContainerV2(
    active: Boolean,
    reduced: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val dustColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .32f)
    val progress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(if (reduced) 80 else 400),
        label = "dust-delete"
    )
    Box(
        modifier.graphicsLayer {
            alpha = (1f - progress * .92f).coerceIn(0f, 1f)
            scaleX = 1f - progress * .06f
            scaleY = 1f - progress * .04f
            translationX = progress * 12f
        }
    ) {
        content()
        if (active && !reduced) {
            Canvas(Modifier.matchParentSize()) {
                repeat(24) { i ->
                    val fx = ((i * 37) % 101) / 100f
                    val fy = ((i * 61 + 17) % 101) / 100f
                    val driftX = (18f + (i % 5) * 7f) * progress
                    val driftY = ((i % 7) - 3) * 4f * progress
                    drawCircle(
                        color = dustColor.copy(alpha = (.34f * (1f - progress)).coerceAtLeast(0f)),
                        radius = (1.2f + (i % 3) * .8f) * (1f - progress * .35f),
                        center = Offset(size.width * fx + driftX, size.height * fy + driftY)
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubbleV2(
    message: Message,
    isMe: Boolean,
    reduced: Boolean,
    onLongClick: () -> Unit,
    onReply: () -> Unit,
    onReplyPreviewClick: (String) -> Unit,
    onMedia: () -> Unit,
    onReaction: (String) -> Unit,
    onRetrySend: () -> Unit
) {
    val config = LocalLiquidGlass.current
    var drag by remember { mutableFloatStateOf(0f) }
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
    var overflowed by remember(message.id, message.text) { mutableStateOf(false) }

    LaunchedEffect(message.text) {
        // Edited/replaced text always returns to the predictable collapsed state.
        expanded = false
        overflowed = false
    }

    val offset by animateFloatAsState(
        targetValue = drag,
        animationSpec = if (reduced) tween(0) else spring(dampingRatio = .72f, stiffness = 430f),
        label = "reply-v2"
    )
    val shape = if (isMe) RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp)
    else RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp)
    val bg = if (isMe) config.accentColor.copy(alpha = if (config.isDark) .58f else .82f)
    else if (config.isDark) Color.White.copy(alpha = .07f) else Color.White.copy(alpha = .70f)
    val contentColor = if (isMe) Color.White else MaterialTheme.colorScheme.onSurface
    val metaColor = if (isMe) Color.White.copy(alpha = .78f) else MaterialTheme.colorScheme.onSurfaceVariant
    val genericLabels = setOf("Photo", "Video", "Voice message", "Document")
    val caption = message.text.trim().takeUnless { it in genericLabels }.orEmpty()

    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 330.dp)
                .offset { IntOffset(offset.roundToInt(), 0) }
                .pointerInput(message.id, isMe) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if ((isMe && drag < -58f) || (!isMe && drag > 58f)) onReply()
                            drag = 0f
                        },
                        onDragCancel = { drag = 0f },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            drag = if (isMe) (drag + amount).coerceIn(-92f, 0f) else (drag + amount).coerceIn(0f, 92f)
                        }
                    )
                }
                .combinedClickable(onClick = { if (message.mediaUrl.isNotBlank()) onMedia() }, onLongClick = onLongClick)
        ) {
            GlassCard(shape = shape, backgroundColor = bg, borderColor = if (isMe) Color.White.copy(alpha = .30f) else null, elevation = 1.dp) {
                Column(
                    Modifier.animateContentSize(if (reduced) tween(0) else spring(dampingRatio = .78f, stiffness = 410f)).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (message.replyToText != null) {
                        Row(
                            Modifier
                                .widthIn(min = 120.dp)
                                .background(if (isMe) Color.White.copy(alpha = .10f) else config.accentColor.copy(alpha = .08f), RoundedCornerShape(14.dp))
                                .clickable { message.replyToId?.let(onReplyPreviewClick) }
                                .padding(horizontal = 9.dp, vertical = 7.dp)
                        ) {
                            Column {
                                Text(message.replyToSender.orEmpty().ifBlank { "Reply" }, style = MaterialTheme.typography.labelSmall, color = if (isMe) Color.White else config.accentColor)
                                Text(message.replyToText.orEmpty(), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = .82f))
                            }
                        }
                    }

                    when (message.type) {
                        MessageType.IMAGE -> PrivateImage(
                            message.mediaUrl,
                            "Photo",
                            Modifier.widthIn(min = 210.dp, max = 310.dp).aspectRatio(4f / 3f)
                        )
                        MessageType.VIDEO -> PrivateVideoThumbnail(
                            message.mediaUrl,
                            Modifier.widthIn(min = 210.dp, max = 310.dp).aspectRatio(16f / 10f)
                        )
                        MessageType.VOICE -> VoiceWaveformPlayer(message.voiceDurationSeconds, message.mediaUrl, message.waveform, isOutgoing = isMe)
                        MessageType.FILE -> Text("▤  Document • Tap to open", style = MaterialTheme.typography.bodyMedium, color = contentColor)
                        else -> Unit
                    }

                    val showText = message.type == MessageType.TEXT || (caption.isNotBlank() && message.type != MessageType.VOICE)
                    if (showText) {
                        Text(
                            if (message.type == MessageType.TEXT) message.text else caption,
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor,
                            maxLines = if (expanded) Int.MAX_VALUE else 6,
                            overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                            onTextLayout = { layout ->
                                if (!expanded) overflowed = layout.hasVisualOverflow
                            }
                        )
                        if (overflowed || expanded) {
                            Text(
                                if (expanded) "Read less" else "Read more",
                                color = if (isMe) Color.White.copy(alpha = .92f) else config.accentColor,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clickable {
                                        expanded = if (expanded) false else overflowed
                                        if (!expanded) overflowed = true
                                    }
                                    .padding(horizontal = 2.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (message.isStarred) Text("★", fontSize = 10.sp, color = metaColor)
                        if (message.isEdited) Text("edited", style = MaterialTheme.typography.labelSmall, color = metaColor)
                        Text(SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.createdAt)), style = MaterialTheme.typography.labelSmall, color = metaColor)
                        if (isMe) {
                            val icon = when (message.status) {
                                MessageDeliveryStatus.SENDING -> Icons.Default.Schedule
                                MessageDeliveryStatus.FAILED -> Icons.Default.ErrorOutline
                                MessageDeliveryStatus.SENT -> Icons.Default.Done
                                else -> Icons.Default.DoneAll
                            }
                            Icon(icon, message.status.name, Modifier.size(14.dp), tint = if (message.status == MessageDeliveryStatus.READ) Color(0xFF73E4FF) else metaColor)
                        }
                    }
                    if (message.status == MessageDeliveryStatus.FAILED) {
                        Text(
                            "Failed • tap to retry",
                            color = if (isMe) Color.White else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.End).clickable(onClick = onRetrySend)
                        )
                    }
                }
            }
            if (message.reactions.isNotEmpty()) {
                GlassCard(
                    modifier = Modifier.padding(top = 2.dp),
                    shape = RoundedCornerShape(999.dp),
                    backgroundColor = if (config.isDark) Color(0xFF0B151F).copy(alpha = .82f) else Color.White.copy(alpha = .76f),
                    elevation = 1.dp
                ) {
                    Row(Modifier.padding(horizontal = 7.dp, vertical = 3.dp)) {
                        message.reactions.forEach { reaction ->
                            Text("${reaction.emoji} ${reaction.userIds.size}", Modifier.clickable { onReaction(reaction.emoji) }.padding(horizontal = 3.dp), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
