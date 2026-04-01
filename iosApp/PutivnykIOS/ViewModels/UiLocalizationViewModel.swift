import SwiftUI
import PutivnykShared

@MainActor
final class UiLocalizationViewModel: ObservableObject {
    @Published var effectiveLanguage: String = "uk"
    @Published var isAutoMode: Bool = true
    @Published var manualLanguage: String = "uk"
    @Published var uiTexts: [String: String] = Self.defaultsUk

    let languages: [SupportedLanguages.LanguageEntry] = {
        let list = SupportedLanguages.shared.majorIso639_1
        var result = [SupportedLanguages.LanguageEntry]()
        for i in 0..<list.count { result.append(list[i]) }
        return result
    }()

    private let services = AppServices.shared

    init() {
        Task { await resolveLanguage() }
    }

    func setAutoMode(_ enabled: Bool) {
        isAutoMode = enabled
        Task {
            try? await services.userPreferenceRepository.upsert(key: "ui.lang.mode", value_: enabled ? "auto" : "manual")
            await resolveLanguage()
        }
    }

    func setManualLanguage(_ iso: String) {
        manualLanguage = iso
        isAutoMode = false
        Task {
            try? await services.userPreferenceRepository.upsert(key: "ui.lang.manual", value_: iso)
            try? await services.userPreferenceRepository.upsert(key: "ui.lang.mode", value_: "manual")
            await resolveLanguage()
        }
    }

    func refresh() {
        Task { await resolveLanguage() }
    }

    private func resolveLanguage() async {
        let mode = (try? await services.userPreferenceRepository.getString(key: "ui.lang.mode", defaultValue: "auto")) ?? "auto"
        let manual = (try? await services.userPreferenceRepository.getString(key: "ui.lang.manual", defaultValue: "uk")) ?? "uk"

        isAutoMode = mode != "manual"
        manualLanguage = manual

        let rawLang: String
        if isAutoMode {
            rawLang = Locale.current.language.languageCode?.identifier ?? "uk"
        } else {
            rawLang = manual.isEmpty ? "uk" : manual
        }

        let language = SupportedLanguages.shared.contains(isoCode: rawLang.lowercased()) ? rawLang.lowercased() : "uk"
        effectiveLanguage = language

        if language == "uk" {
            uiTexts = Self.defaultsUk
            return
        }

        // Load from bundled JSON assets
        let fromAssets = services.uiTranslationsProvider.load(language: language)
        if let dict = fromAssets as? [String: String], !dict.isEmpty {
            var merged = Self.defaultsUk
            for (key, value) in dict { merged[key] = value }
            uiTexts = merged
        } else {
            // Fallback to English
            let english = services.uiTranslationsProvider.load(language: "en")
            if let enDict = english as? [String: String], !enDict.isEmpty {
                var merged = Self.defaultsUk
                for (key, value) in enDict { merged[key] = value }
                uiTexts = merged
            }
        }
    }

    // MARK: - Ukrainian defaults (mirrors UiStringDefaults in Android)
    static let defaultsUk: [String: String] = [
        "nav.home": "Головна",
        "nav.map": "Карта",
        "nav.routes": "Маршрути",
        "nav.events": "Події",
        "nav.settings": "Налаштування",
        "home.welcome": "Вітаємо!",
        "home.recommended": "Рекомендовано для вас",
        "home.loading_reco": "Ще формуємо персональні рекомендації",
        "home.add_favorite": "Додати в улюблені",
        "home.choose_place": "Оберіть місце",
        "home.save": "Зберегти",
        "home.cancel": "Скасувати",
        "home.my_places": "Мої місця",
        "home.add_place": "Додати місце",
        "home.load_more": "Завантажити ще",
        "map.search": "Пошук на карті",
        "map.all": "Всі",
        "map.create_route": "Створити маршрут",
        "map.center_kyiv": "Центрувати на Києві",
        "map.add_to_route": "Додати до маршруту",
        "map.add": "Додати",
        "events.title": "Події та афіша",
        "events.search": "Пошук подій",
        "events.refresh": "Оновити",
        "events.loading": "Завантаження подій…",
        "events.empty": "Подій не знайдено",
        "events.on_map": "На карті",
        "events.to_route": "В маршрут",
        "routes.title": "Маршрути",
        "routes.search": "Пошук маршрутів",
        "routes.create": "Створити маршрут",
        "routes.no_saved": "Немає збережених маршрутів",
        "routes.distance": "Відстань",
        "routes.km": "км",
        "routes.duration": "Тривалість",
        "routes.min": "хв",
        "routes.points": "Точок",
        "places.title": "Цікаві місця",
        "places.search": "Пошук місць...",
        "places.not_found": "Місця не знайдено",
        "favorites.title": "Улюблені місця",
        "favorites.empty": "Немає улюблених місць",
        "details.title": "Деталі локації",
        "details.not_found": "Локацію не знайдено",
        "details.mark_visited": "Позначити відвіданим",
        "details.open_map": "Відкрити на карті",
        "details.description": "Опис",
        "details.no_description": "Опис відсутній",
        "details.tags": "Теги",
        "details.coordinates": "Координати",
        "settings.title": "Налаштування",
        "settings.auto": "Авто (системна мова)",
        "settings.manual": "Ручний вибір",
        "settings.interface_lang": "Мова інтерфейсу",
        "settings.version": "Версія",
        "settings.export_routes": "Експорт маршрутів (JSON)",
        "settings.import_routes": "Імпорт маршрутів (JSON)",
        "settings.send_feedback": "Надіслати відгук",
        "settings.reopen_onboarding": "Показати onboarding знову",
        "category.park": "Парк",
        "category.museum": "Музей",
        "category.theater": "Театр",
        "category.restaurant": "Ресторан",
        "category.cathedral": "Собор",
        "category.monastery": "Монастир",
        "category.architecture_monument": "Пам'ятка архітектури",
        "category.square": "Площа",
        "category.street": "Вулиця",
        "category.district": "Район",
        "category.stadium": "Стадіон",
        "category.embankment": "Набережна",
        "category.famous_place": "Відомі місця",
        "category.toilet": "Туалети",
        "category.other": "Інше",
        "sort.popularity": "Популярність",
        "sort.rating": "Рейтинг",
        "sort.distance": "Відстань",
        "onboarding.title": "Ласкаво просимо до Putivnyk",
        "onboarding.continue": "Продовжити",
        "common.close": "Закрити",
        "common.delete": "Видалити",
        "common.back": "Назад",
    ]
}
