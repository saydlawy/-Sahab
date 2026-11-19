package com.saydlawy.sahab

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.saydlawy.sahab.data.auth.UserSyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        savePendingToken(this, token)
        val hasUser = runCatching { FirebaseAuth.getInstance().currentUser != null }.getOrDefault(false)
        if (hasUser) {
            serviceScope.launch {
                val syncService = (applicationContext as? SahabApp)?.container?.userSyncService ?: UserSyncService()
                runCatching { syncService.saveFcmToken(token) }
                    .onSuccess { Log.d("SahabFcm", "FCM token synced to Firestore") }
                    .onFailure { Log.w("SahabFcm", "Failed to sync FCM token: ${it.message}") }
            }
        }
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        val title = data["title"]?.takeIf { it.isNotBlank() }
            ?: remoteMessage.notification?.title?.takeIf { it.isNotBlank() }
            ?: "سحاب"
        val body = data["body"]?.takeIf { it.isNotBlank() }
            ?: data["message"]?.takeIf { it.isNotBlank() }
            ?: remoteMessage.notification?.body?.takeIf { it.isNotBlank() }
            ?: "لديك تحديث جديد"
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val channelId = CHANNEL_ID
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "إشعارات سحاب",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات الأفلام والمباريات والتحديثات من سحاب"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val requestCode = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationId = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    companion object {
        private const val CHANNEL_ID = "sahab_notifications"
        private const val FCM_PREFS = "sahab_fcm_prefs"
        private const val KEY_FCM_TOKEN = "pending_fcm_token"

        fun savePendingToken(context: Context, token: String) {
            if (token.isBlank()) return
            context.applicationContext
                .getSharedPreferences(FCM_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_FCM_TOKEN, token)
                .apply()
        }

        fun getSavedToken(context: Context): String? =
            context.applicationContext
                .getSharedPreferences(FCM_PREFS, Context.MODE_PRIVATE)
                .getString(KEY_FCM_TOKEN, null)
    }
}
