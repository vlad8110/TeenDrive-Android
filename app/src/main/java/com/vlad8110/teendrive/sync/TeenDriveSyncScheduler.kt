package com.vlad8110.teendrive.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object TeenDriveSyncScheduler {
    private const val PERIODIC_SYNC_NAME = "teendrive_periodic_cloud_sync"
    private const val IMMEDIATE_SYNC_NAME = "teendrive_immediate_cloud_sync"

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<CloudSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints)
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(
                PERIODIC_SYNC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
    }

    fun requestNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setConstraints(networkConstraints)
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(
                IMMEDIATE_SYNC_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
    }
}
