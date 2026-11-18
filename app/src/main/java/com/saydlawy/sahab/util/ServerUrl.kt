package com.saydlawy.sahab.util

internal object ServerUrl {
    private val HOST_PORT_REGEX = Regex("^(https?://[^/?#]+)", RegexOption.IGNORE_CASE)

    /** Keeps only scheme, host and port: "host:8080/anything" becomes "http://host:8080". */
    fun normalizeServer(raw: String): String? {
        var text = raw.trim().replace(" ", "")
        if (text.isEmpty()) return null
        if (!text.startsWith("http://", ignoreCase = true) && !text.startsWith("https://", ignoreCase = true)) {
            text = "http://$text"
        }
        val match = HOST_PORT_REGEX.find(text) ?: return null
        return match.groupValues[1].trimEnd('/')
    }
}
