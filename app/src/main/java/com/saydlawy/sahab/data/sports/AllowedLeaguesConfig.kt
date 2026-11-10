package com.saydlawy.sahab.data.sports

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

    fun getLeagueSortPriority(leagueId: Int): Int {
        val index = ALLOWED_LEAGUES.indexOf(leagueId)
        return if (index != -1) index else Int.MAX_VALUE
    }
}
