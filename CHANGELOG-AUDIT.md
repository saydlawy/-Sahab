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

---

## جدول تتبع الدفعات والاختبارات

| المعرّف | الدفعة | الحالة | Commit | نتيجة الاختبار على التلفاز (`10.19.124.138:5555`) | نتيجة الاختبار على A52s (`RZCT504YSEM`) |
| :--- | :---: | :---: | :---: | :--- | :--- |
| `T-C01` | 1 | تم (Run #203) | `21f5b45` | ترقية `201 -> 203` بدون Crash أو `SQLiteException` (`TotalTime: 1257ms`) | ترقية `153 -> 203` بدون Crash أو `SQLiteException` (`TotalTime: 393ms`) |
| `T-C02` | 1 | تم + Unit tests | `88f767a` | نجح الإقلاع بلا مسح؛ مغطى بـ `CloudMergeCoordinatorTest` | نجح الإقلاع والـ Sync المحلي بلا أخطاء؛ مغطى بـ `CloudMergeCoordinatorTest` |
| `T-C03` | 1 | تم (Run #203) | `a61c279` | إقلاع مباشر للرئيسية (`startup_local`) بلا Crash | إقلاع بلا Crash (`SahabEpgSync: [startup_local]`) |
| `T-H11` | 2 | قيد البناء/الاختبار | `4d17e1d` | خط الأساس قبل التحسين: `1257ms` | خط الأساس قبل التحسين: `393ms` |
| `T-H01` | 2 | قيد البناء/الاختبار | `d309fe6` | بانتظار بناء الدفعة 2 + `RepositorySyncCatalogTest` | بانتظار بناء الدفعة 2 |
| `T-H02` | 2 | قيد البناء/الاختبار | `3faad95` | بانتظار بناء الدفعة 2 | بانتظار بناء الدفعة 2 |
| `T-H12 / M-20` | 2 | قيد البناء/الاختبار | `79f53a1` | بانتظار بناء الدفعة 2 | بانتظار بناء الدفعة 2 |
| `T-M24 / M-09` | 2 | قيد البناء/الاختبار | `9b15284` | بانتظار بناء الدفعة 2 | بانتظار بناء الدفعة 2 |
