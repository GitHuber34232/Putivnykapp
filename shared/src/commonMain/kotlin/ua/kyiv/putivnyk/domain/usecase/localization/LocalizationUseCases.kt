package ua.kyiv.putivnyk.domain.usecase.localization

import kotlinx.coroutines.flow.Flow
import ua.kyiv.putivnyk.data.repository.LocalizationRepository

class ObserveLocalizedStringsUseCase(
    private val repository: LocalizationRepository
) {
    operator fun invoke(locale: String): Flow<Map<String, String>> =
        repository.observeByLocale(locale)
}

class UpsertLocalizedStringsUseCase(
    private val repository: LocalizationRepository
) {
    suspend operator fun invoke(
        locale: String,
        values: Map<String, String>,
        source: String = "remote"
    ) = repository.upsertAll(locale = locale, values = values, source = source)
}