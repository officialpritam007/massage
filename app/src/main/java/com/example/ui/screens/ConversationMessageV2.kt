@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Message
import com.example.data.model.MessageDeliveryStatus
import com.example.data.model.MessageType
import com.example.ui.components.GlassAvatar
import com.example.ui.components.PrivateImage
import com.example.ui.components.PrivateVideoThumbnail
import com.example.ui.components.VoiceWaveformPlayer
import com.example.ui.theme.LocalLiquidGlass
import com.example.data.network.LiquidApi
import kotlinx.coroutines.delay
import java.util.Date
import kotlin.math.roundToInt

private object ReplyHighlightBusV2 {
    val targetId = mutableStateOf<String?>(null)

    fun show(messageId: String) {
        targetId.value = messageId
    }

    fun clear(messageId: String) {
        if (targetId.value == messageId) targetId.value = null
    }
}

@Composable
fun DustDeleteContainerV2(
    active: Boolean,
    reduced: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val dustColor = MaterialTheme.colorScheme.onSurface
    val progress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(if (reduced) 70 else 300),
        label = "dust-delete"
    )
    Box(
        modifier
            .clipToBounds()
            .graphicsLayer {
                val disappear = if (reduced) progress else (progress * 1.5f).coerceIn(0f, 1f)
                alpha = (1f - disappear).coerceIn(0f, 1f)
                scaleX = 1f - progress * .035f
                scaleY = 1f - progress * .025f
                translationX = progress * 6.dp.toPx()
            }
    ) {
        content()
        if (active && !reduced) {
            Canvas(Modifier.matchParentSize()) {
                repeat(72) { i ->
                    val fx = ((i * 47 + 13) % 101) / 100f
                    val fy = ((i * 71 + 29) % 101) / 100f
                    val start = (i % 11) / 36f
                    val local = ((progress - start) / (1f - start)).coerceIn(0f, 1f)
                    if (local <= 0f) return@repeat
                    val direction = if (i % 2 == 0) 1f else .72f
                    val driftX = (5.dp.toPx() + (i % 5) * 1.6.dp.toPx()) * local * direction
                    val driftY = (((i % 9) - 4) * 1.25.dp.toPx()) * local - 3.dp.toPx() * local * local
                    val baseRadius = (1.25f + (i % 4) * .55f).dp.toPx()
                    val particleAlpha = ((1f - local) * (.72f - (i % 5) * .055f)).coerceIn(0f, .78f)
                    drawCircle(
                        color = dustColor.copy(alpha = particleAlpha),
                        radius = baseRadius * (1f - local * .32f),
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
    deleting: Boolean = false,
    highlighted: Boolean = false,
    voiceAvatarUrl: String = "",
    voiceAvatarName: String = "",
    onLongClick: () -> Unit,
    onReply: () -> Unit,
    onReplyPreviewClick: (String) -> Unit,
    onMedia: () -> Unit,
    onReaction: (String) -> Unit,
    onRetrySend: () -> Unit
) {
    val config = LocalLiquidGlass.current
    val maxBubbleWidth = (LocalConfiguration.current.screenWidthDp * .82f).coerceAtMost(360f).dp
    val density = LocalDensity.current
    val replyThresholdPx = with(density) { 58.dp.toPx() }
    val maxDragPx = with(density) { 92.dp.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
    var collapsedOverflow by remember(message.id, message.text) { mutableStateOf(false) }
    val globalHighlightId by ReplyHighlightBusV2.targetId
    val effectiveHighlighted = highlighted || globalHighlightId == message.id

    LaunchedEffect(message.text) {
        expanded = false
        collapsedOverflow = false
    }
    LaunchedEffect(globalHighlightId, message.id) {
        if (globalHighlightId == message.id) {
            delay(if (reduced) 220 else 900)
            ReplyHighlightBusV2.clear(message.id)
        }
    }

    val offset by animateFloatAsState(
        targetValue = drag,
        animationSpec = if (reduced) tween(0) else spring(dampingRatio = .72f, stiffness = 430f),
        label = "reply-v2"
    )
    val highlightAmount by animateFloatAsState(
        targetValue = if (effectiveHighlighted) 1f else 0f,
        animationSpec = if (reduced) tween(0) else spring(dampingRatio = .55f, stiffness = 360f),
        label = "reply_target_highlight"
    )
    val shape = if (isMe) RoundedCornerShape(24.dp, 24.dp, 7.dp, 24.dp)
    else RoundedCornerShape(24.dp, 24.dp, 24.dp, 7.dp)
    val outgoing = MaterialTheme.colorScheme.primary
    val incoming = MaterialTheme.colorScheme.surfaceContainerHigh
    val bubbleBrush = remember(isMe, outgoing, incoming, config.isReducedTransparency) {
        if (isMe) Brush.linearGradient(listOf(outgoing, lerp(outgoing, Color.Black, .09f)))
        else Brush.linearGradient(listOf(incoming.copy(alpha = if (config.isReducedTransparency) 1f else .94f), incoming.copy(alpha = if (config.isReducedTransparency) 1f else .88f)))
    }
    val contentColor = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val metaColor = if (isMe) contentColor.copy(alpha = .74f) else MaterialTheme.colorScheme.onSurfaceVariant
    val genericLabels = setOf("Photo", "Video", "Voice message", "Document")
    val caption = message.text.trim().takeUnless { it in genericLabels }.orEmpty()
    val borderColor = when {
        highlightAmount > .01f -> config.accentColor.copy(alpha = .32f + highlightAmount * .62f)
        isMe -> Color.White.copy(alpha = if (config.isDark) .24f else .42f)
        config.isDark -> Color.White.copy(alpha = .10f)
        else -> Color.White.copy(alpha = .72f)
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start) {
        DustDeleteContainerV2(active = deleting, reduced = reduced) {
            Column(
            Modifier
                .widthIn(max = maxBubbleWidth)
                .offset { IntOffset(offset.roundToInt(), 0) }
                .graphicsLayer {
                    val pulse = 1f + highlightAmount * .022f
                    scaleX = pulse
                    scaleY = pulse
                    translationY = -3.dp.toPx() * highlightAmount
                    shadowElevation = 8.dp.toPx() * highlightAmount
                }
                .pointerInput(message.id, isMe, replyThresholdPx, maxDragPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if ((isMe && drag < -replyThresholdPx) || (!isMe && drag > replyThresholdPx)) onReply()
                            drag = 0f
                        },
                        onDragCancel = { drag = 0f },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            drag = if (isMe) (drag + amount).coerceIn(-maxDragPx, 0f) else (drag + amount).coerceIn(0f, maxDragPx)
                        }
                    )
                }
                .combinedClickable(onClick = { if (LiquidApi.isSupportedMedia(message.mediaUrl)) onMedia() }, onLongClick = onLongClick, onLongClickLabel = "Message actions")
                .semantics {
                    customActions = listOf(CustomAccessibilityAction("Reply") { onReply(); true })
                }
        ) {
            // Message rows deliberately avoid backdrop blur. Rendering a haze layer for every
            // LazyColumn item is expensive; tint + rim + a small shadow keeps the glass language.
            Box(
                Modifier
                    .shadow(
                        elevation = if (effectiveHighlighted) 6.dp else 0.dp,
                        shape = shape,
                        clip = false
                    )
                    .clip(shape)
                    .background(bubbleBrush)
                    .border(0.5.dp, borderColor.copy(alpha = borderColor.alpha * .72f), shape)
            ) {
                Column(
                    Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (message.replyToText != null) {
                        Row(
                            Modifier
                                .widthIn(max = 260.dp)
                                .defaultMinSize(minHeight = 48.dp)
                                .background(if (isMe) contentColor.copy(alpha = .09f) else config.accentColor.copy(alpha = .08f), RoundedCornerShape(15.dp))
                                .clickable {
                                    message.replyToId?.let { replyId ->
                                        ReplyHighlightBusV2.show(replyId)
                                        onReplyPreviewClick(replyId)
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Column {
                                Text(message.replyToSender.orEmpty().ifBlank { "Reply" }, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = if (isMe) contentColor else config.accentColor)
                                Text(message.replyToText.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = .82f))
                            }
                        }
                    }

                    if (message.type in setOf(MessageType.IMAGE, MessageType.VIDEO, MessageType.VOICE, MessageType.AUDIO, MessageType.FILE)
                        && !LiquidApi.isSupportedMedia(message.mediaUrl)) {
                        Text("Media unavailable", color = contentColor, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(8.dp))
                    } else when (message.type) {
                        MessageType.IMAGE -> PrivateImage(message.mediaUrl, "Photo", Modifier.widthIn(min = 120.dp, max = maxBubbleWidth - 24.dp).clip(RoundedCornerShape(16.dp)), preview = true)
                        MessageType.VIDEO -> PrivateVideoThumbnail(message.mediaUrl, Modifier.widthIn(min = 120.dp, max = maxBubbleWidth - 24.dp).aspectRatio(16f / 10f).clip(RoundedCornerShape(16.dp)))
                        MessageType.VOICE, MessageType.AUDIO -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(44.dp)) {
                                GlassAvatar(photoUrl = voiceAvatarUrl, name = voiceAvatarName.ifBlank { message.senderName.ifBlank { "Voice" } }, size = 42.dp)
                                Box(
                                    Modifier.size(17.dp).align(Alignment.BottomEnd).background(
                                        if (isMe) Color.White.copy(alpha = .94f) else config.accentColor.copy(alpha = .94f),
                                        CircleShape
                                    ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(10.dp), tint = if (isMe) config.accentColor else Color.White)
                                }
                            }
                            VoiceWaveformPlayer(
                                message.voiceDurationSeconds,
                                message.mediaUrl,
                                message.waveform,
                                modifier = Modifier.weight(1f).widthIn(max = 250.dp),
                                isOutgoing = isMe
                            )
                        }
                        MessageType.FILE -> Text("▤  Document • Tap to open", style = MaterialTheme.typography.bodyMedium, color = contentColor)
                        else -> Unit
                    }

                    val showText = message.type == MessageType.TEXT ||
                        (caption.isNotBlank() && message.type != MessageType.VOICE)
                    val displayText = if (message.type == MessageType.TEXT) message.text else caption
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
                                    color = if (isMe) contentColor else config.accentColor,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 48.dp)
                                        .clickable(role = Role.Button) { expanded = !expanded }
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
                    if (message.status == MessageDeliveryStatus.FAILED) {
                        Text(
                            "Failed • tap to retry",
                            color = if (isMe) contentColor else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.End).defaultMinSize(minHeight = 48.dp).clickable(role = Role.Button, onClick = onRetrySend).padding(vertical = 12.dp)
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
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .96f))
                        .border(
                            0.5.dp,
                            if (config.isDark) Color.White.copy(alpha = .08f) else Color.White.copy(alpha = .42f),
                            reactionShape
                        )
                ) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 2.dp)) {
                        message.reactions.forEach { reaction ->
                            Text(
                                "${reaction.emoji} ${reaction.userIds.size}",
                                Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .clickable(role = Role.Button) { onReaction(reaction.emoji) }
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
    val context = LocalContext.current
    val timeFormatter = remember(context) { android.text.format.DateFormat.getTimeFormat(context) }
    val timeLabel = remember(message.createdAt, timeFormatter) { timeFormatter.format(Date(message.createdAt)) }
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
            timeLabel,
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
                        if (isMe) metaColor else MaterialTheme.colorScheme.primary
                    } else {
                        metaColor
                    }
                )
            }
        }
    }
}
