from pathlib import Path
import re

path = Path('app/src/main/java/com/example/ui/screens/ConversationScreen.kt')
p = path.read_text(encoding='utf-8')

if 'import androidx.compose.foundation.layout.aspectRatio' not in p:
    p = p.replace(
        'import androidx.compose.foundation.layout.Arrangement\n',
        'import androidx.compose.foundation.layout.Arrangement\nimport androidx.compose.foundation.layout.aspectRatio\n',
        1,
    )

old_upload = '''                val label = when (type) {
                    MessageType.IMAGE -> "Photo"
                    MessageType.VIDEO -> "Video"
                    MessageType.VOICE -> "Voice message"
                    else -> "Document"
                }
                viewModel.sendMessage(conversationId, label, type, url)'''
new_upload = '''                val caption = when (type) {
                    MessageType.IMAGE, MessageType.VIDEO -> ""
                    MessageType.VOICE -> "Voice message"
                    MessageType.FILE -> "Document"
                    else -> ""
                }
                viewModel.sendMessage(conversationId, caption, type, url)'''
if old_upload not in p:
    raise SystemExit('upload label block not found')
p = p.replace(old_upload, new_upload, 1)

old_date = '''@Composable
private fun DateSeparator(time: Long, unread: Boolean = false) {
    Box(Modifier.fillMaxWidth().padding(vertical = 5.dp), contentAlignment = Alignment.Center) {
        GlassCard(shape = RoundedCornerShape(999.dp)) {
            Text(if (unread) "Unread messages" else dayLabel(time), Modifier.padding(horizontal = 12.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall)
        }
    }
}'''
new_date = '''@Composable
private fun DateSeparator(time: Long, unread: Boolean = false) {
    val config = LocalLiquidGlass.current
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        GlassCard(
            shape = RoundedCornerShape(999.dp),
            backgroundColor = if (config.isDark) Color.White.copy(alpha = .045f) else Color.White.copy(alpha = .46f),
            elevation = 0.dp
        ) {
            Text(
                if (unread) "Unread messages" else dayLabel(time),
                Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (unread) config.accentColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}'''
if old_date not in p:
    raise SystemExit('date separator block not found')
p = p.replace(old_date, new_date, 1)

top_bar = '''            topBar = {
                Column(
                    Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        GlassIconButton(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            onBackClick,
                            size = 44.dp
                        )
                        GlassCard(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(28.dp),
                            backgroundColor = if (config.isDark) Color(0xFF0D1723).copy(alpha = .62f) else Color.White.copy(alpha = .55f),
                            elevation = 6.dp,
                            onClick = { onNavigateToProfile(other.uid) }
                        ) {
                            Row(
                                Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GlassAvatar(
                                    other.photoUrl,
                                    other.displayName.ifBlank { "Contact" },
                                    34.dp,
                                    conversation?.isOnline == true && other.onlineVisible
                                )
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        other.displayName.ifBlank { "Contact" },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    val label = presenceLabel(other)
                                    if (label.isNotBlank()) {
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (other.isOnline) EmeraldOnline else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                        GlassIconButton(
                            Icons.Default.MoreHoriz,
                            "Chat menu",
                            { conversationSettings = true },
                            size = 44.dp
                        )
                    }

                    AnimatedVisibility(search) {
                        GlassTextField(
                            query,
                            { query = it },
                            placeholder = "Search this conversation",
                            modifier = Modifier.padding(top = 7.dp),
                            shape = RoundedCornerShape(24.dp)
                        )
                    }

                    allMessages.firstOrNull { it.isPinned && !it.isDeleted }?.let { pinned ->
                        GlassCard(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            shape = RoundedCornerShape(18.dp),
                            backgroundColor = config.accentColor.copy(alpha = if (config.isDark) .10f else .08f),
                            elevation = 0.dp,
                            onClick = { actions = pinned }
                        ) {
                            Text(
                                "📌 ${pinned.text.take(60)}",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                maxLines = 1
                            )
                        }
                    }
                }
            },
            bottomBar = {
'''
p, count = re.subn(
    r'            topBar = \{\n.*?            bottomBar = \{\n',
    top_bar,
    p,
    count=1,
    flags=re.S,
)
if count != 1:
    raise SystemExit(f'topBar replacement count={count}')

