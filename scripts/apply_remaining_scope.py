from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def patch_backend():
    path = ROOT / "appwrite-functions/liquid-api/src/main.js"
    text = path.read_text()

    if "function sanitizeWaveform" not in text:
        marker = "function previewFor(m) {"
        helper = """function sanitizeWaveform(value) {
  if (!Array.isArray(value)) return [];
  return value.map(Number).filter(Number.isFinite).map(x => Math.max(0.05, Math.min(1, x))).slice(0, 80);
}

function expectedMimePrefix(type) {
  if (type === 'IMAGE') return 'image/';
  if (type === 'VIDEO') return 'video/';
  if (type === 'VOICE') return 'audio/';
  return '';
}

"""
        text = replace_once(text, marker, helper + marker, "backend helper insertion")

    text = text.replace(
        "          voiceDurationSeconds: source.voiceDurationSeconds || 0,\n          createdAt: now,",
        "          voiceDurationSeconds: source.voiceDurationSeconds || 0,\n          waveform: sanitizeWaveform(source.waveform),\n          createdAt: now,",
        1,
    )

    old_media = """      if (p.mediaUrl) {
        const mm = (await db.doc('media/' + String(p.mediaUrl).replace('appwrite:', '')).get()).data();
        if (!mm?.ready || mm.owner !== uid || mm.conversationId !== p.conversationId) throw new Error('Invalid media attachment');
      }
      if (p.type !== 'TEXT' && !p.mediaUrl) throw new Error('Upload media first');"""
    new_media = """      if (p.mediaUrl) {
        const mm = (await db.doc('media/' + String(p.mediaUrl).replace('appwrite:', '')).get()).data();
        if (!mm?.ready || mm.owner !== uid || mm.conversationId !== p.conversationId) throw new Error('Invalid media attachment');
        const prefix = expectedMimePrefix(p.type);
        const mime = String(mm.mimeType || '').toLowerCase();
        if (prefix && !mime.startsWith(prefix)) throw new Error(`Attachment type does not match ${p.type.toLowerCase()} message`);
        if (p.type === 'FILE' && !mime) throw new Error('Unknown attachment type');
      }
      if (p.type !== 'TEXT' && !p.mediaUrl) throw new Error('Upload media first');"""
    if old_media in text:
        text = replace_once(text, old_media, new_media, "backend MIME validation")

    old_tx = """        const existing = await t.get(mref);
        const current = await t.get(ref);
        if (existing.exists) {
          if (existing.data().senderId !== uid) throw new Error('Invalid message ID');
          return;
        }
        const sec = current.data().disappearingSeconds || 0;
        const m = {"""
    new_tx = """        const existing = await t.get(mref);
        const current = await t.get(ref);
        if (existing.exists) {
          if (existing.data().senderId !== uid) throw new Error('Invalid message ID');
          return;
        }
        const hiddenFor = [];
        for (const participant of c.participantIds) {
          const hidden = await t.get(db.doc(`messageHiddenTombstones/${p.conversationId}_${p.id}_${participant}`));
          if (hidden.exists) hiddenFor.push(participant);
        }
        const sec = current.data().disappearingSeconds || 0;
        const m = {"""
    if old_tx in text:
        text = replace_once(text, old_tx, new_tx, "backend hidden tombstone reads")

    text = text.replace(
        "          voiceDurationSeconds: Math.min(600, Math.max(0, Number(p.voiceDurationSeconds) || 0)),\n          createdAt: now,",
        "          voiceDurationSeconds: Math.min(600, Math.max(0, Number(p.voiceDurationSeconds) || 0)),\n          waveform: p.type === 'VOICE' ? sanitizeWaveform(p.waveform) : [],\n          createdAt: now,",
        1,
    )
    text = text.replace("          hiddenFor: [],\n          isEdited: false,", "          hiddenFor,\n          isEdited: false,", 1)

    old_me = """    if (p.action === 'deleteForMe') {
      const s = await mref.get();
      if (!s.exists) return res.json({ok: true});
      await db.runTransaction(async t => {
        const snap = await t.get(mref);
        if (!snap.exists) return;
        t.update(mref, {hiddenFor: FieldValue.arrayUnion(uid)});
        const current = await t.get(ref);
        if (current.data()?.lastMessageId === String(p.messageId)) {
          t.update(ref, {[`hiddenLastFor.${uid}`]: String(p.messageId)});
        }
      });
      return res.json({ok: true});
    }"""
    new_me = """    if (p.action === 'deleteForMe') {
      const hiddenTomb = db.doc(`messageHiddenTombstones/${p.conversationId}_${p.messageId}_${uid}`);
      await db.runTransaction(async t => {
        const snap = await t.get(mref);
        const current = await t.get(ref);
        t.set(hiddenTomb, {
          conversationId: p.conversationId,
          messageId: String(p.messageId),
          uid,
          deletedAt: now
        }, {merge: true});
        if (snap.exists) t.update(mref, {hiddenFor: FieldValue.arrayUnion(uid)});
        if (current.data()?.lastMessageId === String(p.messageId)) {
          t.update(ref, {[`hiddenLastFor.${uid}`]: String(p.messageId)});
        }
      });
      return res.json({ok: true});
    }"""
    if old_me in text:
        text = replace_once(text, old_me, new_me, "backend delete for me")

    old_everyone = """    if (p.action === 'deleteForEveryone' || p.action === 'delete') {
      let mediaUrl = '';
      await db.runTransaction(async t => {
        const s = await t.get(mref);
        const m = s.data();
        if (!m) return;
        if (m.senderId !== uid) throw new Error('Only the sender can do this');
        mediaUrl = m.mediaUrl || '';
        const tomb = db.doc(`messageTombstones/${p.conversationId}_${p.messageId}`);
        t.set(tomb, {
          conversationId: p.conversationId,
          messageId: String(p.messageId),
          senderId: m.senderId,
          deletedBy: uid,
          deletedAt: now,
          type: m.type || 'TEXT',
          mediaRef: mediaUrl || ''
        }, {merge: true});
        t.delete(mref);
      });
      await revokeMediaFromConversation(db, mediaUrl, p.conversationId);
      await refreshConversationSummary(ref);
      await notifyDeletion(db, p.conversationId, String(p.messageId), uid);
      return res.json({ok: true});
    }"""
    new_everyone = """    if (p.action === 'deleteForEveryone' || p.action === 'delete') {
      let mediaUrl = '';
      await db.runTransaction(async t => {
        const s = await t.get(mref);
        const m = s.data();
        if (m && m.senderId !== uid) throw new Error('Only the sender can do this');
        mediaUrl = m?.mediaUrl || '';
        const tomb = db.doc(`messageTombstones/${p.conversationId}_${p.messageId}`);
        t.set(tomb, {
          conversationId: p.conversationId,
          messageId: String(p.messageId),
          senderId: m?.senderId || uid,
          deletedBy: uid,
          deletedAt: now,
          type: m?.type || 'TEXT',
          mediaRef: mediaUrl || ''
        }, {merge: true});
        if (s.exists) t.delete(mref);
      });
      await revokeMediaFromConversation(db, mediaUrl, p.conversationId);
      await refreshConversationSummary(ref);
      await notifyDeletion(db, p.conversationId, String(p.messageId), uid);
      return res.json({ok: true});
    }"""
    if old_everyone in text:
        text = replace_once(text, old_everyone, new_everyone, "backend delete everyone")

    path.write_text(text)


