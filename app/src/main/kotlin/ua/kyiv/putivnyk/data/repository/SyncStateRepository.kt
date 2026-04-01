package ua.kyiv.putivnyk.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ua.kyiv.putivnyk.data.local.dao.SyncStateDao
import ua.kyiv.putivnyk.data.model.SyncState
import ua.kyiv.putivnyk.data.model.SyncStatus
import ua.kyiv.putivnyk.data.model.toDomain
import ua.kyiv.putivnyk.data.model.toEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomSyncStateRepository @Inject constructor(
    private val syncStateDao: SyncStateDao
) : SyncStateRepository {
    override
    fun observeAll(): Flow<List<SyncState>> =
        syncStateDao.observeAll().map { list -> list.map { it.toDomain() } }

    override
    suspend fun getByEntity(entityName: String): SyncState? =
        syncStateDao.getByEntity(entityName)?.toDomain()

    override
    suspend fun setRunning(entityName: String) {
        syncStateDao.upsert(
            SyncState(
                entityName = entityName,
                status = SyncStatus.RUNNING,
                updatedAt = System.currentTimeMillis()
            ).toEntity()
        )
    }

    override
    suspend fun setSuccess(entityName: String, syncedAt: Long) {
        syncStateDao.upsert(
            SyncState(
                entityName = entityName,
                lastSyncAt = syncedAt,
                status = SyncStatus.SUCCESS,
                lastError = null,
                updatedAt = System.currentTimeMillis()
            ).toEntity()
        )
    }

    override
    suspend fun setError(entityName: String, message: String?) {
        val current = getByEntity(entityName)
        syncStateDao.upsert(
            SyncState(
                entityName = entityName,
                lastSyncAt = current?.lastSyncAt ?: 0,
                status = SyncStatus.ERROR,
                lastError = message,
                updatedAt = System.currentTimeMillis()
            ).toEntity()
        )
    }

    override
    suspend fun clearAll() {
        syncStateDao.clearAll()
    }
}
