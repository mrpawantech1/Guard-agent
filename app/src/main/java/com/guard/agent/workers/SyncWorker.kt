package com.guard.agent.workers

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.guard.agent.core.DeviceKey
import com.guard.agent.helpers.data.BatteryHelper
import com.guard.agent.helpers.data.NetworkHelper
import com.guard.agent.security.OfflineQueueManager
import com.guard.agent.utils.Logger
import java.util.concurrent.TimeUnit

class SyncWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        return try {
            val ctx = applicationContext
            val key = DeviceKey.get(ctx)

            // Periodic data sync
            BatteryHelper.push(ctx, key)
            NetworkHelper.push(ctx, key)

            // Offline commands flush
            OfflineQueueManager.flush(ctx, key)

            Logger.d("SyncWorker", "Sync complete")
            Result.success()
        } catch (e: Exception) {
            Logger.e("SyncWorker", "Failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "guard_sync"

        fun schedule(ctx: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = PeriodicWorkRequestBuilder<SyncWorker>(
                    30, TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
                Logger.d("SyncWorker", "Scheduled every 30 min")
            } catch (e: Exception) {
                Logger.e("SyncWorker", "schedule failed", e)
            }
        }

        fun cancel(ctx: Context) {
            try {
                WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME)
            } catch (_: Exception) {}
        }
    }
}
