package com.example.media

import android.content.Context
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.OfflineTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

/**
 * Singleton Music Engine for Vagabond Riders.
 * Provides unified interface between Jetpack Compose UI, WebView JavaScript hooks,
 * Foreground Media Playback Service, and Offline Database storage.
 */
object VRMusicManager {

    private const val TAG = "VRMusicManager"

    private val _playbackState = MutableStateFlow(VRPlaybackState())
    val playbackState: StateFlow<VRPlaybackState> = _playbackState.asStateFlow()

    val openPlayerSheetRequested = MutableStateFlow(false)

    private var activeServiceRef: WeakReference<VRMediaPlaybackService>? = null
    private val managerScope = CoroutineScope(Dispatchers.Main + Job())

    // Web callback listener (allows notifying web page when lock screen buttons are clicked)
    var onWebMediaActionListener: ((action: String, track: VRTrack?) -> Unit)? = null

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun bindService(service: VRMediaPlaybackService) {
        activeServiceRef = WeakReference(service)
        val current = _playbackState.value
        val track = current.currentTrack
        if (track != null) {
            service.syncExternalTrack(track, current.isPlaying, current.positionMs, current.durationMs)
        }
    }

    fun unbindService() {
        activeServiceRef = null
    }

    fun updatePlaybackState(reducer: (VRPlaybackState) -> VRPlaybackState) {
        _playbackState.update(reducer)
    }

    /**
     * Synchronizes metadata and playback state originating from the in-app WebView
     * (e.g. PHP Music player with JioSaavn tracks), showing the real song title,
     * artist, and artwork on the system lock screen.
     */
    fun syncWebTrack(
        context: Context,
        track: VRTrack,
        isPlaying: Boolean = true,
        positionMs: Long = 0L,
        durationMs: Long = 0L
    ) {
        val currentState = _playbackState.value
        val isNativePlaying = activeServiceRef?.get()?.isNativePlaying() == true ||
                (currentState.currentTrack?.isOfflineAvailable == true && currentState.isPlaying)

        if (!isPlaying && isNativePlaying) {
            // Do not let paused web page state overwrite active native offline track
            return
        }

        if (isPlaying) {
            // Online song playing has 1st priority: immediately stop any offline native player
            activeServiceRef?.get()?.stopNativeMediaPlayerOnly()
        }
        _playbackState.update {
            it.copy(
                currentTrack = track,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = if (durationMs > 0) durationMs else it.durationMs,
                errorMessage = null
            )
        }
        VRMediaPlaybackService.startService(context)
        activeServiceRef?.get()?.syncExternalTrack(track, isPlaying, positionMs, durationMs)
    }

    fun updateServicePlaybackStatus(isPlaying: Boolean, positionMs: Long) {
        activeServiceRef?.get()?.updateExternalPlaybackStatus(isPlaying, positionMs)
    }

    /**
     * Plays a single track, optionally replacing or updating the active playlist.
     */
    fun playTrack(context: Context, track: VRTrack, newPlaylist: List<VRTrack>? = null) {
        managerScope.launch {
            // Check if track is saved locally in offline DB
            val offlineTrack = withContext(Dispatchers.IO) {
                try {
                    val db = AppDatabase.getDatabase(context)
                    db.offlineTrackDao().getTrackByUrl(track.streamUrl)
                        ?: db.offlineTrackDao().getTrackById(track.id)
                } catch (_: Exception) {
                    null
                }
            }

            val finalTrack = if (offlineTrack != null && File(offlineTrack.localFilePath).exists()) {
                track.copy(
                    isOfflineAvailable = true,
                    localFilePath = offlineTrack.localFilePath,
                    title = if (track.title.isNotBlank() && track.title != "Vagabond Music") track.title else offlineTrack.title,
                    artist = if (track.artist.isNotBlank() && track.artist != "Vagabond Riders") track.artist else offlineTrack.artist,
                    album = if (track.album.isNotBlank()) track.album else offlineTrack.album,
                    artworkUrl = if (!track.artworkUrl.isNullOrBlank()) track.artworkUrl else offlineTrack.artworkUrl,
                    durationMs = if (track.durationMs > 0) track.durationMs else offlineTrack.durationMs
                )
            } else {
                track
            }

            val playlist = newPlaylist ?: if (_playbackState.value.playlist.any { it.id == finalTrack.id }) {
                _playbackState.value.playlist
            } else {
                _playbackState.value.playlist + finalTrack
            }

            val index = playlist.indexOfFirst { it.id == finalTrack.id }.coerceAtLeast(0)

            _playbackState.update {
                it.copy(
                    currentTrack = finalTrack,
                    playlist = playlist,
                    currentIndex = index,
                    isPlaying = true,
                    isBuffering = true,
                    positionMs = 0L,
                    durationMs = finalTrack.durationMs,
                    errorMessage = null
                )
            }

            VRMediaPlaybackService.startService(context)
            activeServiceRef?.get()?.prepareAndPlay(finalTrack)
        }
    }

