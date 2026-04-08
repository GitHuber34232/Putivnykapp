package ua.kyiv.putivnyk.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ua.kyiv.putivnyk.data.local.dao.LocalizationDao
import ua.kyiv.putivnyk.data.local.dao.MapBookmarkDao
import ua.kyiv.putivnyk.data.local.dao.PlaceDao
import ua.kyiv.putivnyk.data.local.dao.RouteDao
import ua.kyiv.putivnyk.data.local.dao.SyncStateDao
import ua.kyiv.putivnyk.data.local.dao.UserPreferenceDao
import ua.kyiv.putivnyk.data.local.entity.LocalizedStringEntity
import ua.kyiv.putivnyk.data.local.entity.MapBookmarkEntity
import ua.kyiv.putivnyk.data.local.entity.PlaceEntity
import ua.kyiv.putivnyk.data.local.entity.PlaceFtsEntity
import ua.kyiv.putivnyk.data.local.entity.RouteEntity
import ua.kyiv.putivnyk.data.local.entity.SyncStateEntity
import ua.kyiv.putivnyk.data.local.entity.UserPreferenceEntity

@Database(
    entities = [
        PlaceEntity::class,
        PlaceFtsEntity::class,
        RouteEntity::class,
        MapBookmarkEntity::class,
        LocalizedStringEntity::class,
        UserPreferenceEntity::class,
        SyncStateEntity::class
    ],
    version = 8,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class PutivnykDatabase : RoomDatabase() {
    abstract fun placeDao(): PlaceDao
    abstract fun routeDao(): RouteDao
    abstract fun mapBookmarkDao(): MapBookmarkDao
    abstract fun localizationDao(): LocalizationDao
    abstract fun userPreferenceDao(): UserPreferenceDao
    abstract fun syncStateDao(): SyncStateDao
}
