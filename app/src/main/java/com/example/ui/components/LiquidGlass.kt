package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.AzureBlue
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.GlassBorderStroke
import com.example.ui.theme.GlassHighlight
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.theme.MidnightDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun GlassCard(
  modifier: Modifier = Modifier,
  shape: Shape = RoundedCornerShape(24.dp),
  backgroundColor: Color? = null,
  borderColor: Color? = null,
  elevation: Dp = 3.dp,
  onClick: (() -> Unit)? = null,
  content: @Composable () -> Unit
) {
  val glassConfig = LocalLiquidGlass.current
  val defaultBg = backgroundColor ?: Color(0xFF13213A).copy(
    alpha = (0.34f + (glassConfig.glassIntensity * 0.22f)).coerceIn(0.20f, 0.72f)
  )
  val borderBrush = Brush.linearGradient(
    colors = listOf(
      borderColor ?: GlassHighlight.copy(alpha = 0.35f * glassConfig.glassIntensity),
      borderColor ?: GlassBorderStroke.copy(alpha = 0.15f * glassConfig.glassIntensity),
      borderColor ?: Color(0x05FFFFFF)
    )
  )

  val cardModifier = modifier
    .shadow(
      elevation = elevation,
      shape = shape,
      ambientColor = Color.Black.copy(alpha = 0.24f),
      spotColor = CyanAccent.copy(alpha = 0.08f)
    )
    .clip(shape)
    .background(defaultBg)
    .border(
      border = BorderStroke(1.dp, borderBrush),
      shape = shape
    )
    .then(
      if (onClick != null) {
        Modifier.clickable(onClick = onClick)
      } else {
        Modifier
      }
    )

  Box(modifier = cardModifier) {
    content()
  }
}

