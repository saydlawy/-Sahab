package com.saydlawy.sahab.data.catalog

import com.saydlawy.sahab.data.TmdbApi
import com.saydlawy.sahab.data.TmdbMovie
import com.saydlawy.sahab.data.TmdbSearchResult
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/**
 * Port of the v5 Python matcher (tested on 200-sample Nova catalog).
 *
 * Pipeline:
 *   1. cleanIptv()          — strip IPTV noise (quality tags, junk, year suffix)
 *   2. normalizeArabic()    — unify alef/hamza/ya/ta-marbuta + Roman numerals
 *   3. extractTitleVariants() — split "AR | EN" mixed titles
 *   4. extractYear()        — trailing-year first (avoids 1917-style traps)
 *   5. generateQueries()    — cascade of 8-20 queries, strongest first
 *   6. scoreMatch()         — Jaro-Winkler + Jaccard + year + votes + popularity
 *   7. acceptLevel()        — strong / weak / reject
 */
object TmdbMatcher {

    data class Score(
        val titleSim: Double,
        val jw: Double,
        val jaccard: Double,
        val yearMatch: Double,
        val final: Double,
        val tmdbYear: Int?,
        val votes: Int,
    )

    data class EnrichResult(
        val tmdbId: Int?,
        val movie: TmdbMovie?,
        val score: Score?,
        val level: Level,
        val queryUsed: String,
        val tried: Int,
    )

    enum class Level { STRONG, WEAK, REJECT, EMPTY, NETWORK_ERROR }

    // ─────────────────────────────── Normalization ───────────────────────────────
    private val ARABIC_DIACRITICS = Regex("[\\u064B-\\u065F\\u0670]")
    private val ARABIC_PUNCT = Regex("[،؛؟!]")
    private val LATIN_PUNCT = Regex("[-:\\.'\\u2019\\u2018,\\\"\\u201C\\u201D\\[\\](){}/\\\\|+_*~`]")
    private val MULTI_SPACE = Regex("\\s+")

    private val ROMAN_MAP = listOf(
        Regex("\\bVIII\\b", RegexOption.IGNORE_CASE) to "8",
        Regex("\\bVII\\b", RegexOption.IGNORE_CASE) to "7",
        Regex("\\bVI\\b", RegexOption.IGNORE_CASE) to "6",
        Regex("\\bIV\\b", RegexOption.IGNORE_CASE) to "4",
        Regex("\\bV\\b", RegexOption.IGNORE_CASE) to "5",
        Regex("\\bIII\\b", RegexOption.IGNORE_CASE) to "3",
        Regex("\\bII\\b", RegexOption.IGNORE_CASE) to "2",
    )

    fun normalizeArabic(raw: String): String {
        if (raw.isBlank()) return ""
        var s = raw
        s = ARABIC_DIACRITICS.replace(s, "").replace("\u0640", "")
        s = Regex("[أإآٱ]").replace(s, "ا")
        s = s.replace("ؤ", "ء").replace("ئ", "ء").replace("ى", "ي").replace("ة", "ه")
        s = ARABIC_PUNCT.replace(s, " ")
        s = LATIN_PUNCT.replace(s, " ")
        for ((pat, repl) in ROMAN_MAP) s = pat.replace(s, repl)
        return MULTI_SPACE.replace(s, " ").trim()
    }

    // ─────────────────────────────── IPTV cleaning ───────────────────────────────
    private val PREFIX_TAG = Regex("^(?:\\s*[A-Z0-9]{2,8}\\s*[:|\\-]+\\s*)+", RegexOption.IGNORE_CASE)
    private val BRACKET_TAG = Regex("\\[[^\\]]*\\]|\\([^)]*(?:1080|720|480|4K|HD|CAM|WEB|BLU|مترجم|مدبلج)[^)]*\\)", RegexOption.IGNORE_CASE)
    private val BRACE_TAG = Regex("\\{[^}]*\\}")
    private val YEAR_PAREN = Regex("\\((19|20)\\d{2}\\)")
    private val YEAR_TRAIL = Regex("[\\s\\-]*\\b(19[2-9]\\d|20[0-3]\\d)\\b[\\s\\-]*$")

