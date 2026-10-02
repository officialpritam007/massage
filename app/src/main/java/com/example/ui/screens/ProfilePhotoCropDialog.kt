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
import androidx.compose.runtime.produceState
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.domain.profileCrop
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
    val bitmap by produceState<Bitmap?>(null, sourceUri) {
        runCatching { withContext(Dispatchers.IO) { decodeProfileBitmap(context, sourceUri) } }
            .onSuccess { value = it }.onFailure { error = it.message ?: "Unable to open photo" }
    }
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
                bitmap?.let { source ->
                    Canvas(Modifier.size(previewDp).clip(CircleShape).pointerInput(sourceUri, source) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            zoom = (zoom * gestureZoom).coerceIn(1f, 4f)
                            val base = max(previewPx / source.width, previewPx / source.height) * zoom
                            val limitX = ((source.width * base - previewPx) / 2).coerceAtLeast(0f)
                            val limitY = ((source.height * base - previewPx) / 2).coerceAtLeast(0f)
                            offset = Offset((offset.x + pan.x).coerceIn(-limitX, limitX), (offset.y + pan.y).coerceIn(-limitY, limitY))
                        }
                    }) {
                        val crop = profileCrop(source.width, source.height, previewPx, zoom, offset.x, offset.y)
                        drawImage(source.asImageBitmap(), srcOffset = IntOffset(crop.left, crop.top),
                            srcSize = IntSize(crop.side, crop.side), dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                    }
                }

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
                    isLoading = saving,
                    enabled = bitmap != null && !saving
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

    val rect = profileCrop(source.width, source.height, previewPixels, zoom, offset.x, offset.y)
    val cropped = Bitmap.createBitmap(source, rect.left, rect.top, rect.side, rect.side)
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
