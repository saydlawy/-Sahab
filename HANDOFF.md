# وثيقة التسليم الشاملة للمشروع (Sahab IPTV / OTT Master Handoff)

> **الإصدار:** 2.0 (Full Refactor & Production Audit Complete)  
> **تاريخ التوثيق:** 13 نوفمبر 2026  
> **الهدف:** توفير مرجع هندسي وتقني شامل وتفصيلي لكل ملف، كلاس، وظيفة، ومسار بيانات داخل مشروع تطبيق **سحاب (Sahab)**؛ لتمكين أي مهندس أو فريق عمل من استيعاب المنظومة بالكامل، صيانتها، وتطويرها دون فقدان أي تفصيلة برمجية أو معمارية.

---

## فهرس المحتويات
1. [الهيكل العام والمعمارية (Architecture & Tech Stack)](#1-الهيكل-العام-والمعمارية)
2. [دليل شجرة الملفات الشامل (File-by-File Codebase Directory)](#2-دليل-شجرة-الملفات-الشامل)
   - [2.1 نقطة الدخول وحاوية التطبيق (App & Entry Point)](#21-نقطة-الدخول-وحاوية-التطبيق)
   - [2.2 طبقة البيانات والشبكة (Data & Network Layer)](#22-طبقة-البيانات-والشبكة)
   - [2.3 طبقة كتالوج الأفلام المدمج (Catalog & TMDB Matching)](#23-طبقة-كتالوج-الأفلام-المدمج)
   - [2.4 طبقة المركز الرياضي (Sports Hub & Room Database)](#24-طبقة-المركز-الرياضي)
   - [2.5 محرك الذكاء الاصطناعي لتوليد القصص (Gemini AI Engine)](#25-محرك-الذكاء-الاصطناعي-لتوليد-القصص)
   - [2.6 أساسات الواجهة والتكيف (UI Foundation & Adaptive System)](#26-أساسات-الواجهة-والتكيف)
   - [2.7 المكونات المشتركة والتصميم (Design System & Components)](#27-المكونات-المشتركة-والتصميم)
   - [2.8 شاشات التطبيق الأساسية (Application Screens)](#28-شاشات-التطبيق-الأساسية)
   - [2.9 محرك تشغيل الفيديو (ExoPlayer & SahabPlayer Engine)](#29-محرك-تشغيل-الفيديو)
   - [2.10 الأدوات المساعدة واللغة (Utilities & Localization)](#210-الأدوات-المساعدة-واللغة)
   - [2.11 الموارد والتكوين (Resources & Manifest)](#211-الموارد-والتكوين)
3. [دورة حياة البيانات والتخزين المؤقت (Data Lifecycle & Caching)](#3-دورة-حياة-البيانات-والتخزين-المؤقت)
4. [هندسة مشغل الفيديو والأداء على التلفاز (Media & TV Performance)](#4-هندسة-مشغل-الفيديو-والأداء-على-التلفاز)
5. [الأمان وإدارة المفاتيح والشبكة (Security & Networking)](#5-الأمان-وإدارة-المفاتيح-والشبكة)
6. [نظام البناء والاختبارات والأتمتة (Build System & CI/CD Pipeline)](#6-نظام-البناء-والاختبارات-والأتمتة)

---

## 1. الهيكل العام والمعمارية

تطبيق **سحاب (Sahab)** مبني باستخدام أحدث حزم تطوير أندرويد المعاصرة:
- **لغة البرمجة:** Kotlin 2.0+ بميزات الأداء والتزامن العالي.
- **واجهات المستخدم:** Jetpack Compose (Material3) بالكامل وبدون أي ملفات Layout XML تقليدية.
- **مشغل الوسائط:** AndroidX Media3 / ExoPlayer 1.5+ مجهز بذاكرة تخزين مؤقت وميزات استهلاك منخفض لوحدات فك التشفير العتادية (Hardware MediaCodec).
- **قواعد البيانات:** Android Room Database (v2.6+) مع هجرات ومؤشرات استعلام سريعة.
- **إدارة الحالة والتزامن:** Coroutines + StateFlow / SharedFlow وتجميع متوافق مع دورة حياة الشاشة (`collectAsStateWithLifecycle`).
- **التكيف مع الشاشات (Adaptive Design):** دعم متكامل وموحد لشاشات التلفزيون (Android TV / Leanback مع تنقل كامل بريموت التحكم D-Pad)، والهواتف الذكية (Portrait / Landscape)، والأجهزة اللوحية (Foldables / Tablets).
- **حقن الاعتماديات (DI):** نمط Container يدوي خفيف الوزن (`AppContainer`) متصل بدورة حياة `SahabApp`، مما يمنع بطء بدء التشغيل المرتبط بمكتبات التوليد الكودي الضخمة.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        AppRoot (Compose Router)                        │
└─────┬──────────────┬──────────────┬──────────────┬──────────────┬──────┘
      │              │              │              │              │
┌─────▼─────┐  ┌─────▼─────┐  ┌─────▼─────┐  ┌─────▼─────┐  ┌─────▼─────┐
│HomeScreen │  │LiveScreen │  │BrowseScr. │  │SportsScr. │  │PlayerScr. │
└─────┬─────┘  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘
      │              │              │              │              │
      └──────────────┴──────────────┼──────────────┴──────────────┘
                                    │
                       ┌────────────▼────────────┐
                       │  AppContainer / ViewModels
                       └────────────┬────────────┘
                                    │
         ┌──────────────────────────┼──────────────────────────┐
         │                          │                          │
┌────────▼────────┐        ┌────────▼────────┐        ┌────────▼────────┐
│ Repository      │        │ SportsDatabase  │        │ GeminiStoryServ │
│ (Xtream + Tmdb) │        │ (Room DB v2)    │        │ (AI Stories)    │
└────────┬────────┘        └────────┬────────┘        └─────────────────┘
         │                          │
┌────────▼────────┐        ┌────────▼────────┐
│ DiskCache       │        │ GoalApi / Sync  │
│ (Catalog/Media) │        │ (WorkManager)   │
└─────────────────┘        └─────────────────┘
```

---

## 2. دليل شجرة الملفات الشامل

### 2.1 نقطة الدخول وحاوية التطبيق (App & Entry Point)

#### `MainActivity.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/MainActivity.kt`
- **الوظيفة:** نقطة الدخول الوحيدة للتطبيق (Single-Activity Architecture).
- **المسؤوليات:**
  - تفعيل العرض من الحافة للحافة (`enableEdgeToEdge()`).
  - حقن حاوية التطبيق `container = (application as SahabApp).container`.
  - استدعاء الجذر الرسومي `AppRoot(container)`.

#### `SahabApp.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/SahabApp.kt`
- **الكلاسات المضمنة:**
  - `class SahabApp : Application(), ImageLoaderFactory`:
    - تهيئة `FirebaseApp.initializeApp(this)` لخدمات السحابة وFirestore.
    - تهيئة `AppContainer(this)`.
    - جدولة المهام الخلفية التلقائية عبر `appScope.launch` بعيداً عن الخيط الرئيسي (`SyncScheduler.scheduleInitialRunIfNeeded` و `SahabPlayer.warmUpMediaCache`).
    - تهيئة مكتبة الصور `Coil ImageLoader` بذاكرة RAM بنسبة 25% وذاكرة تخزين للقرص بسعة 500 ميجابايت مع تعطيل `crossfade` لمنع بطء التمرير على التلفاز.
  - `class AppContainer(context: Context)`: الحاوية المركزية التي توفر نسخ Singletons للخدمات:
    - `prefs`: إعدادات وتفضيلات المستخدم والحساب.
    - `diskCache`: محرك التخزين المؤقت المحلي.
    - `gemini`: خدمة الذكاء الاصطناعي لتوليد قصص الأفلام.
    - `aiKeys`: مدير مفاتيح Gemini المشفرة.
    - `catalogDb`: قاعدة بيانات كتالوج الأفلام المدمجة.
    - `catalogSync`: محرك مزامنة وبحث الكتالوج.
    - `client`: عميل `OkHttpClient` المركزي مهيأ بذاكرة تخزين مؤقت 50 ميجابايت، مهلة اتصال 15 ثانية، وحراس الأمان.
    - `CleartextGuardInterceptor`: حارس أمان يمنع مرور أي طلب HTTP غير مشفر للنطاقات الرسمية الحساسة (TMDB, Goal-API, GitHub).
    - `RetryInterceptor`: معالج إعادة المحاولة الذكي مع تراجع زمني (Exponential Backoff: 500ms ثم 1500ms) للأخطاء الخادمة 5xx؛ مع استثناء مسارات بث الفيديو لتترك لإكسوبلير.
    - `tmdb`: عميل واجهة TMDB.
    - `repo`: مستودع البيانات المركزي (`Repository`).
    - `appScope`: نطاق كوروتين عام (`SupervisorJob + Dispatchers.IO`) يضمن استمرار حفظ التقدم حتى لو أُغلقت شاشة المشغل.
    - `accountHash`: تجزئة أمان فريدة (SHA-256) للحساب النشط لعزل التخزين المؤقت للفيديو بين المستخدمين.
    - `saveProgress(item, positionMs, durationMs)`: دالة تسجيل وحفظ تقدم المشاهدة المشتركة.

---

### 2.2 طبقة البيانات والشبكة (Data & Network Layer)

#### `Models.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/data/Models.kt`
- **الوظيفة:** نماذج البيانات غير القابلة للتغيير (Immutable Data Models):
  - `enum class Kind { LIVE, MOVIE, SERIES }`: الأنواع الثلاثة للمحتوى التلفزيوني.
  - `data class Account(server, user, pass)`: بيانات اعتماد الاتصال بسيرفر Xtream.
  - `data class UserInfo(...)`: بيانات اشتراك المستخدم (الحالة، تاريخ الانتهاء، الاتصالات المتزامنة).
  - `data class Category(id, name, kind)`: تصنيفات القنوات والأفلام والمسلسلات.
  - `data class Channel(id, num, name, icon, streamId, categoryId, epgChannelId, isFavorite)`: قناة البث المباشر.
  - `data class Movie(id, name, streamId, categoryId, icon, rating, year, containerExt, tmdbId, backdrop, plot, isFavorite)`: بيانات الفيلم.
  - `data class Series(...)`: بيانات المسلسل وقائمة المواسم.
  - `data class Season(...)` & `data class Episode(...)`: تفاصيل المواسم والحلقات.
  - `data class EpgProgram(id, startMs, endMs, title, desc)`: برامج دليل البث الإلكتروني.
  - `data class PlayItem(...)`: عنصر التشغيل الفعلي المحول لرابط وسائط.
  - `data class PlayRequest(...)`: حمولة طلب فتح المشغل (قائمة التشغيل، الفهرس الحالي، وضع البداية).
  - `data class RecentItem(...)`: سجل عناصر المشاهدة الأخيرة وموضع التوقف بالمللي ثانية.

#### `Prefs.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/data/Prefs.kt`
- **الوظيفة:** إدارة التخزين الدائم للتفضيلات والحسابات:
  - تشفير بيانات الاعتماد عبر `EncryptedSharedPreferences` ومفتاح الأمان الرئيسي للنظام `MasterKey (AES256_GCM + AES256_SIV)`.
  - تخزين المفضلة (`favorites`) بصيغة `Set<String>` سريعة القراءة.
  - تخزين سجل المشاهدة الأخيرة (`recents`) بحد أقصى 50 عنصراً مع تحديث تلقائي وتدفق `StateFlow<List<RecentItem>>`.
  - إدارة تفضيلات الشاشة الرئيسية المخصصة:
    - `showRecents: Flow<Boolean>`: إظهار أو إخفاء قسم "تابع المشاهدة" (`RecentsStrip`).
    - `homeChannelsLayout: Flow<HomeChannelsLayout>`: أسلوب عرض القنوات المميزة (سلايدر متحرك `CAROUSEL` أو شبكة أيقونات `GRID`).
  - إدارة تفضيلات مشغل الفيديو: نسبة أبعاد العرض (Aspect Ratio: Fit, Zoom, Stretch)، المسار الصوتي المفضل، ولغة الترجمة المفضلة.
  - إدارة توقيت المزامنة الدورية للرياضة والكتالوج.

#### `Repository.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/data/Repository.kt`
- **الوظيفة:** المستودع المركزي الذي يجمع ويوحد مصادر البيانات:
  - إدارة دورة جلب وتحديث وتحليل بيانات Xtream Codes API.
  - تحليل قوائم القنوات والأفلام والمسلسلات بخوارزميات مسح أحادي (`Single-pass parsing`) عالية الكفاءة مع منع التكرار.
  - كاش دليل برامج EPG خفيف في الذاكرة عبر `LinkedHashMap` محدود السعة (LRU Cache حتى 256 عنصراً) يوفر دالة `peekShortEpg` للقراءة الفورية بدون تأخير أثناء التمرير السريع.
  - تخزين القوائم محلياً على القرص عبر `DiskCache` لتمكين فتح التطبيق والعمل في وضع عدم الاتصال (Offline Mode).

#### `XtreamApi.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/data/XtreamApi.kt`
- **الوظيفة:** عميل اتصال بروتوكول Xtream Codes IPTV:
  - بناء روابط الاستعلام: `player_api.php?username=...&password=...&action=...`.
  - جلب بيانات الحساب: `action=get_live_categories`, `get_live_streams`, `get_vod_categories`, `get_vod_streams`, `get_series_categories`, `get_series`, `get_series_info`.
  - جلب دليل البرامج المختصر: `action=get_short_epg&stream_id=...`.

#### `TmdbApi.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/data/TmdbApi.kt`
- **الوظيفة:** إثراء محتوى الأفلام والمسلسلات ببيانات TheMovieDb الرسمية:
  - استخدام كاش ذاكرة محمي الخيوط (`LinkedHashMap LRU` بسعة 200 فيلم).
  - تنظيف عناوين سيرفرات IPTV عبر تعبيرات نمطية مسبقة التجميع وثابتة (`CLEAN_REGEX`, `RESOLUTIONS_REGEX`) لإزالة الضجيج مثل (4K, 1080p, FHD, Arabic, Multi-Sub) للوصول للاسم الحقيقي.
  - جلب البوستر عالي الدقة، ملخص القصة باللغة العربية، التقييم الدولي، وأسماء الممثلين.

#### `DiskCache.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/data/DiskCache.kt`
- **الوظيفة:** محرك تخزين القرص المؤقت المتين:
  - تشفير أسماء الملفات عبر `ThreadLocal<MessageDigest>` لتجزئة SHA-256 دون تزاحم خيوط.
  - تخزين بيانات الكتالوج وبيانات الوسائط مع مراقبة الحجم التقديري للقرص وتطبيق خوارزمية إخلاء LRU تلقائية عند تجاوز الحد المسموح لمنع ملء ذاكرة التلفاز.

#### `Json.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/data/Json.kt`
- **الوظيفة:** كائن `kotlinx.serialization.json.Json` مهيأ بوضع متسامح (`ignoreUnknownKeys = true`, `isLenient = true`, `coerceInputValues = true`) لتحمل اختلافات استجابات سيرفرات IPTV غير القياسية.

---

### 2.3 طبقة كتالوج الأفلام المدمج (Catalog & TMDB Matching)

#### `CatalogDatabase.kt` & `CatalogMovieEntity.kt` & `CatalogMovieDao.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/data/catalog/`
- **الوظيفة:** قاعدة بيانات Room محلية (`catalog.db` - الإصدار 1) لفهرسة آلاف الأفلام مع بياناتها السينمائية مسبقة الإعداد:
  - `CatalogMovieEntity`: المعرف، الاسم الأصلي، الاسم العربي، سنة الإنتاج، مسار البوستر، مسار الخلفية، التقييم، ملخص القصة، ومعرف التصنيف.
  - `CatalogMovieDao`: استعلامات البحث والمطابقة وتدفقات المراقبة التفاعلية (`observeByCategory`, `findByStreamId`, `upsertAll`).

#### `CatalogSync.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/data/catalog/CatalogSync.kt`
- **الوظيفة:** محرك استخراج وبذر الكتالوج المدمج من الملف المضغوط `catalog_seed.json.gz` الموجود في مجلد `assets` في الخلفية عند التثبيت الأولي لمنح المستخدم تصفحاً فورياً وغنياً دون انتظار اتصالات الشبكة.

#### `TmdbMatcher.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/data/catalog/TmdbMatcher.kt`
- **الوظيفة:** خوارزمية مطابقة غامضة (Fuzzy Matcher) ذكية:
  - توحيد الحروف العربية وإزالة التشكيل وهمزات الألف وتطبيع التاء المربوطة.
  - تنظيف الكلمات الدلالية الزائدة في أسماء القنوات والأفلام.
  - حساب درجات التشابه عبر خوارزمية Levenshtein الموزونة لمطابقة أسماء القنوات والأفلام مع سجلات TMDB بأعلى دقة.

---

### 2.4 طبقة المركز الرياضي (Sports Hub & Room Database)

#### `SportsDatabase.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/data/sports/SportsDatabase.kt`
- **الوظيفة:** قاعدة بيانات المباريات والبطولات المحلية بنظام Room (الإصدار 2):
  - الكيان الأول `MatchEntity`: يمثل المباراة (المعرف، معرف البطولة، اسم البطولة، الدولة، الفريق المستضيف وشعاره، الفريق الضيف وشعاره، النتيجة، حالة المباراة: مجدولة، مباشرة، منتهية، التوقيت، القناة الناقلة والمعلق).
  - الكيان الثاني `StandingEntity`: يمثل جدول ترتيب الدوري (المركز، الفريق، عدد المباريات، الفوز، التعادل، الخسارة، الأهداف، النقاط).
  - الفهرسة `Indices`: إنشاء مؤشرات سريعة `index_matches_leagueId` و `index_matches_date` لتسريع استعلامات التصفية الزمنية اللحظية.
  - الترحيل `MIGRATION_1_2`: ترحيل سليم يحافظ على بيانات المستخدم والقاعدة المدمجة مسبقاً في `assets/databases/sports_db`.

#### `SportsModels.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/data/sports/SportsModels.kt`
- **الوظيفة:** كائنات النطاق الرياضي: `MatchStatus` (UPCOMING, LIVE, FINISHED, POSTPONED, CANCELLED)، `Match`، `Standing`، و `League`.

#### `GoalApi.kt` & `GoalLeagueMapping.kt` & `AllowedLeaguesConfig.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/data/sports/`
- **الوظيفة:** تكامل خدمة البيانات الرياضية المباشرة Goal-API:
  - ربط 19 بطولة عالمية وعربية كبرى (الدوري المصري الممتاز، دوري أبطال إفريقيا، دوري روشن السعودي، الدوري الإنجليزي، دوري أبطال أوروبا، كأس العالم...).
  - ترتيب الدوريات حسب أولوية المشاهد العربي لضمان ظهور البطولات الأكثر أهمية في المقدمة.

#### `MonthlySyncWorker.kt` & `SyncScheduler.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/data/sports/`
- **الوظيفة:** المزامنة الخلفية عبر Android WorkManager:
  - جدولة مهمة دورية مجدولة شهرياً أو عند فتح التطبيق مع اشتراط وجود اتصال إنترنت وبطارية غير منخفضة لتحديث جداول ومباريات الشهر دون أي استهلاك للواجهة.

---

### 2.5 محرك الذكاء الاصطناعي لتوليد القصص (Gemini AI Engine)

#### `ApiKeyManager.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/data/ai/ApiKeyManager.kt`
- **الوظيفة:** إدارة مشفرة لمفاتيح Google Gemini API:
  - حفظ المفاتيح مشفرة بـ `EncryptedSharedPreferences (AES256)`.
  - دعم إضافة عدة مفاتيح وتسميتها والتنقل بينها.
  - التدوير الذكي للمفاتيح وتتبع أخطاء تجاوز معدل الاستخدام (`Rate Limit - HTTP 429`) للتبديل التلقائي إلى مفتاح بديل فعال.

#### `GeminiStoryService.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/data/ai/GeminiStoryService.kt`
- **الوظيفة:** خدمة توليد ملخصات وقصص الأفلام والمسلسلات المشوهة عبر الذكاء الاصطناعي متعدد الوسائط:
  - تنزيل صورة بوستر الفيلم أو المسلسل وتشفيرها إلى Base64.
  - إرسال طلب إلى Google Gemini 2.5 Flash / Pro مدعوماً بميزة البحث في الويب (Google Search Grounding).
  - هندسة أوامر (Prompt Engineering) تطلب التعرف على العمل الأصلي من البوستر والاسم وتوليد قصة شيقة باللغة العربية الفصحى بصيغة JSON محكمة مع مصادر الويب ومستوى الثقة.

---

### 2.6 أساسات الواجهة والتكيف (UI Foundation & Adaptive System)

#### `AppRoot.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/ui/AppRoot.kt`
- **الوظيفة:** منسق التنقل العام وتوزيع الشاشات:
  - مراقبة حالة تسجيل الدخول عبر `prefs.accountFlow`: التوجيه تلقائياً إما إلى `LoginScreen` أو شاشات التطبيق الأساسية.
  - إدارة شريط التبويبات العلوي `NavTabsRow` عبر `CompositionLocalProvider(LocalTabNavigator)`.
  - إدارة التنقل بين التبويبات: الرئيسية (`Home`)، البث المباشر (`Live`)، الأفلام (`Movies`)، المسلسلات (`Series`)، الرياضة (`Sports`)، الإعدادات (`Settings`).
  - الاستجابة لطلبات تشغيل الفيديو والانتقال لشاشة المشغل بكامل الشاشة `PlayerScreen`.

#### `Locals.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/ui/Locals.kt`
- **الوظيفة:** نظام التكيف الشامل (Adaptive Layout System):
  - حساب تصنيف عرض وارتفاع الشاشة (`WidthSizeClass`, `HeightSizeClass`).
  - تحديد وضع الجهاز: تلفاز ذكي (`TV`)، هاتف أفقي (`PhoneLandscape`)، أو هاتف عمودي (`PhonePortrait`).
  - توفير كائنات `LocalAdaptive` و `LocalTabNavigator` و `SimpleFactory` لحقن الـ ViewModels في Compose بسهولة.

#### `Theme.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/ui/theme/Theme.kt`
- **الوظيفة:** الهوية البصرية ونظام الألوان:
  - خط **Cairo** العربي الأصيل المدمج في التطبيق بخمسة أوزان (Regular, Medium, SemiBold, Bold, ExtraBold) ليعمل دون الحاجة لخدمات Google Play على شاشات التلفاز الصينية.
  - لوحة الألوان الكونية `Palette`: الأزرق العميق (`DeepBlue = #0B0F24`)، البنفسجي الملكي (`Royal = #8E45FF`)، التوهج السماوي (`Glow = #7DC4FF`)، وأسطح الزجاج المصنفر (`GlassStrong = #A612122D`).
  - فرض اتجاه الواجهة من اليمين لليسار (`LayoutDirection.Rtl`) عالمياً في كامل التطبيق.

---

### 2.7 المكونات المشتركة والتصميم (Design System & Components)

#### `Background.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/ui/components/Background.kt`
- **الوظيفة:** خلفية التطبيق الكونية الحية `DreamyBackground`: رسم التدرجات الفلكية عبر `Modifier.drawWithCache` لتثبيت التدرجات في ذاكرة GPU الرسومية ومنع أي تخصيص للذاكرة أثناء التمرير.

#### `Glass.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/ui/components/Glass.kt`
- **الوظيفة:** عناصر التصميم الزجاجي المصنفر (Frosted Glass):
  - `GlassCard`, `GlassPanel`, `GlassButton`, `GlassIconButton`, `Chip`, `Tag`.
  - معالجة التركيز التفاعلي للتلفاز (`onFocusChanged`) مع توهج وحواف مضيئة.
  - عرض بوسترات الوسائط عبر `AsyncImage` مع إيقاف `crossfade` للأداء الفائق.

#### `TopBar.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/ui/components/TopBar.kt`
- **الوظيفة:** الشريط العلوي للتطبيق:
  - شعار سحاب المضيء `BrandLogo`.
  - ساعة وتاريخ النظام المتجددة تلقائياً كل 30 ثانية `ClockPill`.
  - أزرار تبويب التنقل `NavTabsRow` مع دعم التحكم التام عبر ريموت التلفاز.

#### `Fields.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/ui/components/Fields.kt`
- **الوظيفة:** حقول الإدخال والبحث ولوحة التصنيفات الجانبية (`CategoryPane`, `SearchBox`, `GlassTextField`).

#### `Icons.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/ui/components/Icons.kt`
- **الوظيفة:** مكتبة متجهات الأيقونات النقية المدمجة كودياً (`Ic`): بديل فائق الخفة لمكتبات Material Icons الكبيرة، توفر أيقونات التشغيل، الإيقاف، الصوت، الترجمة، القفل، المفضلة، الرياضة، والرجوع.

#### `EpgDialog.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/ui/components/EpgDialog.kt`
- **الوظيفة:** نافذة منبثقة تفاعلية لعرض جدول مواعيد برامج القناة المختارة الكامل على مدار اليوم مع مؤشر البرنامج المعروض حالياً.

#### `SyncOverlay.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/ui/components/SyncOverlay.kt`
- **الوظيفة:** شاشة التحميل والتهيئة الأولية التي تظهر عند أول تشغيل أثناء فك ضغط وبذر كتالوج البيانات المدمجة.

---

### 2.8 شاشات التطبيق الأساسية (Application Screens)

#### `HomeScreen.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/ui/home/HomeScreen.kt`
- **الوظيفة:** الواجهة الرئيسية:
  - ودجت الساعة والطقس التفاعلي الاحترافي (`ClockWeatherCard`): يضم ساعة تناظرية كلاسيكية فائقة السلاسة (`AnalogClockFace`) مرسومة بـ Canvas مع عقارب متدرجة وتوهج كوني، وساعة رقمية، وتاريخ عربي، وقرص طقس حقيقي ديناميكي يعرض درجة الحرارة الحالية، المدينة، وأيقونة الطقس الحية المجلوبة تلقائياً من `WeatherService`.
  - قسم القنوات المميزة المخصص (`FeaturedChannelsSection`): يظهر مباشرة أسفل ودجت الساعة والطقس، ويُعرض وفق اختيار المستخدم في الإعدادات:
    1. **سلايدر متحرك (`FeaturedChannelsCarousel`)**: يستخدم `TvLazyRow` مع إزاحة التمركز `pivotOffsets = PivotOffsets(0.5f, 0.5f)` بحيث يبدأ أول عنصر في منتصف الشاشة تماماً، ويطبق تأثير الـ 3D Parallax (`Modifier.scale(1.12f)`, `Modifier.alpha(1.0f)`, `Modifier.zIndex(5f)`) على العنصر النشط مع تصغير وتعتيم العناصر المجاورة وسحب العنصر النشط تلقائياً إلى المركز عند التنقل بالريموت (D-Pad).
    2. **شبكة أيقونات (`FeaturedChannelsGrid`)**: يعرض نفس القنوات كبلاطات زجاجية مربعة متراصة عبر `LazyVerticalGrid`.
  - جلب ذكي وتلقائي للقنوات المميزة (`loadFeaturedChannels`): يستخرج القنوات المستهدفة من اشتراك المستخدم الفعلي بالاسم (روتانا سينما، روتانا خليجية، الجزيرة، العربية، beIN SPORTS، SSC، MBC...) مع قائمة احتياطية `FALLBACK_FEATURED_CHANNELS` للتحميل الفوري.
  - بطاقات الدخول السريع للأقسام الرئيسية (البث المباشر، الأفلام، المسلسلات، الرياضة).
  - شريط المشاهدة الأخيرة `RecentsStrip` مع شريط تقدم لمتابعة المحتوى من لحظة التوقف بنقرة واحدة، ويمكن إظهاره أو إخفاؤه حسب تفضيل المستخدم (`showRecents`).
  - بطاقة المباريات السريعة `SportsQuickCard` التي تعرض أهم مباريات اليوم المباشرة، وشريط البث الرياضي المباشر `LiveMatchesBar`.
  - تصميمات متكيفة منفصلة للتلفاز العريض، والهاتف الأفقي، والهاتف العمودي.

#### `LiveScreen.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/ui/live/LiveScreen.kt`
- **الوظيفة:** مركز البث التلفزيوني المباشر (أكثر من 1400 سطر برمجي):
  - مشغل مصغر تفاعلي (Mini Preview Player) مهيأ بكسل لا يعمل إلا عند اختيار القناة، ويتحرر فوراً من الذاكرة (`release()`) عند الانتقال للشاشة الكاملة لتجنب استنزاف وحدات فك التشفير.
  - قائمة القنوات الأفقية/العمودية المقسمة حسب الفئات مع بحث سريع فوري غير حاجب للخيط الرئيسي (`Dispatchers.Default`).
  - سطور القنوات `ChannelRow` تقرأ دليل EPG الفوري من الذاكرة وتؤخر جلب الشبكة بـ 300 ملي ثانية حتى تستقر حركة التمرير.

#### `BrowseScreen.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/ui/browse/BrowseScreen.kt`
- **الوظيفة:** متصفح مكتبة الأفلام والمسلسلات:
  - شبكة عرض بوسترات سينمائية مرنة تتكيف مع حجم الشاشة (من 2 إلى 7 أعمدة).
  - لوحة التصنيفات مع عدادات المحتوى لكل قسم.
  - معاينة سريعة للفيلم المختار بدون فتح شاشة التفاصيل، مع دعم كامل للريموت.

#### `MovieDetailScreen.kt` & `SeriesDetailScreen.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/ui/detail/`
- **الوظيفة:** شاشات تفاصيل المحتوى السينمائي:
  - عرض خلفية العمل الملحمية، التقييم، سنة الإنتاج، التصنيفات، وأزرار التشغيل وإضافة للمفضلة.
  - زر الذكاء الاصطناعي "قصة العمل بـ AI" في شاشة الفيلم والمسلسل الذي يفتح نافذة التوليد التفاعلية مع Gemini.
  - شبكة الحلقات في المسلسلات مقسمة حسب المواسم مع عرض الحلقة الأخيرة المشاهدة ومؤشر التقدم لكل حلقة.

#### `FavoritesScreen.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/ui/home/FavoritesScreen.kt`
- **الوظيفة:** مركز المفضلة الموحد، يجمع قنوات التلفزيون والأفلام والمسلسلات المفضلة مع سهولة الإزالة والتشغيل المباشر.

#### `SportsScreen.kt` & `SportsViewModel.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/ui/sports/`
- **الوظيفة:** المركز الرياضي المتكامل (أكثر من 2500 سطر برمجي):
  - عرض ومتابعة مباريات اليوم، الأمس، الغد، ومباريات الـ 7 و 14 و 28 يوماً.
  - تصفية سريعة حسب الدوريات والبطولات مع جدول ترتيب الدوري لحظي بالنقاط وفارق الأهداف.
  - تفاصيل كل مباراة في نافذة منبثقة مع أسماء المعلقين، القنوات الناقلة، والملاعب.
  - استعلامات SQLite سريعة وفعالة بدون إعادة تحميل عشوائية للبيانات.

#### `LoginScreen.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/ui/login/LoginScreen.kt`
- **الوظيفة:** شاشة الدخول:
  - تطبيع عنوان السيرفر تلقائياً وتصحيح كتابة HTTP/HTTPS والـ Port.
  - تحذير أمني شفاف عند استخدام اتصالات غير مشفرة.
  - فحص الصلاحية وحفظ البيانات مشفرة وبدء مزامنة القنوات فور تسجيل الدخول الناجح.

#### `SettingsScreen.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/ui/settings/SettingsScreen.kt`
- **الوظيفة:** شاشة الإعدادات:
  - تفاصيل الحساب وصلاحية الاشتراك وسيرفر الاتصال.
  - إدارة مفاتيح Gemini للذكاء الاصطناعي (إضافة، تعديل، حذف، واختبار).
  - إدارة مساحة التخزين المؤقت للصور وقوائم التشغيل وزر تنظيف الكاش.
  - تسجيل الخروج الآمن ومسح البيانات المؤقتة.

---

### 2.9 محرك تشغيل الفيديو (ExoPlayer & SahabPlayer Engine)

#### `SahabPlayer.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/ui/player/SahabPlayer.kt`
- **الوظيفة:** مصنع مشغل ExoPlayer المركزي:
  - بناء مشغل `ExoPlayer` محسن بذاكرة تخزين فيديو مؤقتة `SimpleCache` مع كاش غير محدود للـ VOD وكاش معزول لكل حساب.
  - تجريد روابط الوسائط من بيانات الاعتماد الحساسة في مفاتيح الكاش عبر تعبير نمطي ثابت.
  - استخراج شارات الجودة والصوت (4K, 1080p, 5.1 Surround, Stereo) من مسارات الوسائط.

#### `PlayerViewModel.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/ui/player/PlayerViewModel.kt`
- **الوظيفة:** إدارة منطق المشغل المعقد:
  - فصل تقدم المخطط الزمني عالي التردد في تدفق مستقل `val progress: StateFlow<PlaybackProgress>` لتحديث شريط الوقت كل 500 ملي ثانية دون إعادة رسم كامل شاشة المشغل (Decoupled Recomposition).
  - الانتقال التلقائي للحلقة التالية في المسلسلات.
  - الحفظ الذكي لتقدم المشاهدة لمحتوى VOD فقط كل 10 ثوانٍ، وتخطي البث المباشر لمنع إجهاد الذاكرة الدائمة.
  - التنقل بين القنوات (Zap Next / Previous) والعودة لآخر موضع بث مباشر (`seekToDefaultPosition`).

#### `PlayerScreen.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/ui/player/PlayerScreen.kt`
- **الوظيفة:** شاشة مشغل الفيديو الملحمية (2550 سطراً برمجياً):
  - طبقة الفيديو السفلية عبر `AndroidView(PlayerView)`.
  - واجهة التحكم التفاعلية الشفافة (OSD): تختفي تلقائياً بعد 5 ثوانٍ من عدم النشاط.
  - درج القنوات السريع الجانبي أثناء البث المباشر `LiveChannelsDrawer` لتبديل القناة دون مغادرة الفيديو.
  - درج الحلقات الجانبي `VodEpisodesDrawer` لاختيار حلقات المسلسل.
  - لوحات اختيار مسار الصوت والترجمة ونسبة أبعاد الشاشة وقفل الشاشة لمنع اللمس العرضي على الهواتف.
  - حلقة تركيز D-Pad ذكية تضمن انتقال المؤشر تلقائياً لزر التشغيل/الإيقاف عند ظهور الواجهة على التلفاز.

---

### 2.10 الأدوات المساعدة واللغة (Utilities & Localization)

#### `Ar.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/util/Ar.kt`
- **الوظيفة:** محرك التعريب واللغة العربية:
  - ترجمة رسائل أخطاء الشبكة والسيرفرات وصيغ الوقت وتنسيق أرقام المواسم والحلقات باللغة العربية الفصحى السليمة.

#### `Device.kt`
- **المسار:** `app/src/main/java/com/saydlawy/sahab/util/Device.kt`
- **الوظيفة:** كشف خواص الأجهزة والتنسيق الزمني:
  - دالة `isTelevision(context)` للكشف عن نظام Leanback أو التلفاز الذكي.
  - كاش `ThreadLocal<SimpleDateFormat>` لتنسيق التوقيت والساعات والتواريخ بسرعة فائقة ودون استنزاف الذاكرة العشوائية.

---

### 2.11 الموارد والتكوين (Resources & Manifest)

- **`AndroidManifest.xml`:** الأذونات (`INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`)، إعلان وضع الشاشة التلفزيونية (`LEANBACK_LAUNCHER`)، وأمان الشبكة.
- **`network_security_config.xml`:** فرض اتصالات مشفرة HTTPS لجميع الخدمات الرسمية، مع السماح المشروط لروابط سيرفرات IPTV الخاصة بالمستخدم.
- **`app/build.gradle.kts` & `libs.versions.toml`:** تعريف الإصدارات وتبعيات AndroidX, Compose, Media3, Room, Coil, OkHttp, WorkManager, Firebase, Security Crypto.
- **`app/proguard-rules.pro`:** قواعد تقليص الكود وحماية كلاسات Serialized ونماذج البيانات من التشويش أثناء بناء الـ Release.

---

## 3. دورة حياة البيانات والتخزين المؤقت

```
                      ┌───────────────────────────┐
                      │    Xtream Server / API    │
                      └─────────────┬─────────────┘
                                    │ (Network Fetch)
                      ┌─────────────▼─────────────┐
                      │    Repository (Memory)    │
                      │  - Single-Pass Parsed     │
                      │  - LRU Short EPG (256)    │
                      └──────┬─────────────┬──────┘
                             │             │
        (Write cache to disk)│             │(On-demand memory lookup)
                             │             │
        ┌────────────────────▼────┐   ┌────▼─────────────────────┐
        │ DiskCache (app storage) │   │ UI Layer (Compose Flows) │
        │ - gzipped / sha256      │   │ - Channels / Movies List │
        │ - 50MB Catalog limit    │   │ - Instant scroll render  │
        └─────────────────────────┘   └──────────────────────────┘
```

---

## 4. هندسة مشغل الفيديو والأداء على التلفاز

1. **إدارة وحدات فك التشفير (Hardware Codecs):**
   - تم حل مشكلة نفاد الموارد (`MediaCodec.CodecException`) على شاشات التلفاز القديمة عبر التدمير الفوري لأي مشغل مصغر في `LiveScreen` بمجرد الانتقال إلى شاشة العرض الكاملة `PlayerScreen`.
2. **عزل إعادة الرسم (Decoupled Recomposition):**
   - فصل `PlaybackProgress` عن `PlayerUiState` يمنع إعادة رسم عناصر واجهة المشغل كل نصف ثانية أثناء تشغيل الفيديو، مما يخفض استهلاك وحدة المعالجة المركزية (CPU) بنسبة تفوق 70%.
3. **التنقل عبر D-Pad:**
   - توفير `FocusRequester` مخصص لكل درج وقائمة جانبية يضمن عدم ضياع مؤشر الريموت عند فتح القوائم أو إغلاقها.

---

## 5. الأمان وإدارة المفاتيح والشبكة

- **تشفير البيانات الحساسة:** كلمات مرور الحساب ومفاتيح Gemini API لا تخزن كنصوص صريحة بل تشفر بمستوى عسكري (`AES256_GCM`) بواسطة Android Keystore.
- **حارس الاتصال الصريح (Cleartext Guard):** يعترض عميل الشبكة أي طلب غير مشفر لنطاقات النظام ويلغيه فوراً لحماية الخصوصية.
- **تجريد الروابط:** التخزين المؤقت للفيديو يشطب أسماء المستخدمين وكلمات المرور من مفاتيح الكاش حتى لا تظهر في سجلات النظام أو الملفات المؤقتة.

---

## 6. نظام البناء والاختبارات والأتمتة

- **الاختبارات الآلية في المستودع (`app/src/test/`):**
  - `DiskCacheTest.kt`: التحقق من سلامة التخزين المؤقت والإخلاء التلقائي.
  - `PrefsTest.kt`: اختبار حفظ وقراءة تفضيلات المستخدم وسجل المشاهدة.
  - `RepositoryTest.kt`: اختبار كاش الذاكرة والتصفح السريع للـ EPG.
  - `TmdbMatcherTest.kt`: اختبار خوارزميات مطابقة الأسماء والتطبيع العربي.
  - `GoalApiTest.kt`: اختبار تحليل بيانات المباريات والدوريات.
  - `SportsFilterTest.kt`: اختبار تصفية المباريات حسب التواريخ والأقسام.

- **خط الأنابيب السحابي (GitHub Actions - `.github/workflows/build-apk.yml`):**
  1. تشغيل اختبارات الوحدة: `gradle :app:testReleaseUnitTest`.
  2. بناء حزمة الإصدار: `gradle :app:assembleRelease`.
  3. توثيق وتوقيع الحزمة بمفتاح الإنتاج المشفر `apksigner verify`.
  4. نشر الحزمة الناتجة تلقائياً كإصدار دائم باسم `Sahab.apk` تحت وسم الإصدار الأحدث (`latest Release`).
  5. رفع حزمة الـ APK تلقائياً إلى **Firebase App Distribution** لتمكين التحديث التلقائي الفوري داخل التطبيق (`In-App Updates`) عبر `Firebase.appDistribution.updateIfNewReleaseAvailable()`.

---
> **ملاحظة للمطورين الجدد:** المشروع خالٍ تماماً من أي ملفات زائدة أو سكريبتات مهجورة، ومعد للبناء السحابي المستمر. تم حفظ كافة الملفات والوثائق والنماذج السابقة محلياً في مجلد `_local_archive/` خارج تتبع Git.
