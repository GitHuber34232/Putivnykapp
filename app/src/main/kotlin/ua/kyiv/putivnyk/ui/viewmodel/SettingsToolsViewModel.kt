package ua.kyiv.putivnyk.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ua.kyiv.putivnyk.data.repository.RouteRepository
import ua.kyiv.putivnyk.data.telemetry.AppTelemetry
import javax.inject.Inject

@HiltViewModel
class SettingsToolsViewModel @Inject constructor(
    private val routeRepository: RouteRepository,
    private val telemetry: AppTelemetry
) : ViewModel() {

    private val maxImportBytes = 2 * 1024 * 1024

    private val _transferState = MutableStateFlow("")
    val transferState: StateFlow<String> = _transferState.asStateFlow()

    fun exportRoutes(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val json = routeRepository.exportRoutesJson()
            _transferState.value = "Маршрути підготовлено до експорту"
            telemetry.trackEvent("routes_export_prepared", mapOf("bytes" to json.length.toString()))
            onReady(json)
        }
    }

    fun importRoutes(json: String) {
        viewModelScope.launch {
            if (json.toByteArray().size > maxImportBytes) {
                _transferState.value = "Файл завеликий для імпорту (макс ${maxImportBytes / (1024 * 1024)} MB)"
                telemetry.trackEvent("routes_import_rejected_too_large")
                return@launch
            }
            val count = runCatching { routeRepository.importRoutesJson(json) }
                .onFailure { telemetry.trackError("routes_import_failed", it) }
                .getOrDefault(0)
            _transferState.value = "Імпортовано маршрутів: $count"
            telemetry.trackEvent("routes_imported", mapOf("count" to count.toString()))
        }
    }

    fun notifyImportTooLarge(maxBytes: Int = maxImportBytes) {
        _transferState.value = "Файл завеликий для імпорту (макс ${maxBytes / (1024 * 1024)} MB)"
        telemetry.trackEvent("routes_import_rejected_too_large")
    }
}
