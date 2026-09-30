package com.example.ui.components
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
@Composable
fun GlassDialog(title:String,onDismiss:()->Unit,content:@Composable ColumnScope.()->Unit){
 Dialog(onDismissRequest=onDismiss){GlassCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(22.dp).heightIn(max=560.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){Text(title,style=MaterialTheme.typography.titleLarge);content();TextButton(onClick=onDismiss){Text("Close")}}}}
}
