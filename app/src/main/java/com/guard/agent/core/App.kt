package com.guard.agent.core

import android.app.Application
import android.content.Context

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Device key ensure karo
        DeviceKey.get(this)
        // Prefs init
        PrefsManager.init(this)
    }

    companion object {
        lateinit var instance: App
            private set
        val context: Context get() = instance
    }
}
