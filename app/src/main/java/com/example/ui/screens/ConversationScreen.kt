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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.media.saveMediaToGallery
import com.example.data.model.Message
import com.example.data.model.MessageDeliveryStatus
import com.example.data.model.MessageType
import com.example.data.model.User
import com.example.ui.components.GlassActionRow
import com.example.ui.components.GlassAvatar
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassDialog
import com.example.ui.components.GlassIconButton
import com.example.ui.components.GlassSheet
import com.example.ui.components.GlassTextField
import com.example.ui.components.LiquidBackground
import com.example.ui.components.PrivateImage
import com.example.ui.components.PrivateVideoThumbnail
import com.example.ui.components.VoiceWaveformPlayer
import com.example.ui.components.frostEdges
import com.example.ui.components.lensEdge
import com.example.ui.components.lensHighlight
import com.example.ui.components.rememberLiquidHaptics
import com.example.ui.theme.BubbleIncomingTintDark
import com.example.ui.theme.BubbleIncomingTintLight
import com.example.ui.theme.BubbleMetaTextStyle
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.theme.MetaOnBubbleLight
import com.example.ui.theme.MetaOnAccent
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

// Legacy placeholder captions that must never be shown as a real message caption.
private val GenericMediaLabels = setOf("Photo", "Video", "Voice message", "Document")

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
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        GlassCard(
            shape = RoundedCornerShape(999.dp),
            backgroundColor = if (unread) {
                config.accentColor.copy(alpha = if (config.isDark) .18f else .14f)
            } else if (config.isDark) {
                Color.White.copy(alpha = .05f)
            } else {
                Color.White.copy(alpha = .42f)
            },
            elevation = 0.dp,
            lensing = false
        ) {
            Text(
                if (unread) "New messages" else dayLabel(time),
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
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
    /** Search hit to jump to and highlight on open. */
    initialMessageId: String? = null,
    modifier: Modifier = Modifier
) {
    val conversations by viewModel.conversations.collectAsState()
    val messageMap by viewModel.messages.collectAsState()
    val me by viewModel.currentUser.collectAsState()
    val upload by viewModel.upload.collectAsState()
    val blocked by viewModel.blockedUserIds.collectAsState()
    val privacySettings by viewModel.privacy.collectAsState()
    val directory by viewModel.users.collectAsState()

    val repo = viewModel.repository
    val conversation = conversations.find { it.id == conversationId }
    val other = (conversation?.otherUser ?: repo.peerForConversation(conversationId))
        ?.withDirectoryFallback(directory)
        ?: User(displayName = "Contact")
    val allMessages = messageMap[conversationId].orEmpty()

    val config = LocalLiquidGlass.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptics = rememberLiquidHaptics()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // 6.7"-friendly bubble ceiling: never wider than ~78% of the viewport.
    val maxBubbleWidth = (LocalConfiguration.current.screenWidthDp * 0.78f).dp
    val compactHeader by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 1 || listState.firstVisibleItemScrollOffset > 72
        }
    }
    val avatarSize by animateDpAsState(
        targetValue = if (compactHeader) 27.dp else 35.dp,
        animationSpec = spring(dampingRatio = .82f, stiffness = 420f),
        label = "header_avatar"
    )

    var autoSaved by remember(conversationId) { mutableStateOf(repo.autoSavedMediaIds(conversationId)) }
    var autoSavePrimed by remember(conversationId) { mutableStateOf(false) }
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
    var highlightId by remember(conversationId) { mutableStateOf<String?>(null) }
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

        if (initial && initialMessageId != null) {
            // A search jump owns the first scroll position, so do not snap to the newest message.
            initial = false
        } else {
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
        }
        viewModel.clearUnread(conversationId)
    }

    LaunchedEffect(tailId) {
        if (tailId != null) {
            delay(1100)
            tailId = null
        }
    }

    // Optional gallery auto-save for received photos and videos — off unless the user opts in.
    // Saved ids are persisted, and the existing history is only ever marked as seen, so
    // reopening the app or flipping the switch never dumps or duplicates gallery entries.
    LaunchedEffect(conversationId, allMessages.size, privacySettings.autoSaveReceivedMedia) {
        if (!autoSavePrimed) {
            if (allMessages.isEmpty()) return@LaunchedEffect
            autoSavePrimed = true
            val history = allMessages.filter {
                it.senderId != me.uid &&
                    (it.type == MessageType.IMAGE || it.type == MessageType.VIDEO) &&
                    it.mediaUrl.isNotBlank()
            }.map { it.id }
            if (history.isNotEmpty()) {
                repo.markAutoSavedMedia(conversationId, history)
                autoSaved = repo.autoSavedMediaIds(conversationId)
            }
            return@LaunchedEffect
        }
        if (!privacySettings.autoSaveReceivedMedia) return@LaunchedEffect
        val pending = allMessages.filter {
            it.senderId != me.uid &&
                (it.type == MessageType.IMAGE || it.type == MessageType.VIDEO) &&
                it.mediaUrl.isNotBlank() &&
                it.id !in autoSaved
        }.takeLast(2)
        if (pending.isEmpty()) return@LaunchedEffect
        autoSaved = autoSaved + pending.map { it.id }
        repo.markAutoSavedMedia(conversationId, pending.map { it.id })
        var failures = 0
        pending.forEach { message -> if (saveMediaToGallery(context, message).isFailure) failures++ }
        if (failures > 0) {
            android.widget.Toast.makeText(
                context,
                "Couldn't auto-save $failures file(s). Long-press the message and use Save to gallery.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    val visibleMessages = allMessages.filter {
        (!starsOnly || it.isStarred) && (query.isBlank() || it.text.contains(query, true))
    }
    val morphMessage = visibleMessages.find { it.id == tailId }

    // Jump-to-message: scroll to the search hit and highlight it briefly.
    LaunchedEffect(initialMessageId, visibleMessages.size) {
        val target = initialMessageId ?: return@LaunchedEffect
        val index = visibleMessages.indexOfFirst { it.id == target }
        if (index < 0) return@LaunchedEffect
        highlightId = target
        haptics.tap()
        val targetIndex = index + 1
        if (config.isReducedMotion) listState.scrollToItem(targetIndex) else listState.animateScrollToItem(targetIndex)
        delay(2200)
        highlightId = null
    }

    LiquidBackground(
        modifier = modifier,
        crystal = conversation?.wallpaperIndex != 1,
        scrim = true
    ) {
        Box(Modifier.fillMaxSize().frostEdges(dark = config.isDark)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column(
                    Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    // One balanced floating nav: back, identity and menu share a single glass surface.
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(if (compactHeader) 22.dp else 28.dp),
                        backgroundColor = config.chromeSurface,
                        borderColor = config.chromeBorder,
                        elevation = if (compactHeader) config.compactElevation else config.floatingElevation
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 5.dp, vertical = if (compactHeader) 4.dp else 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NavGlyph(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBackClick)
                            Row(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { onNavigateToProfile(other.uid) }
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GlassAvatar(
                                    other.photoUrl,
                                    other.displayName.ifBlank { "Contact" },
                                    avatarSize,
                                    conversation?.isOnline == true && other.onlineVisible
                                )
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        other.displayName.ifBlank { "Contact" },
                                        style = if (compactHeader) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val label = presenceLabel(other)
                                    if (label.isNotBlank()) {
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (other.isOnline) EmeraldOnline else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            NavGlyph(Icons.Default.MoreHoriz, "Chat menu") { conversationSettings = true }
                        }
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
                            backgroundColor = config.accentFill(0.75f),
                            borderColor = config.chromeBorder,
                            elevation = config.compactElevation,
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
                            backgroundColor = config.insetSurface,
                            borderColor = config.chromeBorder,
                            elevation = config.compactElevation
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
                            shape = RoundedCornerShape(18.dp),
                            backgroundColor = config.accentFill(0.8f),
                            borderColor = config.chromeBorder,
                            elevation = config.compactElevation
                        ) {
                            Column {
                                Row(
                                    Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Uploading ${(progress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = { repo.cancelUpload() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) { Text("Cancel", style = MaterialTheme.typography.labelMedium) }
                                }
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(2.dp)
                                )
                            }
                        }
                    }

                    if (recording) {
                        GlassCard(
                            Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            shape = RoundedCornerShape(24.dp),
                            backgroundColor = config.tintFill(MaterialTheme.colorScheme.error, 0.9f),
                            borderColor = config.chromeBorder,
                            elevation = config.panelElevation
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "● ${elapsed / 60}:${(elapsed % 60).toString().padStart(2, '0')}",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Spacer(Modifier.width(9.dp))
                                    Text(
                                        if (paused) "Paused" else if (locked) "Locked • ready to send" else "Swipe up to lock • left to cancel",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    // Lock state gets its own animated chip so locking feels deliberate.
                                    AnimatedVisibility(
                                        visible = locked,
                                        enter = fadeIn(tween(140)) + scaleIn(initialScale = .7f)
                                    ) {
                                        Row(
                                            Modifier
                                                .padding(end = 6.dp)
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(config.insetSurface)
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Lock,
                                                contentDescription = null,
                                                modifier = Modifier.size(12.dp),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                "Locked",
                                                style = BubbleMetaTextStyle,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                    if (locked) {
                                        GlassIconButton(
                                            if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                            if (paused) "Resume recording" else "Pause recording",
                                            {
                                                val active = recorder
                                                runCatching { if (paused) active?.resume() else active?.pause() }
                                                    .onSuccess {
                                                        paused = !paused
                                                        haptics.toggle()
                                                    }
                                            },
                                            size = 36.dp
                                        )
                                    }
                                    GlassIconButton(Icons.Default.Delete, "Cancel recording", { stopRecording(false) }, size = 36.dp)
                                    GlassIconButton(
                                        Icons.Default.Send,
                                        "Send voice message",
                                        { stopRecording(true) },
                                        tint = Color.White,
                                        backgroundColor = config.solidAccent,
                                        size = 36.dp
                                    )
                                }
                                Spacer(Modifier.height(3.dp))
                                LiveRecordingWaveform(voiceWaveform, Modifier.fillMaxWidth())
                            }
                        }
                    }

                    GlassCard(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(26.dp),
                        backgroundColor = config.chromeSurface,
                        borderColor = config.chromeBorder,
                        elevation = config.floatingElevation
                    ) {
                        Row(
                            Modifier.padding(horizontal = 5.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            GlassIconButton(
                                Icons.Default.Add,
                                "Attach",
                                { attachmentSheet = true },
                                size = 40.dp
                            )
                            Spacer(Modifier.width(3.dp))
                            GlassTextField(
                                text,
                                { if (it.length <= 8000) text = it },
                                placeholder = if (other.uid in blocked) "Contact blocked" else "Message…",
                                modifier = Modifier.weight(1f),
                                singleLine = false,
                                maxLines = 5,
                                shape = RoundedCornerShape(22.dp)
                            )
                            Spacer(Modifier.width(3.dp))

                            // Mic glides into a send orb as soon as there is text to send.
                            AnimatedContent(
                                targetState = text.isBlank(),
                                transitionSpec = {
                                    (fadeIn(tween(150)) + scaleIn(initialScale = .82f)) togetherWith
                                        (fadeOut(tween(110)) + scaleOut(targetScale = .82f))
                                },
                                label = "composer_morph"
                            ) { empty ->
                                if (empty) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        GlassIconButton(
                                            Icons.Default.PhotoCamera,
                                            "Camera",
                                            onNavigateToCamera,
                                            size = 40.dp
                                        )
                                        Spacer(Modifier.width(2.dp))
                                        MicButton(
                                            recording = recordingCurrent,
                                            locked = lockedCurrent,
                                            onStart = { requestCurrent() },
                                            onStop = { send -> stopCurrent(send) },
                                            onLock = {
                                                locked = true
                                                keyboard?.hide()
                                                focusManager.clearFocus()
                                            }
                                        )
                                    }
                                } else {
                                    SendOrb(
                                        enabled = other.uid !in blocked,
                                        accent = config.accentColor,
                                        reduced = config.isReducedMotion,
                                        onSend = {
                                            haptics.confirm()
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
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    // Spacing is applied per bubble so grouped messages sit tighter than new ones.
                    verticalArrangement = Arrangement.Top
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
                        val previousMessage = rows.getOrNull(index - 1)
                        val nextMessage = rows.getOrNull(index + 1)
                        // Group only messages from the same sender inside a short window so the
                        // stack stays tight instead of every bubble claiming full height.
                        val grouped = { other: Message? ->
                            other != null &&
                                other.senderId == message.senderId &&
                                sameDay(other.createdAt, message.createdAt) &&
                                kotlin.math.abs(other.createdAt - message.createdAt) < 4 * 60 * 1000L
                        }
                        MessageBubble(
                            message = message,
                            isMe = message.senderId == me.uid,
                            groupWithPrevious = grouped(previousMessage),
                            groupWithNext = grouped(nextMessage),
                            highlighted = message.id == highlightId,
                            maxWidth = maxBubbleWidth,
                            animateIn = index == rows.lastIndex && !initial && !config.isReducedMotion,
                            hideMediaPreview = privacySettings.hideMediaPreview && message.senderId != me.uid,
                            replyPreview = rows.find { it.id == message.replyToId },
                            onLongClick = {
                                actions = message
                                haptics.confirm()
                            },
                            onReply = {
                                reply = message
                                haptics.tap()
                            },
                            onReplyPreviewClick = { replyId ->
                                val target = rows.indexOfFirst { it.id == replyId }
                                if (target >= 0) scope.launch { listState.animateScrollToItem(target + 1) }
                            },
                            onMedia = { viewer = message },
                            onReactionClick = { emoji ->
                                haptics.toggle()
                                viewModel.addReaction(conversationId, message.id, emoji)
                            },
                            onRetry = { haptics.tap(); repo.retryMessage(conversationId, message.id) }
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
        }

        if (attachmentSheet) {
            GlassSheet("Share something", { attachmentSheet = false }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AttachTile(Icons.Default.PhotoLibrary, "Photo", Modifier.weight(1f)) {
                        attachmentSheet = false
                        imagePicker.launch("image/*")
                    }
                    AttachTile(Icons.Default.PhotoCamera, "Camera", Modifier.weight(1f)) {
                        attachmentSheet = false
                        onNavigateToCamera()
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AttachTile(Icons.Default.Videocam, "Video", Modifier.weight(1f)) {
                        attachmentSheet = false
                        videoPicker.launch("video/*")
                    }
                    AttachTile(Icons.Default.InsertDriveFile, "File", Modifier.weight(1f)) {
                        attachmentSheet = false
                        filePicker.launch("*/*")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Uploads go to private Appwrite storage — never to Firebase Storage — and stay private until you save or share them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        if (conversationSettings) {
            GlassSheet("Conversation", { conversationSettings = false }) {
                GlassActionRow(Icons.Default.Search, "Search messages") {
                    search = !search
                    query = ""
                    conversationSettings = false
                }
                GlassActionRow(Icons.Default.Star, if (starsOnly) "Show all messages" else "Starred messages") {
                    starsOnly = !starsOnly
                    conversationSettings = false
                }
                GlassActionRow(Icons.Default.Star, if (conversation?.isPinned == true) "Remove favorite" else "Add favorite") {
                    repo.setFavorite(conversationId, conversation?.isPinned != true)
                    conversationSettings = false
                }
                GlassActionRow(Icons.Default.MoreHoriz, if (conversation?.isMuted == true) "Unmute" else "Mute") {
                    viewModel.setConversationMuted(conversationId, conversation?.isMuted != true)
                    conversationSettings = false
                }
                GlassActionRow(Icons.Default.Schedule, "Disappearing messages: ${disappearingLabel(conversation?.disappearingSeconds ?: 0L)}") {
                    val next = when (conversation?.disappearingSeconds ?: 0L) {
                        0L -> 86400L
                        86400L -> 604800L
                        else -> 0L
                    }
                    viewModel.setDisappearingMessages(conversationId, next)
                    haptics.toggle()
                }
                GlassActionRow(Icons.Default.Palette, "Toggle crystal / plain wallpaper") {
                    viewModel.setConversationWallpaper(
                        conversationId,
                        if (conversation?.wallpaperIndex == 1) 0 else 1
                    )
                    conversationSettings = false
                }
                GlassActionRow(Icons.Default.Delete, "Delete chat", destructive = true) {
                    conversationSettings = false
                    deleteChatConfirm = true
                }
            }
        }

        actions?.let { message ->
            GlassSheet("Message", { actions = null }) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("❤️", "👍", "😂", "😮", "😢", "🙏").forEach { emoji ->
                        Box(
                            Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(config.insetSurface)
                                .clickable {
                                    haptics.toggle()
                                    viewModel.addReaction(conversationId, message.id, emoji)
                                    actions = null
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 20.sp)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                GlassActionRow(Icons.AutoMirrored.Filled.Reply, "Reply") {
                    reply = message
                    actions = null
                }
                if (message.type == MessageType.TEXT && !message.isDeleted) {
                    GlassActionRow(Icons.Default.ContentCopy, "Copy text") {
                        clipboard.setText(AnnotatedString(message.text))
                        actions = null
                    }
                }
                if ((message.type == MessageType.IMAGE || message.type == MessageType.VIDEO) && !message.isDeleted) {
                    GlassActionRow(Icons.Default.Download, "Save to gallery") {
                        haptics.confirm()
                        val target = message
                        actions = null
                        scope.launch {
                            saveMediaToGallery(context, target)
                                .onSuccess {
                                    android.widget.Toast.makeText(context, "Saved to gallery", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                .onFailure {
                                    android.widget.Toast.makeText(context, it.message ?: "Save failed", android.widget.Toast.LENGTH_LONG).show()
                                }
                        }
                    }
                }
                GlassActionRow(Icons.Default.Star, if (message.isStarred) "Unstar" else "Star") {
                    repo.starMessage(conversationId, message.id)
                    actions = null
                }
                GlassActionRow(Icons.Default.PushPin, if (message.isPinned) "Unpin" else "Pin") {
                    viewModel.pinMessage(conversationId, message.id)
                    actions = null
                }
                GlassActionRow(Icons.Default.Share, "Forward") {
                    forward = message
                    actions = null
                }
                if (message.senderId == me.uid && message.type == MessageType.TEXT && !message.isDeleted) {
                    GlassActionRow(Icons.Default.Edit, "Edit") {
                        editing = message
                        editText = message.text
                        actions = null
                    }
                }
                GlassActionRow(Icons.Default.Delete, "Delete for me") {
                    viewModel.deleteMessageForMe(conversationId, message.id)
                    actions = null
                }
                if (message.senderId == me.uid) {
                    GlassActionRow(Icons.Default.Delete, "Delete for everyone", destructive = true) {
                        deleteForEveryone = message
                        actions = null
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
            GlassSheet("Edit message", { editing = null }) {
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
            GlassSheet("Forward to", { forward = null }) {
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
            GlassSheet("Voice preview", {
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
            GlassSheet("Delete chat?", { deleteChatConfirm = false }) {
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
    val config = LocalLiquidGlass.current
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 5.dp, bottomEnd = 20.dp)
    // Liquid wobble: the bubble breathes with a soft spring pulse while typing is active.
    val wobble by rememberInfiniteTransition(label = "typing_wobble").animateFloat(
        initialValue = 1f,
        targetValue = 1.018f,
        animationSpec = infiniteRepeatable(tween(1150), RepeatMode.Reverse),
        label = "wobble_scale"
    )
    Box(
        Modifier
            .widthIn(min = 58.dp, max = 320.dp)
            .graphicsLayer { scaleX = if (message == null && !reduced) wobble else 1f; scaleY = if (message == null && !reduced) wobble else 1f }
            .clip(shape)
            .background(if (config.isDark) BubbleIncomingTintDark else BubbleIncomingTintLight)
            .border(1.dp, Color.White.copy(alpha = if (config.isDark) .10f else .58f), shape)
            .lensEdge(shape, dark = config.isDark, strength = .7f)
            .animateContentSize(if (reduced) tween(0) else spring(dampingRatio = .72f, stiffness = 360f))
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        if (message == null) {
            TypingDots(reduced)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    when (message.type) {
                        MessageType.TEXT -> message.text
                        MessageType.VOICE -> "Voice message"
                        // Only text messages morph in from the typing bubble, so a generic
                        // "Photo"/"Attachment" placeholder is never invented here.
                        else -> message.text
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.5.sp, lineHeight = 21.sp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.createdAt)),
                        style = BubbleMetaTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        Icons.Default.Done,
                        "Sent",
                        Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
    highlighted: Boolean = false,
    maxWidth: Dp = 330.dp,
    animateIn: Boolean = false,
    hideMediaPreview: Boolean = false,
    replyPreview: Message? = null,
    onLongClick: () -> Unit,
    onReply: () -> Unit,
    onReplyPreviewClick: (String) -> Unit,
    onMedia: () -> Unit,
    onReactionClick: (String) -> Unit,
    onRetry: () -> Unit
) {
    val config = LocalLiquidGlass.current
    val reduced = config.isReducedMotion
    val haptics = rememberLiquidHaptics()
    var drag by remember { mutableFloatStateOf(0f) }
    val offset by animateFloatAsState(
        targetValue = drag,
        animationSpec = if (reduced) tween(0) else spring(dampingRatio = .72f, stiffness = 430f),
        label = "swipe_reply"
    )
    // Newest message springs in from the composer direction; earlier ones are already settled.
    var entered by remember(message.id) { mutableStateOf(!animateIn) }
    val entrance by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = spring(dampingRatio = .68f, stiffness = 340f),
        label = "bubble_entrance"
    )
    LaunchedEffect(message.id) { entered = true }
    // 0f..1f while the bubble is being dragged aside to reply.
    val swipeProgress = (kotlin.math.abs(drag) / 92f).coerceIn(0f, 1f)

    val bubbleShape = if (isMe) {
        RoundedCornerShape(
            topStart = 20.dp,
            topEnd = if (groupWithPrevious) 7.dp else 20.dp,
            bottomStart = 20.dp,
            bottomEnd = if (groupWithNext) 7.dp else 5.dp
        )
    } else {
        RoundedCornerShape(
            topStart = if (groupWithPrevious) 7.dp else 20.dp,
            topEnd = 20.dp,
            bottomStart = if (groupWithNext) 7.dp else 5.dp,
            bottomEnd = 20.dp
        )
    }

    // Sent bubbles carry the accent tint; received bubbles stay a quiet neutral glass.
    val bubbleColor = if (isMe) {
        config.accentColor.copy(alpha = if (config.isDark) .62f else .86f)
    } else {
        if (config.isDark) BubbleIncomingTintDark else BubbleIncomingTintLight
    }
    val contentColor = if (isMe) Color.White else MaterialTheme.colorScheme.onSurface
    val metadataColor = if (isMe) MetaOnAccent else if (config.isDark) MaterialTheme.colorScheme.onSurfaceVariant else MetaOnBubbleLight
    val caption = message.text.trim().takeUnless { it in GenericMediaLabels }.orEmpty()
    val isMedia = message.type == MessageType.IMAGE || message.type == MessageType.VIDEO
    var mediaRatio by remember(message.id) { mutableFloatStateOf(0f) }
    var revealed by remember(message.id) { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            // Grouped messages sit tight; a new sender/day gets breathing room.
            .padding(top = if (groupWithPrevious) 2.dp else 9.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        if (!isMe) SwipeReplyGlyph(swipeProgress)
        Column(
            Modifier
                .widthIn(max = maxWidth)
                .graphicsLayer {
                    alpha = entrance
                    scaleX = 0.94f + entrance * 0.06f
                    scaleY = 0.94f + entrance * 0.06f
                    translationY = (1f - entrance) * 12f
                    // Sent bubbles rise from the composer edge, received ones from the opposite edge.
                    translationX = (1f - entrance) * (if (isMe) 30f else -30f)
                }
                .offset { IntOffset(offset.roundToInt(), 0) }
                .pointerInput(message.id, isMe) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val triggered = if (isMe) drag < -58f else drag > 58f
                            if (triggered) {
                                haptics.tap()
                                onReply()
                            }
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
                    onLongClick = {
                        haptics.confirm()
                        onLongClick()
                    }
                )
        ) {
            GlassCard(
                shape = bubbleShape,
                backgroundColor = bubbleColor,
                overlayBrush = if (isMe) {
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = .30f),
                            Color.Transparent,
                            Color.Black.copy(alpha = .12f)
                        )
                    )
                } else null,
                borderColor = if (highlighted) {
                    config.accentColor.copy(alpha = .85f)
                } else if (isMe) {
                    Color.White.copy(alpha = .34f)
                } else {
                    null
                },
                elevation = if (groupWithPrevious || groupWithNext) 0.dp else 1.dp,
                lensing = !isMe
            ) {
                Column(
                    Modifier
                        .animateContentSize(if (reduced) tween(0) else spring(dampingRatio = .78f, stiffness = 420f))
                        .padding(if (isMedia && !message.isDeleted) 5.dp else 9.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    if (message.replyToText != null) {
                        ReplyQuote(
                            replyToSender = message.replyToSender,
                            replyToText = message.replyToText.orEmpty(),
                            isMe = isMe,
                            contentColor = contentColor,
                            preview = replyPreview,
                            onClick = { message.replyToId?.let(onReplyPreviewClick) }
                        )
                    }

                    if (!message.isDeleted) {
                        when (message.type) {
                            MessageType.IMAGE, MessageType.VIDEO -> {
                                // The container follows the real media ratio, so no black letterboxing.
                                val ratio = if (mediaRatio > 0f) {
                                    mediaRatio.coerceIn(0.62f, 1.85f)
                                } else if (message.type == MessageType.VIDEO) {
                                    16f / 10f
                                } else {
                                    4f / 3f
                                }
                                Box(
                                    Modifier
                                        .widthIn(min = 180.dp, max = (maxWidth - 18.dp).coerceAtLeast(160.dp))
                                        .aspectRatio(ratio)
                                        .clip(RoundedCornerShape(15.dp))
                                ) {
                                    if (message.type == MessageType.IMAGE) {
                                        PrivateImage(
                                            model = message.mediaUrl,
                                            contentDescription = "Photo",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                            cornerRadius = 15.dp,
                                            hidden = hideMediaPreview && !revealed,
                                            onIntrinsicSize = { mediaRatio = it }
                                        )
                                    } else {
                                        PrivateVideoThumbnail(
                                            message.mediaUrl,
                                            Modifier.fillMaxSize(),
                                            hidden = hideMediaPreview && !revealed
                                        )
                                    }

                                    if (hideMediaPreview && !revealed) {
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = .46f))
                                                .clickable { revealed = true },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(Icons.Default.Lock, null, Modifier.size(20.dp), tint = Color.White)
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    "Tap to view",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }

                                    if (isMe && message.status == MessageDeliveryStatus.SENDING) {
                                        Box(
                                            Modifier.fillMaxSize().background(Color.Black.copy(alpha = .26f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(22.dp),
                                                strokeWidth = 2.dp,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                            MessageType.VOICE -> VoiceWaveformPlayer(
                                message.voiceDurationSeconds,
                                message.mediaUrl,
                                waveform = message.waveform,
                                isOutgoing = isMe
                            )
                            MessageType.FILE -> Row(
                                Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.InsertDriveFile, null, Modifier.size(17.dp), tint = contentColor)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    caption.ifBlank { "Document" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = contentColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            else -> Unit
                        }
                    }

                    if (
                        message.type == MessageType.TEXT ||
                        message.isDeleted ||
                        (caption.isNotBlank() && message.type != MessageType.VOICE)
                    ) {
                        val body = if (message.type == MessageType.TEXT || message.isDeleted) message.text else caption
                        Text(
                            if (message.isDeleted) "This message was deleted" else body,
                            modifier = if (isMedia && !message.isDeleted) Modifier.padding(horizontal = 6.dp, vertical = 1.dp) else Modifier,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 15.5.sp,
                                lineHeight = 21.sp,
                                fontStyle = if (message.isDeleted) FontStyle.Italic else FontStyle.Normal
                            ),
                            color = if (message.isDeleted) contentColor.copy(alpha = .62f) else contentColor
                        )
                    }

                    // Compact metadata: timestamp and animated delivery tick share one quiet row.
                    Row(
                        Modifier
                            .align(Alignment.End)
                            .padding(
                                start = if (isMedia) 6.dp else 0.dp,
                                end = if (isMedia) 6.dp else 0.dp,
                                bottom = if (isMedia) 1.dp else 0.dp
                            ),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (message.isStarred) {
                            Icon(Icons.Default.Star, "Starred", Modifier.size(10.dp), tint = metadataColor)
                        }
                        if (message.isPinned) {
                            Icon(Icons.Default.PushPin, "Pinned", Modifier.size(10.dp), tint = metadataColor)
                        }
                        if (message.isEdited) {
                            Text("edited", style = BubbleMetaTextStyle, color = metadataColor)
                        }
                        Text(
                            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.createdAt)),
                            style = BubbleMetaTextStyle,
                            color = metadataColor
                        )
                        if (isMe) {
                            AnimatedContent(
                                targetState = message.status,
                                transitionSpec = {
                                    (fadeIn(tween(180)) + scaleIn(initialScale = .7f)) togetherWith
                                        (fadeOut(tween(120)) + scaleOut(targetScale = .7f))
                                },
                                label = "tick_morph"
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
                                    Modifier.size(13.dp),
                                    tint = when (status) {
                                        MessageDeliveryStatus.READ -> Color(0xFF8FE8FF)
                                        MessageDeliveryStatus.FAILED -> MaterialTheme.colorScheme.error
                                        else -> metadataColor
                                    }
                                )
                            }
                        }
                    }

                    if (message.status == MessageDeliveryStatus.FAILED && isMe) {
                        Row(
                            Modifier
                                .align(Alignment.End)
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = if (config.isDark) .24f else .16f))
                                .clickable(onClick = onRetry)
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.error)
                            Text("Not delivered • tap to retry", style = BubbleMetaTextStyle, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            if (message.reactions.isNotEmpty()) {
                GlassCard(
                    modifier = Modifier.padding(top = 2.dp),
                    shape = RoundedCornerShape(999.dp),
                    backgroundColor = if (config.isDark) Color(0xFF0B151F).copy(alpha = .78f) else Color.White.copy(alpha = .66f),
                    elevation = 2.dp,
                    lensing = false
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
        if (isMe) SwipeReplyGlyph(swipeProgress)
    }
}

/** Reply glyph that expands in beside a bubble while the user swipes to reply. */
@Composable
private fun RowScope.SwipeReplyGlyph(progress: Float) {
    val config = LocalLiquidGlass.current
    Box(
        Modifier
            .align(Alignment.CenterVertically)
            .width((22f * progress).dp)
            .height(22.dp)
            .graphicsLayer { alpha = progress },
        contentAlignment = Alignment.Center
    ) {
        if (progress > 0.02f) {
            Icon(
                Icons.AutoMirrored.Filled.Reply,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = config.accentColor
            )
        }
    }
}

/**
 * Compact translucent quote block: accent edge, sender name, one-line truncation and an
 * optional thumbnail of the original photo/video so a media reply never looks like plain text.
 */
@Composable
private fun ReplyQuote(
    replyToSender: String?,
    replyToText: String,
    isMe: Boolean,
    contentColor: Color,
    preview: Message?,
    onClick: () -> Unit
) {
    val config = LocalLiquidGlass.current
    val accent = if (isMe) Color.White.copy(alpha = .92f) else config.accentColor
    val hasThumb = preview != null && (preview.type == MessageType.IMAGE || preview.type == MessageType.VIDEO)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isMe) Color.White.copy(alpha = .14f) else config.accentColor.copy(alpha = if (config.isDark) .12f else .09f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(2.5.dp)
                .height(if (hasThumb) 32.dp else 26.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(accent)
        )
        Spacer(Modifier.width(8.dp))
        if (preview != null && preview.type == MessageType.IMAGE) {
            PrivateImage(
                model = preview.mediaUrl,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                cornerRadius = 8.dp
            )
            Spacer(Modifier.width(8.dp))
        } else if (preview != null && preview.type == MessageType.VIDEO) {
            PrivateVideoThumbnail(preview.mediaUrl, Modifier.size(32.dp))
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                replyToSender.orEmpty().ifBlank { "Reply" },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                replyToText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = contentColor.copy(alpha = .84f)
            )
        }
    }
}

/** Icon-only nav control that lives inside the floating header card. */
@Composable
private fun NavGlyph(icon: ImageVector, description: String, onClick: () -> Unit) {
    val config = LocalLiquidGlass.current
    val haptics = rememberLiquidHaptics()
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptics.tap()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            description,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(21.dp)
        )
    }
}

/** Hold-to-record mic: drag left cancels, drag up locks — identical behaviour, new shell. */
@Composable
private fun MicButton(
    recording: Boolean,
    locked: Boolean,
    onStart: () -> Unit,
    onStop: (Boolean) -> Unit,
    onLock: () -> Unit
) {
    val config = LocalLiquidGlass.current
    val haptics = rememberLiquidHaptics()
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(config.insetSurface)
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dx = 0f
                        dy = 0f
                        onStart()
                    },
                    onDragEnd = {
                        if (recording && !locked) onStop(true)
                    },
                    onDragCancel = {
                        if (recording && !locked) onStop(false)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dx += dragAmount.x
                        dy += dragAmount.y
                        if (dx < -100f && recording) onStop(false)
                        if (dy < -100f && recording) onLock()
                    }
                )
            }
            .clickable {
                if (!recording) {
                    haptics.confirm()
                    onLock()
                    onStart()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Mic, "Hold to record a voice message", modifier = Modifier.size(20.dp))
    }
}

/** Accent orb that replaces the mic once the composer has text. */
@Composable
private fun SendOrb(
    enabled: Boolean,
    accent: Color,
    reduced: Boolean,
    onSend: () -> Unit
) {
    val config = LocalLiquidGlass.current
    val scale by animateFloatAsState(
        targetValue = if (enabled && !reduced) 1f else 0.9f,
        animationSpec = spring(dampingRatio = .5f, stiffness = 460f),
        label = "send_orb_scale"
    )
    Box(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(40.dp)
            .shadow(
                config.panelElevation,
                CircleShape,
                ambientColor = config.shadowColor,
                spotColor = config.shadowColor.copy(alpha = config.shadowColor.alpha * 0.9f)
            )
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.55f + config.borderStrength * 0.25f))))
            .lensHighlight(dark = true, strength = 0.9f + config.borderStrength * 0.7f)
            .lensEdge(CircleShape, dark = true, strength = 0.6f + config.borderStrength * 0.5f)
            .clickable(enabled = enabled, onClick = onSend),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Send, "Send", tint = Color.White, modifier = Modifier.size(19.dp))
    }
}

/** One frosted tile inside the attachment sheet. */
@Composable
private fun AttachTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val config = LocalLiquidGlass.current
    val haptics = rememberLiquidHaptics()
    GlassCard(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        backgroundColor = config.insetSurface,
        borderColor = config.chromeBorder,
        elevation = config.compactElevation,
        onClick = {
            haptics.tap()
            onClick()
        }
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = config.accentColor)
            Spacer(Modifier.height(7.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun disappearingLabel(seconds: Long): String = when (seconds) {
    0L -> "Off"
    86400L -> "24 hours"
    604800L -> "7 days"
    else -> "${seconds / 3600}h"
}
