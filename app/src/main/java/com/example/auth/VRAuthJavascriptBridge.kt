package com.example.auth

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SavePasswordPromptRequest(
    val username: String,
    val password: String,
    val appName: String = "Vagabond Riders",
    val url: String = ""
)

class VRAuthJavascriptBridge(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private val _hasLoginFormDetected = MutableStateFlow(false)
        val hasLoginFormDetected: StateFlow<Boolean> = _hasLoginFormDetected.asStateFlow()

        private val _pendingSaveRequest = MutableStateFlow<SavePasswordPromptRequest?>(null)
        val pendingSaveRequest: StateFlow<SavePasswordPromptRequest?> = _pendingSaveRequest.asStateFlow()

        var onAutofillRequested: (() -> Unit)? = null

        fun setLoginFormDetected(detected: Boolean) {
            _hasLoginFormDetected.value = detected
        }

        fun clearPendingSaveRequest() {
            _pendingSaveRequest.value = null
        }

        fun triggerSaveRequest(request: SavePasswordPromptRequest) {
            _pendingSaveRequest.value = request
        }
    }

    @JavascriptInterface
    fun isNativeApp(): Boolean = true

    @JavascriptInterface
    fun onLoginFormDetected(hasPasswordInput: Boolean, formInfo: String?) {
        mainHandler.post {
            _hasLoginFormDetected.value = hasPasswordInput
        }
    }

    @JavascriptInterface
    fun promptSavePassword(username: String?, password: String?, appName: String?, url: String?) {
        if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
            mainHandler.post {
                _pendingSaveRequest.value = SavePasswordPromptRequest(
                    username = username.trim(),
                    password = password.trim(),
                    appName = if (appName.isNullOrBlank()) "Vagabond Riders" else appName.trim(),
                    url = url?.trim() ?: ""
                )
            }
        }
    }

    @JavascriptInterface
    fun savePassword(username: String?, password: String?, appName: String?) {
        promptSavePassword(username, password, appName, "")
    }

    @JavascriptInterface
    fun requestAutofill() {
        mainHandler.post {
            onAutofillRequested?.invoke()
        }
    }

    @JavascriptInterface
    fun startGoogleLogin(authUrl: String?) {
        val target = if (!authUrl.isNullOrBlank()) {
            authUrl
        } else {
            "https://membership.vagabondriders.com/login/google"
        }
        GoogleOAuthHelper.openInChrome(context, target)
    }

    @JavascriptInterface
    fun loginWithGoogle(customUrl: String?) {
        startGoogleLogin(customUrl)
    }

    @JavascriptInterface
    fun openOAuth(url: String) {
        if (url.isNotBlank()) {
            GoogleOAuthHelper.openInChrome(context, url)
        }
    }
}
