package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CredentialDao {
    @Query("SELECT * FROM saved_credentials ORDER BY lastUsedTimestamp DESC")
    fun getAllCredentials(): Flow<List<SavedCredential>>

    @Query("SELECT * FROM saved_credentials WHERE id = :id LIMIT 1")
    suspend fun getCredentialById(id: Long): SavedCredential?

    @Query("SELECT * FROM saved_credentials WHERE username = :username LIMIT 1")
    suspend fun getCredentialByUsername(username: String): SavedCredential?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCredential(credential: SavedCredential): Long

    @Update
    suspend fun updateCredential(credential: SavedCredential)

    @Delete
    suspend fun deleteCredential(credential: SavedCredential)

    @Query("DELETE FROM saved_credentials WHERE id = :id")
    suspend fun deleteCredentialById(id: Long)

    @Query("UPDATE saved_credentials SET lastUsedTimestamp = :timestamp WHERE id = :id")
    suspend fun updateLastUsed(id: Long, timestamp: Long = System.currentTimeMillis())
}
