package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.OfflineTrack
import com.example.data.OfflineTrackDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OfflineTrackDatabaseTest {

    private lateinit var database: AppDatabase
    private lateinit var offlineTrackDao: OfflineTrackDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        offlineTrackDao = database.offlineTrackDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testInsertAndRetrieveOfflineTracks() = runBlocking {
        val track1 = OfflineTrack(
            id = "track_1",
            title = "Highway to Freedom",
            artist = "Vagabond Anthem",
            album = "Road Anthems",
            originalStreamUrl = "https://app.vagabondriders.com/music/highway.mp3",
            localFilePath = "/data/user/0/com.example/files/music/track_1.mp3",
            artworkUrl = null,
            durationMs = 210000L,
            fileSize = 4500000L
        )

        val track2 = OfflineTrack(
            id = "track_2",
            title = "Midnight Rider",
            artist = "Vagabond Cruisers",
            album = "Night Rides",
            originalStreamUrl = "https://app.vagabondriders.com/music/midnight.mp3",
            localFilePath = "/data/user/0/com.example/files/music/track_2.mp3",
            artworkUrl = null,
            durationMs = 185000L,
            fileSize = 3900000L
        )

        offlineTrackDao.insertTrack(track1)
        offlineTrackDao.insertTrack(track2)

        val allTracks = offlineTrackDao.getAllOfflineTracksFlow().first()
        assertEquals(2, allTracks.size)

        val retrieved = offlineTrackDao.getTrackById("track_1")
        assertNotNull(retrieved)
        assertEquals("Highway to Freedom", retrieved?.title)

        offlineTrackDao.deleteTrackById("track_1")
        val remainingTracks = offlineTrackDao.getAllOfflineTracksFlow().first()
        assertEquals(1, remainingTracks.size)
        assertEquals("Midnight Rider", remainingTracks[0].title)
    }
}
