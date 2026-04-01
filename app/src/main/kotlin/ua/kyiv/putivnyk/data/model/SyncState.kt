package ua.kyiv.putivnyk.data.model

import ua.kyiv.putivnyk.data.local.entity.SyncStateEntity

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
