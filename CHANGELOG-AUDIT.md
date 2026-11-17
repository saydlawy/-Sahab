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

---

## جدول تتبع الدفعات والاختبارات

| المعرّف | الدفعة | الحالة | Commit | نتيجة الاختبار على التلفاز (`10.19.124.138:5555`) | نتيجة الاختبار على A52s (`RZCT504YSEM`) |
| :--- | :---: | :---: | :---: | :--- | :--- |
| `T-C01` | 1 | تم (Run #203) | `21f5b45` | ترقية `201 -> 203` بدون Crash أو `SQLiteException` (`TotalTime: 1257ms`) | ترقية `153 -> 203` بدون Crash أو `SQLiteException` (`TotalTime: 393ms`) |
| `T-C02` | 1 | تم + Unit tests | `88f767a` | نجح الإقلاع بلا مسح؛ مغطى بـ `CloudMergeCoordinatorTest` | نجح الإقلاع والـ Sync المحلي بلا أخطاء؛ مغطى بـ `CloudMergeCoordinatorTest` |
| `T-C03` | 1 | تم (Run #203) | `a61c279` | إقلاع مباشر للرئيسية (`startup_local`) بلا Crash | إقلاع بلا Crash (`SahabEpgSync: [startup_local]`) |
| `T-H11` | 2 | تم (Run #206) | `4d17e1d`, `72bef49` | إقلاع بارد مباشر للرئيسية `TotalTime: 2079ms` (بدل `1257ms + 1500ms = 2757ms`)، لا تأخير 1.5s | إقلاع ناجح بلا أخطاء (`WaitTime: 3020ms` مع قفل الشاشة؛ قيد القياس بعد الفتح في الدفعة 9) |
| `T-H01` | 2 | تم (Run #206) + Unit test | `d309fe6` | نجح في Run #206 (`RepositorySyncCatalogTest` و`step 2/5`) | نجح في Run #206 (`step 2/5` و`step 3/5`) |
| `T-H02` | 2 | تم (Run #206) | `3faad95` | السلسلة الخمسية كاملة (`step 1..5`, `epgIds=226 upserted=7304 errors=0`) | السلسلة الخمسية كاملة (`1529` قناة، `251` مُثراة، `7304` برنامج EPG، `0` أخطاء) |
| `T-H12 / M-20` | 2 | تم (Run #206) | `79f53a1` | الكود مفعّل والواجهة متكاملة؛ التبديل الكامل بحسابين قيد التحقق إذا توفر اشتراك ثانٍ | الكود مفعّل و`clearAllLocalData` موحدة |
| `T-M24 / M-09` | 2 | تم (Run #206) + Unit test | `9b15284` | نجح اختبار الوحدة `mergeRecents_ordersByUpdatedAtDescBeforeDistinctAndCapsAt30` | نجح في Run #206 |
| `T-H03` | 3 | قيد البناء/الاختبار | `ed90e94` | بانتظار بناء الدفعة 3 | بانتظار بناء الدفعة 3 |
| `T-H04` | 3 | قيد البناء/الاختبار | `41df825` | بانتظار بناء الدفعة 3 | بانتظار بناء الدفعة 3 |
| `T-M03` | 3 | قيد البناء/الاختبار | `9ccc81e` | بانتظار بناء الدفعة 3 | بانتظار بناء الدفعة 3 |
| `T-M22` | 3 | قيد البناء/الاختبار | `ad1aa40` | بانتظار بناء الدفعة 3 | بانتظار بناء الدفعة 3 |
| `T-M05` | 3 | قيد البناء/الاختبار | `25b498f` | بانتظار بناء الدفعة 3 | بانتظار بناء الدفعة 3 |
| `T-M04` | 3 | قيد البناء/الاختبار | `8e46a1f` | بانتظار بناء الدفعة 3 (`CatalogIdAliasTest`) | بانتظار بناء الدفعة 3 |
| `T-M01 / M-02` | 3 | قيد البناء/الاختبار | `f1bc851` | بانتظار بناء الدفعة 3 (`PlayerHttpErrorTest`) | بانتظار بناء الدفعة 3 |
| `T-M08` | 3 | قيد البناء/الاختبار | `963658b` | بانتظار بناء الدفعة 3 | بانتظار بناء الدفعة 3 |
| `T-M10` | 3 | قيد البناء/الاختبار | `a858d92` | بانتظار بناء الدفعة 3 | بانتظار بناء الدفعة 3 |
| `T-M28` | 3 | قيد البناء/الاختبار | pending | بانتظار بناء الدفعة 3 (`M3uParserTest`) | بانتظار بناء الدفعة 3 |
