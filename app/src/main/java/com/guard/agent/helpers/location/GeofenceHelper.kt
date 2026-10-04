package com.guard.agent.helpers.location

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.guard.agent.core.Constants
import com.guard.agent.utils.Logger

object GeofenceHelper {

    private const val TAG = "GeofenceHelper"
    private var geofencingClient: GeofencingClient? = null
    private var listener: ValueEventListener? = null
    private val activeGeofences = mutableMapOf<String, Geofence>()

    /**
     * Firebase se geofences fetch karke locally register karo.
     * Panel se jab bhi geofence add/remove ho, ye call hota hai.
     */
    @SuppressLint("MissingPermission")
    fun syncGeofences(ctx: Context, deviceKey: String) {
        try {
            geofencingClient = LocationServices.getGeofencingClient(ctx)

            val ref = FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_GEOFENCES).child(deviceKey)

            // Purana listener hatao
            listener?.let { ref.removeEventListener(it) }

            listener = object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    val newFences = mutableListOf<Geofence>()
                    activeGeofences.clear()

                    snap.children.forEach { child ->
                        try {
                            val id = child.key ?: return@forEach
                            val lat = child.child("lat").getValue(Double::class.java) ?: return@forEach
                            val lng = child.child("lng").getValue(Double::class.java) ?: return@forEach
                            val radius = child.child("radius").getValue(Float::class.java) ?: 200f
                            val alertOn = child.child("alertOn").getValue(String::class.java) ?: "both"

                            val transitions = when (alertOn.lowercase()) {
                                "enter" -> Geofence.GEOFENCE_TRANSITION_ENTER
                                "exit" -> Geofence.GEOFENCE_TRANSITION_EXIT
                                else -> Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
                            }

                            val fence = Geofence.Builder()
                                .setRequestId(id)
                                .setCircularRegion(lat, lng, radius)
                                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                                .setTransitionTypes(transitions)
                                .build()

                            newFences.add(fence)
                            activeGeofences[id] = fence
                        } catch (e: Exception) {
                            Logger.e(TAG, "Parse geofence failed", e)
                        }
                    }

                    registerFences(ctx, newFences)
                }

                override fun onCancelled(error: DatabaseError) {
                    Logger.e(TAG, "Geofence listener cancelled", error.toException())
                }
            }

            ref.addValueEventListener(listener!!)
        } catch (e: Exception) {
            Logger.e(TAG, "syncGeofences failed", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerFences(ctx: Context, fences: List<Geofence>) {
        try {
            val client = geofencingClient ?: return

            // Pehle saare hatao
            client.removeGeofences(getPendingIntent(ctx))

            if (fences.isEmpty()) {
                Logger.d(TAG, "No geofences to register")
                return
            }

            val request = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(fences)
                .build()

            client.addGeofences(request, getPendingIntent(ctx))
                .addOnSuccessListener {
                    Logger.d(TAG, "Registered ${fences.size} geofences")
                }
                .addOnFailureListener { e ->
                    Logger.e(TAG, "Register failed", e)
                }
        } catch (e: Exception) {
            Logger.e(TAG, "registerFences failed", e)
        }
    }

    private fun getPendingIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, GeofenceReceiver::class.java).apply {
            action = "com.guard.agent.GEOFENCE_EVENT"
        }
        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(ctx, 9001, intent, flags)
    }

    /** Firebase mein geofence alert push karo */
    fun pushAlert(ctx: Context, deviceKey: String, fenceId: String, transition: Int, loc: Location?) {
        try {
            val type = when (transition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> "enter"
                Geofence.GEOFENCE_TRANSITION_EXIT -> "exit"
                else -> "unknown"
            }

            val data = mapOf(
                "fenceId" to fenceId,
                "type" to type,
                "lat" to (loc?.latitude ?: 0.0),
                "lng" to (loc?.longitude ?: 0.0),
                "time" to System.currentTimeMillis()
            )

            FirebaseDatabase.getInstance()
                .getReference("geofenceAlerts")
                .child(deviceKey).push()
                .setValue(data)

            Logger.d(TAG, "Geofence alert: $fenceId / $type")
        } catch (e: Exception) {
            Logger.e(TAG, "pushAlert failed", e)
        }
    }

    /** Listener band karo */
    fun stop(ctx: Context) {
        try {
            listener?.let {
                FirebaseDatabase.getInstance()
                    .getReference(Constants.PATH_GEOFENCES)
                    .removeEventListener(it)
            }
            geofencingClient?.removeGeofences(getPendingIntent(ctx))
        } catch (_: Exception) {}
        listener = null
        activeGeofences.clear()
    }
}
