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
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.repeatOnLifecycle
import com.example.data.model.Message
import com.example.data.model.MessageType
import com.example.data.model.User
import com.example.data.repository.deleteMessageForEveryoneAwait
import com.example.data.repository.deleteMessageForMeAwait
import com.example.ui.components.GlassAvatar
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassDialog
import com.example.ui.components.GlassIconButton
import com.example.ui.components.GlassTextField
import com.example.ui.components.LiquidBackground
import com.example.ui.components.VoiceWaveformPlayer
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.viewmodel.LiquidChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

private data class VoiceDraftV2(
    val file: File,
    val seconds: Int,
    val waveform: List<Float>,
    val uploading: Boolean = false,
    val failed: Boolean = false
)

private enum class DeleteModeV2 { FOR_ME, FOR_EVERYONE }

private fun appendWaveformV2(existing: List<Float>, value: Float): List<Float> {
    var next = existing + value.coerceIn(.05f, 1f)
    while (next.size > 80) next = next.chunked(2).map { it.average().toFloat() }
    return next
}

@Composable
fun ConversationScreenV2(
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
    val messageJump by viewModel.messageJump.collectAsState()
    val config = LocalLiquidGlass.current
    val repo = viewModel.repository

    val conversation = conversations.firstOrNull { it.id == conversationId }
    val other = conversation?.otherUser ?: repo.peerForConversation(conversationId) ?: User(displayName = "Contact")
    val messages = messageMap[conversationId].orEmpty()

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var text by rememberSaveable(conversationId) { mutableStateOf(repo.draft(conversationId)) }
    var reply by remember { mutableStateOf<Message?>(null) }
    var actionMessage by remember { mutableStateOf<Message?>(null) }
    var editMessage by remember { mutableStateOf<Message?>(null) }
    var editText by remember { mutableStateOf("") }
    var viewer by remember { mutableStateOf<Message?>(null) }
    var attachmentSheet by remember { mutableStateOf(false) }
    var pendingAttachment by remember { mutableStateOf<PendingAttachmentV3?>(null) }
    var attachmentCaption by rememberSaveable(conversationId) { mutableStateOf("") }
    var attachmentDeleting by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf(false) }
    var query by rememberSaveable(conversationId) { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Pair<Message, DeleteModeV2>?>(null) }
    var deletingId by remember { mutableStateOf<String?>(null) }
    var deleteRetry by remember { mutableStateOf<Pair<Message, DeleteModeV2>?>(null) }

    var recording by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var elapsed by remember { mutableIntStateOf(0) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var waveform by remember { mutableStateOf<List<Float>>(emptyList()) }
    var voiceDraft by remember { mutableStateOf<VoiceDraftV2?>(null) }
    var voiceDraftDeleting by remember { mutableStateOf(false) }
    var voiceDraftDeleteFailed by remember { mutableStateOf(false) }

    var morphId by remember(conversationId) { mutableStateOf<String?>(null) }
    var typingAt by remember(conversationId) { mutableLongStateOf(0L) }
    var lastRemoteId by remember(conversationId) { mutableStateOf<String?>(null) }
    var previousLastId by remember(conversationId) { mutableStateOf<String?>(null) }
    var newMessageCount by remember(conversationId) { mutableIntStateOf(0) }
    var initialOpen by remember(conversationId) { mutableStateOf(true) }
    var unreadAnchorId by rememberSaveable(conversationId) { mutableStateOf<String?>(null) }
    var stickToBottom by remember(conversationId) { mutableStateOf(true) }
    var searchHighlightId by remember(conversationId) { mutableStateOf<String?>(null) }

    val nearBottom by remember { derivedStateOf { !listState.canScrollForward } }

    fun sendVoiceDraft(draft: VoiceDraftV2) {
        if (draft.uploading || voiceDraftDeleting || !draft.file.exists() || draft.file.length() <= 0L) return
        voiceDraft = draft.copy(uploading = true, failed = false)
        voiceDraftDeleteFailed = false
        repo.uploadChatMedia(conversationId, Uri.fromFile(draft.file), MessageType.VOICE) { result ->
            result.onSuccess { url ->
                viewModel.sendMessage(
                    conversationId = conversationId,
                    text = "Voice message",
                    type = MessageType.VOICE,
                    mediaUrl = url,
                    voiceDurationSeconds = draft.seconds,
                    waveform = draft.waveform
                )
                draft.file.delete()
                if (voiceDraft?.file == draft.file) voiceDraft = null
            }.onFailure {
                if (draft.file.exists()) voiceDraft = draft.copy(uploading = false, failed = true)
            }
        }
    }

    fun discardVoiceDraft(draft: VoiceDraftV2) {
        if (draft.uploading || voiceDraftDeleting) return
        voiceDraftDeleting = true
        voiceDraftDeleteFailed = false
        scope.launch {
            delay(if (config.isReducedMotion) 90 else 520)
            val removed = !draft.file.exists() || draft.file.delete()
            if (removed) {
                if (voiceDraft?.file == draft.file) voiceDraft = null
            } else {
                voiceDraftDeleteFailed = true
            }
            voiceDraftDeleting = false
        }
    }

    fun finishRecording(keep: Boolean, autoSend: Boolean = false) {
        val active = recorder
        recorder = null
        val file = recordingFile
        recordingFile = null
        val seconds = elapsed.coerceAtLeast(1)
        val samples = waveform
        val stopped = runCatching { active?.stop() }.isSuccess
        runCatching { active?.release() }
        recording = false
        locked = false
        paused = false
        focus.clearFocus()

        if (keep && stopped && file != null && file.exists() && file.length() > 0L) {
            voiceDraft?.takeUnless { it.uploading }?.file?.delete()
            val draft = VoiceDraftV2(file, seconds, samples)
            voiceDraft = draft
            voiceDraftDeleteFailed = false
            if (autoSend) sendVoiceDraft(draft)
        } else {
            file?.delete()
        }
    }

    fun startRecording() {
        if (recording || other.uid in blocked) return
        runCatching {
            val file = File.createTempFile("voice-", ".aac", context.cacheDir)
            val active = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            active.setAudioSource(MediaRecorder.AudioSource.MIC)
            active.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            active.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            active.setAudioEncodingBitRate(64000)
            active.setAudioSamplingRate(44100)
            active.setAudioChannels(1)
            active.setOutputFile(file.absolutePath)
            active.prepare()
            active.start()
            recorder = active
            recordingFile = file
            elapsed = 0
            waveform = emptyList()
            paused = false
            recording = true
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }.onFailure {
            android.widget.Toast.makeText(context, it.message ?: "Unable to record", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    val finishCurrent by rememberUpdatedState<(Boolean, Boolean) -> Unit> { keep, auto -> finishRecording(keep, auto) }
    val recordingCurrent by rememberUpdatedState(recording)
    val lockedCurrent by rememberUpdatedState(locked)

    val recordPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val message = if (granted) "Microphone ready — hold the mic to record" else "Microphone permission is required for voice messages"
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    fun requestRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
        else recordPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    val requestCurrent by rememberUpdatedState<() -> Unit> { requestRecording() }

    fun uploadAttachment(uri: Uri, type: MessageType, caption: String = "") {
        repo.uploadChatMedia(conversationId, uri, type) { result ->
            result.onSuccess { url ->
                val fallback = when (type) {
                    MessageType.IMAGE -> "Photo"
                    MessageType.VIDEO -> "Video"
                    MessageType.FILE -> "Document"
                    else -> ""
                }
                val textToSend = caption.trim().ifBlank { fallback }
                viewModel.sendMessage(conversationId, textToSend, type, url)
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            attachmentCaption = ""
            pendingAttachment = PendingAttachmentV3(uri, MessageType.IMAGE)
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            attachmentCaption = ""
            pendingAttachment = PendingAttachmentV3(uri, MessageType.VIDEO)
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            attachmentCaption = ""
            pendingAttachment = PendingAttachmentV3(uri, MessageType.FILE)
        }
    }

    LaunchedEffect(conversationId) { viewModel.observeConversation(conversationId) }
    LaunchedEffect(recording, paused) {
        while (recording) {
            delay(1000)
            if (!paused) elapsed++
            if (elapsed >= 600) finishRecording(keep = true, autoSend = false)
        }
    }
    LaunchedEffect(recording, paused) {
        while (recording) {
            if (!paused) {
                val amp = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                waveform = appendWaveformV2(waveform, sqrt((amp / 32767f).coerceIn(0f, 1f)).coerceAtLeast(.05f))
            }
            delay(120)
        }
    }
    LaunchedEffect(text) {
        repo.saveDraft(conversationId, text)
        if (text.isNotBlank()) {
            repo.setTyping(conversationId, true)
            delay(2600)
        }
        repo.setTyping(conversationId, false)
    }
    LaunchedEffect(conversation?.isTyping) { if (conversation?.isTyping == true) typingAt = System.currentTimeMillis() }
    LaunchedEffect(messages.lastOrNull()?.id) {
        val remote = messages.lastOrNull { it.senderId != me.uid }
        if (remote != null && remote.id != lastRemoteId && remote.type == MessageType.TEXT && System.currentTimeMillis() - typingAt < 8000) {
            morphId = remote.id
            delay(if (config.isReducedMotion) 120 else 1050)
            morphId = null
        }
        lastRemoteId = remote?.id
    }
    LaunchedEffect(messages.size, conversation?.unreadCount) {
        if (unreadAnchorId == null) {
            val unread = conversation?.unreadCount ?: 0
            if (unread > 0 && messages.isNotEmpty()) {
                unreadAnchorId = messages.firstOrNull { it.senderId != me.uid && !it.isDeleted &&
                    !it.isViewed && it.status != com.example.data.model.MessageDeliveryStatus.READ && !repo.wasViewed(conversationId, it.id) }?.id
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to nearBottom }
            .distinctUntilChanged()
            .collect { (scrolling, atBottom) ->
                if (scrolling) stickToBottom = atBottom
                if (atBottom) newMessageCount = 0
            }
    }
    LaunchedEffect(messages.lastOrNull()?.id) {
        val last = messages.lastOrNull() ?: return@LaunchedEffect
        val follow = com.example.domain.shouldFollowMessages(initialOpen, previousLastId, last.id, stickToBottom)
        val appended = previousLastId != null && previousLastId != last.id
        previousLastId = last.id
        if (follow && messageJump?.first != conversationId && query.isBlank()) {
            androidx.compose.runtime.withFrameNanos { }
            if (stickToBottom && !listState.isScrollInProgress) {
                val target = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                if (config.isReducedMotion || initialOpen) listState.scrollToItem(target)
                else listState.animateScrollToItem(target)
                newMessageCount = 0
            }
        } else if (appended && last.senderId != me.uid) {
            newMessageCount++
            if (unreadAnchorId == null) unreadAnchorId = last.id
        }
        initialOpen = false
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                if (recordingCurrent) finishCurrent(false, false)
                repo.setTyping(conversationId, false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { recorder?.release() }
            recordingFile?.delete()
            voiceDraft?.takeUnless { it.uploading }?.file?.delete()
            repo.setTyping(conversationId, false)
        }
    }

    fun performDelete(message: Message, mode: DeleteModeV2) {
        if (deletingId != null) return
        deletingId = message.id
        deleteRetry = null
        scope.launch {
            delay(if (config.isReducedMotion) 90 else 520)
            val result = when (mode) {
                DeleteModeV2.FOR_ME -> repo.deleteMessageForMeAwait(conversationId, message.id)
                DeleteModeV2.FOR_EVERYONE -> repo.deleteMessageForEveryoneAwait(conversationId, message.id)
            }
            deletingId = null
            deleteRetry = if (result.isFailure) message to mode else null
        }
    }

    val filtered = messages.filter { query.isBlank() || it.text.contains(query, ignoreCase = true) }
    val morphMessage = filtered.firstOrNull { it.id == morphId }
    val rows = filtered.filterNot { it.id == morphId }
    val latestRemoteId = rows.lastOrNull { it.senderId != me.uid && !it.isDeleted }?.id

    LaunchedEffect(messageJump, rows.size, conversationId) {
        val jump = messageJump
        if (jump?.first != conversationId) return@LaunchedEffect
        val messageId = jump.second
        val target = rows.indexOfFirst { it.id == messageId }
        if (target >= 0) {
            stickToBottom = false
            initialOpen = false
            delay(90)
            if (config.isReducedMotion) listState.scrollToItem(target + 1)
            else listState.animateScrollToItem(target + 1)
            searchHighlightId = messageId
            delay(if (config.isReducedMotion) 250 else 900)
            if (searchHighlightId == messageId) searchHighlightId = null
            viewModel.clearMessageJump(conversationId, messageId)
        } else {
            repo.loadOlder(conversationId)
        }
    }

    LaunchedEffect(conversationId, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo.filter {
                    it.offset < listState.layoutInfo.viewportEndOffset && it.offset + it.size > listState.layoutInfo.viewportStartOffset
                }.mapNotNull { it.key as? String }.toSet()
            }.distinctUntilChanged().collect { visible ->
                repo.markVisibleRead(conversationId, visible)
            }
        }
    }

    LiquidBackground(modifier = modifier, crystal = conversation?.wallpaperIndex != 1) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column(Modifier.statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBackClick, size = 44.dp)
                        GlassCard(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(28.dp),
                            backgroundColor = if (config.isDark) Color(0xFF0D1723).copy(alpha = .72f) else Color.White.copy(alpha = .70f),
                            elevation = 5.dp,
                            onClick = { onNavigateToProfile(other.uid) }
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                GlassAvatar(other.photoUrl, other.displayName.ifBlank { "Contact" }, 34.dp, conversation?.isOnline == true && other.onlineVisible)
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(other.displayName.ifBlank { "Contact" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                    val label = presenceLabel(other)
                                    if (label.isNotBlank()) Text(label, style = MaterialTheme.typography.labelSmall, color = if (other.isOnline) EmeraldOnline else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                        }
                        GlassIconButton(Icons.Default.MoreHoriz, "Chat menu", { menu = true }, size = 44.dp)
                    }
                    AnimatedVisibility(search) {
                        GlassTextField(query, { query = it }, placeholder = "Search messages", modifier = Modifier.padding(top = 7.dp), shape = RoundedCornerShape(24.dp), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None))
                    }
                }
            },
            bottomBar = {
                Column(
                    Modifier.imePadding().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    reply?.let { target ->
                        GlassCard(shape = RoundedCornerShape(20.dp), elevation = 1.dp) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(target.senderName.ifBlank { "Reply" }, color = config.accentColor, style = MaterialTheme.typography.labelMedium)
                                    Text(target.text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                }
                                GlassIconButton(Icons.Default.Close, "Cancel reply", { reply = null }, size = 34.dp)
                            }
                        }
                    }

                    voiceDraft?.let { draft ->
                        DustDeleteContainerV2(active = voiceDraftDeleting, reduced = config.isReducedMotion, modifier = Modifier.fillMaxWidth()) {
                            GlassCard(shape = RoundedCornerShape(22.dp), backgroundColor = if (draft.failed || voiceDraftDeleteFailed) MaterialTheme.colorScheme.error.copy(alpha = .09f) else config.accentColor.copy(alpha = .08f), elevation = 1.dp) {
                                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            when {
                                                draft.uploading -> "Uploading voice message…"
                                                voiceDraftDeleteFailed -> "Discard failed — recording kept"
                                                draft.failed -> "Upload failed — recording kept"
                                                else -> "Voice preview"
                                            },
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (draft.failed || voiceDraftDeleteFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text("${draft.seconds / 60}:${(draft.seconds % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.labelSmall)
                                    }
                                    VoiceWaveformPlayer(draft.seconds, Uri.fromFile(draft.file).toString(), draft.waveform, isOutgoing = true)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        GlassButton(if (voiceDraftDeleteFailed) "Retry discard" else "Discard", { discardVoiceDraft(draft) }, Modifier.weight(1f), isPrimary = false, enabled = !draft.uploading && !voiceDraftDeleting)
                                        GlassButton(if (draft.failed) "Retry" else "Send", { sendVoiceDraft(draft.copy(uploading = false)) }, Modifier.weight(1f), isLoading = draft.uploading, enabled = !draft.uploading && !voiceDraftDeleting)
                                    }
                                }
                            }
                        }
                    }

                    if (recording) {
                        VoiceRecorderPanelV3(
                            elapsedSeconds = elapsed,
                            locked = locked,
                            paused = paused,
                            waveform = waveform,
                            reducedMotion = config.isReducedMotion,
                            onPauseResume = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    runCatching { if (paused) recorder?.resume() else recorder?.pause() }.onSuccess { paused = !paused }
                                }
                            },
                            onDiscard = { finishRecording(false, false) },
                            onStopPreview = { finishRecording(true, false) },
                            onSend = { finishRecording(true, true) }
                        )
                    }

                    pendingAttachment?.let { attachment ->
                        AttachmentPreviewV3(
                            attachment = attachment,
                            caption = attachmentCaption,
                            deleting = attachmentDeleting,
                            onCaptionChange = { attachmentCaption = it },
                            onDiscard = {
                                if (!attachmentDeleting) {
                                    attachmentDeleting = true
                                    scope.launch {
                                        delay(if (config.isReducedMotion) 90 else 520)
                                        pendingAttachment = null
                                        attachmentCaption = ""
                                        attachmentDeleting = false
                                    }
                                }
                            },
                            onSend = {
                                if (!attachmentDeleting) {
                                    val selected = attachment
                                    val caption = attachmentCaption
                                    pendingAttachment = null
                                    attachmentCaption = ""
                                    uploadAttachment(selected.uri, selected.type, caption)
                                }
                            }
                        )
                    }

                    upload?.let { progress ->
                        if (voiceDraft?.uploading != true) {
                            GlassCard(shape = RoundedCornerShape(18.dp), backgroundColor = config.accentColor.copy(alpha = .07f), elevation = 0.dp) {
                                Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Uploading ${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                                        TextButton(onClick = { repo.cancelUpload() }) { Text("Cancel") }
                                    }
                                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }

                    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp), backgroundColor = if (config.isDark) Color(0xFF0C1620).copy(alpha = .78f) else Color.White.copy(alpha = .72f), elevation = 8.dp) {
                        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            GlassIconButton(Icons.Default.Add, "Attach", { attachmentSheet = true }, size = 40.dp)
                            Spacer(Modifier.width(3.dp))
                            GlassTextField(text, { if (it.length <= 8000) text = it }, placeholder = if (other.uid in blocked) "Contact blocked" else "Message…", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None), singleLine = false, maxLines = 5, shape = RoundedCornerShape(24.dp))
                            Spacer(Modifier.width(3.dp))
                            if (text.isBlank()) {
                                GlassIconButton(Icons.Default.PhotoCamera, "Camera", onNavigateToCamera, size = 40.dp)
                                var dx by remember { mutableFloatStateOf(0f) }
                                var dy by remember { mutableFloatStateOf(0f) }
                                val micX = if (recording && !locked) dx.coerceIn(-120f, 0f) else 0f
                                val micY = if (recording && !locked) dy.coerceIn(-120f, 0f) else 0f
                                Box(
                                    Modifier.size(40.dp).offset { IntOffset(micX.roundToInt(), micY.roundToInt()) }.clip(CircleShape)
                                        .background(if (config.isDark) Color.White.copy(alpha = .08f) else Color.White.copy(alpha = .58f))
                                        .pointerInput(other.uid, blocked) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    dx = 0f; dy = 0f; locked = false
                                                    keyboard?.hide(); focus.clearFocus(); requestCurrent()
                                                },
                                                onDragEnd = {
                                                    if (recordingCurrent && !lockedCurrent) finishCurrent(true, true)
                                                    dx = 0f; dy = 0f
                                                },
                                                onDragCancel = {
                                                    if (recordingCurrent && !lockedCurrent) finishCurrent(false, false)
                                                    dx = 0f; dy = 0f
                                                },
                                                onDrag = { change, amount ->
                                                    change.consume(); dx += amount.x; dy += amount.y
                                                    if (dx < -100f && recordingCurrent) {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        finishCurrent(false, false); dx = 0f; dy = 0f
                                                    } else if (dy < -100f && recordingCurrent && !lockedCurrent) {
                                                        locked = true; dx = 0f; dy = 0f
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Default.Mic, "Press and hold to record", Modifier.size(21.dp), tint = MaterialTheme.colorScheme.onSurface) }
                            } else {
                                GlassIconButton(Icons.Default.Send, "Send", {
                                    if (text.isNotBlank() && other.uid !in blocked) {
                                        stickToBottom = true
                                        viewModel.sendMessage(conversationId, text.trim(), replyToId = reply?.id, replyToText = reply?.text, replyToSender = reply?.senderName)
                                        text = ""; reply = null
                                    }
                                }, tint = Color.White, backgroundColor = config.accentColor.copy(alpha = .90f), size = 40.dp)
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    item(key = "load-earlier") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TextButton(onClick = { repo.loadOlder(conversationId) }) { Text("Load earlier messages") } }
                    }
                    itemsIndexed(rows, key = { _, item -> item.id }) { index, message ->
                        if (index == 0 || dayKeyV3(rows[index - 1].createdAt) != dayKeyV3(message.createdAt)) DateSeparatorV3(message.createdAt)
                        if (message.id == unreadAnchorId) UnreadSeparatorV2()
                        DustDeleteContainerV2(active = deletingId == message.id, reduced = config.isReducedMotion, modifier = Modifier.animateItem().fillMaxWidth()) {
                            MessageBubbleV2(
                                message = message,
                                isMe = message.senderId == me.uid,
                                reduced = config.isReducedMotion,
                                highlighted = message.id == searchHighlightId,
                                voiceAvatarUrl = if (message.senderId == me.uid) me.photoUrl else other.photoUrl,
                                voiceAvatarName = if (message.senderId == me.uid) me.displayName else other.displayName,
                                onLongClick = { actionMessage = message; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                onReply = { reply = message; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                onReplyPreviewClick = { replyId ->
                                    val target = rows.indexOfFirst { it.id == replyId }
                                    if (target >= 0) scope.launch {
                                        stickToBottom = false
                                        listState.animateScrollToItem(target + 1)
                                        searchHighlightId = replyId
                                        delay(900)
                                        searchHighlightId = null
                                    } else viewModel.requestMessageJump(conversationId, replyId)
                                },
                                onMedia = { viewer = message },
                                onReaction = { emoji -> viewModel.addReaction(conversationId, message.id, emoji) },
                                onRetrySend = { repo.retryMessage(conversationId, message.id) }
                            )
                        }
                        if (deleteRetry?.first?.id == message.id && deletingId != message.id) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.senderId == me.uid) Arrangement.End else Arrangement.Start) {
                                TextButton(onClick = { deleteRetry?.let { performDelete(it.first, it.second) } }) { Text("Delete failed • Retry", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                    item(key = "typing-morph") {
                        AnimatedVisibility(visible = morphMessage != null || conversation?.isTyping == true, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                            TypingMorphBubbleV2(morphMessage, config.isReducedMotion)
                        }
                    }
                }
                if (!stickToBottom && listState.layoutInfo.totalItemsCount > 0) {
                    SmallFloatingActionButton(onClick = {
                        stickToBottom = true
                        newMessageCount = 0
                        scope.launch { listState.animateScrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) }
                    }, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)) { Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.KeyboardArrowDown, "Jump to newest")
                        if (newMessageCount > 0) Text("New messages ($newMessageCount)")
                    } }
                }
            }
        }

        if (attachmentSheet) {
            GlassDialog("Share content", { attachmentSheet = false }) {
                TextButton(onClick = { attachmentSheet = false; onNavigateToCamera() }) { Text("Camera") }
                TextButton(onClick = { attachmentSheet = false; imagePicker.launch("image/*") }) { Text("Photo gallery") }
                TextButton(onClick = { attachmentSheet = false; videoPicker.launch("video/*") }) { Text("Video gallery") }
                TextButton(onClick = { attachmentSheet = false; filePicker.launch("*/*") }) { Text("Document") }
            }
        }

        if (menu) {
            GlassDialog("Conversation", { menu = false }) {
                TextButton(onClick = { search = !search; if (!search) query = ""; menu = false }) { Text(if (search) "Close search" else "Search messages") }
                TextButton(onClick = { viewModel.setConversationMuted(conversationId, conversation?.isMuted != true); menu = false }) { Text(if (conversation?.isMuted == true) "Unmute" else "Mute") }
                TextButton(onClick = { repo.setFavorite(conversationId, conversation?.isPinned != true); menu = false }) { Text(if (conversation?.isPinned == true) "Remove favorite" else "Add favorite") }
            }
        }

        actionMessage?.let { message ->
            MessageActionSheetV3(
                message = message,
                isMine = message.senderId == me.uid,
                onDismiss = { actionMessage = null },
                onReaction = { emoji -> viewModel.addReaction(conversationId, message.id, emoji); actionMessage = null },
                onReply = { reply = message; actionMessage = null },
                onCopy = { clipboard.setText(AnnotatedString(message.text)); actionMessage = null },
                onToggleStar = { repo.starMessage(conversationId, message.id); actionMessage = null },
                onTogglePin = { viewModel.pinMessage(conversationId, message.id); actionMessage = null },
                onEdit = { editMessage = message; editText = message.text; actionMessage = null },
                onDeleteForMe = { deleteTarget = message to DeleteModeV2.FOR_ME; actionMessage = null },
                onDeleteForEveryone = { deleteTarget = message to DeleteModeV2.FOR_EVERYONE; actionMessage = null }
            )
        }

        deleteTarget?.let { (message, mode) ->
            GlassDialog(if (mode == DeleteModeV2.FOR_EVERYONE) "Delete for everyone?" else "Delete message?", { deleteTarget = null }) {
                Text(if (mode == DeleteModeV2.FOR_EVERYONE) "This message will be removed for both people." else "This message will be removed from your chat.")
                GlassButton("Delete", { deleteTarget = null; performDelete(message, mode) }, modifier = Modifier.fillMaxWidth())
            }
        }

        editMessage?.let { message ->
            GlassDialog("Edit message", { editMessage = null }) {
                GlassTextField(editText, { if (it.length <= 8000) editText = it }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None), singleLine = false, maxLines = 6)
                GlassButton("Save", {
                    if (editText.isNotBlank()) viewModel.editMessage(conversationId, message.id, editText.trim())
                    editMessage = null
                }, modifier = Modifier.fillMaxWidth(), enabled = editText.isNotBlank())
            }
        }

        viewer?.let { message -> MediaViewer(message) { viewer = null } }
    }
}