bottom_bar = '''            bottomBar = {
                Column(
                    Modifier
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    AnimatedVisibility(reply != null) {
                        GlassCard(
                            Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            shape = RoundedCornerShape(22.dp),
                            backgroundColor = if (config.isDark) Color.White.copy(alpha = .05f) else Color.White.copy(alpha = .52f),
                            elevation = 1.dp
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .width(3.dp)
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(config.accentColor)
                                )
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        reply?.senderName.orEmpty().ifBlank { "Reply" },
                                        color = config.accentColor,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        reply?.text.orEmpty(),
                                        maxLines = 1,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                GlassIconButton(
                                    Icons.Default.Close,
                                    "Cancel reply",
                                    { reply = null },
                                    size = 36.dp
                                )
                            }
                        }
                    }

                    upload?.let { progress ->
                        GlassCard(
                            Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            shape = RoundedCornerShape(20.dp),
                            backgroundColor = config.accentColor.copy(alpha = .08f),
                            elevation = 0.dp
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Uploading ${(progress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { repo.cancelUpload() }) { Text("Cancel") }
                                }
                                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }

                    if (recording) {
                        GlassCard(
                            Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            shape = RoundedCornerShape(22.dp),
                            backgroundColor = MaterialTheme.colorScheme.error.copy(alpha = .08f),
                            elevation = 0.dp
                        ) {
                            Row(
                                Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "● ${elapsed / 60}:${(elapsed % 60).toString().padStart(2, '0')}  ${if (paused) "Paused" else if (locked) "Locked" else "Swipe up to lock • left to cancel"}",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    LiveRecordingWaveform(voiceWaveform, Modifier.fillMaxWidth())
                                }
                                if (locked) {
                                    GlassIconButton(
                                        if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                        if (paused) "Resume" else "Pause",
                                        {
                                            val active = recorder
                                            runCatching { if (paused) active?.resume() else active?.pause() }
                                                .onSuccess { paused = !paused }
                                        },
                                        size = 38.dp
                                    )
                                }
                                GlassIconButton(Icons.Default.Delete, "Cancel recording", { stopRecording(false) }, size = 38.dp)
                                GlassIconButton(
                                    Icons.Default.Send,
                                    "Send voice message",
                                    { stopRecording(true) },
                                    tint = Color.White,
                                    backgroundColor = config.accentColor.copy(alpha = .86f),
                                    size = 38.dp
                                )
                            }
                        }
                    }

                    GlassCard(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(30.dp),
                        backgroundColor = if (config.isDark) Color(0xFF0C1620).copy(alpha = .74f) else Color.White.copy(alpha = .60f),
                        elevation = 10.dp
                    ) {
                        Row(
                            Modifier.padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlassIconButton(
                                Icons.Default.Add,
                                "Attach",
                                { attachmentSheet = true },
                                size = 40.dp
                            )
                            Spacer(Modifier.width(4.dp))
                            GlassTextField(
                                text,
                                { if (it.length <= 8000) text = it },
                                placeholder = if (other.uid in blocked) "Contact blocked" else "Message…",
                                modifier = Modifier.weight(1f),
                                singleLine = false,
                                maxLines = 5,
                                shape = RoundedCornerShape(24.dp)
                            )
                            Spacer(Modifier.width(3.dp))

                            if (text.isBlank()) {
                                GlassIconButton(
                                    Icons.Default.PhotoCamera,
                                    "Camera",
                                    onNavigateToCamera,
                                    size = 40.dp
                                )

                                var dx by remember { mutableFloatStateOf(0f) }
                                var dy by remember { mutableFloatStateOf(0f) }
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (config.isDark) Color.White.copy(alpha = .07f) else Color.White.copy(alpha = .48f))
                                        .pointerInput(Unit) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    dx = 0f
                                                    dy = 0f
                                                    locked = false
                                                    requestCurrent()
                                                },
                                                onDragEnd = {
                                                    if (recordingCurrent && !lockedCurrent) stopCurrent(true)
                                                },
                                                onDragCancel = {
                                                    if (recordingCurrent && !lockedCurrent) stopCurrent(false)
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dx += dragAmount.x
                                                    dy += dragAmount.y
                                                    if (dx < -100f && recordingCurrent) stopCurrent(false)
                                                    if (dy < -100f && recordingCurrent) {
                                                        locked = true
                                                        keyboard?.hide()
                                                        focusManager.clearFocus()
                                                    }
                                                }
                                            )
                                        }
                                        .clickable {
                                            if (!recording) {
                                                locked = true
                                                keyboard?.hide()
                                                focusManager.clearFocus()
                                                requestRecording()
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Mic, "Hold to record", modifier = Modifier.size(21.dp))
                                }
                            } else {
                                GlassIconButton(
                                    Icons.Default.Send,
                                    "Send",
                                    onClick = {
                                        if (text.isNotBlank() && other.uid !in blocked) {
                                            viewModel.sendMessage(
                                                conversationId,
                                                text.trim(),
                                                replyToId = reply?.id,
                                                replyToText = reply?.text,
                                                replyToSender = reply?.senderName
                                            )
                                            text = ""
                                            reply = null
                                        }
                                    },
                                    tint = Color.White,
                                    backgroundColor = config.accentColor.copy(alpha = .88f),
                                    size = 40.dp
                                )
                            }
                        }
                    }
                }
            }
        ) { padding ->
'''
p, count = re.subn(
    r'            bottomBar = \{\n.*?        \) \{ padding ->\n',
    bottom_bar,
    p,
    count=1,
    flags=re.S,
)
if count != 1:
    raise SystemExit(f'bottomBar replacement count={count}')

