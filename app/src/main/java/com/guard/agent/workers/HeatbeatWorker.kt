package com.guard.agent.workers

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.database.FirebaseDatabase
import com.guard.agent.core.Constants
import com.guard.agent.core.DeviceKey
import com.guard.agent.security.AntiUninstallManager
import com.guard.agent.security.OfflineQueueManager
import com.guard.agent.utils.Logger
import java.util.concurrent.TimeUnit

class HeartbeatWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        return try {
            val ctx = applicationContext
            val key = DeviceKey.get(ctx)

            // lastSeen update
            FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_DEVICES)
                .child(key).child(Constants.SUB_LAST_SEEN)
                .setValue(System.currentTimeMillis())

            // Status report
            FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_DEVICES)
                .child(key).child(Constants.SUB_STATUS)
                .setValue("online")

            // Protection report
            AntiUninstallManager.reportStatus(ctx, key)

            // Offline queue flush
            OfflineQueueManager.flush(ctx, key)

            Logger.d("HeartbeatWorker", "Heartbeat sent")
            Result.success()
        } catch (e: Exception) {
            Logger.e("HeartbeatWorker", "Failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "guard_heartbeat"

        fun schedule(ctx: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(
                    Constants.HEARTBEAT_INTERVAL_MIN, TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
                Logger.d("HeartbeatWorker", "Scheduled every ${Constants.HEARTBEAT_INTERVAL_MIN} min")
            } catch (e: Exception) {
                Logger.e("HeartbeatWorker", "schedule failed", e)
            }
        }

        fun cancel(ctx: Context) {
            try {
                WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME)
            } catch (_: Exception) {}
        }
    }
}
