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
 * playback with full lock-screen media controls.
 */
class VRMusicJavascriptBridge(private val context: Context) {

    companion object {
        private const val TAG = "VRMusicBridge"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun isAvailable(): Boolean = true

    @JavascriptInterface
    fun playTrack(url: String?, title: String?, artist: String?, artworkUrl: String?, durationMs: Long) {
        if (url.isNullOrBlank()) return
        val trackTitle = if (title.isNullOrBlank()) "Vagabond Music" else title
        val trackArtist = if (artist.isNullOrBlank()) "Vagabond Riders" else artist

        mainHandler.post {
            val track = VRTrack(
                title = trackTitle,
                artist = trackArtist,
                streamUrl = url,
                artworkUrl = artworkUrl,
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
                        val title = obj.optString("title", obj.optString("name", "Track ${i + 1}"))
                        val artist = obj.optString("artist", obj.optString("singer", "Vagabond Riders"))
                        val album = obj.optString("album", "VR Road Trips")
                        val artwork = obj.optString("artwork", obj.optString("artworkUrl", obj.optString("poster", "")))
                        val duration = obj.optLong("duration", obj.optLong("durationMs", 0L))

                        trackList.add(
                            VRTrack(
                                title = title,
                                artist = artist,
                                album = album,
                                streamUrl = url,
                                artworkUrl = if (artwork.isNotBlank()) artwork else null,
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

    /**
     * Web page media session sync: Web audio players can sync their current track
     * metadata directly to native lock-screen without changing their web audio logic.
     */
    @JavascriptInterface
    fun syncMediaMetadata(title: String?, artist: String?, album: String?, artworkUrl: String?, durationMs: Long) {
        if (title.isNullOrBlank()) return
        mainHandler.post {
            val track = VRTrack(
                title = title,
                artist = artist ?: "Vagabond Riders",
                album = album ?: "VR Music",
                streamUrl = "",
                artworkUrl = artworkUrl,
                durationMs = durationMs
            )
            VRMusicManager.updatePlaybackState {
                it.copy(
                    currentTrack = track,
                    durationMs = durationMs
                )
            }
        }
    }

    /**
     * Web page media session sync: updates current playback state and position.
     */
    @JavascriptInterface
    fun syncPlaybackState(isPlaying: Boolean, positionMs: Long, durationMs: Long) {
        mainHandler.post {
            VRMusicManager.updatePlaybackState {
                it.copy(
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = if (durationMs > 0) durationMs else it.durationMs
                )
            }
        }
    }
}
