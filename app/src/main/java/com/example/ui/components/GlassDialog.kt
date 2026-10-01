package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.LocalLiquidGlass
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextPrimaryLight

/**
 * Centred Liquid Glass dialog — used for destructive confirmations and short forms.
 */
@Composable
fun GlassDialog(
  title: String,
  onDismiss: () -> Unit,
  content: @Composable ColumnScope.() -> Unit
) {
  val config = LocalLiquidGlass.current
  // Panel tint comes from the shared config so the appearance sliders drive it.
  val denseGlass = config.panelSurface

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Box(
      Modifier.fillMaxWidth().padding(horizontal = 18.dp),
      contentAlignment = Alignment.Center
    ) {
      GlassCard(
        modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
        shape = RoundedCornerShape(34.dp),
        backgroundColor = denseGlass,
        borderColor = config.chromeBorder,
        elevation = config.sheetElevation
      ) {
        Column(
          Modifier
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .heightIn(max = 640.dp)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              title,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.weight(1f)
            )
            GlassIconButton(
              icon = Icons.Default.Close,
              contentDescription = "Close",
              onClick = onDismiss,
              size = 42.dp,
              backgroundColor = config.insetSurface
            )
          }
          content()
        }
      }
    }
  }
}

/**
 * Frosted iOS-style bottom sheet. Every menu, picker and settings surface in the app
 * uses this one component so the sheets share a single Liquid Glass language.
 */
@Composable
fun GlassSheet(
  title: String? = null,
  onDismiss: () -> Unit,
  content: @Composable ColumnScope.() -> Unit
) {
  val config = LocalLiquidGlass.current
  val haptics = rememberLiquidHaptics()
  var shown by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { shown = true }

  val sheetBackground = config.panelSurface
  // Taps on the sheet surface must never fall through to the dismissing scrim.
  val swallow = remember { MutableInteractionSource() }
  // Drag the grabber down to dismiss, with a spring snap-back when the pull was short.
  val scope = rememberCoroutineScope()
  val dragY = remember { Animatable(0f) }
  fun settle() {
    scope.launch { dragY.animateTo(0f, spring(dampingRatio = .8f, stiffness = 420f)) }
  }

  Dialog(
    onDismissRequest = { onDismiss() },
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
      Box(
        Modifier
          .fillMaxSize()
          .background(config.scrim)
          .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
            haptics.tap()
            onDismiss()
          }
      )

      AnimatedVisibility(
        visible = shown,
        enter = slideInVertically(
          animationSpec = spring(dampingRatio = .82f, stiffness = 360f),
          initialOffsetY = { it }
        ) + fadeIn(),
        modifier = Modifier.fillMaxWidth()
      ) {
        GlassCard(
          modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, dragY.value.roundToInt()) }
            .padding(horizontal = 8.dp)
            .navigationBarsPadding(),
          shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
          backgroundColor = sheetBackground,
          borderColor = config.chromeBorder,
          elevation = config.sheetElevation
        ) {
          Column(
            Modifier
              .fillMaxWidth()
              .clickable(interactionSource = swallow, indication = null) {}
              .padding(horizontal = 14.dp, vertical = 12.dp)
              .heightIn(max = 620.dp)
              .verticalScroll(rememberScrollState())
          ) {
            Box(
              Modifier
                .fillMaxWidth()
                .height(18.dp)
                .pointerInput(Unit) {
                  detectVerticalDragGestures(
                    onVerticalDrag = { change, amount ->
                      change.consume()
                      scope.launch { dragY.snapTo((dragY.value + amount).coerceAtLeast(0f)) }
                    },
                    onDragEnd = {
                      if (dragY.value > 140f) {
                        haptics.confirm()
                        onDismiss()
                      } else {
                        settle()
                      }
                    },
                    onDragCancel = { settle() }
                  )
                },
              contentAlignment = Alignment.Center
            ) {
              Box(
                Modifier
                  .width(38.dp)
                  .height(4.dp)
                  .clip(RoundedCornerShape(999.dp))
                  .background(config.chromeBorder)
              )
            }
            if (!title.isNullOrBlank()) {
              Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (config.isDark) TextPrimary else TextPrimaryLight,
                modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 6.dp)
              )
            } else {
              Spacer(Modifier.height(8.dp))
            }
            content()
            Spacer(Modifier.height(6.dp))
          }
        }
      }
    }
  }
}

/**
 * One action inside a [GlassSheet] or [GlassDialog]. Destructive actions get the error tint
 * instead of a different component.
 */
@Composable
fun GlassActionRow(
  icon: ImageVector? = null,
  label: String,
  subtitle: String? = null,
  destructive: Boolean = false,
  onClick: () -> Unit
) {
  val config = LocalLiquidGlass.current
  val haptics = rememberLiquidHaptics()
  val tint = when {
    destructive -> MaterialTheme.colorScheme.error
    config.isDark -> TextPrimary
    else -> TextPrimaryLight
  }
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
        haptics.tap()
        onClick()
      }
      .padding(horizontal = 10.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (icon != null) {
      Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
      Spacer(Modifier.width(13.dp))
    }
    Column(Modifier.weight(1f)) {
      Text(
        label,
        style = MaterialTheme.typography.bodyLarge.copy(color = tint, fontSize = 15.sp)
      )
      if (!subtitle.isNullOrBlank()) {
        Text(
          subtitle,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}
