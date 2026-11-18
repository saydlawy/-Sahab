package com.saydlawy.sahab.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerLeaseCoordinatorTest {

    private class FakeLeasedPlayer(
        override val role: PlayerRole,
        private val onReleaseCallback: (() -> Unit)? = null,
    ) : LeasedPlayerHandle {
        var releaseCount: Int = 0
            private set

        override fun stopAndRelease() {
            releaseCount++
            onReleaseCallback?.invoke()
        }
    }

    @Test
    fun acquire_fullscreenPreemptsActivePreviewSynchronously() {
        val coordinator = PlayerLeaseCoordinator()
        val events = mutableListOf<String>()
        var previewRefCleared = false

        val preview = FakeLeasedPlayer(PlayerRole.PREVIEW) {
            previewRefCleared = true
            events += "preview_released"
        }
        val fullscreen = FakeLeasedPlayer(PlayerRole.FULLSCREEN) {
            events += "fullscreen_released"
        }

        coordinator.acquire(preview)
        assertTrue(coordinator.isActive(preview))
        assertEquals(PlayerRole.PREVIEW, coordinator.activeRole())

        coordinator.acquire(fullscreen)

        assertEquals(1, preview.releaseCount)
        assertTrue(previewRefCleared)
        assertFalse(coordinator.isActive(preview))
        assertTrue(coordinator.isActive(fullscreen))
        assertEquals(PlayerRole.FULLSCREEN, coordinator.activeRole())
        assertEquals(listOf("preview_released"), events)
    }

    @Test
    fun acquire_previewPreemptsLingeringFullscreenDuringBackTransition() {
        val coordinator = PlayerLeaseCoordinator()
        val fullscreen = FakeLeasedPlayer(PlayerRole.FULLSCREEN)
        val preview = FakeLeasedPlayer(PlayerRole.PREVIEW)

        coordinator.acquire(fullscreen)
        assertEquals(PlayerRole.FULLSCREEN, coordinator.activeRole())

        coordinator.acquire(preview)

        assertEquals(1, fullscreen.releaseCount)
        assertFalse(coordinator.isActive(fullscreen))
        assertTrue(coordinator.isActive(preview))
        assertEquals(PlayerRole.PREVIEW, coordinator.activeRole())
    }

    @Test
    fun releaseByRole_releasesPreviewOnDoubleOkWithoutAffectingFullscreen() {
        val coordinator = PlayerLeaseCoordinator()
        val preview = FakeLeasedPlayer(PlayerRole.PREVIEW)
        coordinator.acquire(preview)

        // Simulates LiveScreen.playAt() releasing active preview before navigating to fullscreen
        coordinator.releaseByRole(PlayerRole.PREVIEW)
        assertEquals(1, preview.releaseCount)
        assertNull(coordinator.activeRole())

        val fullscreen = FakeLeasedPlayer(PlayerRole.FULLSCREEN)
        coordinator.acquire(fullscreen)

        // Releasing PREVIEW role while FULLSCREEN is active must not touch fullscreen
        coordinator.releaseByRole(PlayerRole.PREVIEW)
        assertEquals(0, fullscreen.releaseCount)
        assertTrue(coordinator.isActive(fullscreen))
    }

    @Test
    fun release_isIdempotentAcrossExplicitExitAndOnDispose() {
        val coordinator = PlayerLeaseCoordinator()
        val fullscreen = FakeLeasedPlayer(PlayerRole.FULLSCREEN)
        coordinator.acquire(fullscreen)

        // First release in exitPlayerNow() on Back press
        coordinator.release(fullscreen)
        // Second release in DisposableEffect.onDispose after NavHost popExitTransition completes
        coordinator.release(fullscreen)

        assertEquals(1, fullscreen.releaseCount)
        assertFalse(coordinator.isActive(fullscreen))
        assertNull(coordinator.activeRole())
    }
}
