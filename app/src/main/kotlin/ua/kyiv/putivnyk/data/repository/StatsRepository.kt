package ua.kyiv.putivnyk.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ua.kyiv.putivnyk.data.local.dao.StatsDao
import ua.kyiv.putivnyk.data.local.entity.AchievementEntity
import ua.kyiv.putivnyk.data.local.entity.CounterEntity
import ua.kyiv.putivnyk.data.local.entity.VisitEntity
import ua.kyiv.putivnyk.data.local.entity.WalkDayEntity
import ua.kyiv.putivnyk.data.model.Place
import ua.kyiv.putivnyk.domain.stats.DayRecord
import ua.kyiv.putivnyk.domain.stats.StatsSnapshot
import ua.kyiv.putivnyk.domain.stats.StatsTime
import ua.kyiv.putivnyk.domain.stats.VisitRecord
import ua.kyiv.putivnyk.domain.stats.VisitedPlace
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepository @Inject constructor(
    private val statsDao: StatsDao,
    private val placeRepository: PlaceRepository
) {
    fun observeSnapshot(): Flow<StatsSnapshot> = combine(
        placeRepository.getAllPlaces(),
        statsDao.observeVisits(),
        statsDao.observeDays(),
        statsDao.observeCounters(),
        statsDao.observeAchievements()
    ) { places, visits, days, counters, achievements ->
        buildSnapshot(places, visits, days, counters, achievements)
    }

    suspend fun snapshot(): StatsSnapshot = buildSnapshot(
        places = placeRepository.getAllPlacesSnapshot(),
        visits = statsDao.getVisits(),
        days = statsDao.getDays(),
        counters = statsDao.getCounters(),
        achievements = statsDao.getAchievements()
    )

    suspend fun recordVisit(placeId: Long, source: String, at: Long = System.currentTimeMillis()) {
        statsDao.insertVisit(VisitEntity(placeId = placeId, visitedAt = at, source = source))
    }

    suspend fun removeVisits(placeId: Long) = statsDao.deleteVisitsForPlace(placeId)

    suspend fun addWalk(meters: Double, millis: Long, startedAt: Long, endedAt: Long) {
        if (meters <= 0.0) return
        statsDao.addWalk(StatsTime.dayKey(endedAt), meters, millis.coerceAtLeast(0L), startedAt, endedAt)
    }

    suspend fun recordRouteCompleted(at: Long = System.currentTimeMillis()) {
        statsDao.addCompletedRoute(StatsTime.dayKey(at), at)
    }

    suspend fun increment(counter: String, delta: Long = 1L) = statsDao.incrementCounter(counter, delta)

    /** True when [id] was not unlocked before. */
    suspend fun unlockAchievement(id: String, at: Long): Boolean =
        statsDao.insertAchievement(AchievementEntity(id = id, unlockedAt = at)) != -1L

    private fun buildSnapshot(
        places: List<Place>,
        visits: List<VisitEntity>,
        days: List<WalkDayEntity>,
        counters: List<CounterEntity>,
        achievements: List<AchievementEntity>
    ): StatsSnapshot = StatsSnapshot(
        visitedPlaces = places.filter { it.isVisited }.map { place ->
            VisitedPlace(place.id, place.name, place.category, place.riverBank)
        },
        visits = visits.map { VisitRecord(it.placeId, it.visitedAt) },
        days = days.map {
            DayRecord(it.day, it.distanceMeters, it.activeMillis, it.routesCompleted, it.firstActivityAt, it.lastActivityAt)
        },
        counters = counters.associate { it.name to it.value },
        favoritesCount = places.count { it.isFavorite },
        unlockedAchievements = achievements.associate { it.id to it.unlockedAt },
        today = StatsTime.today()
    )
}
