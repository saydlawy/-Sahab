package com.saydlawy.sahab.ui.browse

import com.saydlawy.sahab.data.Poster
import com.saydlawy.sahab.data.VodCustomGrouping
import com.saydlawy.sahab.data.catalog.CatalogFacetRow
import com.saydlawy.sahab.util.RatingSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

        // Bayesian score ranks genuine high-vote movies ahead of sparse-vote movies
        val classic = RatingSanitizer.rankingScore("8.4", voteCount = 5000)
        val sparse = RatingSanitizer.rankingScore("8.8", voteCount = 2)
        val fluke = RatingSanitizer.rankingScore("10.0", voteCount = 1)
        assertTrue(classic > sparse)
        assertTrue(sparse > fluke)
        assertEquals(0.0, fluke, 0.0001)
    }

    @Test
    fun organizeStripsBogusTensAndRanksRealMoviesFirstAcrossGenreYearAndCast() {
        val posters = listOf(
            Poster(id = "xtream_ten", name = "Bogus Xtream Ten", image = "a", rating = "10", year = "2024", categoryId = "1", added = 500L),
            Poster(id = "tmdb_fluke", name = "One Vote TMDB Ten", image = "b", rating = "10.0", year = "2024", categoryId = "1", added = 400L),
            Poster(id = "low_vote", name = "Low Vote High Rating", image = "c", rating = "8.8", year = "2024", categoryId = "1", added = 300L),
            Poster(id = "real_hit", name = "Real Cinema Hit", image = "d", rating = "10.0", year = "2024", categoryId = "1", added = 200L),
        )
        val facets = mapOf(
            "xtream_ten" to CatalogFacetRow(
                novaStreamId = "xtream_ten",
                year = "2024",
                genre = "Action",
                cast = "Actor One",
                rating = "",
                novaAdded = 500L,
                tmdbVoteCount = null,
            ),
            "tmdb_fluke" to CatalogFacetRow(
                novaStreamId = "tmdb_fluke",
                year = "2024",
                genre = "Action",
                cast = "Actor One",
                rating = "10.0",
                novaAdded = 400L,
                tmdbVoteCount = 1,
            ),
            "low_vote" to CatalogFacetRow(
                novaStreamId = "low_vote",
                year = "2024",
                genre = "Action",
                cast = "Actor One",
                rating = "8.8",
                novaAdded = 300L,
                tmdbVoteCount = 2,
            ),
            "real_hit" to CatalogFacetRow(
                novaStreamId = "real_hit",
                year = "2024",
                genre = "Action",
                cast = "Actor One",
                rating = "8.4",
                novaAdded = 200L,
                tmdbVoteCount = 5000,
            ),
        )

        val groupings = listOf(
            VodCustomGrouping.GENRE,
            VodCustomGrouping.YEAR,
            VodCustomGrouping.CAST,
        )
        val forbidden = setOf("10", "10.0", "10.00", "9.9")

        for (grouping in groupings) {
            val result = VodCustomOrganizer.organize(posters, facets, grouping)
            assertTrue("Expected categories for $grouping", result.categories.isNotEmpty())
            for ((catId, list) in result.itemsByCategory) {
                assertTrue("Expected populated category $catId for $grouping", list.isNotEmpty())
                for (p in list) {
                    assertTrue(
                        "Forbidden rating '${p.rating}' leaked in $grouping ($catId) for ${p.id}",
                        p.rating !in forbidden,
                    )
                }
                assertEquals(
                    "Real high-vote movie must rank #1 in $grouping ($catId)",
                    "real_hit",
                    list.first().id,
                )
                assertEquals(
                    "Low-vote valid movie must rank #2 in $grouping ($catId)",
                    "low_vote",
                    list[1].id,
                )
            }
        }
    }
}
