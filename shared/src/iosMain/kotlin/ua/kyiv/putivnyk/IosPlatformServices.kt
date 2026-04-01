package ua.kyiv.putivnyk

import ua.kyiv.putivnyk.i18n.BundleUiTranslationsProvider
import ua.kyiv.putivnyk.i18n.IosTranslationServiceStub
import ua.kyiv.putivnyk.i18n.TranslationService
import ua.kyiv.putivnyk.i18n.UiTranslationsProvider
import ua.kyiv.putivnyk.platform.io.BundleTextResourceLoader
import ua.kyiv.putivnyk.platform.io.DocumentsFileSystemProvider
import ua.kyiv.putivnyk.platform.io.FileSystemProvider
import ua.kyiv.putivnyk.platform.io.TextResourceLoader

class IosPlatformServices {
    fun translationService(): TranslationService = IosTranslationServiceStub()

    fun uiTranslationsProvider(): UiTranslationsProvider = BundleUiTranslationsProvider()

    fun textResourceLoader(): TextResourceLoader = BundleTextResourceLoader()

    fun fileSystemProvider(): FileSystemProvider = DocumentsFileSystemProvider()

    fun runtimeVerifier(): IosRuntimeVerifier = IosRuntimeVerifier()
}