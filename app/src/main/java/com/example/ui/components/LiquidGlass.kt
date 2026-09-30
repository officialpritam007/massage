package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import dev.chrisbanes.haze.hazeEffect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AzureBlue
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.GlassBorderStrokeDark
import com.example.ui.theme.GlassBorderStrokeLight
import com.example.ui.theme.GlassHighlight
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextPrimaryLight
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextSecondaryLight

/**
 * iOS 26 Liquid Glass surface.
 *
 * Two materials, shared behaviour:
 * - Clear   : barely-there tint, thin blur, strong specular lens edge — content behind stays readable through it.
 * - Regular : frosted milk tint, heavier blur, softer edge — best for dense UI chrome.
 *
 * Every card gets a lens edge (specular rim), an optional wet highlight and press physics.
 */
@Composable
fun GlassCard(
  modifier: Modifier = Modifier,
  shape: Shape? = null,
  backgroundColor: Color? = null,
  overlayBrush: Brush? = null,
  borderColor: Color? = null,
  elevation: Dp = 3.dp,
  lensing: Boolean = true,
  onClick: (() -> Unit)? = null,
  content: @Composable () -> Unit
) {
  val config = LocalLiquidGlass.current
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (pressed && onClick != null && !config.isReducedMotion) 0.97f else 1f,
    animationSpec = spring(dampingRatio = 0.7f, stiffness = 450f),
    label = "glass_card_press"
  )

  val clear = config.isClear
  val surface = when {
    backgroundColor != null -> backgroundColor
    clear && config.isDark -> Color(0xFF0E1B29).copy(alpha = 0.20f + config.glassIntensity * 0.10f)
    clear -> Color.White.copy(alpha = 0.16f + config.glassIntensity * 0.10f)
    config.isDark -> Color(0xFFB7D9FF).copy(alpha = (0.035f + config.blurAlpha * 0.085f + config.glassIntensity * 0.025f).coerceIn(0.06f, 0.18f))
    else -> Color.White.copy(alpha = (0.36f + config.blurAlpha * 0.14f + config.glassIntensity * 0.06f).coerceIn(0.38f, 0.64f))
  }
  val glassBackground = backgroundColor ?: surface
  val backdrop = LocalGlassBackdrop.current
  val border = borderColor
    ?: if (config.isDark) GlassBorderStrokeDark else Color.White.copy(alpha = if (clear) 0.62f else 0.38f)
  val resolvedShape: Shape = shape ?: RoundedCornerShape(config.cornerRadiusDp.coerceIn(16f, 32f).dp)
  val topHighlight = if (config.isDark) GlassHighlight.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.95f)
  val shadow = if (config.isDark) Color.Black.copy(alpha = 0.42f) else Color.Black.copy(alpha = 0.10f)
  // Clear style keeps a thin blur so content behind stays legible; Regular gets the full frost.
  val blurDp = when {
    !config.isGlassEnabled -> 0f
    clear -> (4f + config.glassIntensity * 8f) * (0.45f + config.blurAlpha * 0.55f)
    else -> 12f + config.blurAlpha * 20f
  }
  val noise = if (clear) 0f else 0.025f

  Box(
    modifier = modifier
      .then(Modifier.graphicsLayerCompat(scale))
      .shadow(elevation, resolvedShape, ambientColor = shadow, spotColor = shadow)
      .clip(resolvedShape)
      .then(
        if (backdrop != null && config.isGlassEnabled) {
          Modifier.hazeEffect(backdrop) {
            this.blurRadius = blurDp.dp
            noiseFactor = noise
            this.backgroundColor = glassBackground
          }
        } else Modifier
      )
      .background(glassBackground)
      .then(if (overlayBrush != null) Modifier.background(overlayBrush) else Modifier)
      .then(if (lensing) Modifier.lensHighlight(dark = config.isDark, strength = if (clear) 1.15f else 0.85f) else Modifier)
      .border(
        BorderStroke(
          1.dp,
          Brush.verticalGradient(
            listOf(
              topHighlight.copy(alpha = if (clear) topHighlight.alpha.coerceAtLeast(0.75f) else topHighlight.alpha),
              border.copy(alpha = (border.alpha * config.borderStrength).coerceIn(0.06f, 1f))
            )
          )
        ),
        resolvedShape
      )
      .then(if (lensing) Modifier.lensEdge(resolvedShape, dark = config.isDark, strength = if (clear) 1.1f else 0.75f) else Modifier)
      .then(
        if (onClick != null) Modifier.clickable(
          interactionSource = interactionSource,
          indication = null,
          onClick = onClick
        ) else Modifier
      )
  ) {
    CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides androidx.compose.material3.MaterialTheme.colorScheme.onSurface) { content() }
  }
}

