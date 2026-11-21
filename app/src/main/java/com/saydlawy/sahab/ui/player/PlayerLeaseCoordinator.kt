package com.saydlawy.sahab.ui.player

/**
 * Coordinates exclusive ownership of active media players across preview and fullscreen surfaces
 * so that two [androidx.media3.exoplayer.ExoPlayer] instances never decode or hold live IPTV HTTP
 * connections simultaneously during screen transitions.
 */
enum class PlayerRole {
    PREVIEW,
    FULLSCREEN,
}

interface LeasedPlayerHandle {
    val role: PlayerRole
    fun stopAndRelease()
}

class PlayerLeaseCoordinator {

    private val lock = Any()
    private var activeHandle: LeasedPlayerHandle? = null

    /**
     * Acquires the exclusive player lease for [handle].
     * Any previously active handle is synchronously stopped and released before [handle] becomes active.
     */
    fun acquire(handle: LeasedPlayerHandle) {
        val previous = synchronized(lock) {
            val prev = activeHandle
            activeHandle = handle
            if (prev != null && prev !== handle) prev else null
        }
        previous?.runCatching { stopAndRelease() }
    }

    /**
     * Synchronously stops and releases [handle] if it currently holds the active lease.
     * Safe to invoke idempotently from both explicit exit paths and Compose `DisposableEffect.onDispose`
     * without retaining strong references to already-released handles.
     */
    fun release(handle: LeasedPlayerHandle?) {
        if (handle == null) return
        val shouldRelease = synchronized(lock) {
            if (activeHandle === handle) {
                activeHandle = null
                true
            } else {
                false
            }
        }
        if (shouldRelease) {
            handle.runCatching { stopAndRelease() }
        }
    }

    /**
     * Synchronously stops and releases the active handle if its role matches [role].
     */
    fun releaseByRole(role: PlayerRole) {
        val target = synchronized(lock) {
            val current = activeHandle
            if (current != null && current.role == role) {
                activeHandle = null
                current
            } else {
                null
            }
        }
        target?.runCatching { stopAndRelease() }
    }

    /**
     * Synchronously stops and releases whichever handle currently holds the lease.
     */
    fun releaseAll() {
        val target = synchronized(lock) {
            val current = activeHandle
            activeHandle = null
            current
        }
        target?.runCatching { stopAndRelease() }
    }

    fun activeRole(): PlayerRole? = synchronized(lock) {
        activeHandle?.role
    }

    fun isActive(handle: LeasedPlayerHandle): Boolean = synchronized(lock) {
        activeHandle === handle
    }
}
