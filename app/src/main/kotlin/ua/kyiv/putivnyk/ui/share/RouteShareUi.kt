package ua.kyiv.putivnyk.ui.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ua.kyiv.putivnyk.data.model.TransportMode
import ua.kyiv.putivnyk.domain.geo.NativeGeoEngine
import ua.kyiv.putivnyk.share.RouteShareCodec
import ua.kyiv.putivnyk.share.RouteShareManager
import ua.kyiv.putivnyk.ui.i18n.tr
import ua.kyiv.putivnyk.ui.i18n.trDynamic
import ua.kyiv.putivnyk.ui.utils.RouteUiFormatter
import kotlin.math.min

/** Crisp QR code drawn module by module (always black on white, so it scans in dark mode too). */
@Composable
fun QrCodeImage(content: String, modifier: Modifier = Modifier) {
    val matrix = remember(content) { runCatching { RouteShareManager.encodeQr(content) }.getOrNull() }
    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
    ) {
        if (matrix == null) return@Canvas
        val cell = min(size.width, size.height) / matrix.width
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix.get(x, y)) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(x * cell, y * cell),
                        size = Size(cell + 0.5f, cell + 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun RouteQrDialog(
    routeName: String,
    qrContent: String?,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(trDynamic(routeName), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (qrContent != null) {
                    QrCodeImage(content = qrContent, modifier = Modifier.fillMaxWidth())
                    Text(
                        tr("share.qr_hint", "Відскануйте код у Путівнику на іншому телефоні: Маршрути → Сканувати QR"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        tr("share.qr_too_long", "Маршрут завеликий для QR-коду — поділіться посиланням"),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(tr("share.share", "Поділитися"), modifier = Modifier.padding(start = 6.dp))
            }
        },
        dismissButton = {
            TextButton(onClick = onCopy) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(tr("share.copy_link", "Копіювати посилання"), modifier = Modifier.padding(start = 6.dp))
            }
        }
    )
}

@Composable
fun RouteImportDialog(
    route: RouteShareCodec.SharedRoute,
    onSave: () -> Unit,
    onSaveAndStart: () -> Unit,
    onDismiss: () -> Unit
) {
    val straight = remember(route) { NativeGeoEngine.polylineDistanceMeters(route.points) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("share.import_title", "Маршрут від друга")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(trDynamic(route.name), style = MaterialTheme.typography.titleMedium)
                Text(
                    buildString {
                        append(route.points.size)
                        append(' ')
                        append(tr("routes.points_label", "точок"))
                        append(" · ≈")
                        append(RouteUiFormatter.formatDistance(straight * 1.3))
                        append(" · ")
                        append(if (route.transportMode == TransportMode.DRIVING) "🚗" else "🚶")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                route.points.take(6).forEachIndexed { index, point ->
                    Row {
                        Text("${index + 1}.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(24.dp))
                        Text(
                            point.name?.let { trDynamic(it) } ?: String.format(java.util.Locale.US, "%.4f, %.4f", point.latitude, point.longitude),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (route.points.size > 6) {
                    Text("…", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSaveAndStart) { Text(tr("share.save_and_start", "Зберегти й почати")) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text(tr("home.cancel", "Скасувати")) }
                TextButton(onClick = onSave) { Text(tr("home.save", "Зберегти")) }
            }
        }
    )
}
