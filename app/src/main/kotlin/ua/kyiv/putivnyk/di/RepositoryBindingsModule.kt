package ua.kyiv.putivnyk.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ua.kyiv.putivnyk.data.repository.EventsRepository
import ua.kyiv.putivnyk.data.repository.LocalizationRepository
import ua.kyiv.putivnyk.data.repository.MapBookmarkRepository
import ua.kyiv.putivnyk.data.repository.PlaceRepository
import ua.kyiv.putivnyk.data.repository.RoomLocalizationRepository
import ua.kyiv.putivnyk.data.repository.RoomMapBookmarkRepository
import ua.kyiv.putivnyk.data.repository.RoomPlaceRepository
import ua.kyiv.putivnyk.data.repository.RoomRouteRepository
import ua.kyiv.putivnyk.data.repository.RoomSyncStateRepository
import ua.kyiv.putivnyk.data.repository.RoomUserPreferenceRepository
import ua.kyiv.putivnyk.data.repository.RouteRepository
import ua.kyiv.putivnyk.data.repository.SyncStateRepository
import ua.kyiv.putivnyk.data.repository.UserPreferenceRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindingsModule {

    @Binds
    @Singleton
    abstract fun bindPlaceRepository(impl: RoomPlaceRepository): PlaceRepository

    @Binds
    @Singleton
    abstract fun bindMapBookmarkRepository(impl: RoomMapBookmarkRepository): MapBookmarkRepository

    @Binds
    @Singleton
    abstract fun bindRouteRepository(impl: RoomRouteRepository): RouteRepository

    @Binds
    @Singleton
    abstract fun bindUserPreferenceRepository(impl: RoomUserPreferenceRepository): UserPreferenceRepository

    @Binds
    @Singleton
    abstract fun bindLocalizationRepository(impl: RoomLocalizationRepository): LocalizationRepository

    @Binds
    @Singleton
    abstract fun bindSyncStateRepository(impl: RoomSyncStateRepository): SyncStateRepository
}