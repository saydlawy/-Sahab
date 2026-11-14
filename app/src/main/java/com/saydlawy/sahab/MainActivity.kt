package com.saydlawy.sahab

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.Firebase
import com.google.firebase.appdistribution.appDistribution
import com.saydlawy.sahab.ui.AppRoot
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.components.BrandLogo
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.ui.theme.SahabTheme
import com.saydlawy.sahab.util.isTelevision
import kotlinx.coroutines.delay

class MainActivity : AppCompatActivity() {
    var showSplash by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            SahabTheme {
                if (showSplash) {
                    SplashScreenContent {
                        showSplash = false
                    }
                } else {
                    LaunchedEffect(Unit) {
                        delay(3000L)
                        checkForUpdates()
                    }
                    CompositionLocalProvider(
                        LocalContainer provides container,
                        LocalIsTv provides tv,
                    ) {
                        AppRoot(container)
                    }
                }
            }
        }
    }

    private fun checkForUpdates() {
        try {
            Firebase.appDistribution.updateIfNewReleaseAvailable()
                .addOnSuccessListener {
                    Log.d("SahabApp", "Firebase App Distribution update check completed")
                }
                .addOnFailureListener { e ->
                    Log.w("SahabApp", "Firebase App Distribution update check failed: ${e.message}")
                }
        } catch (e: Exception) {
            Log.w("SahabApp", "Unable to check for App Distribution updates", e)
        }
    }
}

@Composable
fun SplashScreenContent(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1500L)
        onTimeout()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.DeepBlue),
        contentAlignment = Alignment.Center
    ) {
        BrandLogo(size = 120.dp, bright = true)
    }
}