p = p.replace(
    'contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),\n                    verticalArrangement = Arrangement.spacedBy(9.dp)',
    'contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),\n                    verticalArrangement = Arrangement.spacedBy(3.dp)',
    1,
)

call_anchor = '''                        MessageBubble(
                            message = message,
                            isMe = message.senderId == me.uid,'''
call_replacement = '''                        val previousMessage = rows.getOrNull(index - 1)
                        val nextMessage = rows.getOrNull(index + 1)
                        val groupWithPrevious = previousMessage?.senderId == message.senderId &&
                            previousMessage?.let { sameDay(it.createdAt, message.createdAt) } == true
                        val groupWithNext = nextMessage?.senderId == message.senderId &&
                            nextMessage?.let { sameDay(it.createdAt, message.createdAt) } == true
                        MessageBubble(
                            message = message,
                            isMe = message.senderId == me.uid,
                            groupWithPrevious = groupWithPrevious,
                            groupWithNext = groupWithNext,'''
if call_anchor not in p:
    raise SystemExit('MessageBubble call anchor not found')
p = p.replace(call_anchor, call_replacement, 1)

tail_marker = '@Composable\nprivate fun MorphingTypingBubble('
start = p.find(tail_marker)
if start < 0:
    raise SystemExit('typing/message tail marker not found')

