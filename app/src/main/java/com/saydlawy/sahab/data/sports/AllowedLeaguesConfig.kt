package com.saydlawy.sahab.data.sports

object AllowedLeaguesConfig {
    val ALLOWED_LEAGUES: Set<Int> = linkedSetOf(
        233, 307, // الدوريات المحلية
        12, 17, 20, 2, 3, // الدولية للأندية
        6, 403, 8, 1, 452, // الدولية للمنتخبات
        39, 140, 135, 78, 61, 203 // الأوروبية الكبرى
    )


    val ESPN_SLUG_TO_ID: Map<String, Int> = mapOf(
        "egy.1" to 233, "ksa.1" to 307,
        "caf.champions" to 12, "afc.champions" to 17, "caf.confed" to 20,
        "uefa.champions" to 2, "uefa.europa" to 3,
        "caf.nations" to 6, "uefa.euro" to 403, "afc.cup" to 8,
        "fifa.world" to 1, "fifa.arab.cup" to 452,
        "eng.1" to 39, "esp.1" to 140, "ita.1" to 135,
        "ger.1" to 78, "fra.1" to 61, "tur.1" to 203
    )

    val FOOTBALL_DATA_ORG_TO_ID: Map<String, Int> = mapOf(
        "WC" to 1, "CL" to 2, "EC" to 403, "PL" to 39,
        "PD" to 140, "SA" to 135, "BL1" to 78, "FL1" to 61
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

    fun getLeagueSortPriority(leagueId: Int): Int {
        val index = ALLOWED_LEAGUES.indexOf(leagueId)
        return if (index != -1) index else Int.MAX_VALUE
    }
}
