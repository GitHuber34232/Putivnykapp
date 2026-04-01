import SwiftUI
import PutivnykShared
import UniformTypeIdentifiers

@MainActor
final class SettingsToolsViewModel: ObservableObject {
    @Published var transferState = ""

    private let services = AppServices.shared
    private let maxImportBytes = 2 * 1024 * 1024

    func exportRoutes() async -> String? {
        do {
            let json = try await services.routeRepository.exportRoutesJson()
            transferState = "Маршрути підготовлено до експорту"
            return json
        } catch {
            transferState = "Помилка експорту маршрутів"
            return nil
        }
    }

    func importRoutes(from data: Data) {
        guard data.count <= maxImportBytes else {
            transferState = "Файл завеликий для імпорту (макс \(maxImportBytes / (1024 * 1024)) MB)"
            return
        }
        guard let json = String(data: data, encoding: .utf8), !json.isEmpty else {
            transferState = "Не вдалося прочитати файл"
            return
        }
        Task {
            do {
                let count = try await services.routeRepository.importRoutesJson(json: json)
                transferState = "Імпортовано маршрутів: \(count)"
            } catch {
                transferState = "Помилка імпорту маршрутів"
            }
        }
    }
}
