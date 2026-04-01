import SwiftUI
import PutivnykShared

@MainActor
final class SharedBootstrapViewModel: ObservableObject {
    @Published var headline: String
    @Published var supportedLanguages: [String]
    @Published var runtimeSummary: [String]

    init(api: PutivnykSharedApi = PutivnykSharedApi()) {
        let languages = api.supportedLanguages().compactMap { $0 as? LanguageInfo }
        let runtime = IosRuntimeVerifier().currentStatus(language: "en")
        headline = "Shared KMP module connected"
        supportedLanguages = languages.map { $0.displayName }
        runtimeSummary = [
            "UI translations loaded: \(runtime.uiTranslationEntries)",
            "Seed places parsed: \(runtime.parsedSeedPlaces)",
            "Persisted places: \(runtime.persistedPlaces)",
            "Persisted routes: \(runtime.persistedRoutes)",
            "Stored language pref: \(runtime.storedLanguagePreference)"
        ]
    }
}

struct ContentView: View {
    @StateObject private var viewModel = SharedBootstrapViewModel()

    var body: some View {
        NavigationStack {
            List {
                Section("Status") {
                    Text(viewModel.headline)
                    Text("Available languages: \(viewModel.supportedLanguages.count)")
                        .foregroundStyle(.secondary)
                }

                Section("Shared data sample") {
                    ForEach(viewModel.supportedLanguages, id: \.self) { language in
                        Text(language)
                    }
                }

                Section("iOS runtime wiring") {
                    ForEach(viewModel.runtimeSummary, id: \.self) { item in
                        Text(item)
                    }
                }
            }
            .navigationTitle("Putivnyk")
        }
    }
}

#Preview {
    ContentView()
}