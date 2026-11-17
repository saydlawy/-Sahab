package com.saydlawy.sahab.ui.player

import android.net.Uri
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerHttpErrorTest {

    @Test
    fun extractHttpResponseCode_findsNestedInvalidResponseCodeException() {
        val dataSpec = DataSpec(Uri.EMPTY)
        val httpEx = HttpDataSource.InvalidResponseCodeException(
            403,
            "Forbidden",
            null,
            emptyMap(),
            dataSpec,
            ByteArray(0),
        )
        val wrapped = PlaybackException("Playback failed", httpEx, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
        assertEquals(403, extractHttpResponseCode(wrapped))
    }

    @Test
    fun extractHttpResponseCode_ignoresNumbersInsideUrlMessageWhenNotHttpStatusException() {
        val fakeException = RuntimeException("Failed opening https://cdn.example.com/movies/403.mp4")
        val wrapped = PlaybackException("Error", fakeException, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        assertNull(extractHttpResponseCode(wrapped))
    }
}