@Composable
private fun UnreadSeparatorV2() {
    val config = LocalLiquidGlass.current
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        GlassCard(shape = RoundedCornerShape(999.dp), backgroundColor = config.accentColor.copy(alpha = if (config.isDark) .13f else .10f), elevation = 0.dp) {
            Text("Unread messages", Modifier.padding(horizontal = 12.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = config.accentColor, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TypingMorphBubbleV2(message: Message?, reduced: Boolean) {
    val config = LocalLiquidGlass.current
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 7.dp, bottomEnd = 22.dp)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier.widthIn(min = 54.dp, max = 330.dp).clip(shape)
                .background(if (config.isDark) Color.White.copy(alpha = .07f) else Color.White.copy(alpha = .68f))
                .border(1.dp, Color.White.copy(alpha = if (config.isDark) .10f else .58f), shape)
                .animateContentSize(if (reduced) tween(0) else spring(dampingRatio = .72f, stiffness = 360f))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (message == null) TypingDotsV2(reduced) else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(message.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.createdAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.End))
            }
        }
    }
}

@Composable
private fun TypingDotsV2(reduced: Boolean) {
    val accent = LocalLiquidGlass.current.accentColor
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(vertical = 3.dp)) {
        repeat(3) { index ->
            val y = if (reduced) 0f else {
                val transition = rememberInfiniteTransition(label = "typing-v2-$index")
                val value by transition.animateFloat(initialValue = 0f, targetValue = -3.5f, animationSpec = infiniteRepeatable(tween(330, delayMillis = index * 95), repeatMode = RepeatMode.Reverse), label = "dot-v2")
                value
            }
            Box(Modifier.offset(y = y.dp).size(6.dp).clip(CircleShape).background(accent.copy(alpha = .9f)))
        }
    }
}

private fun dayKeyV3(time: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(time))

@Composable
private fun DateSeparatorV3(time: Long) {
    val yesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }.timeInMillis
    val label = when (dayKeyV3(time)) {
        dayKeyV3(System.currentTimeMillis()) -> "Today"
        dayKeyV3(yesterday) -> "Yesterday"
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(time))
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
        GlassCard(shape = RoundedCornerShape(99.dp), elevation = 0.dp) {
            Text(label, Modifier.padding(horizontal = 13.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall)
        }
    }
}
