package com.example.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.widget.Toast
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object GoogleOAuthHelper {

    const val MAIN_PORTAL_URL = "https://app.vagabondriders.com/index.php"
    const val MEMBERSHIP_LOGIN_URL = "https://membership.vagabondriders.com/index.php?page=login"
    const val MEMBERSHIP_PROFILE_URL = "https://membership.vagabondriders.com/profile.php"
    const val MEMBERSHIP_DOMAIN = "membership.vagabondriders.com"

    // Flow to broadcast incoming OAuth redirect / callback URLs to the active WebView
    private val _incomingOAuthUrl = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val incomingOAuthUrl: SharedFlow<String> = _incomingOAuthUrl.asSharedFlow()

    // Flag indicating an external Google OAuth session is currently active in Chrome
    private val _isOAuthInProgress = MutableStateFlow(false)
    val isOAuthInProgress: StateFlow<Boolean> = _isOAuthInProgress.asStateFlow()

    // Flow to notify UI/WebView that the user returned from Chrome to trigger a session refresh
    private val _authCompletionTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 5)
    val authCompletionTrigger: SharedFlow<Unit> = _authCompletionTrigger.asSharedFlow()

    /**
     * Determines if a given URL is a Google OAuth / Google Account authentication request.
     */
    fun isGoogleOAuthUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase().trim()

        return (
            lower.contains("accounts.google.com/o/oauth2") ||
            lower.contains("accounts.google.com/signin") ||
            lower.contains("accounts.google.com/v3/signin") ||
            lower.contains("accounts.google.com/servicelogin") ||
            lower.contains("accounts.google.com/accountchooser") ||
            lower.contains("accounts.google.com/gsi") ||
            lower.contains("oauth2.googleapis.com") ||
            (lower.contains("accounts.google.com") && lower.contains("oauth")) ||
            (lower.contains("client_id") && lower.contains("googleusercontent.com")) ||
            (lower.contains("oauth2/auth") && lower.contains("google")) ||
            lower.contains("login/google") ||
            lower.contains("auth/google") ||
            lower.contains("oauth/google") ||
            lower.contains("google-login") ||
            lower.contains("google_login") ||
            lower.contains("provider=google") ||
            lower.contains("auth_type=google") ||
            lower.contains("login_with=google")
        )
    }

    /**
     * Opens Google OAuth in Google Chrome / Chrome Custom Tabs to comply with
     * Google's security requirements and prevent '403: disallowed_useragent' errors.
     */
    fun openInChrome(context: Context, url: String) {
        _isOAuthInProgress.value = true
        val uri = Uri.parse(url)

        try {
            // Build Chrome Custom Tab with Vagabond Riders brand theme
            val customTabColorScheme = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(android.graphics.Color.parseColor("#EA580C"))
                .setSecondaryToolbarColor(android.graphics.Color.parseColor("#1E293B"))
                .build()

            val customTabsIntent = CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(customTabColorScheme)
                .setShowTitle(true)
                .setUrlBarHidingEnabled(false)
                .build()

            // Ensure Chrome handles the custom tab if available
            customTabsIntent.intent.setPackage("com.android.chrome")
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            customTabsIntent.launchUrl(context, uri)
            Toast.makeText(context, "Opening Google Sign-In in Chrome...", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            // Fallback 1: Try launching Chrome via standard ACTION_VIEW with Chrome package
            try {
                val chromeIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.android.chrome")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chromeIntent)
                Toast.makeText(context, "Opening Google Sign-In in Chrome...", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                // Fallback 2: Launch via standard external browser
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(browserIntent)
                    Toast.makeText(context, "Opening Google Sign-In in browser...", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    _isOAuthInProgress.value = false
                    Toast.makeText(context, "Unable to open browser for Google Login: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Injects cookies into CookieManager across all target URLs and domain configurations.
     */
    fun injectCookie(name: String, value: String) {
        if (name.isBlank() || value.isBlank()) return
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        val targetDomains = listOf(
            "https://membership.vagabondriders.com",
            "https://membership.vagabondriders.com/",
            "http://membership.vagabondriders.com",
            "https://app.vagabondriders.com",
            "https://app.vagabondriders.com/",
            "https://vagabondriders.com"
        )

        targetDomains.forEach { domainUrl ->
            cookieManager.setCookie(
                domainUrl,
                "$name=$value; Path=/; Domain=membership.vagabondriders.com; Secure; SameSite=Lax"
            )
            cookieManager.setCookie(
                domainUrl,
                "$name=$value; Path=/; Domain=.vagabondriders.com; Secure; SameSite=Lax"
            )
            cookieManager.setCookie(
                domainUrl,
                "$name=$value; Path=/"
            )
        }
        cookieManager.flush()
    }

    /**
     * Handles incoming deep links or redirects returned from Google Chrome after authentication.
     * Automatically resolves post-login landing destination to https://membership.vagabondriders.com/profile.php
     */
    fun handleIncomingRedirect(url: String) {
        if (url.isBlank()) return
        val wasOAuthInProgress = _isOAuthInProgress.value
        _isOAuthInProgress.value = false

        try {
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase() ?: ""
            val host = uri.host?.lowercase() ?: ""

            // Extract all query parameters and inject as cookies
            if (uri.isHierarchical) {
                uri.queryParameterNames.forEach { paramName ->
                    val paramVal = uri.getQueryParameter(paramName)
                    if (!paramVal.isNullOrBlank()) {
                        val lower = paramName.lowercase()
                        if (lower.contains("phpsessid") || lower.contains("session") || lower.contains("token") || lower.contains("jwt") || lower.contains("user")) {
                            val cName = if (lower.contains("phpsessid") || lower.contains("session")) "PHPSESSID" else paramName
                            injectCookie(cName, paramVal)
                        }
                    }
                }
            }

            // Extract fragment parameters if any
            uri.fragment?.let { frag ->
                frag.split("&").forEach { pair ->
                    val parts = pair.split("=", limit = 2)
                    if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                        val fName = parts[0]
                        val fVal = parts[1]
                        val lower = fName.lowercase()
                        if (lower.contains("phpsessid") || lower.contains("session") || lower.contains("token") || lower.contains("jwt") || lower.contains("user")) {
                            val cName = if (lower.contains("phpsessid") || lower.contains("session")) "PHPSESSID" else fName
                            injectCookie(cName, fVal)
                        }
                    }
                }
            }

            // If this is returning from authentication or targets login/auth, redirect to profile.php
            val lowerUrl = url.lowercase()
            val isAuthReturn = wasOAuthInProgress ||
                    scheme == "vrportal" ||
                    lowerUrl.contains("page=login") ||
                    lowerUrl.contains("login") ||
                    lowerUrl.contains("oauth") ||
                    lowerUrl.contains("callback") ||
                    lowerUrl.contains("code=") ||
                    lowerUrl.contains("token=") ||
                    lowerUrl.contains("phpsessid")

            val resolvedUrl = if (isAuthReturn) {
                MEMBERSHIP_PROFILE_URL
            } else if (scheme == "https" || scheme == "http") {
                url
            } else {
                MEMBERSHIP_PROFILE_URL
            }

            _incomingOAuthUrl.tryEmit(resolvedUrl)
            _authCompletionTrigger.tryEmit(Unit)
        } catch (_: Exception) {
            _incomingOAuthUrl.tryEmit(MEMBERSHIP_PROFILE_URL)
            _authCompletionTrigger.tryEmit(Unit)
        }
    }

    /**
     * Called when the app resumes from the background after Google Chrome authentication.
     */
    fun onAppResumed() {
        if (_isOAuthInProgress.value) {
            _isOAuthInProgress.value = false
            _authCompletionTrigger.tryEmit(Unit)
        }
    }
}