// Kept tiny and local so this project does not depend on experimental graphics APIs.
private fun Modifier.graphicsLayerCompat(scale: Float): Modifier =
  this.graphicsLayer { scaleX = scale; scaleY = scale }

@Composable
fun GlassButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  isPrimary: Boolean = true,
  isLoading: Boolean = false,
  enabled: Boolean = true,
  shape: Shape? = null,
  testTag: String = "glass_button"
) {
  val config = LocalLiquidGlass.current
  val haptics = rememberLiquidHaptics()
  val pressedSource = remember { MutableInteractionSource() }
  val pressed by pressedSource.collectIsPressedAsState()
  val accent by animateColorAsState(
    targetValue = if (isPrimary) config.accentColor else if (config.isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.045f),
    animationSpec = tween(180),
    label = "glass_button_color"
  )
  val buttonScale by animateFloatAsState(if (pressed && !config.isReducedMotion) 0.95f else 1f, spring(dampingRatio = .65f, stiffness = 450f), label = "button_spring")
  val contentColor = if (isPrimary) Color.White else if (config.isDark) TextPrimary else TextPrimaryLight
  val resolvedShape = shape ?: RoundedCornerShape(config.cornerRadiusDp.coerceIn(0f, 64f).dp)

  Box(
    modifier = modifier
      .graphicsLayer { scaleX = buttonScale; scaleY = buttonScale }
      .defaultMinSize(minHeight = 50.dp)
      .clip(resolvedShape)
      .background(accent.copy(alpha = if (isPrimary) 0.82f else 1f))
      .lensHighlight(dark = true, strength = 1.3f)
      .liquidSheen(enabled = isPrimary && !config.isReducedMotion, dark = true)
      .border(1.dp, GlassHighlight.copy(alpha = 0.42f), resolvedShape)
      .lensEdge(resolvedShape, dark = true, strength = 1f)
      .clickable(enabled = enabled && !isLoading, interactionSource = pressedSource, indication = null) {
        haptics.tap()
        onClick()
      }
      .padding(horizontal = 22.dp, vertical = 14.dp)
      .testTag(testTag),
    contentAlignment = Alignment.Center
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (icon != null) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp).padding(end = 8.dp))
      }
      Text(
        text = if (isLoading) "Processing..." else text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = contentColor, fontSize = 15.sp)
      )
    }
  }
}

@Composable
fun GlassIconButton(
  icon: ImageVector,
  contentDescription: String?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  tint: Color = TextPrimary,
  backgroundColor: Color? = null,
  size: Dp = 48.dp,
  testTag: String = "glass_icon_button"
) {
  val config = LocalLiquidGlass.current
  val haptics = rememberLiquidHaptics()
  val bg = backgroundColor
    ?: if (config.isDark) {
      Color.White.copy(alpha = if (config.isClear) 0.10f else 0.065f)
    } else {
      Color.White.copy(alpha = if (config.isClear) 0.62f else 0.54f)
    }
  val iconTint = if (tint == TextPrimary && !config.isDark) TextPrimaryLight else tint
  val interactions = remember { MutableInteractionSource() }
  val pressed by interactions.collectIsPressedAsState()
  val pressScale by animateFloatAsState(if (pressed && !config.isReducedMotion) .90f else 1f, spring(dampingRatio = .62f, stiffness = 440f), label = "icon_spring")
  Box(
    modifier = modifier
      .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
      .size(size)
      .clip(CircleShape)
      .background(bg)
      .border(1.dp, if (config.isDark) GlassBorderStrokeDark else GlassBorderStrokeLight, CircleShape)
      .lensEdge(CircleShape, dark = config.isDark, strength = 0.9f)
      .clickable(interactionSource = interactions, indication = null) {
        haptics.tap()
        onClick()
      }
      .testTag(testTag),
    contentAlignment = Alignment.Center
  ) {
    Icon(icon, contentDescription = contentDescription, tint = iconTint, modifier = Modifier.size(22.dp))
  }
}

