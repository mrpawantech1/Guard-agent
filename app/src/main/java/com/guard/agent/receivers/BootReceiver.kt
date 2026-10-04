package com.guard.agent.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.guard.agent.services.GuardService
import com.guard.agent.utils.Logger

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val action = intent.action ?: return
        Logger.d("BootReceiver", "Boot event: $action")

        val svc = Intent(ctx, GuardService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(svc)
            } else {
                ctx.startService(svc)
            }
            Logger.d("BootReceiver", "Service started")
        } catch (e: Exception) {
            Logger.e("BootReceiver", "Failed to start service", e)
        }
    }
}
