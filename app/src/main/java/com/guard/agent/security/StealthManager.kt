package com.guard.agent.security

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.guard.agent.core.Constants
import com.guard.agent.core.PrefsManager
import com.guard.agent.ui.MainActivity
import com.guard.agent.utils.Logger

object StealthManager {

    private const val TAG = "StealthManager"

    /**
     * App icon hide karo (launcher se gayab).
     */
    fun hideIcon(ctx: Context) {
        try {
            ctx.packageManager.setComponentEnabledSetting(
                ComponentName(ctx, MainActivity::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            PrefsManager.putBoolean(Constants.KEY_ICON_HIDDEN, true)
            Logger.d(TAG, "Icon hidden")
        } catch (e: Exception) {
            Logger.e(TAG, "hideIcon failed", e)
        }
    }

    /**
     * App icon wapas dikhao.
     */
    fun showIcon(ctx: Context) {
        try {
            ctx.packageManager.setComponentEnabledSetting(
                ComponentName(ctx, MainActivity::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            PrefsManager.putBoolean(Constants.KEY_ICON_HIDDEN, false)
            Logger.d(TAG, "Icon shown")
        } catch (e: Exception) {
            Logger.e(TAG, "showIcon failed", e)
        }
    }

    fun isIconHidden(): Boolean =
        PrefsManager.getBoolean(Constants.KEY_ICON_HIDDEN, false)
}
