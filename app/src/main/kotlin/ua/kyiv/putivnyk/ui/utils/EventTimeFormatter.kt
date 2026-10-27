package ua.kyiv.putivnyk.ui.utils

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Parses the backend's ISO-8601 event times and formats them for people. */
object EventTimeFormatter {

    // Events happen in Kyiv, so times are shown in Kyiv time whatever the phone's zone is.
    val KYIV_ZONE: ZoneId = runCatching { ZoneId.of("Europe/Kyiv") }
        .getOrElse { ZoneId.of("Europe/Kiev") }

    fun parseToEpochMillis(raw: String?): Long? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        runCatching { return OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        runCatching { return Instant.parse(value).toEpochMilli() }
        runCatching { return LocalDateTime.parse(value).atZone(KYIV_ZONE).toInstant().toEpochMilli() }
        runCatching { return LocalDate.parse(value).atStartOfDay(KYIV_ZONE).toInstant().toEpochMilli() }
        return null
    }

    /** "пт, 31 жовтня · 19:00", or null when the value is not a date. */
    fun format(raw: String?, locale: Locale): String? {
        val millis = parseToEpochMillis(raw) ?: return null
        val dateTime = Instant.ofEpochMilli(millis).atZone(KYIV_ZONE)
        return DateTimeFormatter.ofPattern("EEE, d MMMM · HH:mm", locale).format(dateTime)
    }

    /** "19:00", for the end of an event on the same day. */
    fun formatTime(raw: String?): String? {
        val millis = parseToEpochMillis(raw) ?: return null
        return DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(millis).atZone(KYIV_ZONE))
    }

    fun isSameDay(first: String?, second: String?): Boolean {
        val a = parseToEpochMillis(first) ?: return false
        val b = parseToEpochMillis(second) ?: return false
        return Instant.ofEpochMilli(a).atZone(KYIV_ZONE).toLocalDate() ==
            Instant.ofEpochMilli(b).atZone(KYIV_ZONE).toLocalDate()
    }
}
