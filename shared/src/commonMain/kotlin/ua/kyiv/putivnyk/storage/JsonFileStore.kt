package ua.kyiv.putivnyk.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import ua.kyiv.putivnyk.platform.io.FileSystemProvider

class JsonFileStore<T>(
    private val fileSystemProvider: FileSystemProvider,
    private val path: String,
    private val serializer: KSerializer<T>,
    defaultValue: () -> T,
) {
    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        isLenient = true
    }
    private val state = MutableStateFlow(loadInitial(defaultValue))

    fun observe(): StateFlow<T> = state

    fun snapshot(): T = state.value

    suspend fun update(transform: (T) -> T): T = mutex.withLock {
        val updated = transform(state.value)
        persist(updated)
        state.value = updated
        updated
    }

    suspend fun set(value: T): T = update { value }

    private fun loadInitial(defaultValue: () -> T): T {
        val raw = fileSystemProvider.readText(path) ?: return defaultValue()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrElse { defaultValue() }
    }

    private fun persist(value: T) {
        val directory = path.substringBeforeLast('/', missingDelimiterValue = "")
        if (directory.isNotBlank()) {
            fileSystemProvider.ensureDirectory(directory)
        }
        fileSystemProvider.writeText(path, json.encodeToString(serializer, value))
    }
}