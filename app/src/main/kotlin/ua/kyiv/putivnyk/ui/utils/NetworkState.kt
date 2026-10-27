package ua.kyiv.putivnyk.ui.utils

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import ua.kyiv.putivnyk.data.remote.hasValidatedInternet
import ua.kyiv.putivnyk.data.remote.isOnlineNow

/** Whether the device currently has a validated internet connection; updates live. */
@Composable
fun rememberIsOnline(): State<Boolean> {
    val context = LocalContext.current
    return produceState(initialValue = context.isOnlineNow(), context) {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
            ?: return@produceState
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                value = capabilities.hasValidatedInternet()
            }

            override fun onLost(network: Network) {
                value = false
            }
        }
        val registered = runCatching { connectivityManager.registerDefaultNetworkCallback(callback) }.isSuccess
        awaitDispose {
            if (registered) runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        }
    }
}
