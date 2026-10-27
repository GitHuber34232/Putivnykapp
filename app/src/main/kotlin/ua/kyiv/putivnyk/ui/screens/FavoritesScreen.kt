package ua.kyiv.putivnyk.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ua.kyiv.putivnyk.ui.components.EmptyStateView
import ua.kyiv.putivnyk.ui.components.SkeletonListLoader
import ua.kyiv.putivnyk.ui.i18n.tr
import ua.kyiv.putivnyk.ui.viewmodel.PlacesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onBack: (() -> Unit)? = null,
    onOpenPlaceDetails: (Long) -> Unit = {},
    viewModel: PlacesViewModel = hiltViewModel()
) {
    val favoritePlaces by viewModel.favoritePlaces.collectAsState()
    val isLoaded by viewModel.favoritesLoaded.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("favorites.title", "Улюблені місця")) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("common.back", "Назад"))
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (!isLoaded) {
            SkeletonListLoader(
                itemCount = 3,
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(favoritePlaces, key = { it.id }) { place ->
                    PlaceCard(
                        modifier = Modifier.animateItem(),
                        place = place,
                        onFavoriteClick = { viewModel.toggleFavorite(place.id) },
                        onVisitedClick = { viewModel.toggleVisited(place.id) },
                        onClick = { onOpenPlaceDetails(place.id) }
                    )
                }
            }
        }
    }
}
