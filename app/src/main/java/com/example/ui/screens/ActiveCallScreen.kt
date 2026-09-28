package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CallType
import com.example.ui.components.GlassAvatar
import com.example.ui.components.GlassCard
import com.example.ui.components.LiquidBackground
import com.example.ui.theme.CoralEndCall
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.GlassBorderStroke
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.LiquidChatViewModel

@Composable
fun ActiveCallScreen(
  viewModel: LiquidChatViewModel,
  onCallEnded: () -> Unit,
  modifier: Modifier = Modifier
) {
  val activeCall by viewModel.activeCall.collectAsState()

  if (activeCall == null) {
    onCallEnded()
    return
  }

  val call = activeCall!!
  val isVideo = call.type == CallType.VIDEO

  val infiniteTransition = rememberInfiniteTransition(label = "pulse_avatar")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = 1.08f,
    animationSpec = infiniteRepeatable(
      animation = tween(1000, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "scale"
  )

  LiquidBackground(modifier = modifier) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp, vertical = 48.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      // Top Status Info
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 20.dp)
      ) {
        Text(
          text = if (isVideo) "Liquid Video Call" else "Liquid Audio Call",
          style = MaterialTheme.typography.labelMedium.copy(
            color = CyanAccent,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
          )
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
          text = call.user.displayName,
          style = MaterialTheme.typography.headlineMedium.copy(
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp
          )
        )

        Spacer(modifier = Modifier.height(6.dp))

        val statusText = if (!call.isConnected) {
          if (call.isOutgoing) "Calling…" else "Incoming Call…"
        } else {
          val mins = call.durationSeconds / 60
          val secs = call.durationSeconds % 60
          String.format("%02d:%02d", mins, secs)
        }

        Text(
          text = statusText,
          style = MaterialTheme.typography.bodyMedium.copy(
            color = if (call.isConnected) EmeraldOnline else TextSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
          )
        )
      }

      // Middle: Pulsing Avatar or Video Feed Preview
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(240.dp)
      ) {
        // Outer glow ripple
        Box(
          modifier = Modifier
            .size(220.dp)
            .scale(if (!call.isConnected) pulseScale else 1f)
            .clip(CircleShape)
            .background(
              Brush.radialGradient(
                listOf(CyanAccent.copy(alpha = 0.25f), Color.Transparent)
              )
            )
        )

        Box(
          modifier = Modifier
            .size(160.dp)
            .clip(CircleShape)
            .border(3.dp, Brush.linearGradient(listOf(CyanNeon, ElectricBlue)), CircleShape)
        ) {
          GlassAvatar(
            photoUrl = call.user.photoUrl,
            name = call.user.displayName,
            size = 160.dp
          )
        }
      }

      // Bottom Control Panel
      GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        elevation = 16.dp
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Mute Mic Button
          CallControlButton(
            icon = if (call.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
            isActive = call.isMuted,
            label = if (call.isMuted) "Unmute" else "Mute",
            onClick = { viewModel.toggleMuteCall() },
            testTag = "call_mute_toggle"
          )

          // Camera Video Toggle
          CallControlButton(
            icon = if (call.isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
            isActive = !call.isCameraOn,
            label = if (call.isCameraOn) "Video On" else "Video Off",
            onClick = { viewModel.toggleCameraCall() },
            testTag = "call_camera_toggle"
          )

          // Speaker Toggle
          CallControlButton(
            icon = if (call.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
            isActive = call.isSpeakerOn,
            label = "Speaker",
            onClick = { viewModel.toggleSpeakerCall() },
            testTag = "call_speaker_toggle"
          )

          // End Call Button (Red Coral)
          Box(
            modifier = Modifier
              .size(60.dp)
              .clip(CircleShape)
              .background(CoralEndCall)
              .clickable {
                viewModel.endCall()
                onCallEnded()
              }
              .testTag("end_call_button"),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.CallEnd,
              contentDescription = "End Call",
              tint = Color.White,
              modifier = Modifier.size(28.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
fun CallControlButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  isActive: Boolean,
  label: String,
  onClick: () -> Unit,
  testTag: String
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.clickable(onClick = onClick).testTag(testTag)
  ) {
    Box(
      modifier = Modifier
        .size(52.dp)
        .clip(CircleShape)
        .background(if (isActive) CyanAccent else Color(0x26FFFFFF))
        .border(1.dp, GlassBorderStroke, CircleShape),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (isActive) Color.Black else TextPrimary,
        modifier = Modifier.size(24.dp)
      )
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(
        color = TextSecondary,
        fontSize = 11.sp
      )
    )
  }
}
