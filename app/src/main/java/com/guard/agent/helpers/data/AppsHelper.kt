package com.guard.agent.helpers.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.google.firebase.database.FirebaseDatabase
import com.guard.agent.core.Constants
import com.guard.agent.utils.Logger

object AppsHelper {

    private const val TAG = "AppsHelper"

    fun push(ctx: Context, deviceKey: String) {
        try {
            val pm = ctx.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val list = mutableListOf<Map<String, Any>>()

            apps.forEach { app ->
                try {
                    val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val name = pm.getApplicationLabel(app).toString()
                    val versionName = try {
                        pm.getPackageInfo(app.packageName, 0).versionName ?: "unknown"
                    } catch (_: Exception) { "unknown" }

                    list.add(
                        mapOf(
                            "name" to name,
                            "package" to app.packageName,
                            "version" to versionName,
                            "system" to isSystem
                        )
                    )
                } catch (_: Exception) {}
            }

            // Sort by name
            list.sortBy { it["name"] as String }

            FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_DEVICES)
                .child(deviceKey).child(Constants.SUB_APPS)
                .setValue(
                    mapOf(
                        "count" to list.size,
                        "list" to list,
                        "time" to System.currentTimeMillis()
                    )
                )

            Logger.d(TAG, "Pushed ${list.size} apps")
        } catch (e: Exception) {
            Logger.e(TAG, "push failed", e)
        }
    }
}
