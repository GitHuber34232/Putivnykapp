package ua.kyiv.putivnyk

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import ua.kyiv.putivnyk.ui.theme.PutivnykTheme
import ua.kyiv.putivnyk.ui.viewmodel.AppearanceViewModel
import ua.kyiv.putivnyk.ui.PutivnykApp as PutivnykAppContent

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appearanceViewModel: AppearanceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Keep the splash until the saved theme is known, so the app never flashes the wrong theme,
        // but never longer than SPLASH_MAX_MS in case the database is slow to open.
        val splashStart = SystemClock.uptimeMillis()
        splashScreen.setKeepOnScreenCondition {
            appearanceViewModel.appearance.value == null &&
                SystemClock.uptimeMillis() - splashStart < SPLASH_MAX_MS
        }
        enableEdgeToEdge()

        setContent {
            val appearance by appearanceViewModel.appearance.collectAsState()
            val current = appearance ?: AppearanceViewModel.Appearance()
            val darkTheme = when (current.themeMode) {
                AppearanceViewModel.ThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppearanceViewModel.ThemeMode.LIGHT -> false
                AppearanceViewModel.ThemeMode.DARK -> true
            }

            PutivnykTheme(darkTheme = darkTheme, dynamicColor = current.dynamicColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PutivnykAppContent()
                }
            }
        }
    }

    private companion object {
        const val SPLASH_MAX_MS = 1_500L
    }
}
