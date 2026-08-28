package com.example.notifications

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Background WorkManager Worker ensuring notifications are reliably fetched and displayed
 * even when the application has been completely closed or swiped away from recent apps.
 * Automatically triggers whenever the device connects to the internet.
 */
class PushNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "PushNotificationWorker"
        private const val PERIODIC_WORK_NAME = "vr_push_notification_periodic_sync"
        private const val ONE_TIME_WORK_NAME = "vr_push_notification_immediate_sync"

        /**
         * Schedules recurring background polling with network connectivity requirement.
         * Runs automatically even when app is closed.
         */
        fun schedulePeriodic(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val periodicRequest = PeriodicWorkRequestBuilder<PushNotificationWorker>(
                    15, TimeUnit.MINUTES,
                    5, TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .addTag("push_sync")
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    periodicRequest
                )
                Log.d(TAG, "Periodic push notification worker scheduled successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule periodic push worker: ${e.message}")
            }
        }

        /**
         * Enqueues immediate one-time sync as soon as network connectivity is available.
         */
        fun triggerExpeditedOneTime(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val oneTimeRequest = OneTimeWorkRequestBuilder<PushNotificationWorker>()
                    .setConstraints(constraints)
                    .addTag("push_immediate")
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    ONE_TIME_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    oneTimeRequest
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enqueue one-time push worker: ${e.message}")
            }
        }
    }

    override suspend fun doWork(): Result {
        return try {
            PushNotificationManager.init(applicationContext)
            PushNotificationManager.checkPhpBackendForNotifications(applicationContext)
            // Also ensure next high-frequency AlarmManager tick is armed
            PushAlarmReceiver.scheduleNextAlarm(applicationContext, 20_000L)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "PushNotificationWorker execution error: ${e.message}")
            Result.retry()
        }
    }
}
