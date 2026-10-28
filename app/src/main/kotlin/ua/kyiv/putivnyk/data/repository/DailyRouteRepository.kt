package ua.kyiv.putivnyk.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ua.kyiv.putivnyk.domain.stats.StatsTime
import ua.kyiv.putivnyk.domain.usecase.routing.DailyRouteGenerator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The route of the day is chosen once per day and remembered, so the widget and the home
 * screen show the same stops all day, even after some of them get visited.
 */
@Singleton
class DailyRouteRepository @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val userPreferenceRepository: UserPreferenceRepository
) {
    private val mutex = Mutex()

    suspend fun today(): DailyRouteGenerator.DailyRoute? = mutex.withLock {
        val date = StatsTime.today()
        val places = placeRepository.getAllPlacesSnapshot()
        if (userPreferenceRepository.getString(KEY_DATE, "") == date.toString()) {
            val byId = places.associateBy { it.id }
            val stored = userPreferenceRepository.getString(KEY_IDS, "")
                .split(',')
                .mapNotNull { it.trim().toLongOrNull()?.let(byId::get) }
            if (stored.size >= MIN_STOPS) return@withLock DailyRouteGenerator.fromPlaces(date, stored)
        }
        val generated = DailyRouteGenerator.generate(places, date) ?: return@withLock null
        userPreferenceRepository.upsert(KEY_DATE, date.toString())
        userPreferenceRepository.upsert(KEY_IDS, generated.places.joinToString(",") { it.id.toString() })
        generated
    }

    /** Follows place changes, e.g. stops of today's route being visited. */
    fun observeToday(): Flow<DailyRouteGenerator.DailyRoute?> =
        placeRepository.getAllPlaces().map { today() }

    private companion object {
        const val KEY_DATE = "daily.route.date"
        const val KEY_IDS = "daily.route.ids"
        const val MIN_STOPS = 3
    }
}
