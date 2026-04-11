import SwiftUI
import PutivnykShared

@MainActor
final class HomeViewModel: ObservableObject {
    @Published var recommendedPlaces: [Place] = []
    @Published var pinnedPlaces: [Place] = []
    @Published var availablePlacesForPinning: [Place] = []
    @Published private(set) var hasMoreRecommendations = false
    @Published var isLoaded = false
    @Published var showAddPinnedDialog = false

    private let services = AppServices.shared
    private let recommendationPageSize = 10
    private var pinnedIds: Set<Int64> = []
    private var allPlaces: [Place] = []
    private var allRecommendedPlaces: [Place] = []
    private var recommendationLimit = 10

    init() { Task { await load() } }

    func load() async {
        allPlaces = (try? await services.placeRepository.getAllPlacesSnapshot()) ?? []
        let savedPinned = (try? await services.userPreferenceRepository.getString(key: "home.pinned_place_ids", defaultValue: "")) ?? ""
        pinnedIds = Set(savedPinned.split(separator: ",").compactMap { Int64($0.trimmingCharacters(in: .whitespaces)) })

        pinnedPlaces = allPlaces.filter { pinnedIds.contains($0.id) }
        availablePlacesForPinning = allPlaces.filter { !pinnedIds.contains($0.id) }

        let excludedIds = Set(pinnedIds.map { KotlinLong(value: $0) })
        let favorites = allPlaces.filter { $0.isFavorite }
        let reco = services.recommendationEngine.recommend(
            allPlaces: allPlaces,
            favorites: favorites,
            excludeIds: excludedIds,
            userLat: nil, userLon: nil,
            limit: Int32(allPlaces.count)
        )
        allRecommendedPlaces = reco
        recommendedPlaces = Array(allRecommendedPlaces.prefix(recommendationLimit))
        hasMoreRecommendations = allRecommendedPlaces.count > recommendedPlaces.count
        isLoaded = true
    }

    func pinPlace(_ placeId: Int64) {
        pinnedIds.insert(placeId)
        persistPinnedIds()
        Task { await load() }
    }

    func unpinPlace(_ placeId: Int64) {
        pinnedIds.remove(placeId)
        persistPinnedIds()
        Task { await load() }
    }

    func toggleFavorite(_ placeId: Int64) {
        Task {
            try? await services.placeRepository.toggleFavorite(placeId: placeId)
            await load()
        }
    }

    func loadAvailablePlacesForPinning() {
        availablePlacesForPinning = allPlaces.filter { !pinnedIds.contains($0.id) }
    }

    func loadMoreRecommendations() {
        guard hasMoreRecommendations else { return }
        recommendationLimit += recommendationPageSize
        recommendedPlaces = Array(allRecommendedPlaces.prefix(recommendationLimit))
        hasMoreRecommendations = allRecommendedPlaces.count > recommendedPlaces.count
    }

    func openAddPinnedDialog() { showAddPinnedDialog = true }
    func dismissAddPinnedDialog() { showAddPinnedDialog = false }

    func requestOpenMapForPlace(_ place: Place) {
        Task {
            try? await services.userPreferenceRepository.upsert(key: "map.focus.placeId", value: String(place.id))
        }
    }

    private func persistPinnedIds() {
        Task {
            let value = pinnedIds.map(String.init).joined(separator: ",")
            try? await services.userPreferenceRepository.upsert(key: "home.pinned_place_ids", value: value)
        }
    }
}
