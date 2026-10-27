package ua.kyiv.putivnyk.data.remote.events

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ua.kyiv.putivnyk.data.remote.events.model.EventItem
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Last successfully fetched events per language, so the Events tab works offline. */
@Singleton
class EventsCache @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val gson: Gson
) {
    data class Snapshot(
        val savedAt: Long,
        val items: List<EventItem>
    )

    // Stored shape with nullable fields: Gson ignores Kotlin nullability when reading files.
    private data class StoredSnapshot(
        val savedAt: Long? = null,
        val items: List<StoredEvent?>? = null
    )

    private data class StoredEvent(
        val id: String? = null,
        val title: String? = null,
        val description: String? = null,
        val category: String? = null,
        val startsAt: String? = null,
        val endsAt: String? = null,
        val locationName: String? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val ticketUrl: String? = null,
        val priceLabel: String? = null,
        val coverUrl: String? = null
    )

    private val mutex = Mutex()
    private val cacheDir get() = File(context.filesDir, "events_cache")

    private fun fileFor(language: String) = File(cacheDir, "events_${language.lowercase()}.json")

    suspend fun read(language: String): Snapshot? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = fileFor(language)
            if (!file.exists()) return@withLock null
            val stored = runCatching { gson.fromJson(file.readText(), StoredSnapshot::class.java) }
                .getOrNull()
                ?: return@withLock null
            val items = stored.items.orEmpty().mapNotNull { it?.toEventItem() }
            Snapshot(savedAt = stored.savedAt ?: 0L, items = items)
        }
    }

    suspend fun write(language: String, items: List<EventItem>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                cacheDir.mkdirs()
                val target = fileFor(language)
                val temp = File(cacheDir, "${target.name}.tmp")
                val stored = StoredSnapshot(
                    savedAt = System.currentTimeMillis(),
                    items = items.map { it.toStoredEvent() }
                )
                temp.writeText(gson.toJson(stored))
                if (!temp.renameTo(target)) {
                    target.delete()
                    temp.renameTo(target)
                }
            }
        }
    }

    private fun StoredEvent.toEventItem(): EventItem? {
        val safeId = id ?: return null
        val safeTitle = title ?: return null
        return EventItem(
            id = safeId,
            title = safeTitle,
            description = description.orEmpty(),
            category = category.orEmpty(),
            startsAt = startsAt.orEmpty(),
            endsAt = endsAt.orEmpty(),
            locationName = locationName.orEmpty(),
            latitude = latitude,
            longitude = longitude,
            ticketUrl = ticketUrl,
            priceLabel = priceLabel.orEmpty(),
            coverUrl = coverUrl
        )
    }

    private fun EventItem.toStoredEvent(): StoredEvent = StoredEvent(
        id = id,
        title = title,
        description = description,
        category = category,
        startsAt = startsAt,
        endsAt = endsAt,
        locationName = locationName,
        latitude = latitude,
        longitude = longitude,
        ticketUrl = ticketUrl,
        priceLabel = priceLabel,
        coverUrl = coverUrl
    )
}
