package ua.kyiv.putivnyk.data.telemetry

interface AppTelemetry {
    fun trackEvent(name: String, attributes: Map<String, String> = emptyMap())
    fun trackWarning(name: String, throwable: Throwable? = null, attributes: Map<String, String> = emptyMap())
    fun trackError(name: String, throwable: Throwable? = null, attributes: Map<String, String> = emptyMap())
}