package com.saydlawy.sahab.ui.browse

import com.saydlawy.sahab.data.Category
import com.saydlawy.sahab.data.Poster
import com.saydlawy.sahab.data.VodCustomGrouping
import com.saydlawy.sahab.data.catalog.CatalogFacetRow

internal const val CUSTOM_CAT_PREFIX = "__CUSTOM__:"

internal data class CustomOrganizationResult(
    val categories: List<Category>,
    val itemsByCategory: Map<String, List<Poster>>,
    val counts: Map<String, Int>,
)

internal object VodCustomOrganizer {

    private val yearRegex = Regex("""\b(19\d{2}|20\d{2})\b""")
    private val splitRegex = Regex("""[,،/|]+""")

    private const val OTHER_GENRE = "أخرى / غير مصنف"
    private const val UNKNOWN_YEAR = "سنة غير معروفة"
    private const val MAX_CAST_PER_ITEM = 5
    private const val MAX_TOP_ACTORS = 30

    private val genreAliases: Map<String, String> = mapOf(
        "action" to "أكشن",
        "أكشن" to "أكشن",
        "اكشن" to "أكشن",
        "حركة" to "أكشن",
        "action & adventure" to "أكشن",
        "drama" to "دراما",
        "دراما" to "دراما",
        "comedy" to "كوميديا",
        "كوميديا" to "كوميديا",
        "كوميدي" to "كوميديا",
        "fantasy" to "فانتازيا",
        "فانتازيا" to "فانتازيا",
        "خيال" to "فانتازيا",
        "sci-fi & fantasy" to "فانتازيا",
        "science fiction" to "خيال علمي",
        "sci-fi" to "خيال علمي",
        "خيال علمي" to "خيال علمي",
        "horror" to "رعب",
        "رعب" to "رعب",
        "thriller" to "إثارة وتشويق",
        "إثارة" to "إثارة وتشويق",
        "اثارة" to "إثارة وتشويق",
        "تشويق" to "إثارة وتشويق",
        "إثارة وتشويق" to "إثارة وتشويق",
        "romance" to "رومانسي",
        "رومانسي" to "رومانسي",
        "رومانسية" to "رومانسي",
        "adventure" to "مغامرات",
        "مغامرة" to "مغامرات",
        "مغامرات" to "مغامرات",
        "crime" to "جريمة",
        "جريمة" to "جريمة",
        "mystery" to "غموض",
        "غموض" to "غموض",
        "animation" to "رسوم متحركة",
        "رسوم متحركة" to "رسوم متحركة",
        "أنمي" to "رسوم متحركة",
        "انمي" to "رسوم متحركة",
        "كرتون" to "رسوم متحركة",
        "family" to "عائلي",
        "عائلي" to "عائلي",
        "أسري" to "عائلي",
        "documentary" to "وثائقي",
        "وثائقي" to "وثائقي",
        "history" to "تاريخي",
        "تاريخي" to "تاريخي",
        "تاريخ" to "تاريخي",
        "war" to "حرب",
        "حرب" to "حرب",
        "حربي" to "حرب",
        "war & politics" to "حرب",
        "music" to "موسيقي",
        "موسيقي" to "موسيقي",
        "موسيقى" to "موسيقي",
        "western" to "غربي",
        "غربي" to "غربي",
    )

    fun organize(
        allItems: List<Poster>,
        facetsByStreamId: Map<String, CatalogFacetRow>,
        grouping: VodCustomGrouping,
    ): CustomOrganizationResult {
        if (allItems.isEmpty()) {
            return CustomOrganizationResult(emptyList(), emptyMap(), emptyMap())
        }

        val sortedItems = allItems.sortedWith(
            compareByDescending<Poster> { item ->
                val f = facetsByStreamId[item.id]
                f?.rating?.toDoubleOrNull() ?: item.rating.toDoubleOrNull() ?: 0.0
            }.thenByDescending { item ->
                val f = facetsByStreamId[item.id]
                maxOf(f?.novaAdded ?: 0L, item.added)
            },
        )

        return when (grouping) {
            VodCustomGrouping.GENRE -> groupByGenre(sortedItems, facetsByStreamId)
            VodCustomGrouping.YEAR -> groupByYear(sortedItems, facetsByStreamId)
            VodCustomGrouping.CAST -> groupByCast(sortedItems, facetsByStreamId)
        }
    }

