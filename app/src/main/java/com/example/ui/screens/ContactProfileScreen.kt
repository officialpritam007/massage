package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.CallType
import com.example.ui.components.GlassAvatar
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassIconButton
import com.example.ui.components.LiquidBackground
import com.example.ui.theme.CoralEndCall
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.viewmodel.LiquidChatViewModel

@Composable
fun ContactProfileScreen(
  userId: String,
  viewModel: LiquidChatViewModel,
  onBackClick: () -> Unit,
  onNavigateToConversation: (String) -> Unit,
  onNavigateToActiveCall: () -> Unit,
  modifier: Modifier = Modifier
) {
  val users by viewModel.users.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()
  val blockedUserIds by viewModel.blockedUserIds.collectAsState()
  val glassConfig = LocalLiquidGlass.current

  val user = if (userId == currentUser.uid) currentUser else users.find { it.uid == userId } ?: currentUser
  val isMe = user.uid == currentUser.uid
  val isBlocked = blockedUserIds.contains(user.uid)

  var isMuted by remember { mutableStateOf(false) }

  val sampleSharedMedia = remember {
    listOf(
      "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=400&q=80",
      "https://images.unsplash.com/photo-1550745165-9bc0b252726f?auto=format&fit=crop&w=400&q=80",
      "https://images.unsplash.com/photo-1522071820081-009f0129c71c?auto=format&fit=crop&w=400&q=80",
      "https://images.unsplash.com/photo-1503899036084-c55cdd92da26?auto=format&fit=crop&w=400&q=80"
    )
  }

  LiquidBackground(modifier = modifier) {
    Scaffold(
      containerColor = Color.Transparent,
      topBar = {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          GlassIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            onClick = onBackClick,
            testTag = "profile_back_button"
          )
        }
      }
    ) { innerPadding ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Profile Header Card
        GlassCard(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(32.dp),
          elevation = 8.dp
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            GlassAvatar(
              photoUrl = user.photoUrl,
              name = user.displayName,
              size = 96.dp,
              isOnline = user.isOnline
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = user.displayName,
                style = MaterialTheme.typography.headlineSmall.copy(
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary
                )
              )
              if (user.isVerified) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                  imageVector = Icons.Default.Verified,
                  contentDescription = "Verified",
                  tint = CyanAccent,
                  modifier = Modifier.size(20.dp)
                )
              }
            }

            Text(
              text = "@${user.username}",
              style = MaterialTheme.typography.bodyMedium.copy(color = CyanAccent)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
              text = user.bio,
              style = MaterialTheme.typography.bodyMedium.copy(
                color = TextSecondary,
                lineHeight = 20.sp
              ),
              textAlign = TextAlign.Center
            )

            if (!isMe) {
              Spacer(modifier = Modifier.height(20.dp))

              // Action Buttons Row
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
              ) {
                ProfileActionButton(
                  icon = Icons.Default.Chat,
                  label = "Chat",
                  onClick = {
                    val convId = viewModel.getOrCreateConversationId(user.uid)
                    onNavigateToConversation(convId)
                  }
                )

                ProfileActionButton(
                  icon = Icons.Default.Call,
                  label = "Audio",
                  onClick = {
                    viewModel.startCall(user, CallType.AUDIO)
                    onNavigateToActiveCall()
                  }
                )

                ProfileActionButton(
                  icon = Icons.Default.Videocam,
                  label = "Video",
                  onClick = {
                    viewModel.startCall(user, CallType.VIDEO)
                    onNavigateToActiveCall()
                  }
                )

                ProfileActionButton(
                  icon = Icons.Default.Share,
                  label = "Share",
                  onClick = {}
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Info Details Card
        GlassCard(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(24.dp)
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
            InfoRowItem(icon = Icons.Default.Phone, label = "Mobile", value = user.phoneNumber)
            Spacer(modifier = Modifier.height(12.dp))
            InfoRowItem(icon = Icons.Default.Email, label = "Email", value = user.email)
            Spacer(modifier = Modifier.height(12.dp))
            InfoRowItem(icon = Icons.Default.Person, label = "Username", value = "@${user.username}")
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Shared Media Gallery
        GlassCard(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(24.dp)
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
            Text(
              text = "SHARED MEDIA & GLASSDROPS",
              style = MaterialTheme.typography.labelSmall.copy(
                color = TextMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp
              )
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              items(sampleSharedMedia) { url ->
                Box(
                  modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (glassConfig.isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.70f))
                ) {
                  AsyncImage(
                    model = url,
                    contentDescription = "Shared media",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                  )
                }
              }
            }
          }
        }

        if (!isMe) {
          Spacer(modifier = Modifier.height(16.dp))

          // Privacy & Safety Controls Card
          GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
          ) {
            Column(modifier = Modifier.padding(18.dp)) {
              // Mute toggle
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = if (isMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                  contentDescription = null,
                  tint = TextSecondary,
                  modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                  text = "Mute Notifications",
                  color = TextPrimary,
                  style = MaterialTheme.typography.bodyMedium,
                  modifier = Modifier.weight(1f)
                )
                Switch(
                  checked = isMuted,
                  onCheckedChange = { isMuted = it },
                  colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = CyanAccent
                  )
                )
              }

              Spacer(modifier = Modifier.height(16.dp))

              // Block User
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .clickable {
                    if (isBlocked) viewModel.unblockUser(user.uid) else viewModel.blockUser(user.uid)
                  }
                  .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.Block,
                  contentDescription = null,
                  tint = CoralEndCall,
                  modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                  text = if (isBlocked) "Unblock ${user.displayName}" else "Block ${user.displayName}",
                  color = CoralEndCall,
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 15.sp
                )
              }

              Spacer(modifier = Modifier.height(8.dp))

              // Report User
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .clickable {}
                  .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.Report,
                  contentDescription = null,
                  tint = Color(0xFFF59E0B),
                  modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                  text = "Report User",
                  color = Color(0xFFF59E0B),
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 15.sp
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(30.dp))
      }
    }
  }
}

@Composable
fun ProfileActionButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  onClick: () -> Unit
) {
  val glassConfig = LocalLiquidGlass.current

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.clickable(onClick = onClick)
  ) {
    Box(
      modifier = Modifier
        .size(48.dp)
        .clip(CircleShape)
        .background(if (glassConfig.isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.76f)),
      contentAlignment = Alignment.Center
    ) {
      Icon(icon, contentDescription = label, tint = CyanAccent, modifier = Modifier.size(22.dp))
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(label, style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 11.sp))
  }
}

@Composable
fun InfoRowItem(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  value: String
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
    Spacer(modifier = Modifier.width(12.dp))
    Column {
      Text(label, color = TextMuted, fontSize = 11.sp)
      Text(value, color = TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
    }
  }
}