@Composable
fun GlassButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  isPrimary: Boolean = true,
  isLoading: Boolean = false,
  enabled: Boolean = true,
  shape: Shape = RoundedCornerShape(20.dp),
  testTag: String = "glass_button"
) {
  val glassConfig = LocalLiquidGlass.current
  val primaryGradient = Brush.horizontalGradient(
    listOf(glassConfig.accentColor, ElectricBlue)
  )
  val secondaryGradient = Brush.horizontalGradient(
    listOf(Color(0x33FFFFFF), Color(0x1AFFFFFF))
  )

  val backgroundBrush = if (isPrimary) primaryGradient else secondaryGradient
  val contentColor = if (isPrimary) Color.Black else TextPrimary

  Box(
    modifier = modifier
      .defaultMinSize(minHeight = 50.dp)
      .clip(shape)
      .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
          listOf(
            if (isPrimary) Color.White.copy(alpha = 0.6f) else GlassHighlight,
            Color.Transparent
          )
        ),
        shape = shape
      )
      .background(if (enabled) backgroundBrush else SolidColor(Color.Gray.copy(alpha = 0.3f)))
      .clickable(
        enabled = enabled && !isLoading,
        onClick = onClick
      )
      .padding(horizontal = 24.dp, vertical = 14.dp)
      .testTag(testTag),
    contentAlignment = Alignment.Center
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = contentColor,
          modifier = Modifier
            .size(20.dp)
            .padding(end = 8.dp)
        )
      }
      Text(
        text = if (isLoading) "Processing..." else text,
        style = MaterialTheme.typography.labelLarge.copy(
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp,
          color = contentColor
        )
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
  backgroundColor: Color = Color(0x24FFFFFF),
  size: Dp = 48.dp,
  testTag: String = "glass_icon_button"
) {
  Box(
    modifier = modifier
      .size(size)
      .clip(CircleShape)
      .background(backgroundColor)
      .border(1.dp, GlassBorderStroke, CircleShape)
      .clickable(onClick = onClick)
      .testTag(testTag),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = tint,
      modifier = Modifier.size(22.dp)
    )
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
  shape: Shape = RoundedCornerShape(20.dp),
  testTag: String = "glass_text_field"
) {
  val glassConfig = LocalLiquidGlass.current

  Box(
    modifier = modifier
      .defaultMinSize(minHeight = 52.dp)
      .clip(shape)
      .background(Color(0xFF0F172A).copy(alpha = 0.75f))
      .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
          listOf(
            GlassHighlight.copy(alpha = 0.35f * glassConfig.glassIntensity),
            GlassBorderStroke.copy(alpha = 0.15f)
          )
        ),
        shape = shape
      )
      .padding(horizontal = 16.dp, vertical = 12.dp)
      .testTag(testTag),
    contentAlignment = Alignment.CenterStart
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (leadingIcon != null) {
        leadingIcon()
        Box(modifier = Modifier.size(10.dp))
      }

      Box(modifier = Modifier.weight(1f)) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
          Text(
            text = placeholder,
            style = MaterialTheme.typography.bodyMedium.copy(
              color = TextMuted,
              fontSize = 15.sp
            )
          )
        }
        BasicTextField(
          value = value,
          onValueChange = onValueChange,
          modifier = Modifier.fillMaxWidth(),
          textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = TextPrimary,
            fontSize = 15.sp
          ),
          visualTransformation = visualTransformation,
          keyboardOptions = keyboardOptions,
          keyboardActions = keyboardActions,
          singleLine = singleLine,
          maxLines = maxLines,
          cursorBrush = SolidColor(glassConfig.accentColor)
        )
      }

      if (trailingIcon != null) {
        Box(modifier = Modifier.size(8.dp))
        trailingIcon()
      }
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
  val glassConfig = LocalLiquidGlass.current

  val avatarModifier = modifier
    .size(size)
    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)

  Box(modifier = avatarModifier) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .clip(CircleShape)
        .border(1.5.dp, Brush.linearGradient(listOf(glassConfig.accentColor, AzureBlue)), CircleShape)
        .background(
          Brush.linearGradient(
            listOf(Color(0xFF1E293B), Color(0xFF0F172A))
          )
        ),
      contentAlignment = Alignment.Center
    ) {
      if (!photoUrl.isNullOrBlank()) {
        AsyncImage(
          model = photoUrl,
          contentDescription = name,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize()
        )
      } else {
        // Initials fallback
        val initials = name.split(" ")
          .take(2)
          .mapNotNull { it.firstOrNull()?.uppercaseChar() }
          .joinToString("")
          .ifEmpty { "LC" }

        Text(
          text = initials,
          style = MaterialTheme.typography.titleMedium.copy(
            color = glassConfig.accentColor,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.38f).sp
          )
        )
      }
    }

    if (isOnline) {
      Box(
        modifier = Modifier
          .size(size * 0.28f)
          .align(Alignment.BottomEnd)
          .clip(CircleShape)
          .border(2.dp, MidnightDark, CircleShape)
          .background(EmeraldOnline)
      )
    }
  }
}

@Composable
fun GlassBadge(
  count: Int,
  modifier: Modifier = Modifier,
  color: Color = CyanAccent
) {
  if (count <= 0) return

  Box(
    modifier = modifier
      .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
      .clip(RoundedCornerShape(10.dp))
      .background(color)
      .padding(horizontal = 6.dp, vertical = 2.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = if (count > 99) "99+" else count.toString(),
      style = TextStyle(
        color = Color.Black,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        textAlign = TextAlign.Center
      )
    )
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
  GlassCard(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
    elevation = 6.dp
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (onBackClick != null) {
        IconButton(
          onClick = onBackClick,
          modifier = Modifier.size(44.dp).testTag("header_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = TextPrimary
          )
        }
      }

      Box(
        modifier = Modifier
          .weight(1f)
          .padding(horizontal = 8.dp)
      ) {
        androidx.compose.foundation.layout.Column {
          Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
              fontWeight = FontWeight.Bold,
              color = TextPrimary
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          if (!subtitle.isNullOrBlank()) {
            Text(
              text = subtitle,
              style = MaterialTheme.typography.bodySmall.copy(
                color = TextSecondary,
                fontSize = 12.sp
              ),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }

      if (actions != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          actions()
        }
      }
    }
  }
}
