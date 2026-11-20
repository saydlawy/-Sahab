package com.saydlawy.sahab

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import com.saydlawy.sahab.ui.player.SahabMediaSessionBridge
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
        hideSystemBarsNow()
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

    private fun hideSystemBarsNow() {
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        if (ev != null && !isTelevision(this)) {
            val action = ev.actionMasked
            if (action == android.view.MotionEvent.ACTION_DOWN || action == android.view.MotionEvent.ACTION_POINTER_DOWN) {
                hideSystemBarsNow()
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            val keyName = android.view.KeyEvent.keyCodeToString(event.keyCode)
            val touchMode = window?.decorView?.isInTouchMode
            android.util.Log.d("SAHAB_FOCUS", "KEY code=$keyName touchMode=$touchMode")
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBarsNow()
        }
    }

    private fun checkForUpdates() {
        if (BuildConfig.DEBUG) {
            ReleaseUpdateChecker.checkForUpdates()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isTelevision(this) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            SahabMediaSessionBridge.pipAllowed &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) {
            runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build(),
                )
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        SahabMediaSessionBridge.inPipMode.value = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        (application as? SahabApp)?.container?.flushRecents()
    }
}
