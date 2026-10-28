package ua.kyiv.putivnyk.ui

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import ua.kyiv.putivnyk.navigation.DeepLink
import ua.kyiv.putivnyk.navigation.DeepLinkRouter
import ua.kyiv.putivnyk.ui.components.AudioMiniPlayer
import ua.kyiv.putivnyk.ui.navigation.Screen
import ua.kyiv.putivnyk.ui.i18n.LocalUiRuntimeTranslator
import ua.kyiv.putivnyk.ui.i18n.LocalUiText
import ua.kyiv.putivnyk.ui.i18n.UiRuntimeTranslator
import ua.kyiv.putivnyk.ui.i18n.UiTextProvider
import ua.kyiv.putivnyk.ui.i18n.tr
import ua.kyiv.putivnyk.ui.screens.EventsScreen
import ua.kyiv.putivnyk.ui.screens.FavoritesScreen
import ua.kyiv.putivnyk.ui.screens.HomeScreen
import ua.kyiv.putivnyk.ui.screens.LocationDetailsScreen
import ua.kyiv.putivnyk.ui.screens.MapScreen
import ua.kyiv.putivnyk.ui.screens.OfflineMapsScreen
import ua.kyiv.putivnyk.ui.screens.OnboardingDialog
import ua.kyiv.putivnyk.ui.screens.PlacesScreen
import ua.kyiv.putivnyk.ui.screens.RoutesScreen
import ua.kyiv.putivnyk.ui.screens.SettingsScreen
import ua.kyiv.putivnyk.ui.screens.StatsScreen
import ua.kyiv.putivnyk.ui.share.QrScannerScreen
import ua.kyiv.putivnyk.ui.share.RouteImportDialog
import ua.kyiv.putivnyk.ui.viewmodel.AppEventsViewModel
import ua.kyiv.putivnyk.ui.viewmodel.AppExperienceViewModel
import ua.kyiv.putivnyk.ui.viewmodel.AudioPlayerViewModel
import ua.kyiv.putivnyk.ui.viewmodel.UiLocalizationViewModel

