package com.example.media

import android.net.Uri

/**
 * Represents a musical track in Vagabond Riders Music Player.
 * Supports online streaming URLs, web-intercepted audio, and local cached offline files.
 */
data class VRTrack(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val artist: String = "Vagabond Riders",
    val album: String = "VR Road Trip Beats",
    val streamUrl: String,
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
    val isOfflineAvailable: Boolean = false,
    val localFilePath: String? = null,
    val sourceWebUrl: String? = null,
    val dateAdded: Long = System.currentTimeMillis()
) {
    /**
     * Resolves the actual playback URI, preferring local offline file if available.
     */
    fun getPlaybackUri(): String {
        return if (isOfflineAvailable && !localFilePath.isNullOrBlank()) {
            localFilePath
        } else {
            streamUrl
        }
    }
}

enum class VRRepeatMode {
    OFF,
    ALL,
    ONE
}

/**
 * Represents the real-time playback state of the music engine.
 */
data class VRPlaybackState(
    val currentTrack: VRTrack? = null,
    val playlist: List<VRTrack> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val repeatMode: VRRepeatMode = VRRepeatMode.OFF,
    val isShuffle: Boolean = false,
    val errorMessage: String? = null
) {
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val hasNext: Boolean
        get() = playlist.isNotEmpty() && (currentIndex < playlist.size - 1 || repeatMode == VRRepeatMode.ALL)

    val hasPrevious: Boolean
        get() = playlist.isNotEmpty() && (currentIndex > 0 || positionMs > 3000L || repeatMode == VRRepeatMode.ALL)
}
