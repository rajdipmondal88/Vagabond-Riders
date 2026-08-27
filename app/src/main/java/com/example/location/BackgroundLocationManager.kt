package com.example.location

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.webkit.WebView
import androidx.core.content.ContextCompat
import com.example.service.BackgroundLocationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

object BackgroundLocationManager {

    const val LOCATION_TRIGGER_URL = "https://app.vagabondriders.com/location/index.php"

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _currentLocation = MutableStateFlow<LocationData?>(null)
    val currentLocation: StateFlow<LocationData?> = _currentLocation.asStateFlow()

    private val _hasFineLocationPermission = MutableStateFlow(false)
    val hasFineLocationPermission: StateFlow<Boolean> = _hasFineLocationPermission.asStateFlow()

    private val _hasBackgroundLocationPermission = MutableStateFlow(false)
    val hasBackgroundLocationPermission: StateFlow<Boolean> = _hasBackgroundLocationPermission.asStateFlow()

    private val _isGpsEnabled = MutableStateFlow(false)
    val isGpsEnabled: StateFlow<Boolean> = _isGpsEnabled.asStateFlow()

    private val _activeLocationUrl = MutableStateFlow<String?>(null)
    val activeLocationUrl: StateFlow<String?> = _activeLocationUrl.asStateFlow()

    fun init(context: Context) {
        refreshPermissionStates(context)
        // GPS does not start unconditionally on launch anymore.
        // It starts ONLY when the user visits LOCATION_TRIGGER_URL.
    }

    /**
     * Checks if the given URL matches the designated location tracking URL.
     * Matches:
     * - https://app.vagabondriders.com/location/index.php
     * - http://app.vagabondriders.com/location/index.php
     * - Any query parameters or anchors on /location/index.php
     * - Any page under /location/
     */
    fun isLocationTriggerUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase().trim()
        return lower.contains("membership.vagabondriders.com/location") ||
                lower.contains("app.vagabondriders.com/location") ||
                lower.contains("/location/index.php") ||
                lower.contains("/location/") ||
                lower.startsWith("https://membership.vagabondriders.com/location") ||
                lower.startsWith("http://membership.vagabondriders.com/location") ||
                lower.startsWith("https://app.vagabondriders.com/location") ||
                lower.startsWith("http://app.vagabondriders.com/location")
    }

    /**
     * Called whenever active page URL changes.
     * Starts GPS background service if visiting the location URL.
     * Stops GPS background service if navigating away from the location URL.
     */
    fun onActiveUrlChanged(context: Context, newUrl: String?) {
        _activeLocationUrl.value = newUrl
        val isTarget = isLocationTriggerUrl(newUrl)
        if (isTarget) {
            if (!_isServiceRunning.value) {
                startService(context)
            }
        } else {
            if (_isServiceRunning.value) {
                stopService(context)
            }
        }
    }

    fun refreshPermissionStates(context: Context) {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        _hasFineLocationPermission.value = fineGranted || coarseGranted

        val bgGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            fineGranted || coarseGranted
        }
        _hasBackgroundLocationPermission.value = bgGranted

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        _isGpsEnabled.value = lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }

    fun startService(context: Context) {
        refreshPermissionStates(context)
        if (_hasFineLocationPermission.value) {
            BackgroundLocationService.startService(context)
            _isServiceRunning.value = true
        }
    }

    fun stopService(context: Context) {
        BackgroundLocationService.stopService(context)
        _isServiceRunning.value = false
        _currentLocation.value = null
    }

    fun toggleService(context: Context) {
        if (_isServiceRunning.value) {
            stopService(context)
        } else {
            startService(context)
        }
    }

    fun updateServiceStatus(running: Boolean) {
        _isServiceRunning.value = running
        if (!running) {
            _currentLocation.value = null
        }
    }

    fun updateLocation(data: LocationData) {
        _currentLocation.value = data
    }

    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    fun openLocationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            openAppSettings(context)
        }
    }

    /**
     * Injects updated GPS coordinates directly into the active WebView
     * to keep web-based map navigation continuously updated.
     */
    fun injectLocationIntoWebView(webView: WebView?, locationData: LocationData) {
        if (webView == null) return
        val json = JSONObject().apply {
            put("latitude", locationData.latitude)
            put("longitude", locationData.longitude)
            put("altitude", locationData.altitude)
            put("speed", locationData.speed)
            put("speedKmh", locationData.speedKmh)
            put("heading", locationData.bearing)
            put("accuracy", locationData.accuracy)
            put("timestamp", locationData.timestamp)
        }.toString()

        val js = """
            (function() {
                try {
                    window._vrCurrentLocation = $json;
                    var event = new CustomEvent('vrLocationUpdate', { detail: $json });
                    window.dispatchEvent(event);
                    if (window.onVRLocationUpdate) {
                        window.onVRLocationUpdate($json);
                    }
                } catch(e) {}
            })();
        """.trimIndent()

        webView.post {
            webView.evaluateJavascript(js, null)
        }
    }
}
