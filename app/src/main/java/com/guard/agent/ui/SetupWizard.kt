package com.guard.agent.ui

import android.content.Context
import android.os.Build
import com.guard.agent.core.Constants
import com.guard.agent.core.PrefsManager
import com.guard.agent.utils.PermissionUtils

/**
 * Setup wizard helper — checks setup completeness.
 */
object SetupWizard {

    data class SetupStatus(
        val runtimePermissions: Boolean,
        val admin: Boolean,
        val batteryOptimization: Boolean,
        val accessibility: Boolean,
        val notificationListener: Boolean,
        val backgroundLocation: Boolean
    ) {
        val completedCount: Int
            get() = listOf(
                runtimePermissions,
                admin,
                batteryOptimization,
                accessibility,
                notificationListener,
                backgroundLocation
            ).count { it }

        val totalCount: Int = 6

        val isComplete: Boolean
            get() = completedCount == totalCount
    }

    fun checkStatus(ctx: Context): SetupStatus {
        return SetupStatus(
            runtimePermissions = PermissionUtils.hasAllRuntimePermissions(ctx),
            admin = PermissionUtils.isAdminActive(ctx),
            batteryOptimization = PermissionUtils.isBatteryOptimizationIgnored(ctx),
            accessibility = PermissionUtils.isAccessibilityEnabled(ctx),
            notificationListener = PermissionUtils.isNotificationListenerEnabled(ctx),
            backgroundLocation = hasBackgroundLocation(ctx)
        )
    }

    private fun hasBackgroundLocation(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return PermissionUtils.hasAllRuntimePermissions(ctx)
    }

    fun markSetupComplete() {
        PrefsManager.putBoolean(Constants.KEY_SETUP_DONE, true)
    }

    fun isSetupComplete(): Boolean =
        PrefsManager.getBoolean(Constants.KEY_SETUP_DONE)
}
