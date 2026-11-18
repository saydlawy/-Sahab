package com.saydlawy.sahab.util

import com.saydlawy.sahab.data.Kind

/** Every piece of text the app shows. The app is Arabic only. */
object Ar {
    const val appName = "سحاب"
    const val tagline = "مشاهدتك، بكل هدوء"
    const val brand = "سحاب"
    const val brandTagline = "البث التلفزيوني الذكي للشاشات الكبيرة"
    const val home = "الرئيسية"
    const val profile = "الحساب"
    const val all = "الكل"
    const val dpadReady = "D-Pad Ready"
    const val details = "التفاصيل"
    const val plot = "القصة"
    const val channelList = "قائمة القنوات الحية"
    const val moviesLibrary = "مكتبة الأفلام"
    const val seriesLibrary = "مكتبة المسلسلات"
    const val epgTitle = "دليل البرامج (EPG)"
    const val noEpgShort = "لا تتوفر بيانات الدليل لهذه القناة"
    const val pickChannel = "اختر قناة لعرض تفاصيلها"
    const val pickTitle = "اختر عنواناً لعرض تفاصيله"
    const val liveNow = "مباشر الآن"
    const val onAirNow = "يعرض الآن"
    const val soon = "قريباً"
    const val ended = "انتهى"

    // Login (identity screen)
    const val firstSetup = "إعداد الجهاز الأول"
    const val welcomeTitle = "مرحباً بك في منصة سحاب - البث التلفزيوني الذكي للشاشات الكبيرة"
    const val welcomeBody = "يرجى إدخال بيانات الاشتراك الخاصة بك للبدء في الاستمتاع بآلاف القنوات والأفلام والمسلسلات."
    const val serverLabel = "رابط الخادم (Server URL)"
    const val userLabel = "اسم المستخدم (Username)"
    const val passLabel = "كلمة المرور (Password)"
    const val userPlaceholder = "أدخل اسم المستخدم"
    const val loginInstall = "تسجيل الدخول والتثبيت"
    const val m3uTipTitle = "لصق رابط M3U كامل"
    const val m3uTipBody = "الصق رابط القائمة في حقل الخادم وسنملأ بقية البيانات تلقائياً"
    const val statusReady = "جاهز للاتصال بالخادم"
    const val statusConnecting = "جارٍ الاتصال بالخادم…"
    const val statusFailed = "تعذّر الاتصال - راجع البيانات"

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
    const val tracks = "الجودة والصوت والترجمة"
    const val videoQuality = "جودة الفيديو"
    const val qualityAuto = "تلقائي (أفضل استقرار)"
    const val audio = "الصوت"
    const val subtitles = "الترجمة"
    const val subtitlesOff = "بدون ترجمة"
    const val mute = "كتم الصوت"
    const val unmute = "تشغيل الصوت"
    const val fullscreen = "ملء الشاشة"
    const val reconnecting = "جارٍ إعادة الاتصال بالبث…"

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
    const val nextProgramLabel = "التالي:"
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

    fun versionShort(name: String) = "الإصدار $name"

    fun itemsCount(count: Int) = "$count عنوان"

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
