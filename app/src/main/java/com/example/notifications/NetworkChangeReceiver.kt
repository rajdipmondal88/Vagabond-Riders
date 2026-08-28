package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Listens for network reconnect events (WiFi or Cellular data coming online).
 * The instant internet access is detected on the device, pending notifications are fetched immediately.
 */
class NetworkChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NetworkChangeReceiver"

        fun isOnline(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm.activeNetwork ?: return false
                val capabilities = cm.getNetworkCapabilities(network) ?: return false
                return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } else {
                @Suppress("DEPRECATION")
                val activeNetworkInfo = cm.activeNetworkInfo
                @Suppress("DEPRECATION")
                return activeNetworkInfo != null && activeNetworkInfo.isConnected
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (!isOnline(context)) return

        val pendingResult = goAsync()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "VagabondRiders:NetworkChangeSyncWakeLock"
        )?.apply {
            acquire(10_000L)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                PushNotificationManager.init(context)
                PushNotificationManager.checkPhpBackendForNotifications(context)
                PushAlarmReceiver.scheduleNextAlarm(context, 15_000L)
            } catch (e: Exception) {
                Log.e(TAG, "Failed syncing notifications on network reconnect: ${e.message}")
            } finally {
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (_: Exception) {}
                pendingResult.finish()
            }
        }
    }
}
