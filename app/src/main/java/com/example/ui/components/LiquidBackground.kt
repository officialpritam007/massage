package com.example.ui.components

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.example.ui.theme.LocalLiquidGlass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

val LocalGlassBackdrop = staticCompositionLocalOf<HazeState?> { null }

private const val LIQUID_MESH_SHADER = """
uniform float2 iResolution;
uniform float iTime;
uniform float iDark;

float blob(float2 uv, float2 center, float radius) {
  float d = distance(uv, center);
  return exp(-(d * d) / (radius * radius));
}

half4 main(float2 fragCoord) {
  float2 safeResolution = max(iResolution, float2(1.0));
  float2 uv = fragCoord / safeResolution;
  float phase = iTime * 6.2831853;

  float g = blob(uv, float2(0.13 + 0.045 * sin(phase * 0.72), 0.09 + 0.035 * cos(phase * 0.55)), 0.43);
  float b = blob(uv, float2(0.88 + 0.035 * cos(phase * 0.51), 0.12 + 0.040 * sin(phase * 0.61)), 0.45);
  float a = blob(uv, float2(0.31 + 0.040 * cos(phase * 0.43), 0.91 + 0.030 * sin(phase * 0.37)), 0.48);
  float v = blob(uv, float2(0.91 + 0.020 * sin(phase * 0.40), 0.66 + 0.045 * cos(phase * 0.49)), 0.36);
  float r = blob(uv, float2(0.82 + 0.018 * cos(phase * 0.66), 0.42 + 0.025 * sin(phase * 0.58)), 0.26);

  float dark = clamp(iDark, 0.0, 1.0);
  float3 lightBase = float3(0.94, 0.975, 0.975);
  float3 darkBase = float3(0.008, 0.020, 0.030);
  float3 color = mix(lightBase, darkBase, dark);

  color += float3(0.00, 0.84, 0.55) * g * mix(0.19, 0.30, dark);
  color += float3(0.00, 0.42, 1.00) * b * mix(0.12, 0.22, dark);
  color += float3(0.00, 0.68, 0.72) * a * mix(0.10, 0.19, dark);
  color += float3(0.60, 0.18, 0.98) * v * mix(0.07, 0.15, dark);
  color += float3(1.00, 0.16, 0.36) * r * mix(0.035, 0.09, dark);

  float vignette = smoothstep(0.86, 0.18, distance(uv, float2(0.5, 0.48)));
  color *= mix(0.92, 1.04, vignette);
  return half4(color, 1.0);
}
"""

