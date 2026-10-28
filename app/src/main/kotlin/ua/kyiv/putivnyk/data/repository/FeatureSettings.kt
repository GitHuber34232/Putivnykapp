package ua.kyiv.putivnyk.data.repository

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ua.kyiv.putivnyk.di.AppCoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/** Switches for the optional features in Settings. */
@Singleton
class FeatureSettings @Inject constructor(
    private val userPreferenceRepository: UserPreferenceRepository,
    appScope: AppCoroutineScope
) {
    data class Values(
        val autoVisited: Boolean = true,
        val backgroundGeofences: Boolean = false,
        val backgroundNavigation: Boolean = true,
        val audioNarration: Boolean = true,
        val arImageRecognition: Boolean = true
    )

    val values: StateFlow<Values> = userPreferenceRepository.observeAsMap()
        .map { parse(it) }
        .distinctUntilChanged()
        .stateIn(appScope, SharingStarted.Eagerly, Values())

    /** Reads the stored values directly, for decisions taken before [values] has loaded. */
    suspend fun current(): Values = parse(userPreferenceRepository.getAllAsMap())

    suspend fun set(key: String, enabled: Boolean) {
        userPreferenceRepository.upsert(key, enabled.toString())
    }

    private fun parse(prefs: Map<String, String>): Values {
        fun flag(key: String, default: Boolean) = prefs[key]?.toBooleanStrictOrNull() ?: default
        val defaults = Values()
        return Values(
            autoVisited = flag(KEY_AUTO_VISITED, defaults.autoVisited),
            backgroundGeofences = flag(KEY_BACKGROUND_GEOFENCES, defaults.backgroundGeofences),
            backgroundNavigation = flag(KEY_BACKGROUND_NAVIGATION, defaults.backgroundNavigation),
            audioNarration = flag(KEY_AUDIO_NARRATION, defaults.audioNarration),
            arImageRecognition = flag(KEY_AR_IMAGE_RECOGNITION, defaults.arImageRecognition)
        )
    }

    companion object {
        const val KEY_AUTO_VISITED = "feature.autoVisited"
        const val KEY_BACKGROUND_GEOFENCES = "feature.backgroundGeofences"
        const val KEY_BACKGROUND_NAVIGATION = "feature.backgroundNavigation"
        const val KEY_AUDIO_NARRATION = "feature.audioNarration"
        const val KEY_AR_IMAGE_RECOGNITION = "feature.arImageRecognition"
    }
}
