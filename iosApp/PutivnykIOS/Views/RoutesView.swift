import SwiftUI
import PutivnykShared

struct RoutesView: View {
    @EnvironmentObject var localization: UiLocalizationViewModel
    @StateObject private var viewModel = RoutesViewModel()
    @State private var selectedRouteDetails: Route?
    @State private var showCreateDialog = false
    @State private var routeToDelete: Route?
    var onNavigateToMap: () -> Void = {}

    private var texts: [String: String] { localization.uiTexts }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if !viewModel.isLoaded {
                    LoadingStateView(message: tr("routes.loading", fallback: "Завантаження маршрутів…", texts: texts))
                } else if viewModel.routes.isEmpty {
                    EmptyStateView(
                        systemImage: "point.topleft.down.to.point.bottomright.curvepath",
                        title: tr("routes.no_saved", fallback: "Немає збережених маршрутів", texts: texts),
                        subtitle: tr("routes.create_hint", fallback: "Натисніть + щоб створити маршрут", texts: texts)
                    )
                } else {
                    List(viewModel.routes, id: \.id) { route in
                        RouteCardView(
                            route: route,
                            texts: texts,
                            isActive: viewModel.activeRouteId == route.id,
                            onFavoriteClick: { viewModel.toggleFavorite(route.id) },
                            onDeleteClick: { routeToDelete = route },
                            onActivateClick: {
                                if viewModel.activeRouteId == route.id {
                                    viewModel.deactivateRoute()
                                } else {
                                    viewModel.activateRouteOnMap(route.id)
                                    onNavigateToMap()
                                }
                            },
                            onReverseClick: { viewModel.reverseRoute(route.id) },
                            onClick: { selectedRouteDetails = route }
                        )
                        .listRowSeparator(.hidden)
                        .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle(tr("routes.title", fallback: "Маршрути", texts: texts))
            .searchable(text: Binding(
                get: { viewModel.searchQuery },
                set: { viewModel.updateSearchQuery($0) }
            ), prompt: tr("routes.search", fallback: "Пошук маршрутів", texts: texts))
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    HStack(spacing: 12) {
                        Button {
                            viewModel.toggleShowOnlyFavorites()
                        } label: {
                            Image(systemName: viewModel.showOnlyFavorites ? "heart.fill" : "heart")
                        }
                        Button {
                            showCreateDialog = true
                        } label: {
                            Image(systemName: "plus")
                        }
                    }
                }
            }
            .alert(tr("routes.delete_title", fallback: "Видалити маршрут?", texts: texts),
                   isPresented: Binding(get: { routeToDelete != nil }, set: { if !$0 { routeToDelete = nil } })) {
                Button(tr("common.delete", fallback: "Видалити", texts: texts), role: .destructive) {
                    if let route = routeToDelete {
                        viewModel.deleteRoute(route.id)
                        routeToDelete = nil
                    }
                }
                Button(tr("home.cancel", fallback: "Скасувати", texts: texts), role: .cancel) {
                    routeToDelete = nil
                }
            } message: {
                if let route = routeToDelete {
                    Text("\(tr("routes.delete_confirm", fallback: "Ви впевнені, що хочете видалити маршрут", texts: texts)) \"\(route.name)\"?")
                }
            }
            .sheet(item: $selectedRouteDetails) { route in
                RouteDetailsSheet(route: route, viewModel: viewModel, texts: texts, onNavigateToMap: onNavigateToMap)
            }
            .sheet(isPresented: $showCreateDialog) {
                CreateRouteSheet(viewModel: viewModel, texts: texts) {
                    showCreateDialog = false
                }
            }
            .overlay {
                if viewModel.isCreatingRoute {
                    Color.black.opacity(0.3)
                        .ignoresSafeArea()
                        .overlay { ProgressView() }
                }
            }
        }
    }
}

extension PutivnykShared.Route: @retroactive Identifiable {}

// MARK: - Route Details Sheet

private struct RouteDetailsSheet: View {
    let route: PutivnykShared.Route
    @ObservedObject var viewModel: RoutesViewModel
    let texts: [String: String]
    var onNavigateToMap: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var renameValue: String
    @State private var waypointName = ""
    @State private var waypointLat = ""
    @State private var waypointLon = ""

