package com.saydlawy.sahab.feature.instantwatch.domain

import com.google.firebase.auth.FirebaseAuth
import com.saydlawy.sahab.feature.instantwatch.local.InstantWatchPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed class GateResult {
    data class Authorized(val uid: String) : GateResult()
    data object ServiceDisabled : GateResult()
    data object Unauthenticated : GateResult()
    data class SessionInvalid(val reason: String) : GateResult()
}

class AuthenticationGate(
    private val preferences: InstantWatchPreferences,
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    /**
     * Observes live sign-in state changes so UI and PlayerSessionManager
     * immediately terminate playback and clear session data on sign-out.
     */
    fun observeAuthSignedIn(): Flow<Boolean> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser != null)
        }
        firebaseAuth.addAuthStateListener(listener)
        trySend(firebaseAuth.currentUser != null)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    fun hasCachedUser(): Boolean = firebaseAuth.currentUser != null

    /**
     * Domain-level verification:
     * 1) Service must be enabled in InstantWatchPreferences.
     * 2) User must be signed in AND possess a valid non-expired Firebase ID token.
     */
    suspend fun verifyAccess(forceTokenRefresh: Boolean = false): GateResult {
        if (!preferences.isEnabled) {
            return GateResult.ServiceDisabled
        }

        val user = firebaseAuth.currentUser ?: return GateResult.Unauthenticated

        val tokenValid = suspendCancellableCoroutine { cont ->
            user.getIdToken(forceTokenRefresh)
                .addOnSuccessListener { result ->
                    if (cont.isActive) cont.resume(!result.token.isNullOrBlank())
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume(false)
                }
        }

        return if (tokenValid) {
            GateResult.Authorized(user.uid)
        } else {
            GateResult.SessionInvalid("انتهت صلاحية جلسة تسجيل الدخول. يرجى إعادة تسجيل الدخول.")
        }
    }
}
