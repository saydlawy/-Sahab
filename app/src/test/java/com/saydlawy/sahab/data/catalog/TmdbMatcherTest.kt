package com.saydlawy.sahab.data.catalog

import com.saydlawy.sahab.data.TmdbSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for TmdbMatcher — ported from v5 Python results.
 * These verify pure functions only (no network, no Room).
 */
class TmdbMatcherTest {

    // ─────────────────── normalizeArabic ───────────────────
    @Test
    fun `normalizeArabic unifies alef variants`() {
        assertEquals("اسد", TmdbMatcher.normalizeArabic("أسد"))
        assertEquals("اسد", TmdbMatcher.normalizeArabic("إسد"))
        assertEquals("اسد", TmdbMatcher.normalizeArabic("آسد"))
    }

    @Test
    fun `normalizeArabic unifies ta-marbuta`() {
        assertEquals("اسره", TmdbMatcher.normalizeArabic("أسرة"))
    }

    @Test
    fun `normalizeArabic strips diacritics`() {
        assertEquals("اسد", TmdbMatcher.normalizeArabic("أَسَد"))
    }

    @Test
    fun `normalizeArabic converts roman numerals`() {
        assertEquals("2", TmdbMatcher.normalizeArabic("II"))
        assertEquals("3", TmdbMatcher.normalizeArabic("III"))
    }

    // ─────────────────── cleanIptv ───────────────────
    @Test
    fun `cleanIptv strips quality tags`() {
        assertEquals("Lions For Lambs", TmdbMatcher.cleanIptv("[SD] Lions For Lambs"))
        assertEquals("Inception", TmdbMatcher.cleanIptv("Inception 1080p BluRay x264"))
    }

    @Test
    fun `cleanIptv strips arabic junk`() {
        assertEquals("الفيل الازرق", TmdbMatcher.cleanIptv("فيلم الفيل الازرق مترجم"))
    }

    @Test
    fun `cleanIptv strips prefix tags`() {
        assertEquals("The Transporter", TmdbMatcher.cleanIptv("AR | The Transporter"))
    }

    // ─────────────────── extractYear ───────────────────
    @Test
    fun `extractYear prefers trailing year`() {
        assertEquals(2007, TmdbMatcher.extractYear("Lions For Lambs 2007", ""))
        assertEquals(2019, TmdbMatcher.extractYear("1917 2019", ""))
    }

    @Test
    fun `extractYear ignores title-only years`() {
        // "1917" is the title, not the year — no trailing year → falls to category
        assertEquals(2019, TmdbMatcher.extractYear("1917", "MOVIES 2019"))
    }

    @Test
    fun `extractYear falls back to category`() {
        assertEquals(2026, TmdbMatcher.extractYear("Some Movie", "ARABIC MOVIES 2026"))
    }

    // ─────────────────── jaroWinkler ───────────────────
    @Test
    fun `jaroWinkler exact match returns 1`() {
        assertEquals(1.0, TmdbMatcher.jaroWinkler("hello", "hello"), 0.001)
    }

    @Test
    fun `jaroWinkler similar strings high score`() {
        assertTrue(TmdbMatcher.jaroWinkler("mission", "missions") > 0.9)
    }

    @Test
    fun `jaroWinkler unrelated strings low score`() {
        assertTrue(TmdbMatcher.jaroWinkler("lion", "transformer") < 0.5)
    }

    // ─────────────────── scoreMatch ───────────────────
    private fun result(
        title: String,
        orig: String = "",
        year: String = "",
        votes: Int = 0,
        pop: Double = 0.0,
    ) = TmdbSearchResult(
        id = 1,
        title = title,
        originalTitle = orig.ifBlank { title },
        releaseDate = if (year.isNotEmpty()) "$year-01-01" else "",
        voteCount = votes,
        popularity = pop,
    )

    @Test
    fun `scoreMatch exact title with matching year is strong`() {
        val variants = listOf("what women want")
        val s = TmdbMatcher.scoreMatch(variants, 2000, result("What Women Want", year = "2000", votes = 5000))
        assertEquals(TmdbMatcher.Level.STRONG, TmdbMatcher.acceptLevel(s))
    }

