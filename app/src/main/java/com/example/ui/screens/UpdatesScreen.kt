package com.example.ui.screens

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.VideoView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.StatusType
import com.example.data.model.UserStatus
import com.example.ui.components.GlassAvatar
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassTextField
import com.example.ui.components.LiquidBackground
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.GlassBorderStroke
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletAccent
import com.example.ui.viewmodel.LiquidChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UpdatesScreen(
  viewModel: LiquidChatViewModel,
  onNavigateToChats: () -> Unit,
  onNavigateToGroups: () -> Unit,
  onNavigateToCalls: () -> Unit,
  onNavigateToSettings: () -> Unit,
  modifier: Modifier = Modifier
) {
  val statuses by viewModel.statuses.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()

  var viewingStatus by remember { mutableStateOf<UserStatus?>(null) }
  var showPostStatusDialog by remember { mutableStateOf(false) }
  val context = LocalContext.current

  val imagePicker = rememberLauncherForActivityResult(
    ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    uri ?: return@rememberLauncherForActivityResult
    viewModel.uploadAndPostStatus(uri, StatusType.IMAGE) { }
    showPostStatusDialog = false
  }

  val videoPicker = rememberLauncherForActivityResult(
    ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    uri ?: return@rememberLauncherForActivityResult
    val retriever = MediaMetadataRetriever()
    try {
      retriever.setDataSource(context, uri)
      val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
      if (durationMs <= 30_000L) {
        viewModel.uploadAndPostStatus(uri, StatusType.VIDEO) { }
        showPostStatusDialog = false
      } else {
        Toast.makeText(context, "Please choose or trim a video to 30 seconds or less.", Toast.LENGTH_LONG).show()
      }
    } finally {
      retriever.release()
    }
  }

  val statusGradients = listOf(
    listOf(Color(0xFF0072FF), Color(0xFF00D2FF)),
    listOf(Color(0xFF8B5CF6), Color(0xFF3B82F6)),
    listOf(Color(0xFF10B981), Color(0xFF06B6D4)),
    listOf(Color(0xFFFF3B5C), Color(0xFFFF8A00))
  )

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
              text = "Status Updates",
              style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                fontSize = 20.sp
              ),
              modifier = Modifier.weight(1f)
            )

            IconButton(onClick = { showPostStatusDialog = true }) {
              Icon(Icons.Default.Edit, contentDescription = "New Status", tint = CyanAccent)
            }
          }
        }
      },
      bottomBar = {
        GlassBottomBar(
          selectedRoute = "updates",
          onNavigateToChats = onNavigateToChats,
          onNavigateToGroups = onNavigateToGroups,
          onNavigateToUpdates = {},
          onNavigateToCalls = onNavigateToCalls,
          onNavigateToSettings = onNavigateToSettings
        )
      },
      floatingActionButton = {
        FloatingActionButton(
          onClick = { showPostStatusDialog = true },
          containerColor = CyanAccent,
          contentColor = Color.Black,
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier
            .padding(bottom = 72.dp)
            .testTag("post_status_fab")
        ) {
          Icon(Icons.Default.PhotoCamera, contentDescription = "Add Story")
        }
      }
    ) { innerPadding ->
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        // My Status Row
        item {
          GlassCard(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { showPostStatusDialog = true },
            shape = RoundedCornerShape(20.dp)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier.size(54.dp),
                contentAlignment = Alignment.Center
              ) {
                GlassAvatar(
                  photoUrl = currentUser.photoUrl,
                  name = currentUser.displayName,
                  size = 54.dp
                )
                Box(
                  modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(CyanAccent)
                    .align(Alignment.BottomEnd),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(14.dp)
                  )
                }
              }

              Spacer(modifier = Modifier.width(14.dp))

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "My Status",
                  style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                  )
                )
                Text(
                  text = "Tap to share a story, photo or thought",
                  style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
              }
            }
          }
        }

        item {
          Text(
            text = "RECENT UPDATES",
            style = MaterialTheme.typography.labelSmall.copy(
              color = TextMuted,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp,
              letterSpacing = 1.sp
            ),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
          )
        }

        // Contact Statuses
        items(statuses, key = { it.id }) { status ->
          val isViewed = status.isViewedByMe
          val ringBrush = if (!isViewed) {
            Brush.sweepGradient(listOf(CyanNeon, ElectricBlue, CyanAccent, CyanNeon))
          } else {
            Brush.linearGradient(listOf(Color.Gray.copy(alpha = 0.5f), Color.DarkGray))
          }

          GlassCard(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                viewModel.markStatusViewed(status.id)
                viewingStatus = status
              }
              .testTag("status_item_${status.id}"),
            shape = RoundedCornerShape(20.dp)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(54.dp)
                  .clip(CircleShape)
                  .border(2.5.dp, ringBrush, CircleShape)
                  .padding(3.dp),
                contentAlignment = Alignment.Center
              ) {
                GlassAvatar(
                  photoUrl = status.userPhotoUrl,
                  name = status.userName,
                  size = 46.dp
                )
              }

              Spacer(modifier = Modifier.width(14.dp))

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = status.userName,
                  style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                  )
                )
                val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                Text(
                  text = "Today, ${timeFormat.format(Date(status.createdAt))}",
                  style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
              }

              if (status.viewerNames.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.RemoveRedEye,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = "${status.viewerNames.size}",
                    color = TextMuted,
                    fontSize = 12.sp
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  // Fullscreen Status Viewer Dialog
  viewingStatus?.let { status ->
    Dialog(
      onDismissRequest = { viewingStatus = null },
      properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
      val bgGradient = statusGradients[status.backgroundGradientIndex % statusGradients.size]

      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Brush.verticalGradient(bgGradient))
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
        ) {
          // Progress timer bar
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .height(3.dp)
              .clip(RoundedCornerShape(2.dp))
              .background(Color.White.copy(alpha = 0.35f))
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth(0.85f)
                .fillMaxSize()
                .background(Color.White)
            )
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Top Header in Story
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            GlassAvatar(photoUrl = status.userPhotoUrl, name = status.userName, size = 44.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(text = status.userName, style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
              Text(text = "Expires in 24 hours", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.7f)))
            }
            IconButton(onClick = { viewingStatus = null }) {
              Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
          }

          Spacer(modifier = Modifier.weight(1f))

          // Story Content
          if (status.type == StatusType.IMAGE) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .clip(RoundedCornerShape(24.dp))
            ) {
              AsyncImage(
                model = status.content,
                contentDescription = "Status Image",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
              )
            }
          } else if (status.type == StatusType.VIDEO) {
            AndroidView(
              factory = { ctx ->
                VideoView(ctx).apply {
                  setVideoURI(Uri.parse(status.content))
                  setOnPreparedListener { player ->
                    player.isLooping = true
                    start()
                  }
                }
              },
              modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .clip(RoundedCornerShape(24.dp))
            )
          } else {
            Text(
              text = status.content,
              style = MaterialTheme.typography.headlineMedium.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                textAlign = TextAlign.Center
              ),
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
            )
          }

          Spacer(modifier = Modifier.weight(1f))

          // Viewers row
          if (status.viewerNames.isNotEmpty()) {
            Row(
              modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.RemoveRedEye, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Viewed by ${status.viewerNames.joinToString(", ")}",
                color = Color.White,
                fontSize = 12.sp
              )
            }
          }
        }
      }
    }
  }

  // Create Status Modal
  if (showPostStatusDialog) {
    var statusText by remember { mutableStateOf("") }
    var selectedGradientIndex by remember { mutableIntStateOf(0) }

    Dialog(onDismissRequest = { showPostStatusDialog = false }) {
      GlassCard(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        shape = RoundedCornerShape(24.dp)
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = "Share 24-hr Status",
            style = MaterialTheme.typography.titleLarge.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
          )

          Spacer(modifier = Modifier.height(14.dp))

          // Gradient preview box
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(120.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(Brush.horizontalGradient(statusGradients[selectedGradientIndex]))
              .padding(12.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = statusText.ifBlank { "Type what's on your mind..." },
              color = Color.White,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center,
              fontSize = 16.sp
            )
          }

          Spacer(modifier = Modifier.height(14.dp))

          GlassTextField(
            value = statusText,
            onValueChange = { statusText = it },
            placeholder = "Status update text...",
            testTag = "status_input_text"
          )

          Spacer(modifier = Modifier.height(10.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(
              text = "Photo",
              onClick = { imagePicker.launch("image/*") },
              isPrimary = false,
              modifier = Modifier.weight(1f)
            )
            GlassButton(
              text = "Video • 30s",
              onClick = { videoPicker.launch("video/*") },
              isPrimary = false,
              modifier = Modifier.weight(1f)
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          // Palette selector
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
          ) {
            statusGradients.forEachIndexed { index, gradient ->
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(CircleShape)
                  .background(Brush.linearGradient(gradient))
                  .border(
                    width = if (selectedGradientIndex == index) 2.5.dp else 1.dp,
                    color = if (selectedGradientIndex == index) Color.White else Color.Transparent,
                    shape = CircleShape
                  )
                  .clickable { selectedGradientIndex = index }
              )
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            GlassButton(
              text = "Cancel",
              onClick = { showPostStatusDialog = false },
              isPrimary = false,
              modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            GlassButton(
              text = "Post Story",
              onClick = {
                if (statusText.isNotBlank()) {
                  viewModel.postStatus(StatusType.TEXT, statusText.trim(), selectedGradientIndex)
                  showPostStatusDialog = false
                }
              },
              isPrimary = true,
              modifier = Modifier.weight(1f),
              testTag = "confirm_post_status"
            )
          }
        }
      }
    }
  }
}
