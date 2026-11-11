# سجل التغييرات (CHANGELOG)

## [القادم] — خارطة التطوير الشاملة (المراحل 1–8)

### الأمان والخصوصية
- تشفير بيانات الاشتراك (`server`, `user`, `pass`) عبر `EncryptedSharedPreferences` بمفتاح Android Keystore (`AES256_SIV` / `AES256_GCM`) مع ترحيل تلقائي آمن من DataStore وحذف القيم الصريحة القديمة.
- تقييد حركة Cleartext عبر `network_security_config.xml` وحارس `CleartextGuardInterceptor` لنطاقات API المدمجة مع السماح بروابط IPTV الخاصة بالمستخدم وعرض تحذير أمني مرئي عند استخدام `http://` في شاشة الدخول.
- مسح كامل للمفضلة وكاش القرص وكاش الفيديو (`SimpleCache.release()` وحذف `sahab_media_cache`) عند تسجيل الخروج.
- دعم كشف نوع مفتاح TMDB تلقائياً (Bearer JWT v4 أو v3 API Key).

### الاستقرار والأداء
- إضافة `RetryInterceptor` مركزي في `OkHttpClient` بإعادة محاولتين بفواصل `500ms` و`1500ms` (مع استثناء تدفقات البث الحية وVOD).
- توحيد تقييد طلبات EPG في `dayEpg` باستخدام `epgGate` (`Semaphore(4)`).
- إضافة منع تكرار الطلبات المتزامنة (Single-flight) في `Repository.cachedPersistent`.
- إضافة سياسة LRU بحد `200MB` في `DiskCache`، والتحقق من نجاح `renameTo`، وتثبيت مصفوفة الأقفال.
- تفعيل `R8` (`isMinifyEnabled = true` و`isShrinkResources = true`) مع قواعد `proguard-rules.pro`.
- إضافة مفاتيح `key` فريدة لقوائم Compose لتقليل إعادة البناء غير الضرورية.

### مركز الرياضة والمشغل
- اعتماد قاعدة Room كمصدر العرض الوحيد، وفصل فحص Schema عن فحص بيانات Seed وإزالة `.fallbackToDestructiveMigration()` مع إضافة `SportsMigrations.MIGRATION_1_2`.
- حذف كلاسات المصادر الرياضية القديمة المهملة ونموذج `MatchStats` غير المستخدم، وتنظيم سكريبتات Python في `tools/`.
- دمج **Goal API** كمصدر وحيد للتحديث الشهري via `WorkManager` (`MonthlySyncWorker` + `SyncScheduler`) وعبر زر التحديث اليدوي.
- استخراج منطق تشغيل المشغل وقوائم التشغيل والترميم إلى `PlayerViewModel`.

### الاختبارات وCI
- إضافة اختبارات وحدة JVM لـ `DiskCache` و`Prefs` و`Repository` و`GoalApi` و`SportsViewModel` وتفعيل `testReleaseUnitTest` في GitHub Actions.
