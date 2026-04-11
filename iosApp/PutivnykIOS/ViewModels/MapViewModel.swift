import SwiftUI
import MapKit
import PutivnykShared

struct MapCenter: Equatable {
    let latitude: Double
    let longitude: Double
}

struct MapViewportBounds: Equatable {
    let minLatitude: Double
    let maxLatitude: Double
    let minLongitude: Double
    let maxLongitude: Double

    func contains(_ latitude: Double, _ longitude: Double) -> Bool {
        latitude >= minLatitude && latitude <= maxLatitude &&
        longitude >= minLongitude && longitude <= maxLongitude
    }

    func clamped(to bounds: MapViewportBounds) -> MapViewportBounds {
        MapViewportBounds(
            minLatitude: max(minLatitude, bounds.minLatitude),
            maxLatitude: min(maxLatitude, bounds.maxLatitude),
            minLongitude: max(minLongitude, bounds.minLongitude),
            maxLongitude: min(maxLongitude, bounds.maxLongitude)
        )
    }
}

@MainActor
final class MapViewModel: ObservableObject {
    static let maidanNezalezhnosti = MapCenter(latitude: 50.4501, longitude: 30.5234)
    private static let defaultZoom = 14
    private static let poiPromptRadiusMeters = 120.0
    private static let waypointReachedRadiusMeters = 80.0
    private static let offRouteThresholdMeters = 50.0
    private static let offRouteConsecutiveThreshold = 3
    private static let rerouteCooldownMs: UInt64 = 10_000

    @Published var selectedPlace: Place?
    @Published var showOnlyFavorites = false
    @Published var mapCenter = maidanNezalezhnosti
    @Published var zoomLevel = defaultZoom
    @Published var searchQuery = ""
    @Published var selectedCategory: PlaceCategory?
    @Published var sortMode: PlaceSortMode = .popularity
    @Published var userLocation = maidanNezalezhnosti
    @Published var hasUserLocation = false
    @Published var userBearing: Float = -1
    @Published var isLoaded = false
    @Published var isCreatingRoute = false

    @Published var placesOnMap: [Place] = []
    @Published var activeRoute: Route?
    @Published var activeRouteLabel: String?
    @Published var routeWaypoints: [RoutePoint] = []
    @Published var routeLinePoints: [RoutePoint] = []
    @Published var pendingRouteAddPlace: Place?

    @Published var currentWaypointIndex = 0
    @Published var remainingDistance = 0.0
    @Published var remainingMinutes = 0
    @Published var routeProgressFraction = 0.0
    @Published var distanceToNextWaypoint = 0.0
    @Published var nextWaypointName: String?
    @Published var poiPromptPlace: Place?

    @Published var bookmarks: [MapBookmark] = []
    @Published var visibleBounds: MapViewportBounds

    @Published var isRerouting = false
    @Published var isUnderground = false

    private let services = AppServices.shared
    private var allPlaces: [Place] = []
    private var promptedPoiIds: Set<Int64> = []
    private var consecutiveOffRouteCount = 0
    private var lastRerouteTimestamp: UInt64 = 0
    private var lastGpsAccuracy = Double.greatestFiniteMagnitude
    private var lastUserSpeedMetersPerSecond = 0.0
    private var routeGeometryProgressIndex = 0
    private var activeRouteTotalDistanceMeters = 0.0
    private var activeRouteTotalDurationSeconds = 0.0
    private var fullRouteGeometry: [RoutePoint] = []

    private static let kyivBounds = MapViewportBounds(
        minLatitude: 50.213, maxLatitude: 50.590,
        minLongitude: 30.239, maxLongitude: 30.825
    )

    init() {
        visibleBounds = Self.computeViewportBounds(center: Self.maidanNezalezhnosti, zoom: Self.defaultZoom)
        Task { await restoreMapState() }
        Task { await loadActiveRoute() }
        Task { await loadPlaces() }
        Task { await loadBookmarks() }
        Task { await applyPendingFocus() }
    }

    // MARK: - Place loading & filtering

    func loadPlaces() async {
        do {
            let places = try await services.placeRepository.getAllPlacesSnapshot()
            allPlaces = places as? [Place] ?? []
            applyFilters()
        } catch {}
    }

