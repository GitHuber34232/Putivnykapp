package ua.kyiv.putivnyk

import kotlinx.coroutines.runBlocking
import ua.kyiv.putivnyk.data.model.Route
import ua.kyiv.putivnyk.data.model.RoutePoint
import ua.kyiv.putivnyk.data.repository.IosLocalizationRepository
import ua.kyiv.putivnyk.data.repository.IosMapBookmarkRepository
import ua.kyiv.putivnyk.data.repository.IosPlaceRepository
import ua.kyiv.putivnyk.data.repository.IosRouteRepository
import ua.kyiv.putivnyk.data.repository.IosSyncStateRepository
import ua.kyiv.putivnyk.data.repository.IosUserPreferenceRepository
import ua.kyiv.putivnyk.data.seed.PlaceSeedParser
import ua.kyiv.putivnyk.i18n.BundleUiTranslationsProvider
import ua.kyiv.putivnyk.platform.io.BundleTextResourceLoader
import ua.kyiv.putivnyk.platform.io.DocumentsFileSystemProvider

data class IosRuntimeStatus(
    val uiTranslationEntries: Int,
    val parsedSeedPlaces: Int,
    val persistedPlaces: Int,
    val persistedRoutes: Int,
    val storedLanguagePreference: String,
)

class IosRuntimeVerifier {
    private val fileSystemProvider = DocumentsFileSystemProvider()
    private val textResourceLoader = BundleTextResourceLoader()
    private val uiTranslationsProvider = BundleUiTranslationsProvider(resourceLoader = textResourceLoader)
    private val placeRepository = IosPlaceRepository(fileSystemProvider)
    private val routeRepository = IosRouteRepository(fileSystemProvider)
    private val userPreferenceRepository = IosUserPreferenceRepository(fileSystemProvider)

    val localizationRepository = IosLocalizationRepository(fileSystemProvider)
    val syncStateRepository = IosSyncStateRepository(fileSystemProvider)
    val mapBookmarkRepository = IosMapBookmarkRepository(fileSystemProvider)

    fun currentStatus(language: String = "en"): IosRuntimeStatus = runBlocking {
        val uiTranslations = uiTranslationsProvider.load(language)
        val seedPlaces = PlaceSeedParser.parsePlaces(textResourceLoader.loadText("kyiv_tourist_places.json").orEmpty())

        if (placeRepository.getPlacesCount() == 0 && seedPlaces.isNotEmpty()) {
            placeRepository.savePlaces(seedPlaces)
        }

        userPreferenceRepository.upsert("ios.runtime.lastLanguage", language)

        if (routeRepository.getAllRoutesSnapshot().isEmpty() && seedPlaces.size >= 2) {
            routeRepository.saveRoute(
                Route(
                    name = "Seed Demo Route",
                    description = "Generated from bundled seed data",
                    startPoint = RoutePoint(seedPlaces[0].latitude, seedPlaces[0].longitude, seedPlaces[0].name),
                    endPoint = RoutePoint(seedPlaces[1].latitude, seedPlaces[1].longitude, seedPlaces[1].name),
                    waypoints = emptyList(),
                    distance = 0.0,
                    estimatedDuration = 15,
                )
            )
        }

        IosRuntimeStatus(
            uiTranslationEntries = uiTranslations.size,
            parsedSeedPlaces = seedPlaces.size,
            persistedPlaces = placeRepository.getPlacesCount(),
            persistedRoutes = routeRepository.getAllRoutesSnapshot().size,
            storedLanguagePreference = userPreferenceRepository.getString("ios.runtime.lastLanguage", "")
        )
    }
}