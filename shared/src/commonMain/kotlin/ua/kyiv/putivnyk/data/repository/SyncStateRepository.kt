package ua.kyiv.putivnyk.data.repository

import kotlinx.coroutines.flow.Flow
import ua.kyiv.putivnyk.data.model.SyncState
import ua.kyiv.putivnyk.platform.currentTimeMillis

interface SyncStateRepository {
    fun observeAll(): Flow<List<SyncState>>
    suspend fun getByEntity(entityName: String): SyncState?
    suspend fun setRunning(entityName: String)
    suspend fun setSuccess(entityName: String, syncedAt: Long = currentTimeMillis())
    suspend fun setError(entityName: String, message: String?)
    suspend fun clearAll()
}