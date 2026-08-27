package com.example.data

import android.webkit.WebView

data class WebTab(
    val id: String,
    val title: String = "Vagabond Riders",
    val url: String = "https://app.vagabondriders.com/index.php",
    val webView: WebView? = null,
    val progress: Int = 0,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false
)
