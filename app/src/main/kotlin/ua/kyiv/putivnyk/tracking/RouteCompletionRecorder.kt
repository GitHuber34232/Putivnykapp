package ua.kyiv.putivnyk.tracking

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ua.kyiv.putivnyk.data.repository.AchievementsRepository
import ua.kyiv.putivnyk.data.repository.StatsRepository
import ua.kyiv.putivnyk.data.repository.UserPreferenceRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Counts a finished route once, whether the map or the background service saw the finish. */
@Singleton
class RouteCompletionRecorder @Inject constructor(
    private val userPreferenceRepository: UserPreferenceRepository,
    private val statsRepository: StatsRepository,
    private val achievementsRepository: AchievementsRepository
) {
    private val mutex = Mutex()

    /** True if this call recorded the completion. */
    suspend fun record(routeId: Long): Boolean {
        val recorded = mutex.withLock {
            val key = key(routeId)
            if (userPreferenceRepository.getString(key, "").isNotEmpty()) return@withLock false
            val now = System.currentTimeMillis()
            userPreferenceRepository.upsert(key, now.toString())
            statsRepository.recordRouteCompleted(now)
            true
        }
        if (recorded) runCatching { achievementsRepository.evaluate() }
        return recorded
    }

    /** Lets the next run of the same route count again (its progress was reset). */
    suspend fun reset(routeId: Long) {
        userPreferenceRepository.deleteByKey(key(routeId))
    }

    private fun key(routeId: Long) = "route.completedAt.$routeId"
}
