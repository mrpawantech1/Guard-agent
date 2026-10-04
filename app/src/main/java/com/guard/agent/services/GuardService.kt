package com.guard.agent.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.guard.agent.R
import com.guard.agent.command.CommandHandler
import com.guard.agent.core.Constants
import com.guard.agent.core.DeviceKey
import com.guard.agent.helpers.location.GeofenceHelper
import com.guard.agent.helpers.location.LocationHelper
import com.guard.agent.helpers.media.ScheduledRecorder
import com.guard.agent.security.OfflineQueueManager
import com.guard.agent.utils.Logger
import com.guard.agent.workers.HeartbeatWorker
import com.guard.agent.workers.LocationWorker
import com.guard.agent.workers.SyncWorker
import com.guard.agent.workers.UpdateCheckWorker

class GuardService : Service() {

    private lateinit var cmdRef: DatabaseReference
    private var listener: ValueEventListener? = null
    private val deviceKey by lazy { DeviceKey.get(this) }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(Constants.NOTIF_ID, buildNotification())
        Logger.d("GuardService", "Service created. Key=$deviceKey")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. Location updates
        try {
            LocationHelper.startLocationUpdates(this, deviceKey)
        } catch (e: Exception) {
            Logger.e("GuardService", "Location start failed", e)
        }

        // 2. Geofences sync
        try {
            GeofenceHelper.syncGeofences(this, deviceKey)
        } catch (e: Exception) {
            Logger.e("GuardService", "Geofence sync failed", e)
        }

        // 3. Scheduled tasks sync ⭐ NAYA
        try {
            ScheduledRecorder.syncSchedules(this, deviceKey)
            Logger.d("GuardService", "Schedules synced")
        } catch (e: Exception) {
            Logger.e("GuardService", "Schedule sync failed", e)
        }

        // 4. Firebase commands listen
        listenForCommands()

        // 5. Workers schedule
        try {
            HeartbeatWorker.schedule(this)
            SyncWorker.schedule(this)
            LocationWorker.schedule(this)
            UpdateCheckWorker.schedule(this)
            Logger.d("GuardService", "Workers scheduled")
        } catch (e: Exception) {
            Logger.e("GuardService", "Worker schedule failed", e)
        }

        // 6. Offline queue flush
        try {
            OfflineQueueManager.flush(this, deviceKey)
        } catch (_: Exception) {}

        // 7. lastSeen update
        updateLastSeen()

        return START_STICKY
    }

    private fun listenForCommands() {
        if (listener != null) return
        cmdRef = FirebaseDatabase.getInstance()
            .getReference(Constants.PATH_COMMANDS).child(deviceKey)

        listener = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val cmd = snap.child("cmd").getValue(String::class.java) ?: return
                val status = snap.child("status").getValue(String::class.java)
                    ?: Constants.STATUS_PENDING

                if (status == Constants.STATUS_PENDING) {
                    Logger.d("GuardService", "Command received: $cmd")

                    val executed = CommandHandler.handle(applicationContext, cmd)

                    cmdRef.child("status")
                        .setValue(if (executed) Constants.STATUS_DONE else Constants.STATUS_FAILED)
                    cmdRef.child("executedAt").setValue(System.currentTimeMillis())

                    logCommandHistory(cmd)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Logger.e("GuardService", "Command listener cancelled", error.toException())
            }
        }
        cmdRef.addValueEventListener(listener!!)
    }

    private fun logCommandHistory(cmd: String) {
        try {
            FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_COMMAND_HISTORY)
                .child(deviceKey).push()
                .setValue(
                    mapOf(
                        "cmd" to cmd,
                        "time" to System.currentTimeMillis()
                    )
                )
        } catch (_: Exception) {}
    }

    private fun updateLastSeen() {
        try {
            FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_DEVICES)
                .child(deviceKey).child(Constants.SUB_LAST_SEEN)
                .setValue(System.currentTimeMillis())
        } catch (_: Exception) {}
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(Constants.CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    Constants.CHANNEL_ID,
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = getString(R.string.channel_desc)
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                }
                mgr.createNotificationChannel(ch)
            }
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, Constants.CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.let { cmdRef.removeEventListener(it) }
        listener = null

        val intent = Intent(this, GuardService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restart = Intent(applicationContext, GuardService::class.java)
        restart.setPackage(packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(restart)
        } else {
            applicationContext.startService(restart)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
