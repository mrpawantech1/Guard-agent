package com.guard.agent.workers

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.guard.agent.security.SelfUpdateManager
import com.guard.agent.utils.Logger
import java.util.concurrent.TimeUnit

class UpdateCheckWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        return try {
            SelfUpdateManager.checkAndUpdate(applicationContext)
            Logger.d("UpdateCheckWorker", "Update check complete")
            Result.success()
        } catch (e: Exception) {
            Logger.e("UpdateCheckWorker", "Failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "guard_update"

        fun schedule(ctx: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .build()

                val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                    6, TimeUnit.HOURS
                )
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
                Logger.d("UpdateCheckWorker", "Scheduled every 6 hours")
            } catch (e: Exception) {
                Logger.e("UpdateCheckWorker", "schedule failed", e)
            }
        }

        fun cancel(ctx: Context) {
            try {
                WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME)
            } catch (_: Exception) {}
        }
    }
}
