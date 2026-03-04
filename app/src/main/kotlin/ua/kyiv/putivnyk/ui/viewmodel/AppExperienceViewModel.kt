package ua.kyiv.putivnyk.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ua.kyiv.putivnyk.data.repository.UserPreferenceRepository
import ua.kyiv.putivnyk.data.telemetry.AppTelemetry
import javax.inject.Inject

@HiltViewModel
class AppExperienceViewModel @Inject constructor(
    private val userPreferenceRepository: UserPreferenceRepository,
    private val telemetry: AppTelemetry
) : ViewModel() {

    private val _showOnboarding = MutableStateFlow(false)
    val showOnboarding: StateFlow<Boolean> = _showOnboarding.asStateFlow()

    init {
        viewModelScope.launch {
            val completed = userPreferenceRepository.getString(KEY_ONBOARDING_COMPLETED, "false") == "true"
            _showOnboarding.value = !completed
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            userPreferenceRepository.upsert(KEY_ONBOARDING_COMPLETED, "true")
            _showOnboarding.value = false
            telemetry.trackEvent("onboarding_completed")
        }
    }

    fun resetOnboarding() {
        viewModelScope.launch {
            userPreferenceRepository.upsert(KEY_ONBOARDING_COMPLETED, "false")
            _showOnboarding.value = true
            telemetry.trackEvent("onboarding_reset")
        }
    }

    private companion object {
        const val KEY_ONBOARDING_COMPLETED = "onboarding.completed"
    }
}