    init(route: PutivnykShared.Route, viewModel: RoutesViewModel, texts: [String: String], onNavigateToMap: @escaping () -> Void) {
        self.route = route
        self.viewModel = viewModel
        self.texts = texts
        self.onNavigateToMap = onNavigateToMap
        _renameValue = State(initialValue: route.name)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section(tr("map.route_name", fallback: "Назва маршруту", texts: texts)) {
                    TextField(tr("map.route_name", fallback: "Назва", texts: texts), text: $renameValue)
                }

                Section {
                    Text("\(tr("routes.distance", fallback: "Відстань", texts: texts)): \(formatRouteDistance(route.distance))")
                    Text("\(tr("routes.duration", fallback: "Тривалість", texts: texts)): \(formatRouteDuration(Int(route.estimatedDuration)))")
                    Text("\(tr("routes.points", fallback: "Точок", texts: texts)): \(waypointsList.count + 2)")
                }

                if !waypointsList.isEmpty {
                    Section(tr("routes.waypoints", fallback: "Проміжні точки", texts: texts)) {
                        ForEach(Array(waypointsList.enumerated()), id: \.offset) { index, point in
                            HStack {
                                Text("\(index + 1). \(point.name ?? tr("routes.no_name", fallback: "Без назви", texts: texts))")
                                    .font(.caption)
                                Spacer()
                                Button {
                                    viewModel.removeWaypointAt(route.id, index: index)
                                    dismiss()
                                } label: {
                                    Image(systemName: "xmark.circle.fill")
                                        .foregroundStyle(.red)
                                        .font(.caption)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }

                Section(tr("routes.add_point", fallback: "Додати точку", texts: texts)) {
                    TextField(tr("routes.point_name", fallback: "Назва точки", texts: texts), text: $waypointName)
                    TextField(tr("routes.latitude", fallback: "Широта", texts: texts), text: $waypointLat)
                        .keyboardType(.decimalPad)
                    TextField(tr("routes.longitude", fallback: "Довгота", texts: texts), text: $waypointLon)
                        .keyboardType(.decimalPad)
                    Button(tr("routes.add_point", fallback: "+ Точка", texts: texts)) {
                        if let lat = Double(waypointLat), let lon = Double(waypointLon) {
                            viewModel.addWaypoint(route.id, lat: lat, lon: lon, name: waypointName)
                            dismiss()
                        }
                    }
                }

                Section {
                    if waypointsList.count >= 2 {
                        Button(tr("routes.optimize", fallback: "⚡ Оптимізувати порядок", texts: texts)) {
                            viewModel.optimizeRoute(route.id)
                            dismiss()
                        }
                    }
                    Button(tr("routes.show_on_map", fallback: "🗺️ Показати на карті", texts: texts)) {
                        viewModel.activateRouteOnMap(route.id)
                        dismiss()
                        onNavigateToMap()
                    }
                }
            }
            .navigationTitle(route.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(tr("home.save", fallback: "Зберегти", texts: texts)) {
                        viewModel.renameRoute(route.id, name: renameValue)
                        dismiss()
                    }
                }
                ToolbarItem(placement: .cancellationAction) {
                    Button(tr("common.close", fallback: "Закрити", texts: texts)) { dismiss() }
                }
            }
        }
    }

    private var waypointsList: [RoutePoint] {
        route.waypoints as? [RoutePoint] ?? []
    }
}

// MARK: - Create Route Sheet

private struct CreateRouteSheet: View {
    @ObservedObject var viewModel: RoutesViewModel
    let texts: [String: String]
    var onDismiss: () -> Void
    @State private var routeName = ""
    @State private var selectedPlaceIds: Set<Int64> = []
    @State private var searchText = ""
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                TextField(tr("map.route_name", fallback: "Назва маршруту", texts: texts), text: $routeName)
                    .textFieldStyle(.roundedBorder)
                    .padding()

                let filtered = viewModel.availablePlaces.filter { place in
                    searchText.isEmpty || place.name.localizedCaseInsensitiveContains(searchText)
                }

                List(filtered, id: \.id) { place in
                    Button {
                        if selectedPlaceIds.contains(place.id) {
                            selectedPlaceIds.remove(place.id)
                        } else {
                            selectedPlaceIds.insert(place.id)
                        }
                    } label: {
                        HStack {
                            if selectedPlaceIds.contains(place.id) {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundStyle(.blue)
                            } else {
                                Image(systemName: "circle")
                                    .foregroundStyle(.secondary)
                            }
                            Text(place.name)
                                .lineLimit(1)
                            Spacer()
                            Text(trCategory(place.category, texts: texts))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .buttonStyle(.plain)
                }
                .searchable(text: $searchText, prompt: tr("places.search", fallback: "Пошук місць...", texts: texts))
            }
            .navigationTitle(tr("routes.create", fallback: "Створити маршрут", texts: texts))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(tr("routes.create", fallback: "Створити", texts: texts)) {
                        let selected = viewModel.availablePlaces.filter { selectedPlaceIds.contains($0.id) }
                        viewModel.createRouteFromPlaces(routeName, places: selected)
                        onDismiss()
                    }
                    .disabled(selectedPlaceIds.count < 2)
                }
                ToolbarItem(placement: .cancellationAction) {
                    Button(tr("home.cancel", fallback: "Скасувати", texts: texts)) {
                        onDismiss()
                    }
                }
            }
        }
        .onAppear { viewModel.loadAvailablePlaces() }
    }
}
