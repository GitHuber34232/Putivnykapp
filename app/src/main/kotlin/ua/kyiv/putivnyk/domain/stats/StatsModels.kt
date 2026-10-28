package ua.kyiv.putivnyk.domain.stats

import ua.kyiv.putivnyk.data.model.PlaceCategory
import ua.kyiv.putivnyk.data.model.RiverBank
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Walk statistics are kept per calendar day in Kyiv time. */
object StatsTime {
    val zone: ZoneId = runCatching { ZoneId.of("Europe/Kyiv") }
        .recoverCatching { ZoneId.of("Europe/Kiev") }
        .getOrDefault(ZoneId.systemDefault())

    fun dayKey(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toString()

    fun hourOf(epochMillis: Long): Int = Instant.ofEpochMilli(epochMillis).atZone(zone).hour

    fun today(nowMillis: Long = System.currentTimeMillis()): LocalDate =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
}

/** Names of the event counters kept next to the walk statistics. */
object StatsCounters {
    const val AR_RECOGNITIONS = "ar_recognitions"
    const val AUDIO_LISTENED = "audio_listened"
    const val ROUTES_SHARED = "routes_shared"
    const val ROUTES_IMPORTED = "routes_imported"
    const val OFFLINE_REGIONS = "offline_regions"
}

data class VisitRecord(val placeId: Long, val visitedAt: Long)

data class DayRecord(
    val day: String,
    val distanceMeters: Double,
    val activeMillis: Long,
    val routesCompleted: Int,
    val firstActivityAt: Long,
    val lastActivityAt: Long
)

data class VisitedPlace(
    val placeId: Long,
    val name: String,
    val category: PlaceCategory,
    val riverBank: RiverBank
)

data class StatsSnapshot(
    val visitedPlaces: List<VisitedPlace> = emptyList(),
    val visits: List<VisitRecord> = emptyList(),
    val days: List<DayRecord> = emptyList(),
    val counters: Map<String, Long> = emptyMap(),
    val favoritesCount: Int = 0,
    val unlockedAchievements: Map<String, Long> = emptyMap(),
    val today: LocalDate = StatsTime.today()
) {
    val placesVisited: Int get() = visitedPlaces.size
    val totalDistanceMeters: Double get() = days.sumOf { it.distanceMeters }
    val bestDayDistanceMeters: Double get() = days.maxOfOrNull { it.distanceMeters } ?: 0.0
    val routesCompleted: Int get() = days.sumOf { it.routesCompleted }
    val totalActiveMillis: Long get() = days.sumOf { it.activeMillis }

    fun counter(name: String): Long = counters[name] ?: 0L

    fun visitedIn(vararg categories: PlaceCategory): Int = visitedPlaces.count { it.category in categories }

    /** Distinct Dnipro banks among visited places; a place spanning both counts for both. */
    val banksVisited: Int
        get() {
            val banks = mutableSetOf<RiverBank>()
            visitedPlaces.forEach { place ->
                when (place.riverBank) {
                    RiverBank.LEFT, RiverBank.RIGHT -> banks += place.riverBank
                    RiverBank.BOTH -> {
                        banks += RiverBank.LEFT
                        banks += RiverBank.RIGHT
                    }
                    RiverBank.UNKNOWN -> Unit
                }
            }
            return banks.size
        }

    /** Days with a walk of at least [ACTIVE_DAY_METERS], a visited place or a finished route. */
    val activeDays: Set<LocalDate>
        get() {
            val result = sortedSetOf<LocalDate>()
            days.filter { it.distanceMeters >= ACTIVE_DAY_METERS || it.routesCompleted > 0 }
                .mapNotNullTo(result) { runCatching { LocalDate.parse(it.day) }.getOrNull() }
            visits.mapTo(result) { LocalDate.parse(StatsTime.dayKey(it.visitedAt)) }
            return result
        }

    val longestStreakDays: Int
        get() {
            var best = 0
            var run = 0
            var previous: LocalDate? = null
            activeDays.forEach { day ->
                run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
                best = maxOf(best, run)
                previous = day
            }
            return best
        }

    /** Streak that is still alive, i.e. it ends today or yesterday. */
    val currentStreakDays: Int
        get() {
            val active = activeDays
            var day = if (today in active) today else today.minusDays(1)
            var count = 0
            while (day in active) {
                count++
                day = day.minusDays(1)
            }
            return count
        }

    val hasEarlyWalk: Boolean
        get() = days.any { it.distanceMeters >= 300.0 && StatsTime.hourOf(it.firstActivityAt) < 8 }

    val hasNightVisit: Boolean
        get() = visits.any { visit ->
            val hour = StatsTime.hourOf(visit.visitedAt)
            hour >= 21 || hour < 4
        }

    fun distanceOn(date: LocalDate): Double =
        days.firstOrNull { it.day == date.toString() }?.distanceMeters ?: 0.0

    companion object {
        const val ACTIVE_DAY_METERS = 500.0
    }
}
