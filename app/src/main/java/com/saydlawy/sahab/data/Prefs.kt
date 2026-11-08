package com.saydlawy.sahab.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sahab")

class Prefs(context: Context) {

    private val store = context.applicationContext.dataStore
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
    }

    val account: Flow<Account?> = store.data.map { p ->
        val server = p[Keys.Server]
        val user = p[Keys.User]
        val pass = p[Keys.Pass]
        if (server.isNullOrBlank() || user.isNullOrBlank() || pass == null) null
        else Account(server, user, pass)
    }

    val favorites: Flow<List<FavItem>> = store.data.map { decodeFavs(it[Keys.Favs]) }

    val recents: Flow<List<RecentItem>> = store.data.map { decodeRecents(it[Keys.Recents]) }

    val lightEffects: Flow<Boolean> = store.data.map { it[Keys.LightEffects] ?: false }

    suspend fun saveAccount(account: Account) {
        store.edit {
            it[Keys.Server] = account.server
            it[Keys.User] = account.user
            it[Keys.Pass] = account.pass
        }
    }

    suspend fun clearAccount() {
        store.edit {
            it.remove(Keys.Server)
            it.remove(Keys.User)
            it.remove(Keys.Pass)
            it.remove(Keys.Recents)
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
    }
}
