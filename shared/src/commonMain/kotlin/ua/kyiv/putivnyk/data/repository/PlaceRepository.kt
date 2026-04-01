package ua.kyiv.putivnyk.data.repository

import kotlinx.coroutines.flow.Flow
import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.data.model.PlaceCategory

interface PlaceRepository {
    fun getAllPlaces(): Flow<List<Place>>
    fun getFavoritePlaces(): Flow<List<Place>>
    fun getPlacesByCategory(category: PlaceCategory): Flow<List<Place>>
    suspend fun getPlaceById(id: Long): Place?
    suspend fun savePlace(place: Place): Long
    suspend fun savePlaces(places: List<Place>)
    suspend fun updatePlace(place: Place)
    suspend fun deletePlace(place: Place)
    suspend fun deleteAllPlaces()
    suspend fun getPlacesCount(): Int
    suspend fun toggleFavorite(placeId: Long)
    suspend fun toggleVisited(placeId: Long)
    suspend fun getAllPlacesSnapshot(): List<Place>
    suspend fun searchPlaces(query: String): List<Place>
}