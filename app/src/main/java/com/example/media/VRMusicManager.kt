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
    }

    fun unbindService() {
        activeServiceRef = null
    }

    fun updatePlaybackState(reducer: (VRPlaybackState) -> VRPlaybackState) {
        _playbackState.update(reducer)
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
                    localFilePath = offlineTrack.localFilePath
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
                    isPlaying = false,
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
        activeServiceRef?.get()?.seekTo(positionMs) ?: notifyWebAction("seekTo")
    }

    fun seekRelative(deltaMs: Long) {
        activeServiceRef?.get()?.seekRelative(deltaMs)
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

    private fun notifyWebAction(action: String, track: VRTrack? = _playbackState.value.currentTrack) {
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
                val musicDir = File(context.filesDir, "offline_music").apply { mkdirs() }
                val safeName = "track_${track.id.hashCode()}_${System.currentTimeMillis()}.mp3"
                val destFile = File(musicDir, safeName)

                val req = Request.Builder().url(track.streamUrl).build()
                val response = okHttpClient.newCall(req).execute()

                if (!response.isSuccessful || response.body == null) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "Server returned error ${response.code}")
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
                                val prog = downloadedBytes.toFloat() / contentLength.toFloat()
                                withContext(Dispatchers.Main) {
                                    onProgress(prog)
                                }
                            }
                        }
                        output.flush()
                    }
                }

                // Save to Room DB
                val db = AppDatabase.getDatabase(context)
                val offlineEntity = OfflineTrack.fromVRTrack(
                    track = track,
                    localPath = destFile.absolutePath,
                    size = destFile.length()
                )
                db.offlineTrackDao().insertTrack(offlineEntity)

                // Update in-memory state if current
                if (_playbackState.value.currentTrack?.id == track.id) {
                    _playbackState.update {
                        it.copy(
                            currentTrack = it.currentTrack?.copy(
                                isOfflineAvailable = true,
                                localFilePath = destFile.absolutePath
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
