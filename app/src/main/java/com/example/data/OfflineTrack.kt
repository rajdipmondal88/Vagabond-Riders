package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.media.VRTrack

@Entity(tableName = "offline_tracks")
data class OfflineTrack(
    @PrimaryKey
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val originalStreamUrl: String,
    val localFilePath: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val fileSize: Long,
    val downloadedAt: Long = System.currentTimeMillis()
) {
    fun toVRTrack(): VRTrack {
        return VRTrack(
            id = id,
            title = title,
            artist = artist,
            album = album,
            streamUrl = originalStreamUrl,
            artworkUrl = artworkUrl,
            durationMs = durationMs,
            isOfflineAvailable = true,
            localFilePath = localFilePath,
            dateAdded = downloadedAt
        )
    }

    companion object {
        fun fromVRTrack(track: VRTrack, localPath: String, size: Long): OfflineTrack {
            return OfflineTrack(
                id = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                originalStreamUrl = track.streamUrl,
                localFilePath = localPath,
                artworkUrl = track.artworkUrl,
                durationMs = track.durationMs,
                fileSize = size,
                downloadedAt = System.currentTimeMillis()
            )
        }
    }
}
