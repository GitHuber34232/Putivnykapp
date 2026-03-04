package ua.kyiv.putivnyk.ui.viewmodel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ua.kyiv.putivnyk.data.repository.UserPreferenceRepository
import ua.kyiv.putivnyk.data.telemetry.AppTelemetry
import ua.kyiv.putivnyk.testing.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class AppExperienceViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userPreferenceRepository: UserPreferenceRepository = mock()
    private val telemetry: AppTelemetry = mock()

    @Test
    fun init_shows_onboarding_when_not_completed() = runTest {
        whenever(userPreferenceRepository.getString("onboarding.completed", "false")).thenReturn("false")

        val viewModel = AppExperienceViewModel(userPreferenceRepository, telemetry)
        advanceUntilIdle()

        assertTrue(viewModel.showOnboarding.value)
    }

    @Test
    fun completeOnboarding_persists_and_hides_dialog() = runTest {
        whenever(userPreferenceRepository.getString("onboarding.completed", "false")).thenReturn("false")
        val viewModel = AppExperienceViewModel(userPreferenceRepository, telemetry)
        advanceUntilIdle()

        viewModel.completeOnboarding()
        advanceUntilIdle()

        verify(userPreferenceRepository).upsert(eq("onboarding.completed"), eq("true"))
        verify(telemetry).trackEvent(eq("onboarding_completed"), eq(emptyMap()))
        assertFalse(viewModel.showOnboarding.value)
    }

    @Test
    fun resetOnboarding_persists_and_shows_dialog() = runTest {
        whenever(userPreferenceRepository.getString("onboarding.completed", "false")).thenReturn("true")
        val viewModel = AppExperienceViewModel(userPreferenceRepository, telemetry)
        advanceUntilIdle()

        viewModel.resetOnboarding()
        advanceUntilIdle()

        verify(userPreferenceRepository).upsert(eq("onboarding.completed"), eq("false"))
        verify(telemetry).trackEvent(eq("onboarding_reset"), eq(emptyMap()))
        assertTrue(viewModel.showOnboarding.value)
    }
}
