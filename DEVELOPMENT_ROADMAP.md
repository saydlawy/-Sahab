# خارطة التطوير الشاملة — مشروع سحاب (Sahab)

**الإصدار:** 1.0 · **التاريخ:** 2026-11-11 · **مرجع الكود:** `origin/main @ a7e2e5e`
**النطاق:** تحليل + توصيات جاهزة للتنفيذ. لا يتضمن تعديل كود.

---

## 1. الملخص التنفيذي

مشروع سحاب تطبيق IPTV + مركز رياضة مبني على Kotlin/Compose/Media3 بأساس معماري سليم. الفحص السطري كشف **17 نقطة مؤكدة**:

| الفئة | العدد | الأثر |
|-------|-------|-------|
| 🔴 حرجة (P0) | 6 | تمنع الإصدار العام |
| 🟠 عالية (P1) | 6 | تُقصّر عمر التطبيق |
| 🟡 متوسطة (P2) | 5 | دين تقني يتراكم |

**القرار:** تجميد الميزات الجديدة حتى إغلاق P0 ثم P1 ثم إدخال الاختبارات.

---

## 2. تشخيص الحالة

| المجال | التقييم | التعليل |
|--------|---------|---------|
| الهيكل المعماري | 🟢 جيد | فصل `data/ui/util`، DI يدوي عبر `AppContainer` |
| إدارة الحالة | 🟢 جيد | `StateFlow` + `CompositionLocal` للتكيّف |
| الشبكة | 🟠 يحتاج | OkHttp سليم لكن بلا retry/backoff |
| الكاش | 🟠 يحتاج | `DiskCache` جيد لكن بلا LRU ولا حماية تصادم |
| الرياضة | 🔴 إصلاح | منطق `DateFilter` معطوب + ترحيل DB هش |
| المشغل | 🟢 جيد | إعدادات ممتازة، كاش فيديو يحتاج عزل |
| الأمان | 🔴 حرج | HTTP مسموح + اعتماد غير مشفّر + مفاتيح في APK |
| UI/UX | 🟢 جيد | 3 أنماط عرض فعّالة |
| CI/CD | 🟢 جيد | GitHub Actions نظيف، توقيع مؤتمت |
| الاختبارات | 🔴 غائبة | لا `src/test` ولا `src/androidTest` |

---

## 3. المخاطر P0 (حرجة)

| ID | الموقع | الوصف المؤكد من الكود | الأثر | الإجراء | التعقيد |
|----|--------|-----------------------|-------|---------|---------|
| P0-1 | `AndroidManifest.xml` | `usesCleartextTraffic=true` + تمرير user/pass عبر query | اعتراض بيانات الاشتراك على شبكة مفتوحة | `network_security_config.xml` + تحذير HTTP في Login | بسيط |
| P0-2 | `Prefs.kt` | server/user/pass نص صريح في DataStore | استخراج البيانات من device rooted | تشفير عبر `EncryptedFile`/Keystore | متوسط |
| P0-3 | `SahabPlayer.kt` | كاش VOD يزيل user/pass من المفتاح | تصادم بين حسابات نفس الخادم | Hash قصير من `(server+user)` في المفتاح | بسيط |
| P0-4 | `SportsViewModel` | `DateFilter` 7/14/28/60/90 يستدعي `getFixturesByDate(today)` فقط | عرض فارغ دائماً للنطاقات | `getFixturesByDateRange` للنطاقات | متوسط |
| P0-5 | `SportsViewModel` | `TODAY → getLiveMatches()` = مباريات جارية فقط، ليست كل مباريات اليوم | المستخدم لا يرى القادم/المنتهي | `getFixturesByDate(today)` + دمج مع `live=all` | بسيط |
| P0-6 | `SportsViewModel` (Room) | `fallbackToDestructiveMigration` + فحص محتوى لا Schema | فقدان بيانات عند تحديث Room | Migrations صريحة + فصل فحص Schema عن Seed | مركب |

---

## 4. المخاطر P1 (عالية)