def patch_conversation():
    path = ROOT / "app/src/main/java/com/example/ui/screens/ConversationScreen.kt"
    text = path.read_text()

    # Imports
    text = text.replace(
        "import androidx.compose.foundation.lazy.items\n",
        "import androidx.compose.foundation.lazy.items\nimport androidx.compose.foundation.lazy.itemsIndexed\n",
        1,
    )
    text = text.replace(
        "import androidx.compose.material.icons.filled.MoreHoriz\n",
        "import androidx.compose.material.icons.filled.MoreHoriz\nimport androidx.compose.material.icons.filled.Pause\nimport androidx.compose.material.icons.filled.PlayArrow\n",
        1,
    )
    text = text.replace(
        "import androidx.compose.ui.platform.LocalHapticFeedback\n",
        "import androidx.compose.ui.platform.LocalHapticFeedback\nimport androidx.compose.ui.platform.LocalFocusManager\nimport androidx.compose.ui.platform.LocalSoftwareKeyboardController\n",
        1,
    )
    text = text.replace(
        "import com.example.ui.components.PrivateImage\n",
        "import com.example.ui.components.PrivateImage\nimport com.example.ui.components.PrivateVideoThumbnail\n",
        1,
    )
    if "import kotlin.math.sqrt" not in text:
        text = text.replace("import kotlin.math.roundToInt\n", "import kotlin.math.roundToInt\nimport kotlin.math.sqrt\n", 1)

    if "private data class VoiceDraft" not in text:
        marker = "@Composable\nfun ConversationScreen("
        helper = '''private data class VoiceDraft(val file: File, val seconds: Int, val waveform: List<Float>)\n\nprivate fun appendWaveform(existing: List<Float>, value: Float): List<Float> {\n    var next = existing + value.coerceIn(.05f, 1f)\n    while (next.size > 80) {\n        next = next.chunked(2).map { chunk -> chunk.average().toFloat() }\n    }\n    return next\n}\n\nprivate fun sameDay(a: Long, b: Long): Boolean {\n    val ca = Calendar.getInstance().apply { timeInMillis = a }\n    val cb = Calendar.getInstance().apply { timeInMillis = b }\n    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)\n}\n\nprivate fun dayLabel(time: Long): String {\n    val now = Calendar.getInstance()\n    val day = Calendar.getInstance().apply { timeInMillis = time }\n    if (now.get(Calendar.YEAR) == day.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR)) return "Today"\n    now.add(Calendar.DAY_OF_YEAR, -1)\n    if (now.get(Calendar.YEAR) == day.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR)) return "Yesterday"\n    return SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(time))\n}\n\n@Composable\nprivate fun DateSeparator(time: Long, unread: Boolean = false) {\n    Box(Modifier.fillMaxWidth().padding(vertical = 5.dp), contentAlignment = Alignment.Center) {\n        GlassCard(shape = RoundedCornerShape(999.dp)) {\n            Text(if (unread) "Unread messages" else dayLabel(time), Modifier.padding(horizontal = 12.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall)\n        }\n    }\n}\n\n@Composable\nprivate fun LiveRecordingWaveform(values: List<Float>, modifier: Modifier = Modifier) {\n    androidx.compose.foundation.Canvas(modifier.height(30.dp)) {\n        val bars = values.takeLast(32)\n        if (bars.isEmpty()) return@Canvas\n        val gap = 3.dp.toPx()\n        val width = ((size.width - gap * (bars.size - 1)) / bars.size).coerceAtLeast(2.dp.toPx())\n        bars.forEachIndexed { index, amp ->\n            val h = size.height * amp.coerceIn(.08f, 1f)\n            val x = index * (width + gap) + width / 2\n            drawLine(MaterialTheme.colorScheme.error, Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2), width)\n        }\n    }\n}\n\n'''
        # Canvas draw block cannot access MaterialTheme in DrawScope directly at execution; capture color outside.
        helper = helper.replace('    androidx.compose.foundation.Canvas(modifier.height(30.dp)) {\\n        val bars', '    val waveformColor = MaterialTheme.colorScheme.error\\n    androidx.compose.foundation.Canvas(modifier.height(30.dp)) {\\n        val bars').replace('drawLine(MaterialTheme.colorScheme.error,', 'drawLine(waveformColor,')
        text = replace_once(text, marker, helper + marker, "conversation helper insertion")

    text = text.replace(
        "    val haptic = LocalHapticFeedback.current\n    val scope = rememberCoroutineScope()",
        "    val haptic = LocalHapticFeedback.current\n    val keyboard = LocalSoftwareKeyboardController.current\n    val focusManager = LocalFocusManager.current\n    val scope = rememberCoroutineScope()",
        1,
    )
    text = text.replace(
        "    var deleteForEveryone by remember { mutableStateOf<Message?>(null) }\n",
        "    var deleteForEveryone by remember { mutableStateOf<Message?>(null) }\n    var deleteChatConfirm by remember { mutableStateOf(false) }\n",
        1,
    )
    text = text.replace(
        "    var voiceFile by remember { mutableStateOf<File?>(null) }\n",
        "    var voiceFile by remember { mutableStateOf<File?>(null) }\n    var paused by remember { mutableStateOf(false) }\n    var voiceWaveform by remember { mutableStateOf<List<Float>>(emptyList()) }\n    var voiceDraft by remember { mutableStateOf<VoiceDraft?>(null) }\n    var initialUnread by rememberSaveable(conversationId) { mutableIntStateOf(conversation?.unreadCount ?: 0) }\n",
        1,
    )

    old_stop = '''    fun stopRecording(send: Boolean) {\n        val activeRecorder = recorder\n        recorder = null\n        val file = voiceFile\n        voiceFile = null\n        val seconds = elapsed\n\n        val stoppedCleanly = runCatching { activeRecorder?.stop() }.isSuccess\n        runCatching { activeRecorder?.release() }\n        recording = false\n        locked = false\n\n        if (send && stoppedCleanly && file != null && file.length() > 0) {\n            repo.uploadChatMedia(conversationId, Uri.fromFile(file), MessageType.VOICE) { result ->\n                result.onSuccess { url ->\n                    viewModel.sendMessage(\n                        conversationId,\n                        "Voice message",\n                        MessageType.VOICE,\n                        url,\n                        voiceDurationSeconds = seconds.coerceAtLeast(1)\n                    )\n                    file.delete()\n                }\n            }\n        } else {\n            file?.delete()\n        }\n    }'''
    new_stop = '''    fun stopRecording(send: Boolean) {\n        val activeRecorder = recorder\n        recorder = null\n        val file = voiceFile\n        voiceFile = null\n        val seconds = elapsed.coerceAtLeast(1)\n        val captured = voiceWaveform\n\n        val stoppedCleanly = runCatching { activeRecorder?.stop() }.isSuccess\n        runCatching { activeRecorder?.release() }\n        recording = false\n        paused = false\n        locked = false\n        focusManager.clearFocus()\n\n        if (send && stoppedCleanly && file != null && file.length() > 0) {\n            voiceDraft?.file?.delete()\n            voiceDraft = VoiceDraft(file, seconds, captured)\n        } else {\n            file?.delete()\n        }\n    }'''
    if old_stop in text:
        text = replace_once(text, old_stop, new_stop, "voice stop/preview")

    text = text.replace(
        "            recording = true\n            elapsed = 0",
        "            recording = true\n            paused = false\n            voiceWaveform = emptyList()\n            elapsed = 0",
        1,
    )

    text = text.replace(
        "        if (granted) {\n            locked = true\n            startRecording()",
        "        if (granted) {\n            locked = true\n            keyboard?.hide()\n            focusManager.clearFocus()\n            startRecording()",
        1,
    )

    old_timer = '''    LaunchedEffect(recording) {\n        while (recording) {\n            delay(1000)\n            elapsed++\n            if (elapsed >= 600) stopRecording(true)\n        }\n    }'''
    new_timer = '''    LaunchedEffect(recording, paused) {\n        while (recording) {\n            delay(1000)\n            if (!paused) elapsed++\n            if (elapsed >= 600) stopRecording(true)\n        }\n    }\n\n    LaunchedEffect(recording, paused) {\n        while (recording) {\n            if (!paused) {\n                val amp = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)\n                val level = sqrt((amp / 32767f).coerceIn(0f, 1f)).coerceAtLeast(.05f)\n                voiceWaveform = appendWaveform(voiceWaveform, level)\n            }\n            delay(120)\n        }\n    }\n\n    LaunchedEffect(conversation?.unreadCount) {\n        val count = conversation?.unreadCount ?: 0\n        if (initialUnread == 0 && count > 0) initialUnread = count\n    }'''
    if old_timer in text:
        text = replace_once(text, old_timer, new_timer, "voice timer and waveform")

    text = text.replace(
        "            voiceFile?.delete()\n            repo.setTyping(conversationId, false)",
        "            voiceFile?.delete()\n            voiceDraft?.file?.delete()\n            repo.setTyping(conversationId, false)",
        1,
    )

    # Locked recording UI: inject waveform and pause/resume control before cancel.
    old_record_row = '''                                Text(\n                                    "● ${elapsed / 60}:${(elapsed % 60).toString().padStart(2, '0')} ${if (locked) "Locked" else "↑ lock  ← cancel"}",\n                                    Modifier.weight(1f),\n                                    color = MaterialTheme.colorScheme.error\n                                )\n                                IconButton(onClick = { stopRecording(false) }) {'''
    new_record_row = '''                                Column(Modifier.weight(1f)) {\n                                    Text(\n                                        "● ${elapsed / 60}:${(elapsed % 60).toString().padStart(2, '0')} ${if (paused) "Paused" else if (locked) "Locked" else "↑ lock  ← cancel"}",\n                                        color = MaterialTheme.colorScheme.error\n                                    )\n                                    LiveRecordingWaveform(voiceWaveform, Modifier.fillMaxWidth())\n                                }\n                                if (locked) {\n                                    IconButton(onClick = {\n                                        val active = recorder\n                                        runCatching { if (paused) active?.resume() else active?.pause() }\n                                            .onSuccess { paused = !paused }\n                                    }) {\n                                        Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, if (paused) "Resume" else "Pause")\n                                    }\n                                }\n                                IconButton(onClick = { stopRecording(false) }) {'''
    if old_record_row in text:
        text = replace_once(text, old_record_row, new_record_row, "voice recording bar")

    # Every transition to locked state hides keyboard. Restrict to known indentation patterns.
    text = text.replace(
        "                                                        locked = true\n",
        "                                                        locked = true\n                                                        keyboard?.hide()\n                                                        focusManager.clearFocus()\n",
    )
    text = text.replace(
        "                                                locked = true\n                                                requestRecording()",
        "                                                locked = true\n                                                keyboard?.hide()\n                                                focusManager.clearFocus()\n                                                requestRecording()",
        1,
    )

    # Video thumbnail + recorded waveform playback.
    text = text.replace(
        '                            MessageType.VIDEO -> Text("▶ Video • Tap to play", Modifier.padding(20.dp))',
        '                            MessageType.VIDEO -> PrivateVideoThumbnail(message.mediaUrl, Modifier.fillMaxWidth().height(165.dp))',
        1,
    )
    text = text.replace(
        '''                            MessageType.VOICE -> VoiceWaveformPlayer(\n                                message.voiceDurationSeconds,\n                                message.mediaUrl,\n                                isOutgoing = isMe\n                            )''',
        '''                            MessageType.VOICE -> VoiceWaveformPlayer(\n                                message.voiceDurationSeconds,\n                                message.mediaUrl,\n                                waveform = message.waveform,\n                                isOutgoing = isMe\n                            )''',
        1,
    )

    text = text.replace(
        "                    repo.hideMessage(conversationId, message.id)\n",
        "                    viewModel.deleteMessageForMe(conversationId, message.id)\n",
        1,
    )
    text = text.replace(
        "                    viewModel.deleteMessage(conversationId, message.id)\n",
        "                    viewModel.deleteMessageForEveryone(conversationId, message.id)\n",
        1,
    )

    # Add permanent chat delete to conversation menu.
    menu_marker = '''                TextButton(onClick = {\n                    viewModel.setConversationWallpaper(\n                        conversationId,\n                        if (conversation?.wallpaperIndex == 1) 0 else 1\n                    )\n                    conversationSettings = false\n                }) { Text("Toggle crystal / plain wallpaper") }'''
    menu_new = menu_marker + '''\n                TextButton(onClick = {\n                    conversationSettings = false\n                    deleteChatConfirm = true\n                }) { Text("Delete chat", color = MaterialTheme.colorScheme.error) }'''
    if menu_marker in text and "deleteChatConfirm = true" not in text:
        text = replace_once(text, menu_marker, menu_new, "conversation delete menu")

    # Date separators + unread divider.
    old_items = '''                    items(\n                        visibleMessages.filterNot { it.id == morphMessage?.id },\n                        key = { it.id }\n                    ) { message ->\n                        MessageBubble(\n                            message = message,\n                            isMe = message.senderId == me.uid,\n                            onLongClick = { actions = message },\n                            onReply = {\n                                reply = message\n                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)\n                            },\n                            onMedia = { viewer = message },\n                            onReactionClick = { emoji ->\n                                viewModel.addReaction(conversationId, message.id, emoji)\n                            },\n                            onRetry = { repo.retryMessage(conversationId, message.id) }\n                        )\n                    }'''
    new_items = '''                    val rows = visibleMessages.filterNot { it.id == morphMessage?.id }\n                    itemsIndexed(rows, key = { _, item -> item.id }) { index, message ->\n                        if (index == 0 || !sameDay(rows[index - 1].createdAt, message.createdAt)) {\n                            DateSeparator(message.createdAt)\n                        }\n                        val unreadStart = (rows.size - initialUnread).coerceAtLeast(0)\n                        if (initialUnread > 0 && index == unreadStart) {\n                            DateSeparator(message.createdAt, unread = true)\n                        }\n                        MessageBubble(\n                            message = message,\n                            isMe = message.senderId == me.uid,\n                            onLongClick = {\n                                actions = message\n                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)\n                            },\n                            onReply = {\n                                reply = message\n                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)\n                            },\n                            onReplyPreviewClick = { replyId ->\n                                val target = rows.indexOfFirst { it.id == replyId }\n                                if (target >= 0) scope.launch { listState.animateScrollToItem(target + 1) }\n                            },\n                            onMedia = { viewer = message },\n                            onReactionClick = { emoji ->\n                                viewModel.addReaction(conversationId, message.id, emoji)\n                            },\n                            onRetry = { repo.retryMessage(conversationId, message.id) }\n                        )\n                    }'''
    if old_items in text:
        text = replace_once(text, old_items, new_items, "date/unread/reply list")

    # MessageBubble callback and clickable reply preview.
    text = text.replace(
        "    onReply: () -> Unit,\n    onMedia: () -> Unit,",
        "    onReply: () -> Unit,\n    onReplyPreviewClick: (String) -> Unit,\n    onMedia: () -> Unit,",
        1,
    )
    old_reply = '''                    if (message.replyToText != null) {\n                        Text(\n                            "${message.replyToSender}: ${message.replyToText}",\n                            fontSize = 11.sp,\n                            maxLines = 2,\n                            color = MaterialTheme.colorScheme.primary\n                        )\n                    }'''
    new_reply = '''                    if (message.replyToText != null) {\n                        Text(\n                            "${message.replyToSender}: ${message.replyToText}",\n                            fontSize = 11.sp,\n                            maxLines = 2,\n                            color = MaterialTheme.colorScheme.primary,\n                            modifier = Modifier.clickable { message.replyToId?.let(onReplyPreviewClick) }\n                        )\n                    }'''
    if old_reply in text:
        text = replace_once(text, old_reply, new_reply, "reply jump")

    # Voice preview and permanent chat delete dialogs before media viewer.
    dialog_marker = '''        viewer?.let { message ->\n            MediaViewer(message) { viewer = null }\n        }'''
    extra_dialogs = '''        voiceDraft?.let { draft ->\n            GlassDialog("Voice preview", {\n                draft.file.delete()\n                voiceDraft = null\n            }) {\n                VoiceWaveformPlayer(\n                    draft.seconds,\n                    Uri.fromFile(draft.file).toString(),\n                    waveform = draft.waveform,\n                    isOutgoing = true\n                )\n                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                    GlassButton("Discard", {\n                        draft.file.delete()\n                        voiceDraft = null\n                    }, Modifier.weight(1f), isPrimary = false)\n                    GlassButton("Send", {\n                        val localDraft = draft\n                        voiceDraft = null\n                        repo.uploadChatMedia(conversationId, Uri.fromFile(localDraft.file), MessageType.VOICE) { result ->\n                            result.onSuccess { url ->\n                                viewModel.sendMessage(\n                                    conversationId,\n                                    "Voice message",\n                                    MessageType.VOICE,\n                                    url,\n                                    voiceDurationSeconds = localDraft.seconds,\n                                    waveform = localDraft.waveform\n                                )\n                                localDraft.file.delete()\n                            }.onFailure { localDraft.file.delete() }\n                        }\n                    }, Modifier.weight(1f))\n                }\n            }\n        }\n\n        if (deleteChatConfirm) {\n            GlassDialog("Delete chat?", { deleteChatConfirm = false }) {\n                Text("This permanently removes the existing conversation from your account. It will not return after restart, sign-in, reinstall or sync. A future new message can create a fresh chat without restoring the deleted history.")\n                GlassButton("Delete chat", {\n                    viewModel.deleteChatForMe(conversationId)\n                    deleteChatConfirm = false\n                    onBackClick()\n                }, Modifier.fillMaxWidth())\n            }\n        }\n\n''' + dialog_marker
    if dialog_marker in text and "Voice preview" not in text:
        text = replace_once(text, dialog_marker, extra_dialogs, "voice/chat delete dialogs")

    path.write_text(text)


def patch_media_viewer():
    path = ROOT / "app/src/main/java/com/example/ui/screens/MediaViewer.kt"
    text = path.read_text()
    text = text.replace(
        'MessageType.VOICE->com.example.ui.components.VoiceWaveformPlayer(message.voiceDurationSeconds,message.mediaUrl)',
        'MessageType.VOICE->com.example.ui.components.VoiceWaveformPlayer(message.voiceDurationSeconds,message.mediaUrl,waveform=message.waveform)',
        1,
    )
    path.write_text(text)


patch_backend()
patch_conversation()
patch_media_viewer()
print("remaining implementation patch applied")
