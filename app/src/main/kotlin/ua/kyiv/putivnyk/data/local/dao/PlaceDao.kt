package ua.kyiv.putivnyk.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import ua.kyiv.putivnyk.data.local.entity.PlaceEntity

@Dao
interface PlaceDao {

    @Query("SELECT * FROM places ORDER BY updatedAt DESC")
    fun getAllPlaces(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavoritePlaces(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places WHERE category = :category ORDER BY rating DESC")
    fun getPlacesByCategory(category: String): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun getPlaceById(id: Long): PlaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlace(place: PlaceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaces(places: List<PlaceEntity>)

    @Update
    suspend fun updatePlace(place: PlaceEntity)

    @Delete
    suspend fun deletePlace(place: PlaceEntity)

    @Query("DELETE FROM places")
    suspend fun deleteAllPlaces()

    @Query("SELECT COUNT(*) FROM places")
    suspend fun getPlacesCount(): Int

    @Query(
        """
        SELECT places.* FROM places
        JOIN places_fts ON places.rowid = places_fts.rowid
        WHERE places_fts MATCH :query
        ORDER BY places.rating DESC
        """
    )
    suspend fun searchPlaces(query: String): List<PlaceEntity>
}
