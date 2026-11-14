package com.saydlawy.sahab.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
