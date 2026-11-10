package com.saydlawy.sahab.data.sports

import java.util.zip.CRC32

/** Generates a deterministic positive 31-bit integer ID from any string key. */
fun String.stableId(): Int {
    val crc = CRC32()
    crc.update(this.toByteArray(Charsets.UTF_8))
    return (crc.value and 0x7FFFFFFFL).toInt()
}

object AllowedLeaguesConfig {
    val ALLOWED_LEAGUES: Set<Int> = linkedSetOf(
        233, 307, // الدوريات المحلية
        12, 17, 20, 2, 3, // الدولية للأندية
        6, 403, 8, 1, 452, // الدولية للمنتخبات
        39, 140, 135, 78, 61, 203 // الأوروبية الكبرى
    )

    val FOOTBALL_DATA_ORG_TO_ID: Map<String, Int> = mapOf(
        "WC" to 1, "CL" to 2, "EC" to 403, "PL" to 39,
        "PD" to 140, "SA" to 135, "BL1" to 78, "FL1" to 61
    )

    val THESPORTSDB_LEAGUE_ID: Map<Int, String> = mapOf(
        233 to "4829", // Egyptian Premier League
        307 to "4668", // Saudi Pro League
        12 to "4490",  // CAF Champions League
        17 to "4508",  // AFC Champions League
        20 to "4817",  // CAF Confederation Cup
        2 to "4480",   // UEFA Champions League
        3 to "4481",   // UEFA Europa League
        6 to "4497",   // AFCON
        403 to "4502", // Euro
        8 to "4509",   // Asian Cup
        1 to "4429",   // World Cup
        452 to "5128", // Arab Cup
        39 to "4328",  // EPL
        140 to "4335", // La Liga
        135 to "4332", // Serie A
        78 to "4331",  // Bundesliga
        61 to "4334",  // Ligue 1
        203 to "4339"  // Turkish Super Lig
    )

    fun translateLeagueName(id: Int, defaultName: String = "بطولة غير معروفة"): String {
        return when (id) {
            233 -> "الدوري المصري الممتاز"
            307 -> "دوري روشن السعودي"
            12 -> "دوري أبطال أفريقيا"
            17 -> "دوري أبطال آسيا للنخبة"
            20 -> "كأس الكونفدرالية الأفريقية"
            2 -> "دوري أبطال أوروبا"
            3 -> "الدوري الأوروبي"
            6 -> "كأس الأمم الأفريقية"
            403 -> "كأس الأمم الأوروبية"
            8 -> "كأس آسيا"
            1 -> "كأس العالم"
            452 -> "كأس العرب"
            39 -> "الدوري الإنجليزي الممتاز"
            140 -> "الدوري الإسباني"
            135 -> "الدوري الإيطالي"
            78 -> "الدوري الألماني"
            61 -> "الدوري الفرنسي"
            203 -> "الدوري التركي"
            else -> defaultName
        }
    }

    fun resolveLeagueIdByName(name: String?): Int? {
        if (name.isNullOrBlank()) return null
        return when {
            name.contains("المصري") || name.contains("Egyptian", ignoreCase = true) -> 233
            name.contains("السعودي") || name.contains("روشن") || name.contains("Saudi", ignoreCase = true) || name.contains("Roshn", ignoreCase = true) -> 307
            name.contains("أبطال أفريقيا") || name.contains("CAF Champions", ignoreCase = true) || name.contains("African Champions", ignoreCase = true) -> 12
            name.contains("أبطال آسيا") || name.contains("AFC Champions", ignoreCase = true) || name.contains("Asian Champions", ignoreCase = true) -> 17
            name.contains("الكونفدرالية") || name.contains("Confederation", ignoreCase = true) -> 20
            name.contains("أبطال أوروبا") || name.contains("UEFA Champions", ignoreCase = true) || (name.contains("Champions League", ignoreCase = true) && !name.contains("CAF", ignoreCase = true) && !name.contains("AFC", ignoreCase = true) && !name.contains("Asia", ignoreCase = true)) -> 2
            name.contains("الدوري الأوروبي") || name.contains("Europa League", ignoreCase = true) -> 3
            name.contains("أمم أفريقيا") || name.contains("الأمم الأفريقية") || name.contains("AFCON", ignoreCase = true) || name.contains("African Nations", ignoreCase = true) -> 6
            name.contains("أمم أوروبا") || name.contains("الأمم الأوروبية") || name.contains("Euro ", ignoreCase = true) || name.contains("European Championship", ignoreCase = true) -> 403
            name.contains("كأس آسيا") || name.contains("Asian Cup", ignoreCase = true) -> 8
            name.contains("كأس العالم") || (name.contains("World Cup", ignoreCase = true) && !name.contains("Club", ignoreCase = true)) -> 1
            name.contains("كأس العرب") || name.contains("Arab Cup", ignoreCase = true) -> 452
            name.contains("الإنجليزي") || name.contains("English Premier", ignoreCase = true) || name.equals("Premier League", ignoreCase = true) -> 39
            name.contains("الإسباني") || name.contains("La Liga", ignoreCase = true) || name.contains("LaLiga", ignoreCase = true) -> 140
            name.contains("الإيطالي") || name.contains("Serie A", ignoreCase = true) -> 135
            name.contains("الألماني") || name.contains("Bundesliga", ignoreCase = true) -> 78
            name.contains("الفرنسي") || name.contains("Ligue 1", ignoreCase = true) -> 61
            name.contains("التركي") || name.contains("Super Lig", ignoreCase = true) || name.contains("Turkish", ignoreCase = true) -> 203
            else -> null
        }
    }

    fun getLeagueSortPriority(leagueId: Int): Int {
        val index = ALLOWED_LEAGUES.indexOf(leagueId)
        return if (index != -1) index else Int.MAX_VALUE
    }
}

