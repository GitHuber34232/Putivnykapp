package ua.kyiv.putivnyk.share

import ua.kyiv.putivnyk.data.model.RoutePoint
import ua.kyiv.putivnyk.data.model.TransportMode
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.math.roundToLong

/**
 * Compact, URL-safe encoding of a route for links and QR codes:
 * "PR" + version, transport, name, then delta-encoded coordinates (1e-5 deg) with point names,
 * raw-deflated and Base64url-encoded. Decoding treats the input as untrusted.
 */
object RouteShareCodec {
    const val SCHEME = "putivnyk"
    const val HOST = "route"
    const val QUERY_PARAM = "d"

    const val MAX_POINTS = 100
    private const val MIN_POINTS = 2
    private const val MAX_ROUTE_NAME = 120
    private const val MAX_POINT_NAME = 80
    private const val MAX_DECODED_BYTES = 32 * 1024
    private const val MAX_CODE_LENGTH = 8_000
    private const val VERSION = 1
    private const val SCALE = 100_000.0

    data class SharedRoute(
        val name: String,
        val transportMode: TransportMode,
        val points: List<RoutePoint>
    )

    fun encode(route: SharedRoute): String {
        require(route.points.size in MIN_POINTS..MAX_POINTS) { "A shared route needs $MIN_POINTS..$MAX_POINTS points" }
        val raw = ByteArrayOutputStream()
        raw.write('P'.code)
        raw.write('R'.code)
        raw.write(VERSION)
        raw.write(if (route.transportMode == TransportMode.DRIVING) 1 else 0)
        writeString(raw, route.name.take(MAX_ROUTE_NAME))
        writeVarint(raw, route.points.size.toLong())
        var previousLat = 0L
        var previousLon = 0L
        route.points.forEach { point ->
            val lat = (point.latitude * SCALE).roundToLong()
            val lon = (point.longitude * SCALE).roundToLong()
            writeVarint(raw, zigZag(lat - previousLat))
            writeVarint(raw, zigZag(lon - previousLon))
            writeString(raw, point.name.orEmpty().take(MAX_POINT_NAME))
            previousLat = lat
            previousLon = lon
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(deflate(raw.toByteArray()))
    }

    fun decode(code: String): SharedRoute? = runCatching {
        val trimmed = code.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_CODE_LENGTH) return null
        val bytes = inflate(Base64.getUrlDecoder().decode(trimmed)) ?: return null
        val reader = Reader(bytes)
        if (reader.byte() != 'P'.code || reader.byte() != 'R'.code) return null
        if (reader.byte() != VERSION) return null
        val transport = if (reader.byte() == 1) TransportMode.DRIVING else TransportMode.WALKING
        val name = sanitize(reader.string(MAX_ROUTE_NAME * 4)).ifBlank { return null }
        val count = reader.varint()
        if (count < MIN_POINTS || count > MAX_POINTS) return null
        var lat = 0L
        var lon = 0L
        val points = ArrayList<RoutePoint>(count.toInt())
        repeat(count.toInt()) {
            lat += unZigZag(reader.varint())
            lon += unZigZag(reader.varint())
            val latitude = lat / SCALE
            val longitude = lon / SCALE
            if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
            val pointName = sanitize(reader.string(MAX_POINT_NAME * 4)).takeIf { it.isNotBlank() }
            points += RoutePoint(latitude, longitude, pointName)
        }
        SharedRoute(name = name, transportMode = transport, points = points)
    }.getOrNull()

    fun toLink(code: String): String = "$SCHEME://$HOST?$QUERY_PARAM=$code"

    /** Finds a share code in a pasted link, QR payload or message text. */
    fun extractCode(text: String): String? {
        val trimmed = text.trim()
        val fromLink = Regex("""$SCHEME://$HOST\?(?:[^\s#]*&)?$QUERY_PARAM=([A-Za-z0-9_-]+)""")
            .find(trimmed)?.groupValues?.get(1)
        if (fromLink != null) return fromLink
        return trimmed.takeIf { it.isNotEmpty() && it.length <= MAX_CODE_LENGTH && it.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' } }
    }

    private fun sanitize(value: String): String =
        value.filter { !it.isISOControl() }.trim()

    private fun writeString(out: ByteArrayOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeVarint(out, bytes.size.toLong())
        out.write(bytes)
    }

    private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while (v and 0x7FL.inv() != 0L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write(v.toInt())
    }

    private fun zigZag(value: Long): Long = (value shl 1) xor (value shr 63)

    private fun unZigZag(value: Long): Long = (value ushr 1) xor -(value and 1)

    private fun deflate(input: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        try {
            deflater.setInput(input)
            deflater.finish()
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (!deflater.finished()) {
                val n = deflater.deflate(buffer)
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun inflate(input: ByteArray): ByteArray? {
        val inflater = Inflater(true)
        try {
            inflater.setInput(input)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                // Truncated or malformed data: stop, the parser then rejects the partial bytes.
                if (n == 0) break
                out.write(buffer, 0, n)
                if (out.size() > MAX_DECODED_BYTES) return null
            }
            return out.toByteArray()
        } finally {
            inflater.end()
        }
    }

    private class Reader(private val bytes: ByteArray) {
        private var position = 0

        fun byte(): Int {
            if (position >= bytes.size) throw IllegalArgumentException("Truncated route")
            return bytes[position++].toInt() and 0xFF
        }

        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (shift < 64) {
                val b = byte()
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
            throw IllegalArgumentException("Bad varint")
        }

        fun string(maxBytes: Int): String {
            val length = varint()
            if (length < 0 || length > maxBytes || position + length > bytes.size) {
                throw IllegalArgumentException("Bad string")
            }
            val value = String(bytes, position, length.toInt(), Charsets.UTF_8)
            position += length.toInt()
            return value
        }
    }
}
