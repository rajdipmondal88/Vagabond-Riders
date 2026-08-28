package com.example.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.auth.GoogleOAuthHelper
import com.example.auth.VRAuthJavascriptBridge
import com.example.data.DownloadedFile
import com.example.location.BackgroundLocationManager
import com.example.notifications.VRPushJavascriptBridge
import com.example.updater.AppUpdateManager
import com.example.updater.VRUpdateJavascriptBridge
import com.example.utils.FileDownloadHelper

class VRDownloadBridge(
    private val context: Context,
    private val webViewProvider: () -> WebView?,
    private val onDownloadStarted: (String) -> Unit,
    private val onDownloadFinished: (DownloadedFile) -> Unit,
    private val onNewTabRequested: (String) -> Unit
) {
    @JavascriptInterface
    fun downloadBlob(base64Data: String, fileName: String, mimeType: String) {
        FileDownloadHelper.saveBase64Blob(
            context = context,
            base64Data = base64Data,
            fileName = fileName.ifBlank { "VR_Document" },
            mimeType = mimeType.ifBlank { "application/pdf" },
            onFinished = onDownloadFinished
        )
    }

    @JavascriptInterface
    fun triggerDownload(url: String, filename: String, mimeType: String) {
        FileDownloadHelper.startDownload(
            context = context,
            url = url,
            contentDisposition = "attachment; filename=\"$filename\"",
            mimetype = mimeType,
            onDownloadStarted = onDownloadStarted,
            onDownloadFinished = onDownloadFinished
        )
    }

    @JavascriptInterface
    fun openInNewTab(url: String) {
        if (url.isNotBlank()) {
            onNewTabRequested(url)
        }
    }

    @JavascriptInterface
    fun printPage() {
        val wv = webViewProvider()
        if (wv != null) {
            FileDownloadHelper.printWebPageAsPdf(context, wv, "Vagabond_Riders_Report")
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VRWebView(
    url: String,
    onProgressChange: (Int) -> Unit,
    onPageStarted: (String) -> Unit,
    onPageFinished: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
    onWebViewCreated: (WebView) -> Unit = {},
    onDownloadStarted: (String) -> Unit = {},
    onDownloadFinished: (DownloadedFile) -> Unit = {},
    onNewTabRequested: (String) -> Unit = {},
    onAppUpdateRequested: () -> Unit = {}
) {
    val context = LocalContext.current
    var currentWebView by remember { mutableStateOf<WebView?>(null) }
    var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var pendingCameraPermissionRequest by remember { mutableStateOf<PermissionRequest?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        fileChooserCallback?.onReceiveValue(uris.toTypedArray())
        fileChooserCallback = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingCameraPermissionRequest?.let { req ->
                req.grant(req.resources)
            }
        } else {
            pendingCameraPermissionRequest?.deny()
            Toast.makeText(context, "Camera permission denied by user", Toast.LENGTH_SHORT).show()
        }
        pendingCameraPermissionRequest = null
    }

    val downloadBridge = remember {
        VRDownloadBridge(
            context = context,
            webViewProvider = { currentWebView },
            onDownloadStarted = onDownloadStarted,
            onDownloadFinished = onDownloadFinished,
            onNewTabRequested = onNewTabRequested
        )
    }

    val pushBridge = remember {
        VRPushJavascriptBridge(context)
    }

    val authBridge = remember {
        VRAuthJavascriptBridge(context)
    }

    val updateBridge = remember {
        VRUpdateJavascriptBridge(context)
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // Enable Cookies & Third Party Cookies
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                // Web Settings
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    allowFileAccess = true
                    allowContentAccess = true
                    mediaPlaybackRequiresUserGesture = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    userAgentString = settings.userAgentString + " VRAndroidApp/2.0"
                    
                    // Enable Geolocation for continuous GPS Navigation
                    setGeolocationEnabled(true)
                    try {
                        setGeolocationDatabasePath(ctx.filesDir.path)
                    } catch (_: Exception) {}

                    // Enable multi-window / tab handling for target="_blank"
                    setSupportMultipleWindows(true)
                    javaScriptCanOpenWindowsAutomatically = true
                }

                // Add Javascript Interfaces
                addJavascriptInterface(downloadBridge, "VRNativeDownloader")
                addJavascriptInterface(pushBridge, "AndroidPush")
                addJavascriptInterface(pushBridge, "VagabondPush")
                addJavascriptInterface(authBridge, "AndroidAuth")
                addJavascriptInterface(authBridge, "VRAuth")
                addJavascriptInterface(updateBridge, "VRAppUpdate")
                addJavascriptInterface(updateBridge, "AndroidUpdater")

                // Download Listener: Intercepts PDF, Excel, and file downloads directly
                setDownloadListener { downloadUrl, userAgent, contentDisposition, mimetype, contentLength ->
                    if (downloadUrl.endsWith(".apk", ignoreCase = true) || mimetype.equals("application/vnd.android.package-archive", ignoreCase = true) || downloadUrl.contains("Vagabond-Riders.apk", ignoreCase = true)) {
                        AppUpdateManager.startDownload(ctx)
                        onAppUpdateRequested()
                    } else {
                        FileDownloadHelper.startDownload(
                            context = ctx,
                            url = downloadUrl,
                            userAgent = userAgent,
                            contentDisposition = contentDisposition,
                            mimetype = mimetype,
                            onDownloadStarted = onDownloadStarted,
                            onDownloadFinished = onDownloadFinished
                        )
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgressChange(newProgress)
                    }

                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        super.onReceivedTitle(view, title)
                        if (!title.isNullOrBlank()) {
                            onTitleChange(title)
                        }
                    }

                    // Auto-grant Geolocation permission prompts to allow continuous navigation
                    override fun onGeolocationPermissionsShowPrompt(
                        origin: String?,
                        callback: GeolocationPermissions.Callback?
                    ) {
                        callback?.invoke(origin, true, false)
                    }

                    // Handles WebRTC Camera & Microphone permission requests
                    override fun onPermissionRequest(request: PermissionRequest?) {
                        if (request == null) return
                        val resources = request.resources
                        val needsCamera = resources.any { it == PermissionRequest.RESOURCE_VIDEO_CAPTURE }
                        val needsAudio = resources.any { it == PermissionRequest.RESOURCE_AUDIO_CAPTURE }

                        if (needsCamera) {
                            val cameraGranted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED

                            if (cameraGranted) {
                                request.grant(resources)
                            } else {
                                pendingCameraPermissionRequest = request
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        } else if (needsAudio) {
                            request.grant(resources)
                        } else {
                            request.grant(resources)
                        }
                    }

                    override fun onPermissionRequestCanceled(request: PermissionRequest?) {
                        if (pendingCameraPermissionRequest == request) {
                            pendingCameraPermissionRequest = null
                        }
                    }

                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallback: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        fileChooserCallback?.onReceiveValue(null)
                        fileChooserCallback = filePathCallback
                        val mimeType = fileChooserParams?.acceptTypes?.firstOrNull()
                            ?.takeIf { it.isNotBlank() } ?: "*/*"
                        filePickerLauncher.launch(mimeType)
                        return true
                    }

                    // Handles target="_blank", window.open(), Google OAuth popup tabs
                    override fun onCreateWindow(
                        view: WebView?,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: Message?
                    ): Boolean {
                        val transportWebView = WebView(ctx)
                        transportWebView.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                wv: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val destUrl = request?.url?.toString() ?: ""
                                if (destUrl.isNotBlank()) {
                                    // Intercept Google OAuth requests and open in Google Chrome
                                    if (GoogleOAuthHelper.isGoogleOAuthUrl(destUrl)) {
                                        GoogleOAuthHelper.openInChrome(context, destUrl)
                                        return true
                                    }

                                    val lower = destUrl.lowercase()
                                    if (lower.endsWith(".pdf") || lower.contains("export=pdf") || lower.contains("format=pdf")) {
                                        FileDownloadHelper.startDownload(
                                            context = context,
                                            url = destUrl,
                                            onDownloadStarted = onDownloadStarted,
                                            onDownloadFinished = onDownloadFinished
                                        )
                                    } else {
                                        onNewTabRequested(destUrl)
                                    }
                                }
                                return true
                            }
                        }

                        val transport = resultMsg?.obj as? WebView.WebViewTransport
                        transport?.webView = transportWebView
                        resultMsg?.sendToTarget()
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        // Release previous camera streams when navigating
                        view?.evaluateJavascript(
                            """
                            (function() {
                                if (window._vrActiveMediaStreams) {
                                    window._vrActiveMediaStreams.forEach(function(s) {
                                        try { s.getTracks().forEach(function(t) { t.stop(); }); } catch(e){}
                                    });
                                    window._vrActiveMediaStreams = [];
                                }
                            })();
                            """.trimIndent(),
                            null
                        )
                        url?.let { onPageStarted(it) }
                    }

                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                        super.doUpdateVisitedHistory(view, url, isReload)
                        url?.let { onPageStarted(it) }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        url?.let { onPageFinished(it) }
                        CookieManager.getInstance().flush()

                        // Inject Camera stream tracker & auto-stop hook to prevent camera staying open in backend
                        val jsCameraSafetyHook = """
                            (function() {
                                if (window._vrCameraHookInjected) return;
                                window._vrCameraHookInjected = true;
                                window._vrActiveMediaStreams = [];

                                if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
                                    var originalGUM = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);
                                    navigator.mediaDevices.getUserMedia = function(constraints) {
                                        return originalGUM(constraints).then(function(stream) {
                                            window._vrActiveMediaStreams.push(stream);
                                            return stream;
                                        });
                                    };
                                }

                                window._vrStopCameraStreams = function() {
                                    if (window._vrActiveMediaStreams) {
                                        window._vrActiveMediaStreams.forEach(function(stream) {
                                            try {
                                                stream.getTracks().forEach(function(track) {
                                                    if (track.kind === 'video' || track.kind === 'audio') {
                                                        track.stop();
                                                    }
                                                });
                                            } catch(e) {}
                                        });
                                        window._vrActiveMediaStreams = [];
                                    }
                                };

                                // Also listen for page visibility / hide events
                                document.addEventListener('visibilitychange', function() {
                                    if (document.hidden) {
                                        window._vrStopCameraStreams();
                                    }
                                });
                            })();
                        """.trimIndent()
                        evaluateJavascript(jsCameraSafetyHook, null)

                        // Inject JS helper to intercept client-side Blob / Data downloads (e.g. SheetJS, jsPDF, Print, Table2Excel)
                        val jsBlobInterceptor = """
                            (function() {
                                if (window._vrDownloaderInjected) return;
                                window._vrDownloaderInjected = true;

                                // Hook window.print to invoke native Android PDF printing
                                window.print = function() {
                                    if (window.VRNativeDownloader && window.VRNativeDownloader.printPage) {
                                        window.VRNativeDownloader.printPage();
                                    }
                                };

                                // Intercept Blob clicks on dynamically created anchor elements
                                document.addEventListener('click', function(e) {
                                    var target = e.target.closest('a');
                                    if (!target) return;
                                    var href = target.getAttribute('href') || '';
                                    var downloadAttr = target.getAttribute('download');
                                    var targetAttr = target.getAttribute('target');
                                    
                                    if (targetAttr === '_blank' && href && !href.startsWith('javascript:')) {
                                        if (window.VRNativeDownloader && window.VRNativeDownloader.openInNewTab) {
                                            e.preventDefault();
                                            window.VRNativeDownloader.openInNewTab(href);
                                            return;
                                        }
                                    }

                                    if (href.startsWith('blob:') || href.startsWith('data:') || (downloadAttr !== null && downloadAttr !== undefined)) {
                                        var fileName = downloadAttr || target.innerText.trim() || 'VR_Export';
                                        
                                        if (href.startsWith('data:')) {
                                            var mime = href.substring(5, href.indexOf(';')) || 'application/pdf';
                                            if (window.VRNativeDownloader) {
                                                e.preventDefault();
                                                window.VRNativeDownloader.downloadBlob(href, fileName, mime);
                                            }
                                        } else if (href.startsWith('blob:')) {
                                            e.preventDefault();
                                            fetch(href).then(function(res) { return res.blob(); }).then(function(blob) {
                                                var reader = new FileReader();
                                                reader.onloadend = function() {
                                                    if (window.VRNativeDownloader) {
                                                        window.VRNativeDownloader.downloadBlob(reader.result, fileName, blob.type || 'application/pdf');
                                                    }
                                                };
                                                reader.readAsDataURL(blob);
                                            }).catch(function(err) {
                                                console.error('Blob read error', err);
                                            });
                                        }
                                    }
                                }, true);
                            })();
                        """.trimIndent()

                        evaluateJavascript(jsBlobInterceptor, null)

                        // Web Notifications API Bridge for Web Pages & PHP
                        val jsPushNotificationHook = """
                            (function() {
                                if (window._vrPushHookInjected) return;
                                window._vrPushHookInjected = true;

                                if (typeof Notification === 'undefined' || !window.Notification) {
                                    window.Notification = function(title, options) {
                                        options = options || {};
                                        if (window.AndroidPush) {
                                            window.AndroidPush.showNotification(title, options.body || '', options.data && options.data.url ? options.data.url : '');
                                        }
                                    };
                                    window.Notification.permission = 'granted';
                                    window.Notification.requestPermission = function(cb) {
                                        if (cb) cb('granted');
                                        return Promise.resolve('granted');
                                    };
                                }
                            })();
                        """.trimIndent()
                        evaluateJavascript(jsPushNotificationHook, null)

                        // Inject Geolocation sync hook to feed native background GPS into webpage maps
                        val jsGeoBridge = """
                            (function() {
                                if (window._vrGeoBridgeInjected) return;
                                window._vrGeoBridgeInjected = true;

                                if (navigator.geolocation) {
                                    var origGet = navigator.geolocation.getCurrentPosition.bind(navigator.geolocation);
                                    navigator.geolocation.getCurrentPosition = function(success, error, options) {
                                        if (window._vrCurrentLocation && success) {
                                            var loc = window._vrCurrentLocation;
                                            success({
                                                coords: {
                                                    latitude: loc.latitude,
                                                    longitude: loc.longitude,
                                                    altitude: loc.altitude || 0,
                                                    accuracy: loc.accuracy || 5,
                                                    altitudeAccuracy: loc.accuracy || 5,
                                                    heading: loc.heading || 0,
                                                    speed: loc.speed || 0
                                                },
                                                timestamp: loc.timestamp || Date.now()
                                            });
                                            return;
                                        }
                                        origGet(success, error, options);
                                    };
                                }
                            })();
                        """.trimIndent()

                        evaluateJavascript(jsGeoBridge, null)

                        // Google OAuth DOM & Click Interceptor: Intercepts Google Sign-In clicks and routes to Chrome
                        val jsGoogleOAuthHook = """
                            (function() {
                                if (window._vrGoogleOAuthHookInjected) return;
                                window._vrGoogleOAuthHookInjected = true;

                                function isGoogleUrl(u) {
                                    if (!u) return false;
                                    var lower = u.toLowerCase();
                                    return lower.indexOf('accounts.google.com') !== -1 ||
                                           lower.indexOf('oauth2.googleapis.com') !== -1 ||
                                           (lower.indexOf('client_id') !== -1 && lower.indexOf('googleusercontent.com') !== -1) ||
                                           lower.indexOf('auth/google') !== -1 ||
                                           lower.indexOf('login/google') !== -1 ||
                                           lower.indexOf('oauth/google') !== -1 ||
                                           lower.indexOf('google_login') !== -1 ||
                                           lower.indexOf('google-login') !== -1 ||
                                           lower.indexOf('provider=google') !== -1;
                                }

                                // Intercept window.open for Google Auth
                                var origOpen = window.open;
                                window.open = function(url, target, features) {
                                    if (url && isGoogleUrl(url) && window.AndroidAuth) {
                                        window.AndroidAuth.openOAuth(url);
                                        return null;
                                    }
                                    return origOpen.apply(this, arguments);
                                };

                                // Intercept DOM clicks on Google Login buttons & links
                                document.addEventListener('click', function(e) {
                                    var el = e.target.closest('a, button, [role="button"], .g-signin2, .g_id_signin, [id*="google"], [class*="google"]');
                                    if (!el) return;

                                    var href = el.getAttribute('href') || el.getAttribute('data-url') || '';
                                    var text = (el.innerText || el.textContent || '').trim().toLowerCase();

                                    var isGoogleBtn = isGoogleUrl(href) ||
                                                      text.indexOf('google') !== -1 ||
                                                      (el.id && el.id.toLowerCase().indexOf('google') !== -1) ||
                                                      (el.className && typeof el.className === 'string' && el.className.toLowerCase().indexOf('google') !== -1);

                                    if (isGoogleBtn && window.AndroidAuth) {
                                        if (href && isGoogleUrl(href)) {
                                            e.preventDefault();
                                            e.stopPropagation();
                                            window.AndroidAuth.openOAuth(href);
                                        } else if (href && (href.startsWith('http://') || href.startsWith('https://') || href.startsWith('/'))) {
                                            // Normal link that might redirect to Google
                                            if (href.startsWith('/')) {
                                                href = window.location.origin + href;
                                            }
                                            e.preventDefault();
                                            e.stopPropagation();
                                            window.AndroidAuth.openOAuth(href);
                                        }
                                    }
                                }, true);
                            })();
                        """.trimIndent()
                        evaluateJavascript(jsGoogleOAuthHook, null)

                        // Immediately inject last known location if available
                        BackgroundLocationManager.currentLocation.value?.let { loc ->
                            BackgroundLocationManager.injectLocationIntoWebView(view, loc)
                        }
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val targetUri = request?.url ?: return false
                        val urlString = targetUri.toString()
                        val scheme = targetUri.scheme?.lowercase() ?: ""

                        // Intercept Google OAuth requests and open in Google Chrome
                        if (GoogleOAuthHelper.isGoogleOAuthUrl(urlString)) {
                            GoogleOAuthHelper.openInChrome(context, urlString)
                            return true
                        }

                        // External app schemes (phone, mail, maps, whatsapp)
                        if (scheme == "tel" || scheme == "mailto" || scheme == "geo" || scheme == "whatsapp") {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, targetUri)
                                context.startActivity(intent)
                                return true
                            } catch (_: Exception) {
                                return true
                            }
                        }

                        // Direct APK update installer interception
                        val lowerUrl = urlString.lowercase()
                        val isApkUpdateFile = lowerUrl.endsWith(".apk") || lowerUrl.contains("vagabond-riders.apk")
                        if (isApkUpdateFile) {
                            AppUpdateManager.startDownload(context)
                            onAppUpdateRequested()
                            return true
                        }

                        // Direct file download URL extensions or export query parameters
                        val isDirectDownloadFile = lowerUrl.endsWith(".pdf") ||
                                lowerUrl.endsWith(".xlsx") ||
                                lowerUrl.endsWith(".xls") ||
                                lowerUrl.endsWith(".csv") ||
                                lowerUrl.endsWith(".docx") ||
                                lowerUrl.endsWith(".doc") ||
                                lowerUrl.contains("export=pdf") ||
                                lowerUrl.contains("export=excel") ||
                                lowerUrl.contains("export=xlsx") ||
                                lowerUrl.contains("export=csv") ||
                                lowerUrl.contains("download=true") ||
                                lowerUrl.contains("format=pdf") ||
                                lowerUrl.contains("format=excel") ||
                                lowerUrl.contains("action=export") ||
                                lowerUrl.contains("get_pdf") ||
                                lowerUrl.contains("print_pdf")

                        if (isDirectDownloadFile) {
                            FileDownloadHelper.startDownload(
                                context = context,
                                url = urlString,
                                onDownloadStarted = onDownloadStarted,
                                onDownloadFinished = onDownloadFinished
                            )
                            return true
                        }

                        return false
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        if (request?.isForMainFrame == true) {
                            val description = error?.description?.toString() ?: "Network error"
                            onError("Connection error: $description")
                        }
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?
                    ) {
                        if (request?.isForMainFrame == true) {
                            val statusCode = errorResponse?.statusCode ?: 0
                            if (statusCode >= 400) {
                                onError("HTTP Error $statusCode: Unable to load Vagabond Riders Portal")
                            }
                        }
                    }

                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?
                    ) {
                        // For secure sites, handle gracefully
                        onError("Security/SSL Notice: Verifying portal connection")
                        handler?.proceed()
                    }
                }

                loadUrl(url)
                currentWebView = this
                onWebViewCreated(this)
            }
        },
        update = { webView ->
            currentWebView = webView
        },
        modifier = modifier.testTag("vr_web_view")
    )

    DisposableEffect(Unit) {
        onDispose {
            // Safety: Stop camera streams and pause WebView to ensure camera does not stay open
            currentWebView?.evaluateJavascript("if (window._vrStopCameraStreams) window._vrStopCameraStreams();", null)
            currentWebView?.onPause()
            fileChooserCallback?.onReceiveValue(null)
            fileChooserCallback = null
            pendingCameraPermissionRequest = null
            currentWebView = null
        }
    }
}
