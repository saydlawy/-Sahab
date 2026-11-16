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

private val dateFormatCache: ThreadLocal<MutableMap<String, java.text.SimpleDateFormat>> =
    ThreadLocal.withInitial { HashMap() }

private fun cachedDateFormat(
    pattern: String,
    locale: Locale = Locale.US,
    timeZone: java.util.TimeZone = java.util.TimeZone.getDefault(),
): java.text.SimpleDateFormat {
    val key = "$pattern|${locale.toLanguageTag()}|${timeZone.id}"
    val map = dateFormatCache.get()!!
    return map.getOrPut(key) {
        java.text.SimpleDateFormat(pattern, locale).apply { this.timeZone = timeZone }
    }
}

fun formatClock(epochSeconds: Long): String =
    cachedDateFormat("HH:mm", Locale.US).format(Date(epochSeconds * 1000))

fun formatDate(epochSeconds: Long): String =
    cachedDateFormat("yyyy/MM/dd", Locale.US).format(Date(epochSeconds * 1000))

fun formatNowMillis(epochMillis: Long, pattern: String = "HH:mm", locale: Locale = Locale.US): String =
    cachedDateFormat(pattern, locale).format(Date(epochMillis))

fun formatDayOffsetYmd(offsetDays: Int): String {
    val calendar = java.util.Calendar.getInstance()
    calendar.add(java.util.Calendar.DAY_OF_YEAR, offsetDays)
    return cachedDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
}

fun utcDateToLocalYmd(utcDate: String, utcTime: String): String {
    val cleanDate = utcDate.trim().take(10)
    if (cleanDate.length < 10) return utcDate
    val hm = utcTime.trim().take(5)
    if (hm.length < 5 || hm == "null") return cleanDate
    return try {
        val utc = java.util.TimeZone.getTimeZone("UTC")
        val parsed = cachedDateFormat("yyyy-MM-dd HH:mm", Locale.US, utc).parse("$cleanDate $hm")
        if (parsed != null) {
            cachedDateFormat("yyyy-MM-dd", Locale.US, java.util.TimeZone.getDefault()).format(parsed)
        } else {
            cleanDate
        }
    } catch (_: Exception) {
        cleanDate
    }
}

fun formatUtcClockToLocal(rawTime: String, dateStr: String = ""): String {
    val clean = rawTime.trim()
    if (clean.isBlank() || clean == "null") {
        return ""
    }
    val hm = clean.take(5)
    return try {
        val utc = java.util.TimeZone.getTimeZone("UTC")
        val effectiveDate = dateStr.trim().take(10).ifBlank {
            cachedDateFormat("yyyy-MM-dd", Locale.US, utc).format(Date())
        }
        val parsed = cachedDateFormat("yyyy-MM-dd HH:mm", Locale.US, utc).parse("$effectiveDate $hm")
        if (parsed != null) {
            cachedDateFormat("HH:mm", Locale.US, java.util.TimeZone.getDefault()).format(parsed)
        } else {
            hm.lowercase(Locale.US)
        }
    } catch (_: Exception) {
        hm.lowercase(Locale.US)
    }
}

fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}
