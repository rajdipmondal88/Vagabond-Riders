package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object CustomLogoManager {

    const val OFFICIAL_LOGO_URL = "http://app.vagabondriders.com/logo.png"
    const val OFFICIAL_LOGO_SECURE_URL = "https://app.vagabondriders.com/logo.png"
    private const val LOGO_FILE_NAME = "vagabond_official_logo.png"

    private val _customLogoBitmap = MutableStateFlow<ImageBitmap?>(null)
    val customLogoBitmap: StateFlow<ImageBitmap?> = _customLogoBitmap.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    /**
     * Initializes the Logo Manager. Loads any previously cached logo from disk
     * and downloads/syncs the fresh unedited logo directly from https://app.vagabondriders.com/logo.png
     */
    fun init(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            // First load cached logo if it exists
            loadCachedLogo(context)

            // Then download & update from official URL
            syncLogoFromUrl(context)
        }
    }

    private suspend fun loadCachedLogo(context: Context) {
        val logoFile = File(context.filesDir, LOGO_FILE_NAME)
        if (logoFile.exists() && logoFile.length() > 0) {
            try {
                val bitmap = BitmapFactory.decodeFile(logoFile.absolutePath)
                if (bitmap != null) {
                    val imageBitmap = bitmap.asImageBitmap()
                    withContext(Dispatchers.Main) {
                        _customLogoBitmap.value = imageBitmap
                    }
                }
            } catch (_: Exception) { }
        }
    }

    /**
     * Synchronously/safely retrieves the official Vagabond Riders logo as an Android Bitmap
     * for notifications, with fallback to downloaded disk cache or app launcher icon.
     */
    fun getOfficialLogoBitmap(context: Context): Bitmap? {
        try {
            val logoFile = File(context.filesDir, LOGO_FILE_NAME)
            if (logoFile.exists() && logoFile.length() > 0) {
                val cached = BitmapFactory.decodeFile(logoFile.absolutePath)
                if (cached != null) return cached
            }
        } catch (_: Exception) { }

        // Fallback: load launcher icon bitmap
        return try {
            BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Downloads the exact unedited logo from the official URL and updates the app emblem
     */
    suspend fun syncLogoFromUrl(context: Context) {
        withContext(Dispatchers.IO) {
            _isDownloading.value = true
            val urlsToTry = listOf(OFFICIAL_LOGO_SECURE_URL, OFFICIAL_LOGO_URL)
            var downloaded = false

            for (urlString in urlsToTry) {
                if (downloaded) break
                try {
                    val url = URL(urlString)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000
                    connection.instanceFollowRedirects = true
                    connection.setRequestProperty("User-Agent", "VagabondRiders-Android-App")

                    if (connection.responseCode in 200..299) {
                        val inputStream: InputStream = connection.inputStream
                        val targetFile = File(context.filesDir, LOGO_FILE_NAME)
                        val outputStream = FileOutputStream(targetFile)
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                        outputStream.flush()
                        outputStream.close()
                        inputStream.close()

                        val decodedBitmap = BitmapFactory.decodeFile(targetFile.absolutePath)
                        if (decodedBitmap != null) {
                            val imageBitmap = decodedBitmap.asImageBitmap()
                            withContext(Dispatchers.Main) {
                                _customLogoBitmap.value = imageBitmap
                            }
                            downloaded = true
                        }
                    }
                    connection.disconnect()
                } catch (_: Exception) {
                    // Try next fallback URL if available
                }
            }

            _isDownloading.value = false
        }
    }
}
