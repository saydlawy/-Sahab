package com.saydlawy.sahab.util

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.res.Configuration
import com.saydlawy.sahab.data.ApiException
import java.util.Date
import java.util.Locale

fun isTelevision(context: Context): Boolean {
    if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return true
    val ui = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun Throwable.userMessage(): String = (this as? ApiException)?.message ?: Ar.errUnknown

fun appVersion(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
} catch (e: Exception) {
    ""
}

/** Lower-cases and removes Arabic diacritics and letter variants so searching is forgiving. */
fun String.normalizeForSearch(): String {
    val out = StringBuilder(length)
    for (c in lowercase(Locale.ROOT)) {
        when {
            c == 'أ' || c == 'إ' || c == 'آ' -> out.append('ا')
            c == 'ى' -> out.append('ي')
            c == 'ة' -> out.append('ه')
            c == 'ـ' || c in 'ً'..'ْ' -> Unit
            else -> out.append(c)
        }
    }
    return out.toString()
}

fun formatClock(epochSeconds: Long): String =
    java.text.SimpleDateFormat("HH:mm", Locale.US).format(Date(epochSeconds * 1000))

fun formatDate(epochSeconds: Long): String =
    java.text.SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(epochSeconds * 1000))

fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}
