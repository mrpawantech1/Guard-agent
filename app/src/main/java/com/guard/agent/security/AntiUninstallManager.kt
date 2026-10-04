package com.guard.agent.security

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import com.guard.agent.receivers.AdminReceiver
import com.guard.agent.utils.Logger
import com.guard.agent.utils.PermissionUtils

object AntiUninstallManager {

    private const val TAG = "AntiUninstall"

    /**
     * Check karo ki anti-uninstall protection active hai ya nahi.
     */
    fun isProtected(ctx: Context): Boolean {
        return PermissionUtils.isAdminActive(ctx) &&
                PermissionUtils.isAccessibilityEnabled(ctx)
    }

    /**
     * Protection status Firebase mein push karo.
     */
    fun reportStatus(ctx: Context, deviceKey: String) {
        try {
            val adminActive = PermissionUtils.isAdminActive(ctx)
            val accessEnabled = PermissionUtils.isAccessibilityEnabled(ctx)
            val batteryOptimized = !PermissionUtils.isBatteryOptimizationIgnored(ctx)
            val notifEnabled = PermissionUtils.isNotificationListenerEnabled(ctx)

            com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("devices").child(deviceKey).child("protection")
                .setValue(
                    mapOf(
                        "adminActive" to adminActive,
                        "accessibilityEnabled" to accessEnabled,
                        "batteryOptimized" to batteryOptimized,
                        "notificationListener" to notifEnabled,
                        "protected" to (adminActive && accessEnabled),
                        "time" to System.currentTimeMillis()
                    )
                )

            Logger.d(TAG, "Status: admin=$adminActive access=$accessEnabled")
        } catch (e: Exception) {
            Logger.e(TAG, "reportStatus failed", e)
        }
    }

    /**
     * Device Admin remove hone pe alert bhejo.
     */
    fun reportAdminDisabled(ctx: Context, deviceKey: String) {
        try {
            com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("devices").child(deviceKey).child("adminDisabled")
                .setValue(
                    mapOf(
                        "time" to System.currentTimeMillis(),
                        "alert" to true
                    )
                )
            Logger.w(TAG, "Admin disabled — alert sent")
        } catch (e: Exception) {
            Logger.e(TAG, "reportAdminDisabled failed", e)
        }
    }

    /**
     * Device Admin remove karne ki koshish karo (agar user ne disable kar diya).
     */
    fun tryReEnableAdmin(ctx: Context): Boolean {
        try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(ctx, AdminReceiver::class.java)
            if (!dpm.isAdminActive(admin)) {
                Logger.w(TAG, "Admin not active — user ko manually enable karna padega")
                return false
            }
            return true
        } catch (e: Exception) {
            Logger.e(TAG, "tryReEnableAdmin failed", e)
            return false
        }
    }
}
