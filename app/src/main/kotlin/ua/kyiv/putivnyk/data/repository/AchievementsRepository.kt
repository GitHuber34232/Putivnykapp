package ua.kyiv.putivnyk.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ua.kyiv.putivnyk.domain.stats.AchievementDefinition
import ua.kyiv.putivnyk.domain.stats.AchievementEngine
import ua.kyiv.putivnyk.i18n.AppText
import ua.kyiv.putivnyk.notifications.AppNotifications
import javax.inject.Inject
import javax.inject.Singleton

/** Unlocks achievements when statistics change and tells the user about it. */
@Singleton
class AchievementsRepository @Inject constructor(
    private val statsRepository: StatsRepository,
    private val placeRepository: PlaceRepository,
    private val notifications: AppNotifications,
    private val appText: AppText
) {
    private val mutex = Mutex()
    private val _unlocked = MutableSharedFlow<AchievementDefinition>(extraBufferCapacity = 8)

    /** Collected by the app UI; without a collector the user gets a notification instead. */
    val unlocked: SharedFlow<AchievementDefinition> = _unlocked.asSharedFlow()

    suspend fun evaluate(): List<AchievementDefinition> = mutex.withLock {
        val snapshot = statsRepository.snapshot()
        val now = System.currentTimeMillis()
        val fresh = AchievementEngine.newlyUnlocked(snapshot).filter { statsRepository.unlockAchievement(it.id, now) }
        fresh.forEach { definition ->
            if (_unlocked.subscriptionCount.value > 0) {
                _unlocked.emit(definition)
            } else {
                notifications.showAchievement(
                    id = definition.id,
                    emoji = definition.emoji,
                    title = appText.get(definition.titleKey, definition.title),
                    description = appText.get(definition.descriptionKey, definition.description)
                )
            }
        }
        fresh
    }

    /** Re-checks achievements whenever places are marked visited or favourite anywhere in the app. */
    @OptIn(FlowPreview::class)
    fun startObserving(scope: CoroutineScope) {
        scope.launch {
            placeRepository.getAllPlaces()
                .map { places -> places.count { it.isVisited } to places.count { it.isFavorite } }
                .distinctUntilChanged()
                .debounce(1_500)
                .collect { runCatching { evaluate() } }
        }
    }
}
