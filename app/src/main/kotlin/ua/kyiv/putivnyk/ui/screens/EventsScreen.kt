package ua.kyiv.putivnyk.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import ua.kyiv.putivnyk.data.remote.events.model.EventItem
import ua.kyiv.putivnyk.ui.components.EmptyStateView
import ua.kyiv.putivnyk.ui.components.ErrorRetryBanner
import ua.kyiv.putivnyk.ui.components.SkeletonListLoader
import ua.kyiv.putivnyk.ui.i18n.LocalUiRuntimeTranslator
import ua.kyiv.putivnyk.ui.i18n.tr
import ua.kyiv.putivnyk.ui.i18n.trDynamic
import ua.kyiv.putivnyk.ui.utils.EventTimeFormatter
import ua.kyiv.putivnyk.ui.viewmodel.EventsViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventsScreen(
    onOpenMap: () -> Unit = {},
    viewModel: EventsViewModel = hiltViewModel()
) {
    val events by viewModel.events.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val categories by viewModel.availableCategories.collectAsState()
    val isDataStale by viewModel.isDataStale.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loadFailed by viewModel.loadFailed.collectAsState()
    val selectedEvent by viewModel.selectedEvent.collectAsState()
    val locale = rememberUiLocale()

    val snackbarHostState = remember { SnackbarHostState() }
    val msgAddedRoute = tr("details.added_route", "Додано до маршруту")
    val msgNoActiveRoute = tr("details.no_active_route", "Немає активного маршруту — увімкніть його у вкладці «Маршрути»")
    val msgAlreadyInRoute = tr("details.already_in_route", "Це місце вже є в маршруті")
    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { key ->
            val message = when (key) {
                "added_to_route" -> msgAddedRoute
                "no_active_route" -> msgNoActiveRoute
                "already_in_route" -> msgAlreadyInRoute
                else -> key
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(tr("events.title", "Події та афіша")) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }, enabled = !isLoading) {
                        Icon(Icons.Filled.Refresh, contentDescription = tr("events.refresh", "Оновити"))
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isLoading && events.isNotEmpty(),
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::updateSearchQuery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        shape = RoundedCornerShape(28.dp),
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(Icons.Filled.Clear, contentDescription = tr("map.clear", "Очистити"))
                                }
                            }
                        },
                        placeholder = { Text(tr("events.search", "Пошук подій")) },
                        singleLine = true
                    )
                }

                if (loadFailed) {
                    item(key = "error") {
                        ErrorRetryBanner(
                            message = if (events.isEmpty()) {
                                tr("events.load_failed", "Не вдалося завантажити події. Перевірте з'єднання")
                            } else {
                                tr("events.load_failed_cached", "Немає з'єднання — показано збережену афішу")
                            },
                            onRetry = { viewModel.retrySyncInBackground() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else if (isDataStale) {
                    item(key = "stale") {
                        ErrorRetryBanner(
                            message = tr("events.freshness_stale", "Офлайн-дані подій застаріли"),
                            onRetry = { viewModel.retrySyncInBackground() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                item(key = "filters") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedCategory == null,
                                onClick = { viewModel.setCategory(null) },
                                label = { Text(tr("map.all", "Всі")) }
                            )
                        }
                        items(categories, key = { it }) { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { viewModel.setCategory(category) },
                                label = { Text(trDynamic(category)) }
                            )
                        }
                    }
                }

                item(key = "sort") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = sortMode == EventsViewModel.EventSortMode.DATE,
                            onClick = { viewModel.setSortMode(EventsViewModel.EventSortMode.DATE) },
                            label = { Text(tr("events.sort_date", "За датою")) }
                        )
                        FilterChip(
                            selected = sortMode == EventsViewModel.EventSortMode.TITLE,
                            onClick = { viewModel.setSortMode(EventsViewModel.EventSortMode.TITLE) },
                            label = { Text(tr("events.sort_title", "За назвою")) }
                        )
                        FilterChip(
                            selected = sortMode == EventsViewModel.EventSortMode.CATEGORY,
                            onClick = { viewModel.setSortMode(EventsViewModel.EventSortMode.CATEGORY) },
                            label = { Text(tr("events.sort_category", "За категорією")) }
                        )
                    }
                }

                when {
                    isLoading && events.isEmpty() -> item(key = "loading") {
                        SkeletonListLoader(itemCount = 4, modifier = Modifier.fillMaxWidth())
                    }
                    events.isEmpty() -> item(key = "empty") {
                        EmptyStateView(
                            icon = Icons.Filled.EventBusy,
                            title = tr("events.empty", "Події за обраними фільтрами відсутні"),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp)
                        )
                    }
                    // Prefixed so a backend id can never clash with the fixed item keys above.
                    else -> items(events, key = { "event_${it.id}" }) { event ->
                        EventCard(
                            modifier = Modifier.animateItem(),
                            event = event,
                            locale = locale,
                            onClick = { viewModel.selectEvent(event) }
                        )
                    }
                }
            }
        }
    }

    selectedEvent?.let { event ->
        EventDetailsSheet(
            event = event,
            locale = locale,
            onDismiss = { viewModel.selectEvent(null) },
            onOpenMap = {
                viewModel.openSelectedEventOnMap {
                    viewModel.selectEvent(null)
                    onOpenMap()
                }
            },
            onAddToRoute = {
                viewModel.addSelectedEventToActiveRoute()
                viewModel.selectEvent(null)
            }
        )
    }
}

