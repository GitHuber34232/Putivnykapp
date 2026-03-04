package ua.kyiv.putivnyk.data.model

import ua.kyiv.putivnyk.data.local.entity.SyncStateEntity

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

data class SyncState(
    val entityName: String,
    val lastSyncAt: Long = 0,
    val status: SyncStatus = SyncStatus.IDLE,
    val lastError: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

fun SyncState.toEntity(): SyncStateEntity = SyncStateEntity(
    entityName = entityName,
    lastSyncAt = lastSyncAt,
    status = status.name,
    lastError = lastError,
    updatedAt = updatedAt
)

fun SyncStateEntity.toDomain(): SyncState = SyncState(
    entityName = entityName,
    lastSyncAt = lastSyncAt,
    status = SyncStatus.from(status),
    lastError = lastError,
    updatedAt = updatedAt
)
