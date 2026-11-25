package com.example.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class AppUpdateInstallReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReceiverEntryPoint {
        fun inAppUpdateManager(): InAppUpdateManager
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != InAppUpdateManager.ACTION_INSTALL_COMMIT) return

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ReceiverEntryPoint::class.java
        )
        val manager = entryPoint.inAppUpdateManager()

        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE
        )
        val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val confirmIntent = IntentCompat.getParcelableExtra(
            intent,
            Intent.EXTRA_INTENT,
            Intent::class.java
        )
        val tagName = intent.getStringExtra(InAppUpdateManager.EXTRA_UPDATE_TAG)

        manager.handleInstallStatus(
            status = status,
            statusMessage = statusMessage,
            confirmIntent = confirmIntent,
            tagName = tagName
        )
    }
}
