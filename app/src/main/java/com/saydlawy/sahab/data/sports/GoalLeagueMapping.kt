package com.saydlawy.sahab.data.sports

data class GoalLeagueSpec(
    val leagueId: Int,
    val nanoid: String,
    val arabicName: String,
    val country: String,
)

/**
 * Single source of truth mapping internal app league IDs (1..452) to Goal API NanoIDs
 * documented in LEAGUES_REFERENCE.md.
 */
object GoalLeagueMapping {
    val ALL: List<GoalLeagueSpec> = listOf(
        GoalLeagueSpec(233, "cmr77dwd000irrx06go0ax033", "الدوري المصري الممتاز", "Egypt"),
        GoalLeagueSpec(307, "cmr77dvw600b9rx06euixf818", "دوري روشن السعودي", "Saudi Arabia"),
        GoalLeagueSpec(12, "cmr77dvhg0016rx06xwyoefu1", "دوري أبطال أفريقيا", "Africa"),
        GoalLeagueSpec(20, "cmr77dvhg0017rx06rkze534u", "كأس الكونفدرالية الأفريقية", "Africa"),
        GoalLeagueSpec(17, "cmr77dvhf0014rx06p9obts02", "دوري أبطال آسيا للنخبة", "Asia"),
        GoalLeagueSpec(2, "cmr77dw3900f5rx06j05wgzv4", "دوري أبطال أوروبا", "Europe"),
        GoalLeagueSpec(3, "cmr77dw3900f6rx06tuqwft2d", "الدوري الأوروبي", "Europe"),
        GoalLeagueSpec(6, "cmr77dvhf0015rx069i57rsbo", "كأس الأمم الأفريقية", "Africa"),
        GoalLeagueSpec(32, "cmr77dvhg001srx060axumqos", "تصفيات كأس الأمم الأفريقية", "Africa"),
        GoalLeagueSpec(403, "cmr77dw4800ferx06u57oohrh", "كأس الأمم الأوروبية", "Europe"),
        GoalLeagueSpec(8, "cmr77dvhg001drx06ru2azvov", "كأس آسيا", "Asia"),
        GoalLeagueSpec(1, "cmr77dw4s00fmrx069yvhf8ce", "كأس العالم", "World"),
        GoalLeagueSpec(452, "cmr77dvhg0022rx06ee0umcpy", "كأس العرب", "Arab"),
        GoalLeagueSpec(39, "cmr77dvkr005nrx06lp7rvp49", "الدوري الإنجليزي الممتاز", "England"),
        GoalLeagueSpec(140, "cmr77dvnt006nrx063v3w622e", "الدوري الإسباني", "Spain"),
        GoalLeagueSpec(135, "cmr77dvpd006yrx06zig7907g", "الدوري الإيطالي", "Italy"),
        GoalLeagueSpec(78, "cmr77dvgm0002rx06rt2uqxii", "الدوري الألماني", "Germany"),
        GoalLeagueSpec(61, "cmr77dvqg007crx06q1kaceyo", "الدوري الفرنسي", "France"),
        GoalLeagueSpec(203, "cmr77dw0q00eprx06rqew3m48", "الدوري التركي", "Turkey"),
    )

    private val byLeagueId: Map<Int, GoalLeagueSpec> = ALL.associateBy { it.leagueId }

    fun findByLeagueId(leagueId: Int): GoalLeagueSpec? = byLeagueId[leagueId]
}
