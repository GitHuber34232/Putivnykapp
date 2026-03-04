package ua.kyiv.putivnyk.data.seed

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.data.model.PlaceCategory
import ua.kyiv.putivnyk.data.model.RiverBank
import ua.kyiv.putivnyk.data.repository.PlaceRepository
import ua.kyiv.putivnyk.data.repository.UserPreferenceRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeedManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val placeRepository: PlaceRepository,
    private val userPreferenceRepository: UserPreferenceRepository,
    private val gson: Gson
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
        val json = runCatching {
            context.assets.open(fileName)
                .bufferedReader()
                .use { it.readText() }
        }.getOrElse { return emptyList() }

        val placeDtos = runCatching {
            gson.fromJson(json, Array<PlaceDto>::class.java)?.toList().orEmpty()
        }.getOrElse { emptyList() }

        return placeDtos.mapNotNull { it.toDomainOrNull() }
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

data class PlaceDto(
    @SerializedName("id")
    val id: Long = 0,

    @SerializedName("name")
    val name: String,

    @SerializedName("name_en")
    val nameEn: String? = null,

    @SerializedName("latitude")
    val latitude: Double,

    @SerializedName("longitude")
    val longitude: Double,

    @SerializedName("category")
    val category: String,

    @SerializedName("tags")
    val tags: List<String>? = null,

    @SerializedName("isLeftBank")
    val isLeftBankCamel: Boolean? = null,

    @SerializedName("is_left_bank")
    val isLeftBankSnake: Boolean? = null,

    @SerializedName("description")
    val description: String? = null,

    @SerializedName("rating")
    val rating: Double? = null,

    @SerializedName("popularity")
    val popularity: Int? = null,

    @SerializedName("river_bank")
    val riverBank: String? = null,

    @SerializedName("visitDuration")
    val visitDuration: Int? = null,

    @SerializedName("imageUrls")
    val imageUrls: List<String>? = null
) {
    fun toDomainOrNull(): Place? {
        val normalizedName = name.trim()
        val hasValidLatitude = latitude in -90.0..90.0 && latitude.isFinite()
        val hasValidLongitude = longitude in -180.0..180.0 && longitude.isFinite()
        if (normalizedName.isBlank() || !hasValidLatitude || !hasValidLongitude) {
            return null
        }

        val normalizedTags = tags.orEmpty()
            .map { it.trim().uppercase() }
            .filter { it in SUPPORTED_TAGS }
            .distinct()

        val resolvedCategory = when {
            normalizedTags.isNotEmpty() -> categoryFromUnifiedTag(normalizedTags.first())
            else -> PlaceCategory.fromString(category)
        }

        val leftBankFlag = isLeftBankCamel ?: isLeftBankSnake
        val resolvedRiverBank = when (leftBankFlag) {
            true -> RiverBank.LEFT
            false -> RiverBank.RIGHT
            null -> riverBank?.let { RiverBank.fromString(it) } ?: RiverBank.fromCoordinates(longitude)
        }

        return Place(
            id = id,
            name = normalizedName,
            nameEn = nameEn?.trim()?.takeIf { it.isNotBlank() },
            latitude = latitude,
            longitude = longitude,
            category = resolvedCategory,
            tags = normalizedTags,
            description = description,
            rating = rating,
            popularity = popularity ?: ((rating ?: 0.0) * 20).toInt(),
            riverBank = resolvedRiverBank,
            visitDuration = visitDuration,
            imageUrls = imageUrls.orEmpty(),
            isFavorite = false,
            isVisited = false
        )
    }

    private fun categoryFromUnifiedTag(tag: String): PlaceCategory = when (tag) {
        "PARK" -> PlaceCategory.PARK
        "MUSEUM" -> PlaceCategory.MUSEUM
        "THEATER" -> PlaceCategory.THEATER
        "RESTAURANT" -> PlaceCategory.RESTAURANT
        "RELIGION" -> PlaceCategory.CATHEDRAL
        "MONUMENT" -> PlaceCategory.ARCHITECTURE_MONUMENT
        else -> PlaceCategory.OTHER
    }

    companion object {
        private val SUPPORTED_TAGS = setOf(
            "PARK",
            "MUSEUM",
            "THEATER",
            "RESTAURANT",
            "RELIGION",
            "MONUMENT"
        )
    }
}
