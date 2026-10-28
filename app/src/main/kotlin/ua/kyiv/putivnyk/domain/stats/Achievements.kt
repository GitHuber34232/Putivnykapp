package ua.kyiv.putivnyk.domain.stats

import ua.kyiv.putivnyk.data.model.PlaceCategory

enum class AchievementUnit { COUNT, KILOMETERS, DAYS }

/**
 * One achievement. [title] and [description] are Ukrainian; the UI translates them with
 * [titleKey] / [descriptionKey] like every other string.
 */
class AchievementDefinition(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val target: Double,
    val unit: AchievementUnit = AchievementUnit.COUNT,
    val measure: (StatsSnapshot) -> Double
) {
    val titleKey: String get() = "achievement.$id.title"
    val descriptionKey: String get() = "achievement.$id.description"
}

data class AchievementProgress(
    val definition: AchievementDefinition,
    val current: Double,
    val unlockedAt: Long?
) {
    val isUnlocked: Boolean get() = unlockedAt != null
    val fraction: Float
        get() = if (isUnlocked) 1f else (current / definition.target).toFloat().coerceIn(0f, 1f)
}

object AchievementCatalog {
    private fun km(meters: Double) = meters / 1000.0

    val all: List<AchievementDefinition> = listOf(
        AchievementDefinition("first_visit", "👣", "Перший крок", "Відвідайте перше місце", 1.0) {
            it.placesVisited.toDouble()
        },
        AchievementDefinition("explorer", "🧭", "Дослідник", "Відвідайте 10 місць", 10.0) {
            it.placesVisited.toDouble()
        },
        AchievementDefinition("connoisseur", "🏅", "Знавець Києва", "Відвідайте 25 місць", 25.0) {
            it.placesVisited.toDouble()
        },
        AchievementDefinition("legend", "👑", "Легенда міста", "Відвідайте 50 місць", 50.0) {
            it.placesVisited.toDouble()
        },
        AchievementDefinition("pilgrim", "⛪", "Паломник", "Відвідайте 3 собори чи монастирі", 3.0) {
            it.visitedIn(PlaceCategory.CATHEDRAL, PlaceCategory.MONASTERY).toDouble()
        },
        AchievementDefinition("museum_lover", "🏛️", "Музейний гурман", "Відвідайте 3 музеї", 3.0) {
            it.visitedIn(PlaceCategory.MUSEUM).toDouble()
        },
        AchievementDefinition("theatre_goer", "🎭", "Театрал", "Відвідайте 2 театри", 2.0) {
            it.visitedIn(PlaceCategory.THEATER).toDouble()
        },
        AchievementDefinition("green_soul", "🌳", "Зелена душа", "Відвідайте 3 парки", 3.0) {
            it.visitedIn(PlaceCategory.PARK).toDouble()
        },
        AchievementDefinition("architect", "🏰", "Архітектор", "Відвідайте 5 пам'яток архітектури", 5.0) {
            it.visitedIn(PlaceCategory.ARCHITECTURE_MONUMENT).toDouble()
        },
        AchievementDefinition("two_banks", "🌉", "Два береги", "Побувайте на лівому й правому березі Дніпра", 2.0) {
            it.banksVisited.toDouble()
        },
        AchievementDefinition("day_5km", "🚶", "П'ять кілометрів", "Пройдіть 5 км за день", 5.0, AchievementUnit.KILOMETERS) {
            km(it.bestDayDistanceMeters)
        },
        AchievementDefinition("day_10km", "🏃", "Десятка", "Пройдіть 10 км за день", 10.0, AchievementUnit.KILOMETERS) {
            km(it.bestDayDistanceMeters)
        },
        AchievementDefinition("marathon", "🏁", "Марафонець", "Пройдіть 42,2 км загалом", 42.195, AchievementUnit.KILOMETERS) {
            km(it.totalDistanceMeters)
        },
        AchievementDefinition("century", "💯", "Сотня", "Пройдіть 100 км загалом", 100.0, AchievementUnit.KILOMETERS) {
            km(it.totalDistanceMeters)
        },
        AchievementDefinition("first_route", "🗺️", "Перший маршрут", "Пройдіть маршрут до кінця", 1.0) {
            it.routesCompleted.toDouble()
        },
        AchievementDefinition("route_master", "🧩", "Майстер маршрутів", "Пройдіть 5 маршрутів", 5.0) {
            it.routesCompleted.toDouble()
        },
        AchievementDefinition("streak_3", "🔥", "Три дні поспіль", "Гуляйте 3 дні поспіль", 3.0, AchievementUnit.DAYS) {
            it.longestStreakDays.toDouble()
        },
        AchievementDefinition("streak_7", "📅", "Тиждень прогулянок", "Гуляйте 7 днів поспіль", 7.0, AchievementUnit.DAYS) {
            it.longestStreakDays.toDouble()
        },
        AchievementDefinition("early_bird", "🌅", "Ранній птах", "Вирушайте на прогулянку до 8:00", 1.0) {
            if (it.hasEarlyWalk) 1.0 else 0.0
        },
        AchievementDefinition("night_owl", "🌙", "Нічний Київ", "Відвідайте місце після 21:00", 1.0) {
            if (it.hasNightVisit) 1.0 else 0.0
        },
        AchievementDefinition("ar_hunter", "📷", "AR-мисливець", "Розпізнайте 5 пам'яток камерою", 5.0) {
            it.counter(StatsCounters.AR_RECOGNITIONS).toDouble()
        },
        AchievementDefinition("listener", "🎧", "Уважний слухач", "Прослухайте 10 аудіорозповідей", 10.0) {
            it.counter(StatsCounters.AUDIO_LISTENED).toDouble()
        },
        AchievementDefinition("collector", "❤️", "Колекціонер", "Додайте 10 місць в улюблені", 10.0) {
            it.favoritesCount.toDouble()
        },
        AchievementDefinition("sharer", "📤", "Щедра душа", "Поділіться маршрутом", 1.0) {
            it.counter(StatsCounters.ROUTES_SHARED).toDouble()
        },
        AchievementDefinition("offline_ready", "📥", "Готовий до офлайну", "Завантажте офлайн-район карти", 1.0) {
            it.counter(StatsCounters.OFFLINE_REGIONS).toDouble()
        }
    )

    fun byId(id: String): AchievementDefinition? = all.firstOrNull { it.id == id }
}

object AchievementEngine {
    fun progress(snapshot: StatsSnapshot): List<AchievementProgress> =
        AchievementCatalog.all.map { definition ->
            AchievementProgress(
                definition = definition,
                current = definition.measure(snapshot),
                unlockedAt = snapshot.unlockedAchievements[definition.id]
            )
        }

    fun newlyUnlocked(snapshot: StatsSnapshot): List<AchievementDefinition> =
        AchievementCatalog.all.filter { definition ->
            definition.id !in snapshot.unlockedAchievements && definition.measure(snapshot) >= definition.target
        }
}
