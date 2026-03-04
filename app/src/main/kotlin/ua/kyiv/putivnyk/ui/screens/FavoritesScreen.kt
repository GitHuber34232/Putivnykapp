package ua.kyiv.putivnyk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ua.kyiv.putivnyk.ui.i18n.tr
import ua.kyiv.putivnyk.ui.components.EmptyStateView
import ua.kyiv.putivnyk.ui.components.LoadingStateView
import ua.kyiv.putivnyk.ui.viewmodel.PlacesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onOpenPlaceDetails: (Long) -> Unit = {},
    viewModel: PlacesViewModel = hiltViewModel()
) {
    val favoritePlaces by viewModel.favoritePlaces.collectAsState()
    val isLoaded by viewModel.isLoaded.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("favorites.title", "Улюблені місця")) }
            )
        }
    ) { padding ->
        if (!isLoaded) {
            LoadingStateView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else if (favoritePlaces.isEmpty()) {
            EmptyStateView(
                icon = Icons.Filled.FavoriteBorder,
                title = tr("favorites.empty", "Немає улюблених місць"),
                subtitle = tr("favorites.empty_hint", "Додайте місця в улюблені зі списку або карти"),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(favoritePlaces, key = { it.id }) { place ->
                    PlaceCard(
                        modifier = Modifier.animateItem(),
                        place = place,
                        onFavoriteClick = { viewModel.toggleFavorite(place.id) },
                        onVisitedClick = { viewModel.toggleVisited(place.id) },
                        onClick = {
                            onOpenPlaceDetails(place.id)
                        }
                    )
                }
            }
        }
    }
}
