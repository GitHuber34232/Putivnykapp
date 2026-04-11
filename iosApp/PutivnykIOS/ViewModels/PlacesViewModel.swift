import SwiftUI
import PutivnykShared

@MainActor
final class PlacesViewModel: ObservableObject {
    @Published var places: [Place] = []
    @Published private(set) var favoritePlaces: [Place] = []
    @Published var searchQuery: String = ""
    @Published var selectedCategory: PlaceCategory? = nil
    @Published var showOnlyFavorites: Bool = false
    @Published var sortMode: PlaceSortMode = .popularity
    @Published var isLoaded: Bool = false

    private let services = AppServices.shared
    private var allPlaces: [Place] = []

    init() { Task { await loadPlaces() } }

    func loadPlaces() async {
        let snapshot = try? await services.placeRepository.getAllPlacesSnapshot()
        allPlaces = snapshot as? [Place] ?? []
        favoritePlaces = allPlaces.filter { $0.isFavorite }
        applyFilters()
        isLoaded = true
    }

    func updateSearchQuery(_ query: String) {
        searchQuery = query
        applyFilters()
    }

    func selectCategory(_ category: PlaceCategory?) {
        selectedCategory = category
        applyFilters()
    }

    func toggleShowOnlyFavorites() {
        showOnlyFavorites.toggle()
        applyFilters()
    }

    func setSortMode(_ mode: PlaceSortMode) {
        sortMode = mode
        applyFilters()
    }

    func toggleFavorite(placeId: Int64) {
        Task {
            try? await services.placeRepository.toggleFavorite(placeId: placeId)
            await loadPlaces()
        }
    }

    func toggleFavorite(_ placeId: Int64) {
        toggleFavorite(placeId: placeId)
    }

    func toggleVisited(placeId: Int64) {
        Task {
            try? await services.placeRepository.toggleVisited(placeId: placeId)
            await loadPlaces()
        }
    }

    func toggleVisited(_ placeId: Int64) {
        toggleVisited(placeId: placeId)
    }

    func clearFilters() {
        searchQuery = ""
        selectedCategory = nil
        showOnlyFavorites = false
        sortMode = .popularity
        applyFilters()
    }

    private func applyFilters() {
        var filtered = allPlaces
        let query = searchQuery.trimmingCharacters(in: .whitespaces).lowercased()

        let isToiletRequested = query.contains("туалет") || query.contains("toilet") || query.contains("wc")
        if !isToiletRequested {
            filtered = filtered.filter { $0.category != .toilet }
        }

        if showOnlyFavorites {
            filtered = filtered.filter { $0.isFavorite }
        }

        if let cat = selectedCategory {
            filtered = filtered.filter { $0.category == cat }
        }

        if !query.isEmpty {
            filtered = filtered.filter { place in
                place.name.lowercased().contains(query) ||
                (place.nameEn?.lowercased().contains(query) ?? false) ||
                (place.description_?.lowercased().contains(query) ?? false) ||
                place.category.displayName.lowercased().contains(query)
            }
        }

        switch sortMode {
        case .popularity:
            filtered.sort { ($0.popularity) > ($1.popularity) }
        case .rating:
            filtered.sort { ($0.rating?.doubleValue ?? 0) > ($1.rating?.doubleValue ?? 0) }
        case .distance:
            let loc = LocationService.shared.userLocation
            let lat = loc?.latitude ?? 50.4501
            let lon = loc?.longitude ?? 30.5234
            filtered.sort {
                NativeGeoEngine.shared.distanceMeters(lat1: lat, lon1: lon, lat2: $0.latitude, lon2: $0.longitude) <
                NativeGeoEngine.shared.distanceMeters(lat1: lat, lon1: lon, lat2: $1.latitude, lon2: $1.longitude)
            }
        case .recommended:
            let loc = LocationService.shared.userLocation
            filtered = services.recommendationEngine.recommend(
                allPlaces: filtered,
                favorites: favoritePlaces,
                excludeIds: Set(),
                userLat: loc.map { KotlinDouble(value: $0.latitude) },
                userLon: loc.map { KotlinDouble(value: $0.longitude) },
                limit: Int32(filtered.count)
            ) as? [Place] ?? filtered
        default: break
        }

        places = filtered
    }
}
