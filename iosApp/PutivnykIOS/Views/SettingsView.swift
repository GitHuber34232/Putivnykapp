import SwiftUI
import UniformTypeIdentifiers
import PutivnykShared

struct SettingsView: View {
    @EnvironmentObject var localization: UiLocalizationViewModel
    @StateObject private var appExperience = AppExperienceViewModel()
    @StateObject private var tools = SettingsToolsViewModel()
    @State private var showExporter = false
    @State private var showImporter = false
    @State private var exportData: Data?

    private var texts: [String: String] { localization.uiTexts }

    var body: some View {
        NavigationStack {
            List {
                languageSection
                versionSection
                dataToolsSection
                supportSection
            }
            .listStyle(.insetGrouped)
            .navigationTitle(tr("settings.title", fallback: "Налаштування", texts: texts))
        }
    }

    // MARK: - Language Section

    @ViewBuilder
    private var languageSection: some View {
        Section {
            Text(tr("settings.interface_lang", fallback: "Мова інтерфейсу", texts: texts))
                .font(.headline)

            HStack(spacing: 12) {
                chipToggle(
                    label: tr("settings.auto", fallback: "Авто", texts: texts),
                    isSelected: localization.isAutoMode,
                    action: { localization.setAutoMode(true) }
                )
                chipToggle(
                    label: tr("settings.manual", fallback: "Ручний", texts: texts),
                    isSelected: !localization.isAutoMode,
                    action: { localization.setAutoMode(false) }
                )
            }

            FlowLayout(spacing: 8) {
                ForEach(localization.languages, id: \.isoCode) { lang in
                    let isActive = (!localization.isAutoMode && localization.manualLanguage == lang.isoCode) ||
                                   (localization.isAutoMode && localization.effectiveLanguage == lang.isoCode)
                    Button {
                        localization.setAutoMode(false)
                        localization.setManualLanguage(lang.isoCode)
                    } label: {
                        Text("\(flagFor(lang.isoCode)) \(lang.displayName)")
                            .font(.callout)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(isActive ? Color.accentColor.opacity(0.2) : Color(.systemGray6))
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .liquidGlassCard()
    }

    // MARK: - Version Section

    @ViewBuilder
    private var versionSection: some View {
        Section {
            Text("\(tr("settings.version", fallback: "Версія", texts: texts)): \(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0")")
            Text("\(tr("settings.features", fallback: "Функціонал", texts: texts)): \(tr("settings.features_list", fallback: "карта, рекомендації, події, маршрути, локалізація", texts: texts))")
        }
        .liquidGlassCard()
    }

    // MARK: - Data Tools Section

    @ViewBuilder
    private var dataToolsSection: some View {
        Section(header: Text(tr("settings.data_tools", fallback: "Інструменти даних", texts: texts))) {
            Button {
                Task {
                    if let json = await tools.exportRoutes() {
                        exportData = json.data(using: .utf8)
                        showExporter = true
                    }
                }
            } label: {
                Label(tr("settings.export_routes", fallback: "Експорт маршрутів (JSON)", texts: texts), systemImage: "square.and.arrow.up")
            }

            Button {
                showImporter = true
            } label: {
                Label(tr("settings.import_routes", fallback: "Імпорт маршрутів (JSON)", texts: texts), systemImage: "square.and.arrow.down")
            }

            if !tools.transferState.isEmpty {
                Text(tools.transferState)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .liquidGlassCard()
        .fileExporter(
            isPresented: $showExporter,
            document: JSONFileDocument(data: exportData ?? Data()),
            contentType: .json,
            defaultFilename: "putivnyk-routes.json"
        ) { _ in }
        .fileImporter(isPresented: $showImporter, allowedContentTypes: [.json, .plainText]) { result in
            if case .success(let url) = result {
                guard url.startAccessingSecurityScopedResource() else { return }
                defer { url.stopAccessingSecurityScopedResource() }
                if let data = try? Data(contentsOf: url) {
                    tools.importRoutes(from: data)
                }
            }
        }
    }

    // MARK: - Support Section

    @ViewBuilder
    private var supportSection: some View {
        Section(header: Text(tr("settings.help_feedback", fallback: "Підтримка та feedback", texts: texts))) {
            Button {
                let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
                let subject = "Putivnyk feedback"
                let body = "Версія: \(version)\n\nОпишіть проблему або ідею:"
                if let url = URL(string: "mailto:support@putivnyk.app?subject=\(subject.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")&body=\(body.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")") {
                    UIApplication.shared.open(url)
                }
            } label: {
                Label(tr("settings.send_feedback", fallback: "Надіслати відгук", texts: texts), systemImage: "envelope")
            }

            Button {
                appExperience.resetOnboarding()
            } label: {
                Label(tr("settings.reopen_onboarding", fallback: "Показати onboarding знову", texts: texts), systemImage: "arrow.counterclockwise")
            }
        }
        .liquidGlassCard()
    }

    // MARK: - Helpers

    @ViewBuilder
    private func chipToggle(label: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.callout)
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .background(isSelected ? Color.accentColor.opacity(0.2) : Color(.systemGray6))
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }

    private func flagFor(_ isoCode: String) -> String {
        switch isoCode.lowercased() {
        case "uk": return "🇺🇦"
        case "en": return "🇬🇧"
        case "pl": return "🇵🇱"
        case "de": return "🇩🇪"
        case "fr": return "🇫🇷"
        case "es": return "🇪🇸"
        case "it": return "🇮🇹"
        case "pt": return "🇵🇹"
        case "cs": return "🇨🇿"
        case "ja": return "🇯🇵"
        case "ko": return "🇰🇷"
        case "zh": return "🇨🇳"
        default: return "🏳️"
        }
    }
}

// MARK: - JSON File Document for export

struct JSONFileDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    let data: Data

    init(data: Data) { self.data = data }

    init(configuration: ReadConfiguration) throws {
        data = configuration.file.regularFileContents ?? Data()
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}

// MARK: - Flow Layout (for language chips)

struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var width: CGFloat = 0
        var height: CGFloat = 0
        var lineHeight: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if width + size.width > maxWidth && width > 0 {
                height += lineHeight + spacing
                width = 0
                lineHeight = 0
            }
            width += size.width + spacing
            lineHeight = max(lineHeight, size.height)
        }
        height += lineHeight
        return CGSize(width: maxWidth, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX
        var y = bounds.minY
        var lineHeight: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x + size.width > bounds.maxX && x > bounds.minX {
                y += lineHeight + spacing
                x = bounds.minX
                lineHeight = 0
            }
            subview.place(at: CGPoint(x: x, y: y), proposal: .unspecified)
            x += size.width + spacing
            lineHeight = max(lineHeight, size.height)
        }
    }
}
