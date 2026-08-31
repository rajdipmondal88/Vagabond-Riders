package com.example.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.auth.GoogleOAuthHelper
import com.example.data.DownloadedFile
import com.example.data.WebTab
import com.example.location.BackgroundLocationManager
import com.example.location.LocationData
import com.example.navigation.NavigationManager
import com.example.network.NetworkMonitor
import com.example.notifications.PushNotificationManager
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import com.example.media.VRMusicManager
import com.example.ui.components.AppUpdateBottomSheet
import com.example.ui.components.BottomTabBar
import com.example.ui.components.DownloadNotificationBanner
import com.example.ui.components.DownloadsBottomSheet
import com.example.ui.components.ErrorOverlay
import com.example.ui.components.LocationNavigationSheet
import com.example.ui.components.PasswordManagerBottomSheet
import com.example.ui.components.PushNotificationBottomSheet
import com.example.ui.components.QuickLoginChipBar
import com.example.ui.components.SplashScreen
import com.example.ui.components.VRMiniMusicPlayer
import com.example.ui.components.VRMusicPlayerBottomSheet
import com.example.ui.components.VRWebView
import com.example.ui.components.VagabondLogoBadge
import com.example.ui.theme.GeoBackground
import com.example.ui.theme.GeoOnBackground
import com.example.ui.theme.GeoOnSurfaceVariant
import com.example.ui.theme.GeoSurface
import com.example.ui.viewmodel.PasswordManagerViewModel
import com.example.updater.AppUpdateManager
import com.example.updater.UpdateState
import com.example.utils.CustomLogoManager
import com.example.utils.FileDownloadHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