| ID | الموقع | الوصف المؤكد من الكود | الأثر | الإجراء | التعقيد |
|----|--------|-----------------------|-------|---------|---------|
| P1-1 | `AppContainer` + APIs | لا retry/backoff على فشل الشبكة | فشل عابر = تجربة مكسورة | `OkHttp Interceptor` مركزي مع `RetryPolicy` | بسيط |
| P1-2 | `Repository.shortEpg` vs `dayEpg` | الأول `Semaphore(4)`، الثاني بلا قيد | سلسلة طلبات غير محدودة من `LiveScreen` | توحيد `epgGate` في كل مسارات EPG | بسيط |
| P1-3 | `Repository` | لا single-flight لمنع تكرار الطلب المتزامن لنفس المفتاح | `prewarmCatalog` + فتح شاشة = طلبان متزامنان | `ConcurrentHashMap<String, Deferred<T>>` | متوسط |
| P1-4 | `app/build.gradle.kts` | `isMinifyEnabled=false`, `isShrinkResources=false` | APK ضخم + سهل الهندسة العكسية | تفعيل R8 + `proguard-rules.pro` لـ serialization/Room | متوسط |
| P1-5 | `DiskCache` | لا LRU، ولا تحقق من نجاح `renameTo` | نمو بلا حدود + ملفات تالفة محتملة | حد حجم (200MB) + معالجة rename | متوسط |
| P1-6 | `Prefs.clearAccount()` | لا يحذف `Favs`، ولا يمسح `DiskCache` أو كاش الفيديو | بعد logout: مفضلة قديمة + كاش حساب آخر | توسيع `clearAccount` + `clearCatalog()` + `SimpleCache.release()` | بسيط |

---

## 5. المخاطر P2 (متوسطة / دين تقني)

| ID | الموقع | الوصف | الإجراء | التعقيد |
|----|--------|-------|---------|---------|
| P2-1 | `app/build.gradle.kts` | `targetSdk=34` بينما `compileSdk=36` | ترقية `targetSdk=35` ثم `36` بعد استقرار P0/P1 | متوسط |
| P2-2 | `TmdbApi` | مفتاح TMDB في `api_key=...` query parameter | ترقية إلى `Authorization: Bearer <token>` (v4) | بسيط |
| P2-3 | `SportsScreen` | `MatchStats` موجود في الموديل لكنه **غير معروض** | توسيع `LiveMatchHubContent` بالإحصائيات | متوسط |
| P2-4 | `ui/` | لا ضمان `key` فريدة في كل `items()` | تدقيق كل `LazyColumn/Grid` وإضافة `key = { it.id }` | بسيط |
| P2-5 | `PlayerScreen.kt` | منطق تشغيل معقد داخل Composable | استخراج `PlayerViewModel` لتسهيل الاختبار | مركب |

---

## 6. الديون التقنية الإضافية

| بند | الموقع | الوصف | الأولوية |
|-----|--------|-------|----------|
| سكريبتات Python مبعثرة | الجذر | 13 سكريبت `*.py` بلا توثيق "الأحدث المعتمد" | P2 |
| `MorphTvApi` بلا مفتاح | `MorphTvApi.kt` | خدمة غير موثقة قد تتوقف | P2 |
| `SportsSyncManager` | نفس الملف | منطق `syncLeague` مكرر لـ 5 بطولات — يمكن جدولته في `data class` | P2 |
| لا يوجد `CHANGELOG.md` | الجذر | تتبع الإصدارات يدوي | P3 |
| `WorldCup26Api` مؤقت | `WorldCup26Api.kt` | موجه لكأس 2026، سيتوقف 2027 | P3 |

---

## 7. خارطة الطريق المرحلية

