import SwiftUI
import PutivnykShared

// MARK: - Localization environment

private struct UiTextsKey: EnvironmentKey {
    static let defaultValue: [String: String] = [:]
}

extension EnvironmentValues {
    var uiTexts: [String: String] {
        get { self[UiTextsKey.self] }
        set { self[UiTextsKey.self] = newValue }
    }
}

// MARK: - Translation helper

func tr(_ key: String, fallback: String = "", texts: [String: String]) -> String {
    texts[key] ?? (fallback.isEmpty ? key : fallback)
}

// MARK: - Category translation

func trCategory(_ category: PlaceCategory, texts: [String: String]) -> String {
    let key: String
    switch category {
    case .park: key = "category.park"
    case .museum: key = "category.museum"
    case .theater: key = "category.theater"
    case .restaurant: key = "category.restaurant"
    case .cathedral: key = "category.cathedral"
    case .monastery: key = "category.monastery"
    case .architectureMonument: key = "category.architecture_monument"
    case .square: key = "category.square"
    case .street: key = "category.street"
    case .district: key = "category.district"
    case .stadium: key = "category.stadium"
    case .embankment: key = "category.embankment"
    case .famousPlace: key = "category.famous_place"
    case .toilet: key = "category.toilet"
    case .other: key = "category.other"
    default: key = "category.other"
    }
    return tr(key, fallback: category.displayName, texts: texts)
}

func trRiverBank(_ bank: RiverBank, texts: [String: String]) -> String {
    let key: String
    switch bank {
    case .left: key = "river.left"
    case .right: key = "river.right"
    case .both: key = "river.both"
    case .unknown: key = "river.unknown"
    default: key = "river.unknown"
    }
    return tr(key, texts: texts)
}

func trSortMode(_ mode: PlaceSortMode, texts: [String: String]) -> String {
    let key: String
    switch mode {
    case .popularity: key = "sort.popularity"
    case .rating: key = "sort.rating"
    case .distance: key = "sort.distance"
    case .recommended: key = "sort.recommended"
    default: key = "sort.popularity"
    }
    return tr(key, texts: texts)
}