    private val QUALITY_TAG = Regex(
        "\\b(4K|UHD|FHD|QHD|HD|SD|2160p|1080p|720p|480p|360p|BluRay|Blu-?Ray|BDRip|BRRip|" +
            "WEB-?DL|WEB-?Rip|WEBRip|HDTV|DVDRip|DVDScr|HDRip|x264|x265|H\\.?264|H\\.?265|HEVC|AVC|" +
            "AAC|AC3|DTS|DD5\\.?1|Atmos|10bit|8bit|HDR|HDR10|DV|CAM|CamRip|TS|Telesync|TC|HDTS|HDCAM|SCR|" +
            "PROPER|REPACK|RERIP|EXTENDED|UNRATED|DIRECTORS\\.?CUT|REMASTERED|IMAX|3D|HC|KORSUB|" +
            "Dual\\.?Audio|Multi\\.?Audio|Subbed|Subtitled|Dubbed)\\b",
        RegexOption.IGNORE_CASE,
    )

    private val ARABIC_JUNK = Regex(
        "(?:^\\s*(?:مشاهدة|مشاهده|تحميل|فيلم|فلم|مسلسل|انمي|أنمي|كرتون)\\s+)|" +
            "(?<!\\S)(?:مترجم\\s*للعربية|مترجم\\s*عربي|مترجمة|مترجم|مدبلج\\s*للعربية|مدبلج\\s*عربي|مدبلج|" +
            "كامل\\s*بجودة|كامل|نسخة\\s*اصلية|نسحه\\s*اصليه|بدون\\s*حذف|بدون\\s*فواصل|حصريا|حصري|" +
            "بجودة\\s*عالية|اون\\s*لاين|أونلاين|اونلاين)(?!\\S)|" +
            "(?:\\s+(?:فيلم|فلم|Movie|Film)\\s*$)",
        RegexOption.IGNORE_CASE,
    )

    private val EDGE_SEPARATORS = Regex("^[\\s\\-:|/\\\\]+|[\\s\\-:|/\\\\]+$")

    fun cleanIptv(raw: String): String {
        if (raw.isBlank()) return ""
        var s = raw
        s = PREFIX_TAG.replace(s, " ")
        s = BRACKET_TAG.replace(s, " ")
        s = BRACE_TAG.replace(s, " ")
        s = YEAR_PAREN.replace(s, " ")
        s = QUALITY_TAG.replace(s, " ")
        s = ARABIC_JUNK.replace(s, " ")
        s = YEAR_TRAIL.replace(s, " ")
        s = EDGE_SEPARATORS.replace(s, "")
        return MULTI_SPACE.replace(s, " ").trim()
    }

    fun extractTitleVariants(cleaned: String): List<String> {
        val normFull = normalizeArabic(cleaned)
        if (normFull.isBlank()) return emptyList()
        val variants = mutableListOf(normFull)

        Regex("\\s+[-:|/]\\s+").split(cleaned).forEach { part ->
            val p = normalizeArabic(part)
            if (p.length >= 2 && p !in variants) variants.add(p)
        }

        val arWords = Regex("[\\u0600-\\u06FF0-9]+").findAll(normFull).map { it.value }.toList()
        val enWords = Regex("[a-zA-Z0-9]+").findAll(normFull).map { it.value }.toList()
        val hasAr = arWords.any { !it.all { c -> c.isDigit() } }
        val hasEn = enWords.any { !it.all { c -> c.isDigit() } }

        if (hasAr && hasEn) {
            val arStr = arWords.joinToString(" ").trim()
            val enStr = enWords.joinToString(" ").trim()
            if (arStr.length >= 2 && arStr !in variants) variants.add(arStr)
            if (enStr.length >= 2 && enStr !in variants) variants.add(enStr)
        }
        return variants
    }

    private val YEAR_REGEX = Regex("\\b(19[2-9]\\d|20[0-3]\\d)\\b")
    fun extractYear(name: String, categoryName: String): Int? {
        val matches = YEAR_REGEX.findAll(name).toList()
        if (matches.isNotEmpty()) {
            val endThreshold = maxOf(0, name.length - 25)
            val trailing = matches.filter { it.range.first >= endThreshold }
            if (trailing.isNotEmpty()) return trailing.last().value.toIntOrNull()
        }
        return YEAR_REGEX.find(categoryName)?.value?.toIntOrNull()
    }

