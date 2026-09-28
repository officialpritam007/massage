package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Group
import com.example.data.model.Message
import com.example.ui.components.GlassAvatar
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassTextField
import com.example.ui.components.LiquidBackground
import com.example.ui.theme.BubbleIncoming
import com.example.ui.theme.BubbleIncomingBorder
import com.example.ui.theme.BubbleOutgoingGradientEnd
import com.example.ui.theme.BubbleOutgoingGradientStart
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletAccent
import com.example.ui.viewmodel.LiquidChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
  groupId: String,
  viewModel: LiquidChatViewModel,
  onBackClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val groups by viewModel.groups.collectAsState()
  val allMessages by viewModel.messages.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()

  val group = groups.find { it.id == groupId }
  val messages = allMessages[groupId].orEmpty()

  var inputText by remember { mutableStateOf("") }
  var showGroupInfoSheet by remember { mutableStateOf(false) }

  val listState = rememberLazyListState()

  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.size - 1)
    }
  }

  LiquidBackground(modifier = modifier) {
    Scaffold(
      containerColor = Color.Transparent,
      topBar = {
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
              modifier = Modifier.testTag("group_back_button")
            ) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }

            GlassAvatar(
              photoUrl = group?.photoUrl,
              name = group?.name ?: "Group",
              size = 42.dp
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(
              modifier = Modifier
                .weight(1f)
                .clickable { showGroupInfoSheet = true }
            ) {
              Text(
                text = group?.name ?: "Group Chat",
                style = MaterialTheme.typography.titleMedium.copy(
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onBackground
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = "${group?.members?.size ?: 0} members • Tap for info",
                style = MaterialTheme.typography.bodySmall.copy(
                  color = CyanAccent,
                  fontSize = 11.sp
                )
              )
            }

            IconButton(onClick = { showGroupInfoSheet = true }) {
              Icon(Icons.Default.Info, contentDescription = "Group Info", tint = TextSecondary)
            }
          }
        }
      },
      bottomBar = {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          GlassTextField(
            value = inputText,
            onValueChange = { inputText = it },
            placeholder = "Message ${group?.name ?: "group"}...",
            modifier = Modifier.weight(1f),
            testTag = "group_message_input"
          )

          Spacer(modifier = Modifier.width(8.dp))

          Box(
            modifier = Modifier
              .size(48.dp)
              .clip(CircleShape)
              .background(Brush.linearGradient(listOf(CyanAccent, ElectricBlue)))
              .clickable {
                if (inputText.isNotBlank()) {
                  viewModel.sendGroupMessage(groupId, inputText.trim())
                  inputText = ""
                }
              }
              .testTag("group_send_button"),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.Black, modifier = Modifier.size(20.dp))
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
          GroupMessageBubble(message = msg, isMe = isMe)
        }
      }
    }
  }

  // Group Info Sheet
  if (showGroupInfoSheet && group != null) {
    ModalBottomSheet(
      onDismissRequest = { showGroupInfoSheet = false },
      containerColor = MaterialTheme.colorScheme.surface
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        GlassAvatar(photoUrl = group.photoUrl, name = group.name, size = 72.dp)
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = group.name, style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold))
        Text(text = group.description, style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary), modifier = Modifier.padding(top = 4.dp))

        Spacer(modifier = Modifier.height(20.dp))

        Text(
          text = "MEMBERS (${group.members.size})",
          style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontWeight = FontWeight.Bold),
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        group.members.forEach { member ->
          val isAdmin = group.adminIds.contains(member.uid)
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            GlassAvatar(photoUrl = member.photoUrl, name = member.displayName, size = 38.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(text = member.displayName, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
              Text(text = "@${member.username}", color = TextSecondary, fontSize = 12.sp)
            }
            if (isAdmin) {
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(8.dp))
                  .background(CyanAccent.copy(alpha = 0.2f))
                  .padding(horizontal = 8.dp, vertical = 3.dp)
              ) {
                Text(text = "Admin", color = CyanAccent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }
}

@Composable
fun GroupMessageBubble(
  message: Message,
  isMe: Boolean
) {
  val align = if (isMe) Alignment.End else Alignment.Start
  val bubbleShape = if (isMe) {
    RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
  } else {
    RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
  }

  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = align
  ) {
    if (!isMe) {
      Text(
        text = message.senderName,
        style = MaterialTheme.typography.labelSmall.copy(
          color = VioletAccent,
          fontWeight = FontWeight.Bold,
          fontSize = 11.sp
        ),
        modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
      )
    }

    Box(
      modifier = Modifier
        .clip(bubbleShape)
        .background(
          if (isMe) Brush.linearGradient(listOf(BubbleOutgoingGradientStart, BubbleOutgoingGradientEnd))
          else Brush.linearGradient(listOf(BubbleIncoming, Color(0xFF19253F)))
        )
        .border(
          width = 1.dp,
          color = if (isMe) Color.White.copy(alpha = 0.3f) else BubbleIncomingBorder,
          shape = bubbleShape
        )
        .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
      Column {
        Text(
          text = message.text,
          style = MaterialTheme.typography.bodyMedium.copy(
            color = if (isMe) Color.White else MaterialTheme.colorScheme.onBackground,
            fontSize = 14.5.sp
          )
        )
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        Text(
          text = timeFormat.format(Date(message.createdAt)),
          style = MaterialTheme.typography.bodySmall.copy(
            color = if (isMe) Color.White.copy(alpha = 0.7f) else TextMuted,
            fontSize = 10.sp
          ),
          modifier = Modifier.align(Alignment.End)
        )
      }
    }
  }
}
