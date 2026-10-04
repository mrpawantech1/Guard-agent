package com.guard.agent.helpers.media

import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.firebase.database.FirebaseDatabase
import com.guard.agent.core.Constants
import com.guard.agent.core.DeviceKey
import com.guard.agent.services.LiveStreamService
import com.guard.agent.utils.Logger

object LiveStreamHelper {

    private const val TAG = "LiveStreamHelper"

    /**
     * Live camera stream start karo.
     * Service foreground mein chalegi, frames Firebase pe upload karegi.
     */
    fun startCameraStream(ctx: Context, fps: Int = 1) {
        try {
            val deviceKey = DeviceKey.get(ctx)
            val intent = Intent(ctx, LiveStreamService::class.java).apply {
                action = LiveStreamService.ACTION_START_CAMERA
                putExtra(LiveStreamService.EXTRA_FPS, fps)
                putExtra(LiveStreamService.EXTRA_DEVICE_KEY, deviceKey)
            }
            startService(ctx, intent)
            Logger.d(TAG, "Camera stream start requested (fps=$fps)")
        } catch (e: Exception) {
            Logger.e(TAG, "startCameraStream failed", e)
        }
    }

    /**
     * Live audio stream start karo.
     */
    fun startAudioStream(ctx: Context) {
        try {
            val deviceKey = DeviceKey.get(ctx)
            val intent = Intent(ctx, LiveStreamService::class.java).apply {
                action = LiveStreamService.ACTION_START_AUDIO
                putExtra(LiveStreamService.EXTRA_DEVICE_KEY, deviceKey)
            }
            startService(ctx, intent)
            Logger.d(TAG, "Audio stream start requested")
        } catch (e: Exception) {
            Logger.e(TAG, "startAudioStream failed", e)
        }
    }

    /**
     * Dono (camera + audio) stream start karo.
     */
    fun startBoth(ctx: Context, fps: Int = 1) {
        try {
            val deviceKey = DeviceKey.get(ctx)
            val intent = Intent(ctx, LiveStreamService::class.java).apply {
                action = LiveStreamService.ACTION_START_BOTH
                putExtra(LiveStreamService.EXTRA_FPS, fps)
                putExtra(LiveStreamService.EXTRA_DEVICE_KEY, deviceKey)
            }
            startService(ctx, intent)
            Logger.d(TAG, "Both streams start requested")
        } catch (e: Exception) {
            Logger.e(TAG, "startBoth failed", e)
        }
    }

    /**
     * Live stream band karo.
     */
    fun stop(ctx: Context) {
        try {
            val intent = Intent(ctx, LiveStreamService::class.java).apply {
                action = LiveStreamService.ACTION_STOP
            }
            ctx.startService(intent)
            Logger.d(TAG, "Stop requested")
        } catch (e: Exception) {
            Logger.e(TAG, "stop failed", e)
        }
    }

    /**
     * Firebase mein stream status update karo.
     */
    fun updateStatus(deviceKey: String, status: String, type: String = "") {
        try {
            FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_DEVICES)
                .child(deviceKey).child("liveStream")
                .setValue(
                    mapOf(
                        "status" to status,
                        "type" to type,
                        "time" to System.currentTimeMillis()
                    )
                )
        } catch (e: Exception) {
            Logger.e(TAG, "updateStatus failed", e)
        }
    }

    private fun startService(ctx: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent)
        } else {
            ctx.startService(intent)
        }
    }
}
