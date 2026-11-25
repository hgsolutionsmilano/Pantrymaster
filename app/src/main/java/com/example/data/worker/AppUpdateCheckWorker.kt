package com.example.data.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.local.dao.SettingsDao
import com.example.data.local.entity.SettingsEntity
import com.example.data.repository.AppUpdateRepository
import com.example.data.repository.AppUpdateStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class AppUpdateCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val appUpdateRepository: AppUpdateRepository,
    private val settingsDao: SettingsDao
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val autoEnabled = appUpdateRepository.autoUpdateEnabled.first()
        if (!autoEnabled) return Result.success()

        val result = appUpdateRepository.checkForUpdates(force = false)
        if (result is AppUpdateStatus.UpdateAvailable) {
            val settings = settingsDao.getSettings() ?: SettingsEntity(id = 1)
            val info = result.info
            if (settings.notificationsEnabled &&
                appUpdateRepository.shouldNotifyForTag(info.tagName) &&
                canPostNotifications()
            ) {
                ensureChannel()
                postUpdateNotification(info.latestVersion, info.releaseTitle)
                appUpdateRepository.markNotifiedForTag(info.tagName)
            }
        }
        return Result.success()
    }

    private fun canPostNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.notification_channel_updates_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = applicationContext.getString(R.string.notification_channel_updates_desc)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun postUpdateNotification(latestVersion: String, releaseTitle: String) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            2001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_pantry)
            .setContentTitle("Aggiornamento PantryMaster v$latestVersion")
            .setContentText("Disponibile nuova release ($releaseTitle). Tocca per aprire l'app.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "pantrymaster_update_alerts"
        private const val NOTIFICATION_ID = 4099
    }
}
