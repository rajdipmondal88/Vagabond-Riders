package com.example.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.utils.CustomLogoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit

data class PushMessage(
    val id: String,
    val title: String,
    val message: String,
    val imageUrl: String? = null,
    val targetUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

object PushNotificationManager {

    private const val TAG = "VRPushManager"
    const val CHANNEL_ID = "vr_php_push_channel"
    private const val PREFS_NAME = "vr_push_preferences"
    private const val KEY_DEVICE_TOKEN = "key_device_push_token"
    private const val KEY_PHP_ENDPOINT = "key_php_endpoint"
    private const val KEY_LAST_NOTIFICATION_ID = "key_last_notification_id"

    const val OFFICIAL_LOGO_URL = "https://app.vagabondriders.com/logo.png"
    const val DEFAULT_PHP_ENDPOINT = "https://app.vagabondriders.com/api/notifications.php"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var pollingJob: Job? = null

    private val _deviceToken = MutableStateFlow("")
    val deviceToken: StateFlow<String> = _deviceToken.asStateFlow()

    private val _phpEndpointUrl = MutableStateFlow(DEFAULT_PHP_ENDPOINT)
    val phpEndpointUrl: StateFlow<String> = _phpEndpointUrl.asStateFlow()

    private val _notificationsHistory = MutableStateFlow<List<PushMessage>>(emptyList())
    val notificationsHistory: StateFlow<List<PushMessage>> = _notificationsHistory.asStateFlow()

    private val _isPollingActive = MutableStateFlow(false)
    val isPollingActive: StateFlow<Boolean> = _isPollingActive.asStateFlow()

    fun init(context: Context) {
        createNotificationChannel(context)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var token = prefs.getString(KEY_DEVICE_TOKEN, null)
        if (token.isNullOrBlank()) {
            token = "VR-DEV-" + UUID.randomUUID().toString().substring(0, 13).uppercase()
            prefs.edit().putString(KEY_DEVICE_TOKEN, token).apply()
        }
        _deviceToken.value = token

        var endpoint = prefs.getString(KEY_PHP_ENDPOINT, DEFAULT_PHP_ENDPOINT) ?: DEFAULT_PHP_ENDPOINT
        // Automatically migrate if previous endpoint was the membership subdomain
        if (endpoint.contains("membership.vagabondriders.com/api/notifications.php") || endpoint.isBlank()) {
            endpoint = DEFAULT_PHP_ENDPOINT
            prefs.edit().putString(KEY_PHP_ENDPOINT, DEFAULT_PHP_ENDPOINT).apply()
        }
        _phpEndpointUrl.value = endpoint

        // Start both in-process high-frequency loop and AlarmManager background wake loop
        startPeriodicPushSync(context)
        PushAlarmReceiver.scheduleNextAlarm(context, 5_000L)

        // Preload official logo in background for fast notification rendering
        scope.launch {
            CustomLogoManager.init(context)
        }
    }

    fun clearNotificationsHistory() {
        _notificationsHistory.value = emptyList()
    }

    fun setPhpEndpoint(context: Context, newEndpoint: String) {
        if (newEndpoint.isNotBlank()) {
            _phpEndpointUrl.value = newEndpoint.trim()
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PHP_ENDPOINT, newEndpoint.trim())
                .apply()

            // Trigger immediate sync
            scope.launch {
                checkPhpBackendForNotifications(context)
            }
        }
    }

    fun regenerateDeviceToken(context: Context): String {
        val newToken = "VR-DEV-" + UUID.randomUUID().toString().substring(0, 13).uppercase()
        _deviceToken.value = newToken
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DEVICE_TOKEN, newToken)
            .apply()
        return newToken
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Vagabond Riders Push Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Live trip announcements, route updates, and portal notifications from PHP backend"
                enableLights(true)
                lightColor = Color.parseColor("#EA580C")
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * Downloads an image from a URL into a Bitmap for display in push notifications.
     */
    private fun downloadBitmap(imageUrl: String?): Bitmap? {
        if (imageUrl.isNullOrBlank()) return null
        return try {
            val url = URL(imageUrl.trim())
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "VRAndroidPushClient/2.0")
            if (connection.responseCode in 200..299) {
                val input: InputStream = connection.inputStream
                val bitmap = BitmapFactory.decodeStream(input)
                input.close()
                connection.disconnect()
                bitmap
            } else {
                connection.disconnect()
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to download push image: ${e.message}")
            null
        }
    }

    /**
     * Shows a local rich native Android push notification with:
     * - Logo on the left (Large Icon loaded from https://app.vagabondriders.com/logo.png)
     * - Title and message text
     * - Optional attached image / banner down below the message (BigPictureStyle)
     */
    fun displayNotification(
        context: Context,
        title: String,
        body: String,
        targetUrl: String? = null,
        imageUrl: String? = null,
        notificationId: Int = (System.currentTimeMillis() % 100000).toInt()
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    if (!targetUrl.isNullOrBlank()) {
                        putExtra("extra_target_url", targetUrl)
                        data = Uri.parse(targetUrl)
                    }
                }

                val pendingIntent = PendingIntent.getActivity(
                    context,
                    notificationId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
                )

                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                // 1. Load official Vagabond Riders Logo for the left-side Large Icon
                var logoBitmap = CustomLogoManager.getOfficialLogoBitmap(context)
                if (logoBitmap == null) {
                    logoBitmap = downloadBitmap(OFFICIAL_LOGO_URL)
                }

                // 2. Download attached content image if present
                val contentImageBitmap = if (!imageUrl.isNullOrBlank()) {
                    downloadBitmap(imageUrl)
                } else null

                val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setSound(soundUri)
                    .setColor(Color.parseColor("#EA580C"))
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)

                // Set Logo on the left side
                if (logoBitmap != null) {
                    notificationBuilder.setLargeIcon(logoBitmap)
                }

                // If an image is provided, display down below using BigPictureStyle
                if (contentImageBitmap != null) {
                    val bigPicStyle = NotificationCompat.BigPictureStyle()
                        .bigPicture(contentImageBitmap)
                        .setBigContentTitle(title)
                        .setSummaryText(body)

                    // Keep the logo visible in the header when expanded
                    if (logoBitmap != null) {
                        bigPicStyle.bigLargeIcon(logoBitmap)
                    }

                    notificationBuilder.setStyle(bigPicStyle)
                } else {
                    notificationBuilder.setStyle(
                        NotificationCompat.BigTextStyle()
                            .bigText(body)
                            .setBigContentTitle(title)
                    )
                }

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.notify(notificationId, notificationBuilder.build())

                // Append to local history
                val newMsg = PushMessage(
                    id = notificationId.toString(),
                    title = title,
                    message = body,
                    imageUrl = imageUrl,
                    targetUrl = targetUrl
                )
                _notificationsHistory.value = listOf(newMsg) + _notificationsHistory.value
            } catch (e: Exception) {
                Log.e(TAG, "Error rendering rich notification: ${e.message}", e)
            }
        }
    }

    /**
     * Starts continuous periodic polling of the PHP backend to receive new notifications quickly.
     */
    fun startPeriodicPushSync(context: Context) {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            _isPollingActive.value = true
            while (isActive) {
                try {
                    checkPhpBackendForNotifications(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Push sync error: ${e.message}")
                }
                // High-frequency polling (every 10 seconds) when app is alive
                delay(10_000)
            }
        }
    }

    /**
     * Polls the PHP backend for new notifications for this device.
     */
    suspend fun checkPhpBackendForNotifications(context: Context) = withContext(Dispatchers.IO) {
        val endpoint = _phpEndpointUrl.value
        if (endpoint.isBlank()) return@withContext

        val token = _deviceToken.value
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastId = prefs.getString(KEY_LAST_NOTIFICATION_ID, "0") ?: "0"

        try {
            val urlBuilder = Uri.parse(endpoint).buildUpon()
                .appendQueryParameter("action", "get_notifications")
                .appendQueryParameter("device_token", token)
                .appendQueryParameter("last_id", lastId)
                .appendQueryParameter("package", context.packageName)
                .build()

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("User-Agent", "VRAndroidPushClient/2.0")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string() ?: ""
                    parseAndShowPushNotifications(context, jsonStr)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "PHP push poll request completed: ${e.localizedMessage}")
        }
    }

    /**
     * Sends device registration to PHP backend so PHP can send targeted pushes.
     */
    suspend fun registerDeviceWithPhp(context: Context, userIdentifier: String = ""): Boolean = withContext(Dispatchers.IO) {
        val endpoint = _phpEndpointUrl.value
        val token = _deviceToken.value
        try {
            val json = JSONObject().apply {
                put("action", "register_device")
                put("device_token", token)
                put("user_id", userIdentifier)
                put("device_model", "${Build.MANUFACTURER} ${Build.MODEL}")
                put("os_version", "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                put("timestamp", System.currentTimeMillis())
            }

            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(endpoint)
                .header("User-Agent", "VRAndroidPushClient/2.0")
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (_: Exception) {
            return@withContext false
        }
    }

    private fun parseAndShowPushNotifications(context: Context, responseJson: String) {
        if (responseJson.isBlank()) return
        try {
            val root = JSONObject(responseJson)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            // Supports single notification or list
            val notificationsArray: JSONArray? = if (root.has("notifications")) {
                root.optJSONArray("notifications")
            } else if (root.has("messages")) {
                root.optJSONArray("messages")
            } else if (root.has("title") && (root.has("message") || root.has("body"))) {
                JSONArray().put(root)
            } else null

            if (notificationsArray != null) {
                var maxId = prefs.getString(KEY_LAST_NOTIFICATION_ID, "0") ?: "0"

                for (i in 0 until notificationsArray.length()) {
                    val item = notificationsArray.optJSONObject(i) ?: continue
                    val id = item.optString("id", UUID.randomUUID().toString())
                    val title = item.optString("title", "Vagabond Riders Alert")
                    val message = item.optString("message", item.optString("body", ""))
                    val targetUrl = item.optString("target_url", item.optString("url", item.optString("link", "")))

                    // Extract image from any commonly used field name
                    val imageUrl = item.optString(
                        "image_url",
                        item.optString("image", item.optString("banner", item.optString("photo", item.optString("img", item.optString("picture", "")))))
                    )

                    if (message.isNotBlank()) {
                        displayNotification(
                            context = context,
                            title = title,
                            body = message,
                            targetUrl = targetUrl.ifBlank { null },
                            imageUrl = imageUrl.ifBlank { null },
                            notificationId = (id.hashCode() and 0x7FFFFFFF)
                        )
                        maxId = id
                    }
                }

                prefs.edit().putString(KEY_LAST_NOTIFICATION_ID, maxId).apply()
            }
        } catch (_: Exception) {
            // Non-JSON or standard web page response ignored
        }
    }
}

/**
 * Javascript Interface injected into WebView as `window.AndroidPush` and `window.VagabondPush`
 * allowing PHP web pages and front-end scripts to interact directly with Push Notifications.
 */
class VRPushJavascriptBridge(private val context: Context) {

    @JavascriptInterface
    fun getDeviceToken(): String {
        return PushNotificationManager.deviceToken.value
    }

    @JavascriptInterface
    fun showNotification(title: String, message: String, targetUrl: String? = null) {
        PushNotificationManager.displayNotification(
            context = context,
            title = title.ifBlank { "Vagabond Riders Alert" },
            body = message,
            targetUrl = targetUrl?.ifBlank { null },
            imageUrl = null
        )
    }

    @JavascriptInterface
    fun showRichNotification(title: String, message: String, imageUrl: String? = null, targetUrl: String? = null) {
        PushNotificationManager.displayNotification(
            context = context,
            title = title.ifBlank { "Vagabond Riders Alert" },
            body = message,
            targetUrl = targetUrl?.ifBlank { null },
            imageUrl = imageUrl?.ifBlank { null }
        )
    }

    @JavascriptInterface
    fun registerDevice(userIdentifier: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val success = PushNotificationManager.registerDeviceWithPhp(context, userIdentifier)
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(context, "Device registered for push notifications", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @JavascriptInterface
    fun setPushEndpoint(url: String) {
        if (url.isNotBlank()) {
            PushNotificationManager.setPhpEndpoint(context, url)
        }
    }

    @JavascriptInterface
    fun syncNow() {
        CoroutineScope(Dispatchers.IO).launch {
            PushNotificationManager.checkPhpBackendForNotifications(context)
        }
    }
}
