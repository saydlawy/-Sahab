package com.saydlawy.sahab.util

import com.saydlawy.sahab.data.Kind

/** Every piece of text the app shows. The app is Arabic only. */
object Ar {
    const val appName = "سحاب"
    const val tagline = "مشاهدتك، بكل هدوء"

    // Main sections
    const val live = "بث مباشر"
    const val liveSub = "القنوات ودليل البرامج"
    const val movies = "أفلام"
    const val moviesSub = "مكتبة الأفلام"
    const val series = "مسلسلات"
    const val seriesSub = "كل المواسم والحلقات"
    const val favorites = "المفضلة"
    const val favoritesSub = "ما أحببته"
    const val settings = "الإعدادات"
    const val continueWatching = "تابع المشاهدة"

    // General
    const val back = "رجوع"
    const val close = "إغلاق"
    const val cancel = "إلغاء"
    const val retry = "إعادة المحاولة"
    const val noName = "بدون اسم"
    const val show = "إظهار"
    const val hide = "إخفاء"

    // Login
    const val loginHint = "سجّل الدخول مرة واحدة فقط، وسيتذكّر التطبيق بياناتك"
    const val serverUrl = "رابط الخدمة، مثال: http://example.com:8080"
    const val username = "اسم المستخدم"
    const val password = "كلمة المرور"
    const val login = "دخول"

    // Lists and search
    const val searchChannels = "ابحث عن قناة"
    const val searchTitles = "ابحث بالاسم"
    const val noCategories = "لا توجد أقسام متاحة"
    const val noChannels = "لا توجد قنوات في هذا القسم"
    const val noTitles = "لا يوجد محتوى في هذا القسم"
    const val noFavorites = "لم تضف شيئاً إلى المفضلة بعد"
    const val favoritesHint = "اضغط مطولاً على أي عنصر لإزالته من المفضلة"
    const val noEpisodes = "لا توجد حلقات متاحة لهذا المسلسل"

    // Program guide
    const val guide = "دليل البرامج"
    const val noGuide = "لا يتوفر دليل برامج لهذه القناة"
    const val nowShowing = "الآن"
    const val nextShowing = "التالي"

    // Details
    const val play = "تشغيل"
    const val playFirst = "ابدأ المشاهدة"
    const val resumeEpisode = "تابع من حيث توقفت"
    const val fromStart = "من البداية"
    const val cast = "الممثلون"
    const val director = "الإخراج"

    // Player
    const val liveBadge = "مباشر"
    const val pause = "إيقاف مؤقت"
    const val previous = "السابق"
    const val next = "التالي"
    const val tracks = "الصوت والترجمة"
    const val audio = "الصوت"
    const val subtitles = "الترجمة"
    const val subtitlesOff = "بدون ترجمة"

    // Settings
    const val account = "الحساب"
    const val server = "الخادم"
    const val status = "الحالة"
    const val expiry = "ينتهي الاشتراك"
    const val connections = "الأجهزة المتصلة"
    const val lightMode = "وضع الأداء الخفيف"
    const val lightModeHint = "يوقف حركة الخلفية والتكبير، مفيد للأجهزة الضعيفة"
    const val refreshData = "تحديث القوائم"
    const val refreshed = "تم التحديث"
    const val logout = "تسجيل الخروج"
    const val logoutQuestion = "هل تريد تسجيل الخروج؟"
    const val logoutHint = "ستحتاج إلى إدخال بيانات الدخول مرة أخرى"
    const val subscriptionOpen = "الاشتراك مفتوح"
    const val subscriptionOpenShort = "مفتوح"

    // Errors
    const val errNoAccount = "لم يتم تسجيل الدخول بعد"
    const val errBadUrl = "رابط الخدمة غير صحيح. تأكد من كتابته كما وصلك من المزوّد"
    const val errFillAll = "اكتب رابط الخدمة واسم المستخدم وكلمة المرور"
    const val errBadLogin = "اسم المستخدم أو كلمة المرور غير صحيحة"
    const val errDisabled = "هذا الاشتراك موقوف. تواصل مع المزوّد"
    const val errExpired = "انتهى هذا الاشتراك. تواصل مع المزوّد لتجديده"
    const val errUnreachable = "تعذّر الوصول إلى الخادم. تأكد من الرابط ومن اتصالك بالإنترنت"
    const val errTimeout = "انتهت مهلة الاتصال. حاول مرة أخرى"
    const val errSsl = "تعذّر إنشاء اتصال آمن مع الخادم. جرّب الرابط بصيغة http"
    const val errNetwork = "حدثت مشكلة في الاتصال بالشبكة"
    const val errBadResponse = "ردّ الخادم غير مفهوم. تأكد أن الرابط خاص بخدمة Xtream"
    const val errUnknown = "حدث خطأ غير متوقع"
    const val errPlayback = "تعذّر تشغيل هذا المحتوى. قد يكون الخادم مشغولاً أو الاشتراك لا يسمح بالتشغيل الآن"

    fun errServer(code: Int) = "الخادم رد برمز خطأ ($code)"

    fun episodeNumber(number: Int) = "الحلقة $number"

    fun season(number: Int) = "الموسم $number"

    fun seasonEpisode(season: Int, episode: Int) = "الموسم $season · الحلقة $episode"

    fun resumeFrom(time: String) = "تابع من $time"

    fun trackNumber(number: Int) = "مسار $number"

    fun version(name: String) = "سحاب · الإصدار $name"

    fun subscriptionLine(date: String, daysLeft: Int) = "ينتهي الاشتراك في $date · متبقي $daysLeft يوماً"

    fun subscriptionEnded(date: String) = "انتهى الاشتراك في $date"

    fun kindLabel(kind: String) = when (kind) {
        Kind.LIVE -> "قناة"
        Kind.MOVIE -> "فيلم"
        else -> "مسلسل"
    }

    fun resizeLabel(index: Int) = when (index) {
        0 -> "ملاءمة الشاشة"
        1 -> "ملء الشاشة"
        else -> "تكبير"
    }
}
