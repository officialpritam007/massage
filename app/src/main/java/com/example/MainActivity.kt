package com.example
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.runtime.*
import com.example.data.network.LiquidApi
import com.example.ui.LiquidChatApp
class MainActivity:ComponentActivity(){
 private var notificationConversation by mutableStateOf<String?>(null)
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);LiquidApi.context=applicationContext;enableEdgeToEdge();notificationConversation=intent.getStringExtra("conversation_id")
  if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.POST_NOTIFICATIONS),2001)
  setContent{LiquidChatApp(notificationConversation=notificationConversation,onNotificationHandled={notificationConversation=null})}
 }
 override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);notificationConversation=intent.getStringExtra("conversation_id")}
}
