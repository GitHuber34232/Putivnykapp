package ua.kyiv.putivnyk.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ua.kyiv.putivnyk.di.AppCoroutineScope
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** "Nearby": the closest places to the user's last known position. */
@AndroidEntryPoint
class NearbyWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var widgetUpdater: WidgetUpdater
    @Inject lateinit var appScope: AppCoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WidgetUpdater.ACTION_REFRESH) update()
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) = update()

    override fun onEnabled(context: Context) {
        WidgetRefreshWorker.schedule(WorkManager.getInstance(context))
    }

    override fun onDisabled(context: Context) {
        WidgetRefreshWorker.cancelIfUnused(context)
    }

    private fun update() {
        val pending = goAsync()
        appScope.launch {
            try {
                runCatching { widgetUpdater.updateNearby() }
            } finally {
                pending.finish()
            }
        }
    }
}

/** "Route of the day": a short walk through well-rated places, new every day. */
@AndroidEntryPoint
class DailyRouteWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var widgetUpdater: WidgetUpdater
    @Inject lateinit var appScope: AppCoroutineScope

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        appScope.launch {
            try {
                runCatching { widgetUpdater.updateDailyRoute() }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        WidgetRefreshWorker.schedule(WorkManager.getInstance(context))
    }

    override fun onDisabled(context: Context) {
        WidgetRefreshWorker.cancelIfUnused(context)
    }
}

/** Hourly refresh: new day → new route of the day; "Nearby" follows the last known location. */
@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val widgetUpdater: WidgetUpdater
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        runCatching { widgetUpdater.updateAll() }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "widgets_refresh"

        fun schedule(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(1, TimeUnit.HOURS).build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** The last widget of either kind was removed: no more hourly refreshes. */
        fun cancelIfUnused(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val remaining = listOf(NearbyWidgetProvider::class.java, DailyRouteWidgetProvider::class.java)
                .sumOf { manager.getAppWidgetIds(ComponentName(context, it)).size }
            if (remaining == 0) WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
