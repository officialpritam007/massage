package com.example.ui.screens

import android.net.Uri

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.CallType
import com.example.data.model.Message
import com.example.data.model.MessageDeliveryStatus
import com.example.data.model.MessageType
import com.example.ui.components.GlassAvatar
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassTextField
import com.example.ui.components.LiquidBackground
import com.example.ui.components.VoiceWaveformPlayer
import com.example.ui.theme.BubbleIncoming
import com.example.ui.theme.BubbleIncomingBorder
import com.example.ui.theme.BubbleOutgoingGradientEnd
import com.example.ui.theme.BubbleOutgoingGradientStart
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.GlassBorderStroke
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.LiquidChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
  conversationId: String,
  viewModel: LiquidChatViewModel,
  onBackClick: () -> Unit,
  onNavigateToProfile: (String) -> Unit,
  onNavigateToActiveCall: () -> Unit,
  onNavigateToCamera: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val conversations by viewModel.conversations.collectAsState()
  val allMessages by viewModel.messages.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()
  val allUsers by viewModel.users.collectAsState()

  val conversation = conversations.find { it.id == conversationId }
  val messages = allMessages[conversationId].orEmpty()

  val otherUser = conversation?.otherUser ?: run {
    val parts = conversationId.split("_")
    val otherUid = parts.firstOrNull { it != currentUser.uid } ?: ""
    allUsers.find { it.uid == otherUid }
      ?: com.example.data.model.User(
        uid = otherUid,
        displayName = "Contact",
        username = otherUid.take(8),
        photoUrl = "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=400&q=80"
      )
  }

  var inputText by remember { mutableStateOf("") }
  var replyingTo by remember { mutableStateOf<Message?>(null) }
  var selectedMessageForActions by remember { mutableStateOf<Message?>(null) }
  var showAttachmentSheet by remember { mutableStateOf(false) }
  val typingScope = rememberCoroutineScope()
  var typingJob by remember { mutableStateOf<Job?>(null) }
  var showChatSettings by remember { mutableStateOf(false) }

  val imagePicker = rememberLauncherForActivityResult(
    ActivityResultContracts.GetContent()
  ) { uri ->
    uri ?: return@rememberLauncherForActivityResult
    viewModel.uploadChatMedia(conversationId, uri, MessageType.IMAGE) { result ->
      result.onSuccess { url ->
        viewModel.sendMessage(
          conversationId = conversationId,
          text = "",
          type = MessageType.IMAGE,
          mediaUrl = url,
          replyToId = replyingTo?.id,
          replyToText = replyingTo?.text,
          replyToSender = replyingTo?.senderName
        )
      }.onFailure {
        // Upload error is surfaced by the failed callback; no optimistic message is created.
      }
    }
  }

  val videoPicker = rememberLauncherForActivityResult(
    ActivityResultContracts.GetContent()
  ) { uri ->
    uri ?: return@rememberLauncherForActivityResult
    viewModel.uploadChatMedia(conversationId, uri, MessageType.VIDEO) { result ->
      result.onSuccess { url ->
        viewModel.sendMessage(
          conversationId = conversationId,
          text = "",
          type = MessageType.VIDEO,
          mediaUrl = url
        )
      }.onFailure {
        // Upload error is surfaced by the failed callback; no optimistic message is created.
      }
    }
  }

  // Debounced realtime typing indicator. It stops automatically after a short
  // idle period and is cleared whenever the conversation screen is left.
  fun updateTypingState(text: String) {
    typingJob?.cancel()
    if (text.isBlank()) {
      viewModel.setTyping(conversationId, false)
      return
    }
    viewModel.setTyping(conversationId, true)
    typingJob = typingScope.launch {
      delay(1200)
      viewModel.setTyping(conversationId, false)
    }
  }

  DisposableEffect(conversationId) {
    onDispose {
      typingJob?.cancel()
      viewModel.setTyping(conversationId, false)
    }
  }

  val listState = rememberLazyListState()

  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.size - 1)
    }
  }

  // Real-time Firestore sync & Clear unread badge upon viewing
  LaunchedEffect(conversationId) {
    viewModel.observeConversation(conversationId)
    viewModel.clearUnread(conversationId)
  }

  if (showChatSettings) {
    AlertDialog(
      onDismissRequest = { showChatSettings = false },
      title = { Text("Chat settings") },
      text = {
        Column {
          TextButton(onClick = {
            viewModel.setConversationMuted(conversationId, !(conversation?.isMuted ?: false))
            showChatSettings = false
          }) { Text(if (conversation?.isMuted == true) "Unmute notifications" else "Mute notifications") }

          Text("Disappearing messages", style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
          listOf(0L to "Off", 86400L to "24 hours", 604800L to "7 days", 2592000L to "30 days").forEach { (seconds, label) ->
            TextButton(onClick = {
              viewModel.setDisappearingMessages(conversationId, seconds)
              showChatSettings = false
            }) {
              Text(if ((conversation?.disappearingSeconds ?: 0L) == seconds) "✓ $label" else label)
            }
          }

          Text("Wallpaper", style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 1, 2).forEach { index ->
              TextButton(onClick = {
                viewModel.setConversationWallpaper(conversationId, index)
                showChatSettings = false
              }) { Text(if ((conversation?.wallpaperIndex ?: 0) == index) "✓ ${index + 1}" else "${index + 1}") }
            }
          }
        }
      },
      confirmButton = { TextButton(onClick = { showChatSettings = false }) { Text("Done") } }
    )
  }

  LiquidBackground(modifier = modifier) {
    Scaffold(
      containerColor = Color.Transparent,
      topBar = {
        // Conversation Top Bar
        GlassCard(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
          elevation = 8.dp
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(
              onClick = onBackClick,
              modifier = Modifier.testTag("conversation_back_button")
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TextPrimary
              )
            }

            // User Avatar & Name
            Row(
              modifier = Modifier
                .weight(1f)
                .clickable { onNavigateToProfile(otherUser.uid) },
              verticalAlignment = Alignment.CenterVertically
            ) {
              GlassAvatar(
                photoUrl = otherUser.photoUrl,
                name = otherUser.displayName,
                size = 42.dp,
                isOnline = conversation?.isOnline ?: false
              )

              Spacer(modifier = Modifier.width(10.dp))

              Column {
                Text(
                  text = otherUser.displayName,
                  style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 15.sp
                  ),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = if (conversation?.isTyping == true) "Typing..." else if (conversation?.isOnline == true) "Active now" else "Offline",
                  style = MaterialTheme.typography.bodySmall.copy(
                    color = if (conversation?.isOnline == true) EmeraldOnline else TextMuted,
                    fontSize = 11.sp
                  )
                )
              }
            }

            // Call & Info Actions
            IconButton(
              onClick = {
                viewModel.startCall(otherUser, CallType.AUDIO)
                onNavigateToActiveCall()
              },
              modifier = Modifier.testTag("audio_call_button")
            ) {
              Icon(Icons.Default.Call, contentDescription = "Audio Call", tint = CyanAccent)
            }

            IconButton(
              onClick = {
                viewModel.startCall(otherUser, CallType.VIDEO)
                onNavigateToActiveCall()
              },
              modifier = Modifier.testTag("video_call_button")
            ) {
              Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = CyanAccent)
            }

            IconButton(
              onClick = { showChatSettings = true },
              modifier = Modifier.testTag("conversation_settings_button")
            ) {
              Icon(Icons.Default.MoreVert, contentDescription = "Chat settings", tint = TextSecondary)
            }

            IconButton(
              onClick = { onNavigateToProfile(otherUser.uid) },
              modifier = Modifier.testTag("conversation_info_button")
            ) {
              Icon(Icons.Default.Info, contentDescription = "Contact Info", tint = TextSecondary)
            }
          }
        }
      },
      bottomBar = {
        // Bottom Input Area
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
          // Reply Banner
          AnimatedVisibility(visible = replyingTo != null) {
            replyingTo?.let { replyMsg ->
              GlassCard(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(bottom = 6.dp),
                shape = RoundedCornerShape(16.dp)
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Box(
                    modifier = Modifier
                      .width(3.dp)
                      .height(30.dp)
                      .background(CyanAccent, RoundedCornerShape(2.dp))
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "Replying to ${replyMsg.senderName}",
                      style = MaterialTheme.typography.labelSmall.copy(
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold
                      )
                    )
                    Text(
                      text = replyMsg.text,
                      style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary
                      ),
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                  IconButton(
                    onClick = { replyingTo = null },
                    modifier = Modifier.size(24.dp)
                  ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel reply", tint = TextMuted)
                  }
                }
              }
            }
          }

          // Input Bar Row
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Attachment Button
            IconButton(
              onClick = { showAttachmentSheet = true },
              modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFF131D35))
                .border(1.dp, GlassBorderStroke, CircleShape)
                .testTag("attachment_button")
            ) {
              Icon(Icons.Default.Add, contentDescription = "Attach", tint = CyanAccent)
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Direct Camera Button
            IconButton(
              onClick = onNavigateToCamera,
              modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFF131D35))
                .border(1.dp, GlassBorderStroke, CircleShape)
                .testTag("camera_button")
            ) {
              Icon(Icons.Default.PhotoCamera, contentDescription = "Camera", tint = CyanAccent)
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Text Input Box
            GlassTextField(
              value = inputText,
              onValueChange = {
                inputText = it
                updateTypingState(it)
              },
              placeholder = "Liquid message...",
              singleLine = false,
              maxLines = 4,
              modifier = Modifier.weight(1f),
              leadingIcon = {
                Icon(
                  imageVector = Icons.Default.SentimentSatisfiedAlt,
                  contentDescription = "Emoji",
                  tint = TextMuted,
                  modifier = Modifier
                    .size(20.dp)
                    .clickable {
                      inputText += "✨"
                    }
                )
              },
              testTag = "message_input_field"
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Send / Voice Button
            val isSend = inputText.isNotBlank()
            Box(
              modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(
                  Brush.linearGradient(
                    if (isSend) listOf(CyanAccent, ElectricBlue) else listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                  )
                )
                .border(1.dp, if (isSend) Color.White.copy(alpha = 0.4f) else GlassBorderStroke, CircleShape)
                .clickable {
                  if (isSend) {
                    viewModel.sendMessage(
                      conversationId = conversationId,
                      text = inputText.trim(),
                      replyToId = replyingTo?.id,
                      replyToText = replyingTo?.text,
                      replyToSender = replyingTo?.senderName
                    )
                    inputText = ""
                    typingJob?.cancel()
                    viewModel.setTyping(conversationId, false)
                    replyingTo = null
                  } else {
                    // Send a sample voice note (0:18)
                    viewModel.sendMessage(
                      conversationId = conversationId,
                      text = "Voice message",
                      type = MessageType.VOICE,
                      voiceDurationSeconds = 18
                    )
                  }
                }
                .testTag("send_or_mic_button"),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = if (isSend) Icons.AutoMirrored.Filled.Send else Icons.Default.Mic,
                contentDescription = if (isSend) "Send" else "Voice",
                tint = if (isSend) Color.Black else CyanAccent,
                modifier = Modifier.size(20.dp)
              )
            }
          }
        }
      }
    ) { innerPadding ->
      LazyColumn(
        state = listState,
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(messages, key = { it.id }) { msg ->
          val isMe = msg.senderId == currentUser.uid

          MessageBubble(
            message = msg,
            isMe = isMe,
            onLongClick = { selectedMessageForActions = msg },
            onReactionClick = { emoji ->
              viewModel.addReaction(conversationId, msg.id, emoji)
            }
          )
        }
      }
    }
  }

  // Long-press Actions Modal (Reactions, Reply, Pin, Delete)
  selectedMessageForActions?.let { msg ->
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
      onDismissRequest = { selectedMessageForActions = null },
      sheetState = sheetState,
      containerColor = Color(0xFF0F172A),
      scrimColor = Color.Black.copy(alpha = 0.6f)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 16.dp)
      ) {
        // Quick Emoji Reaction Bar
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1E293B))
            .padding(horizontal = 14.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceAround
        ) {
          listOf("❤️", "👍", "🔥", "😂", "😮", "🙏").forEach { emoji ->
            Text(
              text = emoji,
              fontSize = 24.sp,
              modifier = Modifier
                .clickable {
                  viewModel.addReaction(conversationId, msg.id, emoji)
                  selectedMessageForActions = null
                }
                .padding(4.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Rows
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
              replyingTo = msg
              selectedMessageForActions = null
            }
            .padding(vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.Reply, contentDescription = null, tint = CyanAccent)
          Spacer(modifier = Modifier.width(16.dp))
          Text("Reply to message", color = TextPrimary, fontSize = 15.sp)
        }

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
              viewModel.pinMessage(conversationId, msg.id)
              selectedMessageForActions = null
            }
            .padding(vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.PushPin, contentDescription = null, tint = CyanAccent)
          Spacer(modifier = Modifier.width(16.dp))
          Text(if (msg.isPinned) "Unpin message" else "Pin message", color = TextPrimary, fontSize = 15.sp)
        }

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
              viewModel.deleteMessage(conversationId, msg.id)
              selectedMessageForActions = null
            }
            .padding(vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF4B72))
          Spacer(modifier = Modifier.width(16.dp))
          Text("Delete message", color = Color(0xFFFF4B72), fontSize = 15.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }

  // Attachment Selector BottomSheet
  if (showAttachmentSheet) {
    ModalBottomSheet(
      onDismissRequest = { showAttachmentSheet = false },
      containerColor = Color(0xFF0F172A)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 16.dp)
      ) {
        Text(
          text = "Share Content",
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            color = TextPrimary
          ),
          modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceAround
        ) {
          AttachmentOption(
            title = "Camera",
            icon = Icons.Default.PhotoCamera,
            color = CyanAccent,
            onClick = {
              showAttachmentSheet = false
              onNavigateToCamera()
            }
          )

          AttachmentOption(
            title = "Photo Gallery",
            icon = Icons.Default.AttachFile,
            color = ElectricBlue,
            onClick = {
              showAttachmentSheet = false
              imagePicker.launch("image/*")
            }
          )

          AttachmentOption(
            title = "Video Gallery",
            icon = Icons.Default.Videocam,
            color = EmeraldOnline,
            onClick = {
              showAttachmentSheet = false
              videoPicker.launch("video/*")
            }
          )

          AttachmentOption(
            title = "Voice Note",
            icon = Icons.Default.Mic,
            color = EmeraldOnline,
            onClick = {
              viewModel.sendMessage(
                conversationId = conversationId,
                text = "Voice message",
                type = MessageType.VOICE,
                voiceDurationSeconds = 28
              )
              showAttachmentSheet = false
            }
          )
        }

        Spacer(modifier = Modifier.height(20.dp))
      }
    }
  }
}