    // ─────────────────────────────── Tokens ───────────────────────────────
    private val STOPWORDS = setOf(
        "ال", "في", "علي", "على", "من", "الي", "إلي", "عن", "لا", "ما", "هو", "هي",
        "ان", "أن", "إن", "قد", "كان", "كانت", "مع", "او", "أو", "ثم", "هذا", "هذه", "و",
        "the", "a", "an", "of", "in", "on", "at", "to", "for", "and", "or", "with", "by",
        "is", "are", "was", "were", "be", "been", "being", "this", "that", "these", "those",
    )

    private fun stripArticle(t: String): String {
        for (pre in listOf("وال", "بال", "كال")) {
            if (t.startsWith(pre) && t.length > 4) return t.substring(3)
        }
        for (pre in listOf("ال", "لل")) {
            if (t.startsWith(pre) && t.length > 3) return t.substring(2)
        }
        return t
    }

    fun tokenize(s: String): List<String> {
        val norm = normalizeArabic(s.lowercase(Locale.ROOT))
        val out = mutableListOf<String>()
        for (t in Regex("\\W+").split(norm)) {
            if (t.length < 2) continue
            val stripped = stripArticle(t)
            if (stripped in STOPWORDS || stripped.length < 2) continue
            out.add(stripped)
        }
        return out
    }

    fun jaccardTokens(a: String, b: String): Double {
        val ta = tokenize(a).toSet()
        val tb = tokenize(b).toSet()
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        val inter = ta.intersect(tb).size.toDouble()
        val union = ta.union(tb).size.toDouble()
        return if (union == 0.0) 0.0 else inter / union
    }

    // ─────────────────────────────── Jaro-Winkler ───────────────────────────────
    private fun jaro(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        val md = maxOf(s1.length, s2.length) / 2 - 1
        val m1 = BooleanArray(s1.length)
        val m2 = BooleanArray(s2.length)
        var matches = 0
        for (i in s1.indices) {
            val start = maxOf(0, i - md)
            val end = minOf(i + md + 1, s2.length)
            for (j in start until end) {
                if (m2[j] || s1[i] != s2[j]) continue
                m1[i] = true; m2[j] = true; matches++; break
            }
        }
        if (matches == 0) return 0.0
        var t = 0.0
        var k = 0
        for (i in s1.indices) {
            if (!m1[i]) continue
            while (!m2[k]) k++
            if (s1[i] != s2[k]) t += 1
            k++
        }
        t /= 2
        return (matches.toDouble() / s1.length + matches.toDouble() / s2.length +
            (matches - t) / matches) / 3
    }

    fun jaroWinkler(s1: String, s2: String): Double {
        val a = s1.lowercase(Locale.ROOT)
        val b = s2.lowercase(Locale.ROOT)
        val j = jaro(a, b)
        var p = 0
        for (i in 0 until minOf(4, a.length, b.length)) {
            if (a[i] == b[i]) p++ else break
        }
        return j + p * 0.1 * (1.0 - j)
    }

    fun compareTitle(aRaw: String, bRaw: String): Double {
        var a = aRaw.lowercase(Locale.ROOT).trim()
        var b = bRaw.lowercase(Locale.ROOT).trim()
        for (pre in listOf("the ", "a ", "an ", "ال")) {
            if (a.startsWith(pre) && a.length > pre.length + 2) a = a.substring(pre.length).trim()
            if (b.startsWith(pre) && b.length > pre.length + 2) b = b.substring(pre.length).trim()
        }
        var jw = jaroWinkler(a, b)
        val (sh, lo) = if (a.length <= b.length) a to b else b to a
        if (sh.length >= 8 && lo.startsWith(sh)) {
            jw = min(jw + min(sh.length.toDouble() / lo.length, 1.0) * 0.10, 1.0)
        }
        return jw
    }

    // ─────────────────────────────── Score ───────────────────────────────
    private fun shouldPreferArabic(rawName: String, categoryName: String): Boolean {
        val cat = categoryName.lowercase()
        val arabicCatHints = listOf("عربي", "عربى", "مصري", "مصرى", "خليجي", "سوري", "لبناني", "شامي", "رمضان", "arabic", "egypt")
        if (arabicCatHints.any { cat.contains(it) }) return true
        val foreignCatHints = listOf("أجنبي", "اجنبي", "foreign", "english", "انجليزي", "هندي", "indian", "تركي", "turkish", "كوري", "korean", "فرنسي", "french", "اسباني", "إسباني", "spanish", "اسيوي", "آسيوي", "انمي", "أنمي", "anime", "مدبلج", "مترجم")
        if (foreignCatHints.any { cat.contains(it) }) return false
        return rawName.any { it in '\u0600'..'\u06FF' }
    }

