package com.example.ui.components
import android.media.MediaPlayer
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.network.LiquidApi
import kotlinx.coroutines.delay
@Composable
fun VoiceWaveformPlayer(durationSeconds:Int,mediaUrl:String="",modifier:Modifier=Modifier,isOutgoing:Boolean=false){
 val player=remember(mediaUrl){MediaPlayer()};var ready by remember(mediaUrl){mutableStateOf(false)};var playing by remember(mediaUrl){mutableStateOf(false)};var pos by remember(mediaUrl){mutableFloatStateOf(0f)};var duration by remember(mediaUrl){mutableIntStateOf(durationSeconds.coerceAtLeast(1)*1000)};var error by remember(mediaUrl){mutableStateOf(false)}
 LaunchedEffect(mediaUrl){runCatching{val url=LiquidApi.resolve(mediaUrl);player.setDataSource(url);player.setOnPreparedListener{duration=it.duration.coerceAtLeast(1);ready=true};player.setOnCompletionListener{playing=false;pos=0f;it.seekTo(0)};player.setOnErrorListener{_,_,_->error=true;playing=false;true};player.prepareAsync()}.onFailure{error=true}}
 LaunchedEffect(playing){while(playing){pos=runCatching{player.currentPosition.toFloat()}.getOrDefault(0f);delay(150)}}
 DisposableEffect(player){onDispose{runCatching{player.release()}}}
 Column(modifier.width(235.dp)){
  Row{IconButton(onClick={if(ready){if(playing)player.pause()else player.start();playing=!playing}},enabled=ready&&!error){Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,if(playing)"Pause"else"Play")};Slider(pos,{pos=it},onValueChangeFinished={if(ready)player.seekTo(pos.toInt())},valueRange=0f..duration.toFloat(),enabled=ready,modifier=Modifier.weight(1f))}
  Text(if(error)"Unable to play this recording" else if(!ready)"Loading audio…"else "${(pos/1000).toInt()}s / ${duration/1000}s",style=MaterialTheme.typography.labelSmall)
 }
}
