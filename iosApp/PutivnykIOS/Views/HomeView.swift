import SwiftUI
import PutivnykShared

struct HomeView: View {
    @EnvironmentObject var localization: UiLocalizationViewModel
    @StateObject private var viewModel = HomeViewModel()
    @State private var showWelcome = false
    @State private var showAddPinnedDialog = false
    @State private var pinnedSearchText = ""
    var onOpenMap: () -> Void = {}

    private var texts: [String: String] { localization.uiTexts }

    var body: some View {
        NavigationStack {
            Group {
                if !viewModel.isLoaded {
                    LoadingStateView(message: tr("home.loading_reco", fallback: "Формуємо персональні рекомендації…", texts: texts))
                } else {
                    scrollContent
                }
            }
            .navigationTitle(tr("nav.home", fallback: "Головна", texts: texts))
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        showAddPinnedDialog = true
                        viewModel.loadAvailablePlacesForPinning()
                    } label: {
                        Image(systemName: "plus")
                    }
                }
            }
            .sheet(isPresented: $showAddPinnedDialog) {
                addPinnedSheet
            }
        }
    }

    @ViewBuilder
    private var scrollContent: some View {
        List {
            if showWelcome {
                Section {
                    Text(tr("home.welcome", fallback: "Вітаємо!", texts: texts))
                        .font(.title)
                        .fontWeight(.bold)
                        .listRowBackground(Color.clear)
                }
            }

            if !viewModel.pinnedPlaces.isEmpty {
                Section(header: Text(tr("home.my_places", fallback: "Мої місця", texts: texts))) {
                    ForEach(viewModel.pinnedPlaces, id: \.id) { place in
                        PinnedPlaceRow(
                            place: place,
                            texts: texts,
                            onClick: {
                                viewModel.requestOpenMapForPlace(place)
                                onOpenMap()
                            },
                            onUnpin: { viewModel.unpinPlace(place.id) },
                            onAddToFavorites: { viewModel.toggleFavorite(place.id) }
                        )
                    }
                }
            }

            Section(header: Text(tr("home.recommended", fallback: "Рекомендовано для вас", texts: texts))) {
                if viewModel.recommendedPlaces.isEmpty {
                    EmptyStateView(
                        systemImage: "star",
                        title: tr("home.no_reco", fallback: "Немає рекомендацій", texts: texts),
                        subtitle: tr("home.no_reco_hint", fallback: "Додайте улюблені місця, щоб покращити добірку", texts: texts)
                    )
                    .frame(height: 200)
                } else {
                    ForEach(viewModel.recommendedPlaces, id: \.id) { place in
                        RecommendationRow(
                            place: place,
                            texts: texts,
                            onClick: {
                                viewModel.requestOpenMapForPlace(place)
                                onOpenMap()
                            },
                            onPin: { viewModel.pinPlace(place.id) },
                            onAddToFavorites: { viewModel.toggleFavorite(place.id) }
                        )
                    }

                    if viewModel.hasMoreRecommendations {
                        Button(tr("home.load_more", fallback: "Завантажити ще рекомендації", texts: texts)) {
                            viewModel.loadMoreRecommendations()
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .onAppear { showWelcome = true }
    }

    @ViewBuilder
    private var addPinnedSheet: some View {
        NavigationStack {
            let filteredPlaces = viewModel.availablePlacesForPinning.filter { place in
                pinnedSearchText.isEmpty ||
                place.name.localizedCaseInsensitiveContains(pinnedSearchText)
            }.prefix(20)

            List {
                ForEach(Array(filteredPlaces), id: \.id) { place in
                    Button {
                        viewModel.pinPlace(place.id)
                        showAddPinnedDialog = false
                    } label: {
                        HStack {
                            Text(place.name)
                                .lineLimit(1)
                            Spacer()
                            Text(trCategory(place.category, texts: texts))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
            .searchable(text: $pinnedSearchText, prompt: tr("places.search", fallback: "Пошук місць...", texts: texts))
            .navigationTitle(tr("home.add_place", fallback: "Додати місце", texts: texts))
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(tr("home.cancel", fallback: "Скасувати", texts: texts)) {
                        showAddPinnedDialog = false
                    }
                }
            }
        }
    }
}

// MARK: - Row Components

private struct PinnedPlaceRow: View {
    let place: PutivnykShared.Place
    let texts: [String: String]
    var onClick: () -> Void
    var onUnpin: () -> Void
    var onAddToFavorites: () -> Void

    var body: some View {
        Button(action: onClick) {
            HStack(spacing: 10) {
                Image(systemName: "pin.fill")
                    .font(.caption)
                    .foregroundStyle(.blue)
                VStack(alignment: .leading, spacing: 2) {
                    Text(place.name)
                        .font(.subheadline)
                        .fontWeight(.medium)
                    Text("\(trCategory(place.category, texts: texts)) • ★ \(String(format: "%.1f", place.rating?.doubleValue ?? 0))")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
            }
        }
        .buttonStyle(.plain)
        .swipeActions(edge: .trailing) {
            Button(role: .destructive, action: onUnpin) {
                Label(tr("home.unpin", fallback: "Відкріпити", texts: texts), systemImage: "pin.slash")
            }
            Button(action: onAddToFavorites) {
                Label(tr("home.add_favorite", fallback: "В улюблені", texts: texts), systemImage: "heart")
            }
            .tint(.pink)
        }
        .liquidGlassCard()
    }
}

private struct RecommendationRow: View {
    let place: PutivnykShared.Place
    let texts: [String: String]
    var onClick: () -> Void
    var onPin: () -> Void
    var onAddToFavorites: () -> Void

    var body: some View {
        Button(action: onClick) {
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Image(systemName: "star.fill")
                        .font(.caption)
                        .foregroundStyle(.yellow)
                    Text(place.name)
                        .font(.subheadline)
                        .fontWeight(.medium)
                }
                Text("\(trCategory(place.category, texts: texts)) • ★ \(String(format: "%.1f", place.rating?.doubleValue ?? 0)) • 🔥 \(place.popularity)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                if let desc = place.description_, !desc.isEmpty {
                    Text(desc)
                        .font(.caption)
                        .lineLimit(2)
                        .foregroundStyle(.primary)
                }
            }
        }
        .buttonStyle(.plain)
        .swipeActions(edge: .trailing) {
            Button(action: onPin) {
                Label(tr("home.pin_place", fallback: "Закріпити", texts: texts), systemImage: "pin")
            }
            .tint(.blue)
            Button(action: onAddToFavorites) {
                Label(tr("home.add_favorite", fallback: "В улюблені", texts: texts), systemImage: "heart")
            }
            .tint(.pink)
        }
        .liquidGlassCard()
    }
}
