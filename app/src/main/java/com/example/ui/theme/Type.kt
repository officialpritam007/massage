package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// System sans (Roboto / device default) — deliberately not an Apple type copy,
// but tuned to the same compact rhythm: modest weights, tight line heights,
// small negative tracking on headings and near-zero tracking on body copy.
private val SystemSans = FontFamily.Default

val Typography = Typography(
  displaySmall = TextStyle(
    fontFamily = SystemSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 34.sp,
    lineHeight = 39.sp,
    letterSpacing = (-0.6).sp
  ),
  headlineLarge = TextStyle(
    fontFamily = SystemSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 27.sp,
    lineHeight = 33.sp,
    letterSpacing = (-0.4).sp
  ),
  headlineMedium = TextStyle(
    fontFamily = SystemSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 23.sp,
    lineHeight = 29.sp,
    letterSpacing = (-0.3).sp
  ),
  headlineSmall = TextStyle(
    fontFamily = SystemSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 20.sp,
    lineHeight = 26.sp,
    letterSpacing = (-0.15).sp
  ),
  titleLarge = TextStyle(
    fontFamily = SystemSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 19.sp,
    lineHeight = 24.sp,
    letterSpacing = (-0.1).sp
  ),
  titleMedium = TextStyle(
    fontFamily = SystemSans,
    fontWeight = FontWeight.Medium,
    fontSize = 16.sp,
    lineHeight = 21.sp,
    letterSpacing = 0.sp
  ),
  titleSmall = TextStyle(
    fontFamily = SystemSans,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 19.sp,
    letterSpacing = 0.sp
  ),
  bodyLarge = TextStyle(
    fontFamily = SystemSans,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.05.sp
  ),
  bodyMedium = TextStyle(
    fontFamily = SystemSans,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.05.sp
  ),
  bodySmall = TextStyle(
    fontFamily = SystemSans,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 17.5.sp,
    letterSpacing = 0.05.sp
  ),
  labelLarge = TextStyle(
    fontFamily = SystemSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = 19.sp,
    letterSpacing = 0.1.sp
  ),
  labelMedium = TextStyle(
    fontFamily = SystemSans,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.2.sp
  ),
  labelSmall = TextStyle(
    fontFamily = SystemSans,
    fontWeight = FontWeight.Medium,
    fontSize = 10.5.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.25.sp
  )
)

/** Timestamp / delivery metadata inside bubbles — deliberately tiny and quiet. */
val BubbleMetaTextStyle = TextStyle(
  fontFamily = SystemSans,
  fontWeight = FontWeight.Medium,
  fontSize = 10.sp,
  lineHeight = 13.sp,
  letterSpacing = 0.15.sp
)
