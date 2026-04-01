package ua.kyiv.putivnyk.data.repository

import kotlinx.coroutines.flow.Flow
import ua.kyiv.putivnyk.data.model.MapBookmark

interface MapBookmarkRepository {
    fun observeAll(): Flow<List<MapBookmark>>
    suspend fun getById(id: Long): MapBookmark?
    suspend fun save(bookmark: MapBookmark): Long
    suspend fun update(bookmark: MapBookmark)
    suspend fun delete(bookmark: MapBookmark)
    suspend fun deleteAll()
}