    fun scoreMatch(variants: List<String>, novaYear: Int?, tmdb: TmdbSearchResult, preferArabic: Boolean = false): Score {
        val tt = normalizeArabic(tmdb.title)
        val to = normalizeArabic(tmdb.originalTitle)
        val tmdbYear = tmdb.releaseDate.take(4).toIntOrNull()
        val votes = tmdb.voteCount
        val pop = tmdb.popularity

        var bestJw = 0.0
        var bestJac = 0.0
        for (v in variants) {
            if (v.isEmpty()) continue
            bestJw = maxOf(bestJw, compareTitle(v, tt), compareTitle(v, to))
            bestJac = maxOf(bestJac, jaccardTokens(v, tt), jaccardTokens(v, to))
        }
        val titleSim = maxOf(bestJw, bestJac * 0.92)

        val (ym, final) = if (novaYear != null && tmdbYear != null) {
            val d = abs(novaYear - tmdbYear)
            val ym = when (d) {
                0 -> 1.0
                1 -> 0.85
                2 -> 0.25
                else -> 0.0
            }
            val vt = when {
                votes >= 1000 -> 1.0; votes >= 500 -> 0.85; votes >= 100 -> 0.65
                votes >= 50 -> 0.45; votes >= 10 -> 0.25; else -> 0.10
            }
            ym to (titleSim * 0.50 + ym * 0.25 + vt * 0.20 + min(pop / 100, 1.0) * 0.05)
        } else {
            val vt = when {
                votes >= 1000 -> 1.0; votes >= 500 -> 0.85; votes >= 100 -> 0.65
                votes >= 50 -> 0.45; votes >= 10 -> 0.25; else -> 0.05
            }
            0.5 to (titleSim * 0.60 + vt * 0.35 + min(pop / 100, 1.0) * 0.05)
        }

        val langAdjusted = if (preferArabic) {
            if (tmdb.originalLanguage.equals("ar", ignoreCase = true)) {
                (final + 0.25).coerceAtMost(1.0)
            } else {
                (final - 0.18).coerceAtLeast(0.0)
            }
        } else {
            final
        }

        return Score(
            titleSim = round3(titleSim),
            jw = round3(bestJw),
            jaccard = round3(bestJac),
            yearMatch = ym,
            final = round3(langAdjusted),
            tmdbYear = tmdbYear,
            votes = votes,
        )
    }

    fun acceptLevel(m: Score): Level {
        if (m.titleSim >= 0.98 && m.yearMatch == 1.0) return Level.STRONG
        if (m.titleSim >= 0.98 && m.yearMatch == 0.5 && m.votes >= 20) return Level.STRONG
        if (m.titleSim >= 0.90 && m.yearMatch >= 0.85 && m.votes >= 10) return Level.STRONG
        if (m.final >= 0.70 && m.titleSim >= 0.85 && m.yearMatch >= 0.85 && m.votes >= 5) return Level.STRONG
        if (m.titleSim >= 0.99 && m.yearMatch == 0.5 && m.votes >= 5) return Level.STRONG
        if (m.titleSim >= 0.90 && m.yearMatch == 0.5 && m.votes >= 50) return Level.STRONG
        if (m.titleSim >= 0.95 && m.yearMatch >= 0.85 && m.votes >= 1) return Level.STRONG
        if (m.titleSim >= 0.96 && m.votes >= 1) return Level.WEAK
        if (m.final >= 0.60 && m.titleSim >= 0.80) return Level.WEAK
        return Level.REJECT
    }

    private fun round3(v: Double): Double = Math.round(v * 1000.0) / 1000.0

    // ─────────────────────────────── Query cascade ───────────────────────────────
    data class Query(val q: String, val year: Int?, val lang: String)