    @Test
    fun `scoreMatch exact title no year with votes is strong`() {
        val variants = listOf("captain marvel")
        val s = TmdbMatcher.scoreMatch(variants, null, result("Captain Marvel", year = "2019", votes = 18000))
        assertEquals(TmdbMatcher.Level.STRONG, TmdbMatcher.acceptLevel(s))
    }

    @Test
    fun `scoreMatch unrelated title is rejected`() {
        val variants = listOf("the town of ghosts")
        val s = TmdbMatcher.scoreMatch(variants, null, result("The Town", year = "2010", votes = 5000))
        assertTrue(s.titleSim < 0.90)
    }

    @Test
    fun `scoreMatch arabic title with matches`() {
        val variants = TmdbMatcher.extractTitleVariants("جحيم فى الهند")
        val s = TmdbMatcher.scoreMatch(variants, 2016, result("جحيم في الهند", year = "2016", votes = 100))
        assertEquals(TmdbMatcher.Level.STRONG, TmdbMatcher.acceptLevel(s))
    }

    // ─────────────────── extractTitleVariants ───────────────────
    @Test
    fun `extractTitleVariants splits dual language`() {
        val variants = TmdbMatcher.extractTitleVariants("الفيل الازرق - The Blue Elephant")
        assertTrue(variants.any { it.contains("الفيل") })
        assertTrue(variants.any { it.contains("blue", ignoreCase = true) })
    }

    @Test
    fun `extractTitleVariants single language returns one`() {
        val variants = TmdbMatcher.extractTitleVariants("Inception")
        assertEquals(1, variants.size)
    }

    // ─────────────────── generateQueries ───────────────────
    @Test
    fun `generateQueries produces non-empty cascade`() {
        val (queries, variants) = TmdbMatcher.generateQueries("Inception", "MOVIES 2010", 2010)
        assertTrue(queries.isNotEmpty())
        assertTrue(variants.any { it.equals("inception", ignoreCase = true) })
        assertTrue(queries.any { it.year == 2010 })
    }

    @Test
    fun `generateQueries for dual-language title`() {
        val (queries, _) = TmdbMatcher.generateQueries(
            "الفيل الازرق - The Blue Elephant",
            "ARABIC MOVIES 2019",
            2019,
        )
        assertTrue(queries.any { it.q.contains("الفيل") })
        assertTrue(queries.any { it.q.contains("blue", ignoreCase = true) })
    }

    // ─────────────────── Real v5 samples ───────────────────
    @Test
    fun `v5 sample - What Women Want`() {
        val variants = TmdbMatcher.extractTitleVariants(
            TmdbMatcher.cleanIptv("What Women Want [C]")
        )
        val s = TmdbMatcher.scoreMatch(variants, null, result("What Women Want", year = "2000", votes = 5000))
        assertEquals(TmdbMatcher.Level.STRONG, TmdbMatcher.acceptLevel(s))
    }

    @Test
    fun `v5 sample - The Transporter`() {
        val variants = TmdbMatcher.extractTitleVariants(
            TmdbMatcher.cleanIptv("[SD] The Transporter")
        )
        val s = TmdbMatcher.scoreMatch(variants, null, result("The Transporter", year = "2002", votes = 4000))
        assertEquals(TmdbMatcher.Level.STRONG, TmdbMatcher.acceptLevel(s))
    }

    @Test
    fun `v5 sample - Curse of the Witchs Doll should reject La Llorona`() {
        val variants = TmdbMatcher.extractTitleVariants(
            TmdbMatcher.cleanIptv("Curse Of The Witchs Doll[SD]")
        )
        val s = TmdbMatcher.scoreMatch(variants, null, result("The Curse of La Llorona", year = "2019", votes = 2700))
        // Should be weak or reject — NOT strong
        assertTrue(TmdbMatcher.acceptLevel(s) != TmdbMatcher.Level.STRONG)
    }
}
