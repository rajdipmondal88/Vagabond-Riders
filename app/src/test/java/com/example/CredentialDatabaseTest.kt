package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.CredentialDao
import com.example.data.SavedCredential
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
class CredentialDatabaseTest {

    private lateinit var database: AppDatabase
    private lateinit var credentialDao: CredentialDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        credentialDao = database.credentialDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testInsertAndRetrieveMultipleCredentials() = runBlocking {
        val adminCred = SavedCredential(
            accountLabel = "Admin Account",
            username = "admin@vagabondriders.com",
            password = "SecureAdminPass123",
            role = "ADMIN",
            autoSubmit = true
        )

        val riderCred = SavedCredential(
            accountLabel = "Rider - John",
            username = "john.rider",
            password = "RiderPass2026",
            role = "RIDER",
            autoSubmit = true
        )

        val adminId = credentialDao.insertCredential(adminCred)
        val riderId = credentialDao.insertCredential(riderCred)

        assertTrue(adminId > 0)
        assertTrue(riderId > 0)

        val all = credentialDao.getAllCredentials().first()
        assertEquals(2, all.size)

        val retrievedAdmin = credentialDao.getCredentialById(adminId)
        assertNotNull(retrievedAdmin)
        assertEquals("admin@vagabondriders.com", retrievedAdmin?.username)
        assertEquals("SecureAdminPass123", retrievedAdmin?.password)
        assertEquals("ADMIN", retrievedAdmin?.role)
    }

    @Test
    fun testDeleteCredential() = runBlocking {
        val cred = SavedCredential(
            accountLabel = "Temp Staff",
            username = "staff@test.com",
            password = "password",
            role = "USER"
        )
        val id = credentialDao.insertCredential(cred)
        assertEquals(1, credentialDao.getAllCredentials().first().size)

        credentialDao.deleteCredentialById(id)
        assertEquals(0, credentialDao.getAllCredentials().first().size)
    }
}
