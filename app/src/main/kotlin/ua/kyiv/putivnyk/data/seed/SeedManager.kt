package ua.kyiv.putivnyk.data.seed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.data.repository.PlaceRepository
import ua.kyiv.putivnyk.data.repository.UserPreferenceRepository
import ua.kyiv.putivnyk.platform.io.TextResourceLoader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeedManager @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val userPreferenceRepository: UserPreferenceRepository,
    private val resourceLoader: TextResourceLoader
) {

    suspend fun seedDatabase() = withContext(Dispatchers.IO) {
        val storedVersion = userPreferenceRepository.getString(SEED_VERSION_KEY, "0").toIntOrNull() ?: 0
        val placesCount = placeRepository.getPlacesCount()

        if (placesCount == 0) {
            loadTouristPlaces()
            loadExtraPlaces()
            markSeedVersionApplied()
            return@withContext
        }

        if (storedVersion < CURRENT_SEED_VERSION) {
            forceRefresh()
            markSeedVersionApplied()
        }
    }

    private suspend fun loadTouristPlaces() {
        val places = loadPlacesFromAsset("kyiv_tourist_places.json")
        if (places.isNotEmpty()) {
            placeRepository.savePlaces(places)
        }
    }

    private suspend fun loadExtraPlaces() {
        val places = loadPlacesFromAsset("kyiv_extra_seed.json")
        if (places.isNotEmpty()) {
            placeRepository.savePlaces(places)
        }
    }

    private fun loadPlacesFromAsset(fileName: String): List<Place> {
        val json = resourceLoader.loadText(fileName) ?: return emptyList()

        return PlaceSeedParser.parsePlaces(json)
    }

    suspend fun forceRefresh() = withContext(Dispatchers.IO) {
        placeRepository.deleteAllPlaces()
        loadTouristPlaces()
        loadExtraPlaces()
    }

    private suspend fun markSeedVersionApplied() {
        userPreferenceRepository.upsert(SEED_VERSION_KEY, CURRENT_SEED_VERSION.toString())
    }

    companion object {
        private const val SEED_VERSION_KEY = "seed.version"
        private const val CURRENT_SEED_VERSION = 8
    }
}
