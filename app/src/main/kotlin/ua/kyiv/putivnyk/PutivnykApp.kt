package ua.kyiv.putivnyk

import android.app.Application
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import ua.kyiv.putivnyk.data.repository.AchievementsRepository
import ua.kyiv.putivnyk.data.seed.SeedManager
import ua.kyiv.putivnyk.di.AppCoroutineScope
import ua.kyiv.putivnyk.i18n.AppText
import ua.kyiv.putivnyk.navigation.NavigationEffects
import ua.kyiv.putivnyk.notifications.AppNotifications
import ua.kyiv.putivnyk.sync.SyncScheduler
import ua.kyiv.putivnyk.tracking.GeofenceRegistrar
import ua.kyiv.putivnyk.widget.WidgetUpdater
import androidx.work.WorkManager
import javax.inject.Inject

@HiltAndroidApp
class PutivnykApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workConfiguration: Configuration

    @Inject
    lateinit var seedManager: SeedManager

    @Inject
    lateinit var workManager: WorkManager

    @Inject
    lateinit var appScope: AppCoroutineScope

    @Inject
    lateinit var appNotifications: AppNotifications

    @Inject
    lateinit var achievementsRepository: AchievementsRepository

    @Inject
    lateinit var navigationEffects: NavigationEffects

    @Inject
    lateinit var geofenceRegistrar: GeofenceRegistrar

    @Inject
    lateinit var widgetUpdater: WidgetUpdater

    @Inject
    lateinit var appText: AppText

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this
        applicationScope.launch(context = Dispatchers.IO, start = CoroutineStart.DEFAULT) {
            seedManager.seedDatabase()
            // Widgets placed before the places were seeded (or before an update) get their content.
            runCatching { widgetUpdater.updateAll() }
        }

        appNotifications.ensureChannels()
        appScope.launch { runCatching { appNotifications.localizeChannels(appText) } }
        achievementsRepository.startObserving(appScope)
        navigationEffects.start(appScope)
        geofenceRegistrar.startObserving(appScope)

        SyncScheduler.ensurePeriodicEventsSync(workManager)
        SyncScheduler.ensurePeriodicOfflineCacheSync(workManager)
        SyncScheduler.ensureTranslationModelSync(workManager)
        // Map tiles are not bulk-prefetched (the OSM tile usage policy discourages bulk downloads);
        // without a connection the map switches to the bundled offline map (assets/maps/kyiv.map).
    }

    override val workManagerConfiguration: Configuration
        get() = workConfiguration

    override fun newImageLoader(): ImageLoader {
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "PutivnykApp/1.0 (kyiv-tourist-guide; Android)")
                        .build()
                )
            }
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(httpClient)
            .crossfade(true)
            .build()
    }

    companion object {
        lateinit var instance: PutivnykApp
            private set
    }
}
