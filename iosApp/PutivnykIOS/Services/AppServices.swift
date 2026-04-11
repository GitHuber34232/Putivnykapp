import Foundation
import PutivnykShared

/// Central dependency container for iOS, wrapping shared KMP services.
@MainActor
final class AppServices: ObservableObject {
    static let shared = AppServices()

    let platformServices = IosPlatformServices()
    private let networkFactory = IosNetworkFactory()

    private var eventsBaseUrl: String {
        let configured = (Bundle.main.object(forInfoDictionaryKey: "PUTIVNYK_EVENTS_BASE_URL") as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return configured.isEmpty ? "http://127.0.0.1:3000" : configured
    }

    // Repositories
    lazy var placeRepository: IosPlaceRepository = {
        let fs = platformServices.fileSystemProvider()
        return IosPlaceRepository(fileSystemProvider: fs)
    }()

    lazy var routeRepository: IosRouteRepository = {
        let fs = platformServices.fileSystemProvider()
        return IosRouteRepository(fileSystemProvider: fs)
    }()

    lazy var userPreferenceRepository: IosUserPreferenceRepository = {
        let fs = platformServices.fileSystemProvider()
        return IosUserPreferenceRepository(fileSystemProvider: fs)
    }()

    lazy var localizationRepository: IosLocalizationRepository = {
        let fs = platformServices.fileSystemProvider()
        return IosLocalizationRepository(fileSystemProvider: fs)
    }()

    lazy var syncStateRepository: IosSyncStateRepository = {
        let fs = platformServices.fileSystemProvider()
        return IosSyncStateRepository(fileSystemProvider: fs)
    }()

    lazy var mapBookmarkRepository: IosMapBookmarkRepository = {
        let fs = platformServices.fileSystemProvider()
        return IosMapBookmarkRepository(fileSystemProvider: fs)
    }()

    // Domain use cases
    lazy var routeOptimizer: RouteOptimizer = RouteOptimizer()
    lazy var recommendationEngine: RecommendationEngine = RecommendationEngine()
    lazy var smartRouteBuilder: SmartRouteBuilder = {
        SmartRouteBuilder(routeOptimizer: routeOptimizer)
    }()
    lazy var sharedApi: PutivnykSharedApi = {
        PutivnykSharedApi(
            routeOptimizer: routeOptimizer,
            recommendationEngine: recommendationEngine,
            smartRouteBuilder: smartRouteBuilder
        )
    }()

    // Platform services
    lazy var uiTranslationsProvider: BundleUiTranslationsProvider = {
        platformServices.uiTranslationsProvider() as! BundleUiTranslationsProvider
    }()

    lazy var textResourceLoader: BundleTextResourceLoader = {
        platformServices.textResourceLoader() as! BundleTextResourceLoader
    }()

    lazy var translationService: IosTranslationServiceStub = {
        platformServices.translationService() as! IosTranslationServiceStub
    }()

    // Network
    lazy var walkingDirectionsProvider: KtorWalkingDirectionsProvider = {
        networkFactory.createWalkingDirectionsProvider()
    }()

    lazy var eventsRepository: KtorEventsRepository = {
        networkFactory.createEventsRepository(baseUrl: eventsBaseUrl)
    }()

    // Telemetry & Sync
    let telemetry = IosAppTelemetry.shared
    let syncService = IosSyncService.shared

    private init() {}
}