@Composable
fun AttachmentOption(
  title: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  color: Color,
  onClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClick = onClick)
      .padding(12.dp)
  ) {
    Box(
      modifier = Modifier
        .size(54.dp)
        .clip(CircleShape)
        .background(color.copy(alpha = 0.2f))
        .border(1.dp, color.copy(alpha = 0.5f), CircleShape),
      contentAlignment = Alignment.Center
    ) {
      Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(26.dp))
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(title, style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
  }
}

@Composable
fun MessageBubble(
  message: Message,
  isMe: Boolean,
  onLongClick: () -> Unit,
  onReactionClick: (String) -> Unit
) {
  val align = if (isMe) Alignment.End else Alignment.Start
  val bubbleShape = if (isMe) {
    RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
  } else {
    RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
  }

  val backgroundBrush = if (isMe) {
    Brush.linearGradient(listOf(BubbleOutgoingGradientStart, BubbleOutgoingGradientEnd))
  } else {
    Brush.linearGradient(listOf(BubbleIncoming, Color(0xFF1A2640)))
  }

  val borderStroke = if (isMe) {
    BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
  } else {
    BorderStroke(1.dp, BubbleIncomingBorder)
  }

  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = align
  ) {
    Box(
      modifier = Modifier
        .clip(bubbleShape)
        .background(backgroundBrush)
        .border(borderStroke, bubbleShape)
        .clickable(onClick = onLongClick)
        .padding(horizontal = 14.dp, vertical = 10.dp)
        .testTag("message_bubble_${message.id}")
    ) {
      Column(modifier = Modifier.width(androidx.compose.ui.unit.Dp.Unspecified)) {
        // Reply Preview inside bubble
        if (!message.replyToText.isNullOrBlank()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(Color.Black.copy(alpha = 0.25f))
              .padding(horizontal = 8.dp, vertical = 4.dp)
          ) {
            Column {
              Text(
                text = message.replyToSender ?: "Reply",
                style = TextStyle(color = CyanAccent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
              )
              Text(
                text = message.replyToText,
                style = TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
          Spacer(modifier = Modifier.height(6.dp))
        }

        // Image / Media Message
        if (message.type == MessageType.IMAGE && message.mediaUrl.isNotBlank()) {
          Box(
            modifier = Modifier
              .width(240.dp)
              .height(160.dp)
              .clip(RoundedCornerShape(14.dp))
              .background(Color.Black.copy(alpha = 0.2f))
          ) {
            AsyncImage(
              model = message.mediaUrl,
              contentDescription = "Chat Media",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
          }
          Spacer(modifier = Modifier.height(6.dp))
        }

        // Video message preview
        if (message.type == MessageType.VIDEO && message.mediaUrl.isNotBlank()) {
          androidx.compose.foundation.layout.Box(
            modifier = Modifier
              .width(240.dp)
              .height(180.dp)
              .clip(RoundedCornerShape(14.dp))
              .background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
          ) {
            androidx.compose.ui.viewinterop.AndroidView(
              factory = { context ->
                android.widget.VideoView(context).apply {
                  setVideoURI(Uri.parse(message.mediaUrl))
                  setOnPreparedListener { player ->
                    player.isLooping = true
                    seekTo(1)
                  }
                }
              },
              modifier = Modifier.fillMaxSize()
            )
          }
          Spacer(modifier = Modifier.height(6.dp))
        }

        // Voice Message Waveform
        if (message.type == MessageType.VOICE) {
          VoiceWaveformPlayer(
            durationSeconds = message.voiceDurationSeconds.coerceAtLeast(12),
            isOutgoing = isMe
          )
        } else {
          // Text Content
          Text(
            text = message.text,
            style = MaterialTheme.typography.bodyMedium.copy(
              color = if (isMe) Color.White else TextPrimary,
              fontSize = 14.5.sp,
              lineHeight = 20.sp
            )
          )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Metadata: Time + Ticks
        Row(
          modifier = Modifier.align(Alignment.End),
          verticalAlignment = Alignment.CenterVertically
        ) {
          val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
          Text(
            text = timeFormat.format(Date(message.createdAt)),
            style = TextStyle(
              color = if (isMe) Color.White.copy(alpha = 0.7f) else TextMuted,
              fontSize = 10.sp
            )
          )

          if (isMe) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
              imageVector = if (message.status == MessageDeliveryStatus.READ) Icons.Default.DoneAll else Icons.Default.Done,
              contentDescription = "Status",
              tint = if (message.status == MessageDeliveryStatus.READ) CyanAccent else Color.White.copy(alpha = 0.7f),
              modifier = Modifier.size(13.dp)
            )
          }
        }
      }
    }

    // Floating Emoji Reactions Badge
    if (message.reactions.isNotEmpty()) {
      Row(
        modifier = Modifier
          .padding(top = 2.dp, start = if (!isMe) 6.dp else 0.dp, end = if (isMe) 6.dp else 0.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(Color(0xFF131D35))
          .border(1.dp, GlassBorderStroke, RoundedCornerShape(12.dp))
          .padding(horizontal = 6.dp, vertical = 2.dp)
      ) {
        message.reactions.forEach { reaction ->
          Text(
            text = "${reaction.emoji} ${reaction.userIds.size}",
            fontSize = 11.sp,
            color = TextPrimary
          )
          Spacer(modifier = Modifier.width(4.dp))
        }
      }
    }
  }
}