tail = r'''@Composable
private fun MorphingTypingBubble(
    message: Message?,
    reduced: Boolean,
    onLongClick: () -> Unit
) {
    val config = LocalLiquidGlass.current
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 7.dp, bottomEnd = 22.dp)
    Box(
        Modifier
            .widthIn(min = 54.dp, max = 320.dp)
            .clip(shape)
            .background(if (config.isDark) Color.White.copy(alpha = .065f) else Color.White.copy(alpha = .58f))
            .border(1.dp, Color.White.copy(alpha = if (config.isDark) .10f else .58f), shape)
            .animateContentSize(if (reduced) tween(0) else spring(dampingRatio = .72f, stiffness = 360f))
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        if (message == null) {
            TypingDots(reduced)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (message.type == MessageType.TEXT) message.text else when (message.type) {
                        MessageType.IMAGE -> "Photo"
                        MessageType.VIDEO -> "Video"
                        MessageType.VOICE -> "Voice message"
                        else -> "Document"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun TypingDots(reduced: Boolean) {
    val accent = LocalLiquidGlass.current.accentColor
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(vertical = 3.dp)) {
        repeat(3) { index ->
            val y = if (reduced) 0f else {
                val transition = rememberInfiniteTransition(label = "typing")
                val value by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = -3.5f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(330, delayMillis = index * 100),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "dot"
                )
                value
            }
            Box(
                Modifier
                    .offset(y = y.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = .88f))
            )
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    isMe: Boolean,
    groupWithPrevious: Boolean = false,
    groupWithNext: Boolean = false,
    onLongClick: () -> Unit,
    onReply: () -> Unit,
    onReplyPreviewClick: (String) -> Unit,
    onMedia: () -> Unit,
    onReactionClick: (String) -> Unit,
    onRetry: () -> Unit
) {
    val config = LocalLiquidGlass.current
    val reduced = config.isReducedMotion
    var drag by remember { mutableFloatStateOf(0f) }
    val offset by animateFloatAsState(
        targetValue = drag,
        animationSpec = if (reduced) tween(0) else spring(dampingRatio = .72f, stiffness = 430f),
        label = "swipe_reply"
    )

    val bubbleShape = if (isMe) {
        RoundedCornerShape(
            topStart = 22.dp,
            topEnd = if (groupWithPrevious) 8.dp else 22.dp,
            bottomStart = 22.dp,
            bottomEnd = if (groupWithNext) 8.dp else 6.dp
        )
    } else {
        RoundedCornerShape(
            topStart = if (groupWithPrevious) 8.dp else 22.dp,
            topEnd = 22.dp,
            bottomStart = if (groupWithNext) 8.dp else 6.dp,
            bottomEnd = 22.dp
        )
    }

    val bubbleColor = if (isMe) {
        config.accentColor.copy(alpha = if (config.isDark) .54f else .78f)
    } else {
        if (config.isDark) Color.White.copy(alpha = .065f) else Color.White.copy(alpha = .58f)
    }
    val contentColor = if (isMe) Color.White else MaterialTheme.colorScheme.onSurface
    val metadataColor = if (isMe) Color.White.copy(alpha = .76f) else MaterialTheme.colorScheme.onSurfaceVariant
    val genericMediaLabels = setOf("Photo", "Video", "Voice message", "Document")
    val caption = message.text.trim().takeUnless { it in genericMediaLabels }.orEmpty()
    val isMedia = message.type == MessageType.IMAGE || message.type == MessageType.VIDEO

    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 330.dp)
                .offset { IntOffset(offset.roundToInt(), 0) }
                .pointerInput(message.id, isMe) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val triggered = if (isMe) drag < -58f else drag > 58f
                            if (triggered) onReply()
                            drag = 0f
                        },
                        onDragCancel = { drag = 0f },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            drag = if (isMe) (drag + amount).coerceIn(-92f, 0f)
                            else (drag + amount).coerceIn(0f, 92f)
                        }
                    )
                }
                .combinedClickable(
                    onClick = { if (message.mediaUrl.isNotBlank()) onMedia() },
                    onLongClick = onLongClick
                )
        ) {
            GlassCard(
                shape = bubbleShape,
                backgroundColor = bubbleColor,
                borderColor = if (isMe) Color.White.copy(alpha = .34f) else null,
                elevation = if (groupWithPrevious || groupWithNext) 0.dp else 1.dp
            ) {
                Column(
                    Modifier
                        .animateContentSize(if (reduced) tween(0) else spring(dampingRatio = .78f, stiffness = 420f))
                        .padding(if (isMedia && !message.isDeleted) 6.dp else 10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (message.replyToText != null) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isMe) Color.White.copy(alpha = .10f) else config.accentColor.copy(alpha = .07f))
                                .clickable { message.replyToId?.let(onReplyPreviewClick) }
                                .padding(horizontal = 9.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .width(3.dp)
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (isMe) Color.White.copy(alpha = .9f) else config.accentColor)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    message.replyToSender.orEmpty().ifBlank { "Reply" },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isMe) Color.White else config.accentColor
                                )
                                Text(
                                    message.replyToText.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    color = contentColor.copy(alpha = .82f)
                                )
                            }
                        }
                    }

                    if (!message.isDeleted) {
                        when (message.type) {
                            MessageType.IMAGE -> PrivateImage(
                                message.mediaUrl,
                                "Photo",
                                Modifier
                                    .widthIn(min = 230.dp, max = 318.dp)
                                    .aspectRatio(4f / 3f)
                                    .clip(RoundedCornerShape(18.dp))
                            )
                            MessageType.VIDEO -> PrivateVideoThumbnail(
                                message.mediaUrl,
                                Modifier
                                    .widthIn(min = 230.dp, max = 318.dp)
                                    .aspectRatio(16f / 10f)
                                    .clip(RoundedCornerShape(18.dp))
                            )
                            MessageType.VOICE -> VoiceWaveformPlayer(
                                message.voiceDurationSeconds,
                                message.mediaUrl,
                                waveform = message.waveform,
                                isOutgoing = isMe
                            )
                            MessageType.FILE -> Text(
                                "▤  Document • Tap to open",
                                Modifier.padding(horizontal = 5.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = contentColor
                            )
                            else -> Unit
                        }
                    }

                    if (
                        message.type == MessageType.TEXT ||
                        message.isDeleted ||
                        (caption.isNotBlank() && message.type != MessageType.VOICE)
                    ) {
                        Text(
                            if (message.type == MessageType.TEXT || message.isDeleted) message.text else caption,
                            modifier = if (isMedia && !message.isDeleted) Modifier.padding(horizontal = 5.dp, vertical = 2.dp) else Modifier,
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor
                        )
                    }

                    Row(
                        Modifier
                            .align(Alignment.End)
                            .padding(horizontal = if (isMedia) 5.dp else 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (message.isStarred) Text("★", fontSize = 10.sp, color = metadataColor)
                        if (message.isEdited) Text("edited", style = MaterialTheme.typography.labelSmall, color = metadataColor)
                        Text(
                            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.createdAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = metadataColor
                        )
                        if (isMe) {
                            val icon = when (message.status) {
                                MessageDeliveryStatus.SENDING -> Icons.Default.Schedule
                                MessageDeliveryStatus.FAILED -> Icons.Default.ErrorOutline
                                MessageDeliveryStatus.SENT -> Icons.Default.Done
                                else -> Icons.Default.DoneAll
                            }
                            Icon(
                                icon,
                                message.status.name,
                                Modifier.size(14.dp),
                                tint = if (message.status == MessageDeliveryStatus.READ) Color(0xFF73E4FF) else metadataColor
                            )
                        }
                    }

                    if (message.status == MessageDeliveryStatus.FAILED) {
                        Text(
                            "Failed • tap to retry",
                            color = if (isMe) Color.White else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.End).clickable(onClick = onRetry)
                        )
                    }
                }
            }

            if (message.reactions.isNotEmpty()) {
                GlassCard(
                    modifier = Modifier.padding(top = 2.dp),
                    shape = RoundedCornerShape(999.dp),
                    backgroundColor = if (config.isDark) Color(0xFF0B151F).copy(alpha = .78f) else Color.White.copy(alpha = .66f),
                    elevation = 2.dp
                ) {
                    Row(Modifier.padding(horizontal = 7.dp, vertical = 3.dp)) {
                        message.reactions.forEach { reaction ->
                            Text(
                                "${reaction.emoji} ${reaction.userIds.size}",
                                Modifier.clickable { onReactionClick(reaction.emoji) }.padding(horizontal = 3.dp),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
'''
p = p[:start] + tail
path.write_text(p, encoding='utf-8')

