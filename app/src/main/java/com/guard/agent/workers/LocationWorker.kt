package com.guard.agent.workers

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.guard.agent.core.DeviceKey
import com.guard.agent.helpers.location.LocationHelper
import com.guard.agent.helpers.location.LocationHistoryHelper
import com.guard.agent.utils.Logger
import java.util.concurrent.TimeUnit

class LocationWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        return try {
            val ctx = applicationContext
            val key = DeviceKey.get(ctx)

            // Backup location push (agar GuardService kill ho gaya ho)
            LocationHelper.sendOnce(ctx, key)

            // Purani history cleanup (30 din se zyada)
            LocationHistoryHelper.cleanup(ctx, key)

            Logger.d("LocationWorker", "Location pushed")
            Result.success()
        } catch (e: Exception) {
            Logger.e("LocationWorker", "Failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "guard_location"

        fun schedule(ctx: Context) {
            try {
                val constraints = Constraints.Builder().build()

                val request = PeriodicWorkRequestBuilder<LocationWorker>(
                    20, TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
                Logger.d("LocationWorker", "Scheduled every 20 min")
            } catch (e: Exception) {
                Logger.e("LocationWorker", "schedule failed", e)
            }
        }

        fun cancel(ctx: Context) {
            try {
                WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME)
            } catch (_: Exception) {}
        }
    }
}
