package com.saydlawy.sahab

import android.content.SharedPreferences
import androidx.compose.ui.unit.dp
import com.saydlawy.sahab.data.weather.WeatherCondition
import com.saydlawy.sahab.data.weather.WeatherService
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.computeAdaptiveWindowInfo
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase3ArchitectureAndModernizationTest {

    @Test
    fun weatherService_parsesOpenMeteoAndGeocodingViaKotlinxSerialization() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val prefs = SimpleInMemoryPrefs()
            val baseUrl = server.url("/").toString().trimEnd('/')
            val service = WeatherService(
                client = OkHttpClient(),
                context = null,
                prefsOverride = prefs,
                geoBaseUrl = baseUrl,
                forecastBaseUrl = baseUrl,
                ipGeoUrl = "$baseUrl/ip",
            )

            // 1. Geocoding search + forecast response for manual city
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"results":[{"latitude":24.7136,"longitude":46.6753,"name":"Riyadh"}]}""",
                ),
            )
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"current":{"temperature_2m":33.4,"weather_code":2,"is_day":1}}""",
                ),
            )

            val ok = service.setManualCity("Riyadh")
            assertTrue(ok)
            val data = service.weather.value
            assertTrue(data.isLoaded)
            assertEquals("الرياض", data.city)
            assertEquals(33, data.tempC)
            assertEquals(WeatherCondition.PARTLY_CLOUDY, data.condition)
            assertTrue(data.isDay)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun adaptiveWindowInfo_classifiesTvTabletLandscapeAndPortraitCleanly() {
        val tv = computeAdaptiveWindowInfo(maxWidth = 960.dp, maxHeight = 540.dp, isTv = true)
        assertEquals(DisplayMode.TABLET_OR_TV, tv.mode)

        val tablet = computeAdaptiveWindowInfo(maxWidth = 800.dp, maxHeight = 1280.dp, isTv = false)
        assertEquals(DisplayMode.TABLET_OR_TV, tablet.mode)

        val phoneLandscape = computeAdaptiveWindowInfo(maxWidth = 800.dp, maxHeight = 360.dp, isTv = false)
        assertEquals(DisplayMode.PHONE_LANDSCAPE, phoneLandscape.mode)

        val phonePortrait = computeAdaptiveWindowInfo(maxWidth = 390.dp, maxHeight = 844.dp, isTv = false)
        assertEquals(DisplayMode.PHONE_PORTRAIT, phonePortrait.mode)
    }

    private class SimpleInMemoryPrefs : SharedPreferences {
        private val map = linkedMapOf<String, Any?>()
        override fun getAll(): MutableMap<String, *> = map.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = (map[key] as? String) ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (map[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = linkedMapOf<String, Any?>()
            private var clearAll = false
            override fun putString(k: String?, v: String?) = apply { if (k != null) pending[k] = v }
            override fun putStringSet(k: String?, v: MutableSet<String>?) = apply { if (k != null) pending[k] = v }
            override fun putInt(k: String?, v: Int) = apply { if (k != null) pending[k] = v }
            override fun putLong(k: String?, v: Long) = apply { if (k != null) pending[k] = v }
            override fun putFloat(k: String?, v: Float) = apply { if (k != null) pending[k] = v }
            override fun putBoolean(k: String?, v: Boolean) = apply { if (k != null) pending[k] = v }
            override fun remove(k: String?) = apply { if (k != null) pending[k] = null }
            override fun clear() = apply { clearAll = true }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clearAll) map.clear()
                pending.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
                pending.clear()
                clearAll = false
            }
        }
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }
}
