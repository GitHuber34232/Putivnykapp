import SwiftUI
import PutivnykShared

struct FavoritesView: View {
    @EnvironmentObject var localization: UiLocalizationViewModel
    @StateObject private var viewModel = PlacesViewModel()
    var onOpenPlaceDetails: (Int64) -> Void = { _ in }

    private var texts: [String: String] { localization.uiTexts }

    var body: some View {
        NavigationStack {
            Group {
                if !viewModel.isLoaded {
                    LoadingStateView()
                } else if viewModel.favoritePlaces.isEmpty {
                    EmptyStateView(
                        systemImage: "heart",
                        title: tr("favorites.empty", fallback: "Немає улюблених місць", texts: texts),
                        subtitle: tr("favorites.empty_hint", fallback: "Додайте місця в улюблені зі списку або карти", texts: texts)
                    )
                } else {
                    List(viewModel.favoritePlaces, id: \.id) { place in
                        PlaceCardView(
                            place: place,
                            texts: texts,
                            onFavoriteClick: { viewModel.toggleFavorite(place.id) },
                            onVisitedClick: { viewModel.toggleVisited(place.id) },
                            onClick: { onOpenPlaceDetails(place.id) }
                        )
                        .listRowSeparator(.hidden)
                        .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle(tr("favorites.title", fallback: "Улюблені місця", texts: texts))
        }
    }
}
