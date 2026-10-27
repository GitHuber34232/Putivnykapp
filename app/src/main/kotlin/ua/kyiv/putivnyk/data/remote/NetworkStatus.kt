package ua.kyiv.putivnyk.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** True when the active network has validated internet access. */
fun Context.isOnlineNow(): Boolean {
    val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return true
    val capabilities = runCatching {
        connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
    }.getOrNull() ?: return false
    return capabilities.hasValidatedInternet()
}

fun NetworkCapabilities.hasValidatedInternet(): Boolean =
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
