package ua.kyiv.putivnyk.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ua.kyiv.putivnyk.map.offline.OfflineAreas
import ua.kyiv.putivnyk.map.offline.OfflineRegionsRepository
import ua.kyiv.putivnyk.map.offline.OfflineRegionsRepository.DownloadedRegion
import ua.kyiv.putivnyk.ui.i18n.tr
import ua.kyiv.putivnyk.ui.i18n.trDynamic
import ua.kyiv.putivnyk.ui.viewmodel.OfflineMapsViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMapsScreen(
    onBack: () -> Unit,
    viewModel: OfflineMapsViewModel = hiltViewModel()
) {
    val regions by viewModel.regions.collectAsState()
    val failure by viewModel.failure.collectAsState()
    val currentView by viewModel.currentView.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var regionToDelete by remember { mutableStateOf<DownloadedRegion?>(null) }
    val failedMessage = tr("offline.failed", "Не вдалося завантажити район")
    val currentViewTitle = tr("offline.current_view", "Поточна область карти")

    LaunchedEffect(failure) {
        val message = failure ?: return@LaunchedEffect
        snackbarHostState.showSnackbar("$failedMessage: $message")
        viewModel.clearFailure()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(tr("offline.title", "Офлайн-карти")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("common.back", "Назад"))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "intro") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            tr(
                                "offline.intro",
                                "Завантажте потрібні райони — детальна карта працюватиме без інтернету. Без завантажень у вас завжди є спрощена вбудована карта Києва."
                            ) + " " + tr("offline.zoom_note", "Деталізація до рівня") + " ${viewModel.maxZoom}.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            currentView?.let { bounds ->
                item(key = "current_view") {
                    val tiles = viewModel.estimateTiles(bounds)
                    AreaRow(
                        icon = { Icon(Icons.Filled.CropFree, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        title = currentViewTitle,
                        subtitle = sizeLabel(viewModel.estimateBytes(bounds), tiles),
                        tooLarge = tiles > OfflineRegionsRepository.MAX_TILES_PER_REGION,
                        onDownload = { viewModel.downloadCurrentView(currentViewTitle) }
                    )
                }
            }

            if (regions.isNotEmpty()) {
                item(key = "downloaded_title") { SectionHeader(tr("offline.downloaded", "Завантажені")) }
                items(regions, key = { "region_${it.regionId}" }) { region ->
                    DownloadedRegionCard(
                        region = region,
                        title = OfflineAreas.kyiv.firstOrNull { it.id == region.areaId }
                            ?.let { tr(it.titleKey, it.title) }
                            ?: trDynamic(region.title),
                        onPause = { viewModel.pause(region.regionId) },
                        onResume = { viewModel.resume(region.regionId) },
                        onDelete = { regionToDelete = region },
                        modifier = Modifier.animateItem()
                    )
                }
            }

            item(key = "areas_title") { SectionHeader(tr("offline.kyiv_areas", "Райони Києва")) }
            items(viewModel.areas, key = { "area_${it.id}" }) { area ->
                val downloaded = regions.firstOrNull { it.areaId == area.id }
                val tiles = viewModel.estimateTiles(area.bounds)
                val title = tr(area.titleKey, area.title)
                AreaRow(
                    icon = {
                        Icon(
                            if (downloaded?.isComplete == true) Icons.Filled.CheckCircle else Icons.Filled.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    title = title,
                    subtitle = when {
                        downloaded?.isComplete == true -> tr("offline.ready", "Доступно офлайн")
                        downloaded != null -> "${tr("offline.downloading", "Завантаження")} ${(downloaded.fraction * 100).toInt()}%"
                        else -> sizeLabel(viewModel.estimateBytes(area.bounds), tiles)
                    },
                    tooLarge = tiles > OfflineRegionsRepository.MAX_TILES_PER_REGION,
                    onDownload = if (downloaded == null) {
                        { viewModel.download(area, title) }
                    } else {
                        null
                    }
                )
            }
        }
    }

    regionToDelete?.let { region ->
        AlertDialog(
            onDismissRequest = { regionToDelete = null },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text(tr("offline.delete_title", "Видалити район?")) },
            text = { Text(tr("offline.delete_body", "Карта цього району знову потребуватиме інтернету.")) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(region.regionId)
                    regionToDelete = null
                }) { Text(tr("common.delete", "Видалити")) }
            },
            dismissButton = {
                TextButton(onClick = { regionToDelete = null }) { Text(tr("home.cancel", "Скасувати")) }
            }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
}

@Composable
private fun AreaRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    tooLarge: Boolean,
    onDownload: (() -> Unit)?
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (tooLarge) tr("offline.too_large", "Завелика область — наблизьте карту") else subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (tooLarge) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onDownload != null) {
                FilledTonalButton(onClick = onDownload, enabled = !tooLarge) {
                    Text(tr("offline.download", "Завантажити"))
                }
            }
        }
    }
}

@Composable
private fun DownloadedRegionCard(
    region: DownloadedRegion,
    title: String,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when {
                        region.isComplete -> Icons.Filled.CheckCircle
                        region.error != null -> Icons.Filled.ErrorOutline
                        else -> Icons.Filled.CloudDownload
                    },
                    contentDescription = null,
                    tint = if (region.error != null && !region.isComplete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        regionStatus(region),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (region.error != null && !region.isComplete) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!region.isComplete) {
                    IconButton(onClick = if (region.isDownloading) onPause else onResume) {
                        Icon(
                            if (region.isDownloading) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (region.isDownloading) tr("offline.pause", "Пауза") else tr("offline.resume", "Продовжити")
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = tr("common.delete", "Видалити"))
                }
            }
            if (!region.isComplete) {
                LinearProgressIndicator(
                    progress = { region.fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    strokeCap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun regionStatus(region: DownloadedRegion): String {
    val size = formatMegabytes(region.completedBytes)
    return when {
        region.isComplete -> "${tr("offline.ready", "Доступно офлайн")} · $size"
        region.error == "limit" -> tr("offline.limit", "Досягнуто ліміт плиток — оберіть меншу область")
        region.error != null -> "${tr("offline.waiting_network", "Очікуємо мережу")} · ${(region.fraction * 100).toInt()}%"
        region.isDownloading -> "${tr("offline.downloading", "Завантаження")} ${(region.fraction * 100).toInt()}% · $size"
        else -> "${tr("offline.paused", "Призупинено")} ${(region.fraction * 100).toInt()}% · $size"
    }
}

@Composable
private fun sizeLabel(bytes: Long, tiles: Long): String =
    "≈ ${formatMegabytes(bytes)} · $tiles ${tr("offline.tiles", "плиток")}"

@Composable
private fun formatMegabytes(bytes: Long): String {
    val megabytes = bytes / (1024.0 * 1024.0)
    val unit = tr("offline.mb", "МБ")
    return if (megabytes < 10) String.format(Locale.US, "%.1f %s", megabytes, unit) else String.format(Locale.US, "%.0f %s", megabytes, unit)
}
