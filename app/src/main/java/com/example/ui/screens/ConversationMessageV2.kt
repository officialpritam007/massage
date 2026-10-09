@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Message
import com.example.data.model.MessageDeliveryStatus
import com.example.data.model.MessageType
import com.example.ui.components.PrivateImage
import com.example.ui.components.PrivateVideoThumbnail
import com.example.ui.components.VoiceWaveformPlayer
import com.example.ui.theme.LocalLiquidGlass
import com.example.data.network.LiquidApi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val genericMediaLabelsV2 = setOf("Photo", "Video", "Voice message", "Document")

@Composable
fun DustDeleteContainerV2(
    active: Boolean,
    reduced: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val progress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(if (reduced) 0 else 140),
        label = "delete-fade"
    )
    Box(
        modifier
            .clipToBounds()
            .graphicsLayer {
                alpha = 1f - progress
            }
    ) {
        content()
    }
}

@Composable
fun MessageBubbleV2(
    message: Message,
    isMe: Boolean,
    reduced: Boolean,
    deleting: Boolean = false,
    highlighted: Boolean = false,
    onLongClick: () -> Unit,
    onReply: () -> Unit,
    onReplyPreviewClick: (String) -> Unit,
    onMedia: () -> Unit,
    onReaction: (String) -> Unit,
    onRetrySend: () -> Unit
) {
    val config = LocalLiquidGlass.current
    var drag by remember { mutableFloatStateOf(0f) }
    var expanded by rememberSaveable(message.id, message.text) { mutableStateOf(false) }
    var collapsedOverflow by remember(message.id, message.text) { mutableStateOf(false) }

    val offset by animateFloatAsState(
        targetValue = drag,
        animationSpec = if (reduced) tween(0) else spring(dampingRatio = .72f, stiffness = 430f),
        label = "reply-v2"
    )
    val highlightAmount by animateFloatAsState(
        targetValue = if (highlighted) 1f else 0f,
        animationSpec = if (reduced) tween(0) else spring(dampingRatio = .55f, stiffness = 360f),
        label = "reply_target_highlight"
    )
    val shape = if (isMe) RoundedCornerShape(24.dp, 24.dp, 8.dp, 24.dp)
    else RoundedCornerShape(24.dp, 24.dp, 24.dp, 8.dp)
    val bg = if (isMe) {
        if (config.isDark) Color(0xFF163E3A).copy(alpha = .86f)
        else Color(0xFFD9F4EE).copy(alpha = .94f)
    } else {
        if (config.isDark) Color.White.copy(alpha = .075f)
        else Color.White.copy(alpha = .74f)
    }
    val contentColor = if (isMe && config.isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val metaColor = if (isMe && config.isDark) {
        Color.White.copy(alpha = .72f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val caption = remember(message.text) {
        message.text.trim().takeUnless { it in genericMediaLabelsV2 }.orEmpty()
    }
    val borderColor = when {
        highlightAmount > .01f -> config.accentColor.copy(alpha = .32f + highlightAmount * .62f)
        isMe -> if (config.isDark) Color.White.copy(alpha = .18f) else Color.White.copy(alpha = .76f)
        config.isDark -> Color.White.copy(alpha = .10f)
        else -> Color.White.copy(alpha = .72f)
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start) {
        DustDeleteContainerV2(active = deleting, reduced = reduced) {
            Column(
            Modifier
                .widthIn(max = 330.dp)
                .offset { IntOffset(offset.roundToInt(), 0) }
                .graphicsLayer {
                    val pulse = 1f + highlightAmount * .022f
                    scaleX = pulse
                    scaleY = pulse
                    translationY = -3.dp.toPx() * highlightAmount
                    shadowElevation = 8.dp.toPx() * highlightAmount
                }
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
                .combinedClickable(onClick = { if (LiquidApi.isSupportedMedia(message.mediaUrl)) onMedia() }, onLongClick = onLongClick)
        ) {
            // Message rows deliberately avoid backdrop blur. Rendering a haze layer for every
            // LazyColumn item is expensive; tint + rim + a small shadow keeps the glass language.
            Box(
                Modifier
                    .shadow(
                        elevation = if (highlighted) 6.dp else 1.dp,
                        shape = shape,
                        clip = false
                    )
                    .clip(shape)
                    .background(bg)
                    .border(0.5.dp, borderColor.copy(alpha = borderColor.alpha * .72f), shape)
            ) {
                Column(
                    Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (message.replyToText != null) {
                        Row(
                            Modifier
                                .widthIn(max = 260.dp)
                                .background(
                                    if (isMe && config.isDark) Color.White.copy(alpha = .10f)
                                    else Color.White.copy(alpha = .42f),
                                    RoundedCornerShape(15.dp)
                                )
                                .clickable {
                                    message.replyToId?.let(onReplyPreviewClick)
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Column {
                                Text(
                                    message.replyToSender.orEmpty().ifBlank { "Reply" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isMe && config.isDark) Color.White else config.accentColor
                                )
                                Text(message.replyToText.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = .82f))
                            }
                        }
                    }

                    if (message.type in setOf(MessageType.IMAGE, MessageType.VIDEO, MessageType.VOICE, MessageType.AUDIO, MessageType.FILE)
                        && !LiquidApi.isSupportedMedia(message.mediaUrl)) {
                        Text("Media unavailable", color = contentColor, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(8.dp))
                    } else when (message.type) {
                        MessageType.IMAGE -> PrivateImage(message.mediaUrl, "Photo", Modifier.widthIn(min = 210.dp, max = 310.dp), preview = true)
                        MessageType.VIDEO -> PrivateVideoThumbnail(message.mediaUrl, Modifier.widthIn(min = 210.dp, max = 310.dp).aspectRatio(16f / 10f))
                        MessageType.VOICE, MessageType.AUDIO -> VoiceWaveformPlayer(
                                message.voiceDurationSeconds,
                                message.mediaUrl,
                                message.waveform,
                                modifier = Modifier.widthIn(min = 190.dp, max = 250.dp),
                                isOutgoing = isMe
                            )
                        MessageType.FILE -> Text("▤  Document • Tap to open", style = MaterialTheme.typography.bodyMedium, color = contentColor)
                        else -> Unit
                    }

                    val showText = message.type == MessageType.TEXT ||
                        (caption.isNotBlank() && message.type != MessageType.VOICE)
                    val displayText = if (message.type == MessageType.TEXT) message.text else caption
                    val inlineMeta = message.type == MessageType.TEXT &&
                        message.replyToText == null &&
                        !displayText.contains("\n") &&
                        displayText.length <= 24 &&
                        !expanded

                    if (showText && inlineMeta) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                displayText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = contentColor,
                                maxLines = 1
                            )
                            MessageMetaV2(
                                message = message,
                                isMe = isMe,
                                metaColor = metaColor,
                                reduced = reduced
                            )
                        }
                    } else {
                        if (showText) {
                            Text(
                                displayText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = contentColor,
                                maxLines = if (expanded) Int.MAX_VALUE else 6,
                                overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                                onTextLayout = { layout ->
                                    if (!expanded) collapsedOverflow = layout.hasVisualOverflow
                                }
                            )
                            if (collapsedOverflow || expanded) {
                                Text(
                                    if (expanded) "Read less" else "Read more",
                                    color = if (isMe && config.isDark) Color.White.copy(alpha = .92f) else config.accentColor,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clickable { expanded = !expanded }
                                        .padding(horizontal = 2.dp, vertical = 6.dp)
                                )
                            }
                        }

                        MessageMetaV2(
                            message = message,
                            isMe = isMe,
                            metaColor = metaColor,
                            reduced = reduced,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                    if (message.status == MessageDeliveryStatus.FAILED) {
                        Text(
                            "Failed • tap to retry",
                            color = if (isMe && config.isDark) Color.White else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.End).clickable(onClick = onRetrySend)
                        )
                    }
                }
            }
            if (message.reactions.isNotEmpty()) {
                val reactionShape = RoundedCornerShape(999.dp)
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .clip(reactionShape)
                        .background(if (config.isDark) Color(0xFF17313B).copy(alpha = .82f) else Color(0xFFEFF9FC).copy(alpha = .78f))
                        .border(
                            0.5.dp,
                            if (config.isDark) Color.White.copy(alpha = .08f) else Color.White.copy(alpha = .42f),
                            reactionShape
                        )
                ) {
                    Row(Modifier.padding(horizontal = 7.dp, vertical = 3.dp)) {
                        message.reactions.forEach { reaction ->
                            Text(
                                "${reaction.emoji} ${reaction.userIds.size}",
                                Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .clickable { onReaction(reaction.emoji) }
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun MessageMetaV2(
    message: Message,
    isMe: Boolean,
    metaColor: Color,
    reduced: Boolean,
    modifier: Modifier = Modifier
) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val timestamp = remember(message.createdAt, locale) {
        SimpleDateFormat("h:mm", locale).format(Date(message.createdAt))
    }
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (message.isStarred) {
            Text("★", fontSize = 11.sp, color = metaColor)
        }
        if (message.isEdited) {
            Text(
                "edited",
                fontSize = 11.sp,
                color = metaColor.copy(alpha = .86f)
            )
        }
        Text(
            timestamp,
            fontSize = 12.sp,
            color = metaColor
        )
        if (isMe) {
            AnimatedContent(
                targetState = message.status,
                transitionSpec = {
                    if (reduced) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    else (fadeIn(tween(150)) + scaleIn(
                        initialScale = .70f,
                        animationSpec = tween(170)
                    )) togetherWith
                        (fadeOut(tween(110)) + scaleOut(
                            targetScale = 1.18f,
                            animationSpec = tween(130)
                        ))
                },
                label = "delivery_status"
            ) { status ->
                val icon = when (status) {
                    MessageDeliveryStatus.SENDING -> Icons.Default.Schedule
                    MessageDeliveryStatus.FAILED -> Icons.Default.ErrorOutline
                    MessageDeliveryStatus.SENT -> Icons.Default.Done
                    else -> Icons.Default.DoneAll
                }
                Icon(
                    icon,
                    status.name,
                    Modifier.size(14.dp),
                    tint = if (status == MessageDeliveryStatus.READ) {
                        Color(0xFF27A6D1)
                    } else {
                        metaColor
                    }
                )
            }
        }
    }
}
