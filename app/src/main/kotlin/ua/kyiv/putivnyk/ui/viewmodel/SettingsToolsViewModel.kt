package ua.kyiv.putivnyk.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.kyiv.putivnyk.i18n.OnDeviceTranslationService
import ua.kyiv.putivnyk.data.repository.RouteRepository
import ua.kyiv.putivnyk.data.telemetry.AppTelemetry
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsToolsViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val routeRepository: RouteRepository,
    private val translationService: OnDeviceTranslationService,
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

    fun clearMlKitModels() {
        viewModelScope.launch {
            val deletedCount = runCatching { translationService.deleteAllDownloadedModels() }
                .onFailure { telemetry.trackError("mlkit_models_clear_failed", it) }
                .getOrDefault(-1)

            val filesCleanedBytes = withContext(Dispatchers.IO) {
                cleanMlKitFilesOnDisk()
            }

            if (deletedCount >= 0) {
                val sizeStr = if (filesCleanedBytes > 0) {
                    " (${filesCleanedBytes / (1024 * 1024)} МБ)"
                } else ""
                _transferState.value = if (deletedCount == 0 && filesCleanedBytes == 0L) {
                    "ML Kit моделі не знайдено"
                } else {
                    "Видалено ML Kit моделей: $deletedCount$sizeStr"
                }
                telemetry.trackEvent("mlkit_models_cleared", mapOf(
                    "count" to deletedCount.toString(),
                    "bytes_cleaned" to filesCleanedBytes.toString()
                ))
            } else {
                _transferState.value = "Не вдалося видалити моделі ML Kit"
            }
        }
    }

    private fun cleanMlKitFilesOnDisk(): Long {
        var totalCleaned = 0L
        val mlKitDirs = listOf(
            File(appContext.filesDir, "com.google.mlkit.translate.models"),
            File(appContext.noBackupFilesDir, "com.google.mlkit.translate.models"),
            File(appContext.cacheDir, "com.google.mlkit.translate.models"),
        )
        for (dir in mlKitDirs) {
            if (dir.exists() && dir.isDirectory) {
                totalCleaned += dir.walkBottomUp().filter { it.isFile }.sumOf { file ->
                    val size = file.length()
                    file.delete()
                    size
                }
                dir.deleteRecursively()
            }
        }
        return totalCleaned
    }
}
