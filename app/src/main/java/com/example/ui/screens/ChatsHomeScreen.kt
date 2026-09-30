package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.example.data.model.Conversation
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.LiquidChatViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatsHomeScreen(viewModel:LiquidChatViewModel,onNavigateToConversation:(String)->Unit,onNavigateToSettings:()->Unit,onNavigateToSearch:()->Unit,onNavigateToAppearance:()->Unit,onNavigateToProfile:(String)->Unit,initialTab:String="All",onHomeTabSelected:(String)->Unit={},modifier:Modifier=Modifier) {
  val conversations by viewModel.conversations.collectAsState()
  val loading by viewModel.loading.collectAsState()
  val current by viewModel.currentUser.collectAsState()
  var tab by rememberSaveable{mutableStateOf(initialTab)}
  var menu by remember{mutableStateOf<Conversation?>(null)}
  var delete by remember{mutableStateOf<Conversation?>(null)}
  LaunchedEffect(initialTab){tab=initialTab}
  val visible=conversations.filter{when(tab){"Favorites"->it.isPinned&&!it.isArchived;"Archived"->it.isArchived;else->!it.isArchived}}
  LiquidBackground(modifier) {
    Scaffold(containerColor=Color.Transparent,bottomBar={GlassBottomBar(if(tab=="Favorites")"favorites" else if(tab=="Archived")"archived" else "chats",{tab="All";onHomeTabSelected(tab)},{tab="Favorites";onHomeTabSelected(tab)},{tab="Archived";onHomeTabSelected(tab)},onNavigateToSettings)}){padding->
      LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(horizontal=20.dp,vertical=12.dp)) {
        item {
          Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            GlassAvatar(current.photoUrl,current.displayName,size=38.dp,onClick={onNavigateToProfile(current.uid)})
            Spacer(Modifier.weight(1f))
            GlassIconButton(Icons.Default.Palette,"Appearance",onNavigateToAppearance)
            Spacer(Modifier.width(8.dp))
            GlassIconButton(Icons.Default.Add,"New message",onNavigateToSearch,tint=Color.White,backgroundColor=Color(0xFF18B96A))
          }
          Text(if(tab=="All")"Chats"else tab,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=20.dp,bottom=14.dp))
          GlassCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(30.dp),onClick=onNavigateToSearch){Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Search,null,tint=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.width(10.dp));Text("Search people and messages",color=MaterialTheme.colorScheme.onSurfaceVariant)}}
          Spacer(Modifier.height(20.dp))
        }
        if(loading)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
        items(visible,key={it.id}){c->
          Row(Modifier.fillMaxWidth().clickable{onNavigateToConversation(c.id)}.padding(vertical=13.dp),verticalAlignment=Alignment.CenterVertically){
            GlassAvatar(c.otherUser.photoUrl,c.otherUser.displayName,isOnline=c.isOnline&&c.otherUser.onlineVisible,size=54.dp,onClick={onNavigateToProfile(c.otherUser.uid)})
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)){
              Row(verticalAlignment=Alignment.CenterVertically){Text(c.otherUser.displayName,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f));Text(if(c.lastMessageTime>0)SimpleDateFormat("h:mm a",Locale.getDefault()).format(Date(c.lastMessageTime)) else "",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
              Spacer(Modifier.height(5.dp))
              Row(verticalAlignment=Alignment.CenterVertically){Text(if(c.isTyping)"Typing…"else c.lastMessageText.ifBlank{"Start a conversation"},maxLines=2,overflow=TextOverflow.Ellipsis,color=if(c.isTyping)EmeraldOnline else MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f));if(c.unreadCount>0)GlassBadge(c.unreadCount,color=EmeraldOnline)}
            }
            IconButton(onClick={menu=c},modifier=Modifier.size(32.dp)){Icon(Icons.Default.MoreVert,"Chat actions",modifier=Modifier.size(19.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)}
          }
          HorizontalDivider(Modifier.padding(start=67.dp),color=MaterialTheme.colorScheme.outline.copy(alpha=.12f))
        }
        if(!loading&&visible.isEmpty())item{Column(Modifier.fillMaxWidth().padding(vertical=64.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.ChatBubbleOutline,null,Modifier.size(40.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(12.dp));Text("No ${if(tab=="All")"chats yet" else tab.lowercase()+" chats"}");TextButton(onClick=onNavigateToSearch){Text("Start a new conversation")}}}
      }
    }
    menu?.let{c->GlassDialog("Chat actions",{menu=null}){
      Text(c.otherUser.displayName,fontWeight=FontWeight.SemiBold)
      TextButton(onClick={viewModel.repository.setFavorite(c.id,!c.isPinned);menu=null}){Text(if(c.isPinned)"Remove from Favorites" else "Add to Favorites")}
      TextButton(onClick={viewModel.setConversationArchived(c.id,!c.isArchived);menu=null}){Text(if(c.isArchived)"Unarchive" else "Archive")}
      TextButton(onClick={viewModel.setConversationMuted(c.id,!c.isMuted);menu=null}){Text(if(c.isMuted)"Unmute" else "Mute")}
      TextButton(onClick={delete=c;menu=null}){Text("Delete chat for me",color=MaterialTheme.colorScheme.error)}
    }}
    delete?.let{c->GlassDialog("Delete this chat?",{delete=null}){Text("This hides the conversation from your inbox. A new message can bring it back.");TextButton(onClick={viewModel.deleteChatForMe(c.id);delete=null}){Text("Delete for me")}}}
  }
}

@Composable
fun GlassBottomBar(selectedRoute:String,onNavigateToChats:()->Unit,onNavigateToFavorites:()->Unit,onNavigateToArchived:()->Unit,onNavigateToSettings:()->Unit,modifier:Modifier=Modifier){
  val items=listOf(Triple("chats","All",Icons.Default.ChatBubbleOutline),Triple("favorites","Favorites",Icons.Default.StarOutline),Triple("archived","Archived",Icons.Default.Inventory2),Triple("settings","Settings",Icons.Default.Settings))
  val clicks=listOf(onNavigateToChats,onNavigateToFavorites,onNavigateToArchived,onNavigateToSettings)
  val index=items.indexOfFirst{it.first==selectedRoute}.coerceAtLeast(0)
  val reduced=LocalLiquidGlass.current.isReducedMotion
  Box(modifier.navigationBarsPadding().padding(horizontal=16.dp,vertical=9.dp)){
    GlassCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(36.dp),elevation=8.dp){
      BoxWithConstraints(Modifier.fillMaxWidth().padding(6.dp)){
        val width=maxWidth/4
        val x by animateDpAsState(width*index,if(reduced)tween(0)else spring(dampingRatio=.72f,stiffness=430f),label="tab_glass_pill")
        Box(Modifier.offset(x=x).width(width).height(58.dp).clip(RoundedCornerShape(29.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha=.13f)))
        Row(Modifier.fillMaxWidth()) {items.forEachIndexed{i,item->Column(Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(29.dp)).clickable(onClick=clicks[i]),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){val color=if(index==i)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant;Icon(item.third,item.second,tint=color,modifier=Modifier.size(23.dp));Spacer(Modifier.height(3.dp));Text(item.second,fontSize=10.sp,color=color,maxLines=1)}}
      }
    }
  }
}