@Composable
fun PutivnykApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }
    val owner: ViewModelStoreOwner = activity ?: checkNotNull(LocalViewModelStoreOwner.current)
    val localizationViewModel: UiLocalizationViewModel = hiltViewModel(viewModelStoreOwner = owner)
    val appExperienceViewModel: AppExperienceViewModel = hiltViewModel(viewModelStoreOwner = owner)
    val appEventsViewModel: AppEventsViewModel = hiltViewModel(viewModelStoreOwner = owner)
    val audioViewModel: AudioPlayerViewModel = hiltViewModel(viewModelStoreOwner = owner)
    val audioState by audioViewModel.state.collectAsState()
    val pendingImport by appEventsViewModel.pendingImport.collectAsState()
    val pendingDeepLink by DeepLinkRouter.pending.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val uiTexts by localizationViewModel.uiTexts.collectAsState()
    val effectiveLanguage by localizationViewModel.effectiveLanguage.collectAsState()
    val showOnboarding by appExperienceViewModel.showOnboarding.collectAsState()
    val runtimeTranslator = remember(localizationViewModel, effectiveLanguage) {
        UiRuntimeTranslator(
            effectiveLanguage = effectiveLanguage,
            translateDynamic = { text -> localizationViewModel.translateDynamicText(text) },
            peekDynamic = { text -> localizationViewModel.peekDynamicTranslation(text) }
        )
    }

    CompositionLocalProvider(
        LocalUiText provides UiTextProvider(uiTexts),
        LocalUiRuntimeTranslator provides runtimeTranslator
    ) {

    // Asked once onboarding is dismissed instead of on top of it; the map asks again if needed.
    val requestLocationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    val requestLocationPermissionIfNeeded = {
        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        // Notifications carry the navigation progress, visits and achievements (Android 13+).
        val needsNotifications = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        val permissions = buildList {
            if (!hasFine && !hasCoarse) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (needsNotifications) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permissions.isNotEmpty()) {
            requestLocationPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    // Texts of app-wide messages (resolved here, shown from coroutines).
    val msgVisited = tr("visit.auto_title", "Ви відвідали місце")
    val msgListen = tr("audio.listen", "Слухати")
    val msgAchievement = tr("stats.achievement_unlocked", "Нове досягнення")
    val msgOpen = tr("common.open", "Відкрити")
    val msgInvalidRoute = tr("share.invalid", "Посилання не містить маршруту")
    val msgImported = tr("share.imported", "Маршрут збережено в «Маршрути»")
    val msgNoStories = tr("audio.no_stories", "Для цього маршруту немає розповідей")
    val achievementTitles = rememberAchievementTitles()

    val navigateToMap = { navController.navigateToMapTab() }
    val playRouteStories: (List<ua.kyiv.putivnyk.data.model.RoutePoint>) -> Unit = { points ->
        audioViewModel.playRoute(points) {
            coroutineScope.launch { snackbarHostState.showSnackbar(msgNoStories) }
        }
    }

    // Visits and achievements: snackbars while the app is visible, notifications otherwise
    // (the trackers notify when nobody collects).
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                appEventsViewModel.placeVisited.collect { place ->
                    val result = snackbarHostState.showSnackbar(
                        message = "✓ $msgVisited: ${localizationViewModel.peekDynamicTranslation(place.name) ?: place.name}",
                        actionLabel = msgListen,
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) audioViewModel.playPlace(place.id)
                }
            }
            launch {
                appEventsViewModel.achievementUnlocked.collect { definition ->
                    val title = achievementTitles[definition.id] ?: definition.title
                    val result = snackbarHostState.showSnackbar(
                        message = "${definition.emoji} $msgAchievement: $title",
                        actionLabel = msgOpen,
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        navController.navigate(Screen.Stats.route) { launchSingleTop = true }
                    }
                }
            }
            launch {
                appEventsViewModel.messages.collect { key ->
                    snackbarHostState.showSnackbar(
                        when (key) {
                            AppEventsViewModel.MESSAGE_INVALID_ROUTE -> msgInvalidRoute
                            AppEventsViewModel.MESSAGE_IMPORTED -> msgImported
                            else -> key
                        }
                    )
                }
            }
        }
    }

    // Links from widgets, notifications and shared routes.
    LaunchedEffect(pendingDeepLink) {
        val link = pendingDeepLink ?: return@LaunchedEffect
        DeepLinkRouter.consume(link)
        when (link) {
            is DeepLink.Place -> navController.navigate(Screen.LocationDetails.route(link.placeId)) { launchSingleTop = true }
            is DeepLink.SharedRoute -> appEventsViewModel.offerSharedRoute(link.code)
            is DeepLink.DailyRoute -> appEventsViewModel.startDailyRoute(link.placeIds) { navigateToMap() }
            DeepLink.Map -> navigateToMap()
            DeepLink.Stats -> navController.navigate(Screen.Stats.route) { launchSingleTop = true }
            DeepLink.OfflineMaps -> navController.navigate(Screen.OfflineMaps.route) { launchSingleTop = true }
        }
    }

    val navBackStackEntryForBars by navController.currentBackStackEntryAsState()
    val fullScreenRoute = navBackStackEntryForBars?.destination?.route == Screen.QrScanner.route

    // Screens draw their own top bars, so the outer scaffold only reserves the navigation bar,
    // which keeps its default insets to stay clear of the gesture/3-button bar (edge-to-edge).
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!fullScreenRoute) Column {
            AudioMiniPlayer(
                state = audioState,
                onToggle = { audioViewModel.toggle() },
                onNext = { audioViewModel.next() },
                onPrevious = { audioViewModel.previous() },
                onStop = { audioViewModel.stop() },
                onOpen = { placeId -> navController.navigate(Screen.LocationDetails.route(placeId)) { launchSingleTop = true } }
            )
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                Screen.bottomNavItems.forEach { screen ->
                    val localizedTitle = when (screen) {
                        Screen.Home -> tr("nav.home", screen.title)
                        Screen.Map -> tr("nav.map", screen.title)
                        Screen.Routes -> tr("nav.routes", screen.title)
                        Screen.Events -> tr("nav.events", screen.title)
                        Screen.Settings -> tr("nav.settings", screen.title)
                        else -> screen.title
                    }
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = localizedTitle) },
                        label = { Text(localizedTitle) },
                        alwaysShowLabel = true,
                        selected = currentDestination?.hierarchy?.any {
                            val route = it.route ?: return@any false
                            route == screen.route || route.startsWith("${screen.route}?") ||
                                (screen == Screen.Home && (route == Screen.Places.route || route == Screen.Favorites.route || route == Screen.Stats.route)) ||
                                (screen == Screen.Settings && route == Screen.OfflineMaps.route) ||
                                (screen == Screen.Routes && route == Screen.QrScanner.route)
                        } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onOpenMap = { navController.navigateToMapTab() },
                    onOpenPlaces = { navController.navigate(Screen.Places.route) { launchSingleTop = true } },
                    onOpenFavorites = { navController.navigate(Screen.Favorites.route) { launchSingleTop = true } },
                    onOpenPlaceDetails = { placeId -> navController.navigate(Screen.LocationDetails.route(placeId)) },
                    onOpenStats = { navController.navigate(Screen.Stats.route) { launchSingleTop = true } },
                    onStartDailyRoute = { placeIds -> appEventsViewModel.startDailyRoute(placeIds) { navigateToMap() } }
                )
            }
            composable(Screen.Places.route) {
                PlacesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPlaceDetails = { placeId -> navController.navigate(Screen.LocationDetails.route(placeId)) }
                )
            }
            composable(Screen.Favorites.route) {
                FavoritesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPlaceDetails = { placeId -> navController.navigate(Screen.LocationDetails.route(placeId)) }
                )
            }
            composable(
                route = Screen.Map.routePattern,
                arguments = listOf(navArgument("routeBuilder") {
                    type = NavType.BoolType
                    defaultValue = false
                })
            ) { backStackEntry ->
                val routeBuilderRequested = backStackEntry.arguments?.getBoolean("routeBuilder") ?: false
                MapScreen(
                    startRouteBuilder = routeBuilderRequested,
                    onOpenPlaceDetails = { placeId ->
                        navController.navigate(Screen.LocationDetails.route(placeId))
                    },
                    onPlayRouteStories = playRouteStories
                )
            }
            composable(Screen.Routes.route) {
                RoutesScreen(
                    onNavigateToMap = { navController.navigateToMapTab() },
                    onScanQr = { navController.navigate(Screen.QrScanner.route) { launchSingleTop = true } },
                    onImportText = { text -> appEventsViewModel.offerSharedRoute(text) },
                    onListenRoute = { route ->
                        playRouteStories(listOf(route.startPoint) + route.waypoints + route.endPoint)
                    }
                )
            }
            composable(Screen.Events.route) {
                EventsScreen(
                    onOpenMap = { navController.navigateToMapTab() }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onOpenOfflineMaps = { navController.navigate(Screen.OfflineMaps.route) { launchSingleTop = true } },
                    onOpenStats = { navController.navigate(Screen.Stats.route) { launchSingleTop = true } }
                )
            }
            composable(Screen.Stats.route) {
                StatsScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.OfflineMaps.route) {
                OfflineMapsScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.QrScanner.route) {
                QrScannerScreen(
                    onCode = { code ->
                        navController.popBackStack()
                        appEventsViewModel.offerSharedRoute(code)
                    },
                    onClose = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.LocationDetails.route,
                arguments = listOf(navArgument("placeId") { type = NavType.LongType })
            ) {
                LocationDetailsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenMap = {
                        navController.popBackStack()
                        navController.navigateToMapTab()
                    },
                    onRouteHere = {
                        navController.popBackStack()
                        navController.navigateToMapTab()
                    },
                    onListen = { placeId -> audioViewModel.playPlace(placeId) }
                )
            }
        }
    }

    pendingImport?.let { route ->
        RouteImportDialog(
            route = route,
            onSave = { appEventsViewModel.confirmImport(startNow = false) {} },
            onSaveAndStart = { appEventsViewModel.confirmImport(startNow = true) { navigateToMap() } },
            onDismiss = { appEventsViewModel.dismissImport() }
        )
    }

    if (showOnboarding) {
        OnboardingDialog(
            onFinish = {
                appExperienceViewModel.completeOnboarding()
                requestLocationPermissionIfNeeded()
            }
        )
    }
    }
}

/** Achievement titles in the current language, for the "unlocked" snackbar. */
@Composable
private fun rememberAchievementTitles(): Map<String, String> =
    ua.kyiv.putivnyk.domain.stats.AchievementCatalog.all.associate { it.id to tr(it.titleKey, it.title) }

/** Switches to the Map tab the same way the bottom navigation does, keeping each tab's stack. */
private fun NavHostController.navigateToMapTab() {
    navigate(Screen.Map.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private tailrec fun Context.findComponentActivity(): androidx.activity.ComponentActivity? {
    return when (this) {
        is androidx.activity.ComponentActivity -> this
        is ContextWrapper -> baseContext.findComponentActivity()
        else -> null
    }
}
