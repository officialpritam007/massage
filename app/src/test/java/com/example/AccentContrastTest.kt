package com.example

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.ui.theme.resolveLiquidAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentContrastTest {
  @Test fun `accent labels remain readable after changing appearance`() {
    val palettes = listOf(0xFF78C7FF, 0xFF00A38D, 0xFF007AFF, 0xFFAF52DE, 0xFFD98320)
    for (surface in listOf(Color(0xFF132036), Color(0xFFFAFCFF))) {
      for (palette in palettes) {
        val accent = resolveLiquidAccent(Color(palette), surface)
        val ratio = (maxOf(accent.luminance(), surface.luminance()) + .05f) /
          (minOf(accent.luminance(), surface.luminance()) + .05f)
        assertTrue("Palette $palette has contrast $ratio", ratio >= 4.5f)
      }
    }
  }

  @Test fun `an already readable accent preserves the selected color`() {
    val candidate = Color(0xFF78C7FF)
    assertEquals(candidate, resolveLiquidAccent(candidate, Color(0xFF132036)))
  }
}
