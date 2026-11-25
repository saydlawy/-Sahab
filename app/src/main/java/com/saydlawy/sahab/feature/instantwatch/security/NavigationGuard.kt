package com.saydlawy.sahab.feature.instantwatch.security

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

class NavigationGuard(
    private val policy: StreamSecurityPolicy,
    private val inspector: RequestInspector,
    private val monitor: SecurityMonitor,
) {
    /**
     * Returns true if navigation should be BLOCKED (matches WebViewClient.shouldOverrideUrlLoading).
     */
    fun shouldBlockNavigation(url: String?, isForMainFrame: Boolean): Boolean {
        val target = url?.trim().orEmpty()
        if (target.isEmpty()) return true

        val check = policy.validateUrl(target, requireTrustedHost = isForMainFrame)
        if (!check.isAllowed) {
            val eventType = when (check.verdict) {
                PolicyVerdict.BLOCK_EXECUTABLE_DOWNLOAD -> SecurityEventType.EXECUTABLE_DOWNLOAD_BLOCKED
                else -> SecurityEventType.UNTRUSTED_REDIRECT_BLOCKED
            }
            monitor.record(eventType, check.reason)
            return true
        }

        val inspected = inspector.inspect(target, isForMainFrame = isForMainFrame)
        if (!inspected.allowed) {
            monitor.record(SecurityEventType.UNTRUSTED_REDIRECT_BLOCKED, inspected.reason)
            return true
        }

        monitor.record(SecurityEventType.VIDEO_SOURCE_REVERIFIED, "Verified navigation: ${check.host}")
        return false
    }

    /**
     * Returns an empty blocked WebResourceResponse if the request must be blocked,
     * or null to let WebView proceed normally.
     */
    fun interceptRequest(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url?.toString().orEmpty()
        val accept = request.requestHeaders?.entries
            ?.firstOrNull { it.key.equals("Accept", ignoreCase = true) }
            ?.value

        val inspected = inspector.inspect(
            url = url,
            isForMainFrame = request.isForMainFrame,
            acceptHeader = accept,
        )

        if (!inspected.allowed) {
            val eventType = when {
                inspected.reason.contains("executable", ignoreCase = true) ->
                    SecurityEventType.EXECUTABLE_DOWNLOAD_BLOCKED
                request.isForMainFrame ->
                    SecurityEventType.UNTRUSTED_REDIRECT_BLOCKED
                else ->
                    SecurityEventType.AD_REQUEST_BLOCKED
            }
            monitor.record(eventType, inspected.reason)
            return blockedEmptyResponse()
        }

        if (inspected.category == RequestCategory.SUBTITLE) {
            monitor.record(SecurityEventType.SUBTITLE_ALLOWED, "Verified subtitle request")
        }

        return null
    }

    fun onPopupAttempt(url: String?) {
        monitor.record(SecurityEventType.POPUP_BLOCKED, "Blocked popup window: ${url.orEmpty()}")
    }

    fun onPermissionRequestDenied(resources: Array<String>) {
        monitor.record(
            SecurityEventType.PERMISSION_DENIED,
            "Denied device permissions: ${resources.joinToString()}",
        )
    }

    fun onExecutableDownloadBlocked(url: String) {
        monitor.record(SecurityEventType.EXECUTABLE_DOWNLOAD_BLOCKED, "Blocked download: $url")
    }

    private fun blockedEmptyResponse(): WebResourceResponse =
        WebResourceResponse(
            "text/plain",
            "utf-8",
            403,
            "Blocked by InstantWatch Security Policy",
            emptyMap(),
            ByteArrayInputStream(ByteArray(0)),
        )
}
