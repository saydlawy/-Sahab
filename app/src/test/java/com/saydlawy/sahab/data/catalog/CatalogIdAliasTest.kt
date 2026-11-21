package com.saydlawy.sahab.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogIdAliasTest {

    @Test
    fun aliasesFor_includesPendingAlias_whenTmdbDocument() {
        val entity = CatalogMovieEntity(
            sahabId = "tmdb:550",
            novaStreamId = "9001",
            titleAr = "فيلم تجريبي",
        )
        assertEquals(listOf("pending:9001"), FirebaseCatalogService.aliasesFor(entity))
    }

    @Test
    fun aliasesFor_returnsEmpty_whenStillPending() {
        val entity = CatalogMovieEntity(
            sahabId = "pending:9001",
            novaStreamId = "9001",
            titleAr = "فيلم تجريبي",
        )
        assertTrue(FirebaseCatalogService.aliasesFor(entity).isEmpty())
    }

    @Test
    fun resolveRedirectId_followsTargetAndGuardsSelfLoop() {
        assertEquals("tmdb:550", FirebaseCatalogService.resolveRedirectId("pending:9001", "tmdb:550"))
        assertEquals("pending:9001", FirebaseCatalogService.resolveRedirectId("pending:9001", "pending:9001"))
        assertEquals("pending:9001", FirebaseCatalogService.resolveRedirectId("pending:9001", ""))
    }

    @Test
    fun idPending_separatesSeriesAndMoviePrimaryKeysWithoutCollision() {
        val moviePending = CatalogMovieEntity.idPending("1234", "movie")
        val seriesPending = CatalogMovieEntity.idPending("1234", "series")
        assertEquals("pending:1234", moviePending)
        assertEquals("series_pending:1234", seriesPending)

        val seriesEntity = CatalogMovieEntity(
            sahabId = "tmdb:999",
            novaStreamId = "1234",
            mediaType = "series",
            titleAr = "مسلسل تجريبي",
        )
        assertEquals(listOf("series_pending:1234"), FirebaseCatalogService.aliasesFor(seriesEntity))
    }
}
