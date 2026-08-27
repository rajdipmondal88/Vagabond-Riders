package com.example.data

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DownloadedFile(
    val id: String = System.currentTimeMillis().toString(),
    val fileName: String,
    val filePath: String,
    val mimeType: String,
    val fileSize: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceUrl: String = ""
) {
    val file: File
        get() = File(filePath)

    val exists: Boolean
        get() = file.exists()

    val isPdf: Boolean
        get() = mimeType.contains("pdf", ignoreCase = true) || fileName.endsWith(".pdf", ignoreCase = true)

    val isExcel: Boolean
        get() = mimeType.contains("excel", ignoreCase = true) ||
                mimeType.contains("spreadsheet", ignoreCase = true) ||
                fileName.endsWith(".xlsx", ignoreCase = true) ||
                fileName.endsWith(".xls", ignoreCase = true)

    val isCsv: Boolean
        get() = mimeType.contains("csv", ignoreCase = true) || fileName.endsWith(".csv", ignoreCase = true)

    val isDoc: Boolean
        get() = mimeType.contains("word", ignoreCase = true) ||
                mimeType.contains("document", ignoreCase = true) ||
                fileName.endsWith(".docx", ignoreCase = true) ||
                fileName.endsWith(".doc", ignoreCase = true)

    val formattedSize: String
        get() {
            val size = if (file.exists()) file.length() else fileSize
            return when {
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", size / 1024.0)
                else -> String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024.0))
            }
        }

    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

    val extensionLabel: String
        get() = when {
            isPdf -> "PDF"
            isExcel && fileName.endsWith(".xlsx", ignoreCase = true) -> "XLSX"
            isExcel -> "XLS"
            isCsv -> "CSV"
            isDoc -> "DOCX"
            else -> fileName.substringAfterLast('.', "FILE").uppercase()
        }
}
