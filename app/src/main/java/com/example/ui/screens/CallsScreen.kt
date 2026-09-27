package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CallRecord
import com.example.data.model.CallStatus
import com.example.data.model.CallType
import com.example.ui.components.GlassAvatar
import com.example.ui.components.GlassCard
import com.example.ui.components.LiquidBackground
import com.example.ui.theme.CoralEndCall
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.LiquidChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CallsScreen(
  viewModel: LiquidChatViewModel,
  onNavigateToActiveCall: () -> Unit,
  onNavigateToChats: () -> Unit,
  onNavigateToGroups: () -> Unit,
  onNavigateToUpdates: () -> Unit,
  onNavigateToCard: () -> Unit,
  modifier: Modifier = Modifier
) {
  val callRecords by viewModel.callRecords.collectAsState()
  val users by viewModel.users.collectAsState()

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
              .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Calls & WebRTC",
              style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                fontSize = 20.sp
              ),
              modifier = Modifier.weight(1f)
            )
          }
        }
      },
      bottomBar = {
        GlassBottomBar(
          selectedRoute = "calls",
          onNavigateToChats = onNavigateToChats,
          onNavigateToGroups = onNavigateToGroups,
          onNavigateToUpdates = onNavigateToUpdates,
          onNavigateToCalls = {},
          onNavigateToCard = onNavigateToCard
        )
      },
      floatingActionButton = {
        FloatingActionButton(
          onClick = {
            if (users.isNotEmpty()) {
              viewModel.startCall(users.first(), CallType.AUDIO)
              onNavigateToActiveCall()
            }
          },
          containerColor = CyanAccent,
          contentColor = Color.Black,
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier
            .padding(bottom = 72.dp)
            .testTag("start_call_fab")
        ) {
          Icon(Icons.Default.Call, contentDescription = "Start Call")
        }
      }
    ) { innerPadding ->
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        item {
          Text(
            text = "RECENT CALLS",
            style = MaterialTheme.typography.labelSmall.copy(
              color = TextMuted,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp,
              letterSpacing = 1.sp
            ),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
          )
        }

        items(callRecords, key = { it.id }) { record ->
          CallRowItem(
            record = record,
            onCallClick = { type ->
              viewModel.startCall(record.otherUser, type)
              onNavigateToActiveCall()
            }
          )
        }
      }
    }
  }
}

@Composable
fun CallRowItem(
  record: CallRecord,
  onCallClick: (CallType) -> Unit
) {
  GlassCard(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("call_item_${record.id}"),
    shape = RoundedCornerShape(20.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      GlassAvatar(
        photoUrl = record.otherUser.photoUrl,
        name = record.otherUser.displayName,
        size = 50.dp
      )

      Spacer(modifier = Modifier.width(14.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = record.otherUser.displayName,
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            color = if (record.status == CallStatus.MISSED) CoralEndCall else TextPrimary,
            fontSize = 15.sp
          )
        )

        Spacer(modifier = Modifier.height(3.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
          val (icon, tint) = when (record.status) {
            CallStatus.MISSED -> Icons.AutoMirrored.Filled.CallMissed to CoralEndCall
            CallStatus.INCOMING -> Icons.AutoMirrored.Filled.CallReceived to EmeraldOnline
            else -> Icons.AutoMirrored.Filled.CallMade to CyanAccent
          }

          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(15.dp)
          )

          Spacer(modifier = Modifier.width(4.dp))

          val timeFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
          val statusText = if (record.durationSeconds > 0) {
            "${timeFormat.format(Date(record.timestamp))} (${record.durationSeconds / 60}m ${record.durationSeconds % 60}s)"
          } else {
            timeFormat.format(Date(record.timestamp))
          }

          Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 12.sp)
          )
        }
      }

      IconButton(
        onClick = { onCallClick(record.type) },
        modifier = Modifier.testTag("call_action_${record.id}")
      ) {
        Icon(
          imageVector = if (record.type == CallType.VIDEO) Icons.Default.Videocam else Icons.Default.Call,
          contentDescription = "Call",
          tint = CyanAccent,
          modifier = Modifier.size(24.dp)
        )
      }
    }
  }
}
