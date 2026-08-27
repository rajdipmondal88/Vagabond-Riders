package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.WebTab

@Composable
fun BottomTabBar(
    tabs: List<WebTab>,
    activeTabId: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    downloadCount: Int,
    onTabSelected: (String) -> Unit,
    onTabClosed: (String) -> Unit,
    onNewTab: () -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onGoHome: () -> Unit,
    onRefresh: () -> Unit,
    onShare: () -> Unit,
    onPrintPdf: () -> Unit,
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("bottom_tab_bar_container"),
        color = Color(0xFF1E293B),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Top Row: Interactive Tabs Carousel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Scrollable tab chips
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(scrollState),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val isActive = tab.id == activeTabId
                        val chipBg = if (isActive) Color(0xFFEA580C) else Color(0xFF334155)
                        val textColor = if (isActive) Color.White else Color(0xFFCBD5E1)

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = chipBg,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onTabSelected(tab.id) }
                                .testTag("tab_chip_${tab.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Small logo or tab index
                                VagabondLogoBadge(
                                    size = 14.dp,
                                    showRegistrationText = false
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                val displayTitle = if (tab.title.isNotBlank()) {
                                    tab.title
                                } else {
                                    "Tab ${index + 1}"
                                }

                                Text(
                                    text = displayTitle,
                                    fontSize = 12.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    color = textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthInMax(110.dp)
                                )

                                if (tabs.size > 1) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .clickable { onTabClosed(tab.id) }
                                            .padding(2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close tab",
                                            tint = if (isActive) Color.White.copy(alpha = 0.9f) else Color(0xFF94A3B8),
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // New Tab Button (+)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF334155),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onNewTab() }
                        .testTag("btn_new_tab")
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Open new tab",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF334155), thickness = 0.5.dp)

            // Bottom Navigation & Actions Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Back
                IconButton(
                    onClick = onGoBack,
                    enabled = canGoBack,
                    modifier = Modifier.testTag("btn_bottom_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (canGoBack) Color.White else Color(0xFF64748B),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Forward
                IconButton(
                    onClick = onGoForward,
                    enabled = canGoForward,
                    modifier = Modifier.testTag("btn_bottom_forward")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Forward",
                        tint = if (canGoForward) Color.White else Color(0xFF64748B),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Home
                IconButton(
                    onClick = onGoHome,
                    modifier = Modifier.testTag("btn_bottom_home")
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Home",
                        tint = Color(0xFFFDBA74),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Reload
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("btn_bottom_refresh")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Print to PDF
                IconButton(
                    onClick = onPrintPdf,
                    modifier = Modifier.testTag("btn_bottom_print_pdf")
                ) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = "Save / Print as PDF",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Share URL
                IconButton(
                    onClick = onShare,
                    modifier = Modifier.testTag("btn_bottom_share")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share Link",
                        tint = Color(0xFF4ADE80),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Downloads
                IconButton(
                    onClick = onOpenDownloads,
                    modifier = Modifier.testTag("btn_bottom_downloads")
                ) {
                    BadgedBox(
                        badge = {
                            if (downloadCount > 0) {
                                Badge(
                                    containerColor = Color(0xFFEA580C),
                                    contentColor = Color.White
                                ) {
                                    Text(
                                        text = downloadCount.toString(),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Downloads",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.widthInMax(max: androidx.compose.ui.unit.Dp): Modifier {
    return this.width(max)
}
