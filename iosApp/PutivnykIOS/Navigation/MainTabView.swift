import SwiftUI
import PutivnykShared

struct MainTabView: View {
    @EnvironmentObject var localization: UiLocalizationViewModel
    @State private var selectedTab: Tab = .home
    @State private var navigationPath = NavigationPath()

    private var texts: [String: String] { localization.uiTexts }

    enum Tab: String, CaseIterable {
        case home, map, routes, events, settings
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            homeTab
            mapTab
            routesTab
            eventsTab
            settingsTab
        }
        .liquidGlass()
    }

    @ViewBuilder
    private var homeTab: some View {
        HomeView(onOpenMap: { selectedTab = .map })
            .tabItem {
                Label(tr("nav.home", fallback: "Головна", texts: texts), systemImage: "house")
            }
            .tag(Tab.home)
    }

    @ViewBuilder
    private var mapTab: some View {
        NavigationStack {
            MapView(onOpenPlaceDetails: { placeId in
                navigationPath.append(PlaceDetailRoute(placeId: placeId))
            })
            .navigationDestination(for: PlaceDetailRoute.self) { route in
                LocationDetailsView(placeId: route.placeId, onBack: {
                    navigationPath.removeLast()
                })
            }
        }
        .tabItem {
            Label(tr("nav.map", fallback: "Карта", texts: texts), systemImage: "map")
        }
        .tag(Tab.map)
    }

    @ViewBuilder
    private var routesTab: some View {
        RoutesView(onNavigateToMap: { selectedTab = .map })
            .tabItem {
                Label(tr("nav.routes", fallback: "Маршрути", texts: texts), systemImage: "point.topleft.down.to.point.bottomright.curvepath")
            }
            .tag(Tab.routes)
    }

    @ViewBuilder
    private var eventsTab: some View {
        EventsView(onOpenMap: { selectedTab = .map })
            .tabItem {
                Label(tr("nav.events", fallback: "Події", texts: texts), systemImage: "calendar")
            }
            .tag(Tab.events)
    }

    @ViewBuilder
    private var settingsTab: some View {
        SettingsView()
            .tabItem {
                Label(tr("nav.settings", fallback: "Налаштування", texts: texts), systemImage: "gearshape")
            }
            .tag(Tab.settings)
    }
}

struct PlaceDetailRoute: Hashable {
    let placeId: Int64
}
