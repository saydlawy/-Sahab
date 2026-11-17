package com.saydlawy.sahab

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.saydlawy.sahab.ui.AppRoot
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.theme.SahabTheme
import com.saydlawy.sahab.data.ThemePreferences
import com.saydlawy.sahab.util.isTelevision
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private var isAppReady by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { !isAppReady }

        // سقف أمان 3 ثوانٍ لإزالة شاشة البداية حتى لا يُحجب الإقلاع إذا تأخر التحميل الأولي
        lifecycleScope.launch {
            delay(3_000L)
            isAppReady = true
        }

        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        val container = (application as SahabApp).container
        val tv = isTelevision(this)

        setContent {
            val themeSettings by container.prefs.themePreferences.collectAsState(initial = ThemePreferences())
            SahabTheme(settings = themeSettings) {
                LaunchedEffect(Unit) {
                    delay(3000L)
                    checkForUpdates()
                }
                CompositionLocalProvider(
                    LocalContainer provides container,
                    LocalIsTv provides tv,
                ) {
                    AppRoot(container = container, onReady = { isAppReady = true })
                }
            }
        }
    }

    private fun checkForUpdates() {
        if (BuildConfig.DEBUG) {
            ReleaseUpdateChecker.checkForUpdates()
        }
    }

    override fun onStop() {
        super.onStop()
        (application as? SahabApp)?.container?.flushRecents()
    }
}
