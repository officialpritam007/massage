package com.example.ui.components

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.data.network.LiquidApi

@Composable
fun PrivateImage(model:String?, contentDescription:String?, modifier:Modifier=Modifier, contentScale:ContentScale=ContentScale.Crop) {
  var resolved by remember(model){mutableStateOf<String?>(null)}
  var failed by remember(model){mutableStateOf(false)}
  LaunchedEffect(model){if(!model.isNullOrBlank())runCatching{LiquidApi.resolve(model)}.onSuccess{resolved=it}.onFailure{failed=true}}
  if(failed) Box(modifier){Text("Unavailable",style=MaterialTheme.typography.labelSmall)}
  else AsyncImage(model=resolved,contentDescription=contentDescription,modifier=modifier,contentScale=contentScale)
}
