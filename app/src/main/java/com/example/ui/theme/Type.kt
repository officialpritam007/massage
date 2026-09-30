package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val SystemSans = FontFamily.Default

val Typography = Typography(
  displaySmall = TextStyle(
    fontFamily = SystemSans,
    fontWeight = FontWeight.Bold,
    fontSize = 36.sp,
    lineHeight = 41.sp,
    letterSpacing = (-0.7).sp
  ),
  headlineLarge = TextStyle(
    fontFamily = SystemSans,
    fontWeight = FontWeight.Bold,
    fontSize = 32.sp,
    lineHeight = 38.sp,
    letterSpacing = (-0.55).sp
  ),
  headlineMedium = TextStyle(
    fontFamily = SystemSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 27.sp,
    lineHeight = 33.sp,
    letterSpacing = (-0.35).sp
  ),
  headlineSmall = TextStyle(
    fontFamily = SystemSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 23.sp,
    lineHeight = 29.sp,
    letterSpacing = (-0.2).sp
  ),
  titleLarge = TextStyle(
    fontFamily = SystemSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 21.sp,
    lineHeight = 27.sp,
    letterSpacing = (-0.2).sp
  ),
  titleMedium = TextStyle(
    fontFamily = SystemSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 17.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.sp
  ),
  titleSmall = TextStyle(
    fontFamily = SystemSans,
    fontWeight = FontWeight.Medium,
    fontSize = 15.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp
  ),
  bodyLarge = TextStyle(
    fontFamily = SystemSans,
    fontWeight = FontWeight.Normal,
    fontSize = 17.sp,
    lineHeight = 23.sp,
    letterSpacing = 0.sp
  ),
  bodyMedium = TextStyle(
    fontFamily = SystemSans,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 21.sp,
    letterSpacing = 0.05.sp
  ),
  bodySmall = TextStyle(
    fontFamily = SystemSans,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.1.sp
  ),
  labelLarge = TextStyle(
    fontFamily = SystemSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 15.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.05.sp
  ),
  labelMedium = TextStyle(
    fontFamily = SystemSans,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    lineHeight = 17.sp,
    letterSpacing = 0.1.sp
  ),
  labelSmall = TextStyle(
    fontFamily = SystemSans,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 15.sp,
    letterSpacing = 0.15.sp
  )
)
