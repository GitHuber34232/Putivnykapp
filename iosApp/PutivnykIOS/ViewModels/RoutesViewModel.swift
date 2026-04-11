import SwiftUI
import PutivnykShared

@MainActor
final class RoutesViewModel: ObservableObject {
    @Published var routes: [Route] = []
    @Published var availablePlaces: [Place] = []
    @Published var searchQuery: String = ""
    @Published var showOnlyFavorites: Bool = false
    @Published var activeRouteId: Int64? = nil
    @Published var selectedRoute: Route? = nil
    @Published var isLoaded: Bool = false
    @Published var isCreatingRoute: Bool = false

    private let services = AppServices.shared

    init() { Task { await loadRoutes() } }

    func loadRoutes() async {
        let snapshot = try? await services.routeRepository.getAllRoutesSnapshot()
        var all = snapshot as? [Route] ?? []
        let query = searchQuery.trimmingCharacters(in: .whitespaces).lowercased()

        if showOnlyFavorites { all = all.filter { $0.isFavorite } }
        if !query.isEmpty {
            all = all.filter {
                $0.name.lowercased().contains(query) ||
                ($0.description_?.lowercased().contains(query) ?? false)
            }
        }
        routes = all
        await refreshActiveRouteState()
        isLoaded = true
    }

    func updateSearchQuery(_ value: String) {
        searchQuery = value
        Task { await loadRoutes() }
    }

    func toggleShowOnlyFavorites() {
        showOnlyFavorites.toggle()
        Task { await loadRoutes() }
    }

    func selectRoute(_ route: Route?) { selectedRoute = route }

    func toggleFavorite(routeId: Int64) {
        Task {
            try? await services.routeRepository.toggleFavorite(routeId: routeId)
            await loadRoutes()
        }
    }

    func deleteRoute(_ route: Route) {
        deleteRoute(route.id)
    }

    func deleteRoute(_ routeId: Int64) {
        Task {
            guard let route = try? await services.routeRepository.getRouteById(id: routeId) else { return }
            if activeRouteId == routeId {
                activeRouteId = nil
                try? await services.userPreferenceRepository.deleteByKey(key: "map.activeRouteId")
                try? await services.userPreferenceRepository.deleteByKey(key: "map.pending.activeRouteId")
            }
            try? await services.routeRepository.deleteRoute(route: route)
            await loadRoutes()
        }
    }

    func renameRoute(_ routeId: Int64, name: String) {
        Task {
            guard let route = try? await services.routeRepository.getRouteById(id: routeId) else { return }
            let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
            let updated = route.doCopy(
                id: route.id,
                name: trimmedName.isEmpty ? route.name : trimmedName,
                description: route.description_,
                startPoint: route.startPoint,
                endPoint: route.endPoint,
                waypoints: route.waypoints,
                distance: route.distance,
                estimatedDuration: route.estimatedDuration,
                isFavorite: route.isFavorite,
                createdAt: route.createdAt,
                updatedAt: currentTimeMillis()
            )
            try? await services.routeRepository.updateRoute(route: updated)
            await loadRoutes()
        }
    }

    func saveRoute(_ route: Route) {
        Task {
            let enriched = await enrichRouteMetrics(route)
            try? await services.routeRepository.saveRoute(route: enriched)
            await loadRoutes()
        }
    }

    func optimizeRoute(routeId: Int64) {
        Task {
            guard let route = try? await services.routeRepository.getRouteById(id: routeId) else { return }
            let optimized = services.routeOptimizer.optimizeWaypoints(route: route)
            try? await services.routeRepository.updateRoute(route: await enrichRouteMetrics(optimized))
            await loadRoutes()
        }
    }

    func reverseRoute(routeId: Int64) {
        Task {
            guard let route = try? await services.routeRepository.getRouteById(id: routeId) else { return }
            let reversed = route.doCopy(
                id: route.id,
                name: route.name,
                description: route.description_,
                startPoint: route.endPoint,
                endPoint: route.startPoint,
                waypoints: Array(route.waypoints.reversed()),
                distance: route.distance,
                estimatedDuration: route.estimatedDuration,
                isFavorite: route.isFavorite,
                createdAt: route.createdAt,
                updatedAt: currentTimeMillis()
            )
            try? await services.routeRepository.updateRoute(route: await enrichRouteMetrics(reversed))
            await loadRoutes()
        }
    }

