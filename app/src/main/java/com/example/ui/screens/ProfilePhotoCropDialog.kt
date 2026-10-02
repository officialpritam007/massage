package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassDialog
import com.example.ui.theme.LocalLiquidGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun ProfilePhotoCropDialog(
    sourceUri: Uri,
    onDismiss: () -> Unit,
    onCropped: (Uri) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val glass = LocalLiquidGlass.current
    val scope = rememberCoroutineScope()

    var zoom by remember(sourceUri) { mutableFloatStateOf(1f) }
    var offset by remember(sourceUri) { mutableStateOf(Offset.Zero) }
    var saving by remember(sourceUri) { mutableStateOf(false) }
    var error by remember(sourceUri) { mutableStateOf<String?>(null) }
    val previewDp = 280.dp
    val previewPx = with(density) { previewDp.toPx() }

    GlassDialog("Adjust profile photo", onDismiss = { if (!saving) onDismiss() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .animateContentSize(spring(dampingRatio = .78f, stiffness = 390f)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(previewDp)
                    .background(
                        if (glass.isDark) Color.Black.copy(alpha = .28f)
                        else Color.White.copy(alpha = .34f),
                        CircleShape
                    )
                    .border(2.dp, Color.White.copy(alpha = .62f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = sourceUri,
                    contentDescription = "Profile photo crop preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(previewDp)
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = offset.x
                            translationY = offset.y
                            clip = true
                            shape = CircleShape
                        }
                        .pointerInput(sourceUri) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                val nextZoom = (zoom * gestureZoom).coerceIn(1f, 4f)
                                val maxPan = previewPx * (.18f + (nextZoom - 1f) * .48f)
                                zoom = nextZoom
                                offset = Offset(
                                    x = (offset.x + pan.x).coerceIn(-maxPan, maxPan),
                                    y = (offset.y + pan.y).coerceIn(-maxPan, maxPan)
                                )
                                error = null
                            }
                        }
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ZoomIn,
                    contentDescription = null,
                    tint = glass.accentColor
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    "Pinch to zoom • drag to position",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassButton(
                    "Cancel",
                    onDismiss,
                    modifier = Modifier.weight(1f),
                    isPrimary = false,
                    enabled = !saving
                )
                GlassButton(
                    "Use photo",
                    {
                        if (saving) return@GlassButton
                        saving = true
                        error = null
                        scope.launch {
                            runCatching {
                                cropProfilePhoto(
                                    context = context,
                                    uri = sourceUri,
                                    zoom = zoom,
                                    offset = offset,
                                    previewPixels = previewPx
                                )
                            }.onSuccess(onCropped)
                                .onFailure {
                                    error = it.message ?: "Could not crop this photo"
                                    saving = false
                                }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    isLoading = saving
                )
            }
        }
    }
}

private suspend fun cropProfilePhoto(
    context: Context,
    uri: Uri,
    zoom: Float,
    offset: Offset,
    previewPixels: Float
): Uri = withContext(Dispatchers.IO) {
    val source = decodeProfileBitmap(context, uri)
    require(source.width > 0 && source.height > 0) { "Invalid photo" }

    val width = source.width.toFloat()
    val height = source.height.toFloat()
    val coverScale = max(previewPixels / width, previewPixels / height)
    val effectiveScale = (coverScale * zoom.coerceIn(1f, 4f)).coerceAtLeast(.0001f)
    val sourceSide = (previewPixels / effectiveScale)
        .coerceAtLeast(1f)
        .coerceAtMost(minOf(width, height))

    val centerX = (width / 2f - offset.x / effectiveScale)
        .coerceIn(sourceSide / 2f, width - sourceSide / 2f)
    val centerY = (height / 2f - offset.y / effectiveScale)
        .coerceIn(sourceSide / 2f, height - sourceSide / 2f)

    val side = sourceSide.roundToInt().coerceIn(1, minOf(source.width, source.height))
    val left = (centerX - side / 2f).roundToInt().coerceIn(0, source.width - side)
    val top = (centerY - side / 2f).roundToInt().coerceIn(0, source.height - side)

    val cropped = Bitmap.createBitmap(source, left, top, side, side)
    val output = if (cropped.width == 1024 && cropped.height == 1024) cropped
    else Bitmap.createScaledBitmap(cropped, 1024, 1024, true)

    val file = File(context.cacheDir, "profile-crop-" + System.nanoTime() + ".jpg")
    file.outputStream().use { stream ->
        check(output.compress(Bitmap.CompressFormat.JPEG, 90, stream)) {
            "Could not save cropped photo"
        }
    }

    if (output !== cropped) output.recycle()
    if (cropped !== source) cropped.recycle()
    source.recycle()

    require(file.exists() && file.length() > 0L) { "Cropped photo is empty" }
    Uri.fromFile(file)
}

private fun decodeProfileBitmap(context: Context, uri: Uri): Bitmap {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(
            ImageDecoder.createSource(context.contentResolver, uri)
        ) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > 4096) {
                val ratio = longest / 4096f
                decoder.setTargetSize(
                    (info.size.width / ratio).roundToInt().coerceAtLeast(1),
                    (info.size.height / ratio).roundToInt().coerceAtLeast(1)
                )
            }
        }
    } else {
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open photo" }
            BitmapFactory.decodeStream(input) ?: error("Unable to decode photo")
        }
    }
}
