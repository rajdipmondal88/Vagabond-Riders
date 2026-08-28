package com.example.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class Available(
        val fileSizeBytes: Long,
        val formattedSize: String,
        val serverLastModified: String?
    ) : UpdateState()
    object UpToDate : UpdateState()
    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val progress: Float,
        val percent: Int,
        val speedText: String
    ) : UpdateState()
    data class Downloaded(
        val apkFile: File,
        val formattedSize: String,
        val downloadTime: Long = System.currentTimeMillis()
    ) : UpdateState()
    object Installing : UpdateState()
    object PermissionRequired : UpdateState()
    data class Error(val message: String) : UpdateState()
}

object AppUpdateManager {

    private const val TAG = "VRAppUpdateManager"
    const val APK_DOWNLOAD_URL = "https://app.vagabondriders.com/Vagabond-Riders.apk"
    private const val APK_FILE_NAME = "Vagabond-Riders.apk"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var downloadJob: Job? = null

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private var downloadedApkFile: File? = null

    /**
     * Retrieves the currently installed version name and version code.
     */
    fun getInstalledVersionInfo(context: Context): Pair<String, Long> {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            val versionName = pInfo.versionName ?: "1.0"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
            Pair(versionName, versionCode)
        } catch (e: Exception) {
            Pair("1.0", 1L)
        }
    }

    /**
     * Checks whether the app has permission to request package installs on Android 8.0+.
     */
    fun hasInstallPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Opens system settings to allow installing unknown apps from this app source.
     */
    fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open unknown app sources settings: ${e.message}")
            }
        }
    }

    /**
     * Formats bytes into human-readable string (e.g., 24.5 MB).
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, index.toDouble())
        return String.format(Locale.US, "%.1f %s", value, units[index])
    }

    /**
     * Checks remote server for the APK metadata.
     */
    fun checkForUpdates(
        context: Context,
        onResult: ((isAvailable: Boolean, message: String) -> Unit)? = null
    ) {
        scope.launch {
            _updateState.value = UpdateState.Checking
            try {
                val request = Request.Builder()
                    .url(APK_DOWNLOAD_URL)
                    .head()
                    .header("User-Agent", "VRAndroidUpdater/2.0")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val contentLength = response.header("Content-Length")?.toLongOrNull() ?: 0L
                        val lastModified = response.header("Last-Modified")

                        val formattedSize = if (contentLength > 0) formatBytes(contentLength) else "Unknown size"
                        _updateState.value = UpdateState.Available(
                            fileSizeBytes = contentLength,
                            formattedSize = formattedSize,
                            serverLastModified = lastModified
                        )
                        withContext(Dispatchers.Main) {
                            onResult?.invoke(true, "Update found ($formattedSize)")
                        }
                    } else {
                        // If HEAD is blocked, fallback to normal available state
                        _updateState.value = UpdateState.Available(
                            fileSizeBytes = 0L,
                            formattedSize = "Latest Build",
                            serverLastModified = null
                        )
                        withContext(Dispatchers.Main) {
                            onResult?.invoke(true, "Latest update build ready for download")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates: ${e.message}")
                _updateState.value = UpdateState.Error("Unable to reach update server: ${e.localizedMessage}")
                withContext(Dispatchers.Main) {
                    onResult?.invoke(false, e.localizedMessage ?: "Network error")
                }
            }
        }
    }

    /**
     * Initiates APK download with real-time progress, speed, and percentage calculation.
     */
    fun startDownload(context: Context) {
        downloadJob?.cancel()
        downloadJob = scope.launch {
            try {
                _updateState.value = UpdateState.Downloading(
                    bytesDownloaded = 0L,
                    totalBytes = 0L,
                    progress = 0f,
                    percent = 0,
                    speedText = "Connecting..."
                )

                val request = Request.Builder()
                    .url(APK_DOWNLOAD_URL)
                    .header("User-Agent", "VRAndroidUpdater/2.0")
                    .build()

                val destinationDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: File(context.cacheDir, "updates").apply { mkdirs() }

                val destinationFile = File(destinationDir, APK_FILE_NAME)
                if (destinationFile.exists()) {
                    destinationFile.delete()
                }

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("HTTP Server Error ${response.code}: ${response.message}")
                    }

                    val body = response.body ?: throw Exception("Empty server response body")
                    val totalBytes = body.contentLength()

                    var bytesCopied: Long = 0
                    val buffer = ByteArray(8192)
                    val inputStream: InputStream = body.byteStream()
                    val outputStream = FileOutputStream(destinationFile)

                    var lastUpdateTime = System.currentTimeMillis()
                    var bytesSinceLastUpdate: Long = 0

                    outputStream.use { out ->
                        inputStream.use { input ->
                            while (isActive) {
                                val bytesRead = input.read(buffer)
                                if (bytesRead == -1) break

                                out.write(buffer, 0, bytesRead)
                                bytesCopied += bytesRead
                                bytesSinceLastUpdate += bytesRead

                                val now = System.currentTimeMillis()
                                val timeDelta = now - lastUpdateTime

                                if (timeDelta >= 400 || bytesCopied == totalBytes) {
                                    val speedBytesPerSec = if (timeDelta > 0) {
                                        (bytesSinceLastUpdate * 1000) / timeDelta
                                    } else 0L

                                    val speedFormatted = "${formatBytes(speedBytesPerSec)}/s"
                                    val progress = if (totalBytes > 0) {
                                        (bytesCopied.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                    } else {
                                        0.5f
                                    }
                                    val percent = (progress * 100).toInt()

                                    _updateState.value = UpdateState.Downloading(
                                        bytesDownloaded = bytesCopied,
                                        totalBytes = totalBytes,
                                        progress = progress,
                                        percent = percent,
                                        speedText = speedFormatted
                                    )

                                    lastUpdateTime = now
                                    bytesSinceLastUpdate = 0
                                }
                            }
                        }
                    }

                    if (isActive) {
                        downloadedApkFile = destinationFile
                        val formattedSize = formatBytes(destinationFile.length())
                        _updateState.value = UpdateState.Downloaded(
                            apkFile = destinationFile,
                            formattedSize = formattedSize
                        )
                        Log.d(TAG, "APK download completed: ${destinationFile.absolutePath} ($formattedSize)")

                        // Automatically attempt installation if permission is already granted
                        withContext(Dispatchers.Main) {
                            if (hasInstallPermission(context)) {
                                installDownloadedApk(context)
                            } else {
                                _updateState.value = UpdateState.PermissionRequired
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "APK download cancelled")
                _updateState.value = UpdateState.Idle
            } catch (e: Exception) {
                Log.e(TAG, "APK download failed: ${e.message}", e)
                _updateState.value = UpdateState.Error(e.localizedMessage ?: "Download failed")
            }
        }
    }

    /**
     * Cancels the active APK download.
     */
    fun cancelDownload() {
        downloadJob?.cancel()
        _updateState.value = UpdateState.Idle
    }

    /**
     * Launches the system Android package installer to install the downloaded APK.
     */
    fun installDownloadedApk(context: Context): Boolean {
        val file = downloadedApkFile ?: run {
            val destinationDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.cacheDir, "updates")
            val candidate = File(destinationDir, APK_FILE_NAME)
            if (candidate.exists() && candidate.length() > 0) candidate else null
        }

        if (file == null || !file.exists() || file.length() == 0L) {
            _updateState.value = UpdateState.Error("Downloaded APK not found. Please download again.")
            return false
        }

        if (!hasInstallPermission(context)) {
            _updateState.value = UpdateState.PermissionRequired
            requestInstallPermission(context)
            return false
        }

        return try {
            _updateState.value = UpdateState.Installing

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            }

            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer: ${e.message}", e)
            _updateState.value = UpdateState.Error("Installation trigger failed: ${e.localizedMessage}")
            false
        }
    }

    /**
     * Fallback to open the APK download URL directly in an external web browser.
     */
    fun openInBrowser(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(APK_DOWNLOAD_URL)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun resetState() {
        _updateState.value = UpdateState.Idle
    }
}

/**
 * Javascript Bridge for web pages/PHP to interact with in-app updater.
 * Injected into WebView as `window.VRAppUpdate` and `window.AndroidUpdater`.
 */
class VRUpdateJavascriptBridge(private val context: Context) {

    @JavascriptInterface
    fun checkForUpdates() {
        AppUpdateManager.checkForUpdates(context)
    }

    @JavascriptInterface
    fun downloadUpdate() {
        AppUpdateManager.startDownload(context)
    }

    @JavascriptInterface
    fun installUpdate() {
        AppUpdateManager.installDownloadedApk(context)
    }

    @JavascriptInterface
    fun getInstalledVersion(): String {
        val (ver, code) = AppUpdateManager.getInstalledVersionInfo(context)
        return "$ver ($code)"
    }

    @JavascriptInterface
    fun openInBrowser() {
        AppUpdateManager.openInBrowser(context)
    }
}
