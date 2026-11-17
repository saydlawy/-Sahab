package com.saydlawy.sahab

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.appdistribution.appDistribution

object ReleaseUpdateChecker {
    fun checkForUpdates() {
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
