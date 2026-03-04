package ua.kyiv.putivnyk.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ua.kyiv.putivnyk.data.local.dao.PlaceDao
import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.data.model.PlaceCategory
import ua.kyiv.putivnyk.data.model.toDomain
import ua.kyiv.putivnyk.data.model.toEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaceRepository @Inject constructor(
    private val placeDao: PlaceDao
) {

    fun getAllPlaces(): Flow<List<Place>> =
        placeDao.getAllPlaces().map { entities ->
            entities.map { it.toDomain() }
        }

    fun getFavoritePlaces(): Flow<List<Place>> =
        placeDao.getFavoritePlaces().map { entities ->
            entities.map { it.toDomain() }
        }

    fun getPlacesByCategory(category: PlaceCategory): Flow<List<Place>> =
        placeDao.getPlacesByCategory(category.name.lowercase()).map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun getPlaceById(id: Long): Place? =
        placeDao.getPlaceById(id)?.toDomain()

    suspend fun savePlace(place: Place): Long {
        require(place.name.isNotBlank()) { "Place name must not be blank" }
        require(place.latitude in -90.0..90.0) { "Invalid latitude: ${place.latitude}" }
        require(place.longitude in -180.0..180.0) { "Invalid longitude: ${place.longitude}" }
        return placeDao.insertPlace(place.toEntity())
    }

    suspend fun savePlaces(places: List<Place>) {
        val valid = places.filter {
            it.name.isNotBlank() &&
                it.latitude in -90.0..90.0 &&
                it.longitude in -180.0..180.0
        }
        if (valid.isNotEmpty()) {
            placeDao.insertPlaces(valid.map { it.toEntity() })
        }
    }

    suspend fun updatePlace(place: Place) =
        placeDao.updatePlace(
            place.copy(updatedAt = System.currentTimeMillis()).toEntity()
        )

    suspend fun deletePlace(place: Place) =
        placeDao.deletePlace(place.toEntity())

    suspend fun deleteAllPlaces() =
        placeDao.deleteAllPlaces()

    suspend fun getPlacesCount(): Int =
        placeDao.getPlacesCount()

    suspend fun toggleFavorite(placeId: Long) {
        val place = getPlaceById(placeId) ?: return
        updatePlace(place.copy(isFavorite = !place.isFavorite))
    }

    suspend fun toggleVisited(placeId: Long) {
        val place = getPlaceById(placeId) ?: return
        updatePlace(place.copy(isVisited = !place.isVisited))
    }

    suspend fun getAllPlacesSnapshot(): List<Place> =
        getAllPlaces().first()

    suspend fun searchPlaces(query: String): List<Place> {
        val normalized = query.trim()
        if (normalized.isBlank()) return emptyList()

        val currentSnapshot = getAllPlaces().first()
        return currentSnapshot.filter { place ->
            place.name.contains(normalized, ignoreCase = true) ||
                place.nameEn?.contains(normalized, ignoreCase = true) == true ||
                place.description?.contains(normalized, ignoreCase = true) == true
        }
    }
}
