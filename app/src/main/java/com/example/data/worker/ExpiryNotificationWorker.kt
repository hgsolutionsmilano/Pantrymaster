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
import androidx.work.workDataOf
import com.example.MainActivity
import com.example.R
import com.example.data.local.dao.ProductBatchDao
import com.example.data.local.dao.ReminderDao
import com.example.data.local.dao.SettingsDao
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.SettingsEntity
import com.example.data.preferences.UserPreferencesDataStore
import com.example.domain.model.ExpiryType
import com.example.domain.usecase.ExpiryCalculator
import com.example.domain.usecase.PantryDateUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.UUID

@HiltWorker
class ExpiryNotificationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val batchDao: ProductBatchDao,
    private val reminderDao: ReminderDao,
    private val settingsDao: SettingsDao,
    private val preferencesDataStore: UserPreferencesDataStore
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val isManual = inputData.getBoolean(KEY_MANUAL_TRIGGER, false)
        val settings = settingsDao.getSettings() ?: SettingsEntity(id = 1)
        if (!settings.notificationsEnabled && !isManual) {
            return Result.success(workDataOf(KEY_NOTIFIED_COUNT to 0))
        }

        val enabledThresholds = buildList {
            if (settings.notify7Days) add(7)
            if (settings.notify3Days) add(3)
            if (settings.notify1Day) add(1)
            if (settings.notify0Days) add(0)
        }
        if (enabledThresholds.isEmpty()) {
            return Result.success(workDataOf(KEY_NOTIFIED_COUNT to 0))
        }

        ensureNotificationChannel(applicationContext)

        val today = PantryDateUtils.todayStartMillis()
        val batches = batchDao.getActiveBatchesWithExpiry()
        val pendingAlerts = mutableListOf<Pair<String, String>>()

        for (item in batches) {
            val batch = item.batch
            val product = item.product ?: continue
            val expiryDate = batch.expiryDate ?: continue
            val matchedThreshold = ExpiryCalculator.matchingThreshold(
                expiryDateMillis = expiryDate,
                todayMillis = today,
                enabledThresholds = enabledThresholds
            ) ?: continue

            val existing = reminderDao.getForBatchAndThreshold(batch.id, matchedThreshold)
            if (!isManual && (existing?.notifiedAt != null || existing?.isDismissed == true)) {
                continue
            }

            val daysRemaining = PantryDateUtils.daysBetween(today, expiryDate).toInt()
            val dateKind = if (batch.expiryType == ExpiryType.BEST_BEFORE) {
                "TMC"
            } else {
                "Scadenza"
            }
            val whenLabel = when {
                daysRemaining < -1 -> "superato da ${-daysRemaining} giorni"
                daysRemaining == -1 -> "superato ieri"
                daysRemaining == 0 -> "oggi"
                daysRemaining == 1 -> "domani"
                else -> "tra $daysRemaining giorni"
            }
            pendingAlerts.add(
                product.name to "$dateKind $whenLabel (${PantryDateUtils.formatShortDate(expiryDate)})"
            )

            reminderDao.upsert(
                ReminderEntity(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    batchId = batch.id,
                    thresholdDays = matchedThreshold,
                    scheduledDate = today,
                    notifiedAt = System.currentTimeMillis(),
                    isDismissed = false
                )
            )
        }

        val permitted = canPostNotifications(applicationContext)
        if (pendingAlerts.isNotEmpty() && permitted) {
            sendGroupedOrSingleNotification(pendingAlerts)
        }

        preferencesDataStore.setLastNotificationCheck(System.currentTimeMillis())
        return Result.success(
            workDataOf(
                KEY_NOTIFIED_COUNT to pendingAlerts.size,
                KEY_PERMISSION_GRANTED to permitted
            )
        )
    }

    private fun sendGroupedOrSingleNotification(alerts: List<Pair<String, String>>) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (alerts.size == 1) {
            "Promemoria: ${alerts.first().first}"
        } else {
            "${alerts.size} alimenti prossimi alla scadenza o TMC"
        }
        val content = if (alerts.size == 1) {
            alerts.first().second
        } else {
            alerts.take(3).joinToString(" • ") { it.first }
        }

        val inboxStyle = NotificationCompat.InboxStyle().setBigContentTitle(title)
        alerts.take(6).forEach { (name, desc) ->
            inboxStyle.addLine("$name: $desc")
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_pantry)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "pantrymaster_expiry_alerts"
        const val KEY_MANUAL_TRIGGER = "manual_trigger"
        const val KEY_NOTIFIED_COUNT = "notified_count"
        const val KEY_PERMISSION_GRANTED = "permission_granted"
        private const val NOTIFICATION_ID = 4021

        fun canPostNotifications(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED &&
                    NotificationManagerCompat.from(context).areNotificationsEnabled()
            } else {
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_expiry_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.notification_channel_expiry_desc)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}
