package com.saydlawy.sahab.util

import androidx.compose.ui.graphics.Color
import java.util.Locale

/**
 * Resolves high-availability internet channel logos for Arabic & International IPTV channels
 * whenever the Xtream server provides an empty, broken, or unreachable `stream_icon`.
 */
object ChannelLogoResolver {

    private const val TV_LOGOS_BASE = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries"
    private const val WIKI_COMMONS = "https://upload.wikimedia.org/wikipedia/commons"

    data class ChannelBrandInfo(
        val logoUrl: String,
        val accentColor: Color,
        val badgeLabel: String,
    )

    /**
     * Returns the best primary URL to try first (either the server icon if valid HTTP/HTTPS,
     * or the resolved internet logo URL if the server icon is blank).
     */
    fun resolvePrimaryUrl(rawIcon: String, channelName: String): String {
        val trimmed = rawIcon.trim()
        if (trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true)
        ) {
            return trimmed
        }
        return resolveInternetLogoUrl(channelName).orEmpty()
    }

    /**
     * Returns a reliable internet fallback logo URL based on the channel's Arabic or English name.
     * Used both when `channel.icon` is blank and when `channel.icon` fails to load from the IPTV server.
     */
    fun resolveInternetLogoUrl(channelName: String): String? {
        val n = normalizeChannelName(channelName)
        if (n.isBlank()) return null

        return when {
            // --- beIN SPORTS ---
            n.contains("bein") && (n.contains("news") || n.contains("اخبار") || n.contains("إخبار")) ->
                "$TV_LOGOS_BASE/middle-east/bein-sports-news-me.png"
            n.contains("bein") && n.contains("max") ->
                "$TV_LOGOS_BASE/france/bein-sports-max-4-fr.png"
            n.contains("bein") && n.contains("premium 1") ->
                "$TV_LOGOS_BASE/middle-east/bein-sports-1-premium-me.png"
            n.contains("bein") && n.contains("premium 2") ->
                "$TV_LOGOS_BASE/middle-east/bein-sports-2-premium-me.png"
            n.contains("bein") && n.contains("premium 3") ->
                "$TV_LOGOS_BASE/middle-east/bein-sports-3-premium-me.png"
            (n.contains("bein") || n.contains("بي ان") || n.contains("بين سبورت")) && n.contains("2") ->
                "$TV_LOGOS_BASE/middle-east/bein-sports-2-me.png"
            (n.contains("bein") || n.contains("بي ان") || n.contains("بين سبورت")) && n.contains("3") ->
                "$TV_LOGOS_BASE/middle-east/bein-sports-3-me.png"
            (n.contains("bein") || n.contains("بي ان") || n.contains("بين سبورت")) && n.contains("4") ->
                "$TV_LOGOS_BASE/middle-east/bein-sports-4-me.png"
            (n.contains("bein") || n.contains("بي ان") || n.contains("بين سبورت")) && n.contains("5") ->
                "$TV_LOGOS_BASE/middle-east/bein-sports-5-me.png"
            (n.contains("bein") || n.contains("بي ان") || n.contains("بين سبورت")) && n.contains("6") ->
                "$TV_LOGOS_BASE/middle-east/bein-sports-6-me.png"
            (n.contains("bein") || n.contains("بي ان") || n.contains("بين سبورت")) && n.contains("7") ->
                "$TV_LOGOS_BASE/middle-east/bein-sports-7-me.png"
            n.contains("bein") || n.contains("بي ان") || n.contains("بين سبورت") ->
                "$TV_LOGOS_BASE/middle-east/bein-sports-1-me.png"

            // --- SSC & ARAB SPORTS ---
            n.contains("ssc") || n.contains("اس اس سي") ->
                "$WIKI_COMMONS/thumb/c/c8/SSC_Sports_logo.svg/512px-SSC_Sports_logo.svg.png"
            n.contains("alkass") || n.contains("al kass") || n.contains("الكاس") || n.contains("الكأس") ->
                "$TV_LOGOS_BASE/qatar/alkass-one-qa.png"
            n.contains("ad sport") || n.contains("abu dhabi sport") || n.contains("ابوظبي الرياض") || n.contains("أبوظبي الرياض") ->
                "$TV_LOGOS_BASE/united-arab-emirates/abu-dhabi-sports-1-ae.png"
            n.contains("dubai sport") || n.contains("دبي الرياض") ->
                "$TV_LOGOS_BASE/united-arab-emirates/dubai-sports-1-ae.png"
            n.contains("ontime") || n.contains("on time") || n.contains("اون تايم") || n.contains("أون تايم") ->
                "$WIKI_COMMONS/thumb/9/9e/OnTime_Sports_logo.png/512px-OnTime_Sports_logo.png"
            n.contains("ksa sport") || n.contains("السعودية الرياض") || n.contains("الرياضية السعود") ->
                "$WIKI_COMMONS/thumb/4/4b/KSA_Sports_logo.svg/512px-KSA_Sports_logo.svg.png"
            n.contains("arryadia") || n.contains("المغربية الرياض") ->
                "$TV_LOGOS_BASE/morocco/arryadia-ma.png"
            n.contains("al ahly") || n.contains("الاهلي") || n.contains("الأهلي") ->
                "$WIKI_COMMONS/thumb/a/a1/Al_Ahly_SC_logo.svg/512px-Al_Ahly_SC_logo.svg.png"
            n.contains("zamalek") || n.contains("الزمالك") ->
                "$WIKI_COMMONS/thumb/0/06/Zamalek_SC_logo.svg/512px-Zamalek_SC_logo.svg.png"

            // --- MBC GROUP ---
            (n.contains("mbc") || n.contains("ام بي سي") || n.contains("إم بي سي")) && (n.contains("masr 2") || n.contains("مصر 2")) ->
                "$TV_LOGOS_BASE/egypt/mbc-masr-2-eg.png"
            (n.contains("mbc") || n.contains("ام بي سي") || n.contains("إم بي سي")) && (n.contains("masr") || n.contains("مصر")) ->
                "$TV_LOGOS_BASE/egypt/mbc-masr-eg.png"
            (n.contains("mbc") || n.contains("ام بي سي") || n.contains("إم بي سي")) && (n.contains("action") || n.contains("اكشن") || n.contains("أكشن")) ->
                "$TV_LOGOS_BASE/middle-east/mbc-action-me.png"
            (n.contains("mbc") || n.contains("ام بي سي") || n.contains("إم بي سي")) && (n.contains("max") || n.contains("ماكس")) ->
                "$TV_LOGOS_BASE/middle-east/mbc-max-me.png"
            (n.contains("mbc") || n.contains("ام بي سي") || n.contains("إم بي سي")) && (n.contains("drama") || n.contains("دراما")) ->
                "$TV_LOGOS_BASE/middle-east/mbc-drama-me.png"
            (n.contains("mbc") || n.contains("ام بي سي") || n.contains("إم بي سي")) && (n.contains("bollywood") || n.contains("بوليود") || n.contains("بوليوود")) ->
                "$TV_LOGOS_BASE/middle-east/mbc-bollywood-me.png"
            (n.contains("mbc") || n.contains("ام بي سي") || n.contains("إم بي سي")) && (n.contains("iraq") || n.contains("العراق") || n.contains("عراق")) ->
                "$TV_LOGOS_BASE/iraq/mbc-iraq-iq.png"
            (n.contains("mbc") || n.contains("ام بي سي") || n.contains("إم بي سي")) && n.contains("2") ->
                "$TV_LOGOS_BASE/middle-east/mbc-2-me.png"
            (n.contains("mbc") || n.contains("ام بي سي") || n.contains("إم بي سي")) && n.contains("3") ->
                "$TV_LOGOS_BASE/middle-east/mbc-3-me.png"
            (n.contains("mbc") || n.contains("ام بي سي") || n.contains("إم بي سي")) && n.contains("4") ->
                "$TV_LOGOS_BASE/middle-east/mbc-4-me.png"
            n.contains("mbc") || n.contains("ام بي سي") || n.contains("إم بي سي") || n.contains("wanasah") || n.contains("وناسة") ->
                "$TV_LOGOS_BASE/middle-east/mbc-1-me.png"

            // --- ROTANA GROUP ---
            (n.contains("rotana") || n.contains("روتانا")) && (n.contains("cinema") || n.contains("سينما")) ->
                "$TV_LOGOS_BASE/middle-east/rotana-cinema-me.png"
            (n.contains("rotana") || n.contains("روتانا")) && (n.contains("khalij") || n.contains("خليج")) ->
                "$TV_LOGOS_BASE/middle-east/rotana-khalijia-me.png"
            (n.contains("rotana") || n.contains("روتانا")) && (n.contains("comedy") || n.contains("كوميد")) ->
                "$TV_LOGOS_BASE/middle-east/rotana-comedy-me.png"
            (n.contains("rotana") || n.contains("روتانا")) && (n.contains("classic") || n.contains("كلاسيك")) ->
                "$TV_LOGOS_BASE/middle-east/rotana-classic-me.png"
            (n.contains("rotana") || n.contains("روتانا")) && (n.contains("drama") || n.contains("دراما")) ->
                "$TV_LOGOS_BASE/middle-east/rotana-drama-me.png"
            (n.contains("rotana") || n.contains("روتانا")) && (n.contains("clip") || n.contains("كليب") || n.contains("music") || n.contains("موسيق")) ->
                "$TV_LOGOS_BASE/middle-east/rotana-clip-me.png"
            n.contains("rotana") || n.contains("روتانا") ->
                "$TV_LOGOS_BASE/middle-east/rotana-cinema-me.png"

            // --- NEWS CHANNELS ---
            n.contains("jazeera") || n.contains("الجزيرة") || n.contains("الجزيره") ->
                "$TV_LOGOS_BASE/qatar/al-jazeera-arabic-qa.png"
            n.contains("hadath") || n.contains("الحدث") ->
                "$TV_LOGOS_BASE/united-arab-emirates/al-hadath-ae.png"
            n.contains("arabiya") || n.contains("العربية") || n.contains("العربيه") ->
                "$TV_LOGOS_BASE/united-arab-emirates/al-arabiya-ae.png"
            n.contains("sky news") || n.contains("سكاي نيوز") ->
                "$TV_LOGOS_BASE/united-arab-emirates/sky-news-arabia-ae.png"
            n.contains("asharq") || n.contains("الشرق") ->
                "$WIKI_COMMONS/thumb/5/59/Asharq_News_Logo.svg/512px-Asharq_News_Logo.svg.png"
            n.contains("bbc") || n.contains("بي بي سي") ->
                "$TV_LOGOS_BASE/united-kingdom/bbc-news-uk.png"
            n.contains("france 24") || n.contains("فرانس 24") ->
                "$TV_LOGOS_BASE/france/france-24-fr.png"
            n.contains("cnn") || n.contains("سي ان ان") ->
                "$TV_LOGOS_BASE/united-states/cnn-us.png"
            n.contains("alaraby") || n.contains("al araby") || n.contains("العربي") ->
                "$TV_LOGOS_BASE/qatar/al-araby-tv-qa.png"

            // --- EGYPTIAN CHANNELS ---
            n.contains("dmc") || n.contains("دي ام سي") ->
                "$TV_LOGOS_BASE/egypt/dmc-eg.png"
            n.contains("cbc") || n.contains("سي بي سي") ->
                "$TV_LOGOS_BASE/egypt/cbc-eg.png"
            n.contains("on drama") || n.contains("on e") || n.contains("اون دراما") || n.contains("أون دراما") || n.contains("قناة اون") ->
                "$TV_LOGOS_BASE/egypt/on-e-eg.png"
            n.contains("nahar") || n.contains("النهار") ->
                "$TV_LOGOS_BASE/egypt/al-nahar-eg.png"
            n.contains("hayat") || n.contains("الحياة") || n.contains("الحياه") ->
                "$TV_LOGOS_BASE/egypt/al-hayat-eg.png"
            n.contains("sada el balad") || n.contains("صدى البلد") ->
                "$TV_LOGOS_BASE/egypt/sada-el-balad-eg.png"
            n.contains("nile") || n.contains("نايل") || n.contains("النيل") ->
                "$TV_LOGOS_BASE/egypt/nile-drama-eg.png"

            // --- GULF & LEVANT CHANNELS ---
            n.contains("dubai") || n.contains("دبي") || n.contains("سما دبي") ->
                "$TV_LOGOS_BASE/united-arab-emirates/dubai-tv-ae.png"
            n.contains("abu dhabi") || n.contains("ابوظبي") || n.contains("أبوظبي") || n.contains("امارات") || n.contains("الإمارات") ->
                "$TV_LOGOS_BASE/united-arab-emirates/abu-dhabi-tv-ae.png"
            n.contains("sharjah") || n.contains("الشارقة") ->
                "$TV_LOGOS_BASE/united-arab-emirates/sharjah-tv-ae.png"
            n.contains("saudi") || n.contains("السعودية") || n.contains("القرآن") || n.contains("السنة") || n.contains("الاخبارية") || n.contains("الإخبارية") || n.contains("sbc") ->
                "$TV_LOGOS_BASE/saudi-arabia/saudi-tv-sa.png"
            n.contains("kuwait") || n.contains("الكويت") ->
                "$TV_LOGOS_BASE/kuwait/kuwait-tv-1-kw.png"
            n.contains("qatar") || n.contains("قطر") || n.contains("الريان") ->
                "$TV_LOGOS_BASE/qatar/qatar-tv-qa.png"
            n.contains("oman") || n.contains("عمان") ->
                "$TV_LOGOS_BASE/oman/oman-tv-general-om.png"
            n.contains("roya") || n.contains("رؤيا") ->
                "$TV_LOGOS_BASE/jordan/roya-tv-jo.png"
            n.contains("mamlaka") || n.contains("المملكة") ->
                "$TV_LOGOS_BASE/jordan/al-mamlaka-jo.png"
            n.contains("lbc") || n.contains("ال بي سي") ->
                "$TV_LOGOS_BASE/lebanon/lbci-lb.png"
            n.contains("mtv") && (n.contains("leb") || n.contains("لبنان")) ->
                "$TV_LOGOS_BASE/lebanon/mtv-lb.png"
            n.contains("jadeed") || n.contains("الجديد") ->
                "$TV_LOGOS_BASE/lebanon/al-jadeed-lb.png"
            n.contains("sharqiya") || n.contains("الشرقية") ->
                "$TV_LOGOS_BASE/iraq/al-sharqiya-iq.png"
            n.contains("iraqia") || n.contains("العراقية") ->
                "$TV_LOGOS_BASE/iraq/al-iraqiya-iq.png"
            n.contains("2m") || n.contains("دوزيم") ->
                "$TV_LOGOS_BASE/morocco/2m-monde-ma.png"
            n.contains("al aoula") || n.contains("الاولى المغرب") || n.contains("الأولى المغرب") ->
                "$TV_LOGOS_BASE/morocco/al-aoula-ma.png"

            // --- DOCUMENTARY, KIDS & OSN / SHAHID ---
            n.contains("nat geo") || n.contains("national geographic") || n.contains("ناشونال") ->
                "$TV_LOGOS_BASE/united-states/national-geographic-us.png"
            n.contains("discovery") || n.contains("ديسكفري") ->
                "$TV_LOGOS_BASE/united-states/discovery-channel-us.png"
            n.contains("spacetoon") || n.contains("سبيستون") || n.contains("سبيس تون") ->
                "$TV_LOGOS_BASE/united-arab-emirates/spacetoon-ae.png"
            n.contains("cartoon network") || n.contains("كرتون نتورك") || n.contains("cn arab") ->
                "$TV_LOGOS_BASE/united-states/cartoon-network-us.png"
            n.contains("majid") || n.contains("ماجد") ->
                "$TV_LOGOS_BASE/united-arab-emirates/majid-kids-tv-ae.png"
            n.contains("osn") || n.contains("او اس ان") || n.contains("أو إس إن") ->
                "$TV_LOGOS_BASE/middle-east/osn-movies-first-me.png"
            n.contains("shahid") || n.contains("شاهد") ->
                "$TV_LOGOS_BASE/middle-east/mbc-1-me.png"
            n.contains("netflix") || n.contains("نتفليكس") || n.contains("نتفلكس") ->
                "$WIKI_COMMONS/thumb/0/08/Netflix_2015_logo.svg/512px-Netflix_2015_logo.svg.png"

            else -> null
        }
    }

    /**
     * Returns a clean, human-readable short brand badge (and category color) for any channel
     * so even if offline or while the image loads, the badge looks like the reference design.
     */
    fun resolveBrandStyle(channelName: String): Pair<String, Color> {
        val n = normalizeChannelName(channelName)
        return when {
            n.contains("bein") || n.contains("بي ان") -> "beIN" to Color(0xFF5B21B6)
            n.contains("ssc") || n.contains("اس اس سي") -> "SSC" to Color(0xFF047857)
            n.contains("alkass") || n.contains("الكاس") || n.contains("الكأس") -> "الكأس" to Color(0xFF991B1B)
            n.contains("sport") || n.contains("رياض") -> "رياضية" to Color(0xFF6D28D9)
            n.contains("mbc") || n.contains("ام بي سي") -> "MBC" to Color(0xFF1D4ED8)
            n.contains("rotana") || n.contains("روتانا") -> "روتانا" to Color(0xFF059669)
            n.contains("jazeera") || n.contains("الجزيرة") -> "الجزيرة" to Color(0xFFD97706)
            n.contains("arabiya") || n.contains("العربية") || n.contains("الحدث") -> "العربية" to Color(0xFF7C3AED)
            n.contains("action") || n.contains("اكشن") || n.contains("أكشن") -> "أكشن" to Color(0xFFDC2626)
            n.contains("drama") || n.contains("دراما") -> "دراما" to Color(0xFF2563EB)
            n.contains("cinema") || n.contains("سينما") || n.contains("movie") || n.contains("افلام") || n.contains("أفلام") -> "سينما" to Color(0xFFB91C1C)
            n.contains("kids") || n.contains("اطفال") || n.contains("أطفال") || n.contains("spacetoon") || n.contains("سبيستون") -> "أطفال" to Color(0xFF0284C7)
            n.contains("news") || n.contains("اخبار") || n.contains("إخبار") -> "إخبارية" to Color(0xFF0F766E)
            else -> {
                val clean = channelName
                    .replace(Regex("""\[.*?]|\(.*?\)"""), "")
                    .replace(Regex("""\b(HD|FHD|4K|UHD|SD|AR|VIP|HEVC|RAW)\b""", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("""^[A-Z]{2,3}\s*[:|\-]\s*""", RegexOption.IGNORE_CASE), "")
                    .trim()
                clean.take(8).ifBlank { "مباشر" } to Color(0xFF1E3A8A)
            }
        }
    }

    private fun normalizeChannelName(raw: String): String {
        return raw.lowercase(Locale.ROOT)
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ة', 'ه')
            .replace('ى', 'ي')
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()
    }
}
