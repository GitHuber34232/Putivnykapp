package ua.kyiv.putivnyk.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ua.kyiv.putivnyk.i18n.AssetUiTranslations
import ua.kyiv.putivnyk.i18n.OnDeviceTranslationService
import ua.kyiv.putivnyk.i18n.TranslationService
import ua.kyiv.putivnyk.i18n.UiTranslationsProvider
import ua.kyiv.putivnyk.platform.io.AndroidAssetTextResourceLoader
import ua.kyiv.putivnyk.platform.io.AndroidFileSystemProvider
import ua.kyiv.putivnyk.platform.io.FileSystemProvider
import ua.kyiv.putivnyk.platform.io.TextResourceLoader
import ua.kyiv.putivnyk.sync.AndroidSyncOrchestrator
import ua.kyiv.putivnyk.sync.SyncOrchestrator
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformServiceBindingsModule {

    @Binds
    @Singleton
    abstract fun bindTranslationService(impl: OnDeviceTranslationService): TranslationService

    @Binds
    @Singleton
    abstract fun bindUiTranslationsProvider(impl: AssetUiTranslations): UiTranslationsProvider

    @Binds
    @Singleton
    abstract fun bindTextResourceLoader(impl: AndroidAssetTextResourceLoader): TextResourceLoader

    @Binds
    @Singleton
    abstract fun bindFileSystemProvider(impl: AndroidFileSystemProvider): FileSystemProvider

    @Binds
    @Singleton
    abstract fun bindSyncOrchestrator(impl: AndroidSyncOrchestrator): SyncOrchestrator
}