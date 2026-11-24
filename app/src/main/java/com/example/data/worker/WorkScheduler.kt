package com.example.data.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun scheduleDailyExpiryCheck() {
        val request = PeriodicWorkRequestBuilder<ExpiryNotificationWorker>(12, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun triggerImmediateExpiryCheck() {
        val request = OneTimeWorkRequestBuilder<ExpiryNotificationWorker>()
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_IMMEDIATE_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun schedulePeriodicUpdateCheck() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<AppUpdateCheckWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_UPDATE_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancelPeriodicUpdateCheck() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC_UPDATE_WORK)
    }

    companion object {
        private const val UNIQUE_PERIODIC_WORK = "pantrymaster_periodic_expiry_check"
        private const val UNIQUE_IMMEDIATE_WORK = "pantrymaster_immediate_expiry_check"
        const val UNIQUE_PERIODIC_UPDATE_WORK = "pantrymaster_periodic_update_check"
    }
}

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val request = PeriodicWorkRequestBuilder<ExpiryNotificationWorker>(12, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "pantrymaster_periodic_expiry_check",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<ExpiryNotificationWorker>().build()
            )
            val updateConstraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val updateRequest = PeriodicWorkRequestBuilder<AppUpdateCheckWorker>(24, TimeUnit.HOURS)
                .setConstraints(updateConstraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WorkScheduler.UNIQUE_PERIODIC_UPDATE_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                updateRequest
            )
        }
    }
}
