package com.guard.agent.helpers.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.google.firebase.database.FirebaseDatabase
import com.guard.agent.core.Constants
import com.guard.agent.utils.Logger

object BatteryHelper {

    private const val TAG = "BatteryHelper"

    fun push(ctx: Context, deviceKey: String) {
        try {
            val intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?: return

            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val percent = if (scale > 0) (level * 100) / scale else -1

            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0
            val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)

            FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_DEVICES)
                .child(deviceKey).child(Constants.SUB_BATTERY)
                .setValue(
                    mapOf(
                        "percent" to percent,
                        "isCharging" to isCharging,
                        "temperature" to temp,
                        "voltage" to voltage,
                        "time" to System.currentTimeMillis()
                    )
                )

            Logger.d(TAG, "Battery: $percent%")
        } catch (e: Exception) {
            Logger.e(TAG, "push failed", e)
        }
    }
}
