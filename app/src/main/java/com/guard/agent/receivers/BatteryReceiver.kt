package com.guard.agent.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import com.google.firebase.database.FirebaseDatabase
import com.guard.agent.core.Constants
import com.guard.agent.core.DeviceKey
import com.guard.agent.utils.Logger

class BatteryReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        try {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val percent = if (scale > 0) (level * 100) / scale else -1

            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            val chargingType = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                else -> "None"
            }

            val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0
            val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)

            val health = when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                else -> "Unknown"
            }

            val data = mapOf(
                "percent" to percent,
                "isCharging" to isCharging,
                "chargingType" to chargingType,
                "temperature" to temp,
                "voltage" to voltage,
                "health" to health,
                "time" to System.currentTimeMillis()
            )

            val key = DeviceKey.get(ctx)
            FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_DEVICES)
                .child(key).child(Constants.SUB_BATTERY)
                .setValue(data)

            Logger.d("BatteryReceiver", "Battery: $percent% charging=$isCharging")
        } catch (e: Exception) {
            Logger.e("BatteryReceiver", "Battery push failed", e)
        }
    }
}