    func applyFilters() {
        var filtered = allPlaces.filter { place in
            visibleBounds.contains(place.latitude, place.longitude)
        }

        let query = searchQuery.trimmingCharacters(in: .whitespaces).lowercased()
        let isToiletRequested = query.contains("туалет") || query.contains("toilet") || query.contains("wc")

        if !isToiletRequested {
            filtered = filtered.filter { $0.category != .toilet }
        }

        if let cat = selectedCategory {
            filtered = filtered.filter { $0.category == cat }
        }

        if !query.isEmpty {
            filtered = filtered.filter {
                $0.name.lowercased().contains(query) ||
                $0.category.displayName.lowercased().contains(query) ||
                ($0.tags as? [String] ?? []).contains { $0.lowercased().contains(query) }
            }
        }

        if showOnlyFavorites {
            filtered = filtered.filter { $0.isFavorite }
        }

        switch sortMode {
        case .popularity:
            filtered.sort { $0.popularity > $1.popularity }
        case .rating:
            filtered.sort { ($0.rating?.doubleValue ?? 0) > ($1.rating?.doubleValue ?? 0) }
        case .distance:
            filtered.sort { a, b in
                NativeGeoEngine.shared.distanceMeters(
                    lat1: userLocation.latitude, lon1: userLocation.longitude,
                    lat2: a.latitude, lon2: a.longitude
                ) < NativeGeoEngine.shared.distanceMeters(
                    lat1: userLocation.latitude, lon1: userLocation.longitude,
                    lat2: b.latitude, lon2: b.longitude
                )
            }
        case .recommended:
            filtered.sort { a, b in
                let scoreA = Double(a.popularity) + (a.rating?.doubleValue ?? 0) * 10 + (a.isFavorite ? 100 : 0) - (a.isVisited ? 40 : 0)
                let scoreB = Double(b.popularity) + (b.rating?.doubleValue ?? 0) * 10 + (b.isFavorite ? 100 : 0) - (b.isVisited ? 40 : 0)
                return scoreA > scoreB
            }
        default: break
        }

        placesOnMap = filtered
        isLoaded = true
    }

    // MARK: - Map state

    func selectPlace(_ place: Place?) {
        selectedPlace = place
        if let p = place {
            mapCenter = MapCenter(latitude: p.latitude, longitude: p.longitude)
            visibleBounds = Self.computeViewportBounds(center: mapCenter, zoom: zoomLevel)
        }
    }

    func toggleShowOnlyFavorites() {
        showOnlyFavorites.toggle()
        applyFilters()
    }

    func updateMapCenter(_ lat: Double, _ lon: Double) {
        mapCenter = Self.clampCenterToKyiv(MapCenter(latitude: lat, longitude: lon))
        visibleBounds = Self.computeViewportBounds(center: mapCenter, zoom: zoomLevel)
        schedulePersistMapState()
        applyFilters()
    }

    func updateZoomLevel(_ zoom: Int) {
        zoomLevel = max(10, min(19, zoom))
        visibleBounds = Self.computeViewportBounds(center: mapCenter, zoom: zoomLevel)
        schedulePersistMapState()
        applyFilters()
    }

    func updateSearchQuery(_ query: String) {
        searchQuery = query
        applyFilters()
    }

    func selectCategory(_ category: PlaceCategory?) {
        selectedCategory = category
        applyFilters()
    }

    func setSortMode(_ mode: PlaceSortMode) {
        sortMode = mode
        Task { try? await services.userPreferenceRepository.upsert(key: "map.sort.mode", value: mode.name) }
        applyFilters()
    }

    func centerOnKyiv() {
        mapCenter = Self.maidanNezalezhnosti
        zoomLevel = Self.defaultZoom
        visibleBounds = Self.computeViewportBounds(center: mapCenter, zoom: zoomLevel)
        schedulePersistMapState()
        applyFilters()
    }

    func centerOnUserLocation() {
        guard hasUserLocation else { centerOnKyiv(); return }
        mapCenter = Self.clampCenterToKyiv(userLocation)
        zoomLevel = 16
        visibleBounds = Self.computeViewportBounds(center: mapCenter, zoom: zoomLevel)
        schedulePersistMapState()
        applyFilters()
    }

    func toggleFavorite(_ placeId: Int64) {
        Task { try? await services.placeRepository.toggleFavorite(placeId: placeId) }
        applyFilters()
    }

    // MARK: - User location

    func updateUserLocation(
        _ lat: Double,
        _ lon: Double,
        bearing: Float = -1,
        accuracy: Double = .greatestFiniteMagnitude,
        speedMetersPerSecond: Double = 0
    ) {
        userLocation = MapCenter(latitude: lat, longitude: lon)
        hasUserLocation = true
        if bearing >= 0 { userBearing = bearing }
        lastGpsAccuracy = accuracy
        lastUserSpeedMetersPerSecond = max(0, speedMetersPerSecond)

        if activeRoute != nil {
            mapCenter = Self.clampCenterToKyiv(MapCenter(latitude: lat, longitude: lon))
            if zoomLevel < 16 { zoomLevel = 16 }
        }

        updateRouteProgress(lat: lat, lon: lon)
    }

