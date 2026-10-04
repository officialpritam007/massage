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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.platform.LocalDensity
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
import com.example.data.model.Message
import com.example.data.model.MessageType
import com.example.data.model.User
import com.example.data.repository.deleteMessageForEveryoneAwait
import com.example.notifications.ChatNotificationState
import com.example.data.repository.deleteMessageForMeAwait
import com.example.ui.components.GlassAvatar
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassDialog
import com.example.ui.components.GlassIconButton
import com.example.ui.components.GlassTextField
import com.example.ui.components.LiquidBackground
import com.example.ui.components.VoiceWaveformPlayer
import com.example.ui.components.liquidPressFeedback
import com.example.ui.components.liquidRoundedShape
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.viewmodel.LiquidChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
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

private fun compactPresenceLabelV3(user: User): String {
    val full = presenceLabel(user)
    return when {
        full == "Online" -> full
        full.startsWith("Last seen today at ") ->
            "Last seen " + full.removePrefix("Last seen today at ")
        full.startsWith("Last seen yesterday at ") ->
            "Yesterday • " + full.removePrefix("Last seen yesterday at ")
        full.startsWith("Last seen ") ->
            full.removePrefix("Last seen ").replace(" at ", " • ")
        else -> full
    }
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
    val conversationFlow = remember(viewModel, conversationId) {
        viewModel.conversations.map { items -> items.firstOrNull { it.id == conversationId } }.distinctUntilChanged()
    }
    val messageFlow = remember(viewModel, conversationId) {
        viewModel.messages.map { it[conversationId].orEmpty() }.distinctUntilChanged()
    }
    val conversation by conversationFlow.collectAsStateWithLifecycle(
        initialValue = viewModel.conversations.value.firstOrNull { it.id == conversationId }
    )
    val messages by messageFlow.collectAsStateWithLifecycle(
        initialValue = viewModel.messages.value[conversationId].orEmpty()
    )
    val me by viewModel.currentUser.collectAsStateWithLifecycle()
    val upload by viewModel.upload.collectAsStateWithLifecycle()
    val blocked by viewModel.blockedUserIds.collectAsStateWithLifecycle()
    val messageJump by viewModel.messageJump.collectAsStateWithLifecycle()
    val config = LocalLiquidGlass.current
    val repo = viewModel.repository
    val historyHasOlder by repo.historyHasOlder.collectAsStateWithLifecycle()
    val historyLoading by repo.historyLoading.collectAsStateWithLifecycle()
    val canLoadOlder = historyHasOlder[conversationId] == true
    val loadingOlder = historyLoading[conversationId] == true

    val other = conversation?.otherUser ?: repo.peerForConversation(conversationId) ?: User(displayName = "Contact")
    val e2eeReady = other.e2eePublicKey.isNotBlank() && other.e2eeKeyId.isNotBlank()

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val density = LocalDensity.current
    val clipboard = LocalClipboardManager.current
    val imeBottom = WindowInsets.ime.getBottom(density)
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
    var locallyHiddenDeletes by remember(conversationId) { mutableStateOf<Set<String>>(emptySet()) }

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
    var initialOpen by remember(conversationId) { mutableStateOf(true) }
    var unreadAnchorId by rememberSaveable(conversationId) { mutableStateOf<String?>(null) }
    var stickToBottom by remember(conversationId) { mutableStateOf(true) }
    var searchHighlightId by remember(conversationId) { mutableStateOf<String?>(null) }
    var historyAnchorId by remember(conversationId) { mutableStateOf<String?>(null) }
    var historyAnchorOffset by remember(conversationId) { mutableIntStateOf(0) }
    var historyLoadGestureConsumed by remember(conversationId) { mutableStateOf(false) }

    val nearBottom by remember { derivedStateOf { !listState.canScrollForward } }

    val headerCollapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 1 || listState.firstVisibleItemScrollOffset > 72
        }
    }
    val headerButtonSize by animateDpAsState(
        targetValue = if (headerCollapsed) 34.dp else 38.dp,
        animationSpec = if (config.isReducedMotion) tween(0)
        else spring(dampingRatio = .78f, stiffness = 430f),
        label = "header_button_size"
    )
    val headerAvatarSize by animateDpAsState(
        targetValue = if (headerCollapsed) 27.dp else 30.dp,
        animationSpec = if (config.isReducedMotion) tween(0)
        else spring(dampingRatio = .78f, stiffness = 430f),
        label = "header_avatar_size"
    )
    val headerVerticalPadding by animateDpAsState(
        targetValue = if (headerCollapsed) 3.dp else 5.dp,
        animationSpec = if (config.isReducedMotion) tween(0)
        else spring(dampingRatio = .78f, stiffness = 430f),
        label = "header_padding"
    )

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

    var recordPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val micPermissionInteraction = remember { MutableInteractionSource() }
    val recordPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        recordPermissionGranted = granted
        val message = if (granted) "Microphone ready — hold the mic to record" else "Microphone permission is required for voice messages"
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    fun requestRecording() {
        if (!e2eeReady) {
            android.widget.Toast.makeText(
                context,
                "Encryption setup pending. Ask this contact to open Liquid Chat 4.1.0.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }
        if (recordPermissionGranted) startRecording()
    }
    val requestCurrent by rememberUpdatedState<() -> Unit> { requestRecording() }

    fun uploadAttachment(uri: Uri, type: MessageType, caption: String = "") {
        if (!e2eeReady) {
            android.widget.Toast.makeText(
                context,
                "Encryption setup pending. Ask this contact to open Liquid Chat 4.1.0.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }
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

    DisposableEffect(conversationId, other.uid) {
        ChatNotificationState.setActiveChat(conversationId, other.uid)
        onDispose { ChatNotificationState.clearActiveChat(conversationId) }
    }
    LaunchedEffect(conversationId) {
        ChatNotificationState.foregroundMessages
            .filter { it.conversationId == conversationId }
            .collect { event -> repo.refreshMessageFromPush(event.conversationId, event.messageId) }
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
                val incoming = messages.filter { it.senderId != me.uid && !it.isDeleted }
                unreadAnchorId = incoming.takeLast(minOf(unread, incoming.size)).firstOrNull()?.id
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to nearBottom }
            .distinctUntilChanged()
            .collect { (scrolling, atBottom) ->
                if (scrolling) stickToBottom = atBottom else if (atBottom) stickToBottom = true
            }
    }
    LaunchedEffect(messages.lastOrNull()?.id, messages.size) {
        if (messages.isEmpty()) return@LaunchedEffect
        val last = messages.last()
        val shouldFollow = initialOpen || last.senderId == me.uid || stickToBottom
        if (shouldFollow) {
            delay(50)
            val target = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
            if (config.isReducedMotion) listState.scrollToItem(target) else listState.animateScrollToItem(target)
        } else if (last.senderId != me.uid && unreadAnchorId == null) unreadAnchorId = last.id
        initialOpen = false
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val atBottomCurrent by rememberUpdatedState(nearBottom)
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) repo.setConversationVisible(conversationId, atBottomCurrent)
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) repo.setConversationVisible(conversationId, false)
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                if (recordingCurrent) finishCurrent(false, false)
                repo.setTyping(conversationId, false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        repo.setConversationVisible(conversationId, atBottomCurrent && lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
        onDispose {
            repo.setConversationVisible(conversationId, false)
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { recorder?.release() }
            recordingFile?.delete()
            voiceDraft?.takeUnless { it.uploading }?.file?.delete()
            repo.setTyping(conversationId, false)
        }
    }
    LaunchedEffect(conversationId, lifecycleOwner, listState) {
        snapshotFlow { nearBottom }.distinctUntilChanged().collect { atBottom ->
            repo.setConversationVisible(conversationId, atBottom &&
                lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
        }
    }

    fun performDelete(message: Message, mode: DeleteModeV2) {
        if (deletingId != null || message.id in locallyHiddenDeletes) return
        deletingId = message.id
        deleteRetry = null
        scope.launch {
            delay(if (config.isReducedMotion) 70 else 300)
            locallyHiddenDeletes = locallyHiddenDeletes + message.id
            deletingId = null

            val result = when (mode) {
                DeleteModeV2.FOR_ME -> repo.deleteMessageForMeAwait(conversationId, message.id)
                DeleteModeV2.FOR_EVERYONE -> repo.deleteMessageForEveryoneAwait(conversationId, message.id)
            }

            if (result.isFailure) {
                locallyHiddenDeletes = locallyHiddenDeletes - message.id
                deleteRetry = message to mode
            } else {
                // Keep the local tombstone until the Firestore listener confirms
                // the message is actually gone/hidden. Clearing it immediately
                // causes a brief ghost reappearance between API success and sync.
                deleteRetry = null
            }
        }
    }

    val filtered = messages.filter { query.isBlank() || it.text.contains(query, ignoreCase = true) }
    val morphMessage = filtered.firstOrNull { it.id == morphId }
    val rows = filtered.filterNot { it.id == morphId || it.id in locallyHiddenDeletes }
    val latestRemoteId = rows.lastOrNull { it.senderId != me.uid && !it.isDeleted }?.id

    LaunchedEffect(messages.map { it.id }, locallyHiddenDeletes) {
        if (locallyHiddenDeletes.isEmpty()) return@LaunchedEffect
        val liveIds = messages.asSequence().map { it.id }.toSet()
        val confirmedGone = locallyHiddenDeletes.filterNot { it in liveIds }.toSet()
        if (confirmedGone.isNotEmpty()) {
            locallyHiddenDeletes = locallyHiddenDeletes - confirmedGone
        }
    }

    LaunchedEffect(loadingOlder, rows.size) {
        val anchorId = historyAnchorId ?: return@LaunchedEffect
        if (loadingOlder) return@LaunchedEffect
        val index = rows.indexOfFirst { it.id == anchorId }
        if (index >= 0) {
            listState.scrollToItem(index, historyAnchorOffset)
        }
        historyAnchorId = null
    }

    LaunchedEffect(listState, canLoadOlder, loadingOlder, rows.size) {
        snapshotFlow {
            Triple(
                listState.isScrollInProgress,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
            .distinctUntilChanged()
            .collect { (scrolling, firstIndex, firstOffset) ->
                if (!scrolling) {
                    historyLoadGestureConsumed = false
                    return@collect
                }
                if (!canLoadOlder || loadingOlder || rows.isEmpty() || historyLoadGestureConsumed) {
                    return@collect
                }

                val atTop = firstIndex <= 1 && firstOffset < 72
                if (!atTop || historyAnchorId != null) return@collect

                historyLoadGestureConsumed = true
                val firstVisible = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                    rows.any { it.id == item.key }
                }
                historyAnchorId = firstVisible?.key as? String
                historyAnchorOffset = firstVisible?.offset ?: 0
                repo.loadOlder(conversationId)
            }
    }

    LaunchedEffect(imeBottom, stickToBottom, rows.size) {
        if (!stickToBottom || rows.isEmpty()) return@LaunchedEffect
        delay(40)
        val target = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
        if (config.isReducedMotion) {
            listState.scrollToItem(target)
        } else {
            listState.animateScrollToItem(target)
        }
    }

    LaunchedEffect(messageJump, rows.size, conversationId) {
        val jump = messageJump
        if (jump?.first != conversationId) return@LaunchedEffect
        val messageId = jump.second
        val target = rows.indexOfFirst { it.id == messageId }
        if (target >= 0) {
            delay(90)
            if (config.isReducedMotion) listState.scrollToItem(target + 1)
            else listState.animateScrollToItem(target + 1)
            searchHighlightId = messageId
            delay(if (config.isReducedMotion) 250 else 900)
            if (searchHighlightId == messageId) searchHighlightId = null
            viewModel.clearMessageJump(conversationId, messageId)
        }
    }

    LaunchedEffect(conversationId, latestRemoteId, conversation?.unreadCount) {
        if ((conversation?.unreadCount ?: 0) <= 0 || latestRemoteId == null) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { item -> item.key == latestRemoteId } }
            .distinctUntilChanged()
            .collect { latestIncomingVisible ->
                if (latestIncomingVisible && lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                    viewModel.clearUnread(conversationId)
                    unreadAnchorId = null
                }
            }
    }

    LiquidBackground(modifier = modifier, crystal = conversation?.wallpaperIndex != 1) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column(
                    Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 9.dp, vertical = headerVerticalPadding)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GlassIconButton(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            onBackClick,
                            size = headerButtonSize
                        )
                        GlassCard(
                            modifier = Modifier.weight(1f),
                            shape = liquidRoundedShape(24f),
                            backgroundColor = if (config.isDark) {
                                Color(0xFF142A31).copy(alpha = .74f)
                            } else {
                                Color.White.copy(alpha = .60f)
                            },
                            elevation = 4.dp,
                            onClick = { onNavigateToProfile(other.uid) }
                        ) {
                            Row(
                                Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GlassAvatar(
                                    other.photoUrl,
                                    other.displayName.ifBlank { "Contact" },
                                    headerAvatarSize,
                                    conversation?.isOnline == true && other.onlineVisible
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        other.displayName.ifBlank { "Contact" },
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val label = compactPresenceLabelV3(other)
                                    if (label.isNotBlank() || other.e2eeKeyId.isNotBlank()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            if (other.e2eeKeyId.isNotBlank()) {
                                                Icon(
                                                    Icons.Default.Lock,
                                                    "End-to-end encrypted",
                                                    modifier = Modifier.size(10.dp),
                                                    tint = config.accentColor
                                                )
                                            }
                                            Text(
                                                if (label.isNotBlank()) label else "End-to-end encrypted",
                                                fontSize = 10.sp,
                                                color = if (other.isOnline) {
                                                    EmeraldOnline
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        GlassIconButton(
                            Icons.Default.MoreHoriz,
                            "Chat menu",
                            { menu = true },
                            size = headerButtonSize
                        )
                    }
                    AnimatedVisibility(search) {
                        GlassTextField(query, { query = it }, placeholder = "Search messages", modifier = Modifier.padding(top = 7.dp), shape = liquidRoundedShape(24f), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None))
                    }
                    AnimatedVisibility(!e2eeReady && other.uid.isNotBlank()) {
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            shape = liquidRoundedShape(18f),
                            backgroundColor = MaterialTheme.colorScheme.error.copy(alpha = .08f),
                            elevation = 0.dp
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    "Encryption setup pending — this contact must open Liquid Chat 4.1.0",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            },
            bottomBar = {
                Column(
                    Modifier.imePadding().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    voiceDraft?.let { draft ->
                        DustDeleteContainerV2(active = voiceDraftDeleting, reduced = config.isReducedMotion, modifier = Modifier.fillMaxWidth()) {
                            GlassCard(shape = liquidRoundedShape(22f), backgroundColor = if (draft.failed || voiceDraftDeleteFailed) MaterialTheme.colorScheme.error.copy(alpha = .09f) else config.accentColor.copy(alpha = .08f), elevation = 1.dp) {
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

                    AnimatedVisibility(
                        visible = recording,
                        enter = fadeIn(tween(if (config.isReducedMotion) 0 else 140)) +
                            expandVertically(animationSpec = tween(if (config.isReducedMotion) 0 else 180)),
                        exit = fadeOut(tween(if (config.isReducedMotion) 0 else 110)) +
                            shrinkVertically(animationSpec = tween(if (config.isReducedMotion) 0 else 150))
                    ) {
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
                            GlassCard(shape = liquidRoundedShape(18f), backgroundColor = config.accentColor.copy(alpha = .07f), elevation = 0.dp) {
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

                    GlassCard(
                        Modifier.fillMaxWidth(),
                        shape = liquidRoundedShape(27f),
                        backgroundColor = if (config.isDark) {
                            Color(0xFF12262D).copy(alpha = .84f)
                        } else {
                            Color.White.copy(alpha = .62f)
                        },
                        elevation = 7.dp
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .animateContentSize(
                                    if (config.isReducedMotion) tween(0)
                                    else spring(dampingRatio = .76f, stiffness = 420f)
                                )
                        ) {
                            reply?.let { target ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(config.accentColor.copy(alpha = .075f))
                                        .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier
                                            .width(3.dp)
                                            .size(width = 3.dp, height = 32.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(config.accentColor)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            target.senderName.ifBlank { "Reply" },
                                            color = config.accentColor,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            target.text,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    GlassIconButton(
                                        Icons.Default.Close,
                                        "Cancel reply",
                                        { reply = null },
                                        size = 30.dp
                                    )
                                }
                            }

                            Row(
                                Modifier.padding(horizontal = 3.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GlassIconButton(
                                    Icons.Default.Add,
                                    "Attach",
                                    {
                                        if (e2eeReady) {
                                            attachmentSheet = true
                                        } else {
                                            android.widget.Toast.makeText(
                                                context,
                                                "Encryption setup pending. Ask this contact to open Liquid Chat 4.1.0.",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    },
                                    size = 36.dp
                                )
                                Spacer(Modifier.width(3.dp))
                                GlassTextField(
                                    value = text,
                                    onValueChange = { if (it.length <= 8000) text = it },
                                    placeholder = if (other.uid in blocked) "Contact blocked" else "Message…",
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                                    singleLine = false,
                                    maxLines = 5,
                                    shape = liquidRoundedShape(22f),
                                    minHeight = 40.dp,
                                    horizontalPadding = 11.dp,
                                    verticalPadding = 7.dp
                                )
                                Spacer(Modifier.width(3.dp))
                                AnimatedContent(
                                    targetState = text.isBlank(),
                                    transitionSpec = {
                                        fadeIn(tween(if (config.isReducedMotion) 0 else 140)) togetherWith
                                            fadeOut(tween(if (config.isReducedMotion) 0 else 100))
                                    },
                                    label = "composer_action"
                                ) { blank ->
                                    if (blank) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            GlassIconButton(
                                                Icons.Default.PhotoCamera,
                                                "Camera",
                                                {
                                                    if (e2eeReady) {
                                                        onNavigateToCamera()
                                                    } else {
                                                        android.widget.Toast.makeText(
                                                            context,
                                                            "Encryption setup pending. Ask this contact to open Liquid Chat 4.1.0.",
                                                            android.widget.Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                                },
                                                size = 36.dp
                                            )
                                            var dx by remember { mutableFloatStateOf(0f) }
                                            var dy by remember { mutableFloatStateOf(0f) }
                                            val micX = if (recording && !locked) dx.coerceIn(-120f, 0f) else 0f
                                            val micY = if (recording && !locked) dy.coerceIn(-120f, 0f) else 0f
                                            Box(
                                                Modifier
                                                    .size(36.dp)
                                                    .offset { IntOffset(micX.roundToInt(), micY.roundToInt()) }
                                                    .liquidPressFeedback(pressedScale = .95f, enabled = !config.isReducedMotion)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (config.isDark) Color.White.copy(alpha = .08f)
                                                        else Color.White.copy(alpha = .50f)
                                                    )
                                                    .then(
                                                        if (recordPermissionGranted) {
                                                            Modifier.pointerInput(other.uid, blocked, recordPermissionGranted) {
                                                                detectDragGesturesAfterLongPress(
                                                                    onDragStart = {
                                                                        dx = 0f
                                                                        dy = 0f
                                                                        locked = false
                                                                        keyboard?.hide()
                                                                        focus.clearFocus()
                                                                        requestCurrent()
                                                                    },
                                                                    onDragEnd = {
                                                                        if (recordingCurrent && !lockedCurrent) finishCurrent(true, true)
                                                                        dx = 0f
                                                                        dy = 0f
                                                                    },
                                                                    onDragCancel = {
                                                                        if (recordingCurrent && !lockedCurrent) finishCurrent(false, false)
                                                                        dx = 0f
                                                                        dy = 0f
                                                                    },
                                                                    onDrag = { change, amount ->
                                                                        change.consume()
                                                                        dx += amount.x
                                                                        dy += amount.y
                                                                        if (dx < -100f && recordingCurrent) {
                                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                            finishCurrent(false, false)
                                                                            dx = 0f
                                                                            dy = 0f
                                                                        } else if (dy < -100f && recordingCurrent && !lockedCurrent) {
                                                                            locked = true
                                                                            dx = 0f
                                                                            dy = 0f
                                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                        }
                                                                    }
                                                                )
                                                            }
                                                        } else {
                                                            Modifier.clickable(
                                                                interactionSource = micPermissionInteraction,
                                                                indication = null
                                                            ) {
                                                                recordPermission.launch(Manifest.permission.RECORD_AUDIO)
                                                            }
                                                        }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Mic,
                                                    "Press and hold to record",
                                                    Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    } else {
                                        GlassIconButton(
                                            Icons.Default.Send,
                                            "Send",
                                            {
                                                if (text.isNotBlank() && other.uid !in blocked) {
                                                    if (!e2eeReady) {
                                                        android.widget.Toast.makeText(
                                                            context,
                                                            "Encryption setup pending. Ask this contact to open Liquid Chat 4.1.0.",
                                                            android.widget.Toast.LENGTH_LONG
                                                        ).show()
                                                        return@GlassIconButton
                                                    }
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                                            backgroundColor = config.accentColor.copy(alpha = .92f),
                                            size = 36.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.Bottom)
                ) {
                    if (loadingOlder) {
                        item(key = "history-loading") {
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                    itemsIndexed(rows, key = { _, item -> item.id }) { _, message ->
                        if (message.id == unreadAnchorId) UnreadSeparatorV2()
                        Box(
                            Modifier
                                .animateItem(
                                    fadeInSpec = if (config.isReducedMotion) tween(0) else tween(170),
                                    placementSpec = if (config.isReducedMotion) tween(0)
                                    else spring(dampingRatio = .78f, stiffness = 390f),
                                    fadeOutSpec = if (config.isReducedMotion) tween(0) else tween(130)
                                )
                                .fillMaxWidth()
                        ) {
                            MessageBubbleV2(
                                message = message,
                                isMe = message.senderId == me.uid,
                                reduced = config.isReducedMotion,
                                deleting = deletingId == message.id,
                                highlighted = message.id == searchHighlightId ||
                                    actionMessage?.id == message.id ||
                                    deleteTarget?.first?.id == message.id ||
                                    editMessage?.id == message.id,
                                voiceAvatarUrl = if (message.senderId == me.uid) me.photoUrl else other.photoUrl,
                                voiceAvatarName = if (message.senderId == me.uid) me.displayName else other.displayName,
                                onLongClick = { actionMessage = message; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                onReply = { reply = message; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                onReplyPreviewClick = { replyId ->
                                    val target = rows.indexOfFirst { it.id == replyId }
                                    if (target >= 0) scope.launch { listState.animateScrollToItem(target + 1) }
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
                        scope.launch { listState.animateScrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) }
                    }, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)) { Icon(Icons.Default.KeyboardArrowDown, "Jump to newest") }
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
            CompactDeleteMessageDialogV3(
                forEveryone = mode == DeleteModeV2.FOR_EVERYONE,
                onDismiss = { deleteTarget = null },
                onConfirm = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    deleteTarget = null
                    performDelete(message, mode)
                }
            )
        }

        editMessage?.let { message ->
            CompactEditMessageDialogV3(
                value = editText,
                onValueChange = { editText = it },
                onDismiss = { editMessage = null },
                onSave = {
                    if (editText.isNotBlank()) {
                        viewModel.editMessage(conversationId, message.id, editText.trim())
                    }
                    editMessage = null
                }
            )
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
