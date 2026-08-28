package com.example.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Centralized Navigation Manager for handling target URLs pushed via notifications,
 * deep links, and external triggers. Ensures the active WebView navigates to the exact
 * requested target_url seamlessly, whether the app was already opened or cold-started.
 */
object NavigationManager {

    private const val TAG = "NavigationManager"
    const val DEFAULT_HOME_URL = "https://app.vagabondriders.com/index.php"

    // Emits navigation destination URLs to active WebView tabs
    private val _targetUrlEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val targetUrlEvents: SharedFlow<String> = _targetUrlEvents.asSharedFlow()

    // Holds cold-start target URL if the app was launched by clicking a notification
    private var _coldStartTargetUrl: String? = null

    fun setColdStartTargetUrl(url: String?) {
        val resolved = resolveTargetUrl(url)
        _coldStartTargetUrl = resolved
    }

    fun consumeColdStartTargetUrl(): String? {
        val url = _coldStartTargetUrl
        _coldStartTargetUrl = null
        return url
    }

    /**
     * Broadcasts navigation command to open a specific target_url in the active WebView.
     */
    fun navigateToUrl(rawUrl: String?) {
        val resolved = resolveTargetUrl(rawUrl) ?: return
        Log.d(TAG, "Navigating to resolved target URL: $resolved")
        _targetUrlEvents.tryEmit(resolved)
    }

    /**
     * Resolves target_url from database/API, handling relative paths, absolute URLs,
     * subdomains, and external portal links.
     */
    fun resolveTargetUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.equals("null", ignoreCase = true)) return null

        return when {
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> {
                trimmed
            }
            trimmed.startsWith("vrportal://", ignoreCase = true) -> {
                // Internal scheme
                trimmed
            }
            trimmed.startsWith("/") -> {
                "https://app.vagabondriders.com$trimmed"
            }
            else -> {
                "https://app.vagabondriders.com/$trimmed"
            }
        }
    }

    /**
     * Resolves image_url from database/API, handling relative upload paths like 'uploads/17...'.
     */
    fun resolveImageUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.equals("null", ignoreCase = true)) return null

        return when {
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> {
                trimmed
            }
            trimmed.startsWith("/") -> {
                "https://app.vagabondriders.com$trimmed"
            }
            else -> {
                "https://app.vagabondriders.com/$trimmed"
            }
        }
    }
}
