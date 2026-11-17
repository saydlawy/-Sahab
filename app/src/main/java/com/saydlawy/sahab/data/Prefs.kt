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

enum class HomeChannelsLayout {
    CAROUSEL,
    GRID,
}

enum class HomeChannelsSource {
    AUTO,
    FAVORITES,
    CATEGORY,
    CUSTOM,
}

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
    private val accountSerializer = ListSerializer(Account.serializer())

    private object Keys {
        val Server = stringPreferencesKey("server")
        val User = stringPreferencesKey("user")
        val Pass = stringPreferencesKey("pass")
        val Favs = stringPreferencesKey("favorites")
        val Recents = stringPreferencesKey("recents")
        val LightEffects = booleanPreferencesKey("light_effects")
        val ThemePreferencesKey = stringPreferencesKey("theme_preferences")
        val ShowRecents = booleanPreferencesKey("show_recents")
        val ShowLiveChannelsOnHome = booleanPreferencesKey("show_live_channels_on_home")
        val ShowRecommendedOnHome = booleanPreferencesKey("show_recommended_on_home")
        val ShowQuickCategoriesOnHome = booleanPreferencesKey("show_quick_categories_on_home")
        val ShowMatchesOnHome = booleanPreferencesKey("show_matches_on_home")
        val HomeChannelsLayoutKey = stringPreferencesKey("home_channels_layout")
        val HomeChannelsSourceKey = stringPreferencesKey("home_channels_source")
        val HomeChannelsCategoryIdKey = stringPreferencesKey("home_channels_category_id")
        val HomeCustomChannelIdsKey = stringPreferencesKey("home_custom_channel_ids")
        val LastSyncTime = longPreferencesKey("last_sync_time")
        val LastCatalogSyncTime = longPreferencesKey("last_catalog_sync_time")
        val LastChannelSyncTime = longPreferencesKey("last_channel_sync_time")
        val EpgLastSyncTime = longPreferencesKey("epg_last_sync_time")
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
                var accountsJson = sec.getString(SEC_ACCOUNTS, null)
                
                if (accountsJson.isNullOrBlank()) {
                    // 1. Try secure prefs
                    val oldServer = sec.getString(SEC_SERVER, null)
                    val oldUser = sec.getString(SEC_USER, null)
                    val oldPass = sec.getString(SEC_PASS, null)
                    
                    // 2. If not found, try legacy DataStore
                    var server = oldServer
                    var user = oldUser
                    var pass = oldPass
                    if (server.isNullOrBlank() || user.isNullOrBlank() || pass == null) {
                        val legacy = store.data.first()
                        server = legacy[Keys.Server]
                        user = legacy[Keys.User]
                        pass = legacy[Keys.Pass]
                    }

                    if (!server.isNullOrBlank() && !user.isNullOrBlank() && pass != null) {
                        val legacyAccount = Account(name = "حسابي القديم", type = "XTREAM", server = server, user = user, pass = pass)
                        val list = listOf(legacyAccount)
                        accountsJson = json.encodeToString(accountSerializer, list)
                        sec.edit().putString(SEC_ACCOUNTS, accountsJson).putString(SEC_ACTIVE_ID, legacyAccount.id).remove(SEC_SERVER).remove(SEC_USER).remove(SEC_PASS).commit()
                        
                        // Clean up DataStore to prevent future false positives
                        store.edit {
                            it.remove(Keys.Server)
                            it.remove(Keys.User)
                            it.remove(Keys.Pass)
                        }
                    }
                }

                var accounts = emptyList<Account>()
                if (!accountsJson.isNullOrBlank()) {
                    accounts = runCatching { json.decodeFromString(accountSerializer, accountsJson) }.getOrDefault(emptyList())
                }

                val activeId = sec.getString(SEC_ACTIVE_ID, null)
                _accountState.value = accounts.firstOrNull { it.id == activeId } ?: accounts.firstOrNull()
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

    val themePreferences: Flow<ThemePreferences> = store.data.map { preferences ->
        preferences[Keys.ThemePreferencesKey]
            ?.let { raw -> runCatching { json.decodeFromString(ThemePreferences.serializer(), raw) }.getOrNull() }
            ?: ThemePreferences()
    }

    val showRecents: Flow<Boolean> = store.data.map { it[Keys.ShowRecents] ?: true }

    val showLiveChannelsOnHome: Flow<Boolean> = store.data.map { it[Keys.ShowLiveChannelsOnHome] ?: true }

    val showRecommendedOnHome: Flow<Boolean> = store.data.map { it[Keys.ShowRecommendedOnHome] ?: true }

    val showQuickCategoriesOnHome: Flow<Boolean> = store.data.map { it[Keys.ShowQuickCategoriesOnHome] ?: true }

    val showMatchesOnHome: Flow<Boolean> = store.data.map { it[Keys.ShowMatchesOnHome] ?: true }

    val homeChannelsLayout: Flow<HomeChannelsLayout> = store.data.map {
        val raw = it[Keys.HomeChannelsLayoutKey]
        if (raw == HomeChannelsLayout.GRID.name) HomeChannelsLayout.GRID else HomeChannelsLayout.CAROUSEL
    }

    val homeChannelsSource: Flow<HomeChannelsSource> = store.data.map {
        val raw = it[Keys.HomeChannelsSourceKey]
        runCatching { if (raw != null) HomeChannelsSource.valueOf(raw) else HomeChannelsSource.AUTO }
            .getOrDefault(HomeChannelsSource.AUTO)
    }

    val homeChannelsCategoryId: Flow<String> = store.data.map { it[Keys.HomeChannelsCategoryIdKey].orEmpty() }

    val homeCustomChannelIds: Flow<Set<String>> = store.data.map {
        it[Keys.HomeCustomChannelIdsKey]
            ?.split(",")
            ?.map { id -> id.trim() }
            ?.filter { id -> id.isNotBlank() }
            ?.toSet()
            ?: emptySet()
    }

    suspend fun setShowRecents(show: Boolean) {
        store.edit { it[Keys.ShowRecents] = show }
    }

    suspend fun setShowLiveChannelsOnHome(show: Boolean) {
        store.edit { it[Keys.ShowLiveChannelsOnHome] = show }
    }

    suspend fun setShowRecommendedOnHome(show: Boolean) {
        store.edit { it[Keys.ShowRecommendedOnHome] = show }
    }

    suspend fun setShowQuickCategoriesOnHome(show: Boolean) {
        store.edit { it[Keys.ShowQuickCategoriesOnHome] = show }
    }

    suspend fun setShowMatchesOnHome(show: Boolean) {
        store.edit { it[Keys.ShowMatchesOnHome] = show }
    }

    suspend fun setHomeChannelsLayout(layout: HomeChannelsLayout) {
        store.edit { it[Keys.HomeChannelsLayoutKey] = layout.name }
    }

    suspend fun setHomeChannelsSource(source: HomeChannelsSource) {
        store.edit { it[Keys.HomeChannelsSourceKey] = source.name }
    }

    suspend fun setHomeChannelsCategoryId(categoryId: String) {
        store.edit { it[Keys.HomeChannelsCategoryIdKey] = categoryId }
    }

    suspend fun toggleHomeCustomChannel(channelId: String) {
        store.edit { p ->
            val current = p[Keys.HomeCustomChannelIdsKey]
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.toMutableSet()
                ?: mutableSetOf()
            if (!current.remove(channelId)) {
                current.add(channelId)
            }
            p[Keys.HomeCustomChannelIdsKey] = current.joinToString(",")
        }
    }

    suspend fun setHomeCustomChannelIds(ids: Set<String>) {
        store.edit { p ->
            p[Keys.HomeCustomChannelIdsKey] = ids.filter { it.isNotBlank() }.joinToString(",")
        }
    }

    val lastCatalogSyncTime: Flow<Long> = store.data.map {
        it[Keys.LastCatalogSyncTime] ?: it[Keys.LastSyncTime] ?: 0L
    }

    suspend fun setLastCatalogSyncTime(ms: Long) {
        store.edit { it[Keys.LastCatalogSyncTime] = ms }
    }

    val lastChannelSyncTime: Flow<Long> = store.data.map { it[Keys.LastChannelSyncTime] ?: 0L }

    suspend fun setLastChannelSyncTime(ms: Long) {
        store.edit { it[Keys.LastChannelSyncTime] = ms }
    }

    val lastEpgSyncTime: Flow<Long> = store.data.map { it[Keys.EpgLastSyncTime] ?: 0L }

    suspend fun setLastEpgSyncTime(ms: Long) {
        store.edit { it[Keys.EpgLastSyncTime] = ms }
    }

    /** ترحيل لمرة واحدة من last_sync_time القديم إلى last_catalog_sync_time مع ترك القنوات وEPG عند 0. */
    suspend fun migrateSyncKeysIfNeeded() {
        store.edit { p ->
            val legacy = p[Keys.LastSyncTime]
            if (legacy != null) {
                if (p[Keys.LastCatalogSyncTime] == null) {
                    p[Keys.LastCatalogSyncTime] = legacy
                }
                p.remove(Keys.LastSyncTime)
                p.remove(Keys.LastChannelSyncTime)
                p.remove(Keys.EpgLastSyncTime)
            }
        }
    }

    suspend fun clearSyncTimestamps() {
        store.edit { p ->
            p.remove(Keys.LastSyncTime)
            p.remove(Keys.LastCatalogSyncTime)
            p.remove(Keys.LastChannelSyncTime)
            p.remove(Keys.EpgLastSyncTime)
        }
    }

    suspend fun saveAccount(account: Account) {
        accountMutex.withLock {
            withContext(Dispatchers.IO) {
                val sec = securePrefs()
                val accountsJson = sec.getString(SEC_ACCOUNTS, null)
                val accounts = if (accountsJson.isNullOrBlank()) mutableListOf() else 
                    runCatching { json.decodeFromString(accountSerializer, accountsJson).toMutableList() }.getOrElse { mutableListOf() }
                
                val index = accounts.indexOfFirst { it.id == account.id }
                if (index >= 0) accounts[index] = account else accounts.add(account)
                
                sec.edit()
                    .putString(SEC_ACCOUNTS, json.encodeToString(accountSerializer, accounts))
                    .putString(SEC_ACTIVE_ID, account.id)
                    .commit()
                
                _accountState.value = account
                accountInitialized = true
            }
        }
    }
    
    suspend fun getAccounts(): List<Account> = withContext(Dispatchers.IO) {
        ensureAccountLoaded()
        val accountsJson = securePrefs().getString(SEC_ACCOUNTS, null)
        if (accountsJson.isNullOrBlank()) emptyList() else 
            runCatching { json.decodeFromString(accountSerializer, accountsJson) }.getOrDefault(emptyList())
    }

    suspend fun setActiveAccount(id: String) {
        accountMutex.withLock {
            withContext(Dispatchers.IO) {
                val sec = securePrefs()
                sec.edit().putString(SEC_ACTIVE_ID, id).commit()
                val accountsJson = sec.getString(SEC_ACCOUNTS, null)
                if (!accountsJson.isNullOrBlank()) {
                    val accounts = runCatching { json.decodeFromString(accountSerializer, accountsJson) }.getOrDefault(emptyList())
                    _accountState.value = accounts.firstOrNull { it.id == id }
                }
            }
        }
    }

    suspend fun deleteAccount(id: String) {
        accountMutex.withLock {
            withContext(Dispatchers.IO) {
                val sec = securePrefs()
                val accountsJson = sec.getString(SEC_ACCOUNTS, null)
                if (!accountsJson.isNullOrBlank()) {
                    val accounts = runCatching { json.decodeFromString(accountSerializer, accountsJson).toMutableList() }.getOrElse { mutableListOf() }
                    accounts.removeAll { it.id == id }
                    sec.edit().putString(SEC_ACCOUNTS, json.encodeToString(accountSerializer, accounts)).commit()
                    
                    if (sec.getString(SEC_ACTIVE_ID, null) == id) {
                        val newActive = accounts.firstOrNull()
                        sec.edit().putString(SEC_ACTIVE_ID, newActive?.id ?: "").commit()
                        _accountState.value = newActive
                    }
                }
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
                    .remove(SEC_ACCOUNTS)
                    .remove(SEC_ACTIVE_ID)
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

    suspend fun saveThemePreferences(value: ThemePreferences) {
        store.edit { it[Keys.ThemePreferencesKey] = json.encodeToString(ThemePreferences.serializer(), value) }
    }

    suspend fun setSelectedTheme(themeId: String) {
        store.edit { preferences ->
            val current = preferences[Keys.ThemePreferencesKey]
                ?.let { raw -> runCatching { json.decodeFromString(ThemePreferences.serializer(), raw) }.getOrNull() }
                ?: ThemePreferences()
            preferences[Keys.ThemePreferencesKey] = json.encodeToString(
                ThemePreferences.serializer(),
                current.copy(selectedThemeId = themeId),
            )
        }
    }

    suspend fun setThemeCustomization(themeId: String, customization: ThemeCustomization) {
        updateThemeCustomization(themeId) { customization }
    }

    /** تحديث ذري لتخصيص الثيم داخل DataStore.edit لتجنب ضياع التغييرات المتزامنة. */
    suspend fun updateThemeCustomization(
        themeId: String,
        transform: (ThemeCustomization) -> ThemeCustomization,
    ) {
        store.edit { preferences ->
            val current = preferences[Keys.ThemePreferencesKey]
                ?.let { raw -> runCatching { json.decodeFromString(ThemePreferences.serializer(), raw) }.getOrNull() }
                ?: ThemePreferences()
            val updated = transform(current.customization(themeId))
            preferences[Keys.ThemePreferencesKey] = json.encodeToString(
                ThemePreferences.serializer(),
                current.copy(customizations = current.customizations + (themeId to updated)),
            )
        }
    }

    suspend fun resetThemeCustomization(themeId: String) {
        store.edit { preferences ->
            val current = preferences[Keys.ThemePreferencesKey]
                ?.let { raw -> runCatching { json.decodeFromString(ThemePreferences.serializer(), raw) }.getOrNull() }
                ?: ThemePreferences()
            preferences[Keys.ThemePreferencesKey] = json.encodeToString(
                ThemePreferences.serializer(),
                current.copy(customizations = current.customizations - themeId),
            )
        }
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


    suspend fun restoreFavorites(favs: List<FavItem>) {
        store.edit { p -> p[Keys.Favs] = json.encodeToString(favSerializer, favs) }
    }

    suspend fun restoreRecents(recents: List<RecentItem>) {
        store.edit { p -> p[Keys.Recents] = json.encodeToString(recentSerializer, recents) }
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
        const val SEC_ACCOUNTS = "enc_accounts_list"
        const val SEC_ACTIVE_ID = "enc_active_id"
    }
}
