package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.DeepMidnight
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.GlassBorderStroke
import com.example.ui.theme.TextMuted
import com.example.ui.theme.MaterialTheme.colorScheme.onBackground
import com.example.ui.theme.TextSecondary
import java.io.File
import java.util.concurrent.Executor

@Composable
fun CameraScreen(
  conversationId: String,
  onPhotoCaptured: (photoUri: Uri, caption: String) -> Unit,
  onClose: () -> Unit
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  // Permission State
  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
      ) == PackageManager.PERMISSION_GRANTED
    )
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    hasCameraPermission = isGranted
  }

  // Camera Settings State
  var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
  var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
  var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
  var previewView: PreviewView? by remember { mutableStateOf(null) }
  var isCapturing by remember { mutableStateOf(false) }

  // Review Captured Photo State
  var capturedPhotoUri by remember { mutableStateOf<Uri?>(null) }
  var captionText by remember { mutableStateOf("") }

  LaunchedEffect(Unit) {
    if (!hasCameraPermission) {
      permissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black)
  ) {
    if (!hasCameraPermission) {
      // Permission Request Liquid Card
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(DeepMidnight)
          .padding(24.dp),
        contentAlignment = Alignment.Center
      ) {
        GlassCard(
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Box(
              modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(CyanAccent.copy(alpha = 0.15f))
                .border(1.dp, CyanAccent.copy(alpha = 0.4f), CircleShape),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                Icons.Default.PhotoCamera,
                contentDescription = null,
                tint = CyanAccent,
                modifier = Modifier.size(32.dp)
              )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
              text = "Camera Access Required",
              style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
              )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
              text = "Grant camera permission to take live photos and send them instantly to your Liquid Chat conversations.",
              style = MaterialTheme.typography.bodyMedium.copy(
                color = TextSecondary,
                textAlign = TextAlign.Center
              )
            )

            Spacer(modifier = Modifier.height(24.dp))

            GlassButton(
              text = "Grant Camera Permission",
              onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
              modifier = Modifier.fillMaxWidth().testTag("grant_camera_permission_button")
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
              text = "Cancel",
              style = MaterialTheme.typography.labelLarge.copy(color = TextMuted),
              modifier = Modifier
                .clickable { onClose() }
                .padding(8.dp)
            )
          }
        }
      }
    } else if (capturedPhotoUri != null) {
      // Photo Review Screen
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black)
      ) {
        AsyncImage(
          model = capturedPhotoUri,
          contentDescription = "Captured Photo",
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Fit
        )

        // Top Bar
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(
            onClick = { capturedPhotoUri = null },
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
              .background(Color(0x80000000))
              .testTag("camera_cancel_preview_button")
          ) {
            Icon(Icons.Default.Close, contentDescription = "Discard", tint = Color.White)
          }
        }

        // Bottom Actions Bar
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(24.dp)
        ) {
          GlassCard(
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              // Retake Button
              Row(
                modifier = Modifier
                  .clip(RoundedCornerShape(12.dp))
                  .clickable { capturedPhotoUri = null }
                  .padding(horizontal = 16.dp, vertical = 10.dp)
                  .testTag("camera_retake_button"),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  Icons.Default.Refresh,
                  contentDescription = "Retake",
                  tint = TextSecondary,
                  modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retake", color = TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
              }

              // Send Button
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(24.dp))
                  .background(Brush.horizontalGradient(listOf(CyanAccent, ElectricBlue)))
                  .clickable {
                    capturedPhotoUri?.let { uri ->
                      onPhotoCaptured(uri, captionText)
                    }
                  }
                  .padding(horizontal = 24.dp, vertical = 12.dp)
                  .testTag("camera_send_button"),
                contentAlignment = Alignment.Center
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = "Send Photo",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Icon(
                    Icons.Default.Send,
                    contentDescription = "Send",
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                  )
                }
              }
            }
          }
        }
      }
    } else {
      // Live Camera Preview
      AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
          val view = PreviewView(ctx)
          previewView = view

          val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
          cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases(
              cameraProvider = cameraProvider,
              previewView = view,
              lensFacing = lensFacing,
              flashMode = flashMode,
              lifecycleOwner = lifecycleOwner,
              onImageCaptureReady = { capture ->
                imageCapture = capture
              }
            )
          }, ContextCompat.getMainExecutor(ctx))

          view
        },
        update = { view ->
          previewView = view
        }
      )

      // Top Control Bar (Close, Flash, Lens Facing)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Close Button
        IconButton(
          onClick = onClose,
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(0x66000000))
            .border(1.dp, GlassBorderStroke, CircleShape)
            .testTag("camera_close_button")
        ) {
          Icon(Icons.Default.Close, contentDescription = "Close Camera", tint = Color.White)
        }

        // Center Flash Mode Toggle
        IconButton(
          onClick = {
            flashMode = when (flashMode) {
              ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
              ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
              else -> ImageCapture.FLASH_MODE_OFF
            }
            imageCapture?.flashMode = flashMode
          },
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(0x66000000))
            .border(1.dp, GlassBorderStroke, CircleShape)
            .testTag("camera_flash_button")
        ) {
          val flashIcon = when (flashMode) {
            ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
            ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
            else -> Icons.Default.FlashOff
          }
          val flashTint = if (flashMode != ImageCapture.FLASH_MODE_OFF) CyanAccent else Color.White
          Icon(flashIcon, contentDescription = "Toggle Flash", tint = flashTint)
        }

        // Switch Camera (Front / Back)
        IconButton(
          onClick = {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
              CameraSelector.LENS_FACING_FRONT
            } else {
              CameraSelector.LENS_FACING_BACK
            }
            previewView?.let { pv ->
              val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
              cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(
                  cameraProvider = cameraProvider,
                  previewView = pv,
                  lensFacing = lensFacing,
                  flashMode = flashMode,
                  lifecycleOwner = lifecycleOwner,
                  onImageCaptureReady = { capture ->
                    imageCapture = capture
                  }
                )
              }, ContextCompat.getMainExecutor(context))
            }
          },
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(0x66000000))
            .border(1.dp, GlassBorderStroke, CircleShape)
            .testTag("camera_switch_lens_button")
        ) {
          Icon(
            Icons.Default.Cameraswitch,
            contentDescription = "Switch Camera",
            tint = Color.White
          )
        }
      }

      // Viewfinder Framing Guides
      Box(
        modifier = Modifier
          .size(280.dp)
          .align(Alignment.Center)
          .border(1.dp, CyanAccent.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
      )

      // Bottom Shutter Bar
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .align(Alignment.BottomCenter)
          .navigationBarsPadding()
          .padding(bottom = 36.dp),
        contentAlignment = Alignment.Center
      ) {
        // Glowing Shutter Button
        Box(
          modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(CyanAccent.copy(alpha = 0.25f))
            .border(2.dp, CyanAccent, CircleShape)
            .clickable(enabled = !isCapturing && imageCapture != null) {
              val capture = imageCapture ?: return@clickable
              isCapturing = true

              val photoFile = File(
                context.cacheDir,
                "liquid_capture_${System.currentTimeMillis()}.jpg"
              )
              val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
              val mainExecutor: Executor = ContextCompat.getMainExecutor(context)

              capture.takePicture(
                outputOptions,
                mainExecutor,
                object : ImageCapture.OnImageSavedCallback {
                  override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    isCapturing = false
                    capturedPhotoUri = Uri.fromFile(photoFile)
                  }

                  override fun onError(exception: ImageCaptureException) {
                    isCapturing = false
                    Log.e("CameraScreen", "Photo capture failed: ${exception.message}", exception)
                  }
                }
              )
            }
            .testTag("camera_shutter_button"),
          contentAlignment = Alignment.Center
        ) {
          if (isCapturing) {
            CircularProgressIndicator(
              color = CyanAccent,
              modifier = Modifier.size(36.dp),
              strokeWidth = 3.dp
            )
          } else {
            Box(
              modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(Color.White)
            )
          }
        }
      }
    }
  }
}

private fun bindCameraUseCases(
  cameraProvider: ProcessCameraProvider,
  previewView: PreviewView,
  lensFacing: Int,
  flashMode: Int,
  lifecycleOwner: androidx.lifecycle.LifecycleOwner,
  onImageCaptureReady: (ImageCapture) -> Unit
) {
  try {
    cameraProvider.unbindAll()

    val cameraSelector = CameraSelector.Builder()
      .requireLensFacing(lensFacing)
      .build()

    val preview = Preview.Builder().build().also {
      it.setSurfaceProvider(previewView.surfaceProvider)
    }

    val imageCapture = ImageCapture.Builder()
      .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
      .setFlashMode(flashMode)
      .build()

    cameraProvider.bindToLifecycle(
      lifecycleOwner,
      cameraSelector,
      preview,
      imageCapture
    )

    onImageCaptureReady(imageCapture)
  } catch (e: Exception) {
    Log.e("CameraScreen", "Use case binding failed", e)
  }
}