    func updateUserBearing(_ bearing: Float) {
        if bearing >= 0 { userBearing = bearing }
    }

    // MARK: - Route management

    func activateRoute(_ routeId: Int64) {
        Task {
            guard let route = try? await services.routeRepository.getRouteById(id: routeId) else { return }
            activeRoute = route
            try? await services.userPreferenceRepository.upsert(key: "map.activeRouteId", value: "\(routeId)")
            updateRouteWaypoints(route)
            await fetchWalkingDirections(for: route)
        }
    }

    func deactivateRoute() {
        activeRoute = nil
        routeLinePoints = []
        routeWaypoints = []
        activeRouteLabel = nil
        currentWaypointIndex = 0
        remainingDistance = 0
        remainingMinutes = 0
        routeProgressFraction = 0
        distanceToNextWaypoint = 0
        nextWaypointName = nil
        poiPromptPlace = nil
        fullRouteGeometry = []
        activeRouteTotalDistanceMeters = 0
        activeRouteTotalDurationSeconds = 0
        routeGeometryProgressIndex = 0
        promptedPoiIds.removeAll()
        Task { try? await services.userPreferenceRepository.deleteByKey(key: "map.activeRouteId") }
    }

    func createRouteToPlace(_ destination: Place) {
        guard hasUserLocation else { return }
        let user = userLocation
        isCreatingRoute = true
        Task {
            defer { isCreatingRoute = false }
            let timestamp = PlatformClock.shared.currentTimeMillis()
            let baseRoute = Route(
                id: 0,
                name: "→ \(destination.name)",
                description: nil,
                startPoint: RoutePoint(latitude: user.latitude, longitude: user.longitude, name: "Моя позиція"),
                endPoint: RoutePoint(latitude: destination.latitude, longitude: destination.longitude, name: destination.name),
                waypoints: [],
                distance: 0,
                estimatedDuration: 0,
                isFavorite: false,
                createdAt: timestamp,
                updatedAt: timestamp
            )
            let (route, geometry) = await enrichRouteMetrics(baseRoute)
            do {
                let savedId = try await services.routeRepository.saveRoute(route: route)
                let saved = route.doCopy(
                    id: savedId.int64Value,
                    name: route.name,
                    description: route.description_,
                    startPoint: route.startPoint,
                    endPoint: route.endPoint,
                    waypoints: route.waypoints,
                    distance: route.distance,
                    estimatedDuration: route.estimatedDuration,
                    isFavorite: route.isFavorite,
                    createdAt: route.createdAt,
                    updatedAt: route.updatedAt
                )
                activeRoute = saved
                try? await services.userPreferenceRepository.upsert(key: "map.activeRouteId", value: "\(savedId)")
                updateRouteWaypoints(saved)
                applyWalkingRouteResult(route: saved, geometry: geometry, distanceMeters: route.distance, durationMinutes: Int(route.estimatedDuration))
            } catch {}
        }
    }

    func requestAddToActiveRoute(_ place: Place) {
        pendingRouteAddPlace = place
    }

    func dismissAddToRouteDialog() {
        pendingRouteAddPlace = nil
    }

    func confirmAddToActiveRoute() {
        guard let route = activeRoute, let place = pendingRouteAddPlace else { return }
        guard place.category != .toilet else { pendingRouteAddPlace = nil; return }
        guard RouteMetricsCalculator.shared.isValidCoordinate(lat: place.latitude, lon: place.longitude) else {
            pendingRouteAddPlace = nil; return
        }

        let waypoint = RoutePoint(latitude: place.latitude, longitude: place.longitude, name: place.name)
        guard !RouteMetricsCalculator.shared.isDuplicate(point: waypoint, route: route) else {
            pendingRouteAddPlace = nil; return
        }

        Task {
            let updated = RouteMetricsCalculator.shared.withAppendedWaypoint(route: route, waypoint: waypoint)
            let (enriched, _) = await enrichRouteMetrics(updated)
            try? await services.routeRepository.updateRoute(route: enriched)
            pendingRouteAddPlace = nil
        }
    }

