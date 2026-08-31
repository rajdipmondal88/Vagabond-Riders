package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.AppDatabase
import com.example.data.OfflineTrack
import com.example.media.VRMusicManager
import com.example.media.VRRepeatMode
import com.example.media.VRTrack
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VRMusicPlayerBottomSheet(
    sheetState: SheetState,
    webView: android.webkit.WebView? = null,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by VRMusicManager.playbackState.collectAsState()
    val currentTrack = state.currentTrack

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Now Playing", "Queue", "Offline Tracks")

    // Database offline tracks list
    val db = remember { AppDatabase.getDatabase(context) }
    val offlineTracksFlow = remember { db.offlineTrackDao().getAllOfflineTracksFlow() }
    val offlineTracks by offlineTracksFlow.collectAsState(initial = emptyList())

    var isDownloadingCurrent by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }

    // User dragging slider state
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragPosition by remember { mutableFloatStateOf(0f) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color(0xFF0F172A),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 48.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF475569))
            )
        },
        modifier = Modifier.testTag("vr_music_player_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            // Top Bar with Minimize Downwards Action & Status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismissRequest()
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B))
                        .testTag("btn_minimize_music_player")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Minimize to Mini Player",
                        tint = Color.White
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Vagabond Music Player",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.3.sp
                        ),
                        color = Color.White
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (currentTrack?.isOfflineAvailable == true) Color(0xFF4ADE80) else Color(0xFF38BDF8))
                        )
                        Text(
                            text = if (currentTrack?.isOfflineAvailable == true) "Offline Library Audio" else "Online Web Audio Stream",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (currentTrack?.isOfflineAvailable == true) Color(0xFF4ADE80) else Color(0xFF38BDF8)
                        )
                    }
                }

                // Decorative spacer to keep center alignment
                Spacer(modifier = Modifier.size(36.dp))
            }

            // Header Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = Color(0xFFEA580C),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Color(0xFFEA580C),
                        height = 3.dp
                    )
                },
                divider = {
                    HorizontalDivider(color = Color(0xFF334155))
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == index) Color(0xFFEA580C) else Color(0xFF94A3B8)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedTab) {
                0 -> {
                    // TAB 0: Now Playing Main Controller
                    NowPlayingView(
                        track = currentTrack,
                        state = state,
                        isDraggingSlider = isDraggingSlider,
                        sliderDragPosition = sliderDragPosition,
                        onSliderValueChange = {
                            isDraggingSlider = true
                            sliderDragPosition = it
                        },
                        onSliderValueChangeFinished = {
                            isDraggingSlider = false
                            val targetMs = (sliderDragPosition * state.durationMs).toLong()
                            VRMusicManager.seekTo(targetMs)
                        },
                        isDownloading = isDownloadingCurrent,
                        downloadProgress = downloadProgress,
                        onDownloadClicked = {
                            if (currentTrack != null && !currentTrack.isOfflineAvailable && !isDownloadingCurrent) {
                                isDownloadingCurrent = true
                                downloadProgress = 0f

                                val startDownload = { trackToDownload: VRTrack ->
                                    VRMusicManager.downloadTrackForOffline(
                                        context = context,
                                        track = trackToDownload,
                                        onProgress = { prog -> downloadProgress = prog },
                                        onResult = { success, error ->
                                            isDownloadingCurrent = false
                                            if (success) {
                                                Toast.makeText(context, "Downloaded for offline ride!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Download failed: $error", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    )
                                }

                                if ((currentTrack.streamUrl.isBlank() || !currentTrack.streamUrl.startsWith("http", ignoreCase = true)) && webView != null) {
                                    webView.evaluateJavascript("""
                                        (function() {
                                            var a = window._vrCurrentAudio || document.querySelector('audio');
                                            var s = a ? (a.currentSrc || a.src) : '';
                                            if (!s) {
                                                var cs = window.currentSong || window.nowPlaying || window.currentTrack;
                                                if (cs) s = cs.url || cs.src || cs.streamUrl || cs.download_url || cs.media_url || '';
                                            }
                                            if (s && !s.startsWith('http://') && !s.startsWith('https://')) {
                                                try { s = new URL(s, window.location.href).href; } catch(e) {}
                                            }
                                            return s || '';
                                        })()
                                    """.trimIndent()) { result ->
                                        val cleanedUrl = result?.removeSurrounding("\"")?.replace("\\/", "/")?.trim() ?: ""
                                        val resolvedTrack = if (cleanedUrl.startsWith("http://", ignoreCase = true) || cleanedUrl.startsWith("https://", ignoreCase = true)) {
                                            currentTrack.copy(streamUrl = cleanedUrl)
                                        } else {
                                            currentTrack
                                        }
                                        startDownload(resolvedTrack)
                                    }
                                } else {
                                    startDownload(currentTrack)
                                }
                            }
                        }
                    )
                }
                1 -> {
                    // TAB 1: Active Queue / Playlist
                    PlaylistQueueView(
                        playlist = state.playlist,
                        currentIndex = state.currentIndex,
                        isPlaying = state.isPlaying,
                        onTrackSelected = { track ->
                            VRMusicManager.playTrack(context, track)
                        }
                    )
                }
                2 -> {
                    // TAB 2: Offline Tracks for Road Trips
                    OfflineTracksView(
                        offlineTracks = offlineTracks,
                        currentTrack = currentTrack,
                        isPlaying = state.isPlaying,
                        onPlayOffline = { offlineTrack ->
                            val allOfflineVRTracks = offlineTracks.map { it.toVRTrack() }
                            val clickedIndex = offlineTracks.indexOfFirst { it.id == offlineTrack.id }.coerceAtLeast(0)
                            VRMusicManager.setPlaylistAndPlay(context, allOfflineVRTracks, clickedIndex)
                        },
                        onDeleteOffline = { id ->
                            VRMusicManager.deleteOfflineTrack(context, id) {
                                Toast.makeText(context, "Track removed from offline cache", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NowPlayingView(
    track: VRTrack?,
    state: com.example.media.VRPlaybackState,
    isDraggingSlider: Boolean,
    sliderDragPosition: Float,
    onSliderValueChange: (Float) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    isDownloading: Boolean,
    downloadProgress: Float,
    onDownloadClicked: () -> Unit
) {
    if (track == null) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Headphones,
                contentDescription = null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No Music Playing",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Select a song from the portal or offline tracks to play with lock-screen controls.",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
            )
        }
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "player_disc_spin")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "player_disc_angle"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Large Center Vinyl / Artwork
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(190.dp)
                .shadow(24.dp, CircleShape, spotColor = Color(0xFFEA580C))
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF1E293B), Color(0xFF090D16))
                    )
                )
                .border(3.dp, Color(0xFFEA580C).copy(alpha = 0.8f), CircleShape)
                .then(if (state.isPlaying) Modifier.rotate(rotationAngle) else Modifier)
        ) {
            if (!track.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = track.artworkUrl,
                    contentDescription = "Track Artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(190.dp)
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFFEA580C),
                    modifier = Modifier.size(64.dp)
                )
            }

            // Center Vinyl Spindle Hole
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F172A))
                    .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Song Title & Artist
        Text(
            text = track.title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .basicMarquee()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${track.artist} • ${track.album}",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Time Slider / Scrubber
        val currentProgress = if (isDraggingSlider) sliderDragPosition else state.progress
        val currentDisplayPosMs = if (isDraggingSlider) (sliderDragPosition * state.durationMs).toLong() else state.positionMs

        Slider(
            value = currentProgress,
            onValueChange = onSliderValueChange,
            onValueChangeFinished = onSliderValueChangeFinished,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFEA580C),
                activeTrackColor = Color(0xFFEA580C),
                inactiveTrackColor = Color(0xFF334155)
            ),
            modifier = Modifier.fillMaxWidth().testTag("slider_music_progress")
        )

        // Time labels (Elapsed / Total)
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = formatDuration(currentDisplayPosMs),
                color = Color(0xFF94A3B8),
                fontSize = 11.sp
            )
            Text(
                text = formatDuration(state.durationMs),
                color = Color(0xFF94A3B8),
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Media Control Buttons (Lock Screen mirror)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Shuffle
            IconButton(
                onClick = { VRMusicManager.toggleShuffle() },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (state.isShuffle) Color(0xFFEA580C) else Color(0xFF64748B),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Skip Previous
            IconButton(
                onClick = { VRMusicManager.previousTrack() },
                enabled = state.hasPrevious,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous Track",
                    tint = if (state.hasPrevious) Color.White else Color(0xFF475569),
                    modifier = Modifier.size(28.dp)
                )
            }

            // Rewind 10s
            IconButton(
                onClick = { VRMusicManager.seekRelative(-10000L) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Replay10,
                    contentDescription = "Rewind 10s",
                    tint = Color(0xFFCBD5E1),
                    modifier = Modifier.size(24.dp)
                )
            }

            // Large Play / Pause FAB
            FloatingActionButton(
                onClick = { VRMusicManager.togglePlayPause() },
                containerColor = Color(0xFFEA580C),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .size(64.dp)
                    .testTag("btn_sheet_play_pause")
            ) {
                if (state.isBuffering) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Forward 10s
            IconButton(
                onClick = { VRMusicManager.seekRelative(10000L) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Forward10,
                    contentDescription = "Forward 10s",
                    tint = Color(0xFFCBD5E1),
                    modifier = Modifier.size(24.dp)
                )
            }

            // Skip Next
            IconButton(
                onClick = { VRMusicManager.nextTrack() },
                enabled = state.hasNext,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next Track",
                    tint = if (state.hasNext) Color.White else Color(0xFF475569),
                    modifier = Modifier.size(28.dp)
                )
            }

            // Repeat Mode Cycle
            IconButton(
                onClick = { VRMusicManager.cycleRepeatMode() },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = when (state.repeatMode) {
                        VRRepeatMode.ONE -> Icons.Default.RepeatOne
                        else -> Icons.Default.Repeat
                    },
                    contentDescription = "Repeat Mode",
                    tint = if (state.repeatMode != VRRepeatMode.OFF) Color(0xFFEA580C) else Color(0xFF64748B),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Offline Download Action Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1E293B))
                .clickable(enabled = !track.isOfflineAvailable && !isDownloading) {
                    onDownloadClicked()
                }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (track.isOfflineAvailable) Icons.Default.DownloadDone else if (isDownloading) Icons.Default.Downloading else Icons.Default.Download,
                        contentDescription = null,
                        tint = if (track.isOfflineAvailable) Color(0xFF4ADE80) else Color(0xFFEA580C),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (track.isOfflineAvailable) "Available Offline on Road Trips" else if (isDownloading) "Downloading... ${(downloadProgress * 100).toInt()}%" else "Save for Offline Road Trips",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (track.isOfflineAvailable) "Saved in app storage for no-network riding" else "Listen without internet in mountains & remote ghats",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp
                        )
                    }
                }

                if (isDownloading) {
                    CircularProgressIndicator(
                        progress = { downloadProgress },
                        color = Color(0xFFEA580C),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else if (track.isOfflineAvailable) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Downloaded",
                        tint = Color(0xFF4ADE80),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistQueueView(
    playlist: List<VRTrack>,
    currentIndex: Int,
    isPlaying: Boolean,
    onTrackSelected: (VRTrack) -> Unit
) {
    if (playlist.isEmpty()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        ) {
            Icon(
                imageVector = Icons.Default.QueueMusic,
                contentDescription = null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Queue is Empty",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Open any music list in the portal to populate the queue.",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(380.dp)
    ) {
        itemsIndexed(playlist) { index, item ->
            val isCurrent = index == currentIndex
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isCurrent) Color(0xFF1E293B) else Color.Transparent)
                    .clickable { onTrackSelected(item) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isCurrent) Color(0xFFEA580C) else Color(0xFF334155))
                    ) {
                        if (isCurrent && isPlaying) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = item.title,
                            color = if (isCurrent) Color(0xFFEA580C) else Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.artist,
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (item.isOfflineAvailable) {
                    Icon(
                        imageVector = Icons.Default.DownloadDone,
                        contentDescription = "Offline",
                        tint = Color(0xFF4ADE80),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflineTracksView(
    offlineTracks: List<OfflineTrack>,
    currentTrack: VRTrack?,
    isPlaying: Boolean,
    onPlayOffline: (OfflineTrack) -> Unit,
    onDeleteOffline: (String) -> Unit
) {
    if (offlineTracks.isEmpty()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No Offline Tracks Saved",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Download tracks while on Wi-Fi/data to enjoy uninterrupted music in remote highway routes without network.",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(380.dp)
    ) {
        itemsIndexed(offlineTracks) { index, item ->
            val isCurrent = currentTrack?.id == item.id
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isCurrent) Color(0xFF1E293B) else Color.Transparent)
                    .clickable { onPlayOffline(item) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF16A34A).copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = if (isCurrent && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFF4ADE80),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = item.title,
                            color = if (isCurrent) Color(0xFF4ADE80) else Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${item.artist} • ${formatFileSize(item.fileSize)}",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(
                    onClick = { onDeleteOffline(item.id) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete offline track",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes % 60, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format("%.1f MB", mb)
    } else {
        String.format("%.0f KB", kb)
    }
}
