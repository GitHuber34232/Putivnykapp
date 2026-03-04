package ua.kyiv.putivnyk.ui.i18n

import androidx.compose.runtime.Composable
import ua.kyiv.putivnyk.data.model.PlaceCategory
import ua.kyiv.putivnyk.data.model.PlaceSortMode
import ua.kyiv.putivnyk.data.model.RiverBank

@Composable
fun trCategory(category: PlaceCategory): String = when (category) {
    PlaceCategory.PARK -> tr("category.park", "Парк")
    PlaceCategory.MUSEUM -> tr("category.museum", "Музей")
    PlaceCategory.THEATER -> tr("category.theater", "Театр")
    PlaceCategory.RESTAURANT -> tr("category.restaurant", "Ресторан")
    PlaceCategory.CATHEDRAL -> tr("category.cathedral", "Собор")
    PlaceCategory.MONASTERY -> tr("category.monastery", "Монастир")
    PlaceCategory.ARCHITECTURE_MONUMENT -> tr("category.architecture_monument", "Пам'ятка архітектури")
    PlaceCategory.SQUARE -> tr("category.square", "Площа")
    PlaceCategory.STREET -> tr("category.street", "Вулиця")
    PlaceCategory.DISTRICT -> tr("category.district", "Район")
    PlaceCategory.STADIUM -> tr("category.stadium", "Стадіон")
    PlaceCategory.EMBANKMENT -> tr("category.embankment", "Набережна")
    PlaceCategory.FAMOUS_PLACE -> tr("category.famous_place", "Відомі місця")
    PlaceCategory.TOILET -> tr("category.toilet", "Туалети")
    PlaceCategory.OTHER -> tr("category.other", "Інше")
}

@Composable
fun trRiverBank(riverBank: RiverBank): String = when (riverBank) {
    RiverBank.LEFT -> tr("river.left", "Лівий берег")
    RiverBank.RIGHT -> tr("river.right", "Правий берег")
    RiverBank.BOTH -> tr("river.both", "Обидва береги")
    RiverBank.UNKNOWN -> tr("river.unknown", "Невідомо")
}

@Composable
fun trSortMode(mode: PlaceSortMode): String = when (mode) {
    PlaceSortMode.POPULARITY -> tr("sort.popularity", "Популярність")
    PlaceSortMode.RATING -> tr("sort.rating", "Рейтинг")
    PlaceSortMode.DISTANCE -> tr("sort.distance", "Відстань")
    PlaceSortMode.RECOMMENDED -> tr("sort.recommended", "Рекомендовані")
}