    func skipWaypoint() {
        guard let route = activeRoute else { return }
        let allPoints = buildAllPoints(route)
        let newIdx = min(currentWaypointIndex + 1, allPoints.count - 1)
        currentWaypointIndex = newIdx
        let geometry = fullRouteGeometry.isEmpty ? allPoints : fullRouteGeometry
        let nearestGeometryIdx = nearestGeometryIndex(lat: userLocation.latitude, lon: userLocation.longitude, geometry: geometry)
        routeGeometryProgressIndex = max(routeGeometryProgressIndex, nearestGeometryIdx)
        recalculateRemainingDistance(
            userLat: userLocation.latitude,
            userLon: userLocation.longitude,
            allPoints: allPoints,
            currentIdx: newIdx,
            geometry: geometry,
            geometryIdx: routeGeometryProgressIndex
        )
        updateDisplayedGeometry(
            userLat: userLocation.latitude,
            userLon: userLocation.longitude,
            geometry: geometry,
            geometryIdx: routeGeometryProgressIndex
        )
    }

    func dismissPoiPrompt() { poiPromptPlace = nil }
    func acceptPoiVisit() { poiPromptPlace = nil }

    // MARK: - Bookmarks

    func saveCurrentViewAsBookmark() {
        Task {
            let timestamp = PlatformClock.shared.currentTimeMillis()
            let bm = MapBookmark(
                id: 0,
                title: "Київ \(String(format: "%.4f", mapCenter.latitude)), \(String(format: "%.4f", mapCenter.longitude))",
                note: "Автозбереження позиції карти",
                latitude: mapCenter.latitude, longitude: mapCenter.longitude,
                zoomLevel: Int32(zoomLevel),
                createdAt: timestamp,
                updatedAt: timestamp
            )
            if let savedId = try? await services.mapBookmarkRepository.save(bookmark: bm) {
                bookmarks.append(
                    MapBookmark(
                        id: savedId.int64Value,
                        title: bm.title,
                        note: bm.note,
                        latitude: bm.latitude,
                        longitude: bm.longitude,
                        zoomLevel: bm.zoomLevel,
                        createdAt: bm.createdAt,
                        updatedAt: bm.updatedAt
                    )
                )
            }
        }
    }

    func applyBookmark(_ bookmark: MapBookmark) {
        mapCenter = Self.clampCenterToKyiv(MapCenter(latitude: bookmark.latitude, longitude: bookmark.longitude))
        zoomLevel = max(10, min(19, Int(bookmark.zoomLevel)))
        visibleBounds = Self.computeViewportBounds(center: mapCenter, zoom: zoomLevel)
        schedulePersistMapState()
        applyFilters()
    }

    func removeBookmark(_ bookmark: MapBookmark) {
        Task {
            try? await services.mapBookmarkRepository.delete(bookmark: bookmark)
            bookmarks.removeAll { $0.id == bookmark.id }
        }
    }

    func clearAllBookmarks() {
        Task {
            try? await services.mapBookmarkRepository.deleteAll()
            bookmarks = []
        }
    }

    func syncPendingFocus() {
        Task {
            await applyPendingActiveRoute()
            await applyPendingRouteToPlace()
            await applyPendingFocus()
        }
    }

    // MARK: - Private helpers

    private func loadActiveRoute() async {
        guard let idStr = try? await services.userPreferenceRepository.getString(key: "map.activeRouteId", defaultValue: ""),
              let routeId = Int64(idStr) else { return }
        guard let route = try? await services.routeRepository.getRouteById(id: routeId) else { return }
        activeRoute = route
        updateRouteWaypoints(route)
        await fetchWalkingDirections(for: route)
    }

    private func loadBookmarks() async {
        bookmarks = []
    }

    private func updateRouteWaypoints(_ route: Route) {
        let allPts = buildAllPoints(route)
        routeWaypoints = allPts
        activeRouteLabel = "Активний маршрут: \(route.name) (\(allPts.count) точок)"
    }

    private func buildAllPoints(_ route: Route) -> [RoutePoint] {
        var pts = [route.startPoint]
        if let wps = route.waypoints as? [RoutePoint] {
            pts.append(contentsOf: wps)
        }
        pts.append(route.endPoint)
        return pts
    }

