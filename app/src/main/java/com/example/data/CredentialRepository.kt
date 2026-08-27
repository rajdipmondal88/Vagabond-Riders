package com.example.data

import kotlinx.coroutines.flow.Flow

class CredentialRepository(private val credentialDao: CredentialDao) {
    val allCredentials: Flow<List<SavedCredential>> = credentialDao.getAllCredentials()

    suspend fun getById(id: Long): SavedCredential? = credentialDao.getCredentialById(id)

    suspend fun getByUsername(username: String): SavedCredential? = credentialDao.getCredentialByUsername(username)

    suspend fun saveCredential(credential: SavedCredential): Long = credentialDao.insertCredential(credential)

    suspend fun updateCredential(credential: SavedCredential) = credentialDao.updateCredential(credential)

    suspend fun deleteCredential(credential: SavedCredential) = credentialDao.deleteCredential(credential)

    suspend fun deleteById(id: Long) = credentialDao.deleteCredentialById(id)

    suspend fun markLastUsed(id: Long) = credentialDao.updateLastUsed(id)
}
