package com.guard.agent.core

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

object DeviceKey {

    @Volatile
    private var cached: String? = null

    fun get(ctx: Context): String {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val prefs = ctx.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            var key = prefs.getString(Constants.KEY_DEVICE_KEY, null)
            if (key.isNullOrBlank()) {
                key = generate(ctx)
                prefs.edit().putString(Constants.KEY_DEVICE_KEY, key).apply()
            }
            cached = key
            return key
        }
    }

    private fun generate(ctx: Context): String {
        val androidId = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        val hash = MessageDigest.getInstance("SHA-256").digest(androidId.toByteArray())
        val hex = hash.joinToString("") { "%02x".format(it) }
        return hex.substring(0, 16).uppercase().chunked(4).joinToString("-")
    }
}
