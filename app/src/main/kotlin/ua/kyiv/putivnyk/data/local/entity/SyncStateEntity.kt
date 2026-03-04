package ua.kyiv.putivnyk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey
    val entityName: String,
    val lastSyncAt: Long = 0,
    val status: String = "IDLE",
    val lastError: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
