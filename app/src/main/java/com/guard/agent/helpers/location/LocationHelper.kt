package com.guard.agent.helpers.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.database.FirebaseDatabase
import com.guard.agent.core.Constants
import com.guard.agent.utils.Logger

object LocationHelper {

    private const val TAG = "LocationHelper"
    private var client: FusedLocationProviderClient? = null
    private var callback: LocationCallback? = null

    /** Background location updates chalu karo — 15 min interval */
    @SuppressLint("MissingPermission")
    fun startLocationUpdates(ctx: Context, deviceKey: String) {
        if (client != null) return
        try {
            client = LocationServices.getFusedLocationProviderClient(ctx)

            val request = LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                Constants.LOCATION_INTERVAL_MS
            )
                .setMinUpdateIntervalMillis(Constants.LOCATION_MIN_INTERVAL_MS)
                .setMinUpdateDistanceMeters(Constants.LOCATION_MIN_DISTANCE_M)
                .build()

            callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    push(ctx, deviceKey, loc)
                    LocationHistoryHelper.append(ctx, deviceKey, loc)
                }
            }

            client?.requestLocationUpdates(request, callback!!, Looper.getMainLooper())
            Logger.d(TAG, "Location updates started")
        } catch (e: Exception) {
            Logger.e(TAG, "startLocationUpdates failed", e)
        }
    }

    /** Ek baar location bhejo (on-demand) */
    @SuppressLint("MissingPermission")
    fun sendOnce(ctx: Context, deviceKey: String) {
        try {
            val c = LocationServices.getFusedLocationProviderClient(ctx)
            c.lastLocation
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        push(ctx, deviceKey, loc)
                        LocationHistoryHelper.append(ctx, deviceKey, loc)
                    } else {
                        sendFresh(ctx, deviceKey)
                    }
                }
                .addOnFailureListener {
                    sendFresh(ctx, deviceKey)
                }
        } catch (e: Exception) {
            Logger.e(TAG, "sendOnce failed", e)
        }
    }

    /** Fresh location fetch karo (agar lastLocation null ho) */
    @SuppressLint("MissingPermission")
    private fun sendFresh(ctx: Context, deviceKey: String) {
        try {
            val c = LocationServices.getFusedLocationProviderClient(ctx)
            val req = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                5000L
            ).setMaxUpdates(1).build()

            c.requestLocationUpdates(req, object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let {
                        push(ctx, deviceKey, it)
                        LocationHistoryHelper.append(ctx, deviceKey, it)
                    }
                    c.removeLocationUpdates(this)
                }
            }, Looper.getMainLooper())
        } catch (e: Exception) {
            Logger.e(TAG, "sendFresh failed", e)
        }
    }

    /** Firebase mein location push karo */
    private fun push(ctx: Context, deviceKey: String, loc: Location) {
        try {
            val data = mapOf(
                "lat" to loc.latitude,
                "lng" to loc.longitude,
                "accuracy" to loc.accuracy,
                "provider" to (loc.provider ?: "unknown"),
                "speed" to loc.speed,
                "bearing" to loc.bearing,
                "time" to System.currentTimeMillis()
            )
            FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_DEVICES)
                .child(deviceKey).child(Constants.SUB_LOCATION)
                .setValue(data)

            Logger.d(TAG, "Location: ${loc.latitude}, ${loc.longitude}")
        } catch (e: Exception) {
            Logger.e(TAG, "push failed", e)
        }
    }

    /** Updates band karo */
    fun stop() {
        try {
            client?.let { c ->
                callback?.let { cb -> c.removeLocationUpdates(cb) }
            }
        } catch (_: Exception) {}
        client = null
        callback = null
    }
}
