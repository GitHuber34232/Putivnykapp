import SwiftUI
import PutivnykShared

struct EventsView: View {
    @EnvironmentObject var localization: UiLocalizationViewModel
    @StateObject private var viewModel = EventsViewModel()
    @State private var selectedEvent: EventItem?
    var onOpenMap: () -> Void = {}

    private var texts: [String: String] { localization.uiTexts }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                categoryAndSortChips

                if viewModel.isLoading && viewModel.events.isEmpty {
                    LoadingStateView(message: tr("events.loading", fallback: "Завантаження подій…", texts: texts))
                } else if viewModel.events.isEmpty {
                    EmptyStateView(
                        systemImage: "magnifyingglass",
                        title: tr("events.empty", fallback: "Події за обраними фільтрами відсутні", texts: texts)
                    )
                } else {
                    List(viewModel.events, id: \.id) { event in
                        EventCardRow(event: event, texts: texts) {
                            selectedEvent = event
                        }
                        .listRowSeparator(.hidden)
                        .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle(tr("events.title", fallback: "Події та афіша", texts: texts))
            .searchable(text: Binding(
                get: { viewModel.searchQuery },
                set: { viewModel.updateSearchQuery($0) }
            ), prompt: tr("events.search", fallback: "Пошук подій", texts: texts))
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button { viewModel.refresh() } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                }
            }
            .sheet(item: $selectedEvent) { event in
                eventDetailSheet(event)
            }
        }
    }

    // MARK: - Chips

    @ViewBuilder
    private var categoryAndSortChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                chipButton(
                    label: tr("map.all", fallback: "Всі", texts: texts),
                    isSelected: viewModel.selectedCategory == nil,
                    action: { viewModel.setCategory(nil) }
                )
                ForEach(viewModel.availableCategories, id: \.self) { cat in
                    chipButton(
                        label: cat,
                        isSelected: viewModel.selectedCategory == cat,
                        action: { viewModel.setCategory(cat) }
                    )
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
        }

        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(EventsViewModel.SortMode.allCases, id: \.rawValue) { mode in
                    let label = switch mode {
                    case .date: tr("events.sort_date", fallback: "За датою", texts: texts)
                    case .title: tr("events.sort_title", fallback: "За назвою", texts: texts)
                    case .category: tr("events.sort_category", fallback: "За категорією", texts: texts)
                    }
                    chipButton(
                        label: label,
                        isSelected: viewModel.sortMode == mode,
                        action: { viewModel.setSortMode(mode) }
                    )
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 8)
        }
    }

    // MARK: - Event Detail Sheet

    @ViewBuilder
    private func eventDetailSheet(_ event: EventItem) -> some View {
        NavigationStack {
            List {
                Section {
                    Text(event.description_.isEmpty
                         ? tr("details.no_description", fallback: "Опис відсутній", texts: texts)
                         : event.description_)
                }

                Section {
                    LabeledContent(tr("events.place", fallback: "Місце", texts: texts),
                                   value: event.locationName.isEmpty
                                   ? tr("events.no_location", fallback: "Локація не вказана", texts: texts)
                                   : event.locationName)
                    LabeledContent(tr("events.starts", fallback: "Початок", texts: texts),
                                   value: event.startsAt.isEmpty
                                   ? tr("events.time_tbd", fallback: "Час уточнюється", texts: texts)
                                   : event.startsAt)
                    LabeledContent(tr("events.ends", fallback: "Завершення", texts: texts),
                                   value: event.endsAt.isEmpty
                                   ? tr("events.time_tbd", fallback: "Час уточнюється", texts: texts)
                                   : event.endsAt)
                    LabeledContent(tr("events.price", fallback: "Ціна", texts: texts),
                                   value: event.priceLabel)
                }

                if event.latitude != nil && event.longitude != nil {
                    Section {
                        Button {
                            viewModel.openEventOnMap(event)
                            selectedEvent = nil
                            onOpenMap()
                        } label: {
                            Label(tr("events.on_map", fallback: "На карті", texts: texts), systemImage: "map")
                        }
                    }
                }
            }
            .navigationTitle(event.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(tr("common.close", fallback: "Закрити", texts: texts)) {
                        selectedEvent = nil
                    }
                }
            }
        }
    }

    // MARK: - Helpers

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

extension EventItem: @retroactive Identifiable {}

// MARK: - Event Card Row

private struct EventCardRow: View {
    let event: EventItem
    let texts: [String: String]
    var onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            VStack(alignment: .leading, spacing: 6) {
                Text(event.title)
                    .font(.headline)
                    .lineLimit(1)

                Text("\(event.category.isEmpty ? tr("events.item", fallback: "Подія", texts: texts) : event.category) • \(event.startsAt.isEmpty ? tr("events.time_tbd", fallback: "Час уточнюється", texts: texts) : event.startsAt)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)

                Text(event.priceLabel)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)

                Text(event.locationName.isEmpty
                     ? tr("events.location_tbd", fallback: "Локація уточнюється", texts: texts)
                     : event.locationName)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)

                if !event.description_.isEmpty {
                    Text(event.description_)
                        .font(.subheadline)
                        .lineLimit(2)
                }
            }
            .padding(12)
            .liquidGlassCard()
        }
        .buttonStyle(.plain)
    }
}
