package com.saydlawy.sahab.data.auth

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AuthManager {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    suspend fun signInWithEmail(email: String, pass: String): FirebaseUser? = suspendCancellableCoroutine { cont ->
        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener { cont.resume(it.user) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    suspend fun signUpWithEmail(email: String, pass: String): FirebaseUser? = suspendCancellableCoroutine { cont ->
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener { cont.resume(it.user) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    suspend fun signInWithGoogle(idToken: String): FirebaseUser? = suspendCancellableCoroutine { cont ->
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener { cont.resume(it.user) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    fun signOut(context: Context? = null) {
        auth.signOut()
    }
}
