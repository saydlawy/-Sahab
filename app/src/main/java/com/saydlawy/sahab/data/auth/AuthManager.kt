package com.saydlawy.sahab.data.auth

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInClient
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

    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("570342210661-jcnh1kfrnfsbara01nr11mbplsjhnl6d.apps.googleusercontent.com") // سنستبدلها لاحقاً
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

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

    fun signOut() {
        auth.signOut()
    }
}
