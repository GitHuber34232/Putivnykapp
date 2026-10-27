package ua.kyiv.putivnyk.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ua.kyiv.putivnyk.data.repository.LocalizationRepository
import ua.kyiv.putivnyk.data.repository.UserPreferenceRepository
import ua.kyiv.putivnyk.i18n.AssetUiTranslations
import ua.kyiv.putivnyk.i18n.OnDeviceTranslationService
import ua.kyiv.putivnyk.i18n.SupportedLanguages
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

@HiltViewModel
class UiLocalizationViewModel @Inject constructor(
    private val localizationRepository: LocalizationRepository,
    private val userPreferenceRepository: UserPreferenceRepository,
    private val translationService: OnDeviceTranslationService,
    private val assetUiTranslations: AssetUiTranslations
) : ViewModel() {

    private val dynamicMemoryCache = ConcurrentHashMap<String, String>()
    // Keyed by "<language>::<source text>" for synchronous lookups from composition.
    private val dynamicTextCache = ConcurrentHashMap<String, String>()
    private val uiTextsMemoryCache = ConcurrentHashMap<String, Map<String, String>>()
    private var resolveLanguageJob: Job? = null
    private var uiTranslationJob: Job? = null
    private var downloadModelsJob: Job? = null
    private val languageRequestVersion = AtomicLong(0)

    private val _isAutoMode = MutableStateFlow(true)
    val isAutoMode: StateFlow<Boolean> = _isAutoMode.asStateFlow()

    private val _manualLanguage = MutableStateFlow("uk")
    val manualLanguage: StateFlow<String> = _manualLanguage.asStateFlow()

    private val _effectiveLanguage = MutableStateFlow("uk")
    val effectiveLanguage: StateFlow<String> = _effectiveLanguage.asStateFlow()

    private val _uiTexts = MutableStateFlow(UiStringDefaults.defaultsUk)
    val uiTexts: StateFlow<Map<String, String>> = _uiTexts.asStateFlow()

    private val _downloadState = MutableStateFlow("")
    val downloadState: StateFlow<String> = _downloadState.asStateFlow()

    private val _downloadProgressPercent = MutableStateFlow(0)
    val downloadProgressPercent: StateFlow<Int> = _downloadProgressPercent.asStateFlow()

    private val _isDownloadingModels = MutableStateFlow(false)
    val isDownloadingModels: StateFlow<Boolean> = _isDownloadingModels.asStateFlow()

    val languages = SupportedLanguages.majorIso639_1

    init {
        resolveLanguageJob = viewModelScope.launch {
            val mode = userPreferenceRepository.getString("ui.lang.mode", "auto")
            val manual = userPreferenceRepository.getString("ui.lang.manual", "uk").ifBlank { "uk" }
            _isAutoMode.value = mode != "manual"
            _manualLanguage.value = manual
            resolveEffectiveLanguageAndLoad()
        }
        // Models are fetched on demand for the language in use; downloading every language
        // (~30 MB each) is only done when the user explicitly asks for it in Settings.
    }

    fun setAutoMode(enabled: Boolean) {
        _isAutoMode.value = enabled
        resolveLanguageJob?.cancel()
        resolveLanguageJob = viewModelScope.launch {
            userPreferenceRepository.upsert("ui.lang.mode", if (enabled) "auto" else "manual")
            resolveEffectiveLanguageAndLoad()
        }
    }

    fun setManualLanguage(isoCode: String) {
        _manualLanguage.value = isoCode
        resolveLanguageJob?.cancel()
        resolveLanguageJob = viewModelScope.launch {
            userPreferenceRepository.upsert("ui.lang.manual", isoCode)
            userPreferenceRepository.upsert("ui.lang.mode", "manual")
            _isAutoMode.value = false
            resolveEffectiveLanguageAndLoad()
        }
    }

    fun refresh() {
        resolveLanguageJob?.cancel()
        resolveLanguageJob = viewModelScope.launch {
            resolveEffectiveLanguageAndLoad(forceTranslate = true)
        }
    }

    fun downloadModelForCurrentLanguage() {
        if (_isDownloadingModels.value) return
        val lang = _effectiveLanguage.value.lowercase()
        if (lang == "uk") {
            _downloadProgressPercent.value = 100
            _downloadState.value = "Українська мова не потребує моделі перекладу"
            return
        }
        downloadModelsJob = viewModelScope.launch {
            _isDownloadingModels.value = true
            _downloadState.value = "Завантаження моделі (${lang.uppercase()})..."
            _downloadProgressPercent.value = 0
            // Place names and descriptions are Ukrainian, so the uk -> lang pair covers the app content.
            val success = translationService.isSupportedByMlKit(lang) &&
                translationService.downloadModel(sourceLanguageIso = "uk", targetLanguageIso = lang)
            _downloadProgressPercent.value = 100
            _downloadState.value = if (success) {
                "Модель готова (${lang.uppercase()})"
            } else {
                "Не вдалося завантажити модель (${lang.uppercase()})"
            }
            _isDownloadingModels.value = false
            downloadModelsJob = null
            if (success) {
                resolveEffectiveLanguageAndLoad()
            }
        }
    }

    fun downloadModelsForAllLanguages() {
        if (_isDownloadingModels.value) return

        val targets = try {
            languages
                .map { it.isoCode.lowercase() }
                .filter { translationService.isSupportedByMlKit(it) }
                .distinct()
        } catch (e: Exception) {
            _downloadState.value = "MLKit недоступний: ${e.message}"
            return
        }

        // ML Kit stores one model per language (English is built in), so a single uk -> target
        // pair per language downloads everything the app needs.
        val pairs = targets
            .filter { it != "uk" }
            .map { target -> "uk" to target }

        if (pairs.isEmpty()) {
            _downloadProgressPercent.value = 100
            _downloadState.value = "Немає доступних моделей для завантаження"
            return
        }

        downloadModelsJob = viewModelScope.launch {
            _isDownloadingModels.value = true
            _downloadProgressPercent.value = 0
            var successCount = 0
            var completedSoFar = 0

            val batches = pairs.chunked(DOWNLOAD_BATCH_SIZE)
            Log.d(TAG, "Downloading ${pairs.size} models in ${batches.size} batches of $DOWNLOAD_BATCH_SIZE")

            for ((batchIndex, batch) in batches.withIndex()) {
                val batchLabel = batch.joinToString { (s, t) -> "${s.uppercase()}→${t.uppercase()}" }
                Log.d(TAG, "Batch ${batchIndex + 1}/${batches.size}: $batchLabel")

                _downloadState.value = "Завантаження моделей: батч ${batchIndex + 1}/${batches.size} (${completedSoFar}/${pairs.size})"

                val deferreds = batch.map { (source, target) ->
                    async {
                        translationService.downloadModel(
                            sourceLanguageIso = source,
                            targetLanguageIso = target
                        )
                    }
                }

                val results = deferreds.awaitAll()
                val batchSuccess = results.count { it }
                successCount += batchSuccess
                completedSoFar += batch.size

                val progress = ((completedSoFar * 100f) / pairs.size).toInt().coerceIn(0, 99)
                _downloadProgressPercent.value = progress
                Log.d(TAG, "Batch ${batchIndex + 1} done: $batchSuccess/${batch.size} ok, total $completedSoFar/${pairs.size}")
            }

            _downloadProgressPercent.value = 100
            _downloadState.value = "Моделі завантажено: $successCount/${pairs.size}"
            _isDownloadingModels.value = false
            downloadModelsJob = null

            resolveEffectiveLanguageAndLoad()
        }
    }

    companion object {
        private const val TAG = "UiLocalizationVM"
        private const val DOWNLOAD_BATCH_SIZE = 4
        private const val MAX_CONSECUTIVE_TRANSLATION_FAILURES = 3
    }

    fun cancelDownloadModels() {
        downloadModelsJob?.cancel()
        downloadModelsJob = null
        _isDownloadingModels.value = false
        _downloadProgressPercent.value = 0
        _downloadState.value = "Завантаження скасовано"
    }

    fun peekDynamicTranslation(text: String): String? =
        dynamicTextCache["${_effectiveLanguage.value}::${text.trim()}"]

    suspend fun translateDynamicText(text: String): String {
        val sourceText = text.trim()
        if (sourceText.isBlank()) return text

        val targetLanguage = _effectiveLanguage.value
        if (targetLanguage == "uk") return text
        if (!translationService.isSupportedByMlKit(targetLanguage)) return text

        val key = dynamicTranslationKey(sourceText)
        val memoryKey = "$targetLanguage::$key"
        val textKey = "$targetLanguage::$sourceText"

        dynamicMemoryCache[memoryKey]?.let { return it }

        localizationRepository.getValue(key = key, locale = targetLanguage)
            ?.takeIf { it.isNotBlank() }
            ?.let { cached ->
                dynamicMemoryCache[memoryKey] = cached
                dynamicTextCache[textKey] = cached
                return cached
            }

        val translated = translateWithBestSource(
            text = sourceText,
            targetLanguage = targetLanguage
        ) ?: return text

        localizationRepository.upsertValue(
            key = key,
            locale = targetLanguage,
            value = translated,
            source = "mlkit-dynamic"
        )
        dynamicMemoryCache[memoryKey] = translated
        dynamicTextCache[textKey] = translated
        return translated
    }

    private suspend fun resolveEffectiveLanguageAndLoad(forceTranslate: Boolean = false) {
        val requestVersion = languageRequestVersion.incrementAndGet()

        val rawLang = if (_isAutoMode.value) {
            Locale.getDefault().language.ifBlank { "uk" }
        } else {
            _manualLanguage.value.ifBlank { "uk" }
        }.lowercase()

        val language = if (SupportedLanguages.contains(rawLang)) rawLang else "uk"

        _effectiveLanguage.value = language

        val englishSeed = assetUiTranslations.load("en")
        // English texts are the translation source; keys that exist only in the English asset
        // are included too, so every string the UI uses gets translated.
        val fallbackBase = UiStringDefaults.defaultsUk.mapValues { (key, fallbackUk) ->
            englishSeed[key] ?: fallbackUk
        } + englishSeed.filterKeys { it !in UiStringDefaults.defaultsUk }

        if (language == "uk") {
            uiTranslationJob?.cancel()
            _uiTexts.value = UiStringDefaults.defaultsUk
            uiTextsMemoryCache[language] = UiStringDefaults.defaultsUk
            return
        }

        if (language == "en") {
            uiTranslationJob?.cancel()
            _uiTexts.value = fallbackBase
            uiTextsMemoryCache[language] = fallbackBase
            localizationRepository.upsertAll(locale = language, values = englishSeed, source = "asset")
            return
        }

        if (forceTranslate) {
            localizationRepository.clearLocale(language)
            dynamicMemoryCache.keys.removeIf { it.startsWith("$language::") }
            dynamicTextCache.keys.removeIf { it.startsWith("$language::") }
            uiTextsMemoryCache.remove(language)
        }

        uiTextsMemoryCache[language]?.let { cachedMemory ->
            _uiTexts.value = cachedMemory
        }

        val fromAssets = assetUiTranslations.load(language)
        if (fromAssets.isNotEmpty()) {
            val mergedFromAssets = fallbackBase.mapValues { (key, fallback) ->
                fromAssets[key] ?: fallback
            }
            _uiTexts.value = mergedFromAssets
            localizationRepository.upsertAll(locale = language, values = fromAssets, source = "asset")
        }

        val cached = localizationRepository.getByLocale(language).associate { it.key to it.value }
        val merged = fallbackBase.mapValues { (key, fallback) ->
            cached[key] ?: fallback
        }
        _uiTexts.value = merged
        uiTextsMemoryCache[language] = merged

        if (!translationService.isSupportedByMlKit(language)) return

        val missing = fallbackBase
            .filterKeys { key -> forceTranslate || cached[key].isNullOrBlank() }

        if (missing.isEmpty()) return

        uiTranslationJob?.cancel()
        uiTranslationJob = viewModelScope.launch {
            var consecutiveFailures = 0
            missing.forEach { (key, fallback) ->
                if (languageRequestVersion.get() != requestVersion || _effectiveLanguage.value != language) {
                    return@launch
                }

                val sourceText = englishSeed[key] ?: fallbackBase[key] ?: fallback
                val translated = translateWithBestSource(
                    text = sourceText,
                    targetLanguage = language
                )

                if (languageRequestVersion.get() != requestVersion || _effectiveLanguage.value != language) {
                    return@launch
                }
                // Not cached when the model is unavailable (e.g. offline), so it is retried later.
                if (translated == null) {
                    consecutiveFailures++
                    if (consecutiveFailures >= MAX_CONSECUTIVE_TRANSLATION_FAILURES) return@launch
                    return@forEach
                }
                consecutiveFailures = 0

                localizationRepository.upsertValue(
                    key = key,
                    locale = language,
                    value = translated,
                    source = "mlkit-ui"
                )

                _uiTexts.value = _uiTexts.value.toMutableMap().apply {
                    this[key] = translated
                }.also { updated ->
                    uiTextsMemoryCache[language] = updated
                }
            }
        }
    }

    /** Returns the translation, or null when no model could translate the text. */
    private suspend fun translateWithBestSource(
        text: String,
        targetLanguage: String
    ): String? {
        val sourceCandidates = if (text.any { it in '\u0400'..'\u04FF' }) {
            listOf("uk", "en")
        } else {
            listOf("en", "uk")
        }

        sourceCandidates.forEach { source ->
            if (!translationService.isSupportedByMlKit(source)) return@forEach
            val translated = runCatching {
                translationService.translateText(
                    text = text,
                    sourceLanguageIso = source,
                    targetLanguageIso = targetLanguage
                )
            }.getOrNull()
            if (!translated.isNullOrBlank() && translated != text) {
                return translated
            }
        }

        return null
    }
}

