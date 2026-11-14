package com.saydlawy.sahab

import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        // شاشة البداية ستختفي تلقائياً بمجرد رسم أول إطار للواجهة
        enableEdgeToEdge()
        val container = (application as SahabApp).container
        val tv = isTelevision(this)

        checkForUpdates()

        setContent {
            // طلب إذن الإشعارات لأندرويد 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted -> /* Handle permission */ }
                LaunchedEffect(Unit) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            // رفع FCM Token إلى السحابة
            LaunchedEffect(Unit) {
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        CoroutineScope(Dispatchers.IO).launch {
                            (application as SahabApp).container.userSyncService.saveFcmToken(token)
                        }
                    }
                }
            }

            SahabTheme {
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
