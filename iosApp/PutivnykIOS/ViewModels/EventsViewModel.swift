import SwiftUI
import PutivnykShared

@MainActor
final class EventsViewModel: ObservableObject {
    enum SortMode: String, CaseIterable { case date, title, category }

    @Published var events: [EventItem] = []
    @Published var searchQuery: String = ""
    @Published var selectedCategory: String? = nil
    @Published var sortMode: SortMode = .date
    @Published var isLoading: Bool = false
    @Published var availableCategories: [String] = []

    private let services = AppServices.shared
    private var allEvents: [EventItem] = []

    init() { refresh() }

    func refresh() {
        Task {
            isLoading = true
            let language = await resolveLanguage()
            do {
                let fetched = try await services.eventsRepository.getEvents(language: language)
                allEvents = fetched as? [EventItem] ?? []
            } catch {
                // keep existing events on failure
            }
            availableCategories = Array(Set(allEvents.map { $0.category.trimmingCharacters(in: .whitespaces) }
                .filter { !$0.isEmpty })).sorted()
            applyFilters()
            isLoading = false
        }
    }

    func updateSearchQuery(_ value: String) {
        searchQuery = value
        applyFilters()
    }

    func setCategory(_ cat: String?) {
        selectedCategory = cat
        applyFilters()
    }

    func setSortMode(_ mode: SortMode) {
        sortMode = mode
        applyFilters()
    }

    func selectEvent(_ event: EventItem?) { }

    func openEventOnMap(_ event: EventItem) {
        guard let lat = event.latitude, let lon = event.longitude else { return }
        Task {
            try? await services.userPreferenceRepository.upsert(key: "map.focus.lat", value: "\(lat)")
            try? await services.userPreferenceRepository.upsert(key: "map.focus.lon", value: "\(lon)")
            try? await services.userPreferenceRepository.upsert(key: "map.focus.zoom", value: "15")
        }
    }

    private func applyFilters() {
        var filtered = allEvents
        let query = searchQuery.lowercased().trimmingCharacters(in: .whitespaces)

        if !query.isEmpty {
            filtered = filtered.filter {
                $0.title.lowercased().contains(query) ||
                $0.description_.lowercased().contains(query) ||
                $0.category.lowercased().contains(query) ||
                $0.locationName.lowercased().contains(query)
            }
        }

        if let cat = selectedCategory, !cat.isEmpty {
            filtered = filtered.filter { $0.category.caseInsensitiveCompare(cat) == .orderedSame }
        }

        switch sortMode {
        case .date: filtered.sort { $0.startsAt > $1.startsAt }
        case .title: filtered.sort { $0.title.lowercased() < $1.title.lowercased() }
        case .category: filtered.sort { $0.category.lowercased() < $1.category.lowercased() }
        }

        events = filtered
    }

    private func resolveLanguage() async -> String {
        let mode = (try? await services.userPreferenceRepository.getString(key: "ui.lang.mode", defaultValue: "auto")) ?? "auto"
        if mode == "manual" {
            return (try? await services.userPreferenceRepository.getString(key: "ui.lang.manual", defaultValue: "uk")) ?? "uk"
        }
        return Locale.current.language.languageCode?.identifier ?? "uk"
    }
}
