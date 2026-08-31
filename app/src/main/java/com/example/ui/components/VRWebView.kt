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
import com.example.media.VRMusicJavascriptBridge
import com.example.media.VRMusicManager
import com.example.media.VRTrack
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
    isVisible: Boolean = true,
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

    val musicBridge = remember {
        VRMusicJavascriptBridge(context)
    }

    DisposableEffect(Unit) {
        VRMusicManager.onWebMediaActionListener = { action, _ ->
            currentWebView?.post {
                currentWebView?.evaluateJavascript(
                    "if (window.onVRMusicAction) window.onVRMusicAction('$action');",
                    null
                )
            }
        }
        onDispose {
            VRMusicManager.onWebMediaActionListener = null
        }
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
                addJavascriptInterface(musicBridge, "VRMusicPlayer")
                addJavascriptInterface(musicBridge, "AndroidMusic")
                addJavascriptInterface(musicBridge, "VagabondMusic")

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

                        // Vagabond Riders Music & Lock-Screen MediaSession Bridge:
                        val jsMusicLockScreenHook = """
                            (function() {
                                function cleanText(t) {
                                    if (!t) return '';
                                    try {
                                        var txt = document.createElement('textarea');
                                        txt.innerHTML = t;
                                        var decoded = txt.value || t;
                                        return decoded
                                            .replace(/&quot;/g, '"')
                                            .replace(/&#039;/g, "'")
                                            .replace(/&#39;/g, "'")
                                            .replace(/&amp;/g, '&')
                                            .replace(/&lt;/g, '<')
                                            .replace(/&gt;/g, '>')
                                            .replace(/&nbsp;/g, ' ')
                                            .replace(/\s+/g, ' ')
                                            .trim();
                                    } catch(e) {
                                        return t.replace(/\s+/g, ' ').trim();
                                    }
                                }

                                function getBridge() {
                                    return window.VRMusicPlayer || window.AndroidMusic || window.VagabondMusic;
                                }

                                window._vrActiveAudios = window._vrActiveAudios || [];
                                window._vrCurrentAudio = window._vrCurrentAudio || null;

                                function trackAudioElement(audio) {
                                    if (!audio) return;
                                    if (window._vrActiveAudios.indexOf(audio) === -1) {
                                        window._vrActiveAudios.push(audio);
                                    }
                                    if (audio._vrHooked) return;
                                    audio._vrHooked = true;

                                    var events = ['play', 'playing', 'pause', 'ended', 'timeupdate', 'durationchange', 'loadedmetadata', 'canplay', 'loadeddata'];
                                    events.forEach(function(evt) {
                                        audio.addEventListener(evt, function() {
                                            if (evt === 'play' || evt === 'playing') {
                                                window._vrCurrentAudio = audio;
                                                extractAndSyncNowPlaying(true);
                                            } else if (evt === 'pause' || evt === 'ended') {
                                                extractAndSyncNowPlaying(false);
                                            } else if (evt === 'timeupdate') {
                                                if (!audio.paused) {
                                                    var bridge = getBridge();
                                                    if (bridge && audio.duration && !isNaN(audio.duration)) {
                                                        bridge.syncPlaybackState(true, Math.round(audio.currentTime * 1000), Math.round(audio.duration * 1000));
                                                    }
                                                }
                                            } else {
                                                extractAndSyncNowPlaying();
                                            }
                                        }, true);
                                    });
                                }

                                // Intercept HTMLMediaElement play/pause prototype methods
                                if (window.HTMLMediaElement && window.HTMLMediaElement.prototype) {
                                    if (!window.HTMLMediaElement.prototype._vrPlayHooked) {
                                        var origPlay = window.HTMLMediaElement.prototype.play;
                                        window.HTMLMediaElement.prototype.play = function() {
                                            window._vrCurrentAudio = this;
                                            trackAudioElement(this);
                                            setTimeout(function() { extractAndSyncNowPlaying(true); }, 50);
                                            setTimeout(function() { extractAndSyncNowPlaying(true); }, 200);
                                            setTimeout(function() { extractAndSyncNowPlaying(true); }, 600);
                                            return origPlay.apply(this, arguments);
                                        };
                                        window.HTMLMediaElement.prototype._vrPlayHooked = true;
                                    }

                                    if (!window.HTMLMediaElement.prototype._vrPauseHooked) {
                                        var origPause = window.HTMLMediaElement.prototype.pause;
                                        window.HTMLMediaElement.prototype.pause = function() {
                                            var res = origPause.apply(this, arguments);
                                            setTimeout(function() { extractAndSyncNowPlaying(false); }, 50);
                                            return res;
                                        };
                                        window.HTMLMediaElement.prototype._vrPauseHooked = true;
                                    }

                                    try {
                                        var origSrcDescriptor = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'src');
                                        if (origSrcDescriptor && !HTMLMediaElement.prototype._vrSrcHooked) {
                                            Object.defineProperty(HTMLMediaElement.prototype, 'src', {
                                                configurable: true,
                                                enumerable: true,
                                                get: function() {
                                                    return origSrcDescriptor.get ? origSrcDescriptor.get.call(this) : this.getAttribute('src');
                                                },
                                                set: function(val) {
                                                    this._vrExplicitSrc = val;
                                                    if (origSrcDescriptor.set) {
                                                        origSrcDescriptor.set.call(this, val);
                                                    } else {
                                                        this.setAttribute('src', val);
                                                    }
                                                    trackAudioElement(this);
                                                    setTimeout(function() { extractAndSyncNowPlaying(); }, 50);
                                                }
                                            });
                                            HTMLMediaElement.prototype._vrSrcHooked = true;
                                        }
                                    } catch(e) {}
                                }

                                // Intercept Audio constructor
                                if (window.Audio && !window.Audio._vrHooked) {
                                    var OrigAudio = window.Audio;
                                    window.Audio = function() {
                                        var instance = new OrigAudio(...arguments);
                                        if (arguments.length > 0 && typeof arguments[0] === 'string') {
                                            instance._vrInitialSrc = arguments[0];
                                        }
                                        trackAudioElement(instance);
                                        return instance;
                                    };
                                    window.Audio._vrHooked = true;
                                }

                                // Intercept document.createElement for audio/video
                                if (document.createElement && !document.createElement._vrHooked) {
                                    var origCreateElement = document.createElement;
                                    document.createElement = function(tagName) {
                                        var el = origCreateElement.apply(this, arguments);
                                        if (el && (tagName.toLowerCase() === 'audio' || tagName.toLowerCase() === 'video')) {
                                            trackAudioElement(el);
                                        }
                                        return el;
                                    };
                                    document.createElement._vrHooked = true;
                                }

                                function extractAndSyncNowPlaying(forceState) {
                                    var bridge = getBridge();
                                    if (!bridge) return;
                                    try {
                                        var title = '';
                                        var artist = '';
                                        var album = 'VR Music';
                                        var artwork = '';
                                        var streamUrl = '';

                                        // 1. Check window.currentSong / window.nowPlaying / window.currentTrack / window.activeSong / window.song
                                        var cs = window.currentSong || window.nowPlaying || window.currentTrack || window.activeSong || window.activeTrack || window.song || (window.player && window.player.currentSong) || (window.audioPlayer && window.audioPlayer.currentTrack);
                                        if (cs && typeof cs === 'object') {
                                            title = cs.name || cs.title || cs.song_name || cs.songName || cs.track_name || '';
                                            artist = cs.artist || cs.singer || cs.artist_name || cs.subtitle || cs.singer_name || '';
                                            album = cs.album || cs.category || cs.album_name || 'VR Music';
                                            artwork = cs.image || cs.image_url || cs.poster || cs.thumbnail || cs.cover || cs.artwork || cs.art || '';
                                            streamUrl = cs.url || cs.src || cs.streamUrl || cs.stream_url || cs.media_url || cs.download_url || cs.file || cs.mp3 || cs.audio || '';
                                        }

                                        // 2. Check window.navigator.mediaSession.metadata
                                        if ((!title || title === 'Track Name') && window.navigator && window.navigator.mediaSession && window.navigator.mediaSession.metadata) {
                                            var m = window.navigator.mediaSession.metadata;
                                            if (m.title) title = m.title;
                                            if (m.artist && (!artist || artist === 'Artist')) artist = m.artist;
                                            if (m.album) album = m.album;
                                            if (!artwork && m.artwork && m.artwork.length > 0) artwork = m.artwork[0].src || '';
                                        }

                                        // 3. Scan DOM player bar and now playing elements
                                        if (!title || title === 'Track Name') {
                                            var titleEl = document.querySelector('#nowPlayingTitle, #playerBar .title, #playerBar .song-title, #playerBar .song-name, #bottom-player .title, .bottom-player .title, .music-player .title, .player-bar .title, .player-bar .song-title, .now-playing .title, .current-song-title, .track-title, .song-title, #songTitle, #songName, .song-name, .player-song-title, [data-player-title], [data-song-title], [data-title], .song-info h4, .song-info h3, .song-info h5, .player-info .title, .player-info h4, .player-info h5, .aplayer-title, .jp-title');
                                            if (titleEl) title = cleanText(titleEl.innerText || titleEl.textContent);
                                        }

                                        if (!artist || artist === 'Artist') {
                                            var artistEl = document.querySelector('#nowPlayingArtist, #playerBar .artist, #playerBar .singer, #playerBar .subtitle, #bottom-player .artist, .bottom-player .artist, .music-player .artist, .player-bar .artist, .now-playing .artist, .current-song-artist, .song-artist, #artistName, #songArtist, .player-artist, [data-player-artist], [data-song-artist], [data-artist], .song-info p, .song-info span, .player-info .artist, .player-info p, .aplayer-author, .jp-artist');
                                            if (artistEl) artist = cleanText(artistEl.innerText || artistEl.textContent);
                                        }

                                        if (!artwork) {
                                            var imgEl = document.querySelector('#nowPlayingImg, #playerBar img, #bottom-player img, .bottom-player img, .player-cover img, .player-thumb img, #albumArt, #songPoster, .current-poster, [data-player-poster], .player-bar img, .now-playing img, .aplayer-pic');
                                            if (imgEl && imgEl.src && imgEl.src.length > 5 && !imgEl.src.includes('data:image/svg') && !imgEl.src.includes('placeholder')) {
                                                artwork = imgEl.src;
                                            }
                                        }

                                        // 4. Scan active song row in table/playlist
                                        if (!title || title === 'Track Name') {
                                            var activeRow = document.querySelector('.song-row.active, .song-row.playing, tr.active, tr.playing, .list-group-item.active, [data-playing="true"], .playlist-item.active, .track-item.active');
                                            if (activeRow) {
                                                var rTitle = activeRow.querySelector('.title, .song-name, .track-name, h4, h5, strong, td.title');
                                                if (rTitle) title = cleanText(rTitle.innerText || rTitle.textContent);
                                                if (!artist || artist === 'Artist') {
                                                    var rArtist = activeRow.querySelector('.artist, .singer, .subtitle, p, span.text-muted, td.artist');
                                                    if (rArtist) artist = cleanText(rArtist.innerText || rArtist.textContent);
                                                }
                                                if (!artwork) {
                                                    var rImg = activeRow.querySelector('.poster, img, .thumb');
                                                    if (rImg && rImg.src) artwork = rImg.src;
                                                }
                                                if (!streamUrl) {
                                                    streamUrl = activeRow.getAttribute('data-url') || activeRow.getAttribute('data-src') || activeRow.getAttribute('data-stream') || '';
                                                }
                                            }
                                        }

                                        // 5. Check active audio element
                                        var activeAudio = window._vrCurrentAudio;
                                        if (!activeAudio || activeAudio.paused) {
                                            var domAudios = Array.from(document.getElementsByTagName('audio'));
                                            var allAudios = (window._vrActiveAudios || []).concat(domAudios);
                                            for (var i = 0; i < allAudios.length; i++) {
                                                var a = allAudios[i];
                                                if (a && (!a.paused || a.currentTime > 0)) {
                                                    activeAudio = a;
                                                    window._vrCurrentAudio = a;
                                                    trackAudioElement(a);
                                                    break;
                                                }
                                            }
                                            if (!activeAudio && domAudios.length > 0) {
                                                activeAudio = domAudios[0];
                                                window._vrCurrentAudio = activeAudio;
                                                trackAudioElement(activeAudio);
                                            }
                                        }

                                        // Extract streamUrl from activeAudio if not already set
                                        if (!streamUrl && activeAudio) {
                                            streamUrl = activeAudio.currentSrc || activeAudio.src || activeAudio._vrExplicitSrc || activeAudio._vrInitialSrc || activeAudio.getAttribute('src') || '';
                                            if (!streamUrl) {
                                                var sourceEl = activeAudio.querySelector('source');
                                                if (sourceEl) streamUrl = sourceEl.src || sourceEl.getAttribute('src') || '';
                                            }
                                        }

                                        // 6. Audio src filename fallback
                                        if ((!title || title === 'Track Name') && activeAudio && (activeAudio.src || activeAudio.currentSrc)) {
                                            var rawSrc = activeAudio.src || activeAudio.currentSrc;
                                            var srcParts = rawSrc.split('?')[0].split('/');
                                            var fileName = decodeURIComponent(srcParts[srcParts.length - 1] || '');
                                            if (fileName) {
                                                title = fileName.replace(/\.[^/.]+$/, "").replace(/[_-]/g, " ");
                                            }
                                        }

                                        // 7. Page title fallback
                                        if (!artist || artist === 'Artist') artist = 'Vagabond Riders';
                                        if (!title || title === 'Track Name') {
                                            var dt = document.title || '';
                                            if (dt && !dt.toLowerCase().includes('vagabond') && !dt.toLowerCase().includes('index.php')) {
                                                title = dt.split('-')[0].trim();
                                            }
                                        }

                                        // Clean text
                                        title = cleanText(title);
                                        artist = cleanText(artist);
                                        album = cleanText(album);

                                        // Resolve relative streamUrl to absolute
                                        if (streamUrl && !streamUrl.startsWith('http://') && !streamUrl.startsWith('https://') && !streamUrl.startsWith('blob:')) {
                                            try {
                                                streamUrl = new URL(streamUrl, window.location.href).href;
                                            } catch(e) {}
                                        }

                                        var duration = 0;
                                        var currentTime = 0;
                                        var isPlaying = false;

                                        if (activeAudio) {
                                            if (activeAudio.duration && !isNaN(activeAudio.duration)) duration = Math.round(activeAudio.duration * 1000);
                                            if (activeAudio.currentTime && !isNaN(activeAudio.currentTime)) currentTime = Math.round(activeAudio.currentTime * 1000);
                                            isPlaying = (forceState !== undefined) ? forceState : (!activeAudio.paused && !activeAudio.ended);
                                        } else if (forceState !== undefined) {
                                            isPlaying = forceState;
                                        }

                                        if (title && title !== 'Track Name' && title !== 'index.php') {
                                            bridge.syncNowPlaying(title, artist, album, artwork, isPlaying, currentTime, duration, streamUrl);
                                        }
                                    } catch(e) {
                                        console.log('VR sync error: ' + e);
                                    }
                                }

                                window.extractAndSyncVRMusic = extractAndSyncNowPlaying;

                                // Intercept MediaMetadata constructor
                                if (window.MediaMetadata && !window.MediaMetadata._vrHooked) {
                                    try {
                                        var OrigMediaMetadata = window.MediaMetadata;
                                        window.MediaMetadata = function(init) {
                                            var instance = new OrigMediaMetadata(init);
                                            try {
                                                var bridge = getBridge();
                                                if (init && bridge) {
                                                    var title = cleanText(init.title || '');
                                                    var artist = cleanText(init.artist || 'Vagabond Riders');
                                                    var album = cleanText(init.album || 'VR Music');
                                                    var artwork = (init.artwork && init.artwork.length > 0) ? (init.artwork[0].src || '') : '';
                                                    if (title && title !== 'Track Name') {
                                                        bridge.syncNowPlaying(title, artist, album, artwork, true, 0, 0, null);
                                                    }
                                                }
                                            } catch(e) {}
                                            return instance;
                                        };
                                        window.MediaMetadata._vrHooked = true;
                                    } catch(e) {}
                                }

                                // Intercept navigator.mediaSession.metadata & setActionHandler
                                if (window.navigator && window.navigator.mediaSession) {
                                    try {
                                        var _mediaSessionMeta = window.navigator.mediaSession.metadata;
                                        Object.defineProperty(window.navigator.mediaSession, 'metadata', {
                                            configurable: true,
                                            enumerable: true,
                                            get: function() { return _mediaSessionMeta; },
                                            set: function(meta) {
                                                _mediaSessionMeta = meta;
                                                if (meta) {
                                                    var bridge = getBridge();
                                                    if (bridge) {
                                                        var title = meta.title || '';
                                                        var artist = meta.artist || 'Vagabond Riders';
                                                        var album = meta.album || 'VR Music';
                                                        var artwork = (meta.artwork && meta.artwork.length > 0) ? (meta.artwork[0].src || '') : '';
                                                        if (title && title !== 'Track Name') {
                                                            bridge.syncNowPlaying(title, artist, album, artwork, true, 0, 0);
                                                        }
                                                    }
                                                }
                                            }
                                        });
                                    } catch(e) {}

                                    var origSetActionHandler = window.navigator.mediaSession.setActionHandler;
                                    window._vrWebMediaHandlers = window._vrWebMediaHandlers || {};
                                    window.navigator.mediaSession.setActionHandler = function(action, handler) {
                                        window._vrWebMediaHandlers[action] = handler;
                                        return origSetActionHandler ? origSetActionHandler.apply(this, arguments) : null;
                                    };
                                }

                                // Intercept playSong / setupMediaSession if defined by PHP page
                                var origPlaySong = window.playSong;
                                Object.defineProperty(window, 'playSong', {
                                    configurable: true,
                                    get: function() { return origPlaySong; },
                                    set: function(fn) {
                                        origPlaySong = function(context, index) {
                                            var res = fn.apply(this, arguments);
                                            setTimeout(function() { extractAndSyncNowPlaying(true); }, 50);
                                            setTimeout(function() { extractAndSyncNowPlaying(true); }, 200);
                                            setTimeout(function() { extractAndSyncNowPlaying(true); }, 500);
                                            return res;
                                        };
                                    }
                                });

                                // Native lock-screen action callback handler
                                window.onVRMusicAction = function(action) {
                                    if (window._vrWebMediaHandlers && window._vrWebMediaHandlers[action]) {
                                        try { window._vrWebMediaHandlers[action]({ action: action }); } catch(e) {}
                                    }
                                    if (action === 'next') {
                                        if (typeof window.playNext === 'function') { try { window.playNext(); return; } catch(e) {} }
                                        if (typeof window.nextSong === 'function') { try { window.nextSong(); return; } catch(e) {} }
                                        if (typeof window.next === 'function') { try { window.next(); return; } catch(e) {} }
                                        var nextBtn = document.querySelector('#nextBtn, .next-btn, [data-action="next"], .btn-next, #btnNext, .fa-forward, .fa-step-forward, [title*="Next"]');
                                        if (nextBtn) { nextBtn.click(); return; }
                                    }
                                    if (action === 'previous') {
                                        if (typeof window.playPrev === 'function') { try { window.playPrev(); return; } catch(e) {} }
                                        if (typeof window.prevSong === 'function') { try { window.prevSong(); return; } catch(e) {} }
                                        if (typeof window.prev === 'function') { try { window.prev(); return; } catch(e) {} }
                                        var prevBtn = document.querySelector('#prevBtn, .prev-btn, [data-action="prev"], .btn-prev, #btnPrev, .fa-backward, .fa-step-backward, [title*="Prev"]');
                                        if (prevBtn) { prevBtn.click(); return; }
                                    }
                                    var targetAudio = window._vrCurrentAudio || document.querySelector('audio');
                                    if (!targetAudio) {
                                        var allAudios = document.getElementsByTagName('audio');
                                        if (allAudios.length > 0) targetAudio = allAudios[0];
                                    }
                                    if (targetAudio) {
                                        if (action === 'play') targetAudio.play().catch(function(){});
                                        else if (action === 'pause') targetAudio.pause();
                                        else if (action === 'toggle') { if (targetAudio.paused) targetAudio.play().catch(function(){}); else targetAudio.pause(); }
                                        else if (action === 'seekBackward') targetAudio.currentTime = Math.max(0, targetAudio.currentTime - 10);
                                        else if (action === 'seekForward') targetAudio.currentTime = Math.min(targetAudio.duration || (targetAudio.currentTime + 10), targetAudio.currentTime + 10);
                                        else if (action.startsWith('seekTo:')) {
                                            var targetMs = parseFloat(action.split(':')[1]);
                                            if (!isNaN(targetMs)) targetAudio.currentTime = targetMs / 1000.0;
                                        } else if (action.startsWith('seekRelative:')) {
                                            var deltaSec = parseFloat(action.split(':')[1]);
                                            if (!isNaN(deltaSec)) {
                                                var newPos = targetAudio.currentTime + deltaSec;
                                                if (targetAudio.duration && !isNaN(targetAudio.duration)) newPos = Math.min(targetAudio.duration, newPos);
                                                targetAudio.currentTime = Math.max(0, newPos);
                                            }
                                        }
                                    }
                                };

                                // Global document listeners
                                document.addEventListener('click', function(e) {
                                    setTimeout(function() { extractAndSyncNowPlaying(); }, 100);
                                    setTimeout(function() { extractAndSyncNowPlaying(); }, 400);
                                }, true);

                                // Continuous poller (runs every 1 second)
                                if (!window._vrMusicPoller) {
                                    window._vrMusicPoller = setInterval(function() {
                                        extractAndSyncNowPlaying();
                                    }, 1000);
                                }

                                // Attach existing DOM audio elements
                                var existingAudios = document.getElementsByTagName('audio');
                                for (var i = 0; i < existingAudios.length; i++) {
                                    trackAudioElement(existingAudios[i]);
                                }

                                // Initial run
                                extractAndSyncNowPlaying();
                            })();
                        """.trimIndent()
                        evaluateJavascript(jsMusicLockScreenHook, null)

                        // Vagabond Riders Password Manager & Login DOM Detector Hook:
                        val jsPasswordManagerHook = """
                            (function() {
                                if (window._vrPasswordHookInjected) return;
                                window._vrPasswordHookInjected = true;

                                function getAuthBridge() {
                                    return window.AndroidAuth || window.VRAuth;
                                }

                                function scanForLoginFields() {
                                    try {
                                        var passInputs = document.querySelectorAll('input[type="password"], input[name*="pass" i], input[name*="pwd" i], input[id*="pass" i], input[id*="pwd" i]');
                                        var userInputs = document.querySelectorAll('input[type="text"], input[type="email"], input[name*="user" i], input[name*="email" i], input[name*="login" i], input[id*="user" i], input[id*="email" i], input[id*="login" i]');
                                        
                                        var hasLogin = passInputs.length > 0 || (userInputs.length > 0 && document.querySelector('button[type="submit"], input[type="submit"], [data-action="login"]'));
                                        var bridge = getAuthBridge();
                                        if (bridge && typeof bridge.onLoginFormDetected === 'function') {
                                            bridge.onLoginFormDetected(hasLogin, 'detected:' + passInputs.length);
                                        }
                                    } catch(e) {}
                                }

                                function hookInputs() {
                                    try {
                                        var inputs = document.querySelectorAll('input');
                                        inputs.forEach(function(inp) {
                                            if (inp._vrAuthHooked) return;
                                            inp._vrAuthHooked = true;
                                            
                                            var isPass = inp.type === 'password' || (inp.name && inp.name.toLowerCase().indexOf('pass') !== -1) || (inp.id && inp.id.toLowerCase().indexOf('pass') !== -1);
                                            var isUser = (inp.type === 'text' || inp.type === 'email') && ((inp.name && (inp.name.toLowerCase().indexOf('user') !== -1 || inp.name.toLowerCase().indexOf('login') !== -1 || inp.name.toLowerCase().indexOf('email') !== -1)) || (inp.id && (inp.id.toLowerCase().indexOf('user') !== -1 || inp.id.toLowerCase().indexOf('login') !== -1 || inp.id.toLowerCase().indexOf('email') !== -1)));
                                            
                                            if (isPass || isUser) {
                                                inp.addEventListener('focus', function() {
                                                    var bridge = getAuthBridge();
                                                    if (bridge && typeof bridge.onLoginFormDetected === 'function') {
                                                        bridge.onLoginFormDetected(true, 'focus');
                                                    }
                                                });
                                            }
                                        });
                                    } catch(e) {}
                                }

                                scanForLoginFields();
                                hookInputs();

                                if (window.MutationObserver) {
                                    var observer = new MutationObserver(function() {
                                        scanForLoginFields();
                                        hookInputs();
                                    });
                                    observer.observe(document.body || document.documentElement, {
                                        childList: true,
                                        subtree: true
                                    });
                                }
                            })();
                        """.trimIndent()
                        evaluateJavascript(jsPasswordManagerHook, null)

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

                        // Direct Audio file link interception -> plays in background player with lock-screen controls
                        val isAudioStreamFile = lowerUrl.endsWith(".mp3") ||
                                lowerUrl.endsWith(".m4a") ||
                                lowerUrl.endsWith(".wav") ||
                                lowerUrl.endsWith(".aac") ||
                                lowerUrl.endsWith(".ogg") ||
                                lowerUrl.endsWith(".flac")

                        if (isAudioStreamFile) {
                            val trackTitle = targetUri.lastPathSegment?.substringBeforeLast(".") ?: "Vagabond Music"
                            val track = VRTrack(
                                title = trackTitle.replace("_", " ").replace("-", " "),
                                artist = "Vagabond Riders",
                                streamUrl = urlString
                            )
                            VRMusicManager.playTrack(context, track)
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
            webView.visibility = if (isVisible) android.view.View.VISIBLE else android.view.View.INVISIBLE
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