    private fun groupByGenre(
        sortedItems: List<Poster>,
        facets: Map<String, CatalogFacetRow>,
    ): CustomOrganizationResult {
        val buckets = LinkedHashMap<String, MutableList<Poster>>()
        for (poster in sortedItems) {
            val rawGenre = facets[poster.id]?.genre.orEmpty()
            val normalized = rawGenre
                .split(splitRegex)
                .mapNotNull { token -> normalizeGenre(token) }
                .distinct()
            if (normalized.isEmpty()) {
                buckets.getOrPut(OTHER_GENRE) { ArrayList() }.add(poster)
            } else {
                for (g in normalized) {
                    buckets.getOrPut(g) { ArrayList() }.add(poster)
                }
            }
        }
        val orderedKeys = buckets.keys.sortedWith(
            compareBy<String> { it == OTHER_GENRE }
                .thenByDescending { buckets[it]?.size ?: 0 },
        )
        return buildResult("genre", orderedKeys, buckets)
    }

    private fun groupByYear(
        sortedItems: List<Poster>,
        facets: Map<String, CatalogFacetRow>,
    ): CustomOrganizationResult {
        val buckets = LinkedHashMap<String, MutableList<Poster>>()
        for (poster in sortedItems) {
            val y = extractYear(facets[poster.id]?.year, poster.year, poster.name)
            val label = yearToBucketLabel(y)
            buckets.getOrPut(label) { ArrayList() }.add(poster)
        }
        val orderedKeys = buckets.keys.sortedBy { yearBucketSortOrder(it) }
        return buildResult("year", orderedKeys, buckets)
    }

    private fun groupByCast(
        sortedItems: List<Poster>,
        facets: Map<String, CatalogFacetRow>,
    ): CustomOrganizationResult {
        val buckets = LinkedHashMap<String, MutableList<Poster>>()
        for (poster in sortedItems) {
            val rawCast = facets[poster.id]?.cast.orEmpty()
            if (rawCast.isBlank()) continue
            val actors = rawCast
                .split(splitRegex)
                .map { it.trim() }
                .filter { it.length >= 2 }
                .distinct()
                .take(MAX_CAST_PER_ITEM)
            for (actor in actors) {
                buckets.getOrPut(actor) { ArrayList() }.add(poster)
            }
        }
        val orderedKeys = buckets.keys
            .sortedByDescending { buckets[it]?.size ?: 0 }
            .take(MAX_TOP_ACTORS)
        return buildResult("cast", orderedKeys, buckets)
    }

    private fun buildResult(
        prefix: String,
        orderedKeys: List<String>,
        buckets: Map<String, List<Poster>>,
    ): CustomOrganizationResult {
        val categories = ArrayList<Category>(orderedKeys.size)
        val itemsByCat = LinkedHashMap<String, List<Poster>>(orderedKeys.size)
        val counts = LinkedHashMap<String, Int>(orderedKeys.size)
        for (label in orderedKeys) {
            val list = buckets[label].orEmpty()
            if (list.isEmpty()) continue
            val catId = "$CUSTOM_CAT_PREFIX$prefix:$label"
            categories.add(Category(id = catId, name = label))
            itemsByCat[catId] = list
            counts[catId] = list.size
        }
        return CustomOrganizationResult(categories, itemsByCat, counts)
    }

    private fun normalizeGenre(raw: String): String? {
        val clean = raw.trim()
        if (clean.isEmpty()) return null
        val lower = clean.lowercase()
        return genreAliases[lower] ?: genreAliases[clean] ?: clean
    }

    fun primaryGenre(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val first = raw.split(',', '،', '/', '|', '&').firstOrNull()?.trim().orEmpty()
        if (first.isEmpty()) return ""
        return normalizeGenre(first)
    }

    fun extractTmdbYear(facetYear: String?): String {
        return facetYear?.let { yearRegex.find(it)?.value }.orEmpty()
    }

    private fun extractYear(facetYear: String?, posterYear: String, title: String): Int? {
        return facetYear?.let { yearRegex.find(it)?.value?.toIntOrNull() }
    }

    private fun yearToBucketLabel(year: Int?): String = when {
        year == null -> UNKNOWN_YEAR
        year >= 2020 -> year.toString()
        year >= 2010 -> "2010 - 2019"
        year >= 2000 -> "الألفينات (2000s)"
        year >= 1990 -> "التسعينات (1990s)"
        else -> "كلاسيكيات (قبل 1990)"
    }

    private fun yearBucketSortOrder(label: String): Int {
        val asInt = label.toIntOrNull()
        if (asInt != null) return 10_000 - asInt
        return when (label) {
            "2010 - 2019" -> 20_000
            "الألفينات (2000s)" -> 20_001
            "التسعينات (1990s)" -> 20_002
            "كلاسيكيات (قبل 1990)" -> 20_003
            else -> 30_000
        }
    }
}