| المرحلة | المحتوى | معيار الإغلاق |
|---------|---------|---------------|
| **0. تجهيز** | تجميد الميزات + `proguard-rules.pro` فارغ + `.gitignore` يُبعد `*.jks/*.b64/local.properties` | بناء نظيف على `main` |
| **1. إغلاق P0** | P0-1 → P0-6 بالترتيب | APK مثبّت + لا HTTP في logcat + كل DateFilter يرجع بيانات |
| **2. إغلاق P1** | P1-1 → P1-6 بالترتيب | APK أصغر ≥30% + لا ANR في prewarm/EPG |
| **3. الاختبارات** | Unit Tests لـ Repository/DateFilter/Prefs/applyFilters + Compose UI لـ Login/Sports | CI يفشل عند فشل اختبار |
| **4. الميزات + P2** | P2-1 → P2-5 ثم عودة للميزات | Play Console pre-launch نظيف |

---

## 8. تفصيل تنفيذي — المرحلة 1 (P0)

### 8.1 P0-1 — تقييد Cleartext
| الخطوة | الملف | الإجراء |
|--------|-------|---------|
| 1 | `res/xml/network_security_config.xml` (جديد) | `cleartextTrafficPermitted=false` افتراضي + استثناء لكل نطاق يدخله المستخدم |
| 2 | `AndroidManifest.xml` | استبدال `usesCleartextTraffic="true"` بـ `android:networkSecurityConfig="@xml/network_security_config"` |
| 3 | `LoginScreen.kt` | تحذير مرئي عند `http://` |
| 4 | اختبار | خادم http + خادم https |

### 8.2 P0-2 — تشفير بيانات الحساب
| الخطوة | الملف | الإجراء |
|--------|-------|---------|
| 1 | `app/build.gradle.kts` | إضافة `androidx.security:security-crypto` |
| 2 | `Prefs.kt` | `Account` فقط في `EncryptedSharedPreferences`, وبقية الحقول في DataStore |
| 3 | `Prefs.account` | قراءة غير متزامنة من المشفّر داخل `flow { }` |
| 4 | اختبار | login + إعادة تشغيل + التأكد أن الملف غير قابل للقراءة بـ `cat` |

### 8.3 P0-3 — عزل كاش الفيديو
| الخطوة | الملف | الإجراء |
|--------|-------|---------|
| 1 | `SahabPlayer.kt` | `CacheKeyFactory` يقبل `accountHash` |
| 2 | `AppContainer` | `accountHash = sha256(server + "|" + user).take(8)` عند login |
| 3 | `PlayerScreen.kt` | تمرير `accountHash` لـ `create(...)` |
| 4 | المفتاح | `/<kind>/<accountHash>/<id>.<ext>` |
| 5 | اختبار | حسابان على نفس الخادم + نفس movie id |

### 8.4 P0-4 + P0-5 — إصلاح DateFilter
| الخطوة | الملف | الإجراء |
|--------|-------|---------|
| 1 | `SportsApi` | مراجعة قيود `getFixturesByDateRange` (تتطلب league+season) |
| 2 | `SportsViewModel` | `TODAY` = استدعاء متوازٍ `getFixturesByDate(today)` + `getLiveMatches()` ثم `distinctBy` |
| 3 | `SportsViewModel` | `isRange` = حلقات على أيام النطاق بتوازٍ محدود (≤4) ثم دمج |
| 4 | اختبار | كل فلتر من 7→90 يعرض مباريات |

### 8.5 P0-6 — فصل Schema عن Seed
| الخطوة | الملف | الإجراء |
|--------|-------|---------|
| 1 | `SportsViewModel` | `isSchemaOutdated()` يفحص `PRAGMA table_info` + `user_version` فقط |
| 2 | `SportsViewModel` | `isSeedDataStale()` يفحص التواريخ والبطولات |
| 3 | النسخ | `if (isSchemaOutdated() || isSeedDataStale()) copyFromAssets()` |
| 4 | مستقبلاً | `object SportsMigrations { MIGRATION_1_2 ... }` + `.addMigrations(...)` |
| 5 | اختبار | تثبيت نسخة قديمة + update + بقاء `standings` |

---

## 9. تفصيل تنفيذي — المرحلة 2 (P1)