@Composable
private fun rememberUiLocale(): Locale {
    val language = LocalUiRuntimeTranslator.current?.effectiveLanguage ?: "uk"
    return remember(language) { Locale.forLanguageTag(language) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventCard(
    modifier: Modifier = Modifier,
    event: EventItem,
    locale: Locale,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            EventCover(
                coverUrl = event.coverUrl,
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = trDynamic(event.category.ifBlank { tr("events.item", "Подія") }).uppercase(locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                Text(
                    text = trDynamic(event.title),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                EventMetaLine(
                    icon = Icons.Filled.Schedule,
                    text = EventTimeFormatter.format(event.startsAt, locale)
                        ?: tr("events.time_tbd", "Час уточнюється")
                )
                EventMetaLine(
                    icon = Icons.Filled.Place,
                    text = trDynamic(event.locationName.ifBlank { tr("events.location_tbd", "Локація уточнюється") })
                )
            }
        }
    }
}

@Composable
private fun EventMetaLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EventCover(coverUrl: String?, modifier: Modifier) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.tertiaryContainer
                )
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Event,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(32.dp)
        )
        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EventDetailsSheet(
    event: EventItem,
    locale: Locale,
    onDismiss: () -> Unit,
    onOpenMap: () -> Unit,
    onAddToRoute: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasLocation = event.latitude != null && event.longitude != null
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!event.coverUrl.isNullOrBlank()) {
                EventCover(
                    coverUrl = event.coverUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(24.dp))
                )
            }
            Text(
                text = trDynamic(event.title),
                style = MaterialTheme.typography.headlineSmall
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (event.category.isNotBlank()) {
                    AssistChip(onClick = {}, label = { Text(trDynamic(event.category)) })
                }
                AssistChip(
                    onClick = {},
                    label = { Text(trDynamic(event.priceLabel)) },
                    leadingIcon = { Icon(Icons.Filled.ConfirmationNumber, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EventMetaLine(icon = Icons.Filled.Schedule, text = eventTimeRange(event, locale) ?: tr("events.time_tbd", "Час уточнюється"))
                    EventMetaLine(
                        icon = Icons.Filled.Place,
                        text = trDynamic(event.locationName.ifBlank { tr("events.no_location", "Локація не вказана") })
                    )
                }
            }
            if (event.description.isNotBlank()) {
                Text(
                    text = trDynamic(event.description),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            val ticketUrl = event.ticketUrl
            if (!ticketUrl.isNullOrBlank()) {
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ticketUrl)))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.ConfirmationNumber, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(tr("events.tickets", "Квитки та деталі"))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onOpenMap,
                    enabled = hasLocation,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(tr("events.on_map", "На карті"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onAddToRoute,
                    enabled = hasLocation,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Route, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(tr("events.to_route", "В маршрут"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun eventTimeRange(event: EventItem, locale: Locale): String? {
    val start = EventTimeFormatter.format(event.startsAt, locale) ?: return null
    if (event.endsAt.isBlank()) return start
    val end = if (EventTimeFormatter.isSameDay(event.startsAt, event.endsAt)) {
        EventTimeFormatter.formatTime(event.endsAt)
    } else {
        EventTimeFormatter.format(event.endsAt, locale)
    }
    return if (end != null) "$start – $end" else start
}
