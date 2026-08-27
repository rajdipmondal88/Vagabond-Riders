package com.example.utils

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.DownloadedFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileDownloadHelper {

    private val _downloadHistory = MutableStateFlow<List<DownloadedFile>>(emptyList())
    val downloadHistory: StateFlow<List<DownloadedFile>> = _downloadHistory.asStateFlow()

    private val _activeDownload = MutableStateFlow<DownloadedFile?>(null)
    val activeDownload: StateFlow<DownloadedFile?> = _activeDownload.asStateFlow()

    private val _lastCompletedDownload = MutableStateFlow<DownloadedFile?>(null)
    val lastCompletedDownload: StateFlow<DownloadedFile?> = _lastCompletedDownload.asStateFlow()

    private const val PREFS_NAME = "vr_downloads_prefs"
    private const val KEY_HISTORY = "download_history_json"

    fun init(context: Context) {
        loadHistory(context)
        syncExistingFiles(context)
    }

    private fun loadHistory(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_HISTORY, null) ?: return
        try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<DownloadedFile>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    DownloadedFile(
                        id = obj.optString("id"),
                        fileName = obj.optString("fileName"),
                        filePath = obj.optString("filePath"),
                        mimeType = obj.optString("mimeType"),
                        fileSize = obj.optLong("fileSize"),
                        timestamp = obj.optLong("timestamp"),
                        sourceUrl = obj.optString("sourceUrl")
                    )
                )
            }
            _downloadHistory.value = list
        } catch (_: Exception) { }
    }

    private fun saveHistory(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        _downloadHistory.value.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("fileName", item.fileName)
                put("filePath", item.filePath)
                put("mimeType", item.mimeType)
                put("fileSize", item.fileSize)
                put("timestamp", item.timestamp)
                put("sourceUrl", item.sourceUrl)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    private fun syncExistingFiles(context: Context) {
        try {
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, "downloads").apply { mkdirs() }
            val existingInHistory = _downloadHistory.value.map { it.filePath }.toSet()
            val discovered = mutableListOf<DownloadedFile>()

            downloadDir.listFiles()?.forEach { file ->
                if (file.isFile && file.length() > 0 && !existingInHistory.contains(file.absolutePath)) {
                    val mime = getMimeTypeFromFilename(file.name)
                    discovered.add(
                        DownloadedFile(
                            id = file.lastModified().toString(),
                            fileName = file.name,
                            filePath = file.absolutePath,
                            mimeType = mime,
                            fileSize = file.length(),
                            timestamp = file.lastModified()
                        )
                    )
                }
            }

            if (discovered.isNotEmpty()) {
                val updated = (_downloadHistory.value + discovered).sortedByDescending { it.timestamp }
                _downloadHistory.value = updated
                saveHistory(context)
            }
        } catch (_: Exception) { }
    }

    fun dismissLastCompleted() {
        _lastCompletedDownload.value = null
    }

    fun resolveMimeType(url: String, mimeType: String?, filename: String): String {
        if (!mimeType.isNullOrBlank() && mimeType != "application/octet-stream" && mimeType != "text/html") {
            return mimeType
        }
        val lower = filename.lowercase()
        return when {
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            lower.endsWith(".xls") -> "application/vnd.ms-excel"
            lower.endsWith(".csv") -> "text/csv"
            lower.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            lower.endsWith(".doc") -> "application/msword"
            lower.endsWith(".zip") -> "application/zip"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            else -> {
                val extension = MimeTypeMap.getFileExtensionFromUrl(url).ifBlank {
                    filename.substringAfterLast('.', "")
                }
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: "application/octet-stream"
            }
        }
    }

    private fun getMimeTypeFromFilename(name: String): String {
        return resolveMimeType("", null, name)
    }

    fun resolveFilename(url: String, contentDisposition: String?, mimeType: String?): String {
        var guessed = ""
        try {
            guessed = URLUtil.guessFileName(url, contentDisposition, mimeType)
        } catch (_: Exception) { }

        if (guessed.contains("?")) {
            guessed = guessed.substringBefore("?")
        }
        if (guessed.isBlank() || guessed == "downloadfile" || guessed == "index.php" || guessed == "export.php" || guessed == "report.php") {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val lowerUrl = url.lowercase()
            guessed = when {
                mimeType?.contains("pdf", ignoreCase = true) == true || lowerUrl.contains("pdf") -> "VR_Report_$timestamp.pdf"
                mimeType?.contains("excel", ignoreCase = true) == true || mimeType?.contains("spreadsheet", ignoreCase = true) == true || lowerUrl.contains("excel") || lowerUrl.contains("xlsx") -> "VR_Trip_Data_$timestamp.xlsx"
                mimeType?.contains("csv", ignoreCase = true) == true || lowerUrl.contains("csv") -> "VR_Export_$timestamp.csv"
                else -> "VR_Document_$timestamp.pdf"
            }
        }

        if (!guessed.contains(".")) {
            val lowerUrl = url.lowercase()
            if (mimeType?.contains("pdf", ignoreCase = true) == true || lowerUrl.contains("pdf")) {
                guessed += ".pdf"
            } else if (mimeType?.contains("excel", ignoreCase = true) == true || mimeType?.contains("spreadsheet", ignoreCase = true) == true || lowerUrl.contains("excel")) {
                guessed += ".xlsx"
            } else if (mimeType?.contains("csv", ignoreCase = true) == true || lowerUrl.contains("csv")) {
                guessed += ".csv"
            }
        }

        return guessed
    }

    /**
     * Copy downloaded file to device's Public Download directory
     * so user can find it in their Files / Downloads app directly.
     */
    fun exportToPublicDownloads(context: Context, sourceFile: File, fileName: String, mimeType: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/VagabondRiders")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        sourceFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                }
            } else {
                val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "VagabondRiders")
                if (!publicDir.exists()) publicDir.mkdirs()
                val target = File(publicDir, fileName)
                sourceFile.copyTo(target, overwrite = true)
                MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mimeType), null)
            }
        } catch (_: Exception) { }
    }

    /**
     * Download a file from a web URL using authenticated direct stream and save to app & public downloads.
     */
    fun startDownload(
        context: Context,
        url: String,
        userAgent: String? = null,
        contentDisposition: String? = null,
        mimetype: String? = null,
        onDownloadStarted: (String) -> Unit = {},
        onDownloadFinished: (DownloadedFile) -> Unit = {}
    ) {
        val fileName = resolveFilename(url, contentDisposition, mimetype)
        val finalMime = resolveMimeType(url, mimetype, fileName)
        val cookies = CookieManager.getInstance().getCookie(url)

        val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads").apply { mkdirs() }
        
        var destinationFile = File(targetDir, fileName)
        var counter = 1
        val baseName = fileName.substringBeforeLast(".")
        val extension = if (fileName.contains(".")) ".${fileName.substringAfterLast(".")}" else ""
        while (destinationFile.exists()) {
            destinationFile = File(targetDir, "${baseName}_$counter$extension")
            counter++
        }

        val downloadingFile = DownloadedFile(
            fileName = destinationFile.name,
            filePath = destinationFile.absolutePath,
            mimeType = finalMime,
            fileSize = 0L,
            sourceUrl = url
        )

        _activeDownload.value = downloadingFile
        onDownloadStarted(downloadingFile.fileName)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val httpUrl = URL(url)
                val connection = (httpUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 20000
                    readTimeout = 35000
                    instanceFollowRedirects = true
                    if (!cookies.isNullOrBlank()) {
                        setRequestProperty("Cookie", cookies)
                    }
                    if (!userAgent.isNullOrBlank()) {
                        setRequestProperty("User-Agent", userAgent)
                    }
                    setRequestProperty("Accept", "application/pdf, application/vnd.ms-excel, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, text/csv, */*")
                    setRequestProperty("Referer", "https://app.vagabondriders.com/")
                }

                connection.connect()
                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val inputStream = connection.inputStream
                    val outputStream = FileOutputStream(destinationFile)
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytes = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                    }

                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()

                    // Export to public Downloads so it appears in device's Downloads folder
                    exportToPublicDownloads(context, destinationFile, destinationFile.name, finalMime)

                    val completed = downloadingFile.copy(fileSize = totalBytes)
                    withContext(Dispatchers.Main) {
                        _activeDownload.value = null
                        _lastCompletedDownload.value = completed
                        val updatedList = listOf(completed) + _downloadHistory.value.filter { it.filePath != completed.filePath }
                        _downloadHistory.value = updatedList
                        saveHistory(context)
                        onDownloadFinished(completed)
                        Toast.makeText(context, "Saved ${completed.fileName} to Downloads", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _activeDownload.value = null
                        downloadViaSystemDownloadManager(context, url, fileName, finalMime, cookies, userAgent)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _activeDownload.value = null
                    downloadViaSystemDownloadManager(context, url, fileName, finalMime, cookies, userAgent)
                }
            }
        }
    }

    private fun downloadViaSystemDownloadManager(
        context: Context,
        url: String,
        fileName: String,
        mimeType: String,
        cookies: String?,
        userAgent: String?
    ) {
        try {
            val uri = Uri.parse(url)
            val request = DownloadManager.Request(uri).apply {
                setMimeType(mimeType)
                if (!cookies.isNullOrBlank()) {
                    addRequestHeader("Cookie", cookies)
                }
                if (!userAgent.isNullOrBlank()) {
                    addRequestHeader("User-Agent", userAgent)
                }
                addRequestHeader("Referer", "https://app.vagabondriders.com/")
                setDescription("Downloading $fileName")
                setTitle(fileName)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "VagabondRiders/$fileName")
            }

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(context, "Downloading $fileName to phone...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Save Base64 Blob (generated by JS / SheetJS / jsPDF / client-side exports)
     */
    fun saveBase64Blob(
        context: Context,
        base64Data: String,
        fileName: String,
        mimeType: String,
        onFinished: (DownloadedFile) -> Unit = {}
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cleanBase64 = if (base64Data.contains(",")) {
                    base64Data.substringAfter(",")
                } else {
                    base64Data
                }

                val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: File(context.filesDir, "downloads").apply { mkdirs() }

                var cleanName = fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                val finalMime = resolveMimeType("", mimeType, cleanName)

                if (!cleanName.contains(".")) {
                    cleanName += when {
                        finalMime.contains("pdf") -> ".pdf"
                        finalMime.contains("excel") || finalMime.contains("spreadsheet") -> ".xlsx"
                        finalMime.contains("csv") -> ".csv"
                        else -> ".pdf"
                    }
                }

                val file = File(targetDir, cleanName)
                val fos = FileOutputStream(file)
                fos.write(decodedBytes)
                fos.flush()
                fos.close()

                // Also copy to public Downloads folder
                exportToPublicDownloads(context, file, cleanName, finalMime)

                val downloaded = DownloadedFile(
                    fileName = cleanName,
                    filePath = file.absolutePath,
                    mimeType = finalMime,
                    fileSize = file.length(),
                    sourceUrl = "Client Export (PDF/Excel)"
                )

                withContext(Dispatchers.Main) {
                    _lastCompletedDownload.value = downloaded
                    val updatedList = listOf(downloaded) + _downloadHistory.value.filter { it.filePath != downloaded.filePath }
                    _downloadHistory.value = updatedList
                    saveHistory(context)
                    onFinished(downloaded)
                    Toast.makeText(context, "Saved $cleanName to Downloads", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error saving export: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Print or Save as PDF via native Android PrintManager
     */
    fun printWebPageAsPdf(context: Context, webView: WebView, documentName: String = "Vagabond_Riders_Report") {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            val printAdapter = webView.createPrintDocumentAdapter(documentName)
            val jobName = "${documentName}_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}"
            printManager?.print(
                jobName,
                printAdapter,
                PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setResolution(PrintAttributes.Resolution("pdf", "PDF", 600, 600))
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Print error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Open a downloaded file with external viewers (Adobe PDF, Google PDF, Excel, Sheets, etc.)
     */
    fun openFile(context: Context, downloadedFile: DownloadedFile) {
        try {
            val file = File(downloadedFile.filePath)
            if (!file.exists()) {
                Toast.makeText(context, "File no longer exists at ${downloadedFile.fileName}", Toast.LENGTH_SHORT).show()
                return
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, downloadedFile.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(intent, "Open with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "No app installed to open ${downloadedFile.mimeType.substringAfterLast('/')} files",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to open file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Share a downloaded file via WhatsApp, Gmail, Drive, etc.
     */
    fun shareFile(context: Context, downloadedFile: DownloadedFile) {
        try {
            val file = File(downloadedFile.filePath)
            if (!file.exists()) {
                Toast.makeText(context, "File no longer exists", Toast.LENGTH_SHORT).show()
                return
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = downloadedFile.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, downloadedFile.fileName)
                putExtra(Intent.EXTRA_TEXT, "Sharing ${downloadedFile.fileName} from Vagabond Riders")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share file via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to share file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteFile(context: Context, downloadedFile: DownloadedFile) {
        try {
            val file = File(downloadedFile.filePath)
            if (file.exists()) {
                file.delete()
            }
            val updated = _downloadHistory.value.filter { it.id != downloadedFile.id }
            _downloadHistory.value = updated
            saveHistory(context)
            Toast.makeText(context, "Deleted ${downloadedFile.fileName}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to delete: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun clearAllHistory(context: Context) {
        try {
            _downloadHistory.value.forEach { item ->
                val f = File(item.filePath)
                if (f.exists()) f.delete()
            }
            _downloadHistory.value = emptyList()
            saveHistory(context)
            Toast.makeText(context, "All download history cleared", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) { }
    }
}
