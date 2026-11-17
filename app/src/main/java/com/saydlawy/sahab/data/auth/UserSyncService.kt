package com.saydlawy.sahab.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.saydlawy.sahab.data.FavItem
import com.saydlawy.sahab.data.RecentItem
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import com.saydlawy.sahab.data.Account
import kotlinx.coroutines.suspendCancellableCoroutine
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume

data class CloudPreferences(
    val lightEffects: Boolean = false,
    val homeChannelsLayout: String = "CAROUSEL",
    val homeChannelsSource: String = "AUTO",
    val homeChannelsCategoryId: String = "",
)

sealed class CloudFetchResult<out T> {
    data class Success<T>(val data: T) : CloudFetchResult<T>()
    data object NotFound : CloudFetchResult<Nothing>()
    data class Error(val cause: Throwable? = null) : CloudFetchResult<Nothing>()
}

class UserSyncService {
    private fun keyFor(uid: String): SecretKeySpec {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest("sahab_cloud_v1_$uid".toByteArray(Charsets.UTF_8))
        return SecretKeySpec(hash, "AES")
    }

    private fun encrypt(plain: String, uid: String): String {
        if (plain.isEmpty()) return ""
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, keyFor(uid), GCMParameterSpec(128, iv))
        }
        val out = iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return "enc_v1:" + Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(value: String, uid: String): String {
        if (!value.startsWith("enc_v1:")) return value
        return runCatching {
            val raw = Base64.decode(value.removePrefix("enc_v1:"), Base64.NO_WRAP)
            val iv = raw.copyOfRange(0, 12)
            val ct = raw.copyOfRange(12, raw.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, keyFor(uid), GCMParameterSpec(128, iv))
            }
            String(cipher.doFinal(ct), Charsets.UTF_8)
        }.getOrDefault("")
    }
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val currentUid: String?
        get() = auth.currentUser?.uid

    suspend fun saveXtreamAccount(account: Account) = suspendCancellableCoroutine { cont ->
        val uid = currentUid ?: return@suspendCancellableCoroutine cont.resume(Unit)
        val data = mapOf(
            "server" to encrypt(account.server, uid),
            "user" to encrypt(account.user, uid),
            "pass" to encrypt(account.pass, uid)
        )
        db.collection("users").document(uid).collection("data").document("xtream")
            .set(data)
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resume(Unit) }
    }

    suspend fun saveFcmToken(token: String) = suspendCancellableCoroutine { cont ->
        val uid = currentUid ?: return@suspendCancellableCoroutine cont.resume(Unit)
        db.collection("users").document(uid).update("fcmToken", token)
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resume(Unit) }
    }


    private val json = Json { ignoreUnknownKeys = true }
    private val favSerializer = ListSerializer(FavItem.serializer())
    private val recentSerializer = ListSerializer(RecentItem.serializer())


    private val accountsSerializer = ListSerializer(Account.serializer())

    suspend fun saveAccounts(accounts: List<Account>) = suspendCancellableCoroutine { cont ->
        val uid = currentUid ?: return@suspendCancellableCoroutine cont.resume(Unit)
        val jsonStr = json.encodeToString(accountsSerializer, accounts)
        db.collection("users").document(uid).collection("data").document("accounts_list")
            .set(mapOf("enc" to encrypt(jsonStr, uid)))
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resume(Unit) }
    }

    suspend fun getAccounts(): List<Account>? = suspendCancellableCoroutine { cont ->
        val uid = currentUid ?: return@suspendCancellableCoroutine cont.resume(null)
        db.collection("users").document(uid).collection("data").document("accounts_list")
            .get()
            .addOnSuccessListener { snapshot ->
                val raw = snapshot.getString("enc")?.let { decrypt(it, uid) }
                    ?: snapshot.getString("json")
                val jsonStr = raw?.takeIf { it.isNotBlank() }
                if (jsonStr != null) {
                    val list = runCatching { json.decodeFromString(accountsSerializer, jsonStr) }.getOrDefault(emptyList())
                    cont.resume(list)
                } else cont.resume(null)
            }
            .addOnFailureListener { cont.resume(null) }
    }

    suspend fun saveFavorites(favs: List<FavItem>) = suspendCancellableCoroutine { cont ->
        val uid = currentUid ?: return@suspendCancellableCoroutine cont.resume(Unit)
        val jsonStr = json.encodeToString(favSerializer, favs)
        db.collection("users").document(uid).collection("data").document("favorites")
            .set(mapOf("json" to jsonStr))
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resume(Unit) }
    }

    suspend fun fetchFavorites(): CloudFetchResult<List<FavItem>> = suspendCancellableCoroutine { cont ->
        val uid = currentUid ?: return@suspendCancellableCoroutine cont.resume(CloudFetchResult.Error())
        db.collection("users").document(uid).collection("data").document("favorites")
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    cont.resume(CloudFetchResult.NotFound)
                    return@addOnSuccessListener
                }
                val jsonStr = snapshot.getString("json")
                if (jsonStr == null) {
                    cont.resume(CloudFetchResult.NotFound)
                } else {
                    val parsed = runCatching { json.decodeFromString(favSerializer, jsonStr) }
                    parsed.fold(
                        onSuccess = { cont.resume(CloudFetchResult.Success(it)) },
                        onFailure = { cont.resume(CloudFetchResult.Error(it)) },
                    )
                }
            }
            .addOnFailureListener { e -> cont.resume(CloudFetchResult.Error(e)) }
    }

    suspend fun getFavorites(): List<FavItem>? =
        (fetchFavorites() as? CloudFetchResult.Success)?.data

    suspend fun saveRecents(recents: List<RecentItem>) = suspendCancellableCoroutine { cont ->
        val uid = currentUid ?: return@suspendCancellableCoroutine cont.resume(Unit)
        val jsonStr = json.encodeToString(recentSerializer, recents)
        db.collection("users").document(uid).collection("data").document("recents")
            .set(mapOf("json" to jsonStr))
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resume(Unit) }
    }

    suspend fun fetchRecents(): CloudFetchResult<List<RecentItem>> = suspendCancellableCoroutine { cont ->
        val uid = currentUid ?: return@suspendCancellableCoroutine cont.resume(CloudFetchResult.Error())
        db.collection("users").document(uid).collection("data").document("recents")
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    cont.resume(CloudFetchResult.NotFound)
                    return@addOnSuccessListener
                }
                val jsonStr = snapshot.getString("json")
                if (jsonStr == null) {
                    cont.resume(CloudFetchResult.NotFound)
                } else {
                    val parsed = runCatching { json.decodeFromString(recentSerializer, jsonStr) }
                    parsed.fold(
                        onSuccess = { cont.resume(CloudFetchResult.Success(it)) },
                        onFailure = { cont.resume(CloudFetchResult.Error(it)) },
                    )
                }
            }
            .addOnFailureListener { e -> cont.resume(CloudFetchResult.Error(e)) }
    }

    suspend fun getRecents(): List<RecentItem>? =
        (fetchRecents() as? CloudFetchResult.Success)?.data

    suspend fun getXtreamAccount(): Account? = suspendCancellableCoroutine { cont ->
        val uid = currentUid ?: return@suspendCancellableCoroutine cont.resume(null)
        db.collection("users").document(uid).collection("data").document("xtream")
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val acc = Account(
                        server = decrypt(snapshot.getString("server") ?: "", uid),
                        user = decrypt(snapshot.getString("user") ?: "", uid),
                        pass = decrypt(snapshot.getString("pass") ?: "", uid)
                    )
                    cont.resume(acc)
                } else {
                    cont.resume(null)
                }
            }
            .addOnFailureListener { cont.resume(null) }
    }

    suspend fun savePreferences(prefs: CloudPreferences) = suspendCancellableCoroutine { cont ->
        val uid = currentUid ?: return@suspendCancellableCoroutine cont.resume(Unit)
        val data = mapOf(
            "lightEffects" to prefs.lightEffects,
            "homeChannelsLayout" to prefs.homeChannelsLayout,
            "homeChannelsSource" to prefs.homeChannelsSource,
            "homeChannelsCategoryId" to prefs.homeChannelsCategoryId,
        )
        db.collection("users").document(uid).collection("data").document("preferences")
            .set(data)
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resume(Unit) }
    }

    suspend fun fetchPreferences(): CloudFetchResult<CloudPreferences> = suspendCancellableCoroutine { cont ->
        val uid = currentUid ?: return@suspendCancellableCoroutine cont.resume(CloudFetchResult.Error())
        db.collection("users").document(uid).collection("data").document("preferences")
            .get()
            .addOnSuccessListener { s ->
                if (s.exists()) {
                    cont.resume(
                        CloudFetchResult.Success(
                            CloudPreferences(
                                lightEffects = s.getBoolean("lightEffects") ?: false,
                                homeChannelsLayout = s.getString("homeChannelsLayout") ?: "CAROUSEL",
                                homeChannelsSource = s.getString("homeChannelsSource") ?: "AUTO",
                                homeChannelsCategoryId = s.getString("homeChannelsCategoryId") ?: "",
                            )
                        )
                    )
                } else {
                    cont.resume(CloudFetchResult.NotFound)
                }
            }
            .addOnFailureListener { e -> cont.resume(CloudFetchResult.Error(e)) }
    }

    suspend fun getPreferences(): CloudPreferences? =
        (fetchPreferences() as? CloudFetchResult.Success)?.data
}
