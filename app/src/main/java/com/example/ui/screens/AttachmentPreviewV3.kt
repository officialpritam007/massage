package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.MessageType
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassTextField
import com.example.ui.components.liquidRoundedShape
import com.example.ui.theme.LocalLiquidGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PendingAttachmentV3(val uri: Uri, val type: MessageType)

@Composable
fun AttachmentPreviewV3(
    attachment: PendingAttachmentV3,
    caption: String,
    deleting: Boolean,
    onCaptionChange: (String) -> Unit,
    onDiscard: () -> Unit,
    onSend: () -> Unit,
    uploading: Boolean = false,
    busy: Boolean = false,
    uploadFailed: Boolean = false,
    onCancelUpload: () -> Unit = {}
) {
    val glass = LocalLiquidGlass.current
    val context = LocalContext.current
    val fileName by produceState("Document", attachment.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.query(attachment.uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Document"
        }
    }

    DustDeleteContainerV2(active = deleting, reduced = glass.isReducedMotion, modifier = Modifier.fillMaxWidth()) {
        GlassCard(modifier = Modifier.fillMaxWidth(), shape = liquidRoundedShape(28f), elevation = 5.dp) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                uploading -> "Sending attachment"
                                uploadFailed -> "Ready to retry"
                                else -> "Preview attachment"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (uploadFailed) Text("Upload failed. Your attachment is still here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (uploading) TextButton(onClick = onCancelUpload) { Text("Cancel upload") }
                }

                when (attachment.type) {
                    MessageType.IMAGE -> AsyncImage(
                        model = attachment.uri,
                        contentDescription = "Photo preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(164.dp).clip(RoundedCornerShape(18.dp))
                    )
                    MessageType.VIDEO -> Box(
                        Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Movie, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Video selected", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    MessageType.FILE -> Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .08f)).padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Description, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                    else -> Unit
                }

                if (attachment.type != MessageType.FILE) GlassTextField(
                    value = caption,
                    onValueChange = { if (!uploading && !busy && it.length <= 1000) onCaptionChange(it) },
                    placeholder = "Add a caption…",
                    singleLine = false,
                    maxLines = 3,
                    shape = liquidRoundedShape(20f)
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton("Discard", onDiscard, modifier = Modifier.weight(1f), isPrimary = false, enabled = !deleting && !uploading && !busy)
                    GlassButton(if (uploadFailed) "Retry send" else "Send", onSend, modifier = Modifier.weight(1f), isLoading = uploading, enabled = !deleting && !uploading && !busy)
                }
            }
        }
    }
}
