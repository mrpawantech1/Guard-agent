package com.guard.agent.helpers.location

import android.content.Context
import android.location.Location
import com.google.firebase.database.FirebaseDatabase
import com.guard.agent.core.Constants
import com.guard.agent.utils.Logger

object LocationHistoryHelper {

    private const val TAG = "LocationHistory"
    private const val MIN_DISTANCE_METERS = 100f
    private const val MIN_TIME_GAP_MS = 2 * 60 * 1000L  // 2 min
    private const val MAX_ENTRIES_PER_DAY = 500

    private var lastLat = 0.0
    private var lastLng = 0.0
    private var lastTime = 0L
    private var entriesToday = 0

    /**
     * Location history mein entry add karo (agar worth it ho).
     * Sirf tab save karta hai jab:
     * - 100m se zyada doori ho, YA
     * - 2 min se zyada time ho
     */
    fun append(ctx: Context, deviceKey: String, loc: Location) {
        try {
            val now = System.currentTimeMillis()

            // Distance calculation
            val distance = haversine(lastLat, lastLng, loc.latitude, loc.longitude)
            val timeGap = now - lastTime

            val shouldSave = lastTime == 0L ||
                    distance >= MIN_DISTANCE_METERS ||
                    timeGap >= MIN_TIME_GAP_MS

            if (!shouldSave) return

            // Daily limit check
            val dayKey = getDayKey(now)
            if (dayKey != getDayKey(lastTime)) {
                entriesToday = 0
            }
            if (entriesToday >= MAX_ENTRIES_PER_DAY) return

            val entry = mapOf(
                "lat" to loc.latitude,
                "lng" to loc.longitude,
                "accuracy" to loc.accuracy,
                "speed" to loc.speed,
                "time" to now
            )

            FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_DEVICES)
                .child(deviceKey).child(Constants.SUB_LOCATION_HISTORY)
                .push()
                .setValue(entry)

            lastLat = loc.latitude
            lastLng = loc.longitude
            lastTime = now
            entriesToday++

            Logger.d(TAG, "History entry: ${loc.latitude}, ${loc.longitude}")
        } catch (e: Exception) {
            Logger.e(TAG, "append failed", e)
        }
    }

    /** Haversine formula — 2 points ke beech distance (meters) */
    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        if (lat1 == 0.0 && lon1 == 0.0) return Float.MAX_VALUE
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return (r * c).toFloat()
    }

    private fun getDayKey(time: Long): String {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = time
        return "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.DAY_OF_YEAR)}"
    }

    /** Purani history clear karo (30 din se zyada) */
    fun cleanup(ctx: Context, deviceKey: String) {
        try {
            val cutoff = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            val ref = FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_DEVICES)
                .child(deviceKey).child(Constants.SUB_LOCATION_HISTORY)

            ref.orderByChild("time").endAt(cutoff.toDouble())
                .get()
                .addOnSuccessListener { snap ->
                    snap.children.forEach { it.ref.removeValue() }
                    Logger.d(TAG, "Cleaned ${snap.childrenCount} old entries")
                }
        } catch (e: Exception) {
            Logger.e(TAG, "cleanup failed", e)
        }
    }
}
