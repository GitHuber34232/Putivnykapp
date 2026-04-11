import SwiftUI
import PutivnykShared

struct PlacesView: View {
    @EnvironmentObject var localization: UiLocalizationViewModel
    @StateObject private var viewModel = PlacesViewModel()
    var onOpenPlaceDetails: (Int64) -> Void = { _ in }

    private var texts: [String: String] { localization.uiTexts }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                categoryChips
                sortChips

                if !viewModel.isLoaded {
                    LoadingStateView(message: tr("places.loading", fallback: "Завантаження місць…", texts: texts))
                } else if viewModel.places.isEmpty {
                    EmptyStateView(
                        systemImage: "magnifyingglass",
                        title: tr("places.not_found", fallback: "Місця не знайдено", texts: texts)
                    )
                } else {
                    List(viewModel.places, id: \.id) { place in
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
            .navigationTitle(tr("places.title", fallback: "Цікаві місця", texts: texts))
            .searchable(text: Binding(
                get: { viewModel.searchQuery },
                set: { viewModel.updateSearchQuery($0) }
            ), prompt: tr("places.search", fallback: "Пошук місць...", texts: texts))
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        viewModel.toggleShowOnlyFavorites()
                    } label: {
                        Image(systemName: viewModel.showOnlyFavorites ? "heart.fill" : "heart")
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var categoryChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                chipButton(
                    label: tr("map.all", fallback: "Всі", texts: texts),
                    isSelected: viewModel.selectedCategory == nil,
                    action: { viewModel.selectCategory(nil) }
                )

                ForEach(PlaceCategory.filterOptions, id: \.name) { category in
                    chipButton(
                        label: "\(category.icon) \(trCategory(category, texts: texts))",
                        isSelected: viewModel.selectedCategory == category,
                        action: { viewModel.selectCategory(category) }
                    )
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
        }
    }

    @ViewBuilder
    private var sortChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(PlaceSortMode.allModes, id: \.name) { mode in
                    chipButton(
                        label: trSortMode(mode, texts: texts),
                        isSelected: viewModel.sortMode == mode,
                        action: { viewModel.setSortMode(mode) }
                    )
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 8)
        }
    }

    @ViewBuilder
    private func chipButton(label: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.callout)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(isSelected ? Color.accentColor.opacity(0.2) : Color(.systemGray6))
                .foregroundStyle(isSelected ? .primary : .secondary)
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}
