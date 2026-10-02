package com.example.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Message
import com.example.data.model.MessageType
import com.example.ui.components.GlassCard
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 26.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                modifier = Modifier.fillMaxWidth().widthIn(max = 370.dp),
                shape = RoundedCornerShape(28.dp),
                backgroundColor = if (glass.isDark) {
                    Color(0xFF0C1723).copy(alpha = .88f)
                } else {
                    Color(0xFFF7FBFF).copy(alpha = .82f)
                },
                elevation = 18.dp
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(13.dp)
                        .animateContentSize(spring(dampingRatio = .78f, stiffness = 420f)),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                        Text(
                            "Message",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (message.text.isNotBlank()) {
                            Text(
                                message.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        backgroundColor = glass.accentColor.copy(alpha = if (glass.isDark) .10f else .065f),
                        elevation = 0.dp
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("❤️", "👍", "😂", "😮", "😢", "🙏").forEach { emoji ->
                                Text(
                                    emoji,
                                    fontSize = 23.sp,
                                    modifier = Modifier
                                        .clickable { act { onReaction(emoji) } }
                                        .padding(horizontal = 4.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        MessageActionTileV3(
                            Icons.AutoMirrored.Filled.Reply,
                            "Reply",
                            Modifier.weight(1f)
                        ) { act(onReply) }
                        MessageActionTileV3(
                            Icons.Default.ContentCopy,
                            "Copy",
                            Modifier.weight(1f)
                        ) { act(onCopy) }
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        MessageActionTileV3(
                            Icons.Default.StarOutline,
                            if (message.isStarred) "Unstar" else "Star",
                            Modifier.weight(1f)
                        ) { act(onToggleStar) }
                        MessageActionTileV3(
                            Icons.Default.PushPin,
                            if (message.isPinned) "Unpin" else "Pin",
                            Modifier.weight(1f)
                        ) { act(onTogglePin) }
                    }

                    if (isMine && message.type == MessageType.TEXT && !message.isDeleted) {
                        MessageActionRowV3(
                            icon = Icons.Default.Edit,
                            label = "Edit message"
                        ) { act(onEdit) }
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
    }
}

@Composable
private fun MessageActionTileV3(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val glass = LocalLiquidGlass.current
    GlassCard(
        modifier = modifier,
        shape = RoundedCornerShape(19.dp),
        backgroundColor = if (glass.isDark) Color.White.copy(alpha = .05f)
        else Color.White.copy(alpha = .44f),
        elevation = 0.dp,
        onClick = onClick
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(19.dp)
            )
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
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
    val foreground = if (destructive) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurface

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(19.dp),
        backgroundColor = when {
            destructive -> MaterialTheme.colorScheme.error.copy(alpha = .065f)
            glass.isDark -> Color.White.copy(alpha = .045f)
            else -> Color.White.copy(alpha = .44f)
        },
        elevation = 0.dp,
        onClick = onClick
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(19.dp))
            Text(
                label,
                color = foreground,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
