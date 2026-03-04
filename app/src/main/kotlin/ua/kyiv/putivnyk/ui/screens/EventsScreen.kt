package ua.kyiv.putivnyk.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AssistChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ua.kyiv.putivnyk.data.remote.events.model.EventItem
import ua.kyiv.putivnyk.ui.i18n.tr
import ua.kyiv.putivnyk.ui.i18n.trDynamic
import ua.kyiv.putivnyk.ui.components.ErrorRetryBanner
import ua.kyiv.putivnyk.ui.components.LoadingStateView
import ua.kyiv.putivnyk.ui.components.EmptyStateView
import ua.kyiv.putivnyk.ui.viewmodel.EventsViewModel

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    val syncStatusLabel by viewModel.syncStatusLabel.collectAsState()
    val freshnessLabel by viewModel.freshnessLabel.collectAsState()
    val isDataStale by viewModel.isDataStale.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedEvent by viewModel.selectedEvent.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("events.title", "Події та афіша")) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = tr("events.refresh", "Оновити"))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::updateSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = tr("events.search", "Пошук подій")) },
                placeholder = { Text(tr("events.search", "Пошук подій")) },
                singleLine = true
            )

            AssistChip(
                onClick = {},
                label = {
                    Text(
                        tr(
                            syncStatusLabel,
                            when (syncStatusLabel) {
                                "events.sync_running" -> "Синк: виконується"
                                "events.sync_success" -> "Синк: успішно"
                                "events.sync_error" -> "Синк: помилка"
                                else -> "Синк: очікування"
                            }
                        )
                    )
                },
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            if (isDataStale) {
                ErrorRetryBanner(
                    message = tr(
                        freshnessLabel,
                        when (freshnessLabel) {
                            "events.freshness_stale" -> "Офлайн-дані подій застаріли"
                            else -> "Стан синку подій невідомий"
                        }
                    ),
                    onRetry = { viewModel.retrySyncInBackground() }
                )
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { viewModel.setCategory(null) },
                    label = { Text(tr("map.all", "Всі")) }
                )
                categories.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { viewModel.setCategory(category) },
                        label = { Text(trDynamic(category)) }
                    )
                }
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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

            if (isLoading && events.isEmpty()) {
                LoadingStateView(
                    message = tr("events.loading", "Завантаження подій…")
                )
            } else if (events.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Filled.Search,
                    title = tr("events.empty", "Події за обраними фільтрами відсутні")
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(events, key = { it.id }) { event ->
                        EventCard(
                            modifier = Modifier.animateItem(),
                            event = event,
                            onClick = { viewModel.selectEvent(event) }
                        )
                    }
                }
            }
        }
    }

    if (selectedEvent != null) {
        AlertDialog(
            onDismissRequest = { viewModel.selectEvent(null) },
            title = { Text(selectedEvent!!.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(trDynamic(selectedEvent!!.description.ifBlank { tr("details.no_description", "Опис відсутній") }))
                    Text("${tr("events.place", "Місце")}: ${trDynamic(selectedEvent!!.locationName.ifBlank { tr("events.no_location", "Локація не вказана") })}")
                    Text("${tr("events.starts", "Початок")}: ${trDynamic(selectedEvent!!.startsAt.ifBlank { tr("events.time_tbd", "Час уточнюється") })}")
                    Text("${tr("events.ends", "Завершення")}: ${trDynamic(selectedEvent!!.endsAt.ifBlank { tr("events.time_tbd", "Час уточнюється") })}")
                    Text("${tr("events.price", "Ціна")}: ${trDynamic(selectedEvent!!.priceLabel)}")
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = {
                            viewModel.addSelectedEventToActiveRoute()
                            viewModel.selectEvent(null)
                        },
                        enabled = selectedEvent!!.latitude != null && selectedEvent!!.longitude != null
                    ) {
                        Text(tr("events.to_route", "В маршрут"))
                    }
                    TextButton(
                        onClick = {
                            viewModel.openSelectedEventOnMap()
                            viewModel.selectEvent(null)
                            onOpenMap()
                        },
                        enabled = selectedEvent!!.latitude != null && selectedEvent!!.longitude != null
                    ) {
                        Icon(Icons.Filled.Map, contentDescription = null)
                        Text(tr("events.on_map", "На карті"))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.selectEvent(null) }) {
                    Text(tr("common.close", "Закрити"))
                }
            }
        )
    }
}

@Composable
private fun EventCard(
    modifier: Modifier = Modifier,
    event: EventItem,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = trDynamic(event.title),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${trDynamic(event.category.ifBlank { tr("events.item", "Подія") })} • ${trDynamic(event.startsAt.ifBlank { tr("events.time_tbd", "Час уточнюється") })}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = trDynamic(event.priceLabel),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = trDynamic(event.locationName.ifBlank { tr("events.location_tbd", "Локація уточнюється") }),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (event.description.isNotBlank()) {
                Text(
                    text = trDynamic(event.description),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
