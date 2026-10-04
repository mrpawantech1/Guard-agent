package com.guard.agent.command

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import com.google.firebase.database.FirebaseDatabase
import com.guard.agent.core.Constants
import com.guard.agent.core.DeviceKey
import com.guard.agent.receivers.AdminReceiver
import com.guard.agent.utils.Logger

object CommandHandler {

    private const val TAG = "CommandHandler"
    private var alarmPlayer: MediaPlayer? = null

    val deviceKey: String
        get() = DeviceKey.get(com.guard.agent.core.App.context)

    /**
     * Main entry — command receive karke router ko pass karo.
     */
    fun handle(ctx: Context, cmd: String): Boolean {
        Logger.d(TAG, "Handling: $cmd")
        val key = DeviceKey.get(ctx)
        return CommandRouter.route(ctx, cmd, key)
    }

    // ---------- LOCK ----------
    fun lockDevice(ctx: Context) {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(ctx, AdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) {
            dpm.lockNow()
            Logger.d(TAG, "Device locked")
        } else {
            Logger.w(TAG, "Admin not active — cannot lock")
        }
    }

    // ---------- WIPE ----------
    fun wipeDevice(ctx: Context) {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(ctx, AdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) {
            @Suppress("DEPRECATION")
            dpm.wipeData(0)
            Logger.d(TAG, "Wipe triggered")
        } else {
            Logger.w(TAG, "Admin not active — cannot wipe")
        }
    }

    // ---------- ALARM ----------
    fun playAlarm(ctx: Context) {
        stopAlarm()
        try {
            val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            // Volume max
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(
                AudioManager.STREAM_ALARM,
                am.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0
            )

            alarmPlayer = MediaPlayer().apply {
                setDataSource(ctx, uri)
                isLooping = true
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                prepare()
                start()
            }
            Logger.d(TAG, "Alarm playing")
        } catch (e: Exception) {
            Logger.e(TAG, "Alarm failed", e)
        }
    }

    fun stopAlarm() {
        try {
            alarmPlayer?.stop()
            alarmPlayer?.release()
        } catch (_: Exception) {}
        alarmPlayer = null
    }

    // ---------- PING ----------
    fun ping(ctx: Context, deviceKey: String) {
        try {
            FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_DEVICES)
                .child(deviceKey).child(Constants.SUB_LAST_SEEN)
                .setValue(System.currentTimeMillis())
            Logger.d(TAG, "Ping sent")
        } catch (e: Exception) {
            Logger.e(TAG, "Ping failed", e)
        }
    }
}
