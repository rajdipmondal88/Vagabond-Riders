package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_credentials")
data class SavedCredential(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val accountLabel: String,         // e.g. "Admin Account", "Rider - John", "Manager"
    val username: String,             // Username or Email
    val password: String,             // User password
    val role: String = "USER",        // ADMIN, RIDER, DISPATCHER, USER, etc.
    val autoSubmit: Boolean = true,   // Automatically clicks submit after filling
    val lastUsedTimestamp: Long = System.currentTimeMillis(),
    val notes: String = ""
)
