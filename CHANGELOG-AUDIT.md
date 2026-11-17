# سجل التدقيق والتنفيذ — مشروع سحاب (Sahab IPTV)

هذا الملف يوثق تنفيذ قائمة المهام الشاملة المحدّثة بتاريخ `2026-11-17`، مع تسجيل التحقق من كل ادعاء (`[خارج الجزء المرئي من المرفق]`)، القرارات الحرجة، ونتائج البناء والاختبار على **TCL Android TV** و**Samsung Galaxy A52s**.

---

## سجل القرارات الحرجة والتحقق من الادعاءات

| المعرّف | القرار / التحقق | السبب المختصر |
| :--- | :--- | :--- |
| **T-C01** | اعتماد `.fallbackToDestructiveMigration(dropAllTables = true)` على `CatalogDatabase` و`ChannelDatabase` مع تفعيل `exportSchema = true` وإضافة `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`؛ مع الإبقاء على `MIGRATION_1_2` في `SportsDatabase`. | بيانات الكتالوج والقنوات/EPG مخزنة ومزامنة بالكامل من السيرفر والسحابة، بينما بيانات الرياضة المحلية محفوظة بـ Migration صريحة. |
| **T-C02** | فصل `cloudMergeDone` إلى 3 أعلام مستقلة (`favoritesMergeDone`, `recentsMergeDone`, `prefsMergeDone`) + إضافة `CloudFetchResult` للتمييز بين `NotFound` و`Error` مع إعادة المحاولة (`2s / 5s / 10s`) وكتابة اختبارات وحدة في `CloudMergeCoordinatorTest.kt`. | منع مسح المفضلة أو السجل السحابي على Firestore عند انتهاء المهلة أو تعثر الشبكة وقت الإقلاع. |
| **T-C03** | إنشاء `SyncCoordinator.performCloudRestoreAndMerge()` واستدعاؤه من `AppRoot` عند الإقلاع ومن `CloudLoginScreen(onSuccess)` مع شاشة تحميل ومن ثم التوجيه مباشرة إلى `Routes.HOME` عند وجود اشتراك أو `Routes.LOGIN` عند عدم وجوده. | ضمان استعادة الاشتراكات والمفضلة فور الدخول السحابي دون إجبار المستخدم على إعادة إدخال بيانات Xtream. |
| **T-H11** | استبدال `showSplash` و`delay(1500L)` بـ `installSplashScreen()` و`SahabTheme.Splash` في `MainActivity` مع سقف أمان 3 ثوانٍ وتقليص مهلة استعادة الحساب السحابي إلى 4 ثوانٍ وتمرير الدمج في الخلفية عند الإقلاع. | إزالة التأخير الثابت `1.5s` وتركيب `AppRoot` من اللحظة `0` لتحسين زمن الإقلاع البارد. |
| **T-H01** | إزالة ابتلاع الأخطاء في `Repository.syncCatalog` وإرجاع `SyncResult.Failure` بدون ختم وقت المزامنة عند فشل أي استدعاء أساسي، مع قصر `syncMovies` على `Success` أو وجود بيانات محلية صالحة. | منع وسم مزامنة فاشلة كناجحة لمدة 24 ساعة ومنع إثراء كتالوج غير مزامن. |
| **T-H02** | فصل مفاتيح المزامنة في `Prefs` (`lastCatalogSyncTime`، `lastChannelSyncTime`، `lastEpgSyncTime`) مع ترحيل لمرة واحدة من `last_sync_time`، وتشغيل السلسلة الخمسية في `SyncCoordinator.runFullSync` بعد `LoginScreen.onLoggedIn`. | ضمان اكتمال القنوات وEPG والأفلام بعد تسجيل الدخول دون تداخل توقيت القنوات مع الكتالوج. |
| **T-H12 / M-20** | توحيد `clearAllLocalData()` وتنفيذ مسح القنوات والبرامج والأفلام وكاش القرص ثم تشغيل `runFullSync("switchAccount")` في `switchAccount`، واستدعاء `clearAllLocalData(true)` عند الخروج أو حذف آخر مصدر. | منع تسرب بيانات اشتراك قديم عند التبديل أو حذف آخر مصدر، مع إظهار مؤشر تبديل واضح. |
| **T-M24 / M-09** | تأكيد `setActiveAccount(cloudAccounts.first().id)` بعد حفظ الحسابات بترتيب عكسي في `SyncCoordinator`، وتأكيد ترتيب `sortedByDescending { it.updatedAt }.distinctBy { it.kind to it.id }.take(30)` مع اختبار وحدة. | منع تنشيط آخر حساب في القائمة خطأً عند الاستعادة، وضمان الاحتفاظ بأحدث موضع مشاهدة. |
| **T-H03** | خنق رفع سجل المشاهدة في `SahabApp.startAutoSync()` بـ `sample(180_000L)` مع `flushRecents()` الفوري عند خروج المشغل، الإيقاف المؤقت، أو `ON_STOP` في `PlayerScreen` و`MainActivity`. | تقليل كتابات Firestore أثناء المشاهدة المستمرة مع حفظ الموضع الأخير فور الخروج. |
| **T-H04** | [خارج الجزء المرئي] **تم التأكيد** (`SportsViewModel.kt:135, 270-285`): `init` كان يستدعي `enqueueManualSyncNow` قسرياً دائماً. تم قصر الجلب الفوري على فراغ القاعدة مع `SyncScheduler.enqueueIfIntervalElapsed` (فاصل 4 ساعات). | منع استنزاف Firestore في كل فتح لشاشة الرياضة مع الحفاظ على التحديث اليدوي والفوري عند الفراغ. |
| **T-M03** | جعل `TrendingMoviesSection` Stateless يعتمد على `movies` الممررة من `HomeScreen` وإزالة `LaunchedEffect` المكرر. | إلغاء الجلب المزدوج للأفلام المقترحة عند فتح الرئيسية. |
| **T-M22** | اشتقاق `coilClient` من `container.client.newBuilder().cache(null).readTimeout(30, SECONDS).build()`. | مشاركة `User-Agent` و`CleartextGuardInterceptor` و`RetryInterceptor` مع كاش Coil المستقل. |
| **T-M05** | توحيد الامتداد الافتراضي لأفلام/حلقات VOD في `DEFAULT_VOD_EXT = "mp4"` (`Models.kt`) عبر `Repository` و`CatalogSync` و`FirebaseCatalogService` و`HomeScreen`. | توحيد سلوك التشغيل بين السجل وشاشة التفاصيل والكتالوج (كان `CatalogSync.kt:316` يستخدم `m3u8`). |
| **T-M04** | دعم `aliases` ووثائق `redirectTo` في `FirebaseCatalogService` وربط `pending:{streamId}` بوثيقة `tmdb:{tmdbId}` في `CatalogSync.enrichOne` و`saveCatalogItem` مع اختبار `CatalogIdAliasTest`. | ضمان العثور على الوثائق المُثراة عند البحث بمعرّف `pending:` أو `tmdb:`. |
| **T-M01** | [خارج الجزء المرئي] **تم التأكيد** (`PlayerViewModel.kt:276`): كان يطابق رمز الخطأ بـ Regex نصي. تم استبداله بتتبع سلسلة `cause` لاستخراج `HttpDataSource.InvalidResponseCodeException.responseCode` في `extractHttpResponseCode` مع `PlayerHttpErrorTest`. | منع التباس أرقام المسارات مثل `/403.mp4` مع خطأ تعارض الأجهزة HTTP 403. |
| **T-M02** | [خارج الجزء المرئي] **تم التأكيد** (`PlayerViewModel.kt:122-138` و`PlayerScreen.kt:198, 432, 578`): كان `startEpgLoop` يجلب `shortEpg` كل دقيقة إلى `ui.epg` بينما الواجهة تقرأ من Room `livePrograms` فقط. تم إيقاف الحلقة غير المستخدمة. | إيقاف استدعاءات `shortEpg` المتكررة غير المعروضة في المشغل. |
| **T-M08** | إضافة `writeWithRetry` (3 محاولات بتراجع `1s / 2s`)، وإنشاء وثيقة المستخدم الجذرية `users/{uid}` عبر `SetOptions.merge()` (بما فيها `saveFcmToken`)، وبث أخطاء المزامنة المتكررة عبر `syncWriteErrors`. | منع ضياع أخطاء الكتابة على Firestore بصمت أو فشل `update` عند غياب وثيقة المستخدم الجذرية. |
| **T-M10** | إضافة `Prefs.updateThemeCustomization` الذرية عبر `store.edit` وتحديث `ThemeCustomizationPanel` لاستخدامها بدل `current.copy(...)` من لقطة الـ Composition. | منع الكتابة المتزامنة من محو تعديلات تخصيص الثيم الأخرى. |
| **T-M28** | قصر `M3uParser` على روابط `http://` و`https://` وإعادة ضبط الحالة على الأسطر التالفة مع اختبارات `M3uParserTest`. | منع قبول روابط مكسورة أو بروتوكولات غير مدعومة في قوائم M3U. |
| **T-H05 / M-25** | إضافة `onNewToken` وتخزين/مزامنة توكن FCM وأيقونة `ic_stat_notification` أحادية اللون ومعرف إشعار فريد، ودعم `data`-only مع طلب `POST_NOTIFICATIONS` على API 33+ برسالة سياق؛ وتأطير `requestFocus()` بـ `withFrameNanos` في `CloudLoginScreen` و`PlayerScreen` و`SportsScreen` و`StandingsTab`. | ضمان وصول الإشعارات بدون طمس بعضها وتفادي أخطاء التركيز قبل القياس الأول للواجهة. |
| **T-H09** | [خارج الجزء المرئي] **تم التأكيد** (`UserSyncService.kt:33-37`): كان يشتق مفتاح AES من `SHA-256("sahab_cloud_v1_$uid")`. تم تطوير `CloudAccountCrypto` (v2 عبر PBKDF2 100k + salt عشوائي 16 بايت من Sync PIN يختاره المستخدم في الإعدادات) مع ترحيل تلقائي من `enc_v1:` إلى `enc_v2:`؛ وبدون Sync PIN يُخزّن الاشتراك محلياً فقط عبر Android Keystore. **تنبيه UX:** استعادة الاشتراك المشفر على جهاز ثانٍ تتطلب إدخال Sync PIN في نافذة الاستعادة. | منع اشتقاق مفتاح التشفير من UID المكشوف في مسار الوثيقة وتغطيته بـ `UserSyncEncryptionTest`. |
| **T-H10** | [خارج الجزء المرئي] **تم التأكيد**: لم يكن `firebase-appcheck` مضافاً ولا يوجد ملف `firestore.rules` في الريبو. تم تفعيل `PlayIntegrity` في `release` و`Debug` في `debug` وتأليف `firestore.rules` لحماية `users` و`global_catalog` (رفض التلاعب بالحقوق/الميتاداتا الأصلية). **خطوة يدوية للمالك:** نشر `firestore.rules` وتفعيل App Check Enforcement من Firebase Console. | حماية `global_catalog` ومجموعات التطبيق من الكتابة غير المصرح بها أو التلاعب بحقول الإثراء. |
| **T-H06** | إضافة `accountId` إلى `FavItem` و`RecentItem` مع ترحيل العناصر الفارغة عند الإقلاع وتصفية كل الشاشات (`FavoritesScreen`, `BrowseScreen`, `HomeScreen`, `LiveScreen`, `MovieDetailScreen`, `SeriesDetailScreen`) بـ `activeAccountId` وحصر الدمج بـ `Triple(accountId, kind, id)` و30 سجل لكل حساب. | عزل المفضلة وسجل المشاهدة تماماً بين الاشتراكات المتعددة مع الحفاظ على تاريخ الترقية القديمة. |
| **T-H08 / M-27** | [خارج الجزء المرئي] **تم التأكيد** (`.github/workflows/build-apk.yml`): السطر 60 كان يحتوي على `echo ...` شارد داخل `args` لـ `Dependency-Check_Action` (`continue-on-error: true`)، والأسرار في السطر 93 ممررة مباشرة بدل `env:`، وكل الـ Actions بوسوم عائمة `@v4/@main/@v1`، وشرط Firebase Distribution يفحص `env.` قبل تعريفه. تم إصلاح جميعها وتثبيت الـ 7 Actions بـ commit SHA كامل وفصل `Validate google-services.json` كخطوة فاشلة حقيقياً. | تأمين سلسلة البناء ومنع بناء نسخة بلا `google-services.json` صالح. |
| **T-H07** | تنظيف `app/proguard-rules.pro`: دمج الكتل الأربع المكررة لـ DataStore/DataStorage وحذف القواعد الشاملة (`com.google.firebase.**`, `com.google.android.gms.**`, `androidx.appcompat.**`, `androidx.media3.**`) مع الإبقاء على قواعد `@Serializable` وRoom وWorkers و`SourceFile,LineNumberTable` لـ Crashlytics. | تقليص حجم APK مع الاعتماد على consumer rules الرسمية للمكتبات. |
| **T-M11 / M-12** | **M-11:** حذف السطر المكرر `security-crypto:1.1.0-alpha06` وتوحيد الاعتماد على `libs.androidx.security.crypto` (`1.1.0` الثابت لمطابقة `MasterKey.Builder` في `ApiKeyManager`). **خطة هجرة M-11:** استبدال `EncryptedSharedPreferences` تدريجياً بـ DataStore + تشفير AES-GCM بمفتاح Android Keystore على غرار `CloudAccountCrypto`. **خطة هجرة M-12 (مؤجلة):** الانتقال من `play-services-auth:20.7.0` (`GoogleSignIn`) إلى Jetpack `androidx.credentials:credentials` + `GetGoogleIdOption` مع إبقاء التدفق الحالي عاملاً الآن لتفادي كسر الدخول السحابي. | توحيد التبعيات وحفظ استقرار تسجيل الدخول والتخزين المشفر مع خطة تحديث واضحة. |
| **T-M14 / M-26 / M-15** | نقل `firebase-appdistribution` إلى `debugImplementation` عبر `ReleaseUpdateChecker` وحقن `BuildConfig.DEBUG`؛ إضافة `firebase-crashlytics:3.0.8` مع رفع ملفات الـ mapping؛ تفعيل `lint { abortOnError = true; checkReleaseBuilds = true; baseline = file("lint-baseline.xml") }` وإصلاح أخطاء `@OptIn(UnstableApi::class)` في `PlayerScreen.kt`. | إزالة نافذة المختبرين من إصدارات Production، تفعيل تتبع الأعطال، وضبط بوابة Lint صارمة. |
| **T-M13 / L-04** | ضبط fallback لـ `versionCode` بالطابع الزمني بالدقائق مع أولوية `GITHUB_RUN_NUMBER`؛ ورفع `targetSdk` مرحلياً إلى `35` ثم `36` مع التحقق من `enableEdgeToEdge` و`safeDrawingPadding` و`imePadding` وسلوك الرجوع. | الامتثال لسياسات Google Play الحديثة والتأكد من Edge-to-Edge على Android 15/16. |

