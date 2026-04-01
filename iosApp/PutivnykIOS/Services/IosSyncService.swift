import Foundation
import BackgroundTasks
import PutivnykShared

/// iOS sync orchestration — fetches events from backend, exports offline cache,
/// and schedules periodic background refresh via `BGTaskScheduler`.
@MainActor
final class IosSyncService: ObservableObject {
    static let shared = IosSyncService()

    /// BGTask identifiers (must match Info.plist `BGTaskSchedulerPermittedIdentifiers`).
    static let eventsSyncTaskId = "ua.kyiv.putivnyk.ios.sync.events"
    static let offlineCacheSyncTaskId = "ua.kyiv.putivnyk.ios.sync.offline-cache"

    @Published var isSyncing = false

    private let telemetry = IosAppTelemetry.shared
    private var services: AppServices { AppServices.shared }

    private init() {}

    // MARK: - Public API

    /// Run all sync tasks sequentially. Called on app launch and from background refresh.
    func syncAll() async {
        guard !isSyncing else { return }
        isSyncing = true
        defer { isSyncing = false }

        await syncEvents()
        await syncOfflineCache()
    }

    /// Fetch events from the backend API.
    func syncEvents() async {
        let entity = "events_backend"
        do {
            try await services.syncStateRepository.setRunning(entityName: entity)
            let lang = await resolvePreferredLanguage()
            _ = try await services.eventsRepository.getEvents(language: lang)
            try await services.syncStateRepository.setSuccess(entityName: entity, syncedAt: currentTimeMillis())
            telemetry.trackEvent("events_sync_success")
        } catch {
            try? await services.syncStateRepository.setError(entityName: entity, message: error.localizedDescription)
            telemetry.trackWarning("events_sync_failed", error: error)
        }
    }

    /// Export places, routes, and preferences to offline JSON cache.
    func syncOfflineCache() async {
        let entity = "offline_cache"
        do {
            try await services.syncStateRepository.setRunning(entityName: entity)

            let fs = services.platformServices.fileSystemProvider()
            let cacheDir = "offline_cache"
            fs.ensureDirectory(path: cacheDir)

            let allPlaces = try await services.placeRepository.getAllPlacesSnapshot()
            let allRoutes = try await services.routeRepository.getAllRoutesSnapshot()

            fs.writeText(path: "\(cacheDir)/places.json",
                         text: OfflineCacheSnapshotCodec.shared.encodePlaces(places: allPlaces))
            fs.writeText(path: "\(cacheDir)/routes.json",
                         text: OfflineCacheSnapshotCodec.shared.encodeRoutes(routes: allRoutes))

            let rawPrefs = try await services.userPreferenceRepository.getAllAsMap()
            var prefs: [String: String] = [:]
            for (key, value) in rawPrefs {
                if let k = key as? String, let v = value as? String {
                    prefs[k] = v
                }
            }
            fs.writeText(path: "\(cacheDir)/preferences.json",
                         text: OfflineCacheSnapshotCodec.shared.encodePreferences(prefs: prefs))

            try await services.syncStateRepository.setSuccess(entityName: entity, syncedAt: currentTimeMillis())
            telemetry.trackEvent("offline_cache_sync_success", attributes: [
                "places": "\(allPlaces.count)",
                "routes": "\(allRoutes.count)"
            ])
        } catch {
            try? await services.syncStateRepository.setError(entityName: entity, message: error.localizedDescription)
            telemetry.trackWarning("offline_cache_sync_failed", error: error)
        }
    }

    // MARK: - Background Tasks

    /// Register BGTask handlers. Call once from `application(_:didFinishLaunchingWithOptions:)`.
    func registerBackgroundTasks() {
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: Self.eventsSyncTaskId,
            using: nil
        ) { [weak self] task in
            guard let bgTask = task as? BGAppRefreshTask else { return }
            Task { @MainActor in
                await self?.syncEvents()
                bgTask.setTaskCompleted(success: true)
            }
            self?.scheduleEventsRefresh()
        }

        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: Self.offlineCacheSyncTaskId,
            using: nil
        ) { [weak self] task in
            guard let bgTask = task as? BGAppRefreshTask else { return }
            Task { @MainActor in
                await self?.syncOfflineCache()
                bgTask.setTaskCompleted(success: true)
            }
            self?.scheduleOfflineCacheRefresh()
        }
    }

    /// Schedule periodic background refresh for events (every ~6 hours).
    func scheduleEventsRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: Self.eventsSyncTaskId)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 6 * 60 * 60)
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            telemetry.trackWarning("bg_events_schedule_failed", error: error)
        }
    }

    /// Schedule periodic background refresh for offline cache (every ~4 hours).
    func scheduleOfflineCacheRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: Self.offlineCacheSyncTaskId)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 4 * 60 * 60)
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            telemetry.trackWarning("bg_offline_cache_schedule_failed", error: error)
        }
    }

    // MARK: - Helpers

    private func resolvePreferredLanguage() async -> String {
        let prefs = services.userPreferenceRepository
        let mode = (try? await prefs.getString(key: "ui.lang.mode", defaultValue: "auto")) ?? "auto"
        let manual = (try? await prefs.getString(key: "ui.lang.manual", defaultValue: "uk")) ?? "uk"
        if mode == "manual" && !manual.isEmpty {
            return manual
        }
        return Locale.current.language.languageCode?.identifier ?? "uk"
    }

    private func currentTimeMillis() -> Int64 {
        Int64(Date().timeIntervalSince1970 * 1000)
    }
}
