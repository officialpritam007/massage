@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.Message
import com.example.data.model.MessageDeliveryStatus
import com.example.data.model.MessageType
import com.example.data.model.User
import com.example.ui.components.GlassAvatar
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassDialog
import com.example.ui.components.GlassIconButton
import com.example.ui.components.GlassTextField
import com.example.ui.components.LiquidBackground
import com.example.ui.components.PrivateImage
import com.example.ui.components.PrivateVideoThumbnail
import com.example.ui.components.VoiceWaveformPlayer
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.viewmodel.LiquidChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

fun presenceLabel(u: User): String {
    if (u.onlineVisible && u.isOnline) return "Online"
    if (!u.lastSeenVisible || u.lastSeen <= 0) return ""

    val now = Calendar.getInstance()
    val whenSeen = Calendar.getInstance().apply { timeInMillis = u.lastSeen }
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(u.lastSeen))
    val today = now.get(Calendar.YEAR) == whenSeen.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == whenSeen.get(Calendar.DAY_OF_YEAR)

    now.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = now.get(Calendar.YEAR) == whenSeen.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == whenSeen.get(Calendar.DAY_OF_YEAR)

    return "Last seen " + when {
        today -> "today at $time"
        yesterday -> "yesterday at $time"
        else -> SimpleDateFormat("d MMM yyyy 'at' h:mm a", Locale.getDefault())
            .format(Date(u.lastSeen))
    }
}

private data class VoiceDraft(val file: File, val seconds: Int, val waveform: List<Float>)

private fun appendWaveform(existing: List<Float>, value: Float): List<Float> {
    var next = existing + value.coerceIn(.05f, 1f)
    while (next.size > 80) {
        next = next.chunked(2).map { chunk -> chunk.average().toFloat() }
    }
    return next
}

private fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

private fun dayLabel(time: Long): String {
    val now = Calendar.getInstance()
    val day = Calendar.getInstance().apply { timeInMillis = time }
    if (now.get(Calendar.YEAR) == day.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR)) return "Today"
    now.add(Calendar.DAY_OF_YEAR, -1)
    if (now.get(Calendar.YEAR) == day.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR)) return "Yesterday"
    return SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(time))
}

