package com.saydlawy.sahab.data.catalog

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class CatalogCloudMergeTest {

    @Test
    fun mergeCloudBatchPreservingLocal_preservesIptvProviderFieldsAndIsolatesCloudRefs() = runTest {
        val stored = linkedMapOf(
            "pending:901" to CatalogMovieEntity(
                sahabId = "pending:901",
                novaStreamId = "901",
                categoryId = "cat_ar",
                titleAr = "فيلم المستخدم",
                mediaType = "movie",
                posterUrl = "https://iptv.local/poster.jpg",
                containerExtension = "mkv",
                providerOrder = 4,
                tmdbId = 550,
                lastSeen = 5_000L,
            ),
            "series_pending:202" to CatalogMovieEntity(
                sahabId = "series_pending:202",
                novaStreamId = "202",
                categoryId = "cat_series",
                titleAr = "مسلسل المستخدم",
                mediaType = "series",
                providerOrder = 1,
                tmdbId = 1396,
                episodesJson = """[{"id":"e1","season":1,"number":1,"title":"الأولى"}]""",
                lastSeen = 6_000L,
            ),
        )

        val dao = Proxy.newProxyInstance(
            CatalogMovieDao::class.java.classLoader,
            arrayOf(CatalogMovieDao::class.java),
        ) { _, method, args ->
            when (method.name) {
                "findByIds" -> {
                    @Suppress("UNCHECKED_CAST")
                    val ids = args?.getOrNull(0) as? List<String> ?: emptyList()
                    ids.mapNotNull { stored[it] }
                }
                "findByTmdbIds" -> {
                    @Suppress("UNCHECKED_CAST")
                    val tids = (args?.getOrNull(0) as? List<Int> ?: emptyList()).toSet()
                    stored.values.filter { it.tmdbId != null && it.tmdbId in tids }
                }
                "upsertAll" -> {
                    @Suppress("UNCHECKED_CAST")
                    val items = args?.getOrNull(0) as? List<CatalogMovieEntity> ?: emptyList()
                    items.forEach { stored[it.sahabId] = it }
                    Unit
                }
                else -> null
            }
        } as CatalogMovieDao

        val cloudBatch = listOf(
            CatalogMovieEntity(
                sahabId = "tmdb:550",
                novaStreamId = "99999",
                categoryId = "cloud_cat",
                titleAr = "عنوان سحابي",
                titleEn = "Fight Club",
                plot = "قصة الفيلم من السحابة",
                genre = "دراما",
                rating = "8.8",
                posterUrl = "https://cloud.example/p.jpg",
                backdropUrl = "https://cloud.example/b.jpg",
                mediaType = "movie",
                tmdbId = 550,
                updatedAt = 9_000L,
            ),
            CatalogMovieEntity(
                sahabId = "tmdb:1396",
                novaStreamId = "88888",
                titleAr = "بريكينج باد",
                titleEn = "Breaking Bad",
                plot = "قصة المسلسل من السحابة",
                mediaType = "series",
                tmdbId = 1396,
                updatedAt = 9_000L,
            ),
            CatalogMovieEntity(
                sahabId = "tmdb:603",
                novaStreamId = "77777",
                titleAr = "ذا ماتريكس",
                titleEn = "The Matrix",
                plot = "غير موجود عند مزود المستخدم",
                mediaType = "movie",
                tmdbId = 603,
                updatedAt = 9_000L,
            ),
            CatalogMovieEntity(
                sahabId = "tmdb:680",
                novaStreamId = "66666",
                titleAr = "بالب فيكشن",
                titleEn = "Pulp Fiction",
                plot = "عمل سحابي ثانٍ",
                mediaType = "movie",
                tmdbId = 680,
                updatedAt = 9_000L,
            ),
        )

        dao.mergeCloudBatchPreservingLocalWithDao(cloudBatch)

        // User IPTV movie merged by tmdbId without overwriting IPTV identifiers/order
        val mergedMovie = stored["pending:901"]!!
        assertEquals("901", mergedMovie.novaStreamId)
        assertEquals("cat_ar", mergedMovie.categoryId)
        assertEquals("فيلم المستخدم", mergedMovie.titleAr)
        assertEquals("https://iptv.local/poster.jpg", mergedMovie.posterUrl)
        assertEquals("mkv", mergedMovie.containerExtension)
        assertEquals(4, mergedMovie.providerOrder)
        assertEquals("قصة الفيلم من السحابة", mergedMovie.plot)
        assertEquals("Fight Club", mergedMovie.titleEn)
        assertTrue(mergedMovie.searchTitle.contains("fight club"))
        assertTrue(mergedMovie.isProviderItem)

        // User IPTV series keeps episodesJson and order while taking cloud plot
        val mergedSeries = stored["series_pending:202"]!!
        assertEquals("202", mergedSeries.novaStreamId)
        assertEquals(1, mergedSeries.providerOrder)
        assertTrue(mergedSeries.episodesJson.contains("\"e1\""))
        assertEquals("قصة المسلسل من السحابة", mergedSeries.plot)

        // Unmatched cloud-only items stored with distinct non-colliding sentinel IDs and not marked as provider items
        val cloudRef1 = stored["tmdb:603"]!!
        val cloudRef2 = stored["tmdb:680"]!!
        assertFalse(cloudRef1.isProviderItem)
        assertFalse(cloudRef2.isProviderItem)
        assertEquals("__cloud__:tmdb:603", cloudRef1.novaStreamId)
        assertEquals("__cloud__:tmdb:680", cloudRef2.novaStreamId)
        assertEquals(Int.MAX_VALUE, cloudRef1.providerOrder)
    }
}
