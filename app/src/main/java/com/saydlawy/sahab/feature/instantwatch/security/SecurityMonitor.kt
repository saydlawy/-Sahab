package com.saydlawy.sahab.feature.instantwatch.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class SecurityEventType {
    POPUP_BLOCKED,
    UNTRUSTED_REDIRECT_BLOCKED,
    EXECUTABLE_DOWNLOAD_BLOCKED,
    PERMISSION_DENIED,
    AD_REQUEST_BLOCKED,
    VIDEO_SOURCE_REVERIFIED,
    SUBTITLE_ALLOWED,
    PLAYBACK_FAILED_AFTER_PROTECTION,
}

data class SecurityEvent(
    val type: SecurityEventType,
    val detail: String,
    val timestampMs: Long = System.currentTimeMillis(),
)

data class SecurityMonitorState(
    val blockedCount: Int = 0,
    val lastFailureReason: String? = null,
    val recentEvents: List<SecurityEvent> = emptyList(),
)

class SecurityMonitor {
    private val _state = MutableStateFlow(SecurityMonitorState())
    val state: StateFlow<SecurityMonitorState> = _state.asStateFlow()

    fun record(type: SecurityEventType, detail: String) {
        val isBlock = type in BLOCKED_TYPES
        val failureReason = if (type == SecurityEventType.PLAYBACK_FAILED_AFTER_PROTECTION) detail else null
        _state.update { current ->
            val updatedEvents = (current.recentEvents + SecurityEvent(type, detail)).takeLast(MAX_EVENTS)
            current.copy(
                blockedCount = if (isBlock) current.blockedCount + 1 else current.blockedCount,
                lastFailureReason = failureReason ?: current.lastFailureReason,
                recentEvents = updatedEvents,
            )
        }
    }

    fun clearFailure() {
        _state.update { it.copy(lastFailureReason = null) }
    }

    fun reset() {
        _state.value = SecurityMonitorState()
    }

    companion object {
        private const val MAX_EVENTS = 30
        private val BLOCKED_TYPES = setOf(
            SecurityEventType.POPUP_BLOCKED,
            SecurityEventType.UNTRUSTED_REDIRECT_BLOCKED,
            SecurityEventType.EXECUTABLE_DOWNLOAD_BLOCKED,
            SecurityEventType.PERMISSION_DENIED,
            SecurityEventType.AD_REQUEST_BLOCKED,
        )
    }
}
