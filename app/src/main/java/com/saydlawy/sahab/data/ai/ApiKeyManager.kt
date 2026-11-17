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

    private val prefs: SharedPreferences = try {
        val mk = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context, "sahab_ai_keys", mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        isEncrypted = false
        context.getSharedPreferences("sahab_ai_keys_fb", Context.MODE_PRIVATE)
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
}
