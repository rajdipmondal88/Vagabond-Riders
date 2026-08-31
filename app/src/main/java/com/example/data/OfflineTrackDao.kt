package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineTrackDao {

    @Query("SELECT * FROM offline_tracks ORDER BY downloadedAt DESC")
    fun getAllOfflineTracksFlow(): Flow<List<OfflineTrack>>

    @Query("SELECT * FROM offline_tracks ORDER BY downloadedAt DESC")
    suspend fun getAllOfflineTracks(): List<OfflineTrack>

    @Query("SELECT * FROM offline_tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: String): OfflineTrack?

    @Query("SELECT * FROM offline_tracks WHERE originalStreamUrl = :url LIMIT 1")
    suspend fun getTrackByUrl(url: String): OfflineTrack?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: OfflineTrack)

    @Query("DELETE FROM offline_tracks WHERE id = :id")
    suspend fun deleteTrackById(id: String)

    @Query("DELETE FROM offline_tracks")
    suspend fun deleteAllTracks()
}
