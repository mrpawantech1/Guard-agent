package com.guard.agent.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import com.google.firebase.database.FirebaseDatabase
import com.guard.agent.core.Constants
import com.guard.agent.core.DeviceKey
import com.guard.agent.utils.Logger

class NetworkReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val active = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(active)

            val data = mutableMapOf<String, Any>(
                "time" to System.currentTimeMillis()
            )

            val connected = caps != null
            data["connected"] = connected

            if (connected) {
                data["wifi"] = caps!!.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                data["cellular"] = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                data["vpn"] = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                data["type"] = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                    else -> "Unknown"
                }
            }

            // WiFi name
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    @Suppress("DEPRECATION")
                    val wifiInfo = wm.connectionInfo
                    @Suppress("DEPRECATION")
                    val ssid = wifiInfo?.ssid?.replace("\"", "")
                    if (!ssid.isNullOrBlank() && ssid != "<unknown ssid>") {
                        data["wifiName"] = ssid
                    }
                }
            } catch (_: Exception) {}

            // Cellular operator
            try {
                val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val operator = tm.networkOperatorName
                if (!operator.isNullOrBlank()) {
                    data["carrier"] = operator
                }
            } catch (_: Exception) {}

            val key = DeviceKey.get(ctx)
            FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_DEVICES)
                .child(key).child(Constants.SUB_NETWORK)
                .setValue(data)

            Logger.d("NetworkReceiver", "Network: ${data["type"]}")
        } catch (e: Exception) {
            Logger.e("NetworkReceiver", "Network push failed", e)
        }
    }
}