@Composable
private fun DateSeparator(time: Long, unread: Boolean = false) {
    Box(Modifier.fillMaxWidth().padding(vertical = 5.dp), contentAlignment = Alignment.Center) {
        GlassCard(shape = RoundedCornerShape(999.dp)) {
            Text(if (unread) "Unread messages" else dayLabel(time), Modifier.padding(horizontal = 12.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun LiveRecordingWaveform(values: List<Float>, modifier: Modifier = Modifier) {
    val waveformColor = MaterialTheme.colorScheme.error
    androidx.compose.foundation.Canvas(modifier.height(30.dp)) {
        val bars = values.takeLast(32)
        if (bars.isEmpty()) return@Canvas
        val gap = 3.dp.toPx()
        val width = ((size.width - gap * (bars.size - 1)) / bars.size).coerceAtLeast(2.dp.toPx())
        bars.forEachIndexed { index, amp ->
            val h = size.height * amp.coerceIn(.08f, 1f)
            val x = index * (width + gap) + width / 2
            drawLine(waveformColor, Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2), width)
        }
    }
}

@Composable
fun ConversationScreen(
    conversationId: String,
    viewModel: LiquidChatViewModel,
    onBackClick: () -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToCamera: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val conversations by viewModel.conversations.collectAsState()
    val messageMap by viewModel.messages.collectAsState()
    val me by viewModel.currentUser.collectAsState()
    val upload by viewModel.upload.collectAsState()
    val blocked by viewModel.blockedUserIds.collectAsState()

    val repo = viewModel.repository
    val conversation = conversations.find { it.id == conversationId }
    val other = conversation?.otherUser ?: User(displayName = "Contact")
    val allMessages = messageMap[conversationId].orEmpty()

    val config = LocalLiquidGlass.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var text by rememberSaveable(conversationId) { mutableStateOf(repo.draft(conversationId)) }
    var reply by remember { mutableStateOf<Message?>(null) }
    var actions by remember { mutableStateOf<Message?>(null) }
    var editing by remember { mutableStateOf<Message?>(null) }
    var editText by remember { mutableStateOf("") }
    var forward by remember { mutableStateOf<Message?>(null) }
    var viewer by remember { mutableStateOf<Message?>(null) }
    var deleteForEveryone by remember { mutableStateOf<Message?>(null) }
    var deleteChatConfirm by remember { mutableStateOf(false) }

    var attachmentSheet by remember { mutableStateOf(false) }
    var conversationSettings by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var search by remember { mutableStateOf(false) }
    var starsOnly by remember { mutableStateOf(false) }

    var tailId by remember(conversationId) { mutableStateOf<String?>(null) }
    var lastRemoteId by remember(conversationId) { mutableStateOf<String?>(null) }
    var typingSeen by remember { mutableLongStateOf(0L) }
    var initial by remember { mutableStateOf(true) }

    var recording by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var elapsed by remember { mutableIntStateOf(0) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var voiceFile by remember { mutableStateOf<File?>(null) }
    var paused by remember { mutableStateOf(false) }
    var voiceWaveform by remember { mutableStateOf<List<Float>>(emptyList()) }
    var voiceDraft by remember { mutableStateOf<VoiceDraft?>(null) }
    var initialUnread by rememberSaveable(conversationId) { mutableIntStateOf(conversation?.unreadCount ?: 0) }

    fun stopRecording(send: Boolean) {
        val activeRecorder = recorder
        recorder = null
        val file = voiceFile
        voiceFile = null
        val seconds = elapsed.coerceAtLeast(1)
        val captured = voiceWaveform

        val stoppedCleanly = runCatching { activeRecorder?.stop() }.isSuccess
        runCatching { activeRecorder?.release() }
        recording = false
        paused = false
        locked = false
        focusManager.clearFocus()

        if (send && stoppedCleanly && file != null && file.length() > 0) {
            voiceDraft?.file?.delete()
            voiceDraft = VoiceDraft(file, seconds, captured)
        } else {
            file?.delete()
        }
    }

    fun startRecording() {
        if (recording) return
        runCatching {
            val file = File.createTempFile("voice-", ".m4a", context.cacheDir)
            val activeRecorder = if (Build.VERSION.SDK_INT >= 31) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            activeRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            activeRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            activeRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            activeRecorder.setAudioEncodingBitRate(64000)
            activeRecorder.setOutputFile(file.absolutePath)
            activeRecorder.prepare()
            activeRecorder.start()
            recorder = activeRecorder
            voiceFile = file
            recording = true
            paused = false
            voiceWaveform = emptyList()
            elapsed = 0
        }.onFailure {
            android.widget.Toast.makeText(context, it.message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    val stopCurrent by rememberUpdatedState<(Boolean) -> Unit> { send -> stopRecording(send) }
    val recordingCurrent by rememberUpdatedState(recording)
    val lockedCurrent by rememberUpdatedState(locked)

    val recordPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            locked = true
            keyboard?.hide()
            focusManager.clearFocus()
            startRecording()
        }
    }

    fun requestRecording() {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startRecording()
        } else {
            recordPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val requestCurrent by rememberUpdatedState<() -> Unit> { requestRecording() }

    fun uploadFile(uri: Uri, type: MessageType) {
        repo.uploadChatMedia(conversationId, uri, type) { result ->
            result.onSuccess { url ->
                val label = when (type) {
                    MessageType.IMAGE -> "Photo"
                    MessageType.VIDEO -> "Video"
                    MessageType.VOICE -> "Voice message"
                    else -> "Document"
                }
                viewModel.sendMessage(conversationId, label, type, url)
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { uploadFile(it, MessageType.IMAGE) }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { uploadFile(it, MessageType.VIDEO) }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { uploadFile(it, MessageType.FILE) }
    }

    LaunchedEffect(recording, paused) {
        while (recording) {
            delay(1000)
            if (!paused) elapsed++
            if (elapsed >= 600) stopRecording(true)
        }
    }

    LaunchedEffect(recording, paused) {
        while (recording) {
            if (!paused) {
                val amp = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                val level = sqrt((amp / 32767f).coerceIn(0f, 1f)).coerceAtLeast(.05f)
                voiceWaveform = appendWaveform(voiceWaveform, level)
            }
            delay(120)
        }
    }

    LaunchedEffect(conversation?.unreadCount) {
        val count = conversation?.unreadCount ?: 0
        if (initialUnread == 0 && count > 0) initialUnread = count
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                if (recordingCurrent) stopCurrent(false)
                repo.setTyping(conversationId, false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { recorder?.release() }
            voiceFile?.delete()
            voiceDraft?.file?.delete()
            repo.setTyping(conversationId, false)
        }
    }

    LaunchedEffect(conversationId) {
        viewModel.observeConversation(conversationId)
    }

    LaunchedEffect(text) {
        repo.saveDraft(conversationId, text)
        if (text.isNotBlank()) {
            repo.setTyping(conversationId, true)
            delay(3000)
        }
        repo.setTyping(conversationId, false)
    }

    LaunchedEffect(conversation?.isTyping) {
        if (conversation?.isTyping == true) {
            typingSeen = System.currentTimeMillis()
            tailId = null
        }
    }

    LaunchedEffect(allMessages.lastOrNull()?.id, allMessages.size) {
        val remote = allMessages.lastOrNull { it.senderId != me.uid }
        if (
            !initial &&
            remote != null &&
            remote.type == MessageType.TEXT &&
            remote.id != lastRemoteId &&
            System.currentTimeMillis() - typingSeen < 8000
        ) {
            tailId = remote.id
        }
        lastRemoteId = remote?.id

        val nearBottom = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let {
            it >= listState.layoutInfo.totalItemsCount - 4
        } ?: true

        if (initial || nearBottom || allMessages.lastOrNull()?.senderId == me.uid) {
            delay(60)
            val count = listState.layoutInfo.totalItemsCount
            if (count > 0) {
                if (config.isReducedMotion) {
                    listState.scrollToItem(count - 1)
                } else {
                    listState.animateScrollToItem(count - 1)
                }
            }
        }
        initial = false
        viewModel.clearUnread(conversationId)
    }

    LaunchedEffect(tailId) {
        if (tailId != null) {
            delay(1100)
            tailId = null
        }
    }

    val visibleMessages = allMessages.filter {
        (!starsOnly || it.isStarred) && (query.isBlank() || it.text.contains(query, true))
    }
    val morphMessage = visibleMessages.find { it.id == tailId }

    LiquidBackground(
        modifier = modifier,
        crystal = conversation?.wallpaperIndex != 1
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column(
                    Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GlassIconButton(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            onBackClick
                        )
                        GlassCard(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(32.dp),
                            onClick = { onNavigateToProfile(other.uid) }
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GlassAvatar(
                                    other.photoUrl,
                                    other.displayName,
                                    36.dp,
                                    conversation?.isOnline == true && other.onlineVisible
                                )
                                Spacer(Modifier.width(9.dp))
                                Column {
                                    Text(
                                        other.displayName,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    val label = presenceLabel(other)
                                    if (label.isNotBlank()) {
                                        Text(
                                            label,
                                            fontSize = 10.sp,
                                            color = if (other.isOnline) {
                                                EmeraldOnline
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                        GlassIconButton(
                            Icons.Default.MoreHoriz,
                            "Chat menu",
                            { conversationSettings = true }
                        )
                    }

                    AnimatedVisibility(search) {
                        GlassTextField(
                            query,
                            { query = it },
                            placeholder = "Search this conversation",
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    allMessages.firstOrNull { it.isPinned && !it.isDeleted }?.let { pinned ->
                        Text(
                            "📌 ${pinned.text.take(60)}",
                            fontSize = 12.sp,
                            modifier = Modifier
                                .padding(8.dp)
                                .clickable { actions = pinned }
                        )
                    }
                }
            },
            bottomBar = {
                Column(
                    Modifier
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    AnimatedVisibility(reply != null) {
                        GlassCard(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                            Row(
                                Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Reply to ${reply?.senderName}",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 12.sp
                                    )
                                    Text(reply?.text.orEmpty(), maxLines = 2, fontSize = 13.sp)
                                }
                                IconButton(onClick = { reply = null }) {
                                    Icon(Icons.Default.Close, "Cancel reply")
                                }
                            }
                        }
                    }

                    upload?.let { progress ->
                        GlassCard(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Uploading ${(progress * 100).toInt()}%", fontSize = 12.sp)
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                TextButton(onClick = { repo.cancelUpload() }) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }

                    if (recording) {
                        GlassCard(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                            Row(
                                Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "● ${elapsed / 60}:${(elapsed % 60).toString().padStart(2, '0')} ${if (paused) "Paused" else if (locked) "Locked" else "↑ lock  ← cancel"}",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    LiveRecordingWaveform(voiceWaveform, Modifier.fillMaxWidth())
                                }
                                if (locked) {
                                    IconButton(onClick = {
                                        val active = recorder
                                        runCatching { if (paused) active?.resume() else active?.pause() }
                                            .onSuccess { paused = !paused }
                                    }) {
                                        Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, if (paused) "Resume" else "Pause")
                                    }
                                }
                                IconButton(onClick = { stopRecording(false) }) {
                                    Icon(Icons.Default.Delete, "Cancel recording")
                                }
                                IconButton(onClick = { stopRecording(true) }) {
                                    Icon(Icons.Default.Send, "Send voice message")
                                }
                            }
                        }
                    }

                    GlassCard(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(32.dp)
                    ) {
                        Row(
                            Modifier.padding(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { attachmentSheet = true },
                                enabled = upload == null
                            ) {
                                Icon(Icons.Default.Add, "Attach")
                            }

                            GlassTextField(
                                text,
                                { if (it.length <= 8000) text = it },
                                placeholder = if (other.uid in blocked) {
                                    "Contact blocked"
                                } else {
                                    "Message…"
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = false,
                                maxLines = 5
                            )

                            if (text.isBlank()) {
                                IconButton(
                                    onClick = onNavigateToCamera,
                                    enabled = upload == null && !recording
                                ) {
                                    Icon(Icons.Default.PhotoCamera, "Camera")
                                }

                                var dx by remember { mutableFloatStateOf(0f) }
                                var dy by remember { mutableFloatStateOf(0f) }
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .pointerInput(Unit) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    dx = 0f
                                                    dy = 0f
                                                    locked = false
                                                    requestCurrent()
                                                },
                                                onDragEnd = {
                                                    if (recordingCurrent && !lockedCurrent) {
                                                        stopCurrent(true)
                                                    }
                                                },
                                                onDragCancel = {
                                                    if (recordingCurrent && !lockedCurrent) {
                                                        stopCurrent(false)
                                                    }
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dx += dragAmount.x
                                                    dy += dragAmount.y
                                                    if (dx < -100f && recordingCurrent) {
                                                        stopCurrent(false)
                                                    }
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
                                    Icon(
                                        Icons.Default.Mic,
                                        "Hold to record; swipe up to lock or left to cancel"
                                    )
                                }
                            } else {
                                IconButton(
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
                                    enabled = other.uid !in blocked
                                ) {
                                    Icon(
                                        Icons.Default.Send,
                                        "Send",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    item {
                        Box(
                            Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            TextButton(onClick = { repo.loadOlder(conversationId) }) {
                                Text("Load earlier messages")
                            }
                        }
                    }

                    val rows = visibleMessages.filterNot { it.id == morphMessage?.id }
                    itemsIndexed(rows, key = { _, item -> item.id }) { index, message ->
                        if (index == 0 || !sameDay(rows[index - 1].createdAt, message.createdAt)) {
                            DateSeparator(message.createdAt)
                        }
                        val unreadStart = (rows.size - initialUnread).coerceAtLeast(0)
                        if (initialUnread > 0 && index == unreadStart) {
                            DateSeparator(message.createdAt, unread = true)
                        }
                        MessageBubble(
                            message = message,
                            isMe = message.senderId == me.uid,
                            onLongClick = {
                                actions = message
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onReply = {
                                reply = message
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onReplyPreviewClick = { replyId ->
                                val target = rows.indexOfFirst { it.id == replyId }
                                if (target >= 0) scope.launch { listState.animateScrollToItem(target + 1) }
                            },
                            onMedia = { viewer = message },
                            onReactionClick = { emoji ->
                                viewModel.addReaction(conversationId, message.id, emoji)
                            },
                            onRetry = { repo.retryMessage(conversationId, message.id) }
                        )
                    }

                    item(key = "live-typing-tail") {
                        AnimatedVisibility(
                            visible = morphMessage != null || conversation?.isTyping == true,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            MorphingTypingBubble(
                                message = morphMessage,
                                reduced = config.isReducedMotion,
                                onLongClick = { morphMessage?.let { actions = it } }
                            )
                        }
                    }
                }

                val awayFromBottom = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let {
                    it < listState.layoutInfo.totalItemsCount - 3
                } ?: false

                if (awayFromBottom) {
                    SmallFloatingActionButton(
                        onClick = {
                            scope.launch {
                                listState.animateScrollToItem(
                                    (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                                )
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, "New messages")
                    }
                }
            }
        }

        if (attachmentSheet) {
            GlassDialog("Share content", { attachmentSheet = false }) {
                TextButton(onClick = {
                    attachmentSheet = false
                    onNavigateToCamera()
                }) { Text("Camera") }
                TextButton(onClick = {
                    attachmentSheet = false
                    imagePicker.launch("image/*")
                }) { Text("Photo gallery") }
                TextButton(onClick = {
                    attachmentSheet = false
                    videoPicker.launch("video/*")
                }) { Text("Video gallery") }
                TextButton(onClick = {
                    attachmentSheet = false
                    filePicker.launch("*/*")
                }) { Text("Document") }
            }
        }

        if (conversationSettings) {
            GlassDialog("Conversation", { conversationSettings = false }) {
                TextButton(onClick = {
                    search = !search
                    query = ""
                    conversationSettings = false
                }) { Text("Search messages") }
                TextButton(onClick = {
                    starsOnly = !starsOnly
                    conversationSettings = false
                }) { Text(if (starsOnly) "Show all messages" else "Starred messages") }
                TextButton(onClick = {
                    repo.setFavorite(conversationId, conversation?.isPinned != true)
                    conversationSettings = false
                }) {
                    Text(if (conversation?.isPinned == true) "Remove favorite" else "Add favorite")
                }
                TextButton(onClick = {
                    viewModel.setConversationMuted(conversationId, conversation?.isMuted != true)
                    conversationSettings = false
                }) {
                    Text(if (conversation?.isMuted == true) "Unmute" else "Mute")
                }
                Text("Disappearing messages")
                Row {
                    listOf(
                        "Off" to 0L,
                        "24h" to 86400L,
                        "7 days" to 604800L
                    ).forEach { (label, seconds) ->
                        TextButton(onClick = {
                            viewModel.setDisappearingMessages(conversationId, seconds)
                            conversationSettings = false
                        }) { Text(label) }
                    }
                }
                TextButton(onClick = {
                    viewModel.setConversationWallpaper(
                        conversationId,
                        if (conversation?.wallpaperIndex == 1) 0 else 1
                    )
                    conversationSettings = false
                }) { Text("Toggle crystal / plain wallpaper") }
                TextButton(onClick = {
                    conversationSettings = false
                    deleteChatConfirm = true
                }) { Text("Delete chat", color = MaterialTheme.colorScheme.error) }
            }
        }

        actions?.let { message ->
            GlassDialog("Message", { actions = null }) {
                Row {
                    listOf("❤️", "👍", "😂", "😮", "😢", "🙏").forEach { emoji ->
                        Text(
                            emoji,
                            Modifier
                                .clickable {
                                    viewModel.addReaction(conversationId, message.id, emoji)
                                    actions = null
                                }
                                .padding(6.dp),
                            fontSize = 23.sp
                        )
                    }
                }
                TextButton(onClick = {
                    reply = message
                    actions = null
                }) { Text("Reply") }
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(message.text))
                    actions = null
                }) { Text("Copy") }
                TextButton(onClick = {
                    repo.starMessage(conversationId, message.id)
                    actions = null
                }) { Text(if (message.isStarred) "Unstar" else "Star") }
                TextButton(onClick = {
                    viewModel.pinMessage(conversationId, message.id)
                    actions = null
                }) { Text(if (message.isPinned) "Unpin" else "Pin") }
                TextButton(onClick = {
                    forward = message
                    actions = null
                }) { Text("Forward") }
                if (message.senderId == me.uid && message.type == MessageType.TEXT && !message.isDeleted) {
                    TextButton(onClick = {
                        editing = message
                        editText = message.text
                        actions = null
                    }) { Text("Edit") }
                }
                TextButton(onClick = {
                    viewModel.deleteMessageForMe(conversationId, message.id)
                    actions = null
                }) { Text("Delete for me") }
                if (message.senderId == me.uid) {
                    TextButton(onClick = {
                        deleteForEveryone = message
                        actions = null
                    }) {
                        Text("Delete for everyone", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        deleteForEveryone?.let { message ->
            GlassDialog("Delete for everyone?", { deleteForEveryone = null }) {
                Text("This message will be removed for both people.")
                GlassButton("Delete", {
                    viewModel.deleteMessageForEveryone(conversationId, message.id)
                    deleteForEveryone = null
                })
            }
        }

        editing?.let { message ->
            GlassDialog("Edit message", { editing = null }) {
                GlassTextField(
                    editText,
                    { editText = it },
                    singleLine = false,
                    maxLines = 6
                )
                GlassButton("Save", {
                    viewModel.editMessage(conversationId, message.id, editText)
                    editing = null
                })
            }
        }

        forward?.let { message ->
            GlassDialog("Forward to", { forward = null }) {
                conversations.filter { it.id != conversationId }.forEach { target ->
                    TextButton(onClick = {
                        if (message.type == MessageType.TEXT) {
                            viewModel.sendMessage(target.id, message.text)
                        } else {
                            repo.forwardMedia(message, target.id)
                        }
                        forward = null
                    }) {
                        Text(target.otherUser.displayName)
                    }
                }
                if (conversations.size < 2) {
                    Text("Start another conversation first")
                }
            }
        }

        voiceDraft?.let { draft ->
            GlassDialog("Voice preview", {
                draft.file.delete()
                voiceDraft = null
            }) {
                VoiceWaveformPlayer(
                    draft.seconds,
                    Uri.fromFile(draft.file).toString(),
                    waveform = draft.waveform,
                    isOutgoing = true
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton("Discard", {
                        draft.file.delete()
                        voiceDraft = null
                    }, Modifier.weight(1f), isPrimary = false)
                    GlassButton("Send", {
                        val localDraft = draft
                        voiceDraft = null
                        repo.uploadChatMedia(conversationId, Uri.fromFile(localDraft.file), MessageType.VOICE) { result ->
                            result.onSuccess { url ->
                                viewModel.sendMessage(
                                    conversationId,
                                    "Voice message",
                                    MessageType.VOICE,
                                    url,
                                    voiceDurationSeconds = localDraft.seconds,
                                    waveform = localDraft.waveform
                                )
                                localDraft.file.delete()
                            }.onFailure { localDraft.file.delete() }
                        }
                    }, Modifier.weight(1f))
                }
            }
        }

        if (deleteChatConfirm) {
            GlassDialog("Delete chat?", { deleteChatConfirm = false }) {
                Text("This permanently removes the existing conversation from your account. It will not return after restart, sign-in, reinstall or sync. A future new message can create a fresh chat without restoring the deleted history.")
                GlassButton("Delete chat", {
                    viewModel.deleteChatForMe(conversationId)
                    deleteChatConfirm = false
                    onBackClick()
                }, Modifier.fillMaxWidth())
            }
        }

        viewer?.let { message ->
            MediaViewer(message) { viewer = null }
        }
    }
}

@Composable
private fun MorphingTypingBubble(
    message: Message?,
    reduced: Boolean,
    onLongClick: () -> Unit
) {
    val dark = LocalLiquidGlass.current.isDark
    Box(
        Modifier
            .widthIn(max = 300.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (dark) Color(0xCC2168AA) else Color(0xBBA4D1FF))
            .border(1.dp, Color.White.copy(alpha = .65f), RoundedCornerShape(22.dp))
            .animateContentSize(
                if (reduced) tween(0)
                else spring(dampingRatio = .7f, stiffness = 350f)
            )
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (message == null) {
            TypingDots(reduced)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (message.type == MessageType.TEXT) {
                    Text(message.text, fontSize = 15.sp, lineHeight = 21.sp)
                } else {
                    Text(
                        when (message.type) {
                            MessageType.IMAGE -> "Photo"
                            MessageType.VIDEO -> "Video"
                            MessageType.VOICE -> "Voice message"
                            else -> "Document"
                        }
                    )
                }
                Text(
                    SimpleDateFormat("h:mm a", Locale.getDefault())
                        .format(Date(message.createdAt)),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun TypingDots(reduced: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(vertical = 3.dp)
    ) {
        repeat(3) { index ->
            val y = if (reduced) {
                0f
            } else {
                val transition = rememberInfiniteTransition(label = "typing")
                val value by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = -4f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(350, delayMillis = index * 110),
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
                    .background(Color(0xFF3779C5))
            )
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    isMe: Boolean,
    onLongClick: () -> Unit,
    onReply: () -> Unit,
    onReplyPreviewClick: (String) -> Unit,
    onMedia: () -> Unit,
    onReactionClick: (String) -> Unit,
    onRetry: () -> Unit
) {
    val dark = LocalLiquidGlass.current.isDark
    val reduced = LocalLiquidGlass.current.isReducedMotion
    var drag by remember { mutableFloatStateOf(0f) }
    val offset by animateFloatAsState(
        targetValue = drag,
        animationSpec = if (reduced) tween(0) else spring(dampingRatio = .7f),
        label = "swipe_reply"
    )

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .offset { IntOffset(offset.roundToInt(), 0) }
                .pointerInput(message.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (drag > 65f) onReply()
                            drag = 0f
                        },
                        onDragCancel = { drag = 0f },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            drag = (drag + amount).coerceIn(0f, 100f)
                        }
                    )
                }
                .combinedClickable(
                    onClick = {
                        if (message.mediaUrl.isNotBlank()) onMedia()
                    },
                    onLongClick = onLongClick
                )
        ) {
            GlassCard(
                shape = RoundedCornerShape(22.dp),
                backgroundColor = if (isMe) {
                    if (dark) Color(0xCC353D4A) else Color.White.copy(alpha = .82f)
                } else {
                    if (dark) Color(0xBB2168AA) else Color(0xFFA4D1FF).copy(alpha = .72f)
                },
                elevation = 1.dp
            ) {
                Column(
                    Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (message.replyToText != null) {
                        Text(
                            "${message.replyToSender}: ${message.replyToText}",
                            fontSize = 11.sp,
                            maxLines = 2,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { message.replyToId?.let(onReplyPreviewClick) }
                        )
                    }

                    if (!message.isDeleted) {
                        when (message.type) {
                            MessageType.IMAGE -> PrivateImage(
                                message.mediaUrl,
                                "Photo",
                                Modifier
                                    .fillMaxWidth()
                                    .height(165.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                            MessageType.VIDEO -> PrivateVideoThumbnail(message.mediaUrl, Modifier.fillMaxWidth().height(165.dp))
                            MessageType.VOICE -> VoiceWaveformPlayer(
                                message.voiceDurationSeconds,
                                message.mediaUrl,
                                waveform = message.waveform,
                                isOutgoing = isMe
                            )
                            MessageType.FILE -> Text("▤ Document • Tap to open", Modifier.padding(12.dp))
                            else -> Unit
                        }
                    }

                    if (message.type != MessageType.VOICE || message.isDeleted) {
                        Text(
                            message.text,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        Modifier.align(Alignment.End),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (message.isStarred) Text("★", fontSize = 10.sp)
                        if (message.isEdited) {
                            Text(
                                "edited",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            SimpleDateFormat("h:mm a", Locale.getDefault())
                                .format(Date(message.createdAt)),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                tint = if (message.status == MessageDeliveryStatus.READ) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }

                    if (message.status == MessageDeliveryStatus.FAILED) {
                        Text(
                            "Failed • tap to retry",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable(onClick = onRetry)
                        )
                    }
                }
            }

            if (message.reactions.isNotEmpty()) {
                Row {
                    message.reactions.forEach { reaction ->
                        Text(
                            "${reaction.emoji} ${reaction.userIds.size}",
                            Modifier
                                .clickable { onReactionClick(reaction.emoji) }
                                .padding(4.dp),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
