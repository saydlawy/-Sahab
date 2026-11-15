package com.saydlawy.sahab.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.saydlawy.sahab.data.FavItem
import com.saydlawy.sahab.data.RecentItem
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import com.saydlawy.sahab.data.Account
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class UserSyncService {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val currentUid: String?
        get() = auth.currentUser?.uid

    suspend fun saveXtreamAccount(account: Account) = suspendCancellableCoroutine { cont ->
        val uid = currentUid ?: return@suspendCancellableCoroutine cont.resume(Unit)
        val data = mapOf(
            "server" to account.server,
            "user" to account.user,
            "pass" to account.pass
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
            .set(mapOf("json" to jsonStr))
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resume(Unit) }
    }

    suspend fun getAccounts(): List<Account>? = suspendCancellableCoroutine { cont ->
        val uid = currentUid ?: return@suspendCancellableCoroutine cont.resume(null)
        db.collection("users").document(uid).collection("data").document("accounts_list")
            .get()
            .addOnSuccessListener { snapshot ->
                val jsonStr = snapshot.getString("json")
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

    suspend fun getFavorites(): List<FavItem>? = suspendCancellableCoroutine { cont ->
        val uid = currentUid ?: return@suspendCancellableCoroutine cont.resume(null)
        db.collection("users").document(uid).collection("data").document("favorites")
            .get()
            .addOnSuccessListener { snapshot ->
                val jsonStr = snapshot.getString("json")
                if (jsonStr != null) {
                    val list = runCatching { json.decodeFromString(favSerializer, jsonStr) }.getOrDefault(emptyList())
                    cont.resume(list)
                } else cont.resume(null)
            }
            .addOnFailureListener { cont.resume(null) }
    }

    suspend fun saveRecents(recents: List<RecentItem>) = suspendCancellableCoroutine { cont ->
        val uid = currentUid ?: return@suspendCancellableCoroutine cont.resume(Unit)
        val jsonStr = json.encodeToString(recentSerializer, recents)
        db.collection("users").document(uid).collection("data").document("recents")
            .set(mapOf("json" to jsonStr))
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resume(Unit) }
    }

    suspend fun getRecents(): List<RecentItem>? = suspendCancellableCoroutine { cont ->
        val uid = currentUid ?: return@suspendCancellableCoroutine cont.resume(null)
        db.collection("users").document(uid).collection("data").document("recents")
            .get()
            .addOnSuccessListener { snapshot ->
                val jsonStr = snapshot.getString("json")
                if (jsonStr != null) {
                    val list = runCatching { json.decodeFromString(recentSerializer, jsonStr) }.getOrDefault(emptyList())
                    cont.resume(list)
                } else cont.resume(null)
            }
            .addOnFailureListener { cont.resume(null) }
    }

    suspend fun getXtreamAccount(): Account? = suspendCancellableCoroutine { cont ->
        val uid = currentUid ?: return@suspendCancellableCoroutine cont.resume(null)
        db.collection("users").document(uid).collection("data").document("xtream")
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val acc = Account(
                        server = snapshot.getString("server") ?: "",
                        user = snapshot.getString("user") ?: "",
                        pass = snapshot.getString("pass") ?: ""
                    )
                    cont.resume(acc)
                } else {
                    cont.resume(null)
                }
            }
            .addOnFailureListener { cont.resume(null) }
    }
}
