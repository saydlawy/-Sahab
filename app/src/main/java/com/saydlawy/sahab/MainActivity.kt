package com.saydlawy.sahab

import android.os.Bundle
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
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SahabApp).container
        val tv = isTelevision(this)

        checkForUpdates()

        setContent {
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
