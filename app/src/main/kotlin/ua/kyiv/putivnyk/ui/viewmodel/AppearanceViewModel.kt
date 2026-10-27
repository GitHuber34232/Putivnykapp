package ua.kyiv.putivnyk.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ua.kyiv.putivnyk.data.repository.UserPreferenceRepository
import javax.inject.Inject

/** Theme choices from Settings; shared by the activity (theme) and the Settings screen. */
@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val userPreferenceRepository: UserPreferenceRepository
) : ViewModel() {

    enum class ThemeMode(val key: String) {
        SYSTEM("system"),
        LIGHT("light"),
        DARK("dark");

        companion object {
            fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
        }
    }

    data class Appearance(
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val dynamicColor: Boolean = false
    )

    /** Null until the stored choice is read, so the splash screen can wait for it. */
    val appearance: StateFlow<Appearance?> = userPreferenceRepository.observeAsMap()
        .map { prefs ->
            Appearance(
                themeMode = ThemeMode.fromKey(prefs[KEY_THEME_MODE]),
                dynamicColor = prefs[KEY_DYNAMIC_COLOR] == "true"
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            userPreferenceRepository.upsert(KEY_THEME_MODE, mode.key)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            userPreferenceRepository.upsert(KEY_DYNAMIC_COLOR, enabled.toString())
        }
    }

    companion object {
        const val KEY_THEME_MODE = "ui.theme.mode"
        const val KEY_DYNAMIC_COLOR = "ui.theme.dynamic"
    }
}
