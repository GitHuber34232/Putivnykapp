import SwiftUI
import MapKit
import PutivnykShared

struct MapView: View {
    @EnvironmentObject var localization: UiLocalizationViewModel
    @StateObject private var viewModel = MapViewModel()
    @StateObject private var locationService = LocationService()
    @State private var mapPosition: MapCameraPosition = .region(
        MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 50.4501, longitude: 30.5234),
            span: MKCoordinateSpan(latitudeDelta: 0.05, longitudeDelta: 0.05)
        )
    )
    @State private var showFilters = false
    @State private var showBookmarks = false
    @State private var showRouteInfo = false

    var onOpenPlaceDetails: (Int64) -> Void = { _ in }

    private var texts: [String: String] { localization.uiTexts }

    var body: some View {
        ZStack(alignment: .top) {
            mapLayer

            VStack(spacing: 0) {
                topControls
                Spacer()
                if let route = viewModel.activeRoute {
                    routeProgressBar(route)
                }
                bottomPlaceCards
            }
        }
        .onReceive(locationService.$userLocation) { loc in
            if let loc {
                viewModel.updateUserLocation(loc.latitude, loc.longitude,
                                             bearing: Float(locationService.userBearing),
                                             accuracy: locationService.userAccuracy,
                                             speedMetersPerSecond: locationService.userSpeedMetersPerSecond)
            }
        }
        .onAppear { locationService.requestPermission() }
        .sheet(isPresented: $showFilters) { filterSheet }
        .sheet(isPresented: $showBookmarks) { bookmarkSheet }
        .alert(tr("map.add_to_route_title", fallback: "Додати до маршруту?", texts: texts),
               isPresented: Binding(get: { viewModel.pendingRouteAddPlace != nil },
                                    set: { if !$0 { viewModel.dismissAddToRouteDialog() } })) {
            Button(tr("common.add", fallback: "Додати", texts: texts)) { viewModel.confirmAddToActiveRoute() }
            Button(tr("home.cancel", fallback: "Скасувати", texts: texts), role: .cancel) { viewModel.dismissAddToRouteDialog() }
        } message: {
            if let place = viewModel.pendingRouteAddPlace {
                Text("\(place.name)")
            }
        }
        .alert(tr("map.poi_nearby", fallback: "Цікаве поруч!", texts: texts),
               isPresented: Binding(get: { viewModel.poiPromptPlace != nil },
                                    set: { if !$0 { viewModel.dismissPoiPrompt() } })) {
            Button(tr("map.visit", fallback: "Відвідати", texts: texts)) { viewModel.acceptPoiVisit() }
            Button(tr("map.skip", fallback: "Пропустити", texts: texts), role: .cancel) { viewModel.dismissPoiPrompt() }
        } message: {
            if let poi = viewModel.poiPromptPlace {
                Text(poi.name)
            }
        }
    }

    // MARK: - Map Layer

    @ViewBuilder
    private var mapLayer: some View {
        Map(position: $mapPosition) {
            // User location
            if viewModel.hasUserLocation {
                Annotation("", coordinate: CLLocationCoordinate2D(
                    latitude: viewModel.userLocation.latitude,
                    longitude: viewModel.userLocation.longitude
                )) {
                    Circle()
                        .fill(.blue)
                        .frame(width: 14, height: 14)
                        .overlay(Circle().stroke(.white, lineWidth: 2))
                }
            }

            // Place markers
            ForEach(viewModel.placesOnMap, id: \.id) { place in
                Annotation(place.name, coordinate: CLLocationCoordinate2D(
                    latitude: place.latitude,
                    longitude: place.longitude
                )) {
                    Button {
                        viewModel.selectPlace(place)
                    } label: {
                        Image(systemName: "mappin.circle.fill")
                            .font(.title2)
                            .foregroundStyle(markerColor(for: place.category))
                    }
                }
            }

            // Route polyline
            if !viewModel.routeLinePoints.isEmpty {
                let coords = viewModel.routeLinePoints.map {
                    CLLocationCoordinate2D(latitude: $0.latitude, longitude: $0.longitude)
                }
                MapPolyline(coordinates: coords)
                    .stroke(.blue, lineWidth: 4)
            }

            // Route waypoints
            ForEach(Array(viewModel.routeWaypoints.enumerated()), id: \.offset) { idx, wp in
                Annotation(wp.name ?? "\(idx + 1)", coordinate: CLLocationCoordinate2D(
                    latitude: wp.latitude, longitude: wp.longitude
                )) {
                    ZStack {
                        Circle()
                            .fill(idx <= viewModel.currentWaypointIndex ? .green : .orange)
                            .frame(width: 20, height: 20)
                        Text("\(idx + 1)")
                            .font(.caption2)
                            .fontWeight(.bold)
                            .foregroundStyle(.white)
                    }
                }
            }

            // Bookmarks
            ForEach(viewModel.bookmarks, id: \.id) { bookmark in
                Annotation(bookmark.title, coordinate: CLLocationCoordinate2D(
                    latitude: bookmark.latitude, longitude: bookmark.longitude
                )) {
                    Image(systemName: "bookmark.fill")
                        .foregroundStyle(.purple)
                }
            }
        }
        .mapStyle(.standard(elevation: .realistic))
        .ignoresSafeArea(edges: .top)
        .onMapCameraChange { context in
            let center = context.region.center
            viewModel.updateMapCenter(center.latitude, center.longitude)
        }
    }

    // MARK: - Top Controls

    @ViewBuilder
    private var topControls: some View {
        HStack(spacing: 10) {
            Button { showFilters = true } label: {
                Image(systemName: "line.3.horizontal.decrease.circle")
                    .font(.title3)
            }
            .liquidGlass()

            Spacer()

            Button { viewModel.centerOnUserLocation() } label: {
                Image(systemName: "location.fill")
                    .font(.title3)
            }
            .liquidGlass()

            Button { viewModel.centerOnKyiv() } label: {
                Image(systemName: "building.2")
                    .font(.title3)
            }
            .liquidGlass()

            Button { showBookmarks = true } label: {
                Image(systemName: "bookmark")
                    .font(.title3)
            }
            .liquidGlass()

            if viewModel.activeRoute != nil {
                Button { viewModel.deactivateRoute() } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.title3)
                        .foregroundStyle(.red)
                }
                .liquidGlass()
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 60)
    }

    // MARK: - Route Progress Bar

    @ViewBuilder
    private func routeProgressBar(_ route: Route) -> some View {
        VStack(spacing: 6) {
            if viewModel.isRerouting {
                HStack {
                    ProgressView()
                        .scaleEffect(0.8)
                    Text(tr("map.rerouting", fallback: "Перебудова маршруту…", texts: texts))
                        .font(.caption)
                }
            }

            ProgressView(value: viewModel.routeProgressFraction)
                .progressViewStyle(.linear)
                .padding(.horizontal, 16)

            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(route.name)
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .lineLimit(1)
                    if let nextName = viewModel.nextWaypointName {
                        Text("→ \(nextName) • \(formatRouteDistance(viewModel.distanceToNextWaypoint))")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    Text(formatRouteDistance(viewModel.remainingDistance))
                        .font(.subheadline)
                        .fontWeight(.medium)
                    Text(formatRouteDuration(viewModel.remainingMinutes))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .liquidGlassCard()
            .padding(.horizontal, 16)

            HStack(spacing: 12) {
                Button(tr("map.skip_waypoint", fallback: "Пропустити", texts: texts)) {
                    viewModel.skipWaypoint()
                }
                .buttonStyle(.bordered)
                .tint(.orange)
            }
            .padding(.bottom, 4)
        }
    }

    // MARK: - Bottom Place Cards

    @ViewBuilder
    private var bottomPlaceCards: some View {
        if !viewModel.placesOnMap.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: 10) {
                    ForEach(viewModel.placesOnMap.prefix(20), id: \.id) { place in
                        bottomCard(place)
                    }
                }
                .padding(.horizontal, 16)
            }
            .frame(height: 112)
            .padding(.bottom, 8)
        }
    }

    @ViewBuilder
    private func bottomCard(_ place: Place) -> some View {
        Button {
            if viewModel.selectedPlace?.id == place.id {
                onOpenPlaceDetails(place.id)
            } else {
                viewModel.selectPlace(place)
            }
        } label: {
            VStack(alignment: .leading, spacing: 4) {
                Text(place.name)
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .lineLimit(1)
                Text("\(place.category.icon) \(trCategory(place.category, texts: texts))")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                if let rating = place.rating?.doubleValue {
                    HStack(spacing: 2) {
                        Image(systemName: "star.fill")
                            .font(.caption2)
                            .foregroundStyle(.yellow)
                        Text(String(format: "%.1f", rating))
                            .font(.caption2)
                    }
                }
                if viewModel.activeRoute != nil {
                    Button {
                        viewModel.requestAddToActiveRoute(place)
                    } label: {
                        Text(tr("map.add_to_route", fallback: "+ Маршрут", texts: texts))
                            .font(.caption2)
                    }
                    .buttonStyle(.bordered)
                    .tint(.blue)
                }
            }
            .frame(width: 160)
            .padding(10)
            .liquidGlassCard()
        }
        .buttonStyle(.plain)
    }

    // MARK: - Filter Sheet

    @ViewBuilder
    private var filterSheet: some View {
        NavigationStack {
            Form {
                Section(tr("map.categories", fallback: "Категорії", texts: texts)) {
                    Button(tr("map.all", fallback: "Всі", texts: texts)) {
                        viewModel.selectCategory(nil)
                    }
                    ForEach(PlaceCategory.entries.filter { $0 != .toilet }, id: \.name) { cat in
                        Button {
                            viewModel.selectCategory(cat)
                        } label: {
                            HStack {
                                Text("\(cat.icon) \(trCategory(cat, texts: texts))")
                                Spacer()
                                if viewModel.selectedCategory == cat {
                                    Image(systemName: "checkmark")
                                }
                            }
                        }
                    }
                }

                Section(tr("map.sort", fallback: "Сортування", texts: texts)) {
                    ForEach(PlaceSortMode.entries, id: \.name) { mode in
                        Button {
                            viewModel.setSortMode(mode)
                        } label: {
                            HStack {
                                Text(trSortMode(mode, texts: texts))
                                Spacer()
                                if viewModel.sortMode == mode {
                                    Image(systemName: "checkmark")
                                }
                            }
                        }
                    }
                }

                Section {
                    Toggle(tr("routes.only_favorites", fallback: "Тільки улюблені", texts: texts),
                           isOn: Binding(
                            get: { viewModel.showOnlyFavorites },
                            set: { _ in viewModel.toggleShowOnlyFavorites() }
                           ))
                }
            }
            .navigationTitle(tr("map.filters", fallback: "Фільтри", texts: texts))
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(tr("common.close", fallback: "Закрити", texts: texts)) {
                        showFilters = false
                    }
                }
            }
        }
    }

    // MARK: - Bookmarks Sheet

    @ViewBuilder
    private var bookmarkSheet: some View {
        NavigationStack {
            List {
                Button {
                    viewModel.saveCurrentViewAsBookmark()
                } label: {
                    Label(tr("map.save_bookmark", fallback: "Зберегти поточну позицію", texts: texts), systemImage: "plus")
                }

                ForEach(viewModel.bookmarks, id: \.id) { bookmark in
                    Button {
                        viewModel.applyBookmark(bookmark)
                        showBookmarks = false
                    } label: {
                        VStack(alignment: .leading) {
                            Text(bookmark.title)
                                .font(.subheadline)
                            Text(bookmark.note ?? "")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .swipeActions {
                        Button(role: .destructive) {
                            viewModel.removeBookmark(bookmark)
                        } label: {
                            Image(systemName: "trash")
                        }
                    }
                }

                if !viewModel.bookmarks.isEmpty {
                    Button(role: .destructive) {
                        viewModel.clearAllBookmarks()
                    } label: {
                        Label(tr("map.clear_bookmarks", fallback: "Видалити всі закладки", texts: texts), systemImage: "trash")
                    }
                }
            }
            .navigationTitle(tr("map.bookmarks", fallback: "Закладки", texts: texts))
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(tr("common.close", fallback: "Закрити", texts: texts)) {
                        showBookmarks = false
                    }
                }
            }
        }
    }

    // MARK: - Helpers

    private func markerColor(for category: PlaceCategory) -> Color {
        switch category {
        case .museum: return .orange
        case .theater: return .purple
        case .restaurant: return .red
        case .park: return .green
        case .monument: return .gray
        case .church: return .brown
        case .toilet: return .cyan
        default: return .blue
        }
    }
}