    private func fetchWalkingDirections(for route: Route) async {
        let allWaypoints = buildAllPoints(route)
        guard allWaypoints.count >= 2 else { return }

        let requestedWaypoints: [RoutePoint]
        if hasUserLocation {
            let segmentStartIndex = min(currentWaypointIndex, allWaypoints.count - 1)
            let distToSegmentStart = NativeGeoEngine.shared.distanceMeters(
                lat1: userLocation.latitude,
                lon1: userLocation.longitude,
                lat2: allWaypoints[segmentStartIndex].latitude,
                lon2: allWaypoints[segmentStartIndex].longitude
            )
            if distToSegmentStart > Self.offRouteThresholdMeters {
                requestedWaypoints = [RoutePoint(latitude: userLocation.latitude, longitude: userLocation.longitude, name: nil)] + Array(allWaypoints[min(currentWaypointIndex + 1, allWaypoints.count - 1)...])
            } else if currentWaypointIndex > 0 {
                requestedWaypoints = Array(allWaypoints[currentWaypointIndex...])
            } else {
                requestedWaypoints = allWaypoints
            }
        } else {
            requestedWaypoints = allWaypoints
        }

        fullRouteGeometry = requestedWaypoints
        routeLinePoints = requestedWaypoints
        activeRouteTotalDistanceMeters = route.distance > 0 ? route.distance : NativeGeoEngine.shared.polylineDistanceMeters(points: requestedWaypoints)
        activeRouteTotalDurationSeconds = route.estimatedDuration > 0 ? Double(route.estimatedDuration) * 60.0 : estimateDurationSeconds(activeRouteTotalDistanceMeters)
        routeGeometryProgressIndex = 0

        do {
            let details = try await services.walkingDirectionsProvider.fetchWalkingRouteResult(waypoints: requestedWaypoints)
            guard activeRoute?.id == route.id else { return }
            applyWalkingRouteResult(route: route, result: details)
            if hasUserLocation {
                updateRouteProgress(lat: userLocation.latitude, lon: userLocation.longitude)
            }
        } catch {}
    }

    private func updateRouteProgress(lat: Double, lon: Double) {
        guard let route = activeRoute else { return }
        let allPoints = buildAllPoints(route)
        guard allPoints.count >= 2 else { return }
        let geometry = fullRouteGeometry.isEmpty ? allPoints : fullRouteGeometry

        let nearestIdx = Int(NativeGeoEngine.shared.nearestPointIndex(points: allPoints, lat: lat, lon: lon))
        guard nearestIdx >= 0 else { return }

        let distToNearest = NativeGeoEngine.shared.distanceMeters(
            lat1: lat, lon1: lon,
            lat2: allPoints[nearestIdx].latitude, lon2: allPoints[nearestIdx].longitude
        )

        let currentIdx = currentWaypointIndex
        let newIdx = (distToNearest <= Self.waypointReachedRadiusMeters && nearestIdx >= currentIdx) ? nearestIdx : currentIdx
        currentWaypointIndex = newIdx

        let nearestGeometryIdx = max(routeGeometryProgressIndex, nearestGeometryIndex(lat: lat, lon: lon, geometry: geometry))
        routeGeometryProgressIndex = nearestGeometryIdx

        recalculateRemainingDistance(
            userLat: lat,
            userLon: lon,
            allPoints: allPoints,
            currentIdx: newIdx,
            geometry: geometry,
            geometryIdx: nearestGeometryIdx
        )
        updateDisplayedGeometry(userLat: lat, userLon: lon, geometry: geometry, geometryIdx: nearestGeometryIdx)
        checkOffRouteAndReroute(userLat: lat, userLon: lon, route: route, allWaypoints: allPoints, geometry: geometry, currentIdx: newIdx)
        checkPoiProximity(lat: lat, lon: lon, routePoints: allPoints, currentIdx: newIdx)
    }

    private func recalculateRemainingDistance(
        userLat: Double,
        userLon: Double,
        allPoints: [RoutePoint],
        currentIdx: Int,
        geometry: [RoutePoint],
        geometryIdx: Int
    ) {
        guard currentIdx < allPoints.count - 1 else {
            remainingDistance = 0
            remainingMinutes = 0
            routeProgressFraction = 1
            distanceToNextWaypoint = 0
            nextWaypointName = nil
            return
        }

        let routeRemaining = computeRemainingGeometryDistance(
            userLat: userLat,
            userLon: userLon,
            geometry: geometry,
            geometryIdx: geometryIdx
        )
        remainingDistance = routeRemaining
        let remainingDurationSeconds: Double
        if activeRouteTotalDistanceMeters > 1, activeRouteTotalDurationSeconds > 0 {
            let remainingRatio = min(1.0, max(0.0, routeRemaining / activeRouteTotalDistanceMeters))
            remainingDurationSeconds = activeRouteTotalDurationSeconds * remainingRatio
        } else {
            remainingDurationSeconds = estimateDurationSeconds(routeRemaining)
        }
        remainingMinutes = max(1, Int(ceil(remainingDurationSeconds / 60.0)))
        routeProgressFraction = activeRouteTotalDistanceMeters > 1
            ? 1.0 - min(1.0, max(0.0, routeRemaining / activeRouteTotalDistanceMeters))
            : Double(currentIdx) / Double(max(1, allPoints.count - 1))

        let nextIdx = currentIdx + 1
        let distToNext = NativeGeoEngine.shared.distanceMeters(
            lat1: userLat, lon1: userLon,
            lat2: allPoints[nextIdx].latitude, lon2: allPoints[nextIdx].longitude
        )
        distanceToNextWaypoint = distToNext
        nextWaypointName = allPoints[nextIdx].name
    }

