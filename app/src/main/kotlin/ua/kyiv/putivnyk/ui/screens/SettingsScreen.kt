package ua.kyiv.putivnyk.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import java.io.ByteArrayOutputStream
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import kotlinx.coroutines.launch
import ua.kyiv.putivnyk.BuildConfig
import ua.kyiv.putivnyk.ui.i18n.tr
import ua.kyiv.putivnyk.ui.viewmodel.AppExperienceViewModel
import ua.kyiv.putivnyk.ui.viewmodel.SettingsToolsViewModel
import ua.kyiv.putivnyk.ui.viewmodel.UiLocalizationViewModel

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }
    val owner: ViewModelStoreOwner = activity ?: checkNotNull(LocalViewModelStoreOwner.current)
    val viewModel: UiLocalizationViewModel = hiltViewModel(viewModelStoreOwner = owner)
    val appExperienceViewModel: AppExperienceViewModel = hiltViewModel(viewModelStoreOwner = owner)
    val toolsViewModel: SettingsToolsViewModel = hiltViewModel(viewModelStoreOwner = owner)
    val language by viewModel.manualLanguage.collectAsState()
    val isAutoMode by viewModel.isAutoMode.collectAsState()
    val effectiveLanguage by viewModel.effectiveLanguage.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val downloadProgressPercent by viewModel.downloadProgressPercent.collectAsState()
    val isDownloadingModels by viewModel.isDownloadingModels.collectAsState()
    val transferState by toolsViewModel.transferState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var exportPayload by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(transferState) {
        if (transferState.isNotBlank()) {
            snackbarHostState.showSnackbar(transferState)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val payload = exportPayload ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            coroutineScope.launch {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(payload.toByteArray())
                    }
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val maxImportBytes = 2 * 1024 * 1024
                val json = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0
                        while (true) {
                            val count = input.read(buffer)
                            if (count <= 0) break
                            total += count
                            if (total > maxImportBytes) {
                                return@use null
                            }
                            output.write(buffer, 0, count)
                        }
                        output.toString(Charsets.UTF_8.name())
                    }
                }.getOrNull()
                if (json == null) {
                    toolsViewModel.notifyImportTooLarge(maxImportBytes)
                } else if (json.isNotBlank()) {
                    toolsViewModel.importRoutes(json)
                }
            }
        }
    }

    LaunchedEffect(exportPayload) {
        if (!exportPayload.isNullOrBlank()) {
            exportLauncher.launch("putivnyk-routes-${System.currentTimeMillis()}.json")
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(tr("settings.interface_lang", "Мова інтерфейсу"))
                        FilterChip(
                            selected = isAutoMode,
                            onClick = { viewModel.setAutoMode(true) },
                            label = { Text(tr("settings.auto", "Авто (системна мова)")) }
                        )
                        FilterChip(
                            selected = !isAutoMode,
                            onClick = { viewModel.setAutoMode(false) },
                            label = { Text(tr("settings.manual", "Ручний вибір")) }
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            viewModel.languages.forEach { lang ->
                                FilterChip(
                                    selected = (!isAutoMode && language == lang.isoCode) || (isAutoMode && effectiveLanguage == lang.isoCode),
                                    onClick = {
                                        viewModel.setAutoMode(false)
                                        viewModel.setManualLanguage(lang.isoCode)
                                    },
                                    enabled = true,
                                    label = { Text("${flagFor(lang.isoCode)} ${lang.displayName}") }
                                )
                            }
                        }
                        FilledTonalButton(onClick = { viewModel.downloadModelsForAllLanguages() }) {
                            Text(tr("settings.download_ml", "Завантажити моделі ML Kit"))
                        }
                        if (isDownloadingModels || downloadState.isNotBlank()) {
                            val animatedProgress by animateFloatAsState(
                                targetValue = (downloadProgressPercent / 100f).coerceIn(0f, 1f),
                                animationSpec = tween(durationMillis = 400),
                                label = "ml_download_progress"
                            )
                            val isFinishing = isDownloadingModels && downloadProgressPercent >= 100
                            val trackColor = MaterialTheme.colorScheme.surfaceVariant
                            val indicatorColor = MaterialTheme.colorScheme.primary

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    Crossfade(
                                        targetState = isFinishing,
                                        animationSpec = tween(durationMillis = 300),
                                        label = "indicator_switch"
                                    ) { finishing ->
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            if (finishing) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(52.dp),
                                                    color = indicatorColor,
                                                    trackColor = trackColor,
                                                    strokeWidth = 3.dp,
                                                    strokeCap = StrokeCap.Round
                                                )
                                            } else {
                                                CircularProgressIndicator(
                                                    progress = { animatedProgress },
                                                    modifier = Modifier.size(52.dp),
                                                    color = indicatorColor,
                                                    trackColor = trackColor,
                                                    strokeWidth = 3.dp,
                                                    strokeCap = StrokeCap.Round
                                                )
                                            }
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Translate,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "ML Kit",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = downloadState.ifBlank {
                                            if (isDownloadingModels) "Завантаження… ${downloadProgressPercent}%" else ""
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (isDownloadingModels) {
                                    IconButton(onClick = { viewModel.cancelDownloadModels() }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Скасувати",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${tr("settings.version", "Версія")}: ${BuildConfig.VERSION_NAME}")
                        Text("${tr("settings.features", "Функціонал")}: ${tr("settings.features_list", "карта, рекомендації, події, маршрути, локалізація")}")
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tr("settings.data_tools", "Інструменти даних"))
                        FilledTonalButton(onClick = {
                            toolsViewModel.exportRoutes { payload -> exportPayload = payload }
                        }) {
                            Text(tr("settings.export_routes", "Експорт маршрутів (JSON)"))
                        }
                        FilledTonalButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }) {
                            Text(tr("settings.import_routes", "Імпорт маршрутів (JSON)"))
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tr("settings.help_feedback", "Підтримка та feedback"))
                        FilledTonalButton(onClick = {
                            val subject = Uri.encode("Putivnyk feedback")
                            val body = Uri.encode("Версія: ${BuildConfig.VERSION_NAME}\n\nОпишіть проблему або ідею:")
                            val uri = Uri.parse("mailto:support@putivnyk.app?subject=$subject&body=$body")
                            val intent = Intent(Intent.ACTION_SENDTO, uri)
                            val packageManager = context.packageManager
                            if (intent.resolveActivity(packageManager) != null) {
                                runCatching { context.startActivity(intent) }
                                    .onFailure { if (it is ActivityNotFoundException) Unit }
                            }
                        }) {
                            Text(tr("settings.send_feedback", "Надіслати відгук"))
                        }
                        FilledTonalButton(onClick = { appExperienceViewModel.resetOnboarding() }) {
                            Text(tr("settings.reopen_onboarding", "Показати onboarding знову"))
                        }
                    }
                }
            }
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

private fun flagFor(isoCode: String): String = when (isoCode.lowercase()) {
    "uk" -> "🇺🇦"
    "en" -> "🇬🇧"
    "pl" -> "🇵🇱"
    "de" -> "🇩🇪"
    "fr" -> "🇫🇷"
    "es" -> "🇪🇸"
    "it" -> "🇮🇹"
    "pt" -> "🇵🇹"
    "cs" -> "🇨🇿"
    "ja" -> "🇯🇵"
    "ko" -> "🇰🇷"
    "zh" -> "🇨🇳"
    else -> "🏳️"
}
