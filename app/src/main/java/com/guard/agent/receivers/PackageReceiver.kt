package com.guard.agent.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.database.FirebaseDatabase
import com.guard.agent.core.Constants
import com.guard.agent.core.DeviceKey
import com.guard.agent.utils.Logger

class PackageReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val action = intent.action ?: return
        val pkg = intent.data?.schemeSpecificPart ?: return

        try {
            // Apni app skip karo
            if (pkg == ctx.packageName) return

            val event = when (action) {
                Intent.ACTION_PACKAGE_ADDED -> "installed"
                Intent.ACTION_PACKAGE_REMOVED -> "uninstalled"
                else -> return
            }

            val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            if (replacing) return // Update skip karo

            Logger.d("PackageReceiver", "$event: $pkg")

            val key = DeviceKey.get(ctx)
            FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_DEVICES)
                .child(key).child("packageEvents").push()
                .setValue(
                    mapOf(
                        "event" to event,
                        "package" to pkg,
                        "time" to System.currentTimeMillis()
                    )
                )
        } catch (e: Exception) {
            Logger.e("PackageReceiver", "Package event failed", e)
        }
    }
}
