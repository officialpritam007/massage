package com.example.ui.screens

import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.MessageType
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassTextField
import com.example.ui.theme.LocalLiquidGlass

data class PendingAttachmentV3(
    val uri: Uri,
    val type: MessageType
)

@Composable
fun AttachmentPreviewV3(
    attachment: PendingAttachmentV3,
    caption: String,
    deleting: Boolean,
    onCaptionChange: (String) -> Unit,
    onDiscard: () -> Unit,
    onSend: () -> Unit
) {
    val glass = LocalLiquidGlass.current
    DustDeleteContainerV2(
        active = deleting,
        reduced = glass.isReducedMotion,
        modifier = Modifier.fillMaxWidth()
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            backgroundColor = glass.accentColor.copy(alpha = if (glass.isDark) .09f else .06f),
            elevation = 4.dp
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .animateContentSize(spring(dampingRatio = .78f, stiffness = 390f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (attachment.type) {
                    MessageType.IMAGE -> {
                        AsyncImage(
                            model = attachment.uri,
                            contentDescription = "Photo preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                        )
                    }
                    MessageType.VIDEO -> {
                        Box(
                            Modifier.fillMaxWidth().height(150.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Movie,
                                contentDescription = null,
                                tint = glass.accentColor
                            )
                            Text(
                                "Video selected",
                                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    MessageType.FILE -> {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                tint = glass.accentColor
                            )
                            Text(
                                attachment.uri.lastPathSegment ?: "Document",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    else -> Unit
                }

                if (attachment.type != MessageType.FILE) {
                    GlassTextField(
                        value = caption,
                        onValueChange = { if (it.length <= 1000) onCaptionChange(it) },
                        placeholder = "Add a caption…",
                        singleLine = false,
                        maxLines = 4,
                        shape = RoundedCornerShape(22.dp)
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    GlassButton(
                        "Cancel",
                        onDiscard,
                        modifier = Modifier.weight(1f),
                        isPrimary = false,
                        enabled = !deleting
                    )
                    GlassButton(
                        "Send",
                        onSend,
                        modifier = Modifier.weight(1f),
                        enabled = !deleting
                    )
                }
            }
        }
    }
}
