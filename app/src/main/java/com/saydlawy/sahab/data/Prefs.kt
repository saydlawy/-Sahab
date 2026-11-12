package com.saydlawy.sahab.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sahab")

class Prefs @JvmOverloads constructor(
    context: Context?,
    private val secureStoreOverride: SharedPreferences? = null,
    dataStoreOverride: DataStore<Preferences>? = null,
) {

    private val appContext = context?.applicationContext
    private val store: DataStore<Preferences> = dataStoreOverride
        ?: appContext?.dataStore
        ?: error("Context or dataStoreOverride is required")
    private val json = Json { ignoreUnknownKeys = true }
    private val favSerializer = ListSerializer(FavItem.serializer())
    private val recentSerializer = ListSerializer(RecentItem.serializer())

    private object Keys {
        val Server = stringPreferencesKey("server")
        val User = stringPreferencesKey("user")
        val Pass = stringPreferencesKey("pass")
        val Favs = stringPreferencesKey("favorites")
        val Recents = stringPreferencesKey("recents")
        val LightEffects = booleanPreferencesKey("light_effects")
        val LastSyncTime = longPreferencesKey("last_sync_time")
    }

    private val accountMutex = Mutex()
    private val _accountState = MutableStateFlow<Account?>(null)

    @Volatile
    private var accountInitialized = false

    @Volatile
    private var cachedSecurePrefs: SharedPreferences? = secureStoreOverride

    private fun securePrefs(): SharedPreferences {
        cachedSecurePrefs?.let { return it }
        synchronized(this) {
            cachedSecurePrefs?.let { return it }
            val created = runCatching { createEncryptedPrefs() }.getOrElse {
                // Recover from corrupted Android Keystore entry after backup restore
                runCatching {
                    val parent = appContext?.filesDir?.parentFile
                    if (parent != null) {
                        File(parent, "shared_prefs/$SECURE_PREFS_NAME.xml").delete()
                    }
                }
                createEncryptedPrefs()
            }
            cachedSecurePrefs = created
            return created
        }
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        val ctx = appContext ?: error("Context is required for EncryptedSharedPreferences")
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            SECURE_PREFS_NAME,
            masterKeyAlias,
            ctx,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private suspend fun ensureAccountLoaded() {
        if (accountInitialized) return
        accountMutex.withLock {
            if (accountInitialized) return
            withContext(Dispatchers.IO) {
                val sec = securePrefs()
                var server = sec.getString(SEC_SERVER, null)
                var user = sec.getString(SEC_USER, null)
                var pass = sec.getString(SEC_PASS, null)

                // One-time migration from legacy plain DataStore to EncryptedSharedPreferences
                if (server.isNullOrBlank() || user.isNullOrBlank() || pass == null) {
                    val legacy = store.data.first()
                    val oldServer = legacy[Keys.Server]
                    val oldUser = legacy[Keys.User]
                    val oldPass = legacy[Keys.Pass]
                    if (!oldServer.isNullOrBlank() && !oldUser.isNullOrBlank() && oldPass != null) {
                        sec.edit()
                            .putString(SEC_SERVER, oldServer)
                            .putString(SEC_USER, oldUser)
                            .putString(SEC_PASS, oldPass)
                            .commit()
                        server = oldServer
                        user = oldUser
                        pass = oldPass
                        store.edit {
                            it.remove(Keys.Server)
                            it.remove(Keys.User)
                            it.remove(Keys.Pass)
                        }
                    }
                } else {
                    // Ensure legacy keys are scrubbed if present
                    val legacy = store.data.first()
                    if (legacy.contains(Keys.Server) || legacy.contains(Keys.User) || legacy.contains(Keys.Pass)) {
                        store.edit {
                            it.remove(Keys.Server)
                            it.remove(Keys.User)
                            it.remove(Keys.Pass)
                        }
                    }
                }

                _accountState.value = if (server.isNullOrBlank() || user.isNullOrBlank() || pass == null) {
                    null
                } else {
                    Account(server, user, pass)
                }
                accountInitialized = true
            }
        }
    }

    val account: Flow<Account?> = flow {
        ensureAccountLoaded()
        emitAll(_accountState)
    }

    val favorites: Flow<List<FavItem>> = store.data.map { decodeFavs(it[Keys.Favs]) }

    val recents: Flow<List<RecentItem>> = store.data.map { decodeRecents(it[Keys.Recents]) }

    val lightEffects: Flow<Boolean> = store.data.map { it[Keys.LightEffects] ?: false }

    val lastSyncTime: Flow<Long> = store.data.map { it[Keys.LastSyncTime] ?: 0L }

    suspend fun setLastSyncTime(ms: Long) {
        store.edit { it[Keys.LastSyncTime] = ms }
    }

    suspend fun saveAccount(account: Account) {
        accountMutex.withLock {
            withContext(Dispatchers.IO) {
                securePrefs().edit()
                    .putString(SEC_SERVER, account.server)
                    .putString(SEC_USER, account.user)
                    .putString(SEC_PASS, account.pass)
                    .commit()
                store.edit {
                    it.remove(Keys.Server)
                    it.remove(Keys.User)
                    it.remove(Keys.Pass)
                }
                _accountState.value = account
                accountInitialized = true
            }
        }
    }

    suspend fun clearAccount() {
        accountMutex.withLock {
            withContext(Dispatchers.IO) {
                securePrefs().edit()
                    .remove(SEC_SERVER)
                    .remove(SEC_USER)
                    .remove(SEC_PASS)
                    .commit()
                store.edit {
                    it.remove(Keys.Server)
                    it.remove(Keys.User)
                    it.remove(Keys.Pass)
                    it.remove(Keys.Favs)
                    it.remove(Keys.Recents)
                }
                _accountState.value = null
                accountInitialized = true
            }
        }
    }

    suspend fun setLightEffects(enabled: Boolean) {
        store.edit { it[Keys.LightEffects] = enabled }
    }

    suspend fun toggleFavorite(item: FavItem) {
        store.edit { p ->
            val list = decodeFavs(p[Keys.Favs]).toMutableList()
            val index = list.indexOfFirst { it.kind == item.kind && it.id == item.id }
            if (index >= 0) list.removeAt(index) else list.add(0, item)
            p[Keys.Favs] = json.encodeToString(favSerializer, list)
        }
    }

    suspend fun positionOf(kind: String, id: String): Long =
        recents.first().firstOrNull { it.kind == kind && it.id == id }?.positionMs ?: 0L

    /** Live channels are remembered without a position; finished movies and episodes are dropped. */
    suspend fun recordPlayback(item: RecentItem) {
        store.edit { p ->
            val list = decodeRecents(p[Keys.Recents]).toMutableList()
            list.removeAll { it.kind == item.kind && it.id == item.id }
            val finished = item.kind != Kind.LIVE &&
                item.durationMs > 0 &&
                item.positionMs >= item.durationMs * 95 / 100
            if (!finished) list.add(0, item)
            p[Keys.Recents] = json.encodeToString(recentSerializer, list.take(MAX_RECENTS))
        }
    }

    private fun decodeFavs(raw: String?): List<FavItem> =
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString(favSerializer, raw) }.getOrDefault(emptyList())

    private fun decodeRecents(raw: String?): List<RecentItem> =
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString(recentSerializer, raw) }.getOrDefault(emptyList())

    private companion object {
        const val MAX_RECENTS = 30
        const val SECURE_PREFS_NAME = "sahab_secure_account"
        const val SEC_SERVER = "enc_server"
        const val SEC_USER = "enc_user"
        const val SEC_PASS = "enc_pass"
    }
}
