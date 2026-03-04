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
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ua.kyiv.putivnyk.ui.navigation.Screen
import ua.kyiv.putivnyk.ui.i18n.LocalUiRuntimeTranslator
import ua.kyiv.putivnyk.ui.i18n.LocalUiText
import ua.kyiv.putivnyk.ui.i18n.UiRuntimeTranslator
import ua.kyiv.putivnyk.ui.i18n.UiTextProvider
import ua.kyiv.putivnyk.ui.i18n.tr
import ua.kyiv.putivnyk.ui.screens.EventsScreen
import ua.kyiv.putivnyk.ui.screens.HomeScreen
import ua.kyiv.putivnyk.ui.screens.LocationDetailsScreen
import ua.kyiv.putivnyk.ui.screens.MapScreen
import ua.kyiv.putivnyk.ui.screens.RoutesScreen
import ua.kyiv.putivnyk.ui.screens.SettingsScreen
import ua.kyiv.putivnyk.ui.viewmodel.AppExperienceViewModel
import ua.kyiv.putivnyk.ui.viewmodel.MapViewModel
import ua.kyiv.putivnyk.ui.viewmodel.UiLocalizationViewModel

@Composable
fun PutivnykApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }
    val owner: ViewModelStoreOwner = activity ?: checkNotNull(LocalViewModelStoreOwner.current)
    val localizationViewModel: UiLocalizationViewModel = hiltViewModel(viewModelStoreOwner = owner)
    val appExperienceViewModel: AppExperienceViewModel = hiltViewModel(viewModelStoreOwner = owner)
    val mapViewModel: MapViewModel = hiltViewModel(viewModelStoreOwner = owner)
    val uiTexts by localizationViewModel.uiTexts.collectAsState()
    val effectiveLanguage by localizationViewModel.effectiveLanguage.collectAsState()
    val showOnboarding by appExperienceViewModel.showOnboarding.collectAsState()
    val runtimeTranslator = remember(localizationViewModel, effectiveLanguage) {
        UiRuntimeTranslator(
            effectiveLanguage = effectiveLanguage,
            translateDynamic = { text -> localizationViewModel.translateDynamicText(text) }
        )
    }

    CompositionLocalProvider(
        LocalUiText provides UiTextProvider(uiTexts),
        LocalUiRuntimeTranslator provides runtimeTranslator
    ) {

    val requestLocationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            requestLocationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
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
                            route == screen.route || route.startsWith("${screen.route}?")
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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onOpenMap = {
                        navController.navigate(Screen.Map.route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
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
                    }
                )
            }
            composable(Screen.Routes.route) {
                RoutesScreen(
                    onNavigateToMap = {
                        navController.navigate(Screen.Map.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Screen.Events.route) {
                EventsScreen(
                    onOpenMap = {
                        navController.navigate(Screen.Map.route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
            composable(
                route = Screen.LocationDetails.route,
                arguments = listOf(navArgument("placeId") { type = NavType.LongType })
            ) {
                LocationDetailsScreen(
                    onBack = { navController.popBackStack() },
                    onRouteHere = { placeId ->

                        mapViewModel.requestRouteToPlace(placeId)
                        navController.popBackStack()
                        navController.navigate(Screen.Map.route) {
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

    if (showOnboarding) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(tr("onboarding.title", "Ласкаво просимо до Putivnyk")) },
            text = {
                Text(
                    tr(
                        "onboarding.body",
                        "Увімкніть геолокацію для точніших рекомендацій, зберігайте маршрути офлайн та використовуйте розділ Налаштування для експорту/імпорту даних."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { appExperienceViewModel.completeOnboarding() }) {
                    Text(tr("onboarding.continue", "Продовжити"))
                }
            }
        )
    }
    }
}

private tailrec fun Context.findComponentActivity(): androidx.activity.ComponentActivity? {
    return when (this) {
        is androidx.activity.ComponentActivity -> this
        is ContextWrapper -> baseContext.findComponentActivity()
        else -> null
    }
}
