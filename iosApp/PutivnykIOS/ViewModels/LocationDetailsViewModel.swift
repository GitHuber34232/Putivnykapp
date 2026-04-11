import SwiftUI
import PutivnykShared

@MainActor
final class LocationDetailsViewModel: ObservableObject {
    @Published var place: Place?
    @Published var isLoaded = false
    @Published var translatedDescription: String?
    @Published var selectedLanguage = "uk"
    @Published var isTranslating = false
    @Published var snackbarMessage: String?

    let availableLanguages: [LanguageInfo] = SupportedLanguages.shared.majorIso639_1

    private let placeId: Int64
    private let services = AppServices.shared

    init(placeId: Int64) {
        self.placeId = placeId
        Task { await load() }
    }

    private func load() async {
        let mode = (try? await services.userPreferenceRepository.getString(key: "ui.lang.mode", defaultValue: "auto")) ?? "auto"
        let rawLang: String
        if mode == "manual" {
            rawLang = (try? await services.userPreferenceRepository.getString(key: "ui.lang.manual", defaultValue: "uk")) ?? "uk"
        } else {
            rawLang = Locale.current.language.languageCode?.identifier ?? "uk"
        }
        selectedLanguage = SupportedLanguages.shared.contains(isoCode: rawLang) ? rawLang : "uk"
        place = try? await services.placeRepository.getPlaceById(id: placeId)
        isLoaded = true
        applyTranslation()
    }

    func setLanguage(_ isoCode: String) {
        guard SupportedLanguages.shared.contains(isoCode: isoCode) else { return }
        selectedLanguage = isoCode
        applyTranslation()
    }

    func toggleFavorite() {
        guard let current = place else { return }
        Task {
            try? await services.placeRepository.toggleFavorite(placeId: current.id)
            place = try? await services.placeRepository.getPlaceById(id: current.id)
            snackbarMessage = place?.isFavorite == true ? "added_to_favorites" : "removed_from_favorites"
        }
    }

    func toggleVisited() {
        guard let current = place else { return }
        Task {
            try? await services.placeRepository.toggleVisited(placeId: current.id)
            place = try? await services.placeRepository.getPlaceById(id: current.id)
            snackbarMessage = place?.isVisited == true ? "marked_visited" : "unmarked_visited"
        }
    }

    func openOnMap() {
        guard let current = place else { return }
        Task {
            try? await services.userPreferenceRepository.upsert(key: "map.focus.placeId", value: "\(current.id)")
        }
    }

    func addToActiveRoute() {
        guard let current = place else { return }
        Task {
            do {
                let routes = try await services.routeRepository.getAllRoutesSnapshot()
                guard let routesList = routes as? [Route], let active = routesList.first else { return }
                let waypoint = RoutePoint(latitude: current.latitude, longitude: current.longitude, name: current.name)
                let updated = RouteMetricsCalculator.shared.withAppendedWaypoint(route: active, waypoint: waypoint)
                try await services.routeRepository.updateRoute(route: updated)
                snackbarMessage = "added_to_route"
            } catch {
                snackbarMessage = "route_add_failed"
            }
        }
    }

    private func applyTranslation() {
        guard let desc = place?.description_, !desc.isEmpty else {
            translatedDescription = place?.description_
            return
        }
        if selectedLanguage == "uk" {
            translatedDescription = desc
            return
        }
        // For non-Ukrainian languages, show original (ML Kit translation not available on iOS)
        translatedDescription = desc
    }
}
