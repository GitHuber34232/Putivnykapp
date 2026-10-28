package ua.kyiv.putivnyk.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ua.kyiv.putivnyk.domain.stats.AchievementProgress
import ua.kyiv.putivnyk.domain.stats.AchievementUnit
import ua.kyiv.putivnyk.domain.stats.StatsCounters
import ua.kyiv.putivnyk.domain.stats.StatsSnapshot
import ua.kyiv.putivnyk.domain.stats.StatsTime
import ua.kyiv.putivnyk.ui.components.SkeletonListLoader
import ua.kyiv.putivnyk.ui.i18n.tr
import ua.kyiv.putivnyk.ui.utils.RouteUiFormatter
import ua.kyiv.putivnyk.ui.viewmodel.StatsViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val snapshot by viewModel.snapshot.collectAsState()
    val achievements by viewModel.achievements.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("stats.title", "Прогулянки й досягнення")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("common.back", "Назад"))
                    }
                }
            )
        }
    ) { padding ->
        val current = snapshot
        if (current == null) {
            SkeletonListLoader(itemCount = 4, modifier = Modifier.padding(padding).fillMaxWidth())
            return@Scaffold
        }
        val unlocked = achievements.count { it.isUnlocked }
        // Unlocked first (newest on top), then the closest to being unlocked.
        val ordered = remember(achievements) {
            achievements.sortedWith(
                compareByDescending<AchievementProgress> { it.isUnlocked }
                    .thenByDescending { it.unlockedAt ?: 0L }
                    .thenByDescending { it.fraction }
            )
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "hero") { StatsHero(current) }
            item(key = "week") { WeekCard(current) }
            item(key = "tiles") { StatTiles(current) }
            item(key = "achievements_title") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        tr("stats.achievements", "Досягнення"),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "$unlocked / ${achievements.size}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(ordered, key = { it.definition.id }) { progress ->
                AchievementCard(progress, modifier = Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun StatsHero(snapshot: StatsSnapshot) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                )
            )
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                tr("stats.total_walked", "Пройдено пішки"),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
            )
            Text(
                formatKilometers(snapshot.totalDistanceMeters),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
            val streak = snapshot.currentStreakDays
            Text(
                if (streak > 0) {
                    "🔥 ${tr("stats.streak_now", "Серія")}: $streak ${daysWord(streak)}"
                } else {
                    tr("stats.streak_start", "Прогуляйтеся сьогодні, щоб почати серію днів")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun WeekCard(snapshot: StatsSnapshot) {
    val days = remember(snapshot.today) { (6 downTo 0).map { snapshot.today.minusDays(it.toLong()) } }
    val values = days.map { snapshot.distanceOn(it) }
    var selected by remember(snapshot.today) { mutableIntStateOf(days.lastIndex) }
    val weekdayLabels = days.map { weekdayShort(it) }
    val valueLabels = values.map { formatKilometers(it) }
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(tr("stats.last_7_days", "Останні 7 днів"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${tr("stats.week_total", "Разом")}: ${formatKilometers(values.sum())}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Value of the tapped day (today by default) instead of a number on every bar.
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        valueLabels[selected],
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (selected == days.lastIndex) tr("stats.today", "Сьогодні")
                        else days[selected].format(DateTimeFormatter.ofPattern("dd.MM")),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            WeeklyDistanceChart(
                values = values,
                selected = selected,
                onSelect = { selected = it },
                description = days.indices.joinToString(", ") { "${weekdayLabels[it]} ${valueLabels[it]}" }
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                weekdayLabels.forEachIndexed { index, label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (index == selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (index == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * One series, so one hue (validated against the card surface in both themes) and no legend;
 * bars are thin with 4dp rounded tops on the baseline. Tapping a day column selects it.
 */
@Composable
private fun WeeklyDistanceChart(
    values: List<Double>,
    selected: Int,
    onSelect: (Int) -> Unit,
    description: String
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val barColor = if (dark) Color(0xFF4A8FE0) else Color(0xFF1D5EAD)
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    val selectionColor = MaterialTheme.colorScheme.onSurface
    // Short walks still get a readable bar: the scale starts at 1 km.
    val scaleMax = max(values.maxOrNull() ?: 0.0, 1_000.0)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(128.dp)
            .semantics { contentDescription = description }
            .pointerInput(values.size) {
                detectTapGestures { offset ->
                    val slot = size.width / values.size.toFloat()
                    onSelect((offset.x / slot).toInt().coerceIn(0, values.lastIndex))
                }
            }
    ) {
        val slot = size.width / values.size
        val barWidth = minOf(slot * 0.5f, 28.dp.toPx())
        val radius = 4.dp.toPx()
        val baseline = size.height - 1.dp.toPx()
        val usable = baseline - 8.dp.toPx()
        values.forEachIndexed { index, meters ->
            val centerX = slot * index + slot / 2f
            val left = centerX - barWidth / 2f
            if (meters <= 0.0) {
                // Empty day: a short stub so the slot does not look missing.
                drawLine(
                    color = baselineColor,
                    start = Offset(left + radius, baseline - 2.dp.toPx()),
                    end = Offset(left + barWidth - radius, baseline - 2.dp.toPx()),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            } else {
                val height = max((meters / scaleMax * usable).toFloat(), radius * 2f)
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = left,
                            top = baseline - height,
                            right = left + barWidth,
                            bottom = baseline,
                            topLeftCornerRadius = CornerRadius(radius),
                            topRightCornerRadius = CornerRadius(radius),
                            bottomRightCornerRadius = CornerRadius.Zero,
                            bottomLeftCornerRadius = CornerRadius.Zero
                        )
                    )
                }
                drawPath(path, barColor)
            }
            if (index == selected) {
                drawCircle(
                    color = selectionColor,
                    radius = 2.5.dp.toPx(),
                    center = Offset(centerX, 3.dp.toPx())
                )
            }
        }
        drawLine(baselineColor, Offset(0f, baseline), Offset(size.width, baseline), strokeWidth = 1.dp.toPx())
    }
}

@Composable
private fun StatTiles(snapshot: StatsSnapshot) {
    val activeMinutes = (snapshot.totalActiveMillis / 60_000L).toInt()
    val tiles = listOf(
        Triple("📍", snapshot.placesVisited.toString(), tr("stats.places_visited", "Відвідано місць")),
        Triple("🏁", snapshot.routesCompleted.toString(), tr("stats.routes_completed", "Пройдено маршрутів")),
        Triple("⏱️", RouteUiFormatter.formatDuration(activeMinutes), tr("stats.active_time", "Час у русі")),
        Triple("🏆", formatKilometers(snapshot.bestDayDistanceMeters), tr("stats.best_day", "Найкращий день")),
        Triple("📅", "${snapshot.longestStreakDays} ${daysWord(snapshot.longestStreakDays)}", tr("stats.longest_streak", "Найдовша серія")),
        Triple("🌉", "${snapshot.banksVisited} / 2", tr("stats.banks", "Береги Дніпра")),
        Triple("📷", snapshot.counter(StatsCounters.AR_RECOGNITIONS).toString(), tr("stats.ar_recognized", "Впізнано в AR")),
        Triple("🎧", snapshot.counter(StatsCounters.AUDIO_LISTENED).toString(), tr("stats.audio_listened", "Прослухано історій")),
        Triple("📤", snapshot.counter(StatsCounters.ROUTES_SHARED).toString(), tr("stats.routes_shared", "Поширено маршрутів")),
        Triple("❤️", snapshot.favoritesCount.toString(), tr("stats.favorites", "Улюблених місць"))
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (emoji, value, label) ->
                    StatTile(emoji = emoji, value = value, label = label, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatTile(emoji: String, value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(emoji, style = MaterialTheme.typography.titleMedium)
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AchievementCard(progress: AchievementProgress, modifier: Modifier = Modifier) {
    val definition = progress.definition
    val unlocked = progress.isUnlocked
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = if (unlocked) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        definition.emoji,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.alpha(if (unlocked) 1f else 0.45f)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    tr(definition.titleKey, definition.title),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    tr(definition.descriptionKey, definition.description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (unlocked) {
                    Text(
                        "✓ ${tr("stats.unlocked_on", "Отримано")} ${formatDate(progress.unlockedAt ?: 0L)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { progress.fraction },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            strokeCap = StrokeCap.Round
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            progressLabel(progress),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun progressLabel(progress: AchievementProgress): String {
    val definition = progress.definition
    return when (definition.unit) {
        AchievementUnit.KILOMETERS -> String.format(
            Locale.US,
            "%.1f / %.0f %s",
            progress.current.coerceAtMost(definition.target),
            definition.target,
            tr("stats.km", "км")
        )
        AchievementUnit.DAYS -> "${progress.current.toInt().coerceAtMost(definition.target.toInt())} / ${definition.target.toInt()} ${daysWord(definition.target.toInt())}"
        AchievementUnit.COUNT -> "${progress.current.toInt().coerceAtMost(definition.target.toInt())} / ${definition.target.toInt()}"
    }
}

@Composable
private fun formatKilometers(meters: Double): String =
    String.format(Locale.US, "%.1f %s", meters / 1000.0, tr("stats.km", "км"))

@Composable
private fun daysWord(count: Int): String {
    // Ukrainian plural rules; other languages get one translated word.
    val mod10 = count % 10
    val mod100 = count % 100
    return when {
        mod10 == 1 && mod100 != 11 -> tr("stats.day_one", "день")
        mod10 in 2..4 && mod100 !in 12..14 -> tr("stats.day_few", "дні")
        else -> tr("stats.day_many", "днів")
    }
}

@Composable
private fun weekdayShort(date: LocalDate): String = when (date.dayOfWeek.value) {
    1 -> tr("stats.weekday_1", "Пн")
    2 -> tr("stats.weekday_2", "Вт")
    3 -> tr("stats.weekday_3", "Ср")
    4 -> tr("stats.weekday_4", "Чт")
    5 -> tr("stats.weekday_5", "Пт")
    6 -> tr("stats.weekday_6", "Сб")
    else -> tr("stats.weekday_7", "Нд")
}

private fun formatDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(StatsTime.zone).toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
