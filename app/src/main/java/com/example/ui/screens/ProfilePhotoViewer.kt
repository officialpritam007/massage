package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.GlassAvatar
import com.example.ui.components.GlassCard
import com.example.ui.components.PrivateImage
import com.example.ui.theme.LocalLiquidGlass

@Composable
fun ProfilePhotoViewer(
    photoUrl: String,
    displayName: String,
    onDismiss: () -> Unit
) {
    val glass = LocalLiquidGlass.current
    var scale by remember(photoUrl) { mutableFloatStateOf(1f) }
    var offset by remember(photoUrl) { mutableStateOf(Offset.Zero) }
    val dim by animateFloatAsState(
        targetValue = if (scale > 1f) .94f else .88f,
        animationSpec = spring(dampingRatio = .8f, stiffness = 420f),
        label = "profile_photo_dim"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = dim))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (scale <= 1.02f) onDismiss() },
                        onDoubleTap = {
                            if (scale > 1.02f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.25f
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (photoUrl.isBlank()) {
                GlassCard(shape = CircleShape, backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = .36f)) {
                    GlassAvatar("", displayName, 148.dp)
                }
            } else {
                PrivateImage(
                    model = photoUrl,
                    contentDescription = displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 74.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                        .pointerInput(photoUrl) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val next = (scale * zoom).coerceIn(1f, 5f)
                                scale = next
                                offset = if (next <= 1.02f) Offset.Zero else {
                                    Offset(
                                        x = offset.x + pan.x,
                                        y = offset.y + pan.y
                                    )
                                }
                            }
                        }
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(18.dp)
                    .size(48.dp)
                    .background(
                        if (glass.isDark) Color.White.copy(alpha = .12f) else Color.Black.copy(alpha = .36f),
                        CircleShape
                    )
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close photo", tint = Color.White)
            }
        }
    }
}
