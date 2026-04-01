import SwiftUI
import PutivnykShared

@MainActor
final class HomeViewModel: ObservableObject {
    @Published var recommendedPlaces: [Place] = []
    @Published var pinnedPlaces: [Place] = []
    @Published var isLoaded = false
    @Published var showAddPinnedDialog = false

    private let services = AppServices.shared
    private var pinnedIds: Set<Int64> = []
    private var allPlaces: [Place] = []

    init() { Task { await load() } }

    func load() async {
        allPlaces = services.placeRepository.getAllPlacesSnapshot() as? [Place] ?? []
        let savedPinned = (try? await services.userPreferenceRepository.getString(key: "home.pinned_place_ids", defaultValue: "")) ?? ""
        pinnedIds = Set(savedPinned.split(separator: ",").compactMap { Int64($0.trimmingCharacters(in: .whitespaces)) })

        pinnedPlaces = allPlaces.filter { pinnedIds.contains($0.id) }

        let favorites = allPlaces.filter { $0.isFavorite }
        let reco = services.recommendationEngine.recommend(
            allPlaces: allPlaces,
            favorites: favorites,
            excludeIds: pinnedIds as NSSet,
            userLat: nil, userLon: nil,
            limit: 10
        )
        recommendedPlaces = (reco as? [Place] ?? [])
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

    func openAddPinnedDialog() { showAddPinnedDialog = true }
    func dismissAddPinnedDialog() { showAddPinnedDialog = false }

    func requestOpenMapForPlace(_ place: Place) {
        Task {
            try? await services.userPreferenceRepository.upsert(key: "map.focus.placeId", value_: String(place.id))
        }
    }

    private func persistPinnedIds() {
        Task {
            let value = pinnedIds.map(String.init).joined(separator: ",")
            try? await services.userPreferenceRepository.upsert(key: "home.pinned_place_ids", value_: value)
        }
    }
}