    private func checkOffRouteAndReroute(
        userLat: Double,
        userLon: Double,
        route: Route,
        allWaypoints: [RoutePoint],
        geometry: [RoutePoint],
        currentIdx: Int
    ) {
        guard geometry.count >= 2, currentIdx < allWaypoints.count - 1 else { return }
        if lastGpsAccuracy > 180, lastUserSpeedMetersPerSecond <= 8 { return }

        let nearestGeometryIdx = nearestGeometryIndex(lat: userLat, lon: userLon, geometry: geometry)
        let distanceToRoute = NativeGeoEngine.shared.distanceMeters(
            lat1: userLat,
            lon1: userLon,
            lat2: geometry[nearestGeometryIdx].latitude,
            lon2: geometry[nearestGeometryIdx].longitude
        )

        let dynamicThreshold: Double
        if lastUserSpeedMetersPerSecond >= 10 {
            dynamicThreshold = 120
        } else if lastUserSpeedMetersPerSecond >= 5 {
            dynamicThreshold = 80
        } else {
            dynamicThreshold = min(160, max(Self.offRouteThresholdMeters, lastGpsAccuracy * 1.2))
        }

        let requiredOffRouteCount: Int
        if lastUserSpeedMetersPerSecond >= 10 {
            requiredOffRouteCount = 1
        } else if lastUserSpeedMetersPerSecond >= 5 {
            requiredOffRouteCount = 2
        } else {
            requiredOffRouteCount = Self.offRouteConsecutiveThreshold
        }

        let rerouteCooldown: UInt64
        if lastUserSpeedMetersPerSecond >= 10 {
            rerouteCooldown = 4_000
        } else if lastUserSpeedMetersPerSecond >= 5 {
            rerouteCooldown = 6_000
        } else {
            rerouteCooldown = Self.rerouteCooldownMs
        }

        guard distanceToRoute > dynamicThreshold else {
            consecutiveOffRouteCount = 0
            return
        }

        consecutiveOffRouteCount += 1
        guard consecutiveOffRouteCount >= requiredOffRouteCount else { return }

        let now = UInt64(Date().timeIntervalSince1970 * 1000)
        guard now - lastRerouteTimestamp >= rerouteCooldown else { return }
        lastRerouteTimestamp = now
        consecutiveOffRouteCount = 0

        let newWaypoints = [RoutePoint(latitude: userLat, longitude: userLon, name: nil)] + Array(allWaypoints[min(currentIdx + 1, allWaypoints.count - 1)...])
        isRerouting = true
        Task {
            defer { isRerouting = false }
            do {
                let details = try await services.walkingDirectionsProvider.fetchWalkingRouteResult(waypoints: newWaypoints)
                guard activeRoute?.id == route.id else { return }
                applyWalkingRouteResult(route: route, result: details)
                updateRouteProgress(lat: userLat, lon: userLon)
            } catch {}
        }
    }

    private func applyWalkingRouteResult(route: Route, result: WalkingRouteResult) {
        let geometry = result.geometry as? [RoutePoint] ?? buildAllPoints(route)
        let distanceMeters = result.distanceMeters?.doubleValue ?? NativeGeoEngine.shared.polylineDistanceMeters(points: geometry)
        let durationSeconds = result.durationSeconds?.doubleValue
        let durationMinutes = durationSeconds != nil ? Int(ceil(durationSeconds! / 60.0)) : Int(route.estimatedDuration)
        applyWalkingRouteResult(route: route, geometry: geometry, distanceMeters: distanceMeters, durationMinutes: durationMinutes)
        persistPreciseRouteMetricsIfNeeded(route: route, distanceMeters: distanceMeters, durationMinutes: durationMinutes, durationSeconds: durationSeconds)
    }

    private func applyWalkingRouteResult(route: Route, geometry: [RoutePoint], distanceMeters: Double, durationMinutes: Int) {
        fullRouteGeometry = geometry.isEmpty ? buildAllPoints(route) : geometry
        activeRouteTotalDistanceMeters = distanceMeters > 0 ? distanceMeters : NativeGeoEngine.shared.polylineDistanceMeters(points: fullRouteGeometry)
        activeRouteTotalDurationSeconds = durationMinutes > 0 ? Double(durationMinutes) * 60.0 : estimateDurationSeconds(activeRouteTotalDistanceMeters)
        if hasUserLocation {
            let nearestGeometryIdx = max(routeGeometryProgressIndex, nearestGeometryIndex(lat: userLocation.latitude, lon: userLocation.longitude, geometry: fullRouteGeometry))
            routeGeometryProgressIndex = nearestGeometryIdx
            updateDisplayedGeometry(userLat: userLocation.latitude, userLon: userLocation.longitude, geometry: fullRouteGeometry, geometryIdx: nearestGeometryIdx)
        } else {
            routeLinePoints = fullRouteGeometry
        }
    }

