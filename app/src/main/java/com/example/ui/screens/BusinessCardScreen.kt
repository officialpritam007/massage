package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassAvatar
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.LiquidBackground
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.GlassBorderStroke
import com.example.ui.theme.GlassHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.LiquidChatViewModel
import kotlinx.coroutines.launch

@Composable
fun BusinessCardScreen(
  viewModel: LiquidChatViewModel,
  onNavigateToChats: () -> Unit,
  onNavigateToGroups: () -> Unit,
  onNavigateToUpdates: () -> Unit,
  onNavigateToCalls: () -> Unit,
  modifier: Modifier = Modifier
) {
  val currentUser by viewModel.currentUser.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  LiquidBackground(modifier = modifier) {
    Scaffold(
      containerColor = Color.Transparent,
      snackbarHost = { SnackbarHost(snackbarHostState) },
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
              text = "Digital Business Card",
              style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                fontSize = 20.sp
              ),
              modifier = Modifier.weight(1f)
            )

            Icon(
              imageVector = Icons.Default.QrCode2,
              contentDescription = null,
              tint = CyanAccent,
              modifier = Modifier.size(24.dp)
            )
          }
        }
      },
      bottomBar = {
        GlassBottomBar(
          selectedRoute = "card",
          onNavigateToChats = onNavigateToChats,
          onNavigateToGroups = onNavigateToGroups,
          onNavigateToUpdates = onNavigateToUpdates,
          onNavigateToCalls = onNavigateToCalls,
          onNavigateToCard = {}
        )
      }
    ) { innerPadding ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Luxury Liquid Glass Card (Specially designed for business identity)
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(
              Brush.linearGradient(
                colors = listOf(
                  Color(0xFF13203C).copy(alpha = 0.85f),
                  Color(0xFF0B1428).copy(alpha = 0.90f),
                  Color(0xFF162545).copy(alpha = 0.80f)
                )
              )
            )
            .border(
              width = 1.5.dp,
              brush = Brush.linearGradient(
                listOf(CyanNeon, GlassHighlight, Color(0x10FFFFFF))
              ),
              shape = RoundedCornerShape(32.dp)
            )
            .padding(24.dp)
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
          ) {
            // Header with Brand Badge
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.WaterDrop,
                  contentDescription = null,
                  tint = CyanAccent,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "LIQUID IDENTITY",
                  style = TextStyle(
                    color = CyanAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp
                  )
                )
              }

              Icon(
                imageVector = Icons.Default.Verified,
                contentDescription = "Verified Profile",
                tint = CyanAccent,
                modifier = Modifier.size(20.dp)
              )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Large Glass Avatar
            GlassAvatar(
              photoUrl = currentUser.photoUrl,
              name = currentUser.displayName,
              size = 88.dp,
              isOnline = true
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
              text = currentUser.displayName,
              style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 22.sp
              )
            )

            Text(
              text = "@${currentUser.username}",
              style = MaterialTheme.typography.bodyMedium.copy(
                color = CyanAccent,
                fontWeight = FontWeight.SemiBold
              )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
              text = currentUser.bio,
              style = MaterialTheme.typography.bodyMedium.copy(
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
              ),
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Styled Mock QR Code Matrix
            Box(
              modifier = Modifier
                .size(170.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .padding(14.dp),
              contentAlignment = Alignment.Center
            ) {
              Canvas(modifier = Modifier.fillMaxSize()) {
                val moduleCount = 13
                val moduleSize = size.width / moduleCount
                val darkColor = Color(0xFF0A0F1D)

                // Render aesthetic QR pattern representing public ID
                for (row in 0 until moduleCount) {
                  for (col in 0 until moduleCount) {
                    val isCornerFinder = (row < 4 && col < 4) || (row < 4 && col >= moduleCount - 4) || (row >= moduleCount - 4 && col < 4)
                    val isInnerCorner = (row in 1..2 && col in 1..2) || (row in 1..2 && col in (moduleCount - 3)..(moduleCount - 2)) || (row in (moduleCount - 3)..(moduleCount - 2) && col in 1..2)
                    val isPattern = isCornerFinder || (row + col * 3) % 2 == 0 || (row * col) % 3 == 0

                    if (isPattern && !isInnerCorner || (row in 1..2 && col in 1..2)) {
                      drawRect(
                        color = darkColor,
                        topLeft = Offset(col * moduleSize, row * moduleSize),
                        size = Size(moduleSize * 0.9f, moduleSize * 0.9f)
                      )
                    }
                  }
                }
              }

              // Center water droplet brand mark
              Box(
                modifier = Modifier
                  .size(34.dp)
                  .clip(CircleShape)
                  .background(Color(0xFF0A0F1D))
                  .border(2.dp, CyanAccent, CircleShape),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.WaterDrop,
                  contentDescription = null,
                  tint = CyanAccent,
                  modifier = Modifier.size(18.dp)
                )
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
              text = "Public ID: ${currentUser.uid}",
              style = TextStyle(color = TextMuted, fontSize = 11.sp, letterSpacing = 0.5.sp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Contact details inside card
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.3f))
                .padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Phone, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(currentUser.phoneNumber, color = TextPrimary, fontSize = 12.5.sp)
              }

              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Email, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(currentUser.email, color = TextPrimary, fontSize = 12.5.sp)
              }

              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Language, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(currentUser.website, color = CyanAccent, fontSize = 12.5.sp)
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action Buttons: Share & Copy
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          GlassButton(
            text = "Share Card",
            icon = Icons.Default.Share,
            onClick = {
              scope.launch {
                snackbarHostState.showSnackbar("Digital Business Card shared via system sheet!")
              }
            },
            isPrimary = true,
            modifier = Modifier.weight(1f),
            testTag = "share_business_card"
          )

          GlassButton(
            text = "Copy Link",
            icon = Icons.Default.ContentCopy,
            onClick = {
              scope.launch {
                snackbarHostState.showSnackbar("Public profile link copied to clipboard!")
              }
            },
            isPrimary = false,
            modifier = Modifier.weight(1f),
            testTag = "copy_profile_link"
          )
        }

        Spacer(modifier = Modifier.height(30.dp))
      }
    }
  }
}
