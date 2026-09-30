package com.example.ui.screens
import android.content.Intent
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.data.network.LiquidApi
@Composable
fun MediaViewer(message:Message,onClose:()->Unit){
 var url by remember{mutableStateOf<String?>(null)};var error by remember{mutableStateOf<String?>(null)};var scale by remember{mutableFloatStateOf(1f)};var offset by remember{mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)};var video by remember{mutableStateOf<VideoView?>(null)};val context=LocalContext.current
 LaunchedEffect(message.mediaUrl){runCatching{LiquidApi.resolve(message.mediaUrl)}.onSuccess{url=it}.onFailure{error=it.message}}
 DisposableEffect(Unit){onDispose{video?.stopPlayback()}}
 Dialog(onClose,DialogProperties(usePlatformDefaultWidth=false)){
  Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()){
   TextButton(onClick=onClose){Text("Close",color=Color.White)}
   Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){
    if(error!=null)Text(error.orEmpty(),color=Color.White)
    else if(url==null)CircularProgressIndicator()
    else when(message.type){
     MessageType.IMAGE->{val state=rememberTransformableState{zoom,pan,_->scale=(scale*zoom).coerceIn(1f,5f);offset=if(scale==1f)androidx.compose.ui.geometry.Offset.Zero else offset+pan};AsyncImage(url,"Photo",Modifier.fillMaxSize().transformable(state).graphicsLayer{scaleX=scale;scaleY=scale;translationX=offset.x;translationY=offset.y},contentScale=ContentScale.Fit)}
     MessageType.VIDEO->AndroidView(factory={ctx->VideoView(ctx).apply{video=this;setVideoURI(Uri.parse(url));setMediaController(MediaController(ctx).also{it.setAnchorView(this)});setOnPreparedListener{start()};setOnErrorListener{_,_,_->error="Video playback failed";true}}},modifier=Modifier.fillMaxSize())
     MessageType.VOICE->com.example.ui.components.VoiceWaveformPlayer(message.voiceDurationSeconds,message.mediaUrl,waveform=message.waveform)
     else->Button(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}.onFailure{error="No app available to open this attachment"}}){Text("Open document")}
    }
   }
  }
 }
}
