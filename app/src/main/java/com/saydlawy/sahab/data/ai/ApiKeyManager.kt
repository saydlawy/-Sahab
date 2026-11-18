package com.saydlawy.sahab.data.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ApiKey(
    val id: String,
    val value: String,
    val label: String,
    val lastUsedMs: Long = 0L,
    val last429Ms: Long = 0L,
)

class ApiKeyManager(context: Context) {

    var isEncrypted: Boolean = true
        private set

    private val prefs: SharedPreferences = createSecurePrefsWithRecovery(context.applicationContext)

    private fun createSecurePrefsWithRecovery(ctx: Context): SharedPreferences {
        // Delete any legacy plaintext fallback file left by previous versions.
        runCatching { ctx.deleteSharedPreferences(LEGACY_PLAINTEXT_PREFS_NAME) }
        return runCatching {
            createEncryptedPrefs(ctx)
        }.getOrElse {
            // Attempt Keystore-corrupted prefs cleanup & encrypted recreate before failing closed to RAM-only.
            runCatching { ctx.deleteSharedPreferences(ENCRYPTED_PREFS_NAME) }
            runCatching {
                createEncryptedPrefs(ctx)
            }.getOrElse {
                isEncrypted = false
                InMemorySharedPreferences()
            }
        }
    }

    private fun createEncryptedPrefs(ctx: Context): SharedPreferences {
        val mk = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            ctx,
            ENCRYPTED_PREFS_NAME,
            mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _keys = MutableStateFlow(loadKeys())
    val keys: StateFlow<List<ApiKey>> = _keys.asStateFlow()

    private val _activeId = MutableStateFlow(prefs.getString("active_id", "") ?: "")
    val activeId: StateFlow<String> = _activeId.asStateFlow()

    fun addKey(rawValue: String, label: String = ""): ApiKey? {
        val v = rawValue.trim()
        if (v.length < 20) return null
        val id = "key_${System.currentTimeMillis()}"
        val k = ApiKey(id = id, value = v, label = label.ifBlank { "Key ${_keys.value.size + 1}" })
        prefs.edit()
            .putString("key_value_$id", v)
            .putString("key_label_$id", k.label)
            .apply()
        val updated = _keys.value + k
        _keys.value = updated
        saveIndex(updated)
        if (_activeId.value.isBlank()) {
            _activeId.value = id
            prefs.edit().putString("active_id", id).apply()
        }
        return k
    }

    fun removeKey(id: String) {
        prefs.edit().remove("key_value_$id").remove("key_label_$id").apply()
        val updated = _keys.value.filterNot { it.id == id }
        _keys.value = updated
        saveIndex(updated)
        if (_activeId.value == id) {
            val next = updated.firstOrNull()?.id ?: ""
            _activeId.value = next
            prefs.edit().putString("active_id", next).apply()
        }
    }

    fun setActive(id: String) {
        if (_keys.value.any { it.id == id }) {
            _activeId.value = id
            prefs.edit().putString("active_id", id).apply()
        }
    }

    fun getActiveKey(): ApiKey? =
        _keys.value.firstOrNull { it.id == _activeId.value }

    /** O-01: Active key first, then remaining keys ordered by oldest 429 timestamp for rotation. */
    fun orderedKeysForRotation(): List<ApiKey> {
        val all = _keys.value
        if (all.isEmpty()) return emptyList()
        val active = getActiveKey()
        val rest = all.filter { it.id != active?.id }.sortedBy { it.last429Ms }
        return if (active != null) listOf(active) + rest else rest
    }

    fun markUsed(id: String) = mutate(id) { it.copy(lastUsedMs = System.currentTimeMillis()) }
    fun mark429(id: String) = mutate(id) { it.copy(last429Ms = System.currentTimeMillis()) }

    private fun mutate(id: String, block: (ApiKey) -> ApiKey) {
        val u = _keys.value.map { if (it.id == id) block(it) else it }
        _keys.value = u
        saveIndex(u)
    }

    private fun loadKeys(): List<ApiKey> {
        val idx = prefs.getString("key_index", "") ?: ""
        if (idx.isBlank()) return emptyList()
        return idx.split(",").filter { it.isNotBlank() }.mapNotNull { id ->
            val v = prefs.getString("key_value_$id", "") ?: return@mapNotNull null
            ApiKey(
                id = id,
                value = v,
                label = prefs.getString("key_label_$id", "Key") ?: "Key",
                lastUsedMs = prefs.getLong("key_lastused_$id", 0L),
                last429Ms = prefs.getLong("key_last429_$id", 0L),
            )
        }
    }

    private fun saveIndex(keys: List<ApiKey>) {
        val ed = prefs.edit().putString("key_index", keys.joinToString(",") { it.id })
        keys.forEach {
            ed.putLong("key_lastused_${it.id}", it.lastUsedMs)
                .putLong("key_last429_${it.id}", it.last429Ms)
        }
        ed.apply()
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val lock = Any()
        private val map = linkedMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = synchronized(lock) { map.toMutableMap() }
        override fun getString(key: String?, defValue: String?): String? = synchronized(lock) { (map[key] as? String) ?: defValue }
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            synchronized(lock) { (map[key] as? MutableSet<String>)?.toMutableSet() ?: defValues }
        override fun getInt(key: String?, defValue: Int): Int = synchronized(lock) { (map[key] as? Int) ?: defValue }
        override fun getLong(key: String?, defValue: Long): Long = synchronized(lock) { (map[key] as? Long) ?: defValue }
        override fun getFloat(key: String?, defValue: Float): Float = synchronized(lock) { (map[key] as? Float) ?: defValue }
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = synchronized(lock) { (map[key] as? Boolean) ?: defValue }
        override fun contains(key: String?): Boolean = synchronized(lock) { map.containsKey(key) }
        override fun edit(): SharedPreferences.Editor = EditorImpl()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private inner class EditorImpl : SharedPreferences.Editor {
            private val pending = linkedMapOf<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?) = apply { if (key != null) synchronized(lock) { pending[key] = value } }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { if (key != null) synchronized(lock) { pending[key] = values?.toMutableSet() } }
            override fun putInt(key: String?, value: Int) = apply { if (key != null) synchronized(lock) { pending[key] = value } }
            override fun putLong(key: String?, value: Long) = apply { if (key != null) synchronized(lock) { pending[key] = value } }
            override fun putFloat(key: String?, value: Float) = apply { if (key != null) synchronized(lock) { pending[key] = value } }
            override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) synchronized(lock) { pending[key] = value } }
            override fun remove(key: String?) = apply { if (key != null) synchronized(lock) { pending[key] = null } }
            override fun clear() = apply { synchronized(lock) { clearAll = true } }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                synchronized(lock) {
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

    private companion object {
        const val ENCRYPTED_PREFS_NAME = "sahab_ai_keys"
        const val LEGACY_PLAINTEXT_PREFS_NAME = "sahab_ai_keys_fb"
    }
}