@Composable
fun GlassTextField(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  placeholder: String = "",
  leadingIcon: (@Composable () -> Unit)? = null,
  trailingIcon: (@Composable () -> Unit)? = null,
  visualTransformation: VisualTransformation = VisualTransformation.None,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = KeyboardActions.Default,
  singleLine: Boolean = true,
  maxLines: Int = 1,
  shape: Shape? = null,
  testTag: String = "glass_text_field"
) {
  val config = LocalLiquidGlass.current
  val primaryText = if (config.isDark) TextPrimary else TextPrimaryLight
  val secondaryText = if (config.isDark) Color(0xFFB7CBE2) else TextSecondaryLight
  val resolvedShape = shape ?: RoundedCornerShape(config.cornerRadiusDp.coerceIn(0f, 64f).dp)
  val fieldBg = if (config.isDark) {
    Color.White.copy(alpha = if (config.isClear) 0.075f else 0.055f)
  } else {
    Color.White.copy(alpha = if (config.isClear) 0.55f else 0.48f)
  }
  Box(
    modifier = modifier
      .defaultMinSize(minHeight = 48.dp)
      .clip(resolvedShape)
      .background(fieldBg)
      .border(1.dp, if (config.isDark) GlassBorderStrokeDark else GlassBorderStrokeLight, resolvedShape)
      .lensEdge(resolvedShape, dark = config.isDark, strength = 0.6f)
      .padding(horizontal = 14.dp, vertical = 10.dp)
      .testTag(testTag),
    contentAlignment = Alignment.CenterStart
  ) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      if (leadingIcon != null) {
        leadingIcon(); Box(modifier = Modifier.size(10.dp))
      }
      Box(modifier = Modifier.weight(1f)) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
          Text(placeholder, style = MaterialTheme.typography.bodyMedium.copy(color = secondaryText, fontSize = 15.sp))
        }
        BasicTextField(
          value = value,
          onValueChange = onValueChange,
          modifier = Modifier.fillMaxWidth(),
          textStyle = MaterialTheme.typography.bodyMedium.copy(color = primaryText, fontSize = 15.sp),
          visualTransformation = visualTransformation,
          keyboardOptions = keyboardOptions,
          keyboardActions = keyboardActions,
          singleLine = singleLine,
          maxLines = maxLines,
          cursorBrush = SolidColor(config.accentColor)
        )
      }
      if (trailingIcon != null) { Box(modifier = Modifier.size(8.dp)); trailingIcon() }
    }
  }
}

@Composable
fun GlassAvatar(
  photoUrl: String?,
  name: String,
  size: Dp = 50.dp,
  isOnline: Boolean = false,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null
) {
  val config = LocalLiquidGlass.current
  val borderBrush = Brush.linearGradient(listOf(config.accentColor, AzureBlue))
  Box(modifier = modifier.size(size).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
    Box(
      modifier = Modifier.fillMaxSize().clip(CircleShape).border(1.5.dp, borderBrush, CircleShape)
        .background(if (config.isDark) Color.White.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.75f)),
      contentAlignment = Alignment.Center
    ) {
      if (!photoUrl.isNullOrBlank()) {
        PrivateImage(model = photoUrl, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
      } else {
        val initials = name.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("").ifEmpty { "LC" }
        Text(initials, style = MaterialTheme.typography.titleMedium.copy(color = config.accentColor, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.38f).sp))
      }
    }
    if (isOnline) {
      Box(
        modifier = Modifier.size(size * 0.28f).align(Alignment.BottomEnd).clip(CircleShape)
          .border(2.dp, if (config.isDark) Color.Black else Color.White, CircleShape).background(EmeraldOnline)
      )
    }
  }
}

@Composable
fun GlassBadge(count: Int, modifier: Modifier = Modifier, color: Color = CyanAccent) {
  if (count <= 0) return
  Box(
    modifier = modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp).clip(RoundedCornerShape(10.dp))
      .background(color.copy(alpha = 0.82f)).border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
      .padding(horizontal = 6.dp, vertical = 2.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(if (count > 99) "99+" else count.toString(), style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center))
  }
}

@Composable
fun GlassHeader(
  title: String,
  subtitle: String? = null,
  onBackClick: (() -> Unit)? = null,
  actions: @Composable (RowScope.() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val config = LocalLiquidGlass.current
  GlassCard(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp),
    elevation = 7.dp
  ) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
      if (onBackClick != null) {
        IconButton(onClick = onBackClick, modifier = Modifier.size(44.dp).testTag("header_back_button")) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = if (config.isDark) TextPrimary else TextPrimaryLight)
        }
      }
      Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
        androidx.compose.foundation.layout.Column {
          Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = if (config.isDark) TextPrimary else TextPrimaryLight), maxLines = 1, overflow = TextOverflow.Ellipsis)
          if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = if (config.isDark) TextSecondary else TextSecondaryLight, fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
      if (actions != null) Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
  }
}
