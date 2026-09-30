package com.example

import android.app.Application
import com.example.data.network.LiquidApi
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/** Process-wide initialization before MainActivity or any ViewModel is created. */
class LiquidChatApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    LiquidApi.context = applicationContext
    FirebaseApp.initializeApp(this)

    runCatching {
      FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
        if (BuildConfig.DEBUG) DebugAppCheckProviderFactory.getInstance()
        else PlayIntegrityAppCheckProviderFactory.getInstance()
      )
    }
    // App Check initialization must never make the process unlaunchable. Enforcement is enabled
    // in Firebase Console only after the provider is configured for the installed build.
  }
}
