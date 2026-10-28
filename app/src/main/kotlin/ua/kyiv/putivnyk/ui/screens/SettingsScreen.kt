package ua.kyiv.putivnyk.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.kyiv.putivnyk.BuildConfig
import ua.kyiv.putivnyk.data.repository.FeatureSettings
import ua.kyiv.putivnyk.ui.i18n.tr
import ua.kyiv.putivnyk.ui.viewmodel.AppExperienceViewModel
import ua.kyiv.putivnyk.ui.viewmodel.AppearanceViewModel
import ua.kyiv.putivnyk.ui.viewmodel.FeaturesViewModel
import ua.kyiv.putivnyk.ui.viewmodel.SettingsToolsViewModel
import ua.kyiv.putivnyk.ui.viewmodel.UiLocalizationViewModel
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_IMPORT_BYTES = 2 * 1024 * 1024

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onOpenOfflineMaps: () -> Unit = {},
    onOpenStats: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }
    val owner: ViewModelStoreOwner = activity ?: checkNotNull(LocalViewModelStoreOwner.current)
    val viewModel: UiLocalizationViewModel = hiltViewModel(viewModelStoreOwner = owner)
    val appExperienceViewModel: AppExperienceViewModel = hiltViewModel(viewModelStoreOwner = owner)
    val appearanceViewModel: AppearanceViewModel = hiltViewModel(viewModelStoreOwner = owner)
    val toolsViewModel: SettingsToolsViewModel = hiltViewModel(viewModelStoreOwner = owner)
    val featuresViewModel: FeaturesViewModel = hiltViewModel()
    val features by featuresViewModel.values.collectAsState()
    val language by viewModel.manualLanguage.collectAsState()
    val isAutoMode by viewModel.isAutoMode.collectAsState()
    val effectiveLanguage by viewModel.effectiveLanguage.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val downloadProgressPercent by viewModel.downloadProgressPercent.collectAsState()
    val isDownloadingModels by viewModel.isDownloadingModels.collectAsState()
    val appearance by appearanceViewModel.appearance.collectAsState()
    val transferState by toolsViewModel.transferState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var exportPayload by remember { mutableStateOf<String?>(null) }
    var showAllModelsDialog by remember { mutableStateOf(false) }
    var showBackgroundLocationRationale by remember { mutableStateOf(false) }
    var notificationsGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    val msgBackgroundDenied = tr(
        "settings.background_location_denied",
        "Без дозволу «Дозволити завжди» місця позначаються лише з відкритою картою"
    )

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsGranted = granted
    }
    val backgroundLocationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        featuresViewModel.set(FeatureSettings.KEY_BACKGROUND_GEOFENCES, granted)
        if (!granted) coroutineScope.launch { snackbarHostState.showSnackbar(msgBackgroundDenied) }
    }
    // Android asks for "all the time" only after the foreground location permission.
    val foregroundLocationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            coroutineScope.launch { snackbarHostState.showSnackbar(msgBackgroundDenied) }
        }
    }

    val msgExported = tr("settings.exported", "Маршрути експортовано")
    val msgExportFailed = tr("settings.export_failed", "Не вдалося зберегти файл")
    val msgImportFailed = tr("settings.import_failed", "Не вдалося прочитати файл")

    LaunchedEffect(transferState) {
        if (transferState.isNotBlank()) {
            snackbarHostState.showSnackbar(transferState)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val payload = exportPayload
        // Cleared every time, so exporting the same routes again works.
        exportPayload = null
        if (uri == null || payload == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(payload.toByteArray())
                    } != null
                }.getOrDefault(false)
            }
            snackbarHostState.showSnackbar(if (written) msgExported else msgExportFailed)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { readLimited(context, uri) }
            }
            result.onSuccess { json ->
                when {
                    json == null -> toolsViewModel.notifyImportTooLarge(MAX_IMPORT_BYTES)
                    json.isNotBlank() -> toolsViewModel.importRoutes(json)
                }
            }.onFailure {
                snackbarHostState.showSnackbar(msgImportFailed)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text(tr("settings.title", "Налаштування")) }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "appearance") {
                val current = appearance ?: AppearanceViewModel.Appearance()
                SettingsSection(icon = Icons.Filled.Palette, title = tr("settings.appearance", "Оформлення")) {
                    Text(
                        text = tr("settings.theme", "Тема"),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val modes = AppearanceViewModel.ThemeMode.entries
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        modes.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = current.themeMode == mode,
                                onClick = { appearanceViewModel.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size)
                            ) {
                                Text(
                                    text = when (mode) {
                                        AppearanceViewModel.ThemeMode.SYSTEM -> tr("settings.theme_system", "Системна")
                                        AppearanceViewModel.ThemeMode.LIGHT -> tr("settings.theme_light", "Світла")
                                        AppearanceViewModel.ThemeMode.DARK -> tr("settings.theme_dark", "Темна")
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SettingsSwitchRow(
                            icon = Icons.Filled.DarkMode,
                            title = tr("settings.dynamic_color", "Кольори з шпалер (Material You)"),
                            subtitle = tr("settings.dynamic_color_hint", "Замість фірмових київських кольорів"),
                            checked = current.dynamicColor,
                            onCheckedChange = { appearanceViewModel.setDynamicColor(it) }
                        )
                    }
                }
            }

            item(key = "language") {
                SettingsSection(icon = Icons.Filled.Language, title = tr("settings.interface_lang", "Мова інтерфейсу")) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = isAutoMode,
                            onClick = { viewModel.setAutoMode(true) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text(tr("settings.auto", "Авто (системна мова)"), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        SegmentedButton(
                            selected = !isAutoMode,
                            onClick = { viewModel.setAutoMode(false) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text(tr("settings.manual", "Ручний вибір"), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        viewModel.languages.forEach { lang ->
                            FilterChip(
                                selected = if (isAutoMode) effectiveLanguage == lang.isoCode else language == lang.isoCode,
                                // Picking a language switches to manual mode by itself.
                                onClick = { viewModel.setManualLanguage(lang.isoCode) },
                                label = { Text("${flagFor(lang.isoCode)} ${lang.displayName}") }
                            )
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        TextButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
                                    .setData(Uri.fromParts("package", context.packageName, null))
                                runCatching { context.startActivity(intent) }
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text(tr("settings.system_app_language", "Мова застосунку в налаштуваннях Android"))
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Translate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tr("settings.offline_translation", "Офлайн-переклад"), style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = tr("settings.offline_translation_hint", "Модель для поточної мови займає близько 30 МБ"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.downloadModelForCurrentLanguage() },
                            enabled = !isDownloadingModels
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(tr("settings.download_ml", "Завантажити модель"))
                        }
                        OutlinedButton(
                            onClick = { showAllModelsDialog = true },
                            enabled = !isDownloadingModels
                        ) {
                            Text(tr("settings.download_ml_all", "Усі мови"))
                        }
                        TextButton(
                            onClick = { toolsViewModel.clearMlKitModels() },
                            enabled = !isDownloadingModels
                        ) {
                            Text(tr("settings.clear_ml", "Видалити моделі"))
                        }
                    }
                    AnimatedVisibility(visible = isDownloadingModels || downloadState.isNotBlank()) {
                        ModelDownloadStatus(
                            isDownloading = isDownloadingModels,
                            progressPercent = downloadProgressPercent,
                            state = downloadState,
                            onCancel = { viewModel.cancelDownloadModels() }
                        )
                    }
                }
            }

            item(key = "walks") {
                SettingsSection(icon = Icons.Filled.AutoAwesome, title = tr("settings.walks", "Прогулянки й карти")) {
                    SettingsLinkRow(
                        icon = Icons.Filled.CloudDownload,
                        title = tr("offline.title", "Офлайн-карти"),
                        subtitle = tr("settings.offline_maps_hint", "Завантажте райони Києва для роботи без інтернету"),
                        onClick = onOpenOfflineMaps
                    )
                    SettingsLinkRow(
                        icon = Icons.Filled.EmojiEvents,
                        title = tr("stats.title", "Прогулянки й досягнення"),
                        subtitle = tr("settings.stats_hint", "Пройдені кілометри, відвідані місця, серії днів"),
                        onClick = onOpenStats
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    SettingsSwitchRow(
                        icon = Icons.Filled.TaskAlt,
                        title = tr("settings.auto_visited", "Автоматично позначати відвідані"),
                        subtitle = tr("settings.auto_visited_hint", "Місце позначається, коли ви до нього підходите"),
                        checked = features.autoVisited,
                        onCheckedChange = { featuresViewModel.set(FeatureSettings.KEY_AUTO_VISITED, it) }
                    )
                    if (features.autoVisited) {
                        SettingsSwitchRow(
                            icon = Icons.Filled.LocationOn,
                            title = tr("settings.background_visits", "Навіть із закритим застосунком"),
                            subtitle = tr(
                                "settings.background_visits_hint",
                                "Геозони Google Play: потрібен дозвіл на геолокацію «Дозволити завжди»"
                            ),
                            checked = features.backgroundGeofences && featuresViewModel.hasBackgroundLocation(),
                            onCheckedChange = { enabled ->
                                when {
                                    !enabled -> featuresViewModel.set(FeatureSettings.KEY_BACKGROUND_GEOFENCES, false)
                                    featuresViewModel.hasBackgroundLocation() ->
                                        featuresViewModel.set(FeatureSettings.KEY_BACKGROUND_GEOFENCES, true)
                                    else -> showBackgroundLocationRationale = true
                                }
                            }
                        )
                    }
                    SettingsSwitchRow(
                        icon = Icons.Filled.Navigation,
                        title = tr("settings.background_navigation", "Навігація на заблокованому екрані"),
                        subtitle = tr(
                            "settings.background_navigation_hint",
                            "Маршрут ведеться з вимкненим екраном, прогрес видно у сповіщенні"
                        ),
                        checked = features.backgroundNavigation,
                        onCheckedChange = { featuresViewModel.set(FeatureSettings.KEY_BACKGROUND_NAVIGATION, it) }
                    )
                    SettingsSwitchRow(
                        icon = Icons.Filled.Headphones,
                        title = tr("settings.audio_narration", "Розповідати про місця на маршруті"),
                        subtitle = tr("settings.audio_narration_hint", "Аудіогід озвучує кожну точку, до якої ви підходите"),
                        checked = features.audioNarration,
                        onCheckedChange = { featuresViewModel.set(FeatureSettings.KEY_AUDIO_NARRATION, it) }
                    )
                    SettingsSwitchRow(
                        icon = Icons.Filled.CameraAlt,
                        title = tr("settings.ar_recognition", "Розпізнавати пам'ятки в AR"),
                        subtitle = tr("settings.ar_recognition_hint", "Порівнює вигляд будівель із фото, усе на пристрої"),
                        checked = features.arImageRecognition,
                        onCheckedChange = { featuresViewModel.set(FeatureSettings.KEY_AR_IMAGE_RECOGNITION, it) }
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsGranted) {
                        FilledTonalButton(
                            onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(tr("settings.allow_notifications", "Дозволити сповіщення"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            item(key = "data") {
                SettingsSection(icon = Icons.Filled.Route, title = tr("settings.data_tools", "Маршрути та дані")) {
                    Text(
                        text = tr("settings.data_hint", "Збережіть свої маршрути у файл або перенесіть їх з іншого пристрою"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = {
                                toolsViewModel.exportRoutes { payload ->
                                    exportPayload = payload
                                    val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                    exportLauncher.launch("putivnyk-routes-$stamp.json")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(tr("settings.export_routes", "Експорт"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        FilledTonalButton(
                            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(tr("settings.import_routes", "Імпорт"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            item(key = "about") {
                SettingsSection(icon = Icons.Filled.Info, title = tr("settings.about", "Про застосунок")) {
                    Text(
                        text = "${tr("settings.version", "Версія")} ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = tr(
                            "settings.features_list",
                            "Карта, рекомендації, події, маршрути, AR-камера з розпізнаванням і стрілками, аудіогід, віджети, офлайн-карти, досягнення"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { sendFeedback(context) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.MailOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(tr("settings.send_feedback", "Відгук"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = { appExperienceViewModel.resetOnboarding() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(tr("settings.reopen_onboarding", "Знайомство"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

    if (showBackgroundLocationRationale) {
        AlertDialog(
            onDismissRequest = { showBackgroundLocationRationale = false },
            icon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
            title = { Text(tr("settings.background_location_title", "Геолокація у фоні")) },
            text = {
                Text(
                    tr(
                        "settings.background_location_body",
                        "Щоб позначати відвідані місця, коли Путівник закрито, застосунку потрібен доступ до геолокації «Дозволити завжди». " +
                            "Координати використовуються лише на пристрої для геозон навколо найближчих місць і нікуди не передаються. " +
                            "Вимкнути можна будь-коли тут або в налаштуваннях Android."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBackgroundLocationRationale = false
                    val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED
                    if (hasFine) {
                        backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    } else {
                        foregroundLocationLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    }
                }) { Text(tr("settings.continue", "Продовжити")) }
            },
            dismissButton = {
                TextButton(onClick = { showBackgroundLocationRationale = false }) { Text(tr("home.cancel", "Скасувати")) }
            }
        )
    }

    if (showAllModelsDialog) {
        AlertDialog(
            onDismissRequest = { showAllModelsDialog = false },
            icon = { Icon(Icons.Filled.Translate, contentDescription = null) },
            title = { Text(tr("settings.download_all_title", "Завантажити всі мови?")) },
            text = {
                Text(
                    tr(
                        "settings.download_all_body",
                        "Моделі перекладу для всіх 15 мов займуть близько 400 МБ. Краще робити це через Wi‑Fi."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showAllModelsDialog = false
                    viewModel.downloadModelsForAllLanguages()
                }) { Text(tr("settings.download_all_confirm", "Завантажити")) }
            },
            dismissButton = {
                TextButton(onClick = { showAllModelsDialog = false }) { Text(tr("home.cancel", "Скасувати")) }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsLinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    )
}

private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

@Composable
private fun ModelDownloadStatus(
    isDownloading: Boolean,
    progressPercent: Int,
    state: String,
    onCancel: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (progressPercent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 400),
        label = "ml_download_progress"
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
            CircularProgressIndicator(
                progress = { if (isDownloading) animatedProgress else 1f },
                modifier = Modifier.size(44.dp),
                strokeWidth = 3.dp,
                strokeCap = StrokeCap.Round,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Text("$progressPercent%", style = MaterialTheme.typography.labelSmall)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = state.ifBlank { "…" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (isDownloading) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = tr("home.cancel", "Скасувати"))
            }
        }
    }
}

/** Reads at most [MAX_IMPORT_BYTES]; returns null when the file is larger. */
private fun readLimited(context: Context, uri: Uri): String? {
    val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open $uri")
    return input.use { stream ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = stream.read(buffer)
            if (count <= 0) break
            total += count
            if (total > MAX_IMPORT_BYTES) return null
            output.write(buffer, 0, count)
        }
        output.toString(Charsets.UTF_8.name())
    }
}

private fun sendFeedback(context: Context) {
    val subject = Uri.encode("Putivnyk feedback")
    val body = Uri.encode("Версія: ${BuildConfig.VERSION_NAME}, Android ${Build.VERSION.RELEASE}\n\nОпишіть проблему або ідею:")
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@putivnyk.app?subject=$subject&body=$body"))
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No mail app installed.
    }
}

private tailrec fun Context.findComponentActivity(): androidx.activity.ComponentActivity? {
    return when (this) {
        is androidx.activity.ComponentActivity -> this
        is ContextWrapper -> baseContext.findComponentActivity()
        else -> null
    }
}

private fun flagFor(isoCode: String): String = when (isoCode.lowercase()) {
    "uk" -> "🇺🇦"
    "en" -> "🇬🇧"
    "es" -> "🇪🇸"
    "fr" -> "🇫🇷"
    "de" -> "🇩🇪"
    "it" -> "🇮🇹"
    "pt" -> "🇵🇹"
    "pl" -> "🇵🇱"
    "tr" -> "🇹🇷"
    "hi" -> "🇮🇳"
    "zh" -> "🇨🇳"
    "ja" -> "🇯🇵"
    "ko" -> "🇰🇷"
    "nl" -> "🇳🇱"
    else -> "🌐"
}
