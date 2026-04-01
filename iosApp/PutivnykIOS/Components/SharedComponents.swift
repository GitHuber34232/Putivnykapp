import SwiftUI

struct LoadingStateView: View {
    var message: String = "Завантаження…"

    var body: some View {
        VStack(spacing: 12) {
            ProgressView()
            Text(message)
                .font(.body)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

struct EmptyStateView: View {
    var systemImage: String
    var title: String
    var subtitle: String? = nil

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: systemImage)
                .font(.system(size: 56))
                .foregroundStyle(.primary.opacity(0.7))
            Text(title)
                .font(.title3)
                .fontWeight(.medium)
            if let subtitle {
                Text(subtitle)
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding()
    }
}

struct ErrorRetryBanner: View {
    var message: String
    var onRetry: () -> Void

    var body: some View {
        HStack {
            Text(message)
                .font(.callout)
                .foregroundStyle(.red)
                .lineLimit(2)
            Spacer()
            Button("Повторити", action: onRetry)
                .buttonStyle(.bordered)
                .tint(.red)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(.red.opacity(0.1), in: RoundedRectangle(cornerRadius: 12))
        .padding(.horizontal)
    }
}

struct PlaceCardView: View {
    let place: PutivnykShared.Place
    let texts: [String: String]
    var onFavoriteClick: () -> Void = {}
    var onVisitedClick: () -> Void = {}
    var onClick: () -> Void = {}

    var body: some View {
        Button(action: onClick) {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text(place.name)
                        .font(.headline)
                        .lineLimit(1)
                    Spacer()
                    if let rating = place.rating?.doubleValue {
                        HStack(spacing: 2) {
                            Image(systemName: "star.fill")
                                .font(.caption)
                                .foregroundStyle(.yellow)
                            Text(String(format: "%.1f", rating))
                                .font(.caption)
                        }
                    }
                }

                Text("\(place.category.icon) \(trCategory(place.category, texts: texts))")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                if let desc = place.description_, !desc.isEmpty {
                    Text(desc)
                        .font(.subheadline)
                        .lineLimit(2)
                        .foregroundStyle(.primary)
                }

                if let duration = place.visitDuration?.intValue {
                    Text("⏱️ ~\(duration) \(tr("routes.min", fallback: "хв", texts: texts))")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                HStack(spacing: 16) {
                    Button(action: onFavoriteClick) {
                        Image(systemName: place.isFavorite ? "heart.fill" : "heart")
                            .foregroundStyle(place.isFavorite ? .red : .secondary)
                    }
                    Button(action: onVisitedClick) {
                        Image(systemName: place.isVisited ? "checkmark.circle.fill" : "checkmark.circle")
                            .foregroundStyle(place.isVisited ? .green : .secondary)
                    }
                }
                .buttonStyle(.plain)
            }
            .padding(14)
            .liquidGlassCard()
        }
        .buttonStyle(.plain)
    }
}

struct RouteCardView: View {
    let route: PutivnykShared.Route
    let texts: [String: String]
    var isActive: Bool = false
    var onFavoriteClick: () -> Void = {}
    var onDeleteClick: () -> Void = {}
    var onActivateClick: () -> Void = {}
    var onReverseClick: () -> Void = {}
    var onClick: () -> Void = {}

    var body: some View {
        Button(action: onClick) {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    if isActive {
                        Image(systemName: "figure.walk")
                            .foregroundStyle(.green)
                    }
                    Text(route.name)
                        .font(.headline)
                        .lineLimit(1)
                    Spacer()
                    if route.isFavorite {
                        Image(systemName: "heart.fill")
                            .foregroundStyle(.red)
                            .font(.caption)
                    }
                }

                HStack(spacing: 12) {
                    Label(formatRouteDistance(route.distance), systemImage: "arrow.triangle.swap")
                        .font(.caption)
                    Label(formatRouteDuration(Int(route.estimatedDuration)), systemImage: "clock")
                        .font(.caption)
                    Label("\(waypointCount) \(tr("routes.points", fallback: "точок", texts: texts))", systemImage: "mappin")
                        .font(.caption)
                }
                .foregroundStyle(.secondary)

                if let desc = route.description_, !desc.isEmpty {
                    Text(desc)
                        .font(.subheadline)
                        .lineLimit(2)
                        .foregroundStyle(.primary)
                }

                HStack(spacing: 12) {
                    Button(action: onActivateClick) {
                        Label(isActive ? tr("routes.deactivate", fallback: "Зупинити", texts: texts)
                              : tr("routes.show_on_map", fallback: "На карті", texts: texts),
                              systemImage: isActive ? "stop.fill" : "map")
                    }
                    .buttonStyle(.bordered)
                    .tint(isActive ? .red : .blue)

                    Button(action: onReverseClick) {
                        Image(systemName: "arrow.uturn.backward")
                    }
                    .buttonStyle(.bordered)

                    Spacer()

                    Button(action: onFavoriteClick) {
                        Image(systemName: route.isFavorite ? "heart.fill" : "heart")
                    }
                    .buttonStyle(.plain)

                    Button(action: onDeleteClick) {
                        Image(systemName: "trash")
                            .foregroundStyle(.red)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(14)
            .liquidGlassCard()
        }
        .buttonStyle(.plain)
    }

    private var waypointCount: Int {
        (route.waypoints as? [Any])?.count ?? 0 + 2
    }
}

import PutivnykShared
