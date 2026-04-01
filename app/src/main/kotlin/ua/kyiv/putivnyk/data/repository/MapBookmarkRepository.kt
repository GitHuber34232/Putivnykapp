package ua.kyiv.putivnyk.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ua.kyiv.putivnyk.data.local.dao.MapBookmarkDao
import ua.kyiv.putivnyk.data.model.MapBookmark
import ua.kyiv.putivnyk.data.model.toDomain
import ua.kyiv.putivnyk.data.model.toEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomMapBookmarkRepository @Inject constructor(
    private val mapBookmarkDao: MapBookmarkDao
) : MapBookmarkRepository {
    override
    fun observeAll(): Flow<List<MapBookmark>> =
        mapBookmarkDao.observeAll().map { list -> list.map { it.toDomain() } }

    override
    suspend fun getById(id: Long): MapBookmark? =
        mapBookmarkDao.getById(id)?.toDomain()

    override
    suspend fun save(bookmark: MapBookmark): Long {
        val now = System.currentTimeMillis()
        val prepared = if (bookmark.id == 0L) {
            bookmark.copy(createdAt = now, updatedAt = now)
        } else {
            bookmark.copy(updatedAt = now)
        }
        return mapBookmarkDao.insert(prepared.toEntity())
    }

    override
    suspend fun update(bookmark: MapBookmark) {
        mapBookmarkDao.update(bookmark.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    override
    suspend fun delete(bookmark: MapBookmark) {
        mapBookmarkDao.delete(bookmark.toEntity())
    }

    override
    suspend fun deleteAll() {
        mapBookmarkDao.deleteAll()
    }
}