### 9.1 P1-1 — Retry/Backoff مركزي
| الخطوة | الملف | الإجراء |
|--------|-------|---------|
| 1 | `AppContainer` | إضافة `Interceptor` يعيد المحاولة عند 5xx/IO مرتين بفواصل 500ms, 1500ms |
| 2 | `XtreamApi` + `TmdbApi` | إزالة أي منطق retry محلي (لمنع التكرار) |

### 9.2 P1-2 — توحيد EPG semaphore
| الخطوة | الملف | الإجراء |
|--------|-------|---------|
| 1 | `Repository.dayEpg` | تغليف طلب الشبكة في `epgGate.withPermit { ... }` بنفس نمط `shortEpg` |

### 9.3 P1-3 — Single-flight لطلبات Repository
| الخطوة | الملف | الإجراء |
|--------|-------|---------|
| 1 | `Repository` | `ConcurrentHashMap<String, Deferred<T>>` في `cachedPersistent` |
| 2 | التدفق | `inFlight.getOrPut(key) { async { load().also { cache[key]=it; disk.put(...) } } }.await()` |

### 9.4 P1-4 — تفعيل R8 + ProGuard
| الخطوة | الملف | الإجراء |
|--------|-------|---------|
| 1 | `proguard-rules.pro` (جديد) | `-keep class kotlinx.serialization.** { *; }`, `-keep @Serializable class *`, `-keep class androidx.room.**`, `-keep class com.saydlawy.sahab.data.** { *; }` |
| 2 | `app/build.gradle.kts` | `isMinifyEnabled=true`, `isShrinkResources=true`, `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")` |
| 3 | اختبار | release APK → تشغيل كامل → لا `ClassNotFoundException` |

### 9.5 P1-5 — LRU + إصلاح rename
| الخطوة | الملف | الإجراء |
|--------|-------|---------|
| 1 | `DiskCache` | عند `put()`: احسب إجمالي الحجم، إن تجاوز 200MB احذف الأقدم `lastModified` حتى النزول |
| 2 | `DiskCache.put` | إذا فشل `renameTo` → `file.delete()` + `tmp.renameTo(file)`، وإن فشل مجدداً `tmp.delete()` |
| 3 | `DiskCache.locks` | حذف مفتاح Mutex بعد انتهاء العملية (لمنع تراكم) |

### 9.6 P1-6 — تنظيف كامل عند logout
| الخطوة | الملف | الإجراء |
|--------|-------|---------|
| 1 | `Prefs.clearAccount()` | إضافة `it.remove(Keys.Favs)` |
| 2 | `SettingsScreen` | بعد `clearAccount()` استدعاء `repo.clearCache(clearDisk=true)` + `SahabPlayer.clearMediaCache(context)` |
| 3 | `SahabPlayer` | إضافة `fun clearMediaCache(context)` يستدعي `SimpleCache.release()` ثم حذف المجلد |

---

## 10. معايير القبول لكل مرحلة

| المرحلة | معيار البناء | معيار الجودة | معيار الأمان |
|---------|-------------|--------------|--------------|
| P0 | `assembleRelease` نظيف + تثبيت على 3 أجهزة | لا crash في أول 30 دقيقة استخدام | `apksigner verify` + لا HTTP في logcat |
| P1 | APK ≤60% من الحجم الأصلي | لا ANR في prewarm/EPG | لا أسرار في Crash reports |
| الاختبارات | CI يفشل عند أي فشل | ≥40% coverage على `data/` | static scan لاكتشاف أسرار في الكود |
| P2 | `targetSdk=36` | Play Console pre-launch نظيف | مقاييس Privacy Sandbox واضحة |

---

## 11. تقييم المخاطر المتبقية بعد التنفيذ

| المخاطرة | بعد P0 | بعد P1 | بعد الاختبارات |
|----------|--------|--------|----------------|
| اعتراض بيانات الاعتماد | ✅ مُغلقة | ✅ | ✅ |
| فقدان بيانات Room | ✅ | ✅ | ✅ |
| تصادم كاش الفيديو | ✅ | ✅ | ✅ |
| تسريب مقاييس أداء | 🟠 جزئي | ✅ | ✅ |
| كسر R8 للـ serialization | — | ✅ (باختبار) | ✅ |
| انحدار عند تحديث Compose BOM | 🟠 | 🟠 | ✅ (باختبار) |

