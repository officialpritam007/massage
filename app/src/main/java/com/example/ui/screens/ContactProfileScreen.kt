package com.example.ui.screens
import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ui.components.*
import com.example.ui.viewmodel.LiquidChatViewModel
import com.example.data.model.*
@Composable
fun ContactProfileScreen(userId:String,viewModel:LiquidChatViewModel,onBackClick:()->Unit,onNavigateToConversation:(String)->Unit,modifier:Modifier=Modifier){
 val users by viewModel.users.collectAsState();val me by viewModel.currentUser.collectAsState();val cs by viewModel.conversations.collectAsState();val messages by viewModel.messages.collectAsState();val blocked by viewModel.blockedUserIds.collectAsState();val u=if(userId==me.uid)me else users.find{it.uid==userId}?:User(uid=userId,displayName="Contact");val context=LocalContext.current
 var report by remember{mutableStateOf(false)};var reason by remember{mutableStateOf("")};var media by remember{mutableStateOf<Message?>(null)}
 val conversation=cs.find{u.uid in it.participantIds};val shared=conversation?.let{messages[it.id]}.orEmpty().filter{it.mediaUrl.isNotBlank()&&!it.isDeleted}
 LiquidBackground(modifier){Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
  GlassHeader("Profile",onBackClick=onBackClick)
  GlassCard(Modifier.fillMaxWidth()){Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){
   GlassAvatar(u.photoUrl,u.displayName,96.dp,u.isOnline&&u.onlineVisible);Text(u.displayName,style=MaterialTheme.typography.headlineSmall);Text("@${u.username}",color=MaterialTheme.colorScheme.primary);Text(u.bio)
   if(u.uid!=me.uid){Text(presenceLabel(u),color=MaterialTheme.colorScheme.onSurfaceVariant);GlassButton("Message",{onNavigateToConversation(viewModel.getOrCreateConversationId(u.uid))})}
   TextButton(onClick={context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,"Find ${u.displayName} on Liquid Chat: @${u.username}")},"Share contact"))}){Text("Share contact")}
  }}
  Text("Shared media",style=MaterialTheme.typography.titleMedium)
  if(shared.isEmpty())Text("No shared media yet",color=MaterialTheme.colorScheme.onSurfaceVariant)
  LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){items(shared){m->GlassCard(Modifier.size(100.dp),onClick={media=m}){if(m.type==MessageType.IMAGE)PrivateImage(m.mediaUrl,"Shared photo",Modifier.fillMaxSize())else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text(m.type.name)}}}}
  if(u.uid!=me.uid){
   conversation?.let{c->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Mute notifications",Modifier.weight(1f));Switch(c.isMuted,{viewModel.setConversationMuted(c.id,it)})}}
   GlassButton(if(u.uid in blocked)"Unblock contact"else "Block contact",{if(u.uid in blocked)viewModel.unblockUser(u.uid)else viewModel.blockUser(u.uid)},isPrimary=false,modifier=Modifier.fillMaxWidth())
   TextButton(onClick={report=true}){Text("Report contact",color=MaterialTheme.colorScheme.error)}
  }
 }
 if(report)GlassDialog("Report contact",{report=false}){GlassTextField(reason,{reason=it},placeholder="Describe the problem",singleLine=false,maxLines=5);GlassButton("Submit report",{viewModel.repository.report(u.uid,reason);report=false},enabled=reason.trim().length>=4)}
 media?.let{MediaViewer(it){media=null}}
 }
}
