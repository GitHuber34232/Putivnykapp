package ua.kyiv.putivnyk.data.model

import kotlinx.serialization.Serializable
import ua.kyiv.putivnyk.platform.currentTimeMillis

@Serializable
enum class SyncStatus {
    IDLE,
    RUNNING,
    SUCCESS,
    ERROR;

    companion object {
        fun from(value: String): SyncStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: IDLE
    }
}

@Serializable
data class SyncState(
    val entityName: String,
    val lastSyncAt: Long = 0,
    val status: SyncStatus = SyncStatus.IDLE,
    val lastError: String? = null,
    val updatedAt: Long = currentTimeMillis()
)