---

## 12. ما لا يمكن إثباته بدون اختبار تشغيلي

| البند | سبب عدم القدرة على الإثبات | الاختبار المطلوب |
|-------|---------------------------|-----------------|
| تسريب ذاكرة في `PlayerScreen` | لا يمكن قراءة دورة الحياة كاملة من مقتطفات | Memory Profiler تشغيل طويل |
| تعافي المشغل بعد انقطاع الشبكة | يحتاج محاكاة فعلية | قطع/إعادة Wi-Fi أثناء تشغيل |
| سلوك `DecoderFallback` | يختلف حسب SoC | اختبار على عدة أجهزة متنوعة |
| أداء D-Pad على TV | يحتاج جهاز فعلي | Android TV emulator/جهاز |
| صحة بيانات API-Football بعد 2026 | خارج السيطرة | تشغيل عند بدء الموسم |
| بناء APK على GitHub Actions | لا يوجد سجل بناء محلي | Push + انتظار workflow |
| سلامة `sports_db` في assets | يحتاج فتح SQLite فعلي | `sqlite3 sports_db .schema` |
| تكرار طلبات في `prewarmCatalog` | يحتاج تسجيل شبكة | `OkHttp Logging Interceptor` مؤقت |

---

## 13. الخلاصة والقرار

| السؤال | الجواب |
|--------|--------|
| هل المشروع جاهز للإصدار العام؟ | **لا** — 6 بنود P0 مفتوحة |
| هل يحتاج إعادة كتابة؟ | **لا** — الأساس سليم |
| أول بند يجب البدء به؟ | **P0-4** (منطق DateFilter) لأنه يعطّل وظيفة موجودة الآن |
| أسرع بند أثراً؟ | **P0-3** (عزل كاش الفيديو) |
| أعلى بند خطراً؟ | **P0-1 + P0-2** (كشف بيانات الاعتماد) |
| متى يمكن التفكير بميزات جديدة؟ | بعد إغلاق P0 + P1 + وجود اختبارات |
| ترتيب التنفيذ الموصى به | P0-4 → P0-5 → P0-3 → P0-1 → P0-2 → P0-6 → P1-1..P1-6 → اختبارات → P2 |

---

## 14. ربط كل بند بملفه الأصلي (مرجع سريع)

| ID | الملفات المتأثرة |
|----|------------------|
| P0-1 | `AndroidManifest.xml`, `res/xml/network_security_config.xml` (جديد), `ui/login/LoginScreen.kt` |
| P0-2 | `data/Prefs.kt`, `app/build.gradle.kts` |
| P0-3 | `ui/player/SahabPlayer.kt`, `SahabApp.kt`, `ui/player/PlayerScreen.kt` |
| P0-4 | `data/sports/SportsApi.kt`, `ui/sports/SportsViewModel.kt` |
| P0-5 | `ui/sports/SportsViewModel.kt` |
| P0-6 | `ui/sports/SportsViewModel.kt`, `data/sports/SportsDatabase.kt` |
| P1-1 | `SahabApp.kt` (Interceptor), `data/XtreamApi.kt`, `data/TmdbApi.kt` |
| P1-2 | `data/Repository.kt` |
| P1-3 | `data/Repository.kt` |
| P1-4 | `app/build.gradle.kts`, `proguard-rules.pro` (جديد) |
| P1-5 | `data/DiskCache.kt` |
| P1-6 | `data/Prefs.kt`, `ui/settings/SettingsScreen.kt`, `ui/player/SahabPlayer.kt` |
| P2-1 | `app/build.gradle.kts` |
| P2-2 | `data/TmdbApi.kt` |
| P2-3 | `ui/sports/SportsScreen.kt` |
| P2-4 | كل ملفات `ui/**` |
| P2-5 | `ui/player/PlayerScreen.kt` (استخراج ViewModel) |

---

**نهاية الوثيقة.**
