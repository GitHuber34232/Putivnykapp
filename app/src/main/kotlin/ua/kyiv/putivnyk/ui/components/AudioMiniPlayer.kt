package ua.kyiv.putivnyk.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ua.kyiv.putivnyk.audio.AudioGuideEngine
import ua.kyiv.putivnyk.ui.i18n.tr

/** Compact audio guide controls shown above the bottom navigation while a story plays. */
@Composable
fun AudioMiniPlayer(
    state: AudioGuideEngine.State,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onStop: () -> Unit,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state.isActive || state.unavailable,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        val item = state.current
        Surface(
            onClick = { item?.let { onOpen(it.placeId) } },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            tonalElevation = 3.dp
        ) {
            Column {
                if (item != null && item.chunks.size > 1) {
                    LinearProgressIndicator(
                        progress = { (state.chunkIndex + 1f) / item.chunks.size },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp)) {
                        if (state.isPreparing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.Headphones,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            when {
                                state.unavailable -> tr("audio.unavailable", "Озвучення недоступне на цьому пристрої")
                                state.isPreparing -> tr("audio.preparing", "Готуємо розповідь…")
                                else -> item?.title.orEmpty()
                            },
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val position = if (state.items.size > 1) " · ${state.index + 1}/${state.items.size}" else ""
                        Text(
                            tr("audio.title", "Аудіогід") + position,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                    if (!state.unavailable) {
                        if (state.items.size > 1) {
                            IconButton(onClick = onPrevious) {
                                Icon(Icons.Filled.SkipPrevious, contentDescription = tr("audio.previous", "Назад"))
                            }
                        }
                        FilledIconButton(onClick = onToggle, enabled = !state.isPreparing) {
                            Icon(
                                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (state.isPlaying) tr("audio.pause", "Пауза") else tr("audio.listen", "Слухати")
                            )
                        }
                        if (state.items.size > 1) {
                            IconButton(onClick = onNext) {
                                Icon(Icons.Filled.SkipNext, contentDescription = tr("audio.next", "Далі"))
                            }
                        }
                    }
                    IconButton(onClick = onStop) {
                        Icon(Icons.Filled.Close, contentDescription = tr("audio.stop", "Зупинити"))
                    }
                }
            }
        }
    }
}
