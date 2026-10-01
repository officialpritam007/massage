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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Lock
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import com.example.notifications.VisibleConversation
import com.example.data.DeletionCoordinator
import com.example.data.local.VoiceDraft
import com.example.data.local.VoiceDraftStore
import com.example.ui.components.DustDelete
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Stop
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
    var deletingThisChat by remember { mutableStateOf(false) }
    LaunchedEffect(conversations, deletingThisChat) {
        if (deletingThisChat && conversations.none { it.id == conversationId }) onBackClick()
    }
    val messageMap by viewModel.messages.collectAsState()
    val me by viewModel.currentUser.collectAsState()
    val upload by viewModel.upload.collectAsState()
    val blocked by viewModel.blockedUserIds.collectAsState()

    val repo = viewModel.repository
    val conversation = conversations.find { it.id == conversationId }
    val other = conversation?.otherUser
        ?: repo.peerForConversation(conversationId)
        ?: User(displayName = "Contact")
    val allMessages = messageMap[conversationId].orEmpty()

    val config = LocalLiquidGlass.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberSaveable(conversationId, saver = LazyListState.Saver) { LazyListState() }
    val density = LocalDensity.current

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
    var initial by rememberSaveable(conversationId) { mutableStateOf(true) }

    var recording by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var elapsed by remember { mutableIntStateOf(0) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var voiceFile by remember { mutableStateOf<File?>(null) }
    var paused by remember { mutableStateOf(false) }
    var voiceWaveform by remember { mutableStateOf<List<Float>>(emptyList()) }
    var voiceDraft by remember(conversationId) { mutableStateOf(VoiceDraftStore.read(context, conversationId)) }
    var showVoicePreview by remember { mutableStateOf(voiceDraft != null) }
    var voiceUploading by remember { mutableStateOf(false) }
    var cancellingRecording by remember { mutableStateOf(false) }
    var recordDragX by remember { mutableFloatStateOf(0f) }
    var recordDragY by remember { mutableFloatStateOf(0f) }
    val initialUnread = rememberSaveable(conversationId) { conversation?.unreadCount ?: 0 }
    var unreadAnchor by rememberSaveable(conversationId) { mutableStateOf<String?>(null) }
    var showTyping by remember(conversationId) { mutableStateOf(false) }
    val rowIdentity = remember(conversationId) { BubbleRowIdentity() }

    fun sendVoice(draft: VoiceDraft) {
        if (voiceUploading) return
        voiceUploading = true
        showVoicePreview = false
        repo.uploadChatMedia(conversationId, Uri.fromFile(draft.file), MessageType.VOICE) { result ->
            voiceUploading = false
            result.onSuccess { url ->
                repo.sendMessage(conversationId, "Voice message", MessageType.VOICE, url,
                    voiceDurationSeconds = draft.seconds, waveform = draft.waveform,
                    localVoicePath = draft.file.absolutePath)
                VoiceDraftStore.clear(context, conversationId, deleteFile = false)
                voiceDraft = null
            }.onFailure { showVoicePreview = true }
        }
    }

    fun stopRecording(send: Boolean, preview: Boolean = locked) {
        val activeRecorder = recorder ?: return
        recorder = null
        val file = voiceFile
        voiceFile = null
        val captured = voiceWaveform
        val stoppedCleanly = runCatching { activeRecorder.stop() }.isSuccess
        runCatching { activeRecorder.release() }
        recording = false; paused = false; locked = false; cancellingRecording = false
        recordDragX = 0f; recordDragY = 0f
        focusManager.clearFocus()
        if (send && stoppedCleanly && file != null && file.length() > 0) {
            val seconds = runCatching {
                val reader = android.media.MediaMetadataRetriever()
                try { reader.setDataSource(file.absolutePath)
                    ((reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) + 999L).div(1000).toInt().coerceAtLeast(1)
                } finally { reader.release() }
            }.getOrDefault(elapsed.coerceAtLeast(1))
            val draft = VoiceDraft(file, seconds, captured)
            voiceDraft = draft
            VoiceDraftStore.save(context, conversationId, draft)
            showVoicePreview = preview
            if (!preview) sendVoice(draft)
        } else file?.delete()
    }

    fun cancelRecording() {
        if (cancellingRecording || !recording) return
        cancellingRecording = true; locked = true
        runCatching { recorder?.pause() }; paused = true
        scope.launch { DeletionCoordinator.perform("recording:$conversationId") { stopRecording(false) } }
    }

    fun startRecording() {
        if (recording || voiceUploading || voiceDraft != null) return
        cancellingRecording = false
        runCatching {
            val file = File.createTempFile("voice-", ".m4a", File(context.filesDir, "voice-drafts").apply { mkdirs() })
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
            voiceFile = file
            recorder = activeRecorder
            activeRecorder.setAudioSamplingRate(44100)
            activeRecorder.prepare()
            activeRecorder.start()
            recorder = activeRecorder
            voiceFile = file
            recording = true
            paused = false
            voiceWaveform = emptyList()
            elapsed = 0
        }.onFailure {
            runCatching { recorder?.release() }; recorder = null
            voiceFile?.delete(); voiceFile = null
            android.widget.Toast.makeText(context, it.message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    val stopCurrent by rememberUpdatedState<(Boolean) -> Unit> { send -> stopRecording(send) }
    val recordingCurrent by rememberUpdatedState(recording)
    val lockedCurrent by rememberUpdatedState(locked)
    val cancellingCurrent by rememberUpdatedState(cancellingRecording)
    val cancelCurrent by rememberUpdatedState<() -> Unit> { cancelRecording() }
    val preserveRecording by rememberUpdatedState<() -> Unit> { stopRecording(true, preview = true) }

    val recordPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        android.widget.Toast.makeText(context, if (granted) "Hold the microphone to record" else "Microphone permission is needed to record", android.widget.Toast.LENGTH_SHORT).show()
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
                val caption = when (type) {
                    MessageType.IMAGE, MessageType.VIDEO -> ""
                    MessageType.VOICE -> "Voice message"
                    MessageType.FILE -> "Document"
                    else -> ""
                }
                viewModel.sendMessage(conversationId, caption, type, url)
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

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycleOwner, conversationId) {
        if (resumed) VisibleConversation.id = conversationId
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) { resumed = true; VisibleConversation.id = conversationId }
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) { resumed = false; if (VisibleConversation.id == conversationId) VisibleConversation.id = null }
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                if (recordingCurrent) preserveRecording()
                repo.setTyping(conversationId, false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            if (VisibleConversation.id == conversationId) VisibleConversation.id = null
            lifecycleOwner.lifecycle.removeObserver(observer)
            preserveRecording()
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
        if (conversation?.isTyping == true) showTyping = true
        else { delay(450); showTyping = false }
    }
    var previousTail by remember(conversationId) { mutableStateOf<String?>(null) }
    LaunchedEffect(allMessages.lastOrNull()?.id, allMessages.size) {
        if (allMessages.isEmpty()) return@LaunchedEffect
        val last = allMessages.last()
        if (initial && initialUnread > 0) unreadAnchor = allMessages.filter { it.senderId != me.uid }.takeLast(initialUnread).firstOrNull()?.id
        val added = previousTail != last.id
        if (initial || (added && (!listState.canScrollForward || last.senderId == me.uid))) {
            delay(40)
            val target = listState.layoutInfo.totalItemsCount - 1
            if (target >= 0) {
                if (initial || config.isReducedMotion) listState.scrollToItem(target)
                else listState.animateScrollToItem(target)
            }
        }
        previousTail = last.id; initial = false
    }
    val visibleMessages = allMessages.filter {
        (!starsOnly || it.isStarred) && (query.isBlank() || it.text.contains(query, true))
    }
    val rows = rowIdentity.rows(visibleMessages, showTyping && query.isBlank() && !starsOnly, me.uid)
    val currentRows by rememberUpdatedState(rows)
    LaunchedEffect(conversationId, resumed) {
        if (!resumed) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.key.toString() } }
            .collectLatest { keys ->
                delay(250)
                repo.markVisibleRead(conversationId, currentRows.filter { it.key in keys }.mapNotNull { it.message?.id })
            }
    }

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
                        DustDelete("upload:$conversationId") { GlassCard(
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
                                    TextButton(onClick = { scope.launch { DeletionCoordinator.perform("upload:$conversationId") { repo.cancelUpload() } } }) { Text("Cancel") }
                                }
                                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    }

                    if (voiceDraft != null && !showVoicePreview && !voiceUploading) {
                        TextButton(onClick = { showVoicePreview = true }) { Text("Resume voice draft") }
                    }
                    if (recording) {
                      DustDelete("recording:$conversationId") {
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
                                        "● ${elapsed / 60}:${(elapsed % 60).toString().padStart(2, '0')}  ${if (cancellingRecording) "Discarding…" else if (paused) "Paused" else if (locked) "Locked" else "← Slide to delete     ↑ Lock"}",
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
                                GlassIconButton(Icons.Default.Delete, "Cancel recording", { cancelRecording() }, size = 38.dp)
                                GlassIconButton(
                                    Icons.Default.Stop,
                                    "Stop and preview recording",
                                    { stopRecording(true, preview = true) },
                                    tint = Color.White,
                                    backgroundColor = config.accentColor.copy(alpha = .86f),
                                    size = 38.dp
                                )
                            }
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

                                Box(contentAlignment = Alignment.BottomCenter) {
                                    if (recording && !locked) {
                                        Column(Modifier.offset(y = (-54).dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.Lock, "Slide up to lock", tint = config.accentColor)
                                            Text("↑", color = config.accentColor)
                                        }
                                    }
                                    Box(
                                        modifier = Modifier.size(44.dp)

                                            .clip(CircleShape)
                                            .background(if (recording) config.accentColor.copy(alpha = .25f) else Color.Transparent)
                                            .pointerInput(conversationId) {
                                                val threshold = with(density) { 78.dp.toPx() }
                                                awaitEachGesture {
                                                    val down = awaitFirstDown(requireUnconsumed = false)
                                                    val held = awaitLongPressOrCancellation(down.id)
                                                    if (held == null) {
                                                        android.widget.Toast.makeText(context, "Hold to record • slide up to lock", android.widget.Toast.LENGTH_SHORT).show()
                                                        return@awaitEachGesture
                                                    }
                                                    held.consume()
                                                    locked = false
                                                    recordDragX = 0f; recordDragY = 0f
                                                    requestCurrent()
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    var gestureLocked = false
                                                    var gestureCancelled = false
                                                    var released = false
                                                    while (!released) {
                                                        val event = awaitPointerEvent()
                                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                        if (!lockedCurrent && !cancellingCurrent) {
                                                            val delta = change.positionChange()
                                                            recordDragX = (recordDragX + delta.x).coerceIn(-threshold, 0f)
                                                            recordDragY = (recordDragY + delta.y).coerceIn(-threshold, 0f)
                                                            if (recordDragX <= -threshold) { gestureCancelled = true; cancelCurrent() }
                                                            else if (recordDragY <= -threshold) {
                                                                gestureLocked = true
                                                                locked = true
                                                                recordDragX = 0f; recordDragY = 0f
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            }
                                                        }
                                                        change.consume()
                                                        released = !change.pressed
                                                    }
                                                    if (recordingCurrent && !gestureLocked && !gestureCancelled && !lockedCurrent && !cancellingCurrent) {
                                                        if (released) stopCurrent(true) else preserveRecording()
                                                    }
                                                }
                                            }, contentAlignment = Alignment.Center
                                    ) { Icon(Icons.Default.Mic, "Hold to record", modifier = Modifier.size(23.dp)) }
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
            DustDelete("chat:$conversationId", Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
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

                    itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                        val actual = row.message
                        val typing = actual == null
                        val message = actual ?: Message(id = row.key, conversationId = conversationId, senderId = other.uid, text = "")
                        val previous = rows.getOrNull(index - 1)?.message
                        val next = rows.getOrNull(index + 1)?.message
                        if (!typing && (previous == null || !sameDay(previous.createdAt, message.createdAt))) DateSeparator(message.createdAt)
                        if (!typing && message.id == unreadAnchor) DateSeparator(message.createdAt, unread = true)
                        DustDelete("message:${message.id}", Modifier.animateItem()) {
                            MessageBubble(
                                message = message, typing = typing,
                                isMe = !typing && message.senderId == me.uid,
                                groupWithPrevious = !typing && previous != null && previous.senderId == message.senderId && sameDay(previous.createdAt, message.createdAt),
                                groupWithNext = !typing && next != null && next.senderId == message.senderId && sameDay(next.createdAt, message.createdAt),
                                onLongClick = { if (!typing) { actions = message; haptic.performHapticFeedback(HapticFeedbackType.LongPress) } },
                                onReply = { if (!typing) { reply = message; haptic.performHapticFeedback(HapticFeedbackType.LongPress) } },
                                onReplyPreviewClick = { id ->
                                    val target = rows.indexOfFirst { it.message?.id == id }
                                    if (target >= 0) scope.launch { listState.animateScrollToItem(target + 1) }
                                },
                                onMedia = { if (!typing) viewer = message },
                                onReactionClick = { emoji -> viewModel.addReaction(conversationId, message.id, emoji) },
                                onRetry = { repo.retryMessage(conversationId, message.id) }
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

        voiceDraft?.takeIf { showVoicePreview }?.let { draft ->
            GlassDialog("Voice preview", { showVoicePreview = false }) {
                DustDelete("voice-draft:${draft.file.name}") {
                    Column {
                        VoiceWaveformPlayer(draft.seconds, Uri.fromFile(draft.file).toString(), waveform = draft.waveform)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassButton("Discard", {
                                scope.launch {
                                    DeletionCoordinator.perform("voice-draft:${draft.file.name}") {
                                        VoiceDraftStore.clear(context, conversationId)
                                        voiceDraft = null; showVoicePreview = false
                                    }
                                }
                            }, Modifier.weight(1f), isPrimary = false)
                            GlassButton("Send", { sendVoice(draft) }, Modifier.weight(1f), enabled = !voiceUploading)
                        }
                    }
                }
            }
        }

        if (deleteChatConfirm) {
            GlassDialog("Delete chat?", { deleteChatConfirm = false }) {
                Text("This permanently removes the existing conversation from your account. It will not return after restart, sign-in, reinstall or sync. A future new message can create a fresh chat without restoring the deleted history.")
                GlassButton("Delete chat", {
                    deletingThisChat = true
                    viewModel.deleteChatForMe(conversationId)
                    deleteChatConfirm = false
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
    typing: Boolean = false,
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

    val maxBubbleWidth = LocalConfiguration.current.screenWidthDp.dp * .82f
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(min = 54.dp, max = maxBubbleWidth)
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
                    if (typing) { TypingDots(reduced) } else {
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
                        ExpandableMessageText(
                            id = message.id,
                            text = if (message.type == MessageType.TEXT || message.isDeleted) message.text else caption,
                            modifier = if (isMedia && !message.isDeleted) Modifier.padding(horizontal = 5.dp, vertical = 2.dp) else Modifier,
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


private data class BubbleRow(val key: String, val message: Message?)
private class BubbleRowIdentity {
    private val keys = mutableMapOf<String, String>()
    private val known = mutableSetOf<String>()
    private var first = true
    private var typingKey: String? = null
    private var wasTyping = false
    fun rows(messages: List<Message>, typing: Boolean, me: String): List<BubbleRow> {
        if (typing && !wasTyping) typingKey = "typing-${java.util.UUID.randomUUID()}"
        if (!first) {
            val incoming = messages.firstOrNull { it.id !in known && it.senderId != me }
            if (incoming != null && typingKey != null) { keys[incoming.id] = typingKey!!; typingKey = null }
        }
        known.addAll(messages.map { it.id }); first = false; wasTyping = typing
        if (!typing) typingKey = null
        return messages.map { BubbleRow(keys[it.id] ?: it.id, it) } +
            (typingKey?.takeIf { typing }?.let { listOf(BubbleRow(it, null)) } ?: emptyList())
    }
}

@Composable
private fun ExpandableMessageText(id: String, text: String, modifier: Modifier = Modifier, color: Color) {
    var expanded by rememberSaveable(id, text) { mutableStateOf(false) }
    var overflow by remember(id, text) { mutableStateOf(false) }
    Column(modifier.animateContentSize()) {
        Text(text, color = color, style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 6, overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflow = it.hasVisualOverflow })
        if (overflow || expanded) Text(if (expanded) "Read less" else "Read more",
            Modifier.clickable { expanded = !expanded }.padding(top = 4.dp),
            color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
    }
}
