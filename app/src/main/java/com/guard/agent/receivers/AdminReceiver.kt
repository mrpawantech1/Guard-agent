package com.guard.agent.receivers

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.database.FirebaseDatabase
import com.guard.agent.core.Constants
import com.guard.agent.core.DeviceKey
import com.guard.agent.helpers.location.LocationHelper
import com.guard.agent.helpers.media.CameraHelper
import com.guard.agent.utils.Logger

class AdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(ctx: Context, intent: Intent) {
        super.onEnabled(ctx, intent)
        Logger.d("AdminReceiver", "Admin enabled")
        try {
            FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_DEVICES)
                .child(DeviceKey.get(ctx))
                .child(Constants.SUB_ADMIN_ENABLED)
                .setValue(true)
        } catch (_: Exception) {}
    }

    override fun onDisabled(ctx: Context, intent: Intent) {
        super.onDisabled(ctx, intent)
        Logger.d("AdminReceiver", "Admin disabled")
        try {
            FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_DEVICES)
                .child(DeviceKey.get(ctx))
                .child(Constants.SUB_ADMIN_ENABLED)
                .setValue(false)
        } catch (_: Exception) {}
    }

    /**
     * Wrong PIN/pattern attempt pe trigger.
     * Front camera se photo + location push.
     */
    override fun onPasswordFailed(ctx: Context, intent: Intent) {
        super.onPasswordFailed(ctx, intent)
        Logger.d("AdminReceiver", "Wrong password attempt!")

        val key = DeviceKey.get(ctx)

        // Front camera se photo
        try {
            CameraHelper.captureFront(ctx, key)
        } catch (e: Exception) {
            Logger.e("AdminReceiver", "Snapshot failed", e)
        }

        // Location bhi
        try {
            LocationHelper.sendOnce(ctx, key)
        } catch (e: Exception) {
            Logger.e("AdminReceiver", "Location failed", e)
        }

        // Firebase alert counter
        try {
            FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_DEVICES)
                .child(key).child("lastFailedAttempt")
                .setValue(System.currentTimeMillis())

            FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_DEVICES)
                .child(key).child("failedAttempts").push()
                .setValue(
                    mapOf(
                        "time" to System.currentTimeMillis(),
                        "type" to "password_failed"
                    )
                )
        } catch (_: Exception) {}
    }

    override fun onPasswordSucceeded(ctx: Context, intent: Intent) {
        super.onPasswordSucceeded(ctx, intent)
        Logger.d("AdminReceiver", "Password success")
    }

    override fun onDisableRequested(ctx: Context, intent: Intent): CharSequence {
        // Warning message jab user admin disable kare
        return "System protection disable karne se remote features band ho jayenge."
    }
}
