package com.guard.agent.helpers.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.guard.agent.core.DeviceKey
import com.guard.agent.utils.Logger

class GeofenceReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        try {
            val event = GeofencingEvent.fromIntent(intent) ?: return

            if (event.hasError()) {
                Logger.e("GeofenceReceiver", "Error code: ${event.errorCode}")
                return
            }

            val transition = event.geofenceTransition
            val triggering = event.triggeringGeofences ?: return
            val loc = event.triggeringLocation
            val deviceKey = DeviceKey.get(ctx)

            triggering.forEach { fence ->
                GeofenceHelper.pushAlert(ctx, deviceKey, fence.requestId, transition, loc)
            }
        } catch (e: Exception) {
            Logger.e("GeofenceReceiver", "onReceive failed", e)
        }
    }
}
