package com.example.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.updater.AppUpdateManager
import com.example.updater.UpdateState

private val BrandOrange = Color(0xFFEA580C)
private val BrandOrangeDark = Color(0xFFC2410C)
private val BrandOrangeLight = Color(0xFFFFF7ED)
private val SuccessGreen = Color(0xFF16A34A)
private val SuccessGreenBg = Color(0xFFF0FDF4)
private val InfoBlue = Color(0xFF0284C7)
private val InfoBlueBg = Color(0xFFF0F9FF)
private val ErrorRed = Color(0xFFDC2626)
private val ErrorRedBg = Color(0xFFFEF2F2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdateBottomSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val updateState by AppUpdateManager.updateState.collectAsState()
    val (versionName, versionCode) = AppUpdateManager.getInstalledVersionInfo(context)

    // Check updates immediately when sheet is opened if in Idle state
    LaunchedEffect(Unit) {
        if (updateState is UpdateState.Idle) {
            AppUpdateManager.checkForUpdates(context)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            )
        },
        modifier = modifier.testTag("app_update_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header with App Logo & Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    VagabondLogoBadge(
                        size = 40.dp,
                        showRegistrationText = false
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "App Update",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.2.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Vagabond Riders Direct Installer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("btn_close_update_sheet")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Version Information Card
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "INSTALLED VERSION",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "v$versionName (Build $versionCode)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Source: app.vagabondriders.com",
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandOrange,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        color = BrandOrangeLight,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BrandOrange.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = BrandOrange,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "APK",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = BrandOrangeDark
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dynamic Status & Action Area
            AnimatedContent(
                targetState = updateState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                },
                label = "update_state_content"
            ) { state ->
                when (state) {
                    is UpdateState.Checking -> {
                        Surface(
                            color = InfoBlueBg,
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, InfoBlue.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(36.dp),
                                    color = BrandOrange,
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Checking for updates...",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = InfoBlue
                                )
                                Text(
                                    text = "Connecting to app.vagabondriders.com",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    is UpdateState.Downloading -> {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BrandOrange.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CloudDownload,
                                            contentDescription = null,
                                            tint = BrandOrange,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Downloading APK...",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Text(
                                        text = "${state.percent}%",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = BrandOrange
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                val animatedProgress by animateFloatAsState(
                                    targetValue = state.progress,
                                    animationSpec = tween(300),
                                    label = "progress_anim"
                                )

                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = BrandOrange,
                                    trackColor = BrandOrange.copy(alpha = 0.15f)
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${AppUpdateManager.formatBytes(state.bytesDownloaded)} / ${if (state.totalBytes > 0) AppUpdateManager.formatBytes(state.totalBytes) else "..."}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Speed,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = state.speedText,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedButton(
                                    onClick = { AppUpdateManager.cancelDownload() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("btn_cancel_update_download"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Cancel Download")
                                }
                            }
                        }
                    }

                    is UpdateState.Downloaded -> {
                        Surface(
                            color = SuccessGreenBg,
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(SuccessGreen.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "APK Download Complete!",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = SuccessGreen
                                )

                                Text(
                                    text = "Size: ${state.formattedSize} • Ready to install",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        AppUpdateManager.installDownloadedApk(context)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("btn_install_apk_now"),
                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.InstallMobile,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Install Update Now",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }

                    is UpdateState.PermissionRequired -> {
                        Surface(
                            color = BrandOrangeLight,
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BrandOrange.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = BrandOrangeDark,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Permission Required",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = BrandOrangeDark
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Android requires your permission to install APK updates directly from this app. Tap below to enable 'Allow from this source'.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        AppUpdateManager.requestInstallPermission(context)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("btn_grant_install_permission"),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandOrange),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "Open Settings & Allow",
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedButton(
                                    onClick = {
                                        AppUpdateManager.installDownloadedApk(context)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("btn_try_install_again"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("I Granted Permission, Install Now")
                                }
                            }
                        }
                    }

                    is UpdateState.Error -> {
                        Surface(
                            color = ErrorRedBg,
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = ErrorRed,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Update Check Notice",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = ErrorRed
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            AppUpdateManager.startDownload(context)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .testTag("btn_retry_update_download"),
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandOrange),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Download Now", fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            AppUpdateManager.openInBrowser(context)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .testTag("btn_browser_download_fallback"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Open Browser")
                                    }
                                }
                            }
                        }
                    }

                    is UpdateState.Available, is UpdateState.Idle, is UpdateState.UpToDate -> {
                        val availableState = state as? UpdateState.Available
                        val fileSizeText = availableState?.formattedSize ?: "Latest APK Release"

                        Surface(
                            color = BrandOrangeLight,
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BrandOrange.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(BrandOrange.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CloudDownload,
                                                contentDescription = null,
                                                tint = BrandOrange,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "Update Available",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = BrandOrangeDark
                                            )
                                            Text(
                                                text = "Package: Vagabond-Riders.apk ($fileSizeText)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        AppUpdateManager.startDownload(context)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("btn_download_and_install_update"),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandOrange),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.InstallMobile,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Download & Install Update",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            AppUpdateManager.checkForUpdates(context)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .testTag("btn_recheck_update"),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Re-check Server", fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            AppUpdateManager.openInBrowser(context)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .testTag("btn_open_browser_link"),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.OpenInBrowser,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Browser Link", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    is UpdateState.Installing -> {
                        Surface(
                            color = InfoBlueBg,
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, InfoBlue.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(36.dp),
                                    color = InfoBlue,
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Launching Android Package Installer...",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = InfoBlue
                                )
                                Text(
                                    text = "Follow the system prompt to complete update",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // What's New & Direct Installation Features Card
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Direct In-App Updates",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val features = listOf(
                        "Direct download & self-install from Vagabond Riders official server",
                        "Preserves all your login sessions, passwords, and tab history",
                        "Includes continuous background GPS and real-time push alerts",
                        "Latest safety, route management, and performance enhancements"
                    )

                    features.forEach { feature ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "• ",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = BrandOrange
                            )
                            Text(
                                text = feature,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Direct URL link footer
            Text(
                text = "APK URL: https://app.vagabondriders.com/Vagabond-Riders.apk",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { AppUpdateManager.openInBrowser(context) }
                    .padding(vertical = 4.dp)
            )
        }
    }
}
