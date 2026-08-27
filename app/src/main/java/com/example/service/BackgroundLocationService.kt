package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.location.BackgroundLocationManager
import com.example.location.LocationData
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class BackgroundLocationService : Service() {

    companion object {
        const val CHANNEL_ID = "vr_background_location_channel"
        const val NOTIFICATION_ID = 9001

        const val ACTION_START = "com.example.action.START_LOCATION_SERVICE"
        const val ACTION_STOP = "com.example.action.STOP_LOCATION_SERVICE"

        fun startService(context: Context) {
            val intent = Intent(context, BackgroundLocationService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, BackgroundLocationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var locationManager: LocationManager? = null
    private var nativeLocationListener: LocationListener? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var notificationManager: NotificationManager? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "VagabondRiders:LiveNavigationWakeLock"
        )?.apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopLocationUpdates()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                BackgroundLocationManager.updateServiceStatus(false)
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                startForegroundWithNotification("Initializing live navigation...")
                acquireWakeLock()
                startLocationUpdates()
                BackgroundLocationManager.updateServiceStatus(true)
            }
        }
        return START_STICKY
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24 hours max
            }
        } catch (_: Exception) { }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) { }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Vagabond Riders Live Navigation & Background GPS",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps navigation and GPS tracking active in background and on lock screen"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithNotification(contentText: String) {
        val notification = buildNotification(contentText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val stopIntent = Intent(this, BackgroundLocationService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vagabond Riders – Navigation Active")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Navigation", stopPendingIntent)
            .build()
    }

    private fun updateNotification(locData: LocationData) {
        val speedStr = if (locData.speedKmh > 0.5f) " • Speed: ${locData.formattedSpeed}" else ""
        val text = "GPS: ${locData.formattedCoordinates}$speedStr (Always On)"
        val updatedNotification = buildNotification(text)
        notificationManager?.notify(NOTIFICATION_ID, updatedNotification)
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        // Try Google Play Services Fused Location Provider first
        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
                .setMinUpdateIntervalMillis(1500L)
                .setMinUpdateDistanceMeters(0.5f)
                .setWaitForAccurateLocation(false)
                .build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location = result.lastLocation ?: return
                    handleNewLocation(location)
                }
            }

            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                locationCallback as LocationCallback,
                Looper.getMainLooper()
            )
        } catch (_: Exception) {
            // Fallback to Native LocationManager
            startNativeLocationUpdates()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startNativeLocationUpdates() {
        try {
            locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            nativeLocationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    handleNewLocation(location)
                }
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            val providers = listOfNotNull(
                if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) LocationManager.GPS_PROVIDER else null,
                if (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) LocationManager.NETWORK_PROVIDER else null
            )

            for (p in providers) {
                locationManager?.requestLocationUpdates(
                    p,
                    3000L,
                    0.5f,
                    nativeLocationListener as LocationListener,
                    Looper.getMainLooper()
                )
            }
        } catch (_: Exception) { }
    }

    private fun handleNewLocation(location: Location) {
        val speedKmh = (location.speed * 3.6f)
        val data = LocationData(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            speed = location.speed,
            speedKmh = speedKmh,
            bearing = location.bearing,
            accuracy = location.accuracy,
            timestamp = location.time,
            provider = location.provider ?: "gps"
        )

        BackgroundLocationManager.updateLocation(data)
        updateNotification(data)
    }

    private fun stopLocationUpdates() {
        try {
            locationCallback?.let {
                fusedLocationClient?.removeLocationUpdates(it)
            }
            locationCallback = null
        } catch (_: Exception) { }

        try {
            nativeLocationListener?.let {
                locationManager?.removeUpdates(it)
            }
            nativeLocationListener = null
        } catch (_: Exception) { }

        releaseWakeLock()
    }

    override fun onDestroy() {
        stopLocationUpdates()
        BackgroundLocationManager.updateServiceStatus(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