@Composable
fun LiquidBackground(
  modifier: Modifier = Modifier,
  crystal: Boolean = false,
  content: @Composable () -> Unit
) {
  val config = LocalLiquidGlass.current
  val dark = config.isDark
  val state = remember { HazeState() }
  val motion = rememberInfiniteTransition(label = "liquid_background")
  val phase by motion.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Restart),
    label = "mesh_phase"
  )
  val driftX by motion.animateFloat(
    initialValue = -0.045f,
    targetValue = 0.05f,
    animationSpec = infiniteRepeatable(tween(12_000), RepeatMode.Reverse),
    label = "glass_drift_x"
  )
  val driftY by motion.animateFloat(
    initialValue = 0.035f,
    targetValue = -0.04f,
    animationSpec = infiniteRepeatable(tween(14_000), RepeatMode.Reverse),
    label = "glass_drift_y"
  )

  val runtimeShader = remember {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      runCatching { RuntimeShader(LIQUID_MESH_SHADER) }.getOrNull()
    } else {
      null
    }
  }
  val runtimePaint = remember(runtimeShader) {
    runtimeShader?.let { shader -> Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader } }
  }

  val base = if (dark) Color(0xFF020508) else Color(0xFFF0F7F7)
  CompositionLocalProvider(LocalGlassBackdrop provides if (config.isGlassEnabled) state else null) {
    Box(modifier.fillMaxSize().background(base)) {
      Canvas(
        Modifier
          .fillMaxSize()
          .then(if (config.isGlassEnabled) Modifier.hazeSource(state) else Modifier)
      ) {
        val dx = if (config.isReducedMotion) 0f else driftX
        val dy = if (config.isReducedMotion) 0f else driftY
        val shader = runtimeShader
        val paint = runtimePaint

        if (
          Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
          shader != null &&
          paint != null
        ) {
          shader.setFloatUniform("iResolution", size.width, size.height)
          shader.setFloatUniform("iTime", if (config.isReducedMotion) 0.20f else phase)
          shader.setFloatUniform("iDark", if (dark) 1f else 0f)
          drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
          }
        } else {
          // Android 12 and below keep the same liquid composition without AGSL.
          val greenTop = Offset(size.width * (.12f + dx), size.height * (.06f + dy))
          drawCircle(
            brush = Brush.radialGradient(
              listOf(
                Color(0xFF00D58D).copy(alpha = if (dark) .34f else .24f),
                Color(0xFF00A878).copy(alpha = if (dark) .13f else .09f),
                Color.Transparent
              ),
              center = greenTop,
              radius = size.width * .74f
            ),
            radius = size.width * .74f,
            center = greenTop
          )

          val blueTop = Offset(size.width * (.86f + dx), size.height * (.08f + dy))
          drawCircle(
            brush = Brush.radialGradient(
              listOf(
                Color(0xFF006DFF).copy(alpha = if (dark) .23f else .15f),
                Color(0xFF2E98FF).copy(alpha = if (dark) .08f else .05f),
                Color.Transparent
              ),
              center = blueTop,
              radius = size.width * .82f
            ),
            radius = size.width * .82f,
            center = blueTop
          )

          val aquaBottom = Offset(size.width * (.32f - dx * .7f), size.height * (.94f + dy * .28f))
          drawCircle(
            brush = Brush.radialGradient(
              listOf(
                Color(0xFF00DFA0).copy(alpha = if (dark) .24f else .15f),
                Color(0xFF00A3CB).copy(alpha = if (dark) .09f else .05f),
                Color.Transparent
              ),
              center = aquaBottom,
              radius = size.width * .80f
            ),
            radius = size.width * .80f,
            center = aquaBottom
          )

          val violetRight = Offset(size.width * (.92f - dx * .3f), size.height * (.68f - dy * .4f))
          drawCircle(
            brush = Brush.radialGradient(
              listOf(
                Color(0xFFAA36FF).copy(alpha = if (dark) .18f else .10f),
                Color(0xFF3C78FF).copy(alpha = if (dark) .10f else .05f),
                Color.Transparent
              ),
              center = violetRight,
              radius = size.width * .60f
            ),
            radius = size.width * .60f,
            center = violetRight
          )

          val redRight = Offset(size.width * (.84f + dx * .4f), size.height * (.43f + dy * .4f))
          drawCircle(
            brush = Brush.radialGradient(
              listOf(Color(0xFFFF316F).copy(alpha = if (dark) .12f else .06f), Color.Transparent),
              center = redRight,
              radius = size.width * .40f
            ),
            radius = size.width * .40f,
            center = redRight
          )
        }

        if (crystal) {
          val tint = if (dark) Color(0xFF74A8B3) else Color(0xFFB7D5D7)
          for (i in 0..5) {
            val x = size.width * (i % 3) / 2f
            val y = size.height * (i + 1) / 8f
            val path = Path().apply {
              moveTo(x, y)
              lineTo(size.width * (1f - (i % 2) * .22f), y + size.height * .25f)
              lineTo(size.width * .50f, size.height * .50f)
              close()
            }
            drawPath(
              path,
              Brush.linearGradient(
                listOf(
                  tint.copy(alpha = if (dark) .055f else .08f),
                  Color.Transparent,
                  Color.White.copy(alpha = if (dark) .018f else .035f)
                ),
                start = Offset(x, y),
                end = Offset(size.width / 2f, size.height / 2f)
              )
            )
          }
        }

        drawRect(
          Brush.verticalGradient(
            listOf(
              if (dark) Color.Black.copy(alpha = .10f) else Color.White.copy(alpha = .04f),
              Color.Transparent,
              if (dark) Color.Black.copy(alpha = .24f) else Color(0xFF6E989B).copy(alpha = .08f)
            )
          )
        )
      }
      content()
    }
  }
}
