package com.saydlawy.sahab.ui.browse

import com.saydlawy.sahab.util.RatingSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VodCustomOrganizerRatingTest {

    @Test
    fun sanitizerRejectsSentinelTenAndTinyVoteFlukes() {
        assertEquals("", RatingSanitizer.format(""))
        assertEquals("", RatingSanitizer.format("0"))
        assertEquals("", RatingSanitizer.format("0.0"))
        assertEquals("", RatingSanitizer.format("-1"))
        assertEquals("", RatingSanitizer.format("9.9"))
        assertEquals("", RatingSanitizer.format("10"))
        assertEquals("", RatingSanitizer.format("10.0"))
        assertEquals("", RatingSanitizer.format("10.00"))
        assertEquals("", RatingSanitizer.format("10/10"))
        assertEquals("", RatingSanitizer.format("10,0"))
        assertNull(RatingSanitizer.parseValid("10.0", voteCount = 5000))

        // Tiny-sample TMDB flukes (>= 9.0 with 1..9 votes) must be rejected
        assertEquals("", RatingSanitizer.format("9.5", voteCount = 1))
        assertEquals("", RatingSanitizer.format("9.0", voteCount = 9))
        assertEquals("", RatingSanitizer.format(10.0, voteCount = 1))
        assertEquals("", RatingSanitizer.format(9.4, voteCount = 3))

        // Valid scores format consistently with Locale.US
        assertEquals("8.6", RatingSanitizer.format("8.6", voteCount = 5000))
        assertEquals("9.1", RatingSanitizer.format("9.1", voteCount = 1200))
        assertEquals("7.5", RatingSanitizer.format("7.5/10"))
        assertEquals("7.8", RatingSanitizer.format("7,8"))
    }

    @Test
    fun sanitizerMetadataHelpers_extractYearAndPrimaryGenreCleanly() {
        assertEquals("2024", RatingSanitizer.extractFourDigitYear("2024-05-11"))
        assertEquals("2021", RatingSanitizer.extractFourDigitYear("فيلم تجريبي 2021"))
        assertEquals("", RatingSanitizer.extractFourDigitYear("no year"))

        assertEquals("Action", RatingSanitizer.extractPrimaryGenre("Action, Drama"))
        assertEquals("دراما", RatingSanitizer.extractPrimaryGenre("دراما، أكشن"))
        assertEquals("", RatingSanitizer.extractPrimaryGenre("   "))
    }
}
