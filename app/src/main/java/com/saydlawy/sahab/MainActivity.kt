package com.saydlawy.sahab

import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import androidx.compose.runtime.LaunchedEffect
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import com.google.firebase.Firebase
import com.google.firebase.appdistribution.appDistribution
import com.saydlawy.sahab.ui.AppRoot
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.theme.SahabTheme
import com.saydlawy.sahab.util.isTelevision

class MainActivity : AppCompatActivity() {
    var showSplash by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // شاشة البداية ستختفي تلقائياً بمجرد رسم أول إطار للواجهة
        enableEdgeToEdge()
        val container = (application as SahabApp).container
        val tv = isTelevision(this)

        checkForUpdates()

        setContent {
            SahabTheme {
            if (showSplash) {
                SplashScreenContent {
                    showSplash = false
                }
            } else {
                val container = (application as SahabApp).container
                CompositionLocalProvider(
                    LocalContainer provides container,
                    LocalIsTv provides tv,
                ) {
                    AppRoot(container)
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


@androidx.compose.runtime.Composable
fun SplashScreenContent(onTimeout: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1500)
        onTimeout()
    }
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier
            .fillMaxSize()
            .background(com.saydlawy.sahab.ui.theme.Palette.DeepBlue),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        com.saydlawy.sahab.ui.components.BrandLogo(size = 120.dp, bright = true)
    }
}
