package com.example.notifications

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Background Alarm Receiver that ensures push notifications continue to arrive fast
 * and reliably even when the app is minimized, in background, or in Android Doze mode.
 */
class PushAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "VRPushAlarmReceiver"
        private const val ALARM_REQUEST_CODE = 4040
        const val ACTION_SYNC_PUSH = "com.example.notifications.ACTION_SYNC_PUSH"
        private const val SYNC_INTERVAL_MS = 15_000L // 15 seconds high-frequency check

        @SuppressLint("ScheduleExactAlarm")
        fun scheduleNextAlarm(context: Context, delayMs: Long = SYNC_INTERVAL_MS) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, PushAlarmReceiver::class.java).apply {
                action = ACTION_SYNC_PUSH
            }

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            val pendingIntent = PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags)

            val triggerAtMillis = System.currentTimeMillis() + delayMs

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } catch (e: Exception) {
                // If exact alarm permission is restricted on Android 12+, fallback to setAndAllowWhileIdle
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                    } else {
                        alarmManager.set(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                    }
                } catch (ex: Exception) {
                    Log.e(TAG, "Error scheduling push alarm: ${ex.message}")
                }
            }
        }

        fun cancelAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, PushAlarmReceiver::class.java).apply {
                action = ACTION_SYNC_PUSH
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            val pendingIntent = PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags)
            alarmManager.cancel(pendingIntent)
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED || action == ACTION_SYNC_PUSH) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "VagabondRiders:PushSyncWakeLock"
            )?.apply {
                acquire(10_000L) // Hold wake lock for up to 10 seconds to fetch notifications
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    PushNotificationManager.checkPhpBackendForNotifications(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Alarm push check failed: ${e.message}")
                } finally {
                    try {
                        if (wakeLock?.isHeld == true) {
                            wakeLock.release()
                        }
                    } catch (_: Exception) {}

                    // Schedule next check
                    scheduleNextAlarm(context)
                }
            }
        }
    }
}
