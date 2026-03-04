package ua.kyiv.putivnyk.data.telemetry

import android.util.Log
import ua.kyiv.putivnyk.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

interface AppTelemetry {
    fun trackEvent(name: String, attributes: Map<String, String> = emptyMap())
    fun trackWarning(name: String, throwable: Throwable? = null, attributes: Map<String, String> = emptyMap())
    fun trackError(name: String, throwable: Throwable? = null, attributes: Map<String, String> = emptyMap())
}

@Singleton
class LogcatTelemetry @Inject constructor() : AppTelemetry {
    override fun trackEvent(name: String, attributes: Map<String, String>) {
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "event=$name attrs=$attributes")
        }
    }

    override fun trackWarning(name: String, throwable: Throwable?, attributes: Map<String, String>) {
        if (BuildConfig.DEBUG) {
            Log.w(TAG, "warn=$name attrs=$attributes", throwable)
        } else {
            Log.w(TAG, "warn=$name")
        }
    }

    override fun trackError(name: String, throwable: Throwable?, attributes: Map<String, String>) {
        if (BuildConfig.DEBUG) {
            Log.e(TAG, "error=$name attrs=$attributes", throwable)
        } else {
            Log.e(TAG, "error=$name")
        }
    }

    private companion object {
        const val TAG = "PutivnykTelemetry"
    }
}
