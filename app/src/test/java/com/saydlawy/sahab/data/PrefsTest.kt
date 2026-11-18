package com.saydlawy.sahab.data

import android.content.SharedPreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PrefsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun saveAndClearAccount_removesAccountFavsAndRecents() = runTest {
        val fakeSecure = InMemorySharedPreferences()
        val ds = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(tempFolder.root, "prefs_clear.preferences_pb") },
        )
        val prefs = Prefs(context = null, secureStoreOverride = fakeSecure, dataStoreOverride = ds)

        val account = Account(server = "https://portal.example.com", user = "user1", pass = "pass1")
        prefs.saveAccount(account)
        prefs.toggleFavorite(FavItem(Kind.MOVIE, "42", "فيلم تجريبي"))
        prefs.recordPlayback(RecentItem(Kind.MOVIE, "42", "فيلم تجريبي", positionMs = 15_000L, durationMs = 120_000L))

        assertEquals(account, prefs.account.first())
        assertEquals(1, prefs.favorites.first().size)
        assertEquals(1, prefs.recents.first().size)

        prefs.clearAccount()

        assertNull(prefs.account.first())
        assertTrue(prefs.favorites.first().isEmpty())
        assertTrue(prefs.recents.first().isEmpty())
        assertFalse(fakeSecure.contains("enc_accounts_list"))
        assertFalse(fakeSecure.contains("enc_active_id"))
    }

    @Test
    fun legacyPlainDataStoreAccount_migratesToEncryptedAndDeletesPlainKeys() = runTest {
        val fakeSecure = InMemorySharedPreferences()
        val ds = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(tempFolder.root, "prefs_migrate.preferences_pb") },
        )
        val srvKey = stringPreferencesKey("server")
        val usrKey = stringPreferencesKey("user")
        val pwdKey = stringPreferencesKey("pass")
        ds.edit {
            it[srvKey] = "https://legacy.example.com"
            it[usrKey] = "legacy_user"
            it[pwdKey] = "legacy_pass"
        }

        val prefs = Prefs(context = null, secureStoreOverride = fakeSecure, dataStoreOverride = ds)
        val loaded = prefs.account.first()

        // نتحقق من الحقول الفردية بدلاً من الكائن بالكامل لتجنب اختلاف الـ id العشوائي
        assertEquals("https://legacy.example.com", loaded?.server)
        assertEquals("legacy_user", loaded?.user)
        assertEquals("legacy_pass", loaded?.pass)
        assertEquals("حسابي القديم", loaded?.name)
        assertEquals("XTREAM", loaded?.type)
        assertTrue(fakeSecure.contains("enc_accounts_list"))
        assertTrue(fakeSecure.contains("enc_active_id"))
        val after = ds.data.first()
        assertFalse(after.contains(srvKey))
        assertFalse(after.contains(usrKey))
        assertFalse(after.contains(pwdKey))
    }

    @Test
    fun favoritesFlow_deduplicatesIdenticalUnrelatedDataStoreWrites() = runTest {
        val fakeSecure = InMemorySharedPreferences()
        val ds = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(tempFolder.root, "prefs_distinct.preferences_pb") },
        )
        val prefs = Prefs(context = null, secureStoreOverride = fakeSecure, dataStoreOverride = ds)
        val emissions = mutableListOf<List<FavItem>>()
        val job = launch {
            prefs.favorites.collect { emissions.add(it) }
        }
        advanceUntilIdle()
        prefs.setShowRecents(false)
        prefs.setShowRecents(true)
        advanceUntilIdle()
        // Unrelated preference writes must not re-emit identical favorite lists.
        assertEquals(1, emissions.size)
        job.cancel()
    }

    private class InMemorySharedPreferences : SharedPreferences {
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
        override fun edit(): SharedPreferences.Editor = EditorImpl()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private inner class EditorImpl : SharedPreferences.Editor {
            private val pending = linkedMapOf<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = values
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun remove(key: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = null
            }
            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) map.clear()
                for ((k, v) in pending) {
                    if (v == null) map.remove(k) else map[k] = v
                }
                pending.clear()
                clearAll = false
            }
        }
    }
}
