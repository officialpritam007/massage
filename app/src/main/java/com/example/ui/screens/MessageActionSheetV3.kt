package com.example.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Message
import com.example.data.model.MessageType
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassDialog
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

    fun act(block: () -> Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        block()
    }

    GlassDialog("Message actions", onDismiss) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            backgroundColor = glass.accentColor.copy(alpha = if (glass.isDark) .10f else .07f),
            elevation = 1.dp
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("❤️", "👍", "😂", "😮", "😢", "🙏").forEach { emoji ->
                    Text(
                        emoji,
                        fontSize = 25.sp,
                        modifier = Modifier
                            .clickable { act { onReaction(emoji) } }
                            .padding(horizontal = 5.dp, vertical = 7.dp)
                    )
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .animateContentSize(spring(dampingRatio = .78f, stiffness = 420f)),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MessageActionRowV3(Icons.AutoMirrored.Filled.Reply, "Reply") { act(onReply) }
            MessageActionRowV3(Icons.Default.ContentCopy, "Copy") { act(onCopy) }
            MessageActionRowV3(Icons.Default.StarOutline, if (message.isStarred) "Unstar" else "Star") { act(onToggleStar) }
            MessageActionRowV3(Icons.Default.PushPin, if (message.isPinned) "Unpin" else "Pin") { act(onTogglePin) }

            if (isMine && message.type == MessageType.TEXT && !message.isDeleted) {
                MessageActionRowV3(Icons.Default.Edit, "Edit") { act(onEdit) }
            }

            MessageActionRowV3(
                icon = Icons.Default.DeleteOutline,
                label = "Delete for me",
                destructive = true
            ) { act(onDeleteForMe) }

            if (isMine) {
                MessageActionRowV3(
                    icon = Icons.Default.DeleteOutline,
                    label = "Delete for everyone",
                    destructive = true
                ) { act(onDeleteForEveryone) }
            }
        }
    }
}

@Composable
private fun MessageActionRowV3(
    icon: ImageVector,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val glass = LocalLiquidGlass.current
    val foreground = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        backgroundColor = when {
            destructive -> MaterialTheme.colorScheme.error.copy(alpha = .065f)
            glass.isDark -> Color.White.copy(alpha = .045f)
            else -> Color.White.copy(alpha = .45f)
        },
        elevation = 0.dp,
        onClick = onClick
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(21.dp))
            Text(label, color = foreground, fontWeight = FontWeight.Medium)
        }
    }
}