glass_path = Path('app/src/main/java/com/example/ui/components/LiquidGlass.kt')
g = glass_path.read_text(encoding='utf-8')
replacements = [
    (
        'Color.White.copy(alpha = (0.48f + config.blurAlpha * 0.18f + config.glassIntensity * 0.08f).coerceIn(0.48f, 0.78f))',
        'Color.White.copy(alpha = (0.36f + config.blurAlpha * 0.14f + config.glassIntensity * 0.06f).coerceIn(0.38f, 0.64f))'
    ),
    (
        'val bg = backgroundColor ?: if (config.isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.72f)',
        'val bg = backgroundColor ?: if (config.isDark) Color.White.copy(alpha = 0.065f) else Color.White.copy(alpha = 0.54f)'
    ),
    ('.defaultMinSize(minHeight = 52.dp)', '.defaultMinSize(minHeight = 48.dp)'),
    (
        '.background(if (config.isDark) Color.White.copy(alpha = 0.075f) else Color.White.copy(alpha = 0.76f))',
        '.background(if (config.isDark) Color.White.copy(alpha = 0.055f) else Color.White.copy(alpha = 0.48f))'
    ),
    ('.padding(horizontal = 16.dp, vertical = 12.dp)', '.padding(horizontal = 14.dp, vertical = 10.dp)'),
]
for old, new in replacements:
    if old not in g:
        raise SystemExit('LiquidGlass patch target missing: ' + old[:55])
    g = g.replace(old, new, 1)
glass_path.write_text(g, encoding='utf-8')
