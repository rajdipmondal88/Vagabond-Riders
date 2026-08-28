package com.example

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import com.example.notifications.PushAlarmReceiver
import com.example.notifications.PushNotificationManager
import com.example.notifications.PushNotificationWorker
import com.example.utils.CustomLogoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Main Application class for Vagabond Riders Portal.
 * Initializes persistent push notification daemon, background alarm loops,
 * network connectivity listeners, and asset prefetching on cold startup.
 */
class VRApplication : Application() {

    companion object {
        private const val TAG = "VRApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VRApplication initializing background services...")

        // 1. Initialize Push Notification Manager and channel
        PushNotificationManager.init(this)

        // 2. Schedule persistent background Alarm loop (runs even when closed)
        PushAlarmReceiver.scheduleNextAlarm(this, 1000L)

        // 3. Schedule WorkManager periodic worker (guaranteed Android OS background sync)
        PushNotificationWorker.schedulePeriodic(this)

        // 4. Register system network monitor to trigger notification check as soon as internet connects
        registerNetworkCallback()

        // 5. Prefetch official brand logo in background
        CustomLogoManager.init(this)
    }

    private fun registerNetworkCallback() {
        try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return

            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager.registerNetworkCallback(
                networkRequest,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        Log.d(TAG, "Internet connection detected -> fetching pending push notifications...")
                        CoroutineScope(Dispatchers.IO).launch {
                            PushNotificationManager.checkPhpBackendForNotifications(this@VRApplication)
                            PushAlarmReceiver.scheduleNextAlarm(this@VRApplication, 5000L)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.d(TAG, "Error registering network callback: ${e.message}")
        }
    }
}
