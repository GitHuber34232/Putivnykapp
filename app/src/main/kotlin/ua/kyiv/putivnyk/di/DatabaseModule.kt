package ua.kyiv.putivnyk.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ua.kyiv.putivnyk.data.local.DatabaseMigrations
import ua.kyiv.putivnyk.data.local.PutivnykDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PutivnykDatabase {
        return Room.databaseBuilder(
            context,
            PutivnykDatabase::class.java,
            "putivnyk.db"
        )
            .addMigrations(*DatabaseMigrations.ALL)
            .build()
    }

    @Provides
    fun providePlaceDao(database: PutivnykDatabase) = database.placeDao()

    @Provides
    fun provideRouteDao(database: PutivnykDatabase) = database.routeDao()

    @Provides
    fun provideMapBookmarkDao(database: PutivnykDatabase) = database.mapBookmarkDao()

    @Provides
    fun provideLocalizationDao(database: PutivnykDatabase) = database.localizationDao()

    @Provides
    fun provideUserPreferenceDao(database: PutivnykDatabase) = database.userPreferenceDao()

    @Provides
    fun provideSyncStateDao(database: PutivnykDatabase) = database.syncStateDao()

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()
}
