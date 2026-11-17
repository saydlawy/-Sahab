package com.saydlawy.sahab.data

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {

    private val parser = M3uParser(OkHttpClient())

    @Test
    fun parseLines_acceptsHttpAndHttpsOnlyAndIgnoresMalformedEntries() {
        val sample = listOf(
            "#EXTM3U",
            "",
            "   ",
            "#EXTINF:-1 tvg-logo=\"https://logo.png\" group-title=\"News\" tvg-id=\"ch1\",Channel One",
            "https://stream.example.com/live/ch1.m3u8",
            "#EXTINF:-1,Bad Protocol",
            "rtsp://stream.example.com/bad",
            "#EXTINF:-1,No Protocol At All",
            "stream.example.com/missing-scheme.ts",
            "#EXTINF:-1,Orphaned ExtInf Without Url",
            "#EXTINF:-1 group-title=\"Sports\",Http Stream",
            "HTTP://cdn.example.com/live/2.ts",
        )

        val result = parser.parseLines(sample.asSequence())
        assertEquals(2, result.size)
        assertEquals("Channel One", result[0].name)
        assertEquals("https://stream.example.com/live/ch1.m3u8", result[0].url)
        assertEquals("News", result[0].categoryId)
        assertEquals("Http Stream", result[1].name)
        assertEquals("HTTP://cdn.example.com/live/2.ts", result[1].url)
    }

    @Test
    fun parseLines_returnsEmpty_whenOnlyCommentsOrBlankLines() {
        val result = parser.parseLines(sequenceOf("#EXTM3U", "", "#EXTINF:-1,Empty Only"))
        assertTrue(result.isEmpty())
    }
}
