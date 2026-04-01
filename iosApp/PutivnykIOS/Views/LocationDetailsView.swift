import SwiftUI
import PutivnykShared

struct LocationDetailsView: View {
    @EnvironmentObject var localization: UiLocalizationViewModel
    @StateObject private var viewModel: LocationDetailsViewModel
    @Environment(\.dismiss) private var dismiss
    var onBack: () -> Void = {}
    var onRouteHere: ((Int64) -> Void)?

    private var texts: [String: String] { localization.uiTexts }

    init(placeId: Int64, onBack: @escaping () -> Void = {}, onRouteHere: ((Int64) -> Void)? = nil) {
        _viewModel = StateObject(wrappedValue: LocationDetailsViewModel(placeId: placeId))
        self.onBack = onBack
        self.onRouteHere = onRouteHere
    }

    var body: some View {
        Group {
            if let place = viewModel.place {
                placeContent(place)
            } else if !viewModel.isLoaded {
                LoadingStateView()
            } else {
                EmptyStateView(
                    systemImage: "mappin.slash",
                    title: tr("details.not_found", fallback: "Локацію не знайдено", texts: texts)
                )
            }
        }
        .navigationTitle(viewModel.place?.name ?? tr("details.title", fallback: "Деталі локації", texts: texts))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button { onBack() } label: {
                    Image(systemName: "chevron.left")
                }
            }
        }
    }

    @ViewBuilder
    private func placeContent(_ place: PutivnykShared.Place) -> some View {
        List {
            // Header
            Section {
                VStack(alignment: .leading, spacing: 4) {
                    Text(place.name)
                        .font(.title2)
                        .fontWeight(.bold)
                    Text("\(trCategory(place.category, texts: texts)) • \(trRiverBank(place.riverBank, texts: texts)) • 🔥 \(place.popularity)")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }

            // Photos
            if let urls = place.imageUrls as? [String], !urls.isEmpty {
                Section(tr("details.photo", fallback: "Фото", texts: texts)) {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(urls.prefix(6), id: \.self) { url in
                                AsyncImage(url: URL(string: url)) { image in
                                    image
                                        .resizable()
                                        .scaledToFill()
                                } placeholder: {
                                    Rectangle()
                                        .fill(Color(.systemGray5))
                                        .overlay(ProgressView())
                                }
                                .frame(width: 200, height: 120)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                            }
                        }
                    }
                }
            }

            // Actions
            Section {
                HStack(spacing: 12) {
                    Button {
                        viewModel.toggleFavorite()
                    } label: {
                        Label(
                            place.isFavorite
                                ? tr("favorites.in_list", fallback: "В обраному", texts: texts)
                                : tr("favorites.add", fallback: "Додати в обране", texts: texts),
                            systemImage: place.isFavorite ? "heart.fill" : "heart"
                        )
                    }
                    .buttonStyle(.bordered)
                    .tint(place.isFavorite ? .red : .blue)

                    Button {
                        viewModel.toggleVisited()
                    } label: {
                        Label(
                            tr("details.mark_visited", fallback: "Відвідано", texts: texts),
                            systemImage: place.isVisited ? "checkmark.circle.fill" : "checkmark.circle"
                        )
                    }
                    .buttonStyle(.bordered)
                    .tint(place.isVisited ? .green : .gray)
                }

                HStack(spacing: 12) {
                    Button {
                        viewModel.openOnMap()
                        onBack()
                    } label: {
                        Label(tr("details.open_map", fallback: "На карті", texts: texts), systemImage: "map")
                    }
                    .buttonStyle(.bordered)

                    Button {
                        viewModel.addToActiveRoute()
                    } label: {
                        Label(tr("details.add_active_route", fallback: "До маршруту", texts: texts), systemImage: "plus.circle")
                    }
                    .buttonStyle(.bordered)
                }

                if let routeHere = onRouteHere {
                    Button {
                        routeHere(place.id)
                    } label: {
                        Label(tr("map.route_here", fallback: "Маршрут сюди", texts: texts), systemImage: "location.fill")
                    }
                    .buttonStyle(.borderedProminent)
                }
            }

            // Translation
            Section(tr("details.translate_description", fallback: "Переклад опису", texts: texts)) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(viewModel.availableLanguages.prefix(12), id: \.isoCode) { lang in
                            Button {
                                viewModel.setLanguage(lang.isoCode)
                            } label: {
                                Text(lang.isoCode.uppercased())
                                    .font(.caption)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 5)
                                    .background(viewModel.selectedLanguage == lang.isoCode
                                                ? Color.accentColor.opacity(0.2) : Color(.systemGray6))
                                    .clipShape(Capsule())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }

                if viewModel.isTranslating {
                    ProgressView()
                }

                VStack(alignment: .leading, spacing: 6) {
                    Text("\(tr("details.description", fallback: "Опис", texts: texts)) (\(viewModel.selectedLanguage.uppercased()))")
                        .font(.subheadline)
                        .fontWeight(.medium)
                    Text(viewModel.translatedDescription
                         ?? place.description_
                         ?? tr("details.no_description", fallback: "Опис відсутній", texts: texts))
                        .font(.body)
                }
                .liquidGlassCard()
            }

            // Tags
            if let tags = place.tags as? [String], !tags.isEmpty {
                Section(tr("details.tags", fallback: "Теги", texts: texts)) {
                    ForEach(tags, id: \.self) { tag in
                        Text("• \(tag)")
                            .font(.subheadline)
                    }
                }
            }

            // Coordinates
            Section {
                Text("\(tr("details.coordinates", fallback: "Координати", texts: texts)): \(String(format: "%.5f", place.latitude)), \(String(format: "%.5f", place.longitude))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .listStyle(.insetGrouped)
    }
}