    /**
     * Replaces the playlist and starts playback from a specific index.
     */
    fun setPlaylistAndPlay(context: Context, tracks: List<VRTrack>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, tracks.size - 1)
        val track = tracks[safeIndex]
        playTrack(context, track, tracks)
    }

    fun play() {
        activeServiceRef?.get()?.play() ?: notifyWebAction("play")
    }

    fun pause() {
        activeServiceRef?.get()?.pause() ?: notifyWebAction("pause")
    }

    fun togglePlayPause() {
        activeServiceRef?.get()?.togglePlayPause() ?: notifyWebAction("toggle")
    }

    fun seekTo(positionMs: Long) {
        _playbackState.update { it.copy(positionMs = positionMs) }
        val service = activeServiceRef?.get()
        if (service != null) {
            service.seekTo(positionMs)
        } else {
            notifyWebAction("seekTo:$positionMs")
        }
    }

    fun seekRelative(deltaMs: Long) {
        val service = activeServiceRef?.get()
        if (service != null) {
            service.seekRelative(deltaMs)
        } else {
            val currentPos = _playbackState.value.positionMs
            val duration = _playbackState.value.durationMs
            val maxBound = if (duration > 0L) duration else Long.MAX_VALUE
            val target = (currentPos + deltaMs).coerceIn(0L, maxBound)
            _playbackState.update { it.copy(positionMs = target) }
            if (deltaMs > 0) {
                notifyWebAction("seekForward")
            } else {
                notifyWebAction("seekBackward")
            }
            notifyWebAction("seekTo:$target")
        }
    }

    fun stop() {
        activeServiceRef?.get()?.stopPlayback() ?: notifyWebAction("stop")
    }

    fun nextTrack() {
        val state = _playbackState.value
        val playlist = state.playlist
        if (playlist.isEmpty()) {
            notifyWebAction("next")
            return
        }

        val nextIndex = when {
            state.isShuffle && playlist.size > 1 -> {
                var rand = (playlist.indices).random()
                while (rand == state.currentIndex) {
                    rand = (playlist.indices).random()
                }
                rand
            }
            state.currentIndex < playlist.size - 1 -> state.currentIndex + 1
            state.repeatMode == VRRepeatMode.ALL -> 0
            else -> -1
        }

        if (nextIndex in playlist.indices) {
            val nextTrack = playlist[nextIndex]
            _playbackState.update {
                it.copy(currentIndex = nextIndex, currentTrack = nextTrack)
            }
            activeServiceRef?.get()?.prepareAndPlay(nextTrack)
            notifyWebAction("next", nextTrack)
        } else {
            notifyWebAction("next")
        }
    }

    fun previousTrack() {
        val state = _playbackState.value
        val playlist = state.playlist
        if (playlist.isEmpty()) {
            notifyWebAction("previous")
            return
        }

        if (state.positionMs > 3000L) {
            seekTo(0L)
            return
        }

        val prevIndex = when {
            state.currentIndex > 0 -> state.currentIndex - 1
            state.repeatMode == VRRepeatMode.ALL -> playlist.size - 1
            else -> 0
        }

        if (prevIndex in playlist.indices) {
            val prevTrack = playlist[prevIndex]
            _playbackState.update {
                it.copy(currentIndex = prevIndex, currentTrack = prevTrack)
            }
            activeServiceRef?.get()?.prepareAndPlay(prevTrack)
            notifyWebAction("previous", prevTrack)
        } else {
            notifyWebAction("previous")
        }
    }

    fun toggleShuffle() {
        _playbackState.update { it.copy(isShuffle = !it.isShuffle) }
    }

    fun cycleRepeatMode() {
        _playbackState.update {
            val next = when (it.repeatMode) {
                VRRepeatMode.OFF -> VRRepeatMode.ALL
                VRRepeatMode.ALL -> VRRepeatMode.ONE
                VRRepeatMode.ONE -> VRRepeatMode.OFF
            }
            it.copy(repeatMode = next)
        }
    }

    fun notifyWebAction(action: String, track: VRTrack? = _playbackState.value.currentTrack) {
        try {
            onWebMediaActionListener?.invoke(action, track)
        } catch (_: Exception) {}
    }

    /**
     * Downloads a track to internal app storage for offline road trip playback without cellular data.
     */
    fun downloadTrackForOffline(
        context: Context,
        track: VRTrack,
        onProgress: ((Float) -> Unit)? = null,
        onResult: (Boolean, String?) -> Unit
    ) {
        managerScope.launch(Dispatchers.IO) {
            try {
                var streamUrl = track.streamUrl.trim()

                // Check if URL is present and valid
                if (streamUrl.isBlank() || (!streamUrl.startsWith("http://", ignoreCase = true) && !streamUrl.startsWith("https://", ignoreCase = true))) {
                    withContext(Dispatchers.Main) {
                        onResult(
                            false,
                            "Audio stream URL is not available. Please play the track first in the player so the stream can be downloaded."
                        )
                    }
                    return@launch
                }

                val musicDir = File(context.filesDir, "offline_music").apply { mkdirs() }
                val cleanTitle = VRMusicJavascriptBridge.unescapeHtml(track.title)
                val safeTitle = cleanTitle.replace(Regex("[^a-zA-Z0-9.-]"), "_").take(35)
                val safeName = "vr_${safeTitle}_${System.currentTimeMillis()}.mp3"
                val destFile = File(musicDir, safeName)

                val reqBuilder = Request.Builder()
                    .url(streamUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
                    .addHeader("Accept", "*/*")

                if (streamUrl.contains("vagabondriders", ignoreCase = true)) {
                    reqBuilder.addHeader("Referer", "https://vagabondriders.in/")
                } else if (streamUrl.contains("jiosaavn") || streamUrl.contains("saavn")) {
                    reqBuilder.addHeader("Referer", "https://www.jiosaavn.com/")
                }

                val response = okHttpClient.newCall(reqBuilder.build()).execute()

                if (!response.isSuccessful || response.body == null) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "Server returned error ${response.code}: ${response.message}")
                    }
                    return@launch
                }

                val body = response.body!!
                val contentLength = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (contentLength > 0 && onProgress != null) {
                                val prog = (downloadedBytes.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                                withContext(Dispatchers.Main) {
                                    onProgress(prog)
                                }
                            }
                        }
                        output.flush()
                    }
                }

                // Save to Room DB
                val cleanArtist = VRMusicJavascriptBridge.unescapeHtml(track.artist)
                val cleanAlbum = VRMusicJavascriptBridge.unescapeHtml(track.album)
                val cleanTrack = track.copy(
                    title = cleanTitle,
                    artist = cleanArtist,
                    album = cleanAlbum,
                    streamUrl = streamUrl
                )

                val db = AppDatabase.getDatabase(context)
                val offlineEntity = OfflineTrack.fromVRTrack(
                    track = cleanTrack,
                    localPath = destFile.absolutePath,
                    size = destFile.length()
                )
                db.offlineTrackDao().insertTrack(offlineEntity)

                // Update in-memory state if current
                if (_playbackState.value.currentTrack?.id == track.id) {
                    _playbackState.update {
                        it.copy(
                            currentTrack = it.currentTrack?.copy(
                                title = cleanTitle,
                                artist = cleanArtist,
                                isOfflineAvailable = true,
                                localFilePath = destFile.absolutePath,
                                streamUrl = streamUrl
                            )
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    onResult(true, null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Offline download failed for ${track.title}", e)
                withContext(Dispatchers.Main) {
                    onResult(false, e.message ?: "Download failed")
                }
            }
        }
    }

    /**
     * Deletes an offline saved track and frees up storage.
     */
    fun deleteOfflineTrack(context: Context, trackId: String, onFinished: (() -> Unit)? = null) {
        managerScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val track = db.offlineTrackDao().getTrackById(trackId)
                if (track != null) {
                    try {
                        val file = File(track.localFilePath)
                        if (file.exists()) file.delete()
                    } catch (_: Exception) {}
                    db.offlineTrackDao().deleteTrackById(trackId)
                }
                withContext(Dispatchers.Main) {
                    onFinished?.invoke()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete offline track", e)
            }
        }
    }
}
