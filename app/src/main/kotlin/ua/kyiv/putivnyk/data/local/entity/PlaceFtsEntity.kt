package ua.kyiv.putivnyk.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = PlaceEntity::class)
@Entity(tableName = "places_fts")
data class PlaceFtsEntity(
    val name: String,
    val nameEn: String?,
    val description: String?
)
