package ua.kyiv.putivnyk.domain.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.kyiv.putivnyk.data.model.PlaceCategory
import ua.kyiv.putivnyk.data.model.RiverBank
import java.time.LocalDate
import java.time.ZoneId

class AchievementsTest {

    private val today = LocalDate.of(2026, 10, 27)

    private fun day(date: LocalDate, meters: Double, routes: Int = 0, startHour: Int = 12): DayRecord {
        val start = date.atTime(startHour, 0).atZone(StatsTime.zone).toInstant().toEpochMilli()
        return DayRecord(date.toString(), meters, 3_600_000L, routes, start, start + 3_600_000L)
    }

    @Test
    fun streakCountsConsecutiveActiveDaysUpToYesterday() {
        val snapshot = StatsSnapshot(
            days = listOf(
                day(today.minusDays(1), 2_000.0),
                day(today.minusDays(2), 900.0),
                day(today.minusDays(3), 100.0), // too short to count
                day(today.minusDays(4), 3_000.0)
            ),
            today = today
        )
        assertEquals(2, snapshot.currentStreakDays)
        assertEquals(2, snapshot.longestStreakDays)
        assertEquals(6_000.0, snapshot.totalDistanceMeters, 0.001)
        assertEquals(3_000.0, snapshot.bestDayDistanceMeters, 0.001)
    }

    @Test
    fun visitsAndFinishedRoutesMakeADayActive() {
        val visitAt = today.atTime(19, 30).atZone(StatsTime.zone).toInstant().toEpochMilli()
        val snapshot = StatsSnapshot(
            visits = listOf(VisitRecord(1L, visitAt)),
            days = listOf(day(today.minusDays(1), 0.0, routes = 1)),
            today = today
        )
        assertEquals(2, snapshot.currentStreakDays)
    }

    @Test
    fun bothBanksAreCountedForPlacesOnEitherSide() {
        val snapshot = StatsSnapshot(
            visitedPlaces = listOf(
                VisitedPlace(1L, "Лавра", PlaceCategory.MONASTERY, RiverBank.RIGHT),
                VisitedPlace(2L, "Гідропарк", PlaceCategory.PARK, RiverBank.LEFT)
            ),
            today = today
        )
        assertEquals(2, snapshot.banksVisited)
        assertTrue(AchievementEngine.newlyUnlocked(snapshot).any { it.id == "two_banks" })
    }

    @Test
    fun alreadyUnlockedAchievementsAreNotUnlockedAgain() {
        val snapshot = StatsSnapshot(
            visitedPlaces = listOf(VisitedPlace(1L, "Софія", PlaceCategory.CATHEDRAL, RiverBank.RIGHT)),
            unlockedAchievements = mapOf("first_visit" to 1L),
            today = today
        )
        val fresh = AchievementEngine.newlyUnlocked(snapshot).map { it.id }
        assertFalse("first_visit" in fresh)
        val progress = AchievementEngine.progress(snapshot).first { it.definition.id == "first_visit" }
        assertTrue(progress.isUnlocked)
        assertEquals(1f, progress.fraction, 0f)
    }

    @Test
    fun progressIsAFractionOfTheTarget() {
        val snapshot = StatsSnapshot(days = listOf(day(today, 2_500.0)), today = today)
        val fiveKm = AchievementEngine.progress(snapshot).first { it.definition.id == "day_5km" }
        assertEquals(0.5f, fiveKm.fraction, 0.01f)
        assertFalse(fiveKm.isUnlocked)
    }

    @Test
    fun earlyWalksAndNightVisitsAreDetected() {
        val nightVisit = today.atTime(22, 15).atZone(StatsTime.zone).toInstant().toEpochMilli()
        val snapshot = StatsSnapshot(
            days = listOf(day(today, 1_200.0, startHour = 6)),
            visits = listOf(VisitRecord(5L, nightVisit)),
            today = today
        )
        assertTrue(snapshot.hasEarlyWalk)
        assertTrue(snapshot.hasNightVisit)
    }

    @Test
    fun daysAreKeptInKyivTime() {
        // 22:30 UTC on 26 October is already 27 October in Kyiv (UTC+2 in winter).
        val utc = LocalDate.of(2026, 10, 26).atTime(22, 30).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals("2026-10-27", StatsTime.dayKey(utc))
    }

    @Test
    fun everyAchievementHasAUniqueId() {
        val ids = AchievementCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