    private func updateDisplayedGeometry(userLat: Double, userLon: Double, geometry: [RoutePoint], geometryIdx: Int) {
        guard !geometry.isEmpty else {
            routeLinePoints = []
            return
        }
        let clampedIndex = min(max(0, geometryIdx), geometry.count - 1)
        routeLinePoints = [RoutePoint(latitude: userLat, longitude: userLon, name: nil)] + Array(geometry[clampedIndex...])
    }

    private func computeRemainingGeometryDistance(userLat: Double, userLon: Double, geometry: [RoutePoint], geometryIdx: Int) -> Double {
        guard !geometry.isEmpty else { return 0 }
        let clampedIndex = min(max(0, geometryIdx), geometry.count - 1)
        let tail = [RoutePoint(latitude: userLat, longitude: userLon, name: nil)] + Array(geometry[clampedIndex...])
        return NativeGeoEngine.shared.polylineDistanceMeters(points: tail)
    }

    private func enrichRouteMetrics(_ route: Route) async -> (Route, [RoutePoint]) {
        let points = buildAllPoints(route)
        guard points.count >= 2 else {
            return (RouteMetricsCalculator.shared.recompute(route: route), points)
        }
        do {
            let details = try await services.walkingDirectionsProvider.fetchWalkingRouteResult(waypoints: points)
            let geometry = details.geometry as? [RoutePoint] ?? points
            let distanceMeters = details.distanceMeters?.doubleValue ?? NativeGeoEngine.shared.polylineDistanceMeters(points: geometry)
            let enriched = RouteMetricsCalculator.shared.withMetrics(
                route: route,
                distanceMeters: distanceMeters,
                durationSeconds: details.durationSeconds
            )
            return (enriched, geometry)
        } catch {
            return (RouteMetricsCalculator.shared.recompute(route: route), points)
        }
    }

    private func persistPreciseRouteMetricsIfNeeded(route: Route, distanceMeters: Double, durationMinutes: Int, durationSeconds: Double?) {
        guard route.id != 0 else { return }
        let distanceChanged = abs(route.distance - distanceMeters) >= 25
        let durationChanged = abs(Int(route.estimatedDuration) - durationMinutes) >= 1
        guard distanceChanged || durationChanged else { return }

        Task {
            guard let latest = try? await self.services.routeRepository.getRouteById(id: route.id) else { return }
            let updated = RouteMetricsCalculator.shared.withMetrics(
                route: latest,
                distanceMeters: distanceMeters,
                durationSeconds: durationSeconds.map { KotlinDouble(value: $0) }
            )
            try? await self.services.routeRepository.updateRoute(route: updated)
        }
    }

    private func nearestGeometryIndex(lat: Double, lon: Double, geometry: [RoutePoint]) -> Int {
        guard !geometry.isEmpty else { return 0 }
        return max(0, Int(NativeGeoEngine.shared.nearestPointIndex(points: geometry, lat: lat, lon: lon)))
    }

    private func estimateDurationSeconds(_ distanceMeters: Double) -> Double {
        distanceMeters / 1.25
    }

    private func checkPoiProximity(lat: Double, lon: Double, routePoints: [RoutePoint], currentIdx: Int) {
        guard poiPromptPlace == nil else { return }
        let poiCategories: Set<PlaceCategory> = [.restaurant, .museum, .theater]

        for i in currentIdx..<routePoints.count {
            let rp = routePoints[i]
            let dist = NativeGeoEngine.shared.distanceMeters(lat1: lat, lon1: lon, lat2: rp.latitude, lon2: rp.longitude)
            guard dist <= Self.poiPromptRadiusMeters else { continue }

            if let match = allPlaces.first(where: { place in
                poiCategories.contains(place.category) &&
                !promptedPoiIds.contains(place.id) &&
                NativeGeoEngine.shared.distanceMeters(lat1: place.latitude, lon1: place.longitude, lat2: rp.latitude, lon2: rp.longitude) < 50.0
            }) {
                promptedPoiIds.insert(match.id)
                poiPromptPlace = match
                return
            }
        }
    }

