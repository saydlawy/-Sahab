package com.saydlawy.sahab.data.weather

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
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
    context: Context? = null,
    prefsOverride: SharedPreferences? = null,
    private val geoBaseUrl: String = "https://geocoding-api.open-meteo.com",
    private val forecastBaseUrl: String = "https://api.open-meteo.com",
    private val ipGeoUrl: String = "https://ipwho.is/?lang=ar",
) {
    private val prefs: SharedPreferences? by lazy {
        prefsOverride ?: context?.applicationContext?.getSharedPreferences("sahab_weather_cache", Context.MODE_PRIVATE)
    }
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Volatile
    private var cacheHydrated: Boolean = prefsOverride != null
    private val _weather = MutableStateFlow(if (prefsOverride != null) loadCachedWeather() else WeatherData())
    val weather: StateFlow<WeatherData> = _weather.asStateFlow()

    private var lastFetchMs: Long = 0L

    fun getManualCity(): String = prefs?.getString("manual_city", "") ?: ""

    suspend fun setManualCity(cityName: String): Boolean {
        val trimmed = cityName.trim()
        if (trimmed.isEmpty()) {
            prefs?.edit()?.remove("manual_city")?.remove("manual_lat")?.remove("manual_lon")?.apply()
            refreshIfNeeded(force = true)
            return true
        }
        val geo = withContext(Dispatchers.IO) {
            runCatching {
                val encoded = java.net.URLEncoder.encode(trimmed, "UTF-8")
                val req = Request.Builder()
                    .url("${geoBaseUrl.trimEnd('/')}/v1/search?name=$encoded&count=1&language=ar")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body?.string().orEmpty()
                    if (body.isBlank()) return@use null
                    val parsed = json.decodeFromString(GeoSearchResponse.serializer(), body)
                    val item = parsed.results.firstOrNull() ?: return@use null
                    Triple(
                        item.latitude.toFloat(),
                        item.longitude.toFloat(),
                        item.name.ifBlank { trimmed },
                    )
                }
            }.getOrNull()
        } ?: return false
        prefs?.edit()
            ?.putString("manual_city", geo.third)
            ?.putFloat("manual_lat", geo.first)
            ?.putFloat("manual_lon", geo.second)
            ?.apply()
        refreshIfNeeded(force = true)
        return true
    }

    suspend fun refreshIfNeeded(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastFetchMs < 30 * 60 * 1000L && _weather.value.isLoaded) {
            return
        }

        withContext(Dispatchers.IO) {
            if (!cacheHydrated) {
                _weather.value = loadCachedWeather()
                cacheHydrated = true
            }
            try {
                // 1. Manual city override first, else IP geolocation
                val store = prefs
                val manualCity = store?.getString("manual_city", "").orEmpty()
                val hasManual = manualCity.isNotBlank() &&
                    store?.contains("manual_lat") == true &&
                    store.contains("manual_lon")
                val loc = if (hasManual) null else fetchIpLocation()
                val lat = if (hasManual) store?.getFloat("manual_lat", 29.37f)?.toDouble() ?: 29.37 else (loc?.first ?: 29.37)
                val lon = if (hasManual) store?.getFloat("manual_lon", 47.98f)?.toDouble() ?: 47.98 else (loc?.second ?: 47.98)
                val rawCity = if (hasManual) manualCity else (loc?.third ?: "Kuwait City")

                // 2. Real-time weather from Open-Meteo
                val url = "${forecastBaseUrl.trimEnd('/')}/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,is_day&timezone=auto"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Sahab/1.0 (Android)")
                    .build()

                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) return@withContext
                    val body = res.body?.string() ?: return@withContext
                    val parsed = json.decodeFromString(OpenMeteoResponse.serializer(), body)
                    val current = parsed.current ?: return@withContext
                    val temp = current.temperature_2m.toInt()
                    val code = current.weather_code
                    val isDay = current.is_day == 1

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
                runCatching { Log.w("WeatherService", "Weather refresh failed: ${e.message}") }
            }
        }
    }

    private fun fetchIpLocation(): Triple<Double, Double, String>? {
        return try {
            val req = Request.Builder()
                .url(ipGeoUrl)
                .header("User-Agent", "Sahab/1.0 (Android)")
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return null
                val body = res.body?.string() ?: return null
                val parsed = json.decodeFromString(IpWhoResponse.serializer(), body)
                if (!parsed.success) return null
                val lat = parsed.latitude ?: return null
                val lon = parsed.longitude ?: return null
                val city = parsed.city.ifBlank { "Kuwait City" }
                Triple(lat, lon, city)
            }
        } catch (_: Exception) {
            null
        }
    }

    @Serializable
    private data class GeoSearchResponse(
        val results: List<GeoCityDto> = emptyList(),
    )

    @Serializable
    private data class GeoCityDto(
        val latitude: Double,
        val longitude: Double,
        val name: String = "",
    )

    @Serializable
    private data class OpenMeteoResponse(
        val current: OpenMeteoCurrentDto? = null,
    )

    @Serializable
    private data class OpenMeteoCurrentDto(
        val temperature_2m: Double = 24.0,
        val weather_code: Int = 0,
        val is_day: Int = 1,
    )

    @Serializable
    private data class IpWhoResponse(
        val success: Boolean = true,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val city: String = "Kuwait City",
    )

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
        val store = prefs ?: return WeatherData()
        val temp = store.getInt("cached_temp", 24)
        val city = store.getString("cached_city", "الكويت") ?: "الكويت"
        val codeName = store.getString("cached_cond", WeatherCondition.CLEAR_DAY.name) ?: WeatherCondition.CLEAR_DAY.name
        val cond = try { WeatherCondition.valueOf(codeName) } catch (_: Exception) { WeatherCondition.CLEAR_DAY }
        val text = store.getString("cached_text", "صافٍ") ?: "صافٍ"
        val isDay = store.getBoolean("cached_is_day", true)
        val loaded = store.getBoolean("cached_loaded", false)
        return WeatherData(temp, city, cond, text, isDay, loaded)
    }

    private fun saveCachedWeather(data: WeatherData) {
        prefs?.edit()
            ?.putInt("cached_temp", data.tempC)
            ?.putString("cached_city", data.city)
            ?.putString("cached_cond", data.condition.name)
            ?.putString("cached_text", data.conditionText)
            ?.putBoolean("cached_is_day", data.isDay)
            ?.putBoolean("cached_loaded", true)
            ?.apply()
    }
}