    fun generateQueries(rawName: String, categoryName: String, year: Int?): Pair<List<Query>, List<String>> {
        val base = cleanIptv(rawName)
        if (base.isBlank()) return emptyList<Query>() to emptyList()
        val variants = extractTitleVariants(base)
        if (variants.isEmpty()) return emptyList<Query>() to emptyList()

        val queries = mutableListOf<Query>()
        fun add(q: String, yr: Int?, lang: String) {
            val qn = normalizeArabic(q)
            if (qn.length < 2) return
            if (queries.any { it.q == qn && it.year == yr && it.lang == lang }) return
            queries.add(Query(qn, yr, lang))
        }

        for (v in variants) {
            val isAr = Regex("[\\u0600-\\u06FF]").containsMatchIn(v)
            if (isAr) {
                add(v, year, "ar"); add(v, null, "ar")
                add(v, year, "en-US"); add(v, null, "en-US")
            } else {
                add(v, year, "en-US"); add(v, null, "en-US")
            }
        }

        for (v in variants) {
            val words = v.split(" ").filter { it.isNotEmpty() }
            val wordsSig = words.filter { it.length > 1 && it.lowercase(Locale.ROOT) !in STOPWORDS }
            val sig = wordsSig.joinToString(" ")
            val lang = if (Regex("[\\u0600-\\u06FF]").containsMatchIn(v)) "ar" else "en-US"
            if (sig.isNotEmpty() && sig != v) {
                add(sig, year, lang); add(sig, null, lang)
            }
            if (wordsSig.size >= 4) {
                add(wordsSig.take(3).joinToString(" "), year, lang)
                add(wordsSig.take(3).joinToString(" "), null, lang)
            }
            if (wordsSig.size >= 3) {
                add(wordsSig.take(2).joinToString(" "), year, lang)
                add(wordsSig.take(2).joinToString(" "), null, lang)
            }
            if (wordsSig.size >= 2 && wordsSig[0].length >= 4) {
                add(wordsSig[0], year, lang); add(wordsSig[0], null, lang)
            }
        }
        return queries to variants
    }

    // ─────────────────────────────── Enrich entry point ───────────────────────────────
    suspend fun enrich(
        tmdb: TmdbApi,
        rawName: String,
        categoryName: String,
        novaYear: Int?,
        maxQueries: Int = 4,
        mediaType: String = "movie",
    ): EnrichResult {
        val isTv = mediaType.equals("series", ignoreCase = true) || mediaType.equals("tv", ignoreCase = true)
        val (queries, variants) = generateQueries(rawName, categoryName, novaYear)
        if (queries.isEmpty()) {
            return EnrichResult(null, null, null, Level.EMPTY, "", 0)
        }

        var bestWeak: Pair<TmdbSearchResult, Score>? = null
        var tried = 0
        var sawNetworkError = false

        for (q in queries.take(maxQueries)) {
            tried++
            val searchOutcome = if (isTv) {
                tmdb.searchTvOutcome(q.q, q.year, q.lang)
            } else {
                tmdb.searchMovieOutcome(q.q, q.year, q.lang)
            }
            val results = when (val out = searchOutcome) {
                is TmdbApi.SearchOutcome.Ok -> out.results
                TmdbApi.SearchOutcome.MissingKey -> {
                    return EnrichResult(null, null, null, Level.NETWORK_ERROR, q.q, tried)
                }
                TmdbApi.SearchOutcome.NetworkError -> {
                    sawNetworkError = true
                    continue
                }
            }
            if (results.isEmpty()) continue

            val preferArabic = shouldPreferArabic(rawName, categoryName)
            var bestStrong: Pair<TmdbSearchResult, Score>? = null
            for (c in results.take(5)) {
                val s = scoreMatch(variants, novaYear, c, preferArabic)
                when (acceptLevel(s)) {
                    Level.STRONG -> {
                        if (bestStrong == null || s.final > bestStrong!!.second.final) {
                            bestStrong = c to s
                        }
                    }
                    Level.WEAK -> {
                        if (bestWeak == null || s.final > bestWeak!!.second.final) {
                            bestWeak = c to s
                        }
                    }
                    else -> Unit
                }
            }
            if (bestStrong != null) {
                val (c, s) = bestStrong!!
                val details = if (isTv) tmdb.getTvDetailsById(c.id) else tmdb.getMovieDetailsById(c.id)
                return EnrichResult(c.id, details, s, Level.STRONG, q.q, tried)
            }
        }

        if (bestWeak != null) {
            val (c, s) = bestWeak!!
            val details = if (isTv) tmdb.getTvDetailsById(c.id) else tmdb.getMovieDetailsById(c.id)
            return EnrichResult(c.id, details, s, Level.WEAK, "", tried)
        }
        val finalLevel = if (sawNetworkError) Level.NETWORK_ERROR else Level.REJECT
        return EnrichResult(null, null, null, finalLevel, "", tried)
    }
}