    private func restoreMapState() async {
        let latValue = (try? await services.userPreferenceRepository.getString(key: "map.center.lat", defaultValue: "")) ?? ""
        let lonValue = (try? await services.userPreferenceRepository.getString(key: "map.center.lon", defaultValue: "")) ?? ""
        let zoomValue = (try? await services.userPreferenceRepository.getString(key: "map.zoom", defaultValue: "")) ?? ""
        let lat = Double(latValue)
        let lon = Double(lonValue)
        let zoom = Int(zoomValue)

        if let lat, let lon { mapCenter = MapCenter(latitude: lat, longitude: lon) }
        if let zoom { zoomLevel = zoom }
        let mode = (try? await services.userPreferenceRepository.getString(key: "map.sort.mode", defaultValue: "")) ?? ""
        if let parsed = PlaceSortMode.entries.first(where: { $0.name.uppercased() == mode.uppercased() }) {
            sortMode = parsed
        }
        visibleBounds = Self.computeViewportBounds(center: mapCenter, zoom: zoomLevel)
    }

    private func schedulePersistMapState() {
        Task {
            try? await services.userPreferenceRepository.upsert(key: "map.center.lat", value: "\(mapCenter.latitude)")
            try? await services.userPreferenceRepository.upsert(key: "map.center.lon", value: "\(mapCenter.longitude)")
            try? await services.userPreferenceRepository.upsert(key: "map.zoom", value: "\(zoomLevel)")
        }
    }

    private func applyPendingFocus() async {
        if let idStr = try? await services.userPreferenceRepository.getString(key: "map.focus.placeId", defaultValue: ""),
           let placeId = Int64(idStr) {
            if let place = try? await services.placeRepository.getPlaceById(id: placeId) {
                selectPlace(place)
                try? await services.userPreferenceRepository.deleteByKey(key: "map.focus.placeId")
                return
            }
        }

        let latValue = (try? await services.userPreferenceRepository.getString(key: "map.focus.lat", defaultValue: "")) ?? ""
        let lonValue = (try? await services.userPreferenceRepository.getString(key: "map.focus.lon", defaultValue: "")) ?? ""
        let zoomValue = (try? await services.userPreferenceRepository.getString(key: "map.focus.zoom", defaultValue: "")) ?? ""
        let lat = Double(latValue)
        let lon = Double(lonValue)
        let zoom = Int(zoomValue)
        if let lat, let lon {
            updateMapCenter(lat, lon)
            if let zoom { updateZoomLevel(zoom) }
            try? await services.userPreferenceRepository.deleteByKey(key: "map.focus.lat")
            try? await services.userPreferenceRepository.deleteByKey(key: "map.focus.lon")
            try? await services.userPreferenceRepository.deleteByKey(key: "map.focus.zoom")
        }
    }

    private func applyPendingActiveRoute() async {
        guard let idStr = try? await services.userPreferenceRepository.getString(key: "map.pending.activeRouteId", defaultValue: ""),
              let pendingId = Int64(idStr) else { return }
        try? await services.userPreferenceRepository.deleteByKey(key: "map.pending.activeRouteId")
        guard activeRoute?.id != pendingId else { return }
        activateRoute(pendingId)
    }

    private func applyPendingRouteToPlace() async {
        guard let idStr = try? await services.userPreferenceRepository.getString(key: "map.pending.routeToPlaceId", defaultValue: ""),
              let placeId = Int64(idStr) else { return }
        try? await services.userPreferenceRepository.deleteByKey(key: "map.pending.routeToPlaceId")
        if let place = try? await services.placeRepository.getPlaceById(id: placeId) {
            selectPlace(place)
            createRouteToPlace(place)
        }
    }

    private static func computeViewportBounds(center: MapCenter, zoom: Int) -> MapViewportBounds {
        let baseSpan = 0.6
        let zoomFactor = pow(2.0, Double(zoom - 13))
        let latSpan = max(0.01, min(1.2, baseSpan / zoomFactor))
        let lonSpan = max(0.01, min(1.2, baseSpan / zoomFactor))

        let raw = MapViewportBounds(
            minLatitude: center.latitude - latSpan / 2,
            maxLatitude: center.latitude + latSpan / 2,
            minLongitude: center.longitude - lonSpan / 2,
            maxLongitude: center.longitude + lonSpan / 2
        )
        return raw.clamped(to: kyivBounds)
    }

    private static func clampCenterToKyiv(_ center: MapCenter) -> MapCenter {
        MapCenter(
            latitude: max(50.213, min(50.590, center.latitude)),
            longitude: max(30.239, min(30.825, center.longitude))
        )
    }
}
