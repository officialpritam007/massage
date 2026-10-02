package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Message
import com.example.data.model.MessageType
import com.example.data.model.User
import com.example.ui.components.GlassAvatar
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassDialog
import com.example.ui.components.GlassHeader
import com.example.ui.components.GlassTextField
import com.example.ui.components.LiquidBackground
import com.example.ui.components.PrivateImage
import com.example.ui.components.PrivateVideoThumbnail
import com.example.ui.components.VoiceWaveformPlayer
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.viewmodel.LiquidChatViewModel

@Composable
fun ContactProfileScreen(
    userId: String,
    viewModel: LiquidChatViewModel,
    onBackClick: () -> Unit,
    onNavigateToConversation: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val users by viewModel.users.collectAsState()
    val me by viewModel.currentUser.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val blocked by viewModel.blockedUserIds.collectAsState()
    val glass = LocalLiquidGlass.current
    val context = LocalContext.current

    val user = if (userId == me.uid) me
    else users.find { it.uid == userId } ?: User(uid = userId, displayName = "Contact")

    val conversation = conversations.find { user.uid in it.participantIds }

    LaunchedEffect(conversation?.id) {
        conversation?.id?.let { viewModel.observeConversation(it) }
    }

    val shared = conversation?.let { messages[it.id] }.orEmpty()
        .filter { it.mediaUrl.isNotBlank() && !it.isDeleted }
        .sortedByDescending { it.createdAt }

    var report by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    var media by remember { mutableStateOf<Message?>(null) }
    var showPhoto by remember(user.photoUrl) { mutableStateOf(false) }

    LiquidBackground(modifier, crystal = true) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GlassHeader("Profile", onBackClick = onBackClick)

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                backgroundColor = if (glass.isDark) {
                    Color(0xFF0D1723).copy(alpha = .68f)
                } else {
                    Color.White.copy(alpha = .58f)
                },
                elevation = 7.dp
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .animateContentSize(spring(dampingRatio = .78f, stiffness = 390f))
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        GlassAvatar(
                            photoUrl = user.photoUrl,
                            name = user.displayName.ifBlank { "Contact" },
                            size = 112.dp,
                            isOnline = user.isOnline && user.onlineVisible,
                            onClick = { showPhoto = true }
                        )
                        if (user.isOnline && user.onlineVisible) {
                            Box(
                                Modifier
                                    .size(21.dp)
                                    .background(EmeraldOnline, CircleShape)
                            )
                        }
                    }

                    Spacer(Modifier.height(2.dp))
                    Text(
                        user.displayName.ifBlank { "Contact" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    if (user.username.isNotBlank()) {
                        Text(
                            "@" + user.username,
                            style = MaterialTheme.typography.bodyMedium,
                            color = glass.accentColor
                        )
                    }

                    if (user.uid != me.uid) {
                        val status = presenceLabel(user)
                        if (status.isNotBlank()) {
                            Text(
                                status,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (user.isOnline) EmeraldOnline else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (user.bio.isNotBlank()) {
                        Text(
                            user.bio,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    if (user.uid != me.uid) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GlassButton(
                                "Message",
                                { onNavigateToConversation(viewModel.getOrCreateConversationId(user.uid)) },
                                modifier = Modifier.weight(1f)
                            )
                            GlassButton(
                                "Share",
                                {
                                    context.startActivity(
                                        Intent.createChooser(
                                            Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(
                                                    Intent.EXTRA_TEXT,
                                                    "Find " + user.displayName + " on Liquid Chat: @" + user.username
                                                )
                                            },
                                            "Share contact"
                                        )
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                isPrimary = false
                            )
                        }
                    } else {
                        GlassButton(
                            "View profile photo",
                            { showPhoto = true },
                            modifier = Modifier.fillMaxWidth(),
                            isPrimary = false
                        )
                    }
                }
            }

            Text(
                "Shared media",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 6.dp)
            )

            if (shared.isEmpty()) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    elevation = 0.dp
                ) {
                    Text(
                        "No shared media yet",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(shared.take(30), key = { it.id }) { message ->
                        SharedMediaCard(message = message, onClick = { media = message })
                    }
                }
                conversation?.let { current ->
                    TextButton(
                        onClick = { viewModel.repository.loadOlder(current.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Load older shared media")
                    }
                }
            }

            if (user.uid != me.uid) {
                GlassCard(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    elevation = 1.dp
                ) {
                    Column(Modifier.fillMaxWidth().padding(8.dp)) {
                        conversation?.let { current ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Mute notifications",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        if (current.isMuted) "Notifications are muted" else "Message notifications are on",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = current.isMuted,
                                    onCheckedChange = { viewModel.setConversationMuted(current.id, it) }
                                )
                            }
                        }

                        Spacer(Modifier.height(3.dp))
                        GlassButton(
                            if (user.uid in blocked) "Unblock contact" else "Block contact",
                            {
                                if (user.uid in blocked) viewModel.unblockUser(user.uid)
                                else viewModel.blockUser(user.uid)
                            },
                            isPrimary = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextButton(
                            onClick = { report = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Report contact", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        if (report) {
            GlassDialog("Report contact", { report = false }) {
                GlassTextField(
                    reason,
                    { reason = it },
                    placeholder = "Describe the problem",
                    singleLine = false,
                    maxLines = 5
                )
                GlassButton(
                    "Submit report",
                    {
                        viewModel.repository.report(user.uid, reason)
                        report = false
                    },
                    enabled = reason.trim().length >= 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (showPhoto) {
            ProfilePhotoViewer(
                photoUrl = user.photoUrl,
                displayName = user.displayName.ifBlank { "Contact" },
                onDismiss = { showPhoto = false }
            )
        }

        media?.let { selected ->
            MediaViewer(selected) { media = null }
        }
    }
}

@Composable
private fun SharedMediaCard(
    message: Message,
    onClick: () -> Unit
) {
    val glass = LocalLiquidGlass.current
    GlassCard(
        modifier = when (message.type) {
            MessageType.VOICE -> Modifier.width(230.dp).height(116.dp)
            MessageType.FILE -> Modifier.width(170.dp).height(116.dp)
            else -> Modifier.size(116.dp)
        },
        shape = RoundedCornerShape(24.dp),
        backgroundColor = if (glass.isDark) Color(0xFF0D1723).copy(alpha = .62f)
        else Color.White.copy(alpha = .58f),
        elevation = 1.dp,
        onClick = onClick
    ) {
        when (message.type) {
            MessageType.IMAGE -> PrivateImage(
                message.mediaUrl,
                "Shared photo",
                Modifier.fillMaxSize()
            )
            MessageType.VIDEO -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PrivateVideoThumbnail(message.mediaUrl, Modifier.fillMaxSize())
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
            MessageType.VOICE -> Row(
                Modifier.fillMaxSize().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = glass.accentColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(6.dp))
                VoiceWaveformPlayer(
                    durationSeconds = message.voiceDurationSeconds,
                    mediaUrl = message.mediaUrl,
                    waveform = message.waveform,
                    modifier = Modifier.weight(1f),
                    isOutgoing = false
                )
            }
            MessageType.FILE -> Column(
                Modifier.fillMaxSize().padding(14.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = glass.accentColor,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    message.text.ifBlank { "Document" },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(message.type.name)
            }
        }
    }
}
