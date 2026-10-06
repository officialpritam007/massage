@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Message
import com.example.data.model.MessageType
import com.example.ui.components.GlassCard
import com.example.ui.components.liquidRoundedShape
import com.example.ui.theme.LocalLiquidGlass

@Composable
fun MessageActionSheetV3(
    message: Message,
    isMine: Boolean,
    onDismiss: () -> Unit,
    onReaction: (String) -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onToggleStar: () -> Unit,
    onTogglePin: () -> Unit,
    onEdit: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit
) {
    val glass = LocalLiquidGlass.current
    val haptic = LocalHapticFeedback.current
    val maxHeight = (LocalConfiguration.current.screenHeightDp * .84f).dp
    fun act(block: () -> Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        block()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent,
        dragHandle = null,
        scrimColor = Color.Black.copy(alpha = .36f)
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            shape = liquidRoundedShape(32f),
            backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = if (glass.isDark) .90f else .86f),
            elevation = 12.dp
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(99.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .16f)))
                Column(Modifier.padding(horizontal = 4.dp)) {
                    Text("Message actions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(message.text.ifBlank { "Attachment" }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .08f)).horizontalScroll(rememberScrollState()).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("❤️", "👍", "😂", "😮", "😢", "🙏").forEach { emoji ->
                        val interaction = remember(emoji) { MutableInteractionSource() }
                        val pressed by interaction.collectIsPressedAsState()
                        val scale by animateFloatAsState(if (pressed && !glass.isReducedMotion) 1.14f else 1f, spring(dampingRatio = .72f, stiffness = 560f), label = "reaction_$emoji")
                        Box(
                            Modifier.size(48.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(16.dp))
                                .clickable(interactionSource = interaction, indication = null, role = Role.Button) { act { onReaction(emoji) } }
                                .semantics { contentDescription = "React with $emoji" },
                            contentAlignment = Alignment.Center
                        ) { Text(emoji, fontSize = 26.sp) }
                    }
                }
                Column(Modifier.clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .035f))) {
                    MessageActionRowV3(Icons.AutoMirrored.Filled.Reply, "Reply") { act(onReply) }
                    if (message.text.isNotBlank()) MessageActionRowV3(Icons.Default.ContentCopy, "Copy text") { act(onCopy) }
                    MessageActionRowV3(Icons.Default.StarOutline, if (message.isStarred) "Remove star" else "Star message") { act(onToggleStar) }
                    MessageActionRowV3(Icons.Default.PushPin, if (message.isPinned) "Unpin message" else "Pin message") { act(onTogglePin) }
                    if (isMine && message.type == MessageType.TEXT && !message.isDeleted) MessageActionRowV3(Icons.Default.Edit, "Edit message") { act(onEdit) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
                Column(Modifier.clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.error.copy(alpha = .055f))) {
                    MessageActionRowV3(Icons.Default.DeleteOutline, "Delete for me", destructive = true) { act(onDeleteForMe) }
                    if (isMine) MessageActionRowV3(Icons.Default.DeleteOutline, "Delete for everyone", destructive = true) { act(onDeleteForEveryone) }
                }
            }
        }
    }
}

@Composable
private fun MessageActionRowV3(icon: ImageVector, label: String, destructive: Boolean = false, onClick: () -> Unit) {
    val foreground = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = foreground)
        Text(label, color = foreground, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
    }
}
