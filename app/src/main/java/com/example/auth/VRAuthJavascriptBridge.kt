package com.example.auth

import android.content.Context
import android.webkit.JavascriptInterface

class VRAuthJavascriptBridge(private val context: Context) {

    @JavascriptInterface
    fun isNativeApp(): Boolean = true

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
