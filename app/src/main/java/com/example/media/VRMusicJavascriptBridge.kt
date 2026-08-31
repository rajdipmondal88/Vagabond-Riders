package com.example.media

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject

/**
 * JavaScript Interface injected into the WebView as `window.VRMusicPlayer` and `window.AndroidMusic`.
 * Allows web pages (music lists, rider playlist players, etc.) to trigger native background
 * playback with full lock-screen media controls and offline downloading.
 */
class VRMusicJavascriptBridge(private val context: Context) {

    companion object {
        private const val TAG = "VRMusicBridge"

        fun unescapeHtml(text: String?): String {
            if (text.isNullOrBlank()) return ""
            return try {
                android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()
            } catch (_: Exception) {
                text.replace("&quot;", "\"")
                    .replace("&#039;", "'")
                    .replace("&#39;", "'")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&nbsp;", " ")
                    .trim()
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun isAvailable(): Boolean = true

    @JavascriptInterface
    fun playTrack(url: String?, title: String?, artist: String?, artworkUrl: String?, durationMs: Long) {
        if (url.isNullOrBlank()) return
        val trackTitle = if (title.isNullOrBlank()) "Vagabond Music" else unescapeHtml(title)
        val trackArtist = if (artist.isNullOrBlank()) "Vagabond Riders" else unescapeHtml(artist)

        mainHandler.post {
            val track = VRTrack(
                title = trackTitle,
                artist = trackArtist,
                streamUrl = url.trim(),
                artworkUrl = artworkUrl?.trim(),
                durationMs = durationMs
            )
            VRMusicManager.playTrack(context, track)
        }
    }

    @JavascriptInterface
    fun setPlaylist(jsonArrayString: String?, startIndex: Int) {
        if (jsonArrayString.isNullOrBlank()) return
        mainHandler.post {
            try {
                val jsonArray = JSONArray(jsonArrayString)
                val trackList = mutableListOf<VRTrack>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val url = obj.optString("url", obj.optString("src", obj.optString("streamUrl", "")))
                    if (url.isNotBlank()) {
                        val title = unescapeHtml(obj.optString("title", obj.optString("name", "Track ${i + 1}")))
                        val artist = unescapeHtml(obj.optString("artist", obj.optString("singer", "Vagabond Riders")))
                        val album = unescapeHtml(obj.optString("album", "VR Road Trips"))
                        val artwork = obj.optString("artwork", obj.optString("artworkUrl", obj.optString("poster", "")))
                        val duration = obj.optLong("duration", obj.optLong("durationMs", 0L))

                        trackList.add(
                            VRTrack(
                                title = title,
                                artist = artist,
                                album = album,
                                streamUrl = url.trim(),
                                artworkUrl = if (artwork.isNotBlank()) artwork.trim() else null,
                                durationMs = duration
                            )
                        )
                    }
                }

                if (trackList.isNotEmpty()) {
                    VRMusicManager.setPlaylistAndPlay(context, trackList, startIndex)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing playlist JSON: $jsonArrayString", e)
            }
        }
    }

    @JavascriptInterface
    fun play() {
        mainHandler.post { VRMusicManager.play() }
    }

    @JavascriptInterface
    fun pause() {
        mainHandler.post { VRMusicManager.pause() }
    }

    @JavascriptInterface
    fun toggle() {
        mainHandler.post { VRMusicManager.togglePlayPause() }
    }

    @JavascriptInterface
    fun next() {
        mainHandler.post { VRMusicManager.nextTrack() }
    }

    @JavascriptInterface
    fun previous() {
        mainHandler.post { VRMusicManager.previousTrack() }
    }

    @JavascriptInterface
    fun seekTo(positionMs: Long) {
        mainHandler.post { VRMusicManager.seekTo(positionMs) }
    }

    @JavascriptInterface
    fun stop() {
        mainHandler.post { VRMusicManager.stop() }
    }

    @JavascriptInterface
    fun updateStreamUrl(url: String?) {
        if (url.isNullOrBlank()) return
        mainHandler.post {
            val cleanUrl = url.trim()
            if (cleanUrl.startsWith("http://", ignoreCase = true) || cleanUrl.startsWith("https://", ignoreCase = true)) {
                VRMusicManager.updatePlaybackState { state ->
                    val curr = state.currentTrack
                    if (curr != null && curr.streamUrl.isBlank()) {
                        state.copy(currentTrack = curr.copy(streamUrl = cleanUrl))
                    } else {
                        state
                    }
                }
            }
        }
    }

    /**
     * Web page media session sync: Web audio players can sync their current track
     * metadata directly to native lock-screen.
     */
    @JavascriptInterface
    fun syncMediaMetadata(title: String?, artist: String?, album: String?, artworkUrl: String?, durationMs: Long) {
        syncNowPlaying(title, artist, album, artworkUrl, true, 0L, durationMs, null)
    }

    @JavascriptInterface
    fun syncMediaMetadata(title: String?, artist: String?, album: String?, artworkUrl: String?, durationMs: Long, streamUrl: String?) {
        syncNowPlaying(title, artist, album, artworkUrl, true, 0L, durationMs, streamUrl)
    }

    @JavascriptInterface
    fun syncNowPlaying(title: String?, artist: String?, album: String?, artworkUrl: String?, isPlaying: Boolean, positionMs: Long, durationMs: Long) {
        syncNowPlaying(title, artist, album, artworkUrl, isPlaying, positionMs, durationMs, null)
    }

    @JavascriptInterface
    fun syncNowPlaying(title: String?, artist: String?, album: String?, artworkUrl: String?, isPlaying: Boolean, positionMs: Long, durationMs: Long, streamUrl: String?) {
        if (title.isNullOrBlank()) return
        mainHandler.post {
            val currentState = VRMusicManager.playbackState.value
            val isCurrentOffline = currentState.currentTrack?.isOfflineAvailable == true

            // If an offline song is currently playing or loaded, do NOT allow idle/paused web DOM polling
            // to wipe out the offline track title or reset the state to paused.
            if (isCurrentOffline && !isPlaying) {
                return@post
            }

            val trackTitle = unescapeHtml(title)
            val trackArtist = if (artist.isNullOrBlank()) "Vagabond Riders" else unescapeHtml(artist)
            val trackAlbum = if (album.isNullOrBlank()) "VR Music" else unescapeHtml(album)
            val trackArtwork = if (artworkUrl.isNullOrBlank()) null else artworkUrl.trim()
            val validStreamUrl = streamUrl?.trim() ?: ""

            val existingTrack = currentState.currentTrack
            val finalStreamUrl = if (validStreamUrl.startsWith("http://", ignoreCase = true) || validStreamUrl.startsWith("https://", ignoreCase = true)) {
                validStreamUrl
            } else if (existingTrack != null && existingTrack.title.equals(trackTitle, ignoreCase = true) && existingTrack.streamUrl.isNotBlank()) {
                existingTrack.streamUrl
            } else {
                ""
            }

            val track = VRTrack(
                title = trackTitle,
                artist = trackArtist,
                album = trackAlbum,
                streamUrl = finalStreamUrl,
                artworkUrl = trackArtwork,
                durationMs = durationMs
            )

            VRMusicManager.syncWebTrack(
                context = context,
                track = track,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs
            )
        }
    }

    @JavascriptInterface
    fun updateMetadata(title: String?, artist: String?, artworkUrl: String?) {
        syncNowPlaying(title, artist, "VR Music", artworkUrl, true, 0L, 0L, null)
    }

    @JavascriptInterface
    fun onPlay(title: String?, artist: String?, artworkUrl: String?, durationMs: Long) {
        syncNowPlaying(title, artist, "VR Music", artworkUrl, true, 0L, durationMs, null)
    }

    @JavascriptInterface
    fun onPause() {
        syncPlaybackState(false, VRMusicManager.playbackState.value.positionMs, VRMusicManager.playbackState.value.durationMs)
    }

    @JavascriptInterface
    fun onEnded() {
        syncPlaybackState(false, VRMusicManager.playbackState.value.durationMs, VRMusicManager.playbackState.value.durationMs)
    }

    /**
     * Web page media session sync: updates current playback state and position.
     */
    @JavascriptInterface
    fun syncPlaybackState(isPlaying: Boolean, positionMs: Long, durationMs: Long) {
        mainHandler.post {
            val currentState = VRMusicManager.playbackState.value
            val isCurrentOffline = currentState.currentTrack?.isOfflineAvailable == true

            if (isCurrentOffline && !isPlaying) {
                // Do not let paused web audio state interfere with active offline playback
                return@post
            }

            VRMusicManager.updatePlaybackState {
                it.copy(
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = if (durationMs > 0) durationMs else it.durationMs
                )
            }
            VRMusicManager.updateServicePlaybackStatus(isPlaying, positionMs)
        }
    }
}
