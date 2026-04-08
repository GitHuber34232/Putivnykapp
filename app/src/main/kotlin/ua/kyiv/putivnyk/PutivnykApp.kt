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
import ua.kyiv.putivnyk.data.local.OfflineTileCacheManager
import ua.kyiv.putivnyk.data.seed.SeedManager
import ua.kyiv.putivnyk.sync.SyncScheduler
import androidx.work.WorkManager
import ua.kyiv.putivnyk.BuildConfig
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
    lateinit var offlineTileCacheManager: OfflineTileCacheManager

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this
        applicationScope.launch(context = Dispatchers.IO, start = CoroutineStart.DEFAULT) {
            seedManager.seedDatabase()
        }

        SyncScheduler.ensurePeriodicEventsSync(workManager)
        SyncScheduler.ensurePeriodicOfflineCacheSync(workManager)
        SyncScheduler.ensureTranslationModelSync(workManager)

        offlineTileCacheManager.ensureCached(BuildConfig.MAPLIBRE_STYLE_URI)
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
