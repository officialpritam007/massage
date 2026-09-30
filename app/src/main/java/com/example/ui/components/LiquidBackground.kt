package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import com.example.ui.theme.LocalLiquidGlass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

val LocalGlassBackdrop = staticCompositionLocalOf<HazeState?> { null }
@Composable
fun LiquidBackground(modifier:Modifier=Modifier, crystal:Boolean=false, content:@Composable()->Unit) {
  val dark=LocalLiquidGlass.current.isDark
  val state=remember{HazeState()}
  CompositionLocalProvider(LocalGlassBackdrop provides state) {
    Box(modifier.fillMaxSize().background(if(dark)Color(0xFF131722)else Color(0xFFF9FAFD))) {
      Canvas(Modifier.fillMaxSize().hazeSource(state)) {
        if(crystal) {
          val tint=if(dark)Color(0xFF496285)else Color(0xFFB7D6F4)
          for(i in 0..8) {
            val x=size.width*(i%4)/3f;val y=size.height*i/9f
            val p=Path().apply{moveTo(x,y);lineTo(size.width*(1f-i%3/3f),y+size.height*.36f);lineTo(size.width*.48f,size.height*.5f);close()}
            drawPath(p,Brush.linearGradient(listOf(tint.copy(alpha=.27f),Color.Transparent,Color.White.copy(alpha=.12f)),Offset(x,y),Offset(size.width/2,size.height/2)))
          }
        }
        drawCircle(Brush.radialGradient(listOf(Color(0xFFB2CFFF).copy(alpha=if(dark).09f else .16f),Color.Transparent),center=Offset(size.width*.85f,size.height*.2f),radius=size.width),radius=size.width,center=Offset(size.width*.85f,size.height*.2f))
      }
      content()
    }
  }
}