    func createRouteFromPlaces(name: String, selectedPlaces: [Place]) {
        guard selectedPlaces.count >= 2 else { return }
        isCreatingRoute = true
        Task {
            let points = selectedPlaces.map {
                RoutePoint(latitude: $0.latitude, longitude: $0.longitude, name: $0.name)
            }
            let start = points.first!
            let end = points.last!
            let intermediates = points.count > 2 ? Array(points[1..<points.count-1]) : []
            let route = Route(
                id: 0,
                name: name.isEmpty ? "Мій маршрут" : name,
                description: "Маршрут з \(selectedPlaces.count) точок",
                startPoint: start,
                endPoint: end,
                waypoints: intermediates,
                distance: 0,
                estimatedDuration: 0,
                isFavorite: false,
                createdAt: currentTimeMillis(),
                updatedAt: currentTimeMillis()
            )
            try? await services.routeRepository.saveRoute(route: await enrichRouteMetrics(route))
            isCreatingRoute = false
            await loadRoutes()
        }
    }

    func createRouteFromPlaces(_ name: String, places: [Place]) {
        createRouteFromPlaces(name: name, selectedPlaces: places)
    }

    func activateRouteOnMap(routeId: Int64) {
        activeRouteId = routeId
        Task {
            try? await services.userPreferenceRepository.upsert(key: "map.pending.activeRouteId", value: String(routeId))
        }
    }

    func deactivateRoute() {
        activeRouteId = nil
        Task {
            try? await services.userPreferenceRepository.deleteByKey(key: "map.activeRouteId")
            try? await services.userPreferenceRepository.deleteByKey(key: "map.pending.activeRouteId")
        }
    }

    func loadAvailablePlaces() {
        Task {
            let snapshot = try? await services.placeRepository.getAllPlacesSnapshot()
            let places = snapshot as? [Place] ?? []
            availablePlaces = places.sorted {
                $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending
            }
        }
    }

    func addWaypoint(_ routeId: Int64, lat: Double, lon: Double, name: String) {
        guard RouteMetricsCalculator.shared.isValidCoordinate(lat: lat, lon: lon) else { return }
        Task {
            guard let route = try? await services.routeRepository.getRouteById(id: routeId) else { return }
            let waypoint = RoutePoint(latitude: lat, longitude: lon, name: name.isEmpty ? nil : name)
            guard !RouteMetricsCalculator.shared.isDuplicate(point: waypoint, route: route) else { return }
            let updated = RouteMetricsCalculator.shared.withAppendedWaypoint(route: route, waypoint: waypoint)
            try? await services.routeRepository.updateRoute(route: await enrichRouteMetrics(updated))
            await loadRoutes()
        }
    }

    func removeWaypointAt(_ routeId: Int64, index: Int) {
        Task {
            guard let route = try? await services.routeRepository.getRouteById(id: routeId) else { return }
            let waypoints = route.waypoints as? [RoutePoint] ?? []
            guard waypoints.indices.contains(index) else { return }
            var updatedWaypoints = waypoints
            updatedWaypoints.remove(at: index)
            let updated = route.doCopy(
                id: route.id,
                name: route.name,
                description: route.description_,
                startPoint: route.startPoint,
                endPoint: route.endPoint,
                waypoints: updatedWaypoints,
                distance: route.distance,
                estimatedDuration: route.estimatedDuration,
                isFavorite: route.isFavorite,
                createdAt: route.createdAt,
                updatedAt: currentTimeMillis()
            )
            try? await services.routeRepository.updateRoute(route: await enrichRouteMetrics(updated))
            await loadRoutes()
        }
    }

    private func enrichRouteMetrics(_ route: Route) async -> Route {
        let points = buildRoutePoints(route)
        guard points.count >= 2 else {
            return RouteMetricsCalculator.shared.recompute(route: route)
        }

        let result = try? await services.walkingDirectionsProvider.fetchWalkingRouteResult(waypoints: points)
        guard let result else {
            return RouteMetricsCalculator.shared.recompute(route: route)
        }

        let resolvedDistance = result.distanceMeters?.doubleValue ?? NativeGeoEngine.shared.polylineDistanceMeters(points: result.geometry)
        let resolvedDurationSeconds = result.durationSeconds?.doubleValue
        return RouteMetricsCalculator.shared.withMetrics(
            route: route,
            distanceMeters: resolvedDistance,
            durationSeconds: resolvedDurationSeconds.map { KotlinDouble(value: $0) }
        )
    }

    private func buildRoutePoints(_ route: Route) -> [RoutePoint] {
        var points = [route.startPoint]
        if let waypoints = route.waypoints as? [RoutePoint] {
            points.append(contentsOf: waypoints)
        }
        points.append(route.endPoint)
        return points
    }

    private func refreshActiveRouteState() async {
        let pendingRouteId = (try? await services.userPreferenceRepository.getString(key: "map.pending.activeRouteId", defaultValue: "")) ?? ""
        let activeRouteValue = (try? await services.userPreferenceRepository.getString(key: "map.activeRouteId", defaultValue: "")) ?? ""
        activeRouteId = Int64(pendingRouteId) ?? Int64(activeRouteValue)
    }

    private func currentTimeMillis() -> Int64 {
        Int64(Date().timeIntervalSince1970 * 1000)
    }
}
