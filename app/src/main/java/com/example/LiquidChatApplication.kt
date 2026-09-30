package com.example

import android.app.Application
import com.example.data.network.LiquidApi
import com.google.firebase.FirebaseApp

/**
 * Initializes process-wide dependencies before MainActivity or any ViewModel is created.
 * This prevents startup-order crashes when a cached Firebase session immediately starts sync.
 */
class LiquidChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LiquidApi.context = applicationContext
        FirebaseApp.initializeApp(this)
    }
}
