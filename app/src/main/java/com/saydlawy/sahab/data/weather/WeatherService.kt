package com.saydlawy.sahab.data.weather

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale

enum class WeatherCondition {
    CLEAR_DAY,
    CLEAR_NIGHT,
    PARTLY_CLOUDY,
    CLOUDY,
    FOG,
    RAIN,
    SNOW,
    THUNDERSTORM,
}

data class WeatherData(
    val tempC: Int = 24,
    val city: String = "الكويت",
    val condition: WeatherCondition = WeatherCondition.CLEAR_DAY,
    val conditionText: String = "صافٍ",
    val isDay: Boolean = true,
    val isLoaded: Boolean = false,
)

class WeatherService(
    private val client: OkHttpClient,
    context: Context,
) {
    private val prefs = context.getSharedPreferences("sahab_weather_cache", Context.MODE_PRIVATE)

    private val _weather = MutableStateFlow(loadCachedWeather())
    val weather: StateFlow<WeatherData> = _weather.asStateFlow()

    private var lastFetchMs: Long = 0L

    fun getManualCity(): String = prefs.getString("manual_city", "") ?: ""

    suspend fun setManualCity(cityName: String): Boolean {
        val trimmed = cityName.trim()
        if (trimmed.isEmpty()) {
            prefs.edit().remove("manual_city").remove("manual_lat").remove("manual_lon").apply()
            refreshIfNeeded(force = true)
            return true
        }
        val geo = withContext(Dispatchers.IO) {
            runCatching {
                val encoded = java.net.URLEncoder.encode(trimmed, "UTF-8")
                val req = Request.Builder()
                    .url("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=ar")
                    .build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    val arr = JSONObject(body).optJSONArray("results")
                    if (arr != null && arr.length() > 0) {
                        val item = arr.getJSONObject(0)
                        Triple(
                            item.getDouble("latitude").toFloat(),
                            item.getDouble("longitude").toFloat(),
                            item.optString("name", trimmed).ifBlank { trimmed },
                        )
                    } else null
                }
            }.getOrNull()
        } ?: return false
        prefs.edit()
            .putString("manual_city", geo.third)
            .putFloat("manual_lat", geo.first)
            .putFloat("manual_lon", geo.second)
            .apply()
        refreshIfNeeded(force = true)
        return true
    }

    suspend fun refreshIfNeeded(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastFetchMs < 30 * 60 * 1000L && _weather.value.isLoaded) {
            return
        }

        withContext(Dispatchers.IO) {
            try {
                // 1. Manual city override first, else IP geolocation
                val manualCity = prefs.getString("manual_city", "").orEmpty()
                val hasManual = manualCity.isNotBlank() && prefs.contains("manual_lat") && prefs.contains("manual_lon")
                val loc = if (hasManual) null else fetchIpLocation()
                val lat = if (hasManual) prefs.getFloat("manual_lat", 29.37f).toDouble() else (loc?.first ?: 29.37)
                val lon = if (hasManual) prefs.getFloat("manual_lon", 47.98f).toDouble() else (loc?.second ?: 47.98)
                val rawCity = if (hasManual) manualCity else (loc?.third ?: "Kuwait City")

                // 2. Real-time weather from Open-Meteo
                val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,is_day&timezone=auto"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Sahab/1.0 (Android)")
                    .build()

                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) return@withContext
                    val body = res.body?.string() ?: return@withContext
                    val json = JSONObject(body)
                    val current = json.getJSONObject("current")
                    val temp = current.getDouble("temperature_2m").toInt()
                    val code = current.getInt("weather_code")
                    val isDay = current.getInt("is_day") == 1

                    val (condition, text) = mapWmoCode(code, isDay)
                    val arabCity = translateCity(rawCity)

                    val data = WeatherData(
                        tempC = temp,
                        city = arabCity,
                        condition = condition,
                        conditionText = text,
                        isDay = isDay,
                        isLoaded = true,
                    )

                    _weather.value = data
                    lastFetchMs = now
                    saveCachedWeather(data)
                }
            } catch (e: Exception) {
                Log.w("WeatherService", "Weather refresh failed: ${e.message}")
            }
        }
    }

    private fun fetchIpLocation(): Triple<Double, Double, String>? {
        return try {
            val req = Request.Builder()
                .url("https://ipwho.is/?lang=ar")
                .header("User-Agent", "Sahab/1.0 (Android)")
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return null
                val body = res.body?.string() ?: return null
                val json = JSONObject(body)
                if (!json.optBoolean("success", true)) return null
                val lat = json.getDouble("latitude")
                val lon = json.getDouble("longitude")
                val city = json.optString("city", "Kuwait City")
                Triple(lat, lon, city)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun mapWmoCode(code: Int, isDay: Boolean): Pair<WeatherCondition, String> {
        return when (code) {
            0 -> if (isDay) (WeatherCondition.CLEAR_DAY to "صافٍ") else (WeatherCondition.CLEAR_NIGHT to "سماء صافية")
            1, 2 -> (WeatherCondition.PARTLY_CLOUDY to "غائم جزئياً")
            3 -> (WeatherCondition.CLOUDY to "غائم")
            45, 48 -> (WeatherCondition.FOG to "ضباب")
            51, 53, 55, 56, 57 -> (WeatherCondition.RAIN to "رذاذ خفيف")
            61, 63, 65, 66, 67 -> (WeatherCondition.RAIN to "أمطار")
            71, 73, 75, 77, 85, 86 -> (WeatherCondition.SNOW to "تساقط ثلوج")
            80, 81, 82 -> (WeatherCondition.RAIN to "زخات مطر")
            95, 96, 99 -> (WeatherCondition.THUNDERSTORM to "عواصف رعدية")
            else -> if (isDay) (WeatherCondition.CLEAR_DAY to "معتدل") else (WeatherCondition.CLEAR_NIGHT to "معتدل")
        }
    }

    private fun translateCity(name: String): String {
        val norm = name.trim().lowercase(Locale.US)
        return when {
            norm.contains("kuwait") -> "الكويت"
            norm.contains("cairo") -> "القاهرة"
            norm.contains("giza") -> "الجيزة"
            norm.contains("alexandria") -> "الإسكندرية"
            norm.contains("riyadh") -> "الرياض"
            norm.contains("jeddah") -> "جدة"
            norm.contains("mecca") || norm.contains("makkah") -> "مكة المكرمة"
            norm.contains("medina") || norm.contains("madinah") -> "المدينة المنورة"
            norm.contains("dammam") -> "الدمام"
            norm.contains("dubai") -> "دبي"
            norm.contains("abu dhabi") -> "أبوظبي"
            norm.contains("sharjah") -> "الشارقة"
            norm.contains("doha") -> "الدوحة"
            norm.contains("manama") -> "المنامة"
            norm.contains("muscat") -> "مسقط"
            norm.contains("amman") -> "عمّان"
            norm.contains("beirut") -> "بيروت"
            norm.contains("baghdad") -> "بغداد"
            norm.contains("damascus") -> "دمشق"
            norm.contains("tunis") -> "تونس"
            norm.contains("algiers") -> "الجزائر"
            norm.contains("rabat") -> "الرباط"
            norm.contains("casablanca") -> "الدار البيضاء"
            norm.contains("tripoli") -> "طرابلس"
            norm.contains("khartoum") -> "الخرطوم"
            norm.contains("sanaa") -> "صنعاء"
            else -> name
        }
    }

    private fun loadCachedWeather(): WeatherData {
        val temp = prefs.getInt("cached_temp", 24)
        val city = prefs.getString("cached_city", "الكويت") ?: "الكويت"
        val codeName = prefs.getString("cached_cond", WeatherCondition.CLEAR_DAY.name) ?: WeatherCondition.CLEAR_DAY.name
        val cond = try { WeatherCondition.valueOf(codeName) } catch (_: Exception) { WeatherCondition.CLEAR_DAY }
        val text = prefs.getString("cached_text", "صافٍ") ?: "صافٍ"
        val isDay = prefs.getBoolean("cached_is_day", true)
        val loaded = prefs.getBoolean("cached_loaded", false)
        return WeatherData(temp, city, cond, text, isDay, loaded)
    }

    private fun saveCachedWeather(data: WeatherData) {
        prefs.edit()
            .putInt("cached_temp", data.tempC)
            .putString("cached_city", data.city)
            .putString("cached_cond", data.condition.name)
            .putString("cached_text", data.conditionText)
            .putBoolean("cached_is_day", data.isDay)
            .putBoolean("cached_loaded", true)
            .apply()
    }
}
