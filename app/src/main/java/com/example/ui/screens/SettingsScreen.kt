package com.example.ui.screens
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ui.components.*
import com.example.ui.viewmodel.LiquidChatViewModel
import kotlinx.coroutines.launch
@Composable
fun SettingsScreen(viewModel:LiquidChatViewModel,onBackClick:()->Unit,onNavigateToAppearance:()->Unit,onNavigateToHomeTab:(String)->Unit,onLogout:()->Unit){
 val upload by viewModel.upload.collectAsState();val me by viewModel.currentUser.collectAsState();val privacy by viewModel.privacy.collectAsState();val notifications by viewModel.notifications.collectAsState();val blocked by viewModel.blockedUserIds.collectAsState();val users by viewModel.users.collectAsState();val context=LocalContext.current
 var dialog by remember{mutableStateOf("")};var name by remember(me.displayName){mutableStateOf(me.displayName)};var username by remember(me.username){mutableStateOf(me.username)};var bio by remember(me.bio){mutableStateOf(me.bio)};var busy by remember{mutableStateOf(false)};var cacheSize by remember{mutableStateOf(0L)};val scope=rememberCoroutineScope()
 val photo=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){it?.let{viewModel.uploadProfilePhoto(it)}}
 LiquidBackground{
  Scaffold(containerColor=Color.Transparent,bottomBar={GlassBottomBar("settings",{onNavigateToHomeTab("All")},{onNavigateToHomeTab("Favorites")},{onNavigateToHomeTab("Archived")},{})}){padding->
   Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
    GlassHeader("Settings",onBackClick=onBackClick)
    GlassCard(Modifier.fillMaxWidth()){Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically){GlassAvatar(me.photoUrl,me.displayName,64.dp,onClick={photo.launch("image/*")});Spacer(Modifier.width(16.dp));Column(Modifier.weight(1f)){Text(me.displayName,style=MaterialTheme.typography.titleLarge);Text("@${me.username}",color=MaterialTheme.colorScheme.onSurfaceVariant);TextButton(onClick={dialog="Profile"}){Text("Edit profile")}}}}
    GlassCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){
      SettingRow("Appearance & Liquid Glass",onNavigateToAppearance)
      SettingRow("Privacy",{dialog="Privacy"})
      SettingRow("Notifications",{dialog="Notifications"})
      SettingRow("Data & Storage",{scope.launch{cacheSize=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){context.cacheDir.walkTopDown().filter{it.isFile}.sumOf{it.length()}};dialog="Data & Storage"}})
      SettingRow("Blocked contacts",{dialog="Blocked contacts"})
      SettingRow("Verify email",{viewModel.repository.verifyEmail()})
      SettingRow("About & Support",{dialog="About & Support"})
    }}
    TextButton(onClick={dialog="Log out"}){Text("Log out",color=MaterialTheme.colorScheme.error)}
    TextButton(onClick={dialog="Delete account"}){Text("Delete account",color=MaterialTheme.colorScheme.error)}
   }
  }
  if(dialog.isNotBlank())GlassDialog(dialog,{if(!busy)dialog=""}){when(dialog){
   "Profile"->{GlassTextField(name,{name=it},placeholder="Name");GlassTextField(username,{username=it},placeholder="Username");GlassTextField(bio,{bio=it},placeholder="About",singleLine=false,maxLines=3);GlassButton("Save",{viewModel.updateProfile(name,username,bio,me.phoneNumber);dialog=""})}
   "Privacy"->{
    Toggle("Show last seen",privacy.lastSeenVisibility!="Nobody"){viewModel.updatePrivacy(privacy.copy(lastSeenVisibility=if(it)"Everyone"else"Nobody"))}
    Toggle("Show online status",privacy.onlineVisibility!="Nobody"){viewModel.updatePrivacy(privacy.copy(onlineVisibility=if(it)"Everyone"else"Nobody"))}
    Toggle("Show profile photo",privacy.profilePhotoVisibility!="Nobody"){viewModel.updatePrivacy(privacy.copy(profilePhotoVisibility=if(it)"Everyone"else"Nobody"))}
    Toggle("Read receipts",privacy.readReceipts){viewModel.updatePrivacy(privacy.copy(readReceipts=it))}
   }
   "Notifications"->{Toggle("Message notifications",notifications.messages){viewModel.updateNotifications(notifications.copy(messages=it))};Toggle("Vibration",notifications.vibration){viewModel.updateNotifications(notifications.copy(vibration=it))}}
   "Data & Storage"->{Text("Temporary cache: ${cacheSize/1024/1024} MB");Text("Attachments use Appwrite. Maximum file size: 25 MB.");if(upload!=null)Text("Wait for your upload to finish before clearing cache.") else GlassButton("Clear temporary cache",{scope.launch{with(kotlinx.coroutines.Dispatchers.IO){kotlinx.coroutines.withContext(this){context.cacheDir.listFiles()?.forEach{it.deleteRecursively()}}};cacheSize=0}})}
   "Blocked contacts"->{if(blocked.isEmpty())Text("No blocked contacts");blocked.forEach{id->Row(verticalAlignment=Alignment.CenterVertically){Text(users.find{it.uid==id}?.displayName?:"Contact",Modifier.weight(1f));TextButton(onClick={viewModel.unblockUser(id)}){Text("Unblock")}}}
   "About & Support"->{Text("Liquid Chat 4.0.0");Text("Developed by Pritam Pal");Text("© 2026 Pritam Pal");TextButton(onClick={context.startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO,android.net.Uri.parse("mailto:officialpritam@gmail.com")))}){Text("Contact support")}}
   "Log out"->{Text("Pending unsent messages on this device will be removed. Send or retry them before logging out.");GlassButton("Log out",{viewModel.logout();onLogout();dialog=""})}
   "Delete account"->{Text("This permanently deletes your account, your uploaded files and your conversations for both participants. Sign in again first if prompted.");GlassButton("Permanently delete",{busy=true;viewModel.deleteAccount{ok,error->busy=false;if(ok){dialog="";onLogout()}}},isLoading=busy)}
  }}
 }
}
@Composable private fun SettingRow(title:String,onClick:()->Unit){Text(title,modifier=Modifier.fillMaxWidth().clickable(onClick=onClick).padding(16.dp));HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.12f))}
@Composable private fun Toggle(title:String,checked:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(title,Modifier.weight(1f));Switch(checked,onChange)}}