private fun dynamicTranslationKey(text: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
    val hex = digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    return "dyn.$hex"
}

private object UiStringDefaults {
    val defaultsUk: Map<String, String> = mapOf(
        "nav.home" to "Головна",
        "nav.map" to "Карта",
        "nav.routes" to "Маршрути",
        "nav.events" to "Події",
        "nav.settings" to "Налаштування",
        "home.welcome" to "Вітаємо!",
        "home.recommended" to "Рекомендовано для вас",
        "home.loading_reco" to "Ще формуємо персональні рекомендації",
        "home.add_favorite" to "Додати в улюблені",
        "home.choose_place" to "Оберіть місце",
        "home.favorite_category" to "Категорія улюбленого (власна):",
        "home.favorite_category_hint" to "Напр. Романтика / Їжа / Історія",
        "home.tap_to_open_map" to "Натисніть, щоб відкрити на карті",
        "home.save" to "Зберегти",
        "home.cancel" to "Скасувати",
        "map.search" to "Пошук на карті",
        "map.all" to "Всі",
        "map.create_route" to "Створити маршрут",
        "map.route_mode_on" to "Режим маршруту: ON",
        "map.save_position" to "Зберегти позицію",
        "map.clear" to "Очистити",
        "map.view_hint" to "Натисніть для перегляду",
        "map.route_points" to "Точки маршруту",
        "map.route_name" to "Назва маршруту",
        "map.route_name_hint" to "Наприклад: Вечірній центр",
        "map.details_hint" to "Натисніть на картку нижче для повної інформації",
        "map.center_kyiv" to "Центрувати на Києві",
        "map.add_to_route" to "Додати до маршруту",
        "map.add_to_route_q" to "Додати",
        "map.add_to_route_tail" to "до активного маршруту?",
        "map.add" to "Додати",
        "map.delete_bookmark" to "Видалити закладку",
        "events.title" to "Події та афіша",
        "events.search" to "Пошук подій",
        "events.refresh" to "Оновити",
        "events.loading" to "Завантаження подій…",
        "events.empty" to "Події за обраними фільтрами відсутні",
        "events.sync_idle" to "Синк: очікування",
        "events.sync_running" to "Синк: виконується",
        "events.sync_success" to "Синк: успішно",
        "events.sync_error" to "Синк: помилка",
        "events.sort_date" to "За датою",
        "events.sort_title" to "За назвою",
        "events.sort_category" to "За категорією",
        "events.place" to "Місце",
        "events.no_location" to "Локація не вказана",
        "events.starts" to "Початок",
        "events.ends" to "Завершення",
        "events.time_tbd" to "Час уточнюється",
        "events.price" to "Ціна",
        "events.item" to "Подія",
        "events.location_tbd" to "Локація уточнюється",
        "events.on_map" to "На карті",
        "events.to_route" to "В маршрут",
        "events.retry_sync" to "Спробувати синк",
        "events.freshness_stale" to "Офлайн-дані подій застаріли",
        "routes.title" to "Маршрути",
        "routes.search" to "Пошук маршрутів",
        "routes.create" to "Створити маршрут",
        "routes.only_favorites" to "Тільки улюблені",
        "routes.no_saved" to "Немає збережених маршрутів",
        "routes.create_hint" to "Створіть маршрут через вибір точок на карті",
        "routes.no_description" to "Без опису",
        "routes.distance" to "Відстань",
        "routes.km" to "км",
        "routes.duration" to "Тривалість",
        "routes.min" to "хв",
        "routes.points" to "Точок",
        "routes.waypoints" to "Проміжні точки:",
        "routes.no_name" to "Без назви",
        "routes.point_name" to "Назва точки",
        "routes.latitude" to "Широта",
        "routes.longitude" to "Довгота",
        "routes.add_point" to "+ Точка",
        "routes.remove_last" to "-1 точка",
        "routes.clear_points" to "Очистити точки",
        "settings.title" to "Налаштування",
        "settings.auto" to "Авто",
        "settings.manual" to "Вручну",
        "settings.interface_lang" to "Мова інтерфейсу",
        "settings.download_ml" to "Завантажити модель",
        "settings.version" to "Версія",
        "settings.tech" to "Технології",
        "settings.features" to "Функціонал",
        "settings.features_list" to "Карта, рекомендації, події, маршрути, AR-камера, офлайн-режим",
        "settings.data_tools" to "Маршрути та дані",
        "settings.export_routes" to "Експорт",
        "settings.import_routes" to "Імпорт",
        "settings.help_feedback" to "Підтримка та зворотний зв'язок",
        "settings.send_feedback" to "Відгук",
        "settings.reopen_onboarding" to "Знайомство",
        "places.title" to "Цікаві місця",
        "places.search" to "Пошук місць...",
        "places.not_found" to "Місця не знайдено",
        "favorites.title" to "Улюблені місця",
        "favorites.favorite" to "Улюблене",
        "favorites.in_list" to "В обраному",
        "favorites.add" to "Додати в обране",
        "favorites.empty" to "Немає улюблених місць",
        "favorites.empty_hint" to "Додайте місця в улюблені зі списку або карти",
        "details.title" to "Деталі локації",
        "details.not_found" to "Локацію не знайдено",
        "details.photo" to "Фото",
        "details.mark_visited" to "Позначити відвіданим",
        "details.open_map" to "На карті",
        "details.add_active_route" to "До маршруту",
        "details.translate_description" to "Переклад опису",
        "details.description" to "Опис",
        "details.no_description" to "Опис відсутній",
        "details.tags" to "Теги",
        "details.coordinates" to "Координати",
        "category.park" to "Парк",
        "category.museum" to "Музей",
        "category.theater" to "Театр",
        "category.restaurant" to "Ресторан",
        "category.cathedral" to "Собор",
        "category.monastery" to "Монастир",
        "category.architecture_monument" to "Пам'ятка архітектури",
        "category.square" to "Площа",
        "category.street" to "Вулиця",
        "category.district" to "Район",
        "category.stadium" to "Стадіон",
        "category.embankment" to "Набережна",
        "category.famous_place" to "Відомі місця",
        "category.toilet" to "Туалети",
        "category.other" to "Інше",
        "river.left" to "Лівий берег",
        "river.right" to "Правий берег",
        "river.both" to "Обидва береги",
        "river.unknown" to "Невідомо",
        "sort.popularity" to "Популярність",
        "sort.rating" to "Рейтинг",
        "sort.distance" to "Відстань",
        "onboarding.title" to "Ласкаво просимо до Путівника",
        "onboarding.continue" to "Почати",
        "common.close" to "Закрити",
        "common.delete" to "Видалити",
        "common.back" to "Назад"
    )
}
