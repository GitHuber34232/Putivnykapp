package ua.kyiv.putivnyk.data.repository

import ua.kyiv.putivnyk.data.model.EventItem

interface EventsRepository {
    suspend fun getEvents(language: String = EventLanguageSupport.defaultLanguage): List<EventItem>
}

object EventLanguageSupport {
    const val defaultLanguage = "uk"

    private val supportedBackendLanguages = setOf(defaultLanguage, "en")

    fun normalizeBackendLanguage(language: String): String {
        val normalized = language.lowercase()
        return if (normalized in supportedBackendLanguages) normalized else defaultLanguage
    }
}