const val VR_PORTAL_URL = "https://app.vagabondriders.com/index.php"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VRAppScreen(
    modifier: Modifier = Modifier,
    initialUrl: String = VR_PORTAL_URL,
    passwordViewModel: PasswordManagerViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val networkMonitor = remember { NetworkMonitor(context) }
    val isOnline by networkMonitor.isOnline.collectAsState(initial = true)

    // Location & Background Navigation State
    val isLocationServiceRunning by BackgroundLocationManager.isServiceRunning.collectAsStateWithLifecycle()
    val currentLocationData by BackgroundLocationManager.currentLocation.collectAsStateWithLifecycle()
    val hasFineLocationPermission by BackgroundLocationManager.hasFineLocationPermission.collectAsStateWithLifecycle()
    val hasBackgroundLocationPermission by BackgroundLocationManager.hasBackgroundLocationPermission.collectAsStateWithLifecycle()

    var showLocationSheet by remember { mutableStateOf(false) }
    val locationSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Permission Launchers
    val foregroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val fineGranted = permissionsMap[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissionsMap[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        BackgroundLocationManager.refreshPermissionStates(context)
        if (fineGranted || coarseGranted) {
            BackgroundLocationManager.startService(context)
        }
    }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        BackgroundLocationManager.refreshPermissionStates(context)
        if (isGranted) {
            Toast.makeText(context, "Background location enabled all the time!", Toast.LENGTH_SHORT).show()
            BackgroundLocationManager.startService(context)
        } else {
            Toast.makeText(context, "Select 'Allow all the time' in App Permissions for 24/7 navigation", Toast.LENGTH_LONG).show()
        }
    }

    val requestBackgroundAccess: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (hasFineLocationPermission) {
                try {
                    backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                } catch (_: Exception) {
                    BackgroundLocationManager.openAppSettings(context)
                }
            } else {
                val perms = mutableListOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    perms.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                foregroundLocationLauncher.launch(perms.toTypedArray())
            }
        } else {
            foregroundLocationLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Initialize Downloads, Custom Logo Manager, and Background Location
    LaunchedEffect(Unit) {
        FileDownloadHelper.init(context)
        CustomLogoManager.init(context)
        BackgroundLocationManager.init(context)
    }

    val savedCredentials by passwordViewModel.savedCredentials.collectAsStateWithLifecycle()
    val autoFillOnPageLoad by passwordViewModel.autoFillOnPageLoad.collectAsStateWithLifecycle()

    // Download States
    val downloadHistory by FileDownloadHelper.downloadHistory.collectAsStateWithLifecycle()
    val activeDownload by FileDownloadHelper.activeDownload.collectAsStateWithLifecycle()
    val lastCompletedDownload by FileDownloadHelper.lastCompletedDownload.collectAsStateWithLifecycle()

    // Push Notifications State
    val pushNotificationsHistory by PushNotificationManager.notificationsHistory.collectAsStateWithLifecycle()
    var showPushSheet by remember { mutableStateOf(false) }
    val pushSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Notification Permission Launcher (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Push notifications enabled!", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val startDestinationUrl = remember { NavigationManager.consumeColdStartTargetUrl() ?: initialUrl }

    // Multi-Tab Management State
    val tabs = remember {
        mutableStateListOf(
            WebTab(
                id = UUID.randomUUID().toString(),
                title = "Home",
                url = startDestinationUrl
            )
        )
    }
    var activeTabId by remember { mutableStateOf(tabs.first().id) }
    val activeTab = tabs.find { it.id == activeTabId } ?: tabs.first()

    // Handle incoming Navigation Target URLs (Push Notifications, direct links) & OAuth redirects
    LaunchedEffect(Unit) {
        // 1. Direct Target URL listener for Push Notifications
        launch {
            NavigationManager.targetUrlEvents.collect { targetUrl ->
                if (targetUrl.isNotBlank()) {
                    android.webkit.CookieManager.getInstance().flush()
                    activeTab.webView?.loadUrl(targetUrl)
                    val idx = tabs.indexOfFirst { it.id == activeTabId }
                    if (idx != -1) {
                        tabs[idx] = tabs[idx].copy(url = targetUrl)
                    }
                }
            }
        }

        // 2. Google OAuth return URL listener
        launch {
            GoogleOAuthHelper.incomingOAuthUrl.collect { returnUrl ->
                val finalUrl = if (returnUrl.isNotBlank()) returnUrl else "https://membership.vagabondriders.com/profile.php"
                android.webkit.CookieManager.getInstance().flush()
                activeTab.webView?.loadUrl(finalUrl)
                val idx = tabs.indexOfFirst { it.id == activeTabId }
                if (idx != -1) {
                    tabs[idx] = tabs[idx].copy(url = finalUrl)
                }
                Toast.makeText(context, "Google Sign-In completed! Logged in as Member.", Toast.LENGTH_SHORT).show()
            }
        }

        launch {
            GoogleOAuthHelper.authCompletionTrigger.collect {
                android.webkit.CookieManager.getInstance().flush()
                val profileUrl = "https://membership.vagabondriders.com/profile.php"
                activeTab.webView?.loadUrl(profileUrl)
                val idx = tabs.indexOfFirst { it.id == activeTabId }
                if (idx != -1) {
                    tabs[idx] = tabs[idx].copy(url = profileUrl)
                }
                Toast.makeText(context, "Authentication verified - logged into Profile!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Smart URL-based Background Location Trigger:
    // Starts GPS automatically ONLY when on https://app.vagabondriders.com/location/index.php
    // Automatically stops GPS when navigating away from that URL.
    LaunchedEffect(activeTab.url, activeTabId) {
        if (BackgroundLocationManager.isLocationTriggerUrl(activeTab.url)) {
            if (!hasFineLocationPermission) {
                val perms = mutableListOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    perms.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                foregroundLocationLauncher.launch(perms.toTypedArray())
            } else {
                BackgroundLocationManager.startService(context)
            }
        } else {
            BackgroundLocationManager.onActiveUrlChanged(context, activeTab.url)
        }
    }

    // Continuous location injection into active WebView for map sync
    LaunchedEffect(currentLocationData, activeTabId) {
        currentLocationData?.let { loc ->
            BackgroundLocationManager.injectLocationIntoWebView(activeTab.webView, loc)
        }
    }

    var showSplash by remember { mutableStateOf(true) }
    var backPressCount by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Bottom Sheets State
    var showPasswordSheet by remember { mutableStateOf(false) }
    val passwordSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showDownloadsSheet by remember { mutableStateOf(false) }
    val downloadsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showUpdateSheet by remember { mutableStateOf(false) }
    val updateSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val updateState by AppUpdateManager.updateState.collectAsState()

    // Music Player State & Remote open triggers
    var showMusicPlayerSheet by remember { mutableStateOf(false) }
    val musicPlayerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val musicPlaybackState by VRMusicManager.playbackState.collectAsStateWithLifecycle()
    val openMusicPlayerRequested by VRMusicManager.openPlayerSheetRequested.collectAsStateWithLifecycle()

    LaunchedEffect(openMusicPlayerRequested) {
        if (openMusicPlayerRequested) {
            showMusicPlayerSheet = true
            VRMusicManager.openPlayerSheetRequested.value = false
        }
    }

    var showTopOverflowMenu by remember { mutableStateOf(false) }

    // Splash duration timer & background update check
    LaunchedEffect(Unit) {
        delay(2000)
        showSplash = false
        AppUpdateManager.checkForUpdates(context)
    }

    // Auto-fill on login page
    val isLoginPage = activeTab.url.contains("login", ignoreCase = true) || 
                      activeTab.url.contains("logout", ignoreCase = true) || 
                      activeTab.url.contains("index", ignoreCase = true) ||
                      activeTab.url == VR_PORTAL_URL

    LaunchedEffect(isLoginPage, showSplash, savedCredentials.size, activeTabId) {
        if (isLoginPage && !showSplash && activeTab.webView != null) {
            delay(300)
            passwordViewModel.performAutoFillOnLoginLoad(activeTab.webView)
        }
    }

    // Function to add a new tab
    val addNewTab: (String, String) -> Unit = { targetUrl, title ->
        val newTab = WebTab(
            id = UUID.randomUUID().toString(),
            title = title.ifBlank { "New Tab" },
            url = targetUrl.ifBlank { VR_PORTAL_URL }
        )
        tabs.add(newTab)
        activeTabId = newTab.id
        Toast.makeText(context, "Opened in new tab", Toast.LENGTH_SHORT).show()
    }

    // Function to close a tab
    val closeTab: (String) -> Unit = { tabIdToClose ->
        if (tabs.size > 1) {
            val index = tabs.indexOfFirst { it.id == tabIdToClose }
            if (index != -1) {
                tabs.removeAt(index)
                if (activeTabId == tabIdToClose) {
                    val nextIndex = if (index > 0) index - 1 else 0
                    activeTabId = tabs[nextIndex].id
                }
            }
        }
    }

    // Handle Back Press
    BackHandler {
        val currentWv = activeTab.webView
        if (currentWv != null && currentWv.canGoBack()) {
            currentWv.goBack()
        } else if (tabs.size > 1) {
            closeTab(activeTabId)
        } else {
            if (backPressCount == 0) {
                backPressCount = 1
                Toast.makeText(context, "Press back again to exit Vagabond Riders", Toast.LENGTH_SHORT).show()
                coroutineScope.launch {
                    delay(2000)
                    backPressCount = 0
                }
            } else {
                (context as? android.app.Activity)?.finish()
            }
        }
    }

    // Share URL Handler
    val shareCurrentUrl: () -> Unit = {
        val shareUrl = activeTab.webView?.url ?: activeTab.url.ifBlank { VR_PORTAL_URL }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Vagabond Riders")
            putExtra(Intent.EXTRA_TEXT, "Vagabond Riders Portal: $shareUrl")
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Vagabond Riders Link"))
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("vr_main_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.testTag("top_bar_title_area")
                    ) {
                        VagabondLogoBadge(
                            size = 36.dp,
                            showRegistrationText = false
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = "Vagabond Riders",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.3.sp
                                ),
                                color = GeoOnBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (activeTab.title.isNotBlank() && activeTab.title != "Vagabond Riders") activeTab.title else "Official Portal",
                                style = MaterialTheme.typography.labelSmall,
                                color = GeoOnSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    // Live GPS & Background Navigation Button
                    IconButton(
                        onClick = { showLocationSheet = true },
                        modifier = Modifier.testTag("btn_gps_navigation")
                    ) {
                        BadgedBox(
                            badge = {
                                if (isLocationServiceRunning) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF16A34A))
                                    )
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isLocationServiceRunning) Icons.Default.Navigation else Icons.Default.LocationOn,
                                contentDescription = "Live GPS Navigation",
                                tint = if (isLocationServiceRunning) Color(0xFF16A34A) else Color(0xFFEA580C)
                            )
                        }
                    }

                    // Push Notifications Button
                    IconButton(
                        onClick = { showPushSheet = true },
                        modifier = Modifier.testTag("btn_push_notifications")
                    ) {
                        BadgedBox(
                            badge = {
                                if (pushNotificationsHistory.isNotEmpty()) {
                                    Badge(
                                        containerColor = Color(0xFFEA580C),
                                        contentColor = Color.White
                                    ) {
                                        Text(
                                            text = pushNotificationsHistory.size.toString(),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (pushNotificationsHistory.isNotEmpty()) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                contentDescription = "Push Notifications",
                                tint = Color(0xFFEA580C)
                            )
                        }
                    }

                    // Three-dot Overflow Menu (Share, Downloads Folder, App Updates, Saved Passwords)
                    Box {
                        val isUpdateAvailable = updateState is UpdateState.Available ||
                                updateState is UpdateState.Downloaded ||
                                updateState is UpdateState.Downloading
                        val hasPendingBadges = isUpdateAvailable || downloadHistory.isNotEmpty()

                        IconButton(
                            onClick = { showTopOverflowMenu = true },
                            modifier = Modifier.testTag("btn_top_overflow_menu")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (hasPendingBadges) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(if (isUpdateAvailable) Color(0xFFEA580C) else Color(0xFF0284C7))
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More Options",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showTopOverflowMenu,
                            onDismissRequest = { showTopOverflowMenu = false },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.width(230.dp)
                        ) {
                            // 1. Share Current Link
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = "Share Page",
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "Share portal link",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        tint = Color(0xFF16A34A),
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showTopOverflowMenu = false
                                    shareCurrentUrl()
                                },
                                modifier = Modifier.testTag("menu_item_share")
                            )

                            // 2. Downloads & Reports Folder
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Downloads Folder",
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = if (downloadHistory.isNotEmpty()) "${downloadHistory.size} saved file(s)" else "Reports & PDFs",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (downloadHistory.isNotEmpty()) {
                                            Badge(
                                                containerColor = Color(0xFF0284C7),
                                                contentColor = Color.White
                                            ) {
                                                Text(
                                                    text = downloadHistory.size.toString(),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = Color(0xFF0284C7),
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showTopOverflowMenu = false
                                    showDownloadsSheet = true
                                },
                                modifier = Modifier.testTag("menu_item_downloads")
                            )

                            // 3. In-App Direct Updates
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "App Update",
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = if (isUpdateAvailable) "Update ready" else "Direct APK installer",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isUpdateAvailable) Color(0xFFEA580C) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (isUpdateAvailable) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFEA580C))
                                            )
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.SystemUpdate,
                                        contentDescription = null,
                                        tint = Color(0xFFEA580C),
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showTopOverflowMenu = false
                                    showUpdateSheet = true
                                },
                                modifier = Modifier.testTag("menu_item_updates")
                            )

                            // 4. Vagabond Music Player
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Music Player",
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = if (musicPlaybackState.currentTrack != null) musicPlaybackState.currentTrack!!.title else "Lock screen & offline audio",
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = if (musicPlaybackState.isPlaying) Color(0xFFEA580C) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (musicPlaybackState.isPlaying) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFEA580C))
                                            )
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Headphones,
                                        contentDescription = null,
                                        tint = if (musicPlaybackState.isPlaying) Color(0xFFEA580C) else Color(0xFF8B5CF6),
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showTopOverflowMenu = false
                                    showMusicPlayerSheet = true
                                },
                                modifier = Modifier.testTag("menu_item_music_player")
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )

                            // 5. Saved Passwords Manager
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Saved Passwords",
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = "${savedCredentials.size} saved login(s)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = null,
                                        tint = Color(0xFFEA580C),
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showTopOverflowMenu = false
                                    showPasswordSheet = true
                                },
                                modifier = Modifier.testTag("menu_item_passwords")
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = GeoSurface,
                    scrolledContainerColor = GeoSurface
                )
            )
        },
        bottomBar = {
            BottomTabBar(
                tabs = tabs,
                activeTabId = activeTabId,
                canGoBack = activeTab.canGoBack,
                canGoForward = activeTab.canGoForward,
                downloadCount = downloadHistory.size,
                onTabSelected = { selectedId ->
                    activeTabId = selectedId
                },
                onTabClosed = { closedId ->
                    closeTab(closedId)
                },
                onNewTab = {
                    addNewTab(VR_PORTAL_URL, "Vagabond Riders")
                },
                onGoBack = {
                    activeTab.webView?.goBack()
                },
                onGoForward = {
                    activeTab.webView?.goForward()
                },
                onGoHome = {
                    activeTab.webView?.loadUrl(VR_PORTAL_URL)
                },
                onRefresh = {
                    activeTab.webView?.reload()
                },
                onShare = shareCurrentUrl,
                onPrintPdf = {
                    val wv = activeTab.webView
                    if (wv != null) {
                        FileDownloadHelper.printWebPageAsPdf(context, wv, "Vagabond_Riders_${activeTab.title.replace(" ", "_")}")
                    } else {
                        Toast.makeText(context, "Page not ready for PDF printing", Toast.LENGTH_SHORT).show()
                    }
                },
                onOpenDownloads = {
                    showDownloadsSheet = true
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(GeoBackground)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Live Background GPS Navigation Status Banner
                AnimatedVisibility(visible = isLocationServiceRunning && currentLocationData != null) {
                    Surface(
                        color = Color(0xFFF0FDF4),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLocationSheet = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF16A34A))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "GPS Active (Background & Lock Screen)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF15803D)
                                )
                            }
                            currentLocationData?.let { loc ->
                                Text(
                                    text = "${loc.formattedCoordinates} • ${loc.formattedSpeed}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF166534)
                                )
                            }
                        }
                    }
                }

                // Main Web View Container for Active Tab
                Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                tabs.forEach { tab ->
                    val isCurrent = tab.id == activeTabId
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (isCurrent) Modifier else Modifier.size(0.dp))
                    ) {
                        VRWebView(
                            url = tab.url,
                            onProgressChange = { p ->
                                val idx = tabs.indexOfFirst { it.id == tab.id }
                                if (idx != -1) {
                                    tabs[idx] = tabs[idx].copy(
                                        progress = p,
                                        isLoading = p < 100,
                                        canGoBack = tabs[idx].webView?.canGoBack() ?: false,
                                        canGoForward = tabs[idx].webView?.canGoForward() ?: false
                                    )
                                }
                            },
                            onPageStarted = { loadedUrl ->
                                val idx = tabs.indexOfFirst { it.id == tab.id }
                                if (idx != -1) {
                                    tabs[idx] = tabs[idx].copy(
                                        url = loadedUrl,
                                        isLoading = true,
                                        canGoBack = tabs[idx].webView?.canGoBack() ?: false,
                                        canGoForward = tabs[idx].webView?.canGoForward() ?: false
                                    )
                                }
                                errorMessage = null
                            },
                            onPageFinished = { finishedUrl ->
                                val idx = tabs.indexOfFirst { it.id == tab.id }
                                if (idx != -1) {
                                    tabs[idx] = tabs[idx].copy(
                                        url = finishedUrl,
                                        isLoading = false,
                                        canGoBack = tabs[idx].webView?.canGoBack() ?: false,
                                        canGoForward = tabs[idx].webView?.canGoForward() ?: false
                                    )
                                }
                                errorMessage = null
                            },
                            onTitleChange = { newTitle ->
                                val idx = tabs.indexOfFirst { it.id == tab.id }
                                if (idx != -1) {
                                    tabs[idx] = tabs[idx].copy(title = newTitle)
                                }
                            },
                            onError = { err ->
                                if (isCurrent) {
                                    errorMessage = err
                                }
                            },
                            onWebViewCreated = { wv ->
                                val idx = tabs.indexOfFirst { it.id == tab.id }
                                if (idx != -1) {
                                    tabs[idx] = tabs[idx].copy(webView = wv)
                                }
                            },
                            onDownloadStarted = { fileName ->
                                Toast.makeText(context, "Downloading $fileName...", Toast.LENGTH_SHORT).show()
                            },
                            onDownloadFinished = { downloadedFile ->
                                Toast.makeText(context, "Saved to Downloads: ${downloadedFile.fileName}", Toast.LENGTH_SHORT).show()
                            },
                            onNewTabRequested = { newTabUrl ->
                                addNewTab(newTabUrl ?: VR_PORTAL_URL, "New Tab")
                            },
                            onAppUpdateRequested = {
                                showUpdateSheet = true
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Web Page Loading Progress Indicator
                if (activeTab.isLoading) {
                    LinearProgressIndicator(
                        progress = { activeTab.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.TopCenter)
                            .testTag("loading_progress_bar"),
                        color = Color(0xFFEA580C),
                        trackColor = Color(0x33EA580C)
                    )
                }

                // Quick Login Chip Bar on Login Page
                if (isLoginPage && savedCredentials.isNotEmpty() && !showSplash) {
                    QuickLoginChipBar(
                        credentials = savedCredentials,
                        onSelectAccount = { cred ->
                            passwordViewModel.autofillIntoWebView(cred, activeTab.webView, triggerSubmit = false) {
                                Toast.makeText(context, "Filled login for ${cred.accountLabel}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onOpenManager = {
                            showPasswordSheet = true
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                    )
                }

                // Download Progress / Notification Banner
                DownloadNotificationBanner(
                    activeDownload = activeDownload,
                    completedDownload = lastCompletedDownload,
                    onDismissCompleted = {
                        FileDownloadHelper.dismissLastCompleted()
                    },
                    onOpenDownloadsSheet = {
                        showDownloadsSheet = true
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                )

                // Error Overlay with Offline Detection
                if (errorMessage != null || !isOnline) {
                    ErrorOverlay(
                        errorMessage = if (!isOnline) "No Internet Connection" else (errorMessage ?: "Unable to connect"),
                        onRefresh = {
                            errorMessage = null
                            activeTab.webView?.reload()
                        },
                        onGoHome = {
                            errorMessage = null
                            activeTab.webView?.loadUrl(VR_PORTAL_URL)
                        },
                        isOffline = !isOnline,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Floating Mini Music Player (appears above bottom bar when audio is playing)
                VRMiniMusicPlayer(
                    onExpandPlayer = { showMusicPlayerSheet = true },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (isLoginPage && savedCredentials.isNotEmpty()) 56.dp else 8.dp)
                )
                }
            }

            // Animated Splash Screen overlay
            AnimatedVisibility(
                visible = showSplash,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(500)),
                modifier = Modifier.fillMaxSize()
            ) {
                SplashScreen(
                    appTitle = "Vagabond Riders",
                    subTitle = "Official Portal",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // Live GPS & Background Navigation Sheet
    if (showLocationSheet) {
        LocationNavigationSheet(
            sheetState = locationSheetState,
            onRequestBackgroundPermission = requestBackgroundAccess,
            onOpenLocationPage = {
                showLocationSheet = false
                activeTab.webView?.loadUrl(BackgroundLocationManager.LOCATION_TRIGGER_URL)
            },
            onDismiss = { showLocationSheet = false }
        )
    }

    // Password Manager Bottom Sheet
    if (showPasswordSheet) {
        PasswordManagerBottomSheet(
            credentials = savedCredentials,
            sheetState = passwordSheetState,
            onDismiss = { showPasswordSheet = false },
            onAutofillAccount = { cred ->
                passwordViewModel.autofillIntoWebView(cred, activeTab.webView, triggerSubmit = cred.autoSubmit)
                showPasswordSheet = false
                Toast.makeText(context, "Filled credentials for ${cred.accountLabel}", Toast.LENGTH_SHORT).show()
            },
            onSaveCredential = { label, username, pass, role, autoSubmit, notes, id ->
                passwordViewModel.saveCredential(label, username, pass, role, autoSubmit, notes, id)
            },
            onDeleteCredential = { cred ->
                passwordViewModel.deleteCredential(cred)
            },
            autoFillOnLogout = autoFillOnPageLoad,
            onToggleAutoFillOnLogout = { enabled ->
                passwordViewModel.setAutoFillOnPageLoad(enabled)
            }
        )
    }

    // Downloads Bottom Sheet
    if (showDownloadsSheet) {
        DownloadsBottomSheet(
            downloadList = downloadHistory,
            sheetState = downloadsSheetState,
            onDismiss = { showDownloadsSheet = false },
            onOpenFile = { file ->
                FileDownloadHelper.openFile(context, file)
            },
            onShareFile = { file ->
                FileDownloadHelper.shareFile(context, file)
            },
            onDeleteFile = { file ->
                FileDownloadHelper.deleteFile(context, file)
            },
            onClearAll = {
                FileDownloadHelper.clearAllHistory(context)
            }
        )
    }

    // Push Notifications Bottom Sheet
    if (showPushSheet) {
        PushNotificationBottomSheet(
            sheetState = pushSheetState,
            onDismiss = { showPushSheet = false },
            onOpenUrl = { targetUrl ->
                if (targetUrl.isNotBlank()) {
                    activeTab.webView?.loadUrl(targetUrl)
                    val idx = tabs.indexOfFirst { it.id == activeTabId }
                    if (idx != -1) {
                        tabs[idx] = tabs[idx].copy(url = targetUrl)
                    }
                }
            }
        )
    }

    // In-App App Updater Bottom Sheet
    if (showUpdateSheet) {
        AppUpdateBottomSheet(
            sheetState = updateSheetState,
            onDismiss = { showUpdateSheet = false }
        )
    }

    // Vagabond Music Player & Offline Road Trips Bottom Sheet
    if (showMusicPlayerSheet) {
        VRMusicPlayerBottomSheet(
            sheetState = musicPlayerSheetState,
            onDismissRequest = { showMusicPlayerSheet = false }
        )
    }
}
