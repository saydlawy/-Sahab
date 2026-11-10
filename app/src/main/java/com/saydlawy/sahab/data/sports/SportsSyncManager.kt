package com.saydlawy.sahab.data.sports

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class SportsSyncManager(
    private val dao: MatchDao,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val TAG = "SportsSync"
    private val apiFootball = SportsApi(client)
    private val openFoot = OpenFootApi(client) // Assuming we'll create this
    private val worldCup26 = WorldCup26Api(client) // Assuming we'll create this
    private val tsdb = TheSportsDbApi(client)
    private val morphTv = MorphTvApi(client)

    // دوال مساعدة للتواريخ
    private fun getDate(daysFromNow: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, daysFromNow)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }
    private fun getDateCompact(daysFromNow: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, daysFromNow)
        return SimpleDateFormat("yyyyMMdd", Locale.US).format(cal.time)
    }

    /**
     * المزامنة الشهرية الذكية (Smart Monthly Sync)
     * تعمل في الخلفية وتوفر استهلاك الـ API.
     */
    suspend fun runMonthlySync(openFootKey: String, highlightlyKey: String) {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "بدء المزامنة الشهرية...")
            val fromDate = getDate(0)
            val toDate = getDate(30)

            // 1. تحديث جداول الترتيب (مرة واحدة شهرياً)
            syncStandings()

            // 2. مزامنة البطولات (طبقات دقيقة)
            syncLeague(233, "الدوري المصري", fromDate, toDate, openFootKey, highlightlyKey, "comp_egyptian_prem_egy")
            syncLeague(307, "دوري روشن", fromDate, toDate, openFootKey, highlightlyKey, "comp_saudi_pro_league_sau")
            syncLeague(39, "الدوري الإنجليزي", fromDate, toDate, openFootKey, highlightlyKey, null, "eng.1")
            syncLeague(140, "الدوري الإسباني", fromDate, toDate, openFootKey, highlightlyKey, null, "esp.1")
            syncLeague(2, "دوري أبطال أوروبا", fromDate, toDate, openFootKey, highlightlyKey, "comp_uefa_champions_league")
            
            Log.d(TAG, "اكتملت المزامنة الشهرية بنجاح.")
        }
    }

    private suspend fun syncLeague(
        leagueId: Int, 
        leagueName: String, 
        from: String, 
        to: String, 
        openFootKey: String, 
        highlightlyKey: String,
        openFootCompId: String? = null,
        worldCupSlug: String? = null
    ) {
        // الطبقة 0: فحص قاعدة البيانات المحلية (إذا كان لدينا أكثر من 5 مباريات قادمة، لا داعي للطلب)
        val localCount = dao.getAllMatches().count { 
            it.leagueId == leagueId && it.date >= from && it.date <= to 
        }
        if (localCount >= 5) {
            Log.d(TAG, "[$leagueName] البيانات موجودة محلياً ($localCount مباراة). تخطي الطلب.")
            return
        }

        Log.d(TAG, "[$leagueName] البيانات ناقصة، بدء جلبها من الطبقات...")
        var fetchedMatches: List<Match> = emptyList()

        // الطبقة 1: المصادر المجانية (worldcup26.ir)
        if (worldCupSlug != null) {
            val res = worldCup26.getFixtures(worldCupSlug, getDateCompact(0), getDateCompact(30))
            if (res is ApiResult.Success && res.data.isNotEmpty()) {
                fetchedMatches = res.data
                Log.d(TAG, "[$leagueName] تم الجلب من worldcup26 (${fetchedMatches.size})")
            }
        }

        // الطبقة 2: OpenFootAPI
        if (fetchedMatches.isEmpty() && openFootCompId != null && openFootKey.isNotBlank()) {
            val res = openFoot.getMatches(openFootKey, openFootCompId, from, to)
            if (res is ApiResult.Success && res.data.isNotEmpty()) {
                fetchedMatches = res.data
                Log.d(TAG, "[$leagueName] تم الجلب من OpenFootAPI (${fetchedMatches.size})")
            }
        }

        // الطبقة 3: Highlightly (للدوري المصري فقط كخط أخير)
        if (fetchedMatches.isEmpty() && leagueId == 233 && highlightlyKey.isNotBlank()) {
            val res = HighlightlyApi(client).getMatches(highlightlyKey, "EG", from, to)
            if (res is ApiResult.Success && res.data.isNotEmpty()) {
                fetchedMatches = res.data
                Log.d(TAG, "[$leagueName] تم الجلب من Highlightly (${fetchedMatches.size})")
            }
        }

        // الحفظ في قاعدة البيانات
        if (fetchedMatches.isNotEmpty()) {
            dao.insertMatches(fetchedMatches.map { it.toEntity() })
            Log.d(TAG, "[$leagueName] تم حفظ ${fetchedMatches.size} مباراة جديدة.")
        } else {
            Log.w(TAG, "[$leagueName] لم يتم العثور على بيانات جديدة.")
        }
    }

    private suspend fun syncStandings() {
        // فحص تاريخ آخر تحديث للترتيب (يمكن تنفيذه عبر SharedPreferences في التطبيق)
        // هنا سنقوم بتحديث الترتيب للدوريات الرئيسية مباشرة
        val leaguesToUpdate = mapOf(
            "4829" to 233, // مصري
            "4668" to 307, // سعودي
            "4328" to 39,  // إنجليزي
            "4335" to 140  // إسباني
        )
        for ((tsdbId, appId) in leaguesToUpdate) {
            val res = tsdb.getStandings(tsdbId, "2026-2027")
            if (res is ApiResult.Success) {
                // حفظ في قاعدة البيانات... (يحتاج لتعديل Dao)
                Log.d(TAG, "تم تحديث ترتيب البطولة $appId")
            }
        }
    }
}