---

## جدول تتبع الدفعات والاختبارات

| المعرّف | الدفعة | الحالة | Commit | نتيجة الاختبار على التلفاز (`10.19.124.138:5555`) | نتيجة الاختبار على A52s (`RZCT504YSEM`) |
| :--- | :---: | :---: | :---: | :--- | :--- |
| `T-C01` | 1 | تم (Run #203) | `21f5b45` | ترقية `201 -> 203` بدون Crash أو `SQLiteException` (`TotalTime: 1257ms`) | ترقية `153 -> 203` بدون Crash أو `SQLiteException` (`TotalTime: 393ms`) |
| `T-C02` | 1 | تم + Unit tests | `88f767a` | نجح الإقلاع بلا مسح؛ مغطى بـ `CloudMergeCoordinatorTest` | نجح الإقلاع والـ Sync المحلي بلا أخطاء؛ مغطى بـ `CloudMergeCoordinatorTest` |
| `T-C03` | 1 | تم (Run #203) | `a61c279` | إقلاع مباشر للرئيسية (`startup_local`) بلا Crash | إقلاع بلا Crash (`SahabEpgSync: [startup_local]`) |
| `T-H11` | 2 | تم (Run #206) | `4d17e1d`, `72bef49` | إقلاع بارد مباشر للرئيسية `TotalTime: 2079ms` (Run #208: `1726ms`)، لا تأخير 1.5s | إقلاع ناجح بلا أخطاء (`WaitTime: 3020ms` مع قفل الشاشة) |
| `T-H01` | 2 | تم (Run #206) + Unit test | `d309fe6` | نجح في Run #206 (`RepositorySyncCatalogTest` و`step 2/5`) | نجح في Run #206 (`step 2/5` و`step 3/5`) |
| `T-H02` | 2 | تم (Run #206) | `3faad95` | السلسلة الخمسية كاملة (`step 1..5`, `epgIds=226 upserted=7304 errors=0`) | السلسلة الخمسية كاملة (`1529` قناة، `251` مُثراة، `7304` برنامج EPG، `0` أخطاء) |
| `T-H12 / M-20` | 2 | تم (Run #206) | `79f53a1` | الكود مفعّل والواجهة متكاملة | الكود مفعّل و`clearAllLocalData` موحدة |
| `T-M24 / M-09` | 2 | تم (Run #206) + Unit test | `9b15284` | نجح اختبار الوحدة `mergeRecents_ordersByUpdatedAtDescBeforeDistinctAndCapsAt30` | نجح في Run #206 |
| `T-H03` | 3 | تم (Run #208) | `ed90e94` | تشغيل حلقة `يوميات ونيس` وخروج سليم بلا Crash أو كتابات مفرطة | إقلاع ومزامنة محلية سليمة بلا Crash |
| `T-H04` | 3 | تم (Run #208) | `41df825` | فتح شاشة الرياضة + فحص `logcat` يؤكد عدم تشغيل `MonthlySyncWorker` قسرياً | المزامنة التلقائية تحترم فاصل 4 ساعات بلا أخطاء |
| `T-M03` | 3 | تم (Run #208) | `9ccc81e` | عرض الرئيسية والأفلام المقترحة بجلب واحد فقط | الرئيسية مستقرة بلا أخطاء |
| `T-M22` | 3 | تم (Run #208) | `ad1aa40` | شعارات القنوات ظهرت بوضوح عبر `coilClient` الموحد | عميل الصور الموحد فعّال بلا أخطاء |
| `T-M05` | 3 | تم (Run #208) | `25b498f` | تشغيل VOD متسق عبر `DEFAULT_VOD_EXT = "mp4"` | متوافق بلا أخطاء |
| `T-M04` | 3 | تم (Run #208) + Unit test | `8e46a1f` | نجحت اختبارات `CatalogIdAliasTest` ضمن Run #208 (48/48) | متوافق بلا أخطاء |
| `T-M01 / M-02` | 3 | تم (Run #208) + Unit test | `f1bc851`, `8219681` | نجح `PlayerHttpErrorTest` + تشغيل المشغل بدون حلقة `shortEpg` غير المستخدمة | نجح ضمن Run #208 (48/48) |
| `T-M08` | 3 | تم (Run #208) | `963658b` | المزامنة السحابية مجهزة بإعادة المحاولة ووثيقة الجذر | متوافق بلا أخطاء |
| `T-M10` | 3 | تم (Run #208) | `a858d92` | تحديث الثيم ذرياً عبر `Prefs.updateThemeCustomization` | متوافق بلا أخطاء |
| `T-M28` | 3 | تم (Run #208) + Unit test | `50a5342` | نجحت اختبارات `M3uParserTest` ضمن Run #208 (48/48) | نجح ضمن Run #208 |
| `T-H05 / M-25` | 4 | تم (Run #212) | `42fbc85`, `f454df6` | تم التنقل بالـ D-Pad عبر الرئيسية والإعدادات دون خطأ تركيز (`logcat` خالٍ من `FATAL`) | تم التثبيت والإقلاع (`WaitTime: 3022ms`, `versionCode=212`, تصريح `POST_NOTIFICATIONS` مضاف) |
| `T-H09` | 4 | تم (Run #212) + Unit test | `34e217f` | نجح `UserSyncEncryptionTest` في CI؛ فحص شاشة الحساب السحابي بالإعدادات بلا Crash | نجح `UserSyncEncryptionTest` + التثبيت والإقلاع السليم |
| `T-H10` | 4 | تم (Run #212) — يتطلب نشر القواعد يدوياً | `56f4c60` | تم التثبيت مع `AppCheckInstaller` و`firestore.rules` دون أخطاء في `logcat` | تم التثبيت مع `AppCheckInstaller` (`APK = 8,292,800 bytes`) |
| `T-H06` | 4 | تم (Run #212) + Unit test | `372d068`, `bed28f9` | نجح `CloudMergeCoordinatorTest`؛ سجل `تابع المشاهدة` هاجر وظهرت عناصره سليمة | نجح `CloudMergeCoordinatorTest` والإقلاع السليم |
| `T-H08 / M-27` | 5 | تم (Run #214) | `ed197c2`, `2ddac7d` | نجحت سلسلة CI كاملة مع تثبيت الـ 7 Actions بـ SHA كامل وفصل فحص `google-services.json` | متوافق |
| `T-H07` | 5 | تم (Run #215/216) | `c8e956f` | حجم APK انخفض من `8,292,800` إلى `7,166,484` بايت (-13.6%)؛ جولة كافة الشاشات بلا `ClassNotFoundException` | حجم APK `7,166,484` بايت؛ إقلاع وتشغيل مستقر بلا `ClassNotFoundException` |
| `T-L04` | 5 | تم (Run #215) | `6a769b3` | `versionCode = GITHUB_RUN_NUMBER` (215/216) مع fallback زمني بالدقائق | `versionCode=216` مثبت بنجاح |
| `T-M11 / M-12` | 5 | تم / موثق | `54a5b74` | توحيد `androidx.security:security-crypto:1.1.0` وتوثيق خطتي هجرة M-11 وM-12 | لا تكرار تبعيات في البناء |
| `T-M14` | 5 | تم (Run #215) | `d65ea45` | اختفت نافذة App Distribution من نسخة Release على التلفاز (`BuildConfig.DEBUG` فقط) | اختفت نافذة المختبرين وإقلاع نظيف |
| `T-M26` | 5 | تم (Run #215/216) | `98289b0` | `FirebaseCrashlytics 19.4.2` مفعّل في `logcat` مع رفع mapping في Release | `FirebaseCrashlytics 19.4.2` مسجّل ومفعّل في `logcat` |
| `T-M15` | 5 | تم (Run #215/216) | `66847d7` | بوابة `lintRelease` صارمة (`abortOnError = true`) نجحت مع `app/lint-baseline.xml` | بوابة Lint مجتازة بدون أخطاء |
| `T-M13` | 5 | تم على مرحلتين (35 ثم 36) | `40d7fe5`, `9e2cebf` | `targetSdk=35` (`Run #215`, `TotalTime: 1880ms`) ثم `targetSdk=36` (`Run #216`) + رجوع سليم | `targetSdk=36`, إقلاع بارد `TotalTime: 540ms` مع Edge-to-Edge |

---

## LIVE-01 — إصلاح تجمّد الصورة مع استمرار الصوت في البث المباشر (2026-11-17)

### التشخيص (مُثبت ميدانياً على TCL TV قبل أي تعديل)
- القناة `1920x1080 video/hevc 50fps` + `AAC 48kHz`، الديكودر `OMX.amlogic.hevc.decoder.awesome2` (`feature-adaptive-playback=0`, `feature-tunneled-playback=0`, `nBufferCountActual ... failed: -1010`).
- قبل التجمد: `AudioTrack ... device stall time corrected` / `retrograde timestamp time corrected` ثم موجات `r:0, drop frame`.
- من `16:42:00` إلى `16:52:04`: `VDA` ثابت على `INs=2716/2722, OUTs=2699/2707 {IN=6/8, OUT=4/12[kAtDevice(8) kAtClient(4)]}` بدون أي `PlaybackException`، بينما `c2.android.aac.decoder` و`audio_hw_primary` يواصلان العمل → صورة متجمدة وصوت مستمر. الإيقاف/الاستئناف لم يُصلحه؛ فقط تحرير الـ codec (تبديل القناة).

### القرارات والتعديلات
| البند | الملف | التعديل | السبب |
| :--- | :--- | :--- | :--- |
| مراقب تجمّد الفيديو | `ui/player/LiveVideoStallWatchdog.kt` (جديد) | فحص كل ثانية لـ `videoDecoderCounters.renderedOutputBufferCount`؛ إذا لم يُرسم أي إطار `4.5s` بينما الموضع (ساعة الصوت) تقدّم `≥3s` → `stop()` (يحرر الـ codec العالق) ثم `seekToDefaultPosition()` + `prepare()` على حافة البث. من التعثّر الثاني يُبدَّل `m3u8 ↔ ts`. حد أقصى 3 محاولات/دقيقتين لكل قناة. | الديكودر لا يُطلق خطأ، لذا `onPlayerError` لا يعمل أبداً في هذه الحالة. الحد الأقصى يمنع حلقة إعادة تشغيل في قنوات الصورة الثابتة. |
| ربط المراقب | `PlayerScreen.kt`, `PlayerViewModel.kt` (`switchLiveFormatAfterStall`), `LivePreviewPanel.kt` | تفعيل المراقب في المشغل الكامل ومعاينة البث المباشر. | تغطية الشاشتين اللتين تشغّلان بثاً مباشراً. |
| تثبيت سرعة البث | `SahabPlayer.kt` | `setLiveMinSpeed(1f)` + `setLiveMaxSpeed(1f)` للبث المباشر. | تغيير السرعة لملاحقة حافة البث يُربك ساعة `AudioTrack` ويسبب إسقاط إطارات. |
| مخازن البث المباشر | `SahabPlayer.kt` | `min/max = 10s/30s`، `bufferForPlayback = 1000ms` (كان `550`)، `afterRebuffer = 2500ms` (كان `1400`)، `setBackBuffer(0,false)`. | بدء سريع مع هامش كافٍ لقنوات 50/60fps وHEVC/4K؛ ذاكرة ثابتة في المشاهدة الطويلة. |
| انضمام الفيديو | `SahabPlayer.kt` | `setAllowedVideoJoiningTimeMs(3000)`. | يسمح للفيديو باللحاق بالصوت عند البدء دون إسقاط إطارات البداية. |
| اختيار المسارات | `SahabPlayer.kt` | `DefaultTrackSelector` مع السماح بالتكيّف بين أنواع ترميز الفيديو/الصوت، معدلات العينة، وعدد القنوات الصوتية، وتجاوز القيود عند الحاجة. | التعامل الصحيح مع كل الجودات والفريمات والصوتيات. اختيار الجودة اليدوي في الإعدادات ما زال يعمل (`trackSelectionParameters`). |
| شبكة بلا كاش للبث | `SahabPlayer.kt` | `client.newBuilder().cache(null)` للبث المباشر فقط (نفس الـ connection pool). | منع قراءة `.m3u8` قديمة من كاش القرص `50MB` وتأخير الكتابة على القرص. |
| قوائم 10,000+ قناة | `LiveChannelList.kt`, `PlayerChannelsDrawer.kt`, `PlayerGestures.kt` | مفتاح `id_index` (يمنع الانهيار عند تكرار المعرّف) + `contentType` ثابت + مهلة `180ms` قبل استعلام EPG لكل صف. | الصفوف العابرة أثناء التمرير السريع لا تستعلم Room. |
| `FLAG_ALLOW_NON_IDR_KEYFRAMES` | `SahabPlayer.kt` | **أُبقي كما هو (قرار)**. | يؤثر على `H.264` فقط (القناة المعطلة `HEVC`)، وإزالته قد تُظهر شاشة سوداء في قنوات H.264 التي لا ترسل إطارات IDR. |

### الاختبار
- **GitHub Actions:** نجح البناء في `Run #218` (الـ Commit `cf36797`، حجم `Sahab.apk` = `7,166,488` بايت، `versionCode=218`, `targetSdk=36`).
- **Samsung Galaxy A52s (`RZCT504YSEM`):** تم التثبيت والإقلاع البارد (`TotalTime: 454ms`) واختبار شاشة البث المباشر ("الكل" — `1529` قناة، وتمرير سريع، وتشغيل البث المباشر الفعلي بالصورة والصوت `AAC` مع `c2.qti.avc.decoder` دون أي أخطاء أو `FATAL` في `logcat`).
- **TCL Android TV (`10.19.124.138:5555`):** لم يُلمس ولم يُختبر التزاماً بتوجيه المستخدم الصريح بعدم الاختبار على التلفاز إلا بعد موافقته.

---

## الدفعات 6 و7 و8 — واجهة المستخدم، إعادة الهيكلة، النظافة، والفرص (2026-11-17)

| المعرّف | الدفعة | الحالة | Commit | الملخص والقرار |
| :--- | :--- | :--- | :--- | :--- |
| `T-M06 / M-29` | 6 | منفذ | `77ff8aa` | إزالة القنوات المميزة الوهمية والوقت الوهمي `"٠٨:٠٠ م"` وإخفاء الأقسام الفارغة |
| `T-M07` | 6 | منفذ | `8c1a20a` | إضافة مدخل الدخول السحابي بالإعدادات مع الرجوع والاستعادة عبر `performCloudRestoreAndMerge()` |
| `T-M16 / M-17` | 6 | منفذ | `6e0f72f`, `493d446` | رسالة خطأ وزر إعادة بالمعاينة + تعطيل/إخفاء التقديم والسرعة للبث المباشر |
| `T-M21` | 6 | منفذ | `434a5c9` | دعم `Role` و`contentDescription` وحالات TalkBack في `Glass.kt` والإعدادات |
| `T-M19` | 6 | منفذ | `e350f59` | إزالة `noCompress += "sports_db"` وبقايا البذرة الميتة للرياضة |
| `T-L01` | 7 | تم | `L-01` | حذف `"goal-api.com"` من `STRICT_HTTPS_DOMAINS` بعد التحقق من عدم استخدامه |
| `T-L02` | 7 | تم | `L-02` | حذف `LiveMatchesBar` غير المستدعى من `HomeContentRows.kt` |
| `T-L03` | 7 | تم | `L-03` | حذف `TopBarIconButton` و`TopBarBellButton` غير المستخدمين من `TopBarWidgets.kt` |
| `T-L05` | 7 | تم | `L-05` | تهيئة `ApiKeyManager` (`EncryptedSharedPreferences`) على `appScope` (`Dispatchers.IO`) بدل خيط الواجهة |
| `T-L06` | 7 | مؤجل موثّق | — | الإبقاء على `Palette` كما هي لتفادي كسر الثيم (نقل تدريجي مستقبلي إلى `CompositionLocal`) |
| `T-M18` | 7 | تم | `M-18` | نقل تحميل الرئيسية إلى `HomeViewModel` حقيقي + حفظ نافذة آمنة لـ `PlayRequest` في `filesDir/pending_playback.json` للتعافي بعد Process Death مع رجوع آمن عند `null` |
| `T-M23` | 7 | تم | `M-23` | إعادة تسمية `HomeHeroBanner.kt` إلى `HomeQuickCategories.kt` وحذف 135 سطر استيراد غير مستخدم/مكرر |
| `T-O01` | 8 | تم | `O-01` | تدوير مفاتيح Gemini تلقائياً عند `429` في `ApiKeyManager.orderedKeysForRotation()` و`AiStoryShared.kt` |
| `T-O02` | 8 | تم | `O-02` | تفعيل توليد قصة AI في `SeriesDetailScreen` (`ContentType.SERIES`) مع حفظ ومزامنة `global_catalog` |
| `T-O03` | 8 | تم | `O-03` | إضافة `SahabPlaybackService` (`MediaSessionService`) لصوت البث المباشر بالخلفية + دعم PiP على الهاتف فقط (`!isTv`) |
| `T-O04` | 8 | مُتحقّق ومنجز مسبقاً | `SettingsHomeChannelsPanel.kt` | واجهة `HomeChannelsSource.CUSTOM` (`CustomChannelPickerDialog`) مكتملة بدعم D-Pad وقائمة القنوات المحلية |
| `T-O05` | 8 | تم | `O-05` | فاحص تحديثات خفيف عبر GitHub Releases (`GithubReleaseUpdater` + `GithubUpdatePrompt`) يقارن `versionCode` دون فرض التثبيت |

### نتيجة البناء والاختبار (الدفعات 6 و7 و8 — `Run #220`)
- **GitHub Actions (`Run #220`):** نجح البناء والترجمة واختبارات الوحدة وبوابة `lintRelease` (الـ Commit `91117d3`, حجم `Sahab.apk` = `7,520,157` بايت، `versionCode=220`, `targetSdk=36`).
- **الهاتف (`Samsung Galaxy A52s - RZCT504YSEM`):**
  - تم تثبيت `versionCode=220` وتشغيله بنجاح دون أي `FATAL` أو `ClassNotFoundException` في `logcat`.
  - الشاشة الرئيسية (`HomeViewModel` + `HomeQuickCategories`): عرضت `تابع المشاهدة`، `القنوات المباشرة`، و`أفلام مقترحة لك` بسلاسة.
  - المشغّل (`PlayerScreen` + `LIVE-01` + `T-O03`): تشغيل فوري لقناة `MBC 1` بدقة `1080p FHD` وصوت `AAC`، والانتقال تلقائياً إلى وضع **صورة داخل صورة (`PiP`)** عند الضغط على زر الرئيسية.
- **التلفاز (`TCL Android TV`):** **لم يتم الاختبار أو التثبيت عليه** بناءً على توجيهك الصريح، وبانتظار موافقتك لاختبار النسخة `220` عليه.
