# سجل مراحل التنفيذ والتدقيق — مشروع سحاب (`com.saydlawy.sahab`)

> **المرجع:** `sahab_master_directive.md`  
> **القواعد الملزمة:** قراءة كاملة لكل ملف قبل تعديله • اكتشاف التعارضات والتبليغ قبل التعديل • صفر بيانات وهمية • `VodLayoutStyle.CLASSIC` كافتراضي • حظر الاختبار على الأجهزة المتصلة • حظر `git push` بدون أمر صريح.

---

## جدول تجميع وتصنيف المهام بحسب الملفات المستهدفة (Task Grouping)

| الملف المستهدف | المهام المجمّعة عليه (Single-Pass / Grouped) | المراحل |
| :--- | :--- | :--- |
| `data/Models.kt` | `TASK-02` (`url` في `FavItem` و`RecentItem`) • `TASK-08` (`VodLayoutStyle`) | 1، 4 |
| `data/Prefs.kt` | `TASK-05` (`ensureAccountLoaded` في `toggleFavorite`/`recordPlayback`/`positionOf`) • `TASK-06` (حذف `ShowMatchesOnHome`) • `TASK-08` (`moviesLayoutStyle` / `seriesLayoutStyle`) | 1، 2، 4 |
| `SahabApp.kt` | `TASK-02` (`url` في `saveProgress`) • `TASK-05` (`debounce(15_000L)` على `recentsFlushTrigger`) • `TASK-06` (حذف `SyncScheduler`) • `TASK-08` (مزامنة تخطيطات VOD والثيم) | 1، 2، 4 |
| `ui/AppRoot.kt` | `TASK-03` (`findStartDestination`, حجب الحوارات عن `onPlayer`, حجب اللمسات أثناء التحميل) • `TASK-06` (حذف `Routes.SPORTS` و`onSports`) | 1، 2 |
| `ui/Locals.kt` | `TASK-06` (حذف `Routes.SPORTS`) • `TASK-07` (`LocalSahabColors`) | 2، 3 |
| `ui/login/LoginViewModel.kt` | `TASK-01` (إصلاح استدعاء `Account(...)` بالمعاملات المُسمّاة) | 1 |
| `data/Repository.kt` | `TASK-02` (استرجاع `url` القناة من الكاش في `streamUrl`) • `TASK-05` (`CancellationException` في `cachedPersistent`) | 1 |
| `data/catalog/*` & `data/channel/ChannelDatabase.kt` & `firestore.rules` | `TASK-04` (تثبيت `sahabId`, فهرس `tmdbId`, إحياء المحذوفات ناعماً, عزل `global_catalog`, Migrations صريحة) | 1 |
| `data/auth/*`, `data/SyncCoordinator.kt`, `data/M3uParser.kt`, `data/ai/GeminiStoryService.kt` | `TASK-05` (مزامنة `updatedAt` وسقف المفضلة/السجل، `spec.clearPassword()`، منع تكرار `prewarmCatalog`، إلغاء `M3uParser` + سقف 25k، جمع `parts` وحماية Prompt) | 1 |
| `AndroidManifest.xml`, `ui/player/*` | `TASK-03` (`exported="true"`, `MediaMetadata`, `PendingIntent`) • `TASK-05` (`debounce` حفظ الموضع 30s، إخفاء واجهة Portrait في PiP) • `TASK-16` (`activeBitrateText`, حبس تركيز الدرج، `Crashlytics.log`) | 1، 7 |
| `ui/home/*` | `TASK-02` (`url` في `PlayItem` للرئيسية والمفضلة) • `TASK-06` (حذف الرياضة و`TodayMatchesSection`) • `TASK-07` (ألوان OLED) • `TASK-15` (صف الأعلى تقييماً `topRatedMovies`) | 1، 2، 3، 7 |
| `ui/live/*` | `TASK-02` (`url` في `FavItem`) • `TASK-05` (`EPG` من Room أولاً) • `TASK-10` (القوائم المخصصة `ui/live/custom/*`, إخفاء/ترتيب القنوات) | 1، 5 |
| `ui/theme/*`, `ui/components/*` | `TASK-06` (حذف رموز الرياضة) • `TASK-07` (محرك OLED الموحّد + `CompositionLocal` + `DreamyBackground` + التركيز `focusedRaw` + حماية العناصر الحرجة) • `TASK-16` (خط التلفاز، `CompactTopBarInfo`، `TopBarWidgets` إزالة `.focusable` المكرر) | 2، 3، 7 |
| `ui/settings/*` | `TASK-06` (حذف خيار مباريات اليوم) • `TASK-09` (إعادة هيكلة الإعدادات: Master-Detail + تخطيطات الأفلام/المسلسلات المستقلة + استوديو OLED + تحديث الاشتراك + حوار تأكيد الخروج) | 2، 4 |
| `ui/browse/*` | `TASK-11` (`BrowseContract`, `BrowseViewModel`, `BrowseScreen`, حزمة `layouts/classic/`) • `TASK-12` (`layouts/prime/`) • `TASK-13` (`layouts/netflix/`) • `TASK-14` (`layouts/tivimate/`) | 6 |
| `ui/detail/SeriesDetailScreen.kt` | `TASK-15` (زر تحديث البيانات الوصفية `refreshMetadata`) | 7 |

---

## سجل تنفيذ المراحل

- **الحالة الحالية:** بدء المرحلة الأولى (قراءة الملفات المستهدفة بالكامل والتحقق من عدم وجود تعارضات قبل التعديل).

---

## المرحلة الأولى: إصلاح الأعطال الحرجة وتطهير البيانات والأمان (`TASK-01` إلى `TASK-05`) — **مكتملة**

### 1. الملفات التي تمت قراءتها بالكامل قبل التعديل
- `ui/login/LoginViewModel.kt`, `data/Models.kt`, `SahabApp.kt`, `ui/home/FavoritesScreen.kt`, `ui/live/LiveViewModel.kt`, `ui/home/HomeScreen.kt`, `ui/home/HomeContentRows.kt`, `ui/live/LiveChannelList.kt`, `ui/live/LivePreviewPanel.kt`, `data/Repository.kt`, `ui/AppRoot.kt`, `ui/live/LiveScreen.kt`, `ui/player/SahabPlaybackService.kt`, `AndroidManifest.xml`, `ui/player/PlayerContent.kt`, `ui/player/PlayerScreen.kt`, `data/catalog/CatalogMovieEntity.kt`, `data/catalog/CatalogMovieDao.kt`, `data/catalog/CatalogSync.kt`, `data/catalog/CatalogDatabase.kt`, `data/catalog/FirebaseCatalogService.kt`, `data/channel/ChannelDatabase.kt`, `firestore.rules`, `data/catalog/CatalogIdAliasTest.kt`, `data/Prefs.kt`, `data/auth/CloudMergeCoordinator.kt`, `data/SyncCoordinator.kt`, `data/M3uParser.kt`, `data/ai/GeminiStoryService.kt`, `data/auth/UserSyncService.kt`, `ui/player/PlayerGestures.kt`, `ui/components/EpgDialog.kt`, `data/channel/ChannelDao.kt`.

### 2. الملفات المنشأة / المعدلة والتغييرات التقنية
- **`TASK-01`**: إصلاح استدعاء `Account(...)` في `ui/login/LoginViewModel.kt` باستخدام المعاملات المُسمّاة الصريحة (`name`, `type`, `server`, `user`, `pass`) + إنشاء `app/src/test/java/com/saydlawy/sahab/LoginAccountConstructionTest.kt`.
- **`TASK-02`**: إضافة `url: String = ""` في `FavItem` و`RecentItem` (`data/Models.kt`)؛ تمرير `url` في `SahabApp.saveProgress`, `HomeContentRows.kt`, `HomeScreen.kt`, `FavoritesScreen.kt`, `LiveViewModel.toggleFavorite`؛ واسترجاع الرابط من الكاش المتزامن في `Repository.streamUrl` للقنوات الحية القديمة.
- **`TASK-03`**: استبدال `popUpTo(nav.graph.id)` بـ `popUpTo(nav.graph.findStartDestination().id)` في `ui/AppRoot.kt`؛ حجب `SyncPinUnlockDialog` و`GithubUpdatePrompt` عن `onPlayer`؛ حجب اللمسات أثناء `switchingAccount` و`restoringCloud`؛ تصدير `SahabPlaybackService` (`exported="true"`) وربط `setSessionActivity`؛ تزويد `MediaItem` بـ `MediaMetadata` في `PlayerContent.kt`.
- **`TASK-04`**: تثبيت `sahabId` في `CatalogMovieDao.applyEnrichment` (تحديث بيانات TMDB دون تغيير المفتاح الأساسي)؛ شمول المحذوف ناعماً في `allStreamIds` وإحياؤها في `touchBulk`؛ عزل `global_catalog` للمعرّفات الموثقة `tmdb:*` وحفظ العناصر المخصصة في `users/{uid}/custom_catalog` عبر `FirebaseCatalogService.kt` و`firestore.rules`؛ إزالة `fallbackToDestructiveMigration(dropAllTables = true)` وإضافة `MIGRATION_3_4` (`CatalogDatabase`) و`MIGRATION_1_2` (`ChannelDatabase`).
- **`TASK-05`**: استدعاء `ensureAccountLoaded()` في `Prefs.kt` (`toggleFavorite`, `positionOf`, `recordPlayback`)؛ إضافة `updatedAt` وسقف المفضلة (`800`) والسجل (`150`) و`spec.clearPassword()` في `UserSyncService.kt` و`CloudMergeCoordinator.kt`؛ منع تكرار `prewarmCatalog` عند `forceCatalog=true` في `SyncCoordinator.kt`؛ خنق `recentsFlushTrigger.debounce(15_000L)` في `SahabApp.kt` وحفظ موضع المشغل كل 30s (`ticks >= 60`) في `PlayerContent.kt`؛ فصل `CancellationException` في `Repository.cachedPersistent` وقراءة EPG من `ChannelDao` أولاً في `shortEpg`/`dayEpg`؛ جعل `M3uParser.fetch` قابلاً للإلغاء بسقف `25_000` قناة؛ جمع كل `parts` وحماية عنوان المزوّد بـ `<provider_title>` في `GeminiStoryService.kt`؛ وإخفاء واجهة Portrait في وضع PiP داخل `PlayerGestures.kt`.

### 3. حالة التحقق
- التحقق الساكن اليدوي: متوافق، بدون بيانات وهمية، وبدون اختبار على أجهزة متصلة.
- **المراحل المتبقية:** المراحل 2 إلى 8.


---

## المرحلة 2: إزالة قسم الرياضة بالكامل (`TASK-06`)
**الحالة:** مكتملة (تحقق ساكن بـ `git grep` — لا توجد بقايا لـ `data.sports` أو `ui.sports` أو `Routes.SPORTS` أو `TodayMatchesSection`).

### المهام المنفذة
- `TASK-06`: إزالة حزمة الرياضة بالكامل (`ui/sports`, `data/sports`, `test/.../ui/sports/SportsFilterTest.kt`)، وإزالة مسار `Routes.SPORTS`، وتبويب الرياضة في `TopBar` و`OledNavigationChrome`، وقسم `TodayMatchesSection` و`loadTodayMatches()` من `HomeViewModel` / `HomeScreen` / `HomeContentRows`، وإعداد `ShowMatchesOnHome` من `Prefs` / `SettingsHomeDisplayPanel` / `SettingsScreen`، وتوكنات `Sport*` من `Theme.kt` و`ThemeCustomizationPanel.kt` و`Ar.kt`، وجدولة `SyncScheduler` من `SahabApp.kt`.

### الملفات المقروءة بالكامل قبل التعديل
- `SahabApp.kt`, `Locals.kt`, `AppRoot.kt`, `TopBar.kt`, `OledNavigationChrome.kt`, `HomeViewModel.kt`, `HomeScreen.kt`, `HomeContentRows.kt`, `Prefs.kt`, `SettingsHomeDisplayPanel.kt`, `SettingsScreen.kt`, `Theme.kt`, `ThemeCustomizationPanel.kt`, `Ar.kt`.

### الملفات المحذوفة
- `app/src/main/java/com/saydlawy/sahab/ui/sports/**`
- `app/src/main/java/com/saydlawy/sahab/data/sports/**`
- `app/src/test/java/com/saydlawy/sahab/ui/sports/SportsFilterTest.kt`

### الملفات المعدّلة
- `SahabApp.kt`, `Locals.kt`, `Ar.kt`, `AppRoot.kt`, `TopBar.kt`, `OledNavigationChrome.kt`, `HomeViewModel.kt`, `HomeScreen.kt`, `HomeContentRows.kt`, `Prefs.kt`, `SettingsHomeDisplayPanel.kt`, `SettingsScreen.kt`, `Theme.kt`, `ThemeCustomizationPanel.kt`.

### المراحل المتبقية
- المرحلة 3 (`TASK-07`) حتى المرحلة 8.


---

## المرحلة 3: توحيد محرك الثيمات `OLED Cinema` (`TASK-07`)
**الحالة:** مكتملة (تحقق ساكن مبدئي؛ لا يوجد بناء APK محلي).

### المهام المنفذة
- `TASK-07`:
  - إنشاء `OledColorTokens.kt` متضمناً 6 خيارات للألوان المميزة (`oled_cyan`, `oled_prime`, `oled_netflix`, `oled_gold`, `oled_emerald`, `oled_violet`) وتوكنات الأسود الحقيقي `#000000`.
  - إضافة `oledAccentPreset` إلى `ThemePreferences.kt`.
  - ربط `oled_cinema` في `Theme.kt` بـ `applyOledCinemaTokens`، واستبدال `SideEffect` بتحديث متزامن مع `LocalSahabColors` via `CompositionLocalProvider`، وحماية العناصر الحرجة (`global_navigation`, `details_actions`, `player_controls`) داخل `ThemeCustomization.isVisible()`.
  - تحديث `DreamyBackground()` في `Background.kt` لقراءة التوكنات بشكل تفاعلي ورسم أسود خالص في `oled_cinema`.
  - تحديث `GlassCard` في `Glass.kt` لإظهار إطار التركيز على كافة الأجهزة مع حصر التكبير على `isTv`.
  - دعم التنقل بـ D-Pad في `OledNavigationChrome.kt` (`ChromeTabItem` مع `focusable` و`onPreviewKeyEvent`).
  - استبدال الألوان الثابتة بتوكنات `Palette` في `PosterCards.kt` و`HomeContentRows.kt` و`SyncOverlay.kt`.

### الملفات المقروءة بالكامل قبل التعديل
- `ThemePreferences.kt`, `Theme.kt`, `Background.kt`, `Glass.kt`, `PosterCards.kt`, `HomeContentRows.kt`, `SyncOverlay.kt`, `OledNavigationChrome.kt`.

### الملفات المنشأة
- `app/src/main/java/com/saydlawy/sahab/ui/theme/OledColorTokens.kt`

### الملفات المعدّلة
- `ThemePreferences.kt`, `Theme.kt`, `Background.kt`, `Glass.kt`, `OledNavigationChrome.kt`, `PosterCards.kt`, `HomeContentRows.kt`, `SyncOverlay.kt`.

### المراحل المتبقية
- المرحلة 4 (`TASK-08`, `TASK-09`) حتى المرحلة 8.


---

## المرحلة 4: تخطيطات VOD المستقلة وإعادة تصميم الإعدادات (`TASK-08` & `TASK-09`)
**الحالة:** مكتملة (تحقق ساكن مبدئي؛ لا يوجد بناء APK محلي).

### المهام المنفذة
- `TASK-08`: إضافة `VodLayoutStyle` (`CLASSIC`, `PRIME`, `NETFLIX`, `TIVIMATE` مع الافتراضي `CLASSIC`) في `Models.kt`، وتدفقات `moviesLayoutStyle` و`seriesLayoutStyle` و`setOledAccentPreset` في `Prefs.kt`، ومزامنتها عبر السحابة في `UserSyncService.kt` و`SyncCoordinator.kt` و`SahabApp.kt` و`firestore.rules`.
- `TASK-09`: إعادة تصميم `SettingsScreen.kt` بـ 5 أقسام واضحة مع `LogoutConfirmationDialog`، وإنشاء `SettingsAppearancePanel.kt` (اختيار تخطيط الأفلام والمسلسلات بشكل مستقل + محرك الثيمات + 6 ألوان OLED + الخطوط والزجاج)، و`SettingsCustomTvPanel.kt` (إدارة قنوات الرئيسية + القوائم المخصصة والمخفية من Room)، وتحديث `SettingsSourcesPanel.kt` بزر تحديث بيانات الاشتراك (`refreshUserInfo`)، وحذف `ThemeCustomizationPanel.kt`.

### الملفات المقروءة بالكامل قبل التعديل
- `Models.kt`, `Prefs.kt`, `UserSyncService.kt`, `SyncCoordinator.kt`, `SahabApp.kt`, `firestore.rules`, `SettingsScreen.kt`, `SettingsSourcesPanel.kt`, `SettingsAccountAndAiPanels.kt`, `SettingsHomeChannelsPanel.kt`.

### الملفات المنشأة
- `app/src/main/java/com/saydlawy/sahab/ui/settings/SettingsAppearancePanel.kt`
- `app/src/main/java/com/saydlawy/sahab/ui/settings/SettingsCustomTvPanel.kt`

### الملفات المحذوفة
- `app/src/main/java/com/saydlawy/sahab/ui/settings/ThemeCustomizationPanel.kt`

### الملفات المعدّلة
- `Models.kt`, `Prefs.kt`, `UserSyncService.kt`, `SyncCoordinator.kt`, `SahabApp.kt`, `firestore.rules`, `SettingsSourcesPanel.kt`, `SettingsScreen.kt`.

### المراحل المتبقية
- المرحلة 5 (`TASK-10`) حتى المرحلة 8.


---

## المرحلة 5: قوائم البث المخصصة وإخفاء وترتيب القنوات (`TASK-10`)
**الحالة:** مكتملة (تحقق ساكن مبدئي؛ لا يوجد بناء APK محلي).

### المهام المنفذة
- `TASK-10`:
  - إضافة `observeHiddenIds()` و`unhideAll(now)` في `ChannelDao.kt`.
  - تحديث `LiveViewModel.kt` بمراقبة `observeLists()` و`observeHiddenIds()`، ودعم اختيار القوائم المخصصة (`CUSTOM_LIST_PREFIX`)، وإنشاء/حذف القوائم، وإضافة/حذف القنوات من القائمة، وإخفاء القنوات، وإعادة ترتيب القنوات (`moveChannelOrder`).
  - إنشاء حزمة `ui/live/custom/` (`CustomListsManagerDialog.kt`, `CustomListChannelPickerDialog.kt`, `ChannelManageContextSheet.kt`).
  - دمج القوائم المخصصة وإدارة القنوات بالضغط المطول في `LiveCategoriesSidebar.kt` و`LiveChannelList.kt` و`LiveScreen.kt`.

### الملفات المقروءة بالكامل قبل التعديل
- `ChannelDao.kt`, `ChannelEntities.kt`, `LiveViewModel.kt`, `LiveCategoriesSidebar.kt`, `LiveChannelList.kt`, `LiveScreen.kt`, `SettingsCustomTvPanel.kt`.

### الملفات المنشأة
- `app/src/main/java/com/saydlawy/sahab/ui/live/custom/CustomListsManagerDialog.kt`
- `app/src/main/java/com/saydlawy/sahab/ui/live/custom/CustomListChannelPickerDialog.kt`
- `app/src/main/java/com/saydlawy/sahab/ui/live/custom/ChannelManageContextSheet.kt`

### الملفات المعدّلة
- `ChannelDao.kt`, `SettingsCustomTvPanel.kt`, `LiveViewModel.kt`, `LiveCategoriesSidebar.kt`, `LiveChannelList.kt`, `LiveScreen.kt`.

### المراحل المتبقية
- المرحلة 6 (`TASK-11` حتى `TASK-14`)، المرحلة 7 (`TASK-15`، `TASK-16`)، المرحلة 8 (التحقق الساكن النهائي).


---

## المرحلة 6: قوالب التصفح الأربعة المستقلة (`TASK-11` حتى `TASK-14`)
**الحالة:** مكتملة (تحقق ساكن مبدئي؛ لا يوجد بناء APK محلي).

### المهام المنفذة
- `TASK-11`..`TASK-14`:
  - إنشاء `BrowseContract.kt` وتحديث `BrowseViewModel.kt` بـ `featuredHeroItems`, `top10Items`, `categoryGroups`, `focusedDetails`, و`onPosterFocused()` (مرتبطة ببيانات حقيقية من `CatalogMovieDao` و`Repository` بدون أي بيانات وهمية).
  - استخراج التصميم الكلاسيكي الأصلي إلى `ui/browse/layouts/classic/ClassicBrowseLayout.kt` (الإعداد الافتراضي `VodLayoutStyle.CLASSIC`).
  - بناء قالب `Prime` في `ui/browse/layouts/prime/` (`PrimeHeroBanner.kt`, `PrimeCategoryTabs.kt`, `PrimeBrowseLayout.kt`).
  - بناء قالب `Netflix` في `ui/browse/layouts/netflix/` (`NetflixBillboard.kt`, `NetflixTop10Row.kt`, `NetflixBrowseLayout.kt`).
  - بناء قالب `TiviMate` في `ui/browse/layouts/tivimate/` (`TiviMateDetailsPane.kt`, `TiviMateBrowseLayout.kt`).
  - ربط `BrowseScreen.kt` للتبديل المستقل بين القوالب الأربعة لكل من الأفلام والمسلسلات.

### الملفات المقروءة بالكامل قبل التعديل
- `BrowseViewModel.kt`, `BrowseScreen.kt`.

### الملفات المنشأة
- `ui/browse/BrowseContract.kt`
- `ui/browse/layouts/classic/ClassicBrowseLayout.kt`
- `ui/browse/layouts/prime/PrimeHeroBanner.kt`, `PrimeCategoryTabs.kt`, `PrimeBrowseLayout.kt`
- `ui/browse/layouts/netflix/NetflixBillboard.kt`, `NetflixTop10Row.kt`, `NetflixBrowseLayout.kt`
- `ui/browse/layouts/tivimate/TiviMateDetailsPane.kt`, `TiviMateBrowseLayout.kt`

### الملفات المعدّلة
- `BrowseViewModel.kt`, `BrowseScreen.kt`.

### المراحل المتبقية
- المرحلة 7 (`TASK-15`، `TASK-16`) والمرحلة 8 (التحقق الساكن النهائي).


---

## المرحلة 7: التحسينات واللمسات النهائية (`TASK-15` & `TASK-16`)
**الحالة:** مكتملة (تحقق ساكن مبدئي؛ لا يوجد بناء APK محلي).

### المهام المنفذة
- `TASK-15` & `TASK-16`:
  - إضافة صف `الأعلى تقييماً` (`topRatedMovies`) في `HomeViewModel.kt` و`HomeScreen.kt` باستخدام `TrendingMoviesSection` القابلة لإعادة الاستخدام.
  - إضافة `refreshMetadata()` في `SeriesDetailViewModel.kt` وزر التحديث (`Ic.Refresh`) في `SeriesDetailScreen.kt`.
  - إضافة شارة معدل البت (`activeBitrateText` عبر `FormatChip`) في شريط `UnifiedFullscreenPlayerOsd` (`PlayerControlsOverlay.kt` و`PlayerContent.kt`).
  - تحسين مصيدة التركيز والتمرير للعنصر النشط وزر الرجوع في `PlayerChannelsDrawer.kt`.
  - عرض اسم المدينة وأيقونات الطقس المفصلة في `CompactTopBarInfo.kt`.
  - إزالة `focusable` المكررة قبل `clickable` في `TopBarWidgets.kt`، وإضافة تعزيز حجم خط التلفاز في `Theme.kt`.
  - تسجيل أحداث تعافي البث في `FirebaseCrashlytics` داخل `LiveVideoStallWatchdog.kt`.

### الملفات المقروءة بالكامل قبل التعديل
- `HomeViewModel.kt`, `HomeScreen.kt`, `TrendingMovies.kt`, `SeriesDetailViewModel.kt`, `SeriesDetailScreen.kt`, `PlayerControlsOverlay.kt`, `PlayerContent.kt`, `PlayerChannelsDrawer.kt`, `CompactTopBarInfo.kt`, `WeatherService.kt`, `TopBarWidgets.kt`, `Theme.kt`, `LiveVideoStallWatchdog.kt`.

### الملفات المعدّلة
- `HomeViewModel.kt`, `HomeScreen.kt`, `TrendingMovies.kt`, `SeriesDetailViewModel.kt`, `SeriesDetailScreen.kt`, `PlayerControlsOverlay.kt`, `PlayerContent.kt`, `PlayerChannelsDrawer.kt`, `CompactTopBarInfo.kt`, `TopBarWidgets.kt`, `Theme.kt`, `LiveVideoStallWatchdog.kt`.


---

## المرحلة 8: التحقق الساكن النهائي والتسليم
**الحالة:** مكتملة (تحقق ساكن فقط — لم يتم بناء APK محلياً أو الاختبار على أي جهاز وفق القواعد الحاكمة).

### نتائج التحقق الساكن
- **بقايا قسم الرياضة:** `0` تطابق في `app/src` عبر `git grep -niE "data\.sports|ui\.sports|Routes\.SPORTS|TodayMatch|SportSurface|SyncScheduler|ShowMatchesOnHome|showMatchesOnHome"`.
- **الإعداد الافتراضي لقوالب التصفح:** تم التحقق في `Prefs.kt` و`SyncCoordinator.kt` و`BrowseScreen.kt` أن `VodLayoutStyle.CLASSIC` هو الافتراضي للأفلام والمسلسلات بشكل مستقل.
- **البيانات الوهمية:** صفر بيانات وهمية داخل `app/src/main/**`؛ كافة الشاشات والبطاقات تعتمد على البيانات الحية من `Repository` و`CatalogDatabase` و`ChannelDatabase` و`Prefs`.
- **إدارة Git:** التعديلات جاهزة للرفع عبر قائمة `git add` المحددة للملفات المقصودة فقط (دون `git add .` أو `git push` تلقائي).


---

## خارطة طريق التدقيق — Phase 0 (CI Verified Baseline)
- **الحالة:** `CI Verified` (GitHub Actions Run `#228` على Commit `a81af30` نجح بالكامل؛ إصدار `1.0.228` تم تثبيته على `Smart TV Pro` عبر `adb`).
- **المهام:** `F-01`..`F-04` (إصلاح أخطاء الترجمة، دورة حياة ExoPlayer ومستشعرات الصوت، وترقيات Room دون تدمير بيانات المستخدم + اختبارات `DatabaseMigrationRegressionTest`).

---

## خارطة طريق التدقيق — Phase 1: Security, Data Integrity & Sync Hardening (`1.1`..`1.7`)
- **الحالة:** `Implemented` + `Locally Verified (Static)` (مكتملة في الكود مع اختبارات وحدة pure-JVM مضافة؛ تنتظر اعتماد Commit/Push للتحقق عبر GitHub Actions وتشغيل الاختبارات على الجهازين).
- **المهام المنفذة (`F-05`..`F-07`, `F-10`, `F-11`, `F-13`, `F-21`..`F-23`, `F-27`, `F-28`):**
  - `1.1` (`F-05`): قفل `SahabPlaybackService.onGetSession` عبر `isAllowedMediaController(uid, myUid, isTrusted)` لمنع تحكم التطبيقات الخارجية غير الموثوقة.
  - `1.2` (`F-06`): تشديد `firestore.rules` (`isValidListElem`، حماية حقول الإثراء ومنع حذف `aliases` في `originalEnrichmentUnchanged`، التحقق من `isValidGlobalCatalogRedirect`) وتحديث `FirebaseCatalogService` لحفظ التحويلات تحت `users/{uid}/custom_catalog/{pendingId}`.
  - `1.3` (`F-07`): نقل مفتاح Gemini من رابط الاستعلام إلى ترويسة `x-goog-api-key` في `GeminiStoryService.kt`، وتثبيت HTTPS لـ `generativelanguage.googleapis.com` في `network_security_config.xml` و`SahabApp.kt`، ونقل `HttpSecurityWarningBanner` إلى `Fields.kt` وإظهاره في `LoginScreen.kt` و`SettingsSourcesPanel.kt`.
  - `1.4` (`F-10`): إلغاء علم `forceRefresh` المشترك في `Repository` واستبداله بمعامل `forceNetwork: Boolean = false`، إضافة `invalidateLiveChannelCache()` واستدعاؤها بعد مزامنة `ChannelSync`، عزل مفتاح كاش القرص لحسابات M3U عبر `a.id` وهاش `m3uUrl`، وصمود منتظري `inFlight` عند إلغاء المطلب القائد، وتحويل `_syncing` في `Repository.syncCatalog` إلى `compareAndSet`.
  - `1.5` (`F-11`): قفل ذري `compareAndSet` في `CatalogSync.kt`، و`syncMutex` + `triggerManualSync` على `appScope` + `stopActiveSync()` في `SyncCoordinator.kt` / `SahabApp.kt` / `SettingsScreen.kt`.
  - `1.6` (`F-13`, `F-28`): إضافة `.distinctUntilChanged()` على تدفقات `Prefs.kt` و`startAutoSync()` مع تخزين آخر قيم مرفوعة، ومزامنة قراءة/كتابة `pendingPlaybackFile` ذرياً في `SahabApp.kt`.
  - `1.7` (`F-21`, `F-22`, `F-23`, `F-27`): تعقيم بيانات دخول Xtream ورموز `token`/`password` قبل رفع المفضلة والسجل السحابي في `UserSyncService.redactCredentialsFromStreamUrl`، ونقل التشفير PBKDF2/Crypto إلى `Dispatchers.Default` وتأخير استعادة الحساب السحابي إذا وُجد حساب محلي في `SyncCoordinator`، وإلغاء السقوط الصامت للنص الصريح في `ApiKeyManager.kt` و`Prefs.securePrefs()`، وتقييد روابط التحديث في `GithubReleaseUpdater` و`AppRoot.kt` بمضيفات GitHub الآمنة مع استخلاص `sha256`.
- **اختبارات الوحدة المضافة (pure-JVM):** `Phase1SecurityAndSyncRegressionTest.kt`، وإضافات في `RepositoryTest.kt` و`PrefsTest.kt`.

---

## خارطة طريق التدقيق — Phase 2: Performance, TV D-Pad UX & Reliability (`2.1`..`2.6`)
- **الحالة:** `Implemented` + `Locally Verified (Static)` (مكتملة في الكود مع اختبارات وحدة pure-JVM مضافة؛ تنتظر اعتماد Commit/Push للتحقق عبر GitHub Actions وتشغيل الاختبارات على الجهازين).
- **المهام المنفذة (`F-08`, `F-09`, `F-16`..`F-19`, `F-24`..`F-26`, `F-30`..`F-32`):**
  - `2.1` (`F-08`): تحويل جلب قوائم Xtream الضخمة (`get_live_streams` / `get_vod_streams` / `get_series`) إلى بث متدفق `XtreamApi.streamArray` عبر `decodeToSequence` دون تحميل سلاسل JSON أو أشجار DOM ضخمة في الذاكرة، وتحويل `DiskCache` للقرص إلى `decodeFromStream` / `encodeToStream`، وتحديد التزامن الأقصى في `prewarmCatalog` و`syncCatalog` عبر `Semaphore(2)`.
  - `2.2` (`F-09`, `F-25`, `F-30`): نقل اشتقاق `LiveUiState.visibleChannels` إلى الخلفية (`Dispatchers.Default` + `visibleMutex`) بدلاً من التصفية المتكررة على الخيط الرئيسي أثناء إعادة الرسم، وتحديث `ChannelDao.visibleOrdered()` / `all()` لاحترام `sortOrder`، وإضافة معاملات ذرية `@Transaction` في `ChannelDao` لقوائم القنوات المخصصة، وعزل `ChannelListEntity` عبر `accountId` مع ترقية `ChannelDatabase` (`2 -> 3` عبر `MIGRATION_2_3` دون حذف أي قوائم سابقة)، وحصر مسح السجل في `SettingsAppearancePanel` بالحساب النشط فقط (`clearRecentsForAccount`)، وتوحيد تطبيع الخادم `ServerUrl.normalizeServer` في `LoginViewModel` و`AddSourceDialog`.
  - `2.3` (`F-24`, `F-26`): تجميع تعابير `Regex` مسبقاً في `ChannelSync` و`FirebaseCatalogService`، وتحسين اختيار أحدث الأفلام إلى خوارزمية `PriorityQueue` محدودة الحجم بدلاً من فرز 25 ألف عنصر مع تكرار الفرز، وتخطي استعلامات EPG لصفوف القنوات ذات `epgId` الفارغ، وعزل نبض `LiveBadge` عبر `graphicsLayer` لتجنب إعادة تركيب الصفوف، وفصل مؤقت تقدم المشغل عن الإبقاء على عمله عندما يكون الشريط مخفياً، وقراءة `restorePendingPlayback()` على `Dispatchers.IO` في `PlayerScreen.kt`.
  - `2.4` (`F-16`, `F-17`): توحيد مفاتيح استعادة التركيز عبر `BrowseContract.onOpenPosterWithKey` في `BrowseScreen` و`NetflixBrowseLayout` و`NetflixTop10Row` و`TiviMateBrowseLayout`، وتفريق مفاتيح عناصر صفوف الرئيسية باستخدام `${rowKey}_${id}` في `TrendingMovies.kt`.
  - `2.5` (`F-18`, `F-19`): إصلاح تسلسل التركيز ليشمل زر تبديل الوضع (`toggleMode`) في `CloudLoginScreen.kt`، وتمرير `inputModifier` إلى `BasicTextField` مع دعم `FocusDirection.Next` في `GlassTextField` (`Fields.kt`)، وإزالة `.focusable()` المكررة في `OledNavigationChrome.kt`، وتوفير مُركِّز احتياطي وإطارات تركيز واضحة في `PlayerChannelsDrawer` و`SettingsAppearancePanel` و`LivePreviewPanel` و`HomeContentRows`.
  - `2.6` (`F-31`, `F-32`): إزالة القيم الوهمية الثابتة (`45:00`/`0.45f`/`0.5f`/`25`/`0.85f`/`0.95f`) من `PlayerGestures.kt`، وتحويل `PrimeCategoryTabs` لعمود مناسب للشاشة العمودية في الهواتف، وإزالة العناصر القابلة للنقر المتداخلة في `SettingsSourcesPanel`، وإضافة دلالات شريط التقدم لإمكانية الوصول (`progressBarRangeInfo`)، وإضافة `verticalScroll().imePadding()` لنوافذ الحوار، وإضافة هوامش `WindowInsets.displayCutout` لمشغل الوضع الأفقي والأدراج، وتوفير خيارات القوائم المخصصة وإدارتها في تخطيطات البث للهاتف (`LiveScreen.kt`).
- **اختبارات الوحدة المضافة (pure-JVM):** `Phase2PerformanceAndUxRegressionTest.kt` وتحديث `DatabaseMigrationRegressionTest.kt` لترقية `ChannelDatabase` (`2 -> 3`).

---

## خارطة طريق التدقيق — Phase 3: Architecture Refactor, Repo Hygiene & Build Modernization (`3.1`..`3.5`)
- **الحالة:** `Implemented` + `Locally Verified (Static)` (مكتملة في الكود مع اختبارات وحدة pure-JVM مضافة؛ تنتظر اعتماد Commit/Push للتحقق عبر GitHub Actions وتشغيل الاختبارات على الجهازين).
- **المهام المنفذة (`F-12`, `F-14`, `F-15`, `F-20`, `F-29`, `F-33`, `F-34`):**
  - `3.1` (`F-12`, `OPP-01`): تحويل كاش الذاكرة في `Repository.kt` إلى `CacheEntry(typeTag, value)` محميّ التحقق من نوع المسلسل (`getTypedCache`/`putTypedCache`) لمنع أي تعارض أو إسقاط أنواع غير آمن، ونقل تصفية الفئات الكبيرة (`> 400` عنصر) في `BrowseViewModel.kt` إلى `Dispatchers.Default`.
  - `3.2` (`F-14`, `F-33`): إضافة معامل `key: String? = null` إلى `appViewModel` في `Locals.kt` وتمريره في `BrowseScreen` (`route`) و`MovieDetailScreen` (`"movie_$id"`) و`SeriesDetailScreen` (`"series_$id"`) لمنع إعادة استخدام ViewModel عبر عناصر مختلفة، وتنظيف الاستيرادات والمتغيرات غير المستخدمة في الملفات المعدّلة.
  - `3.3` (`F-15`): تحديث `.gitignore` لعزل مخرجات البناء وبيئات التطوير (`build/`, `app/build/`, `.gradle/`, `.kotlin/`, `.idea/`, `*.apk`, `*.aab`, `*.zip`, `__pycache__/`, `dl/`, `_local_archive/`).
  - `3.4` (`F-29`, `F-34`): استبدال `org.json.JSONObject` في `WeatherService.kt` بنماذج `@Serializable` عبر `kotlinx.serialization.json`؛ إزالة اعتمادية `androidx.work.runtime.ktx` غير المستخدمة وقاعدة `CoroutineWorker` الميتة من `app/proguard-rules.pro` مع إبقاء `isMinifyEnabled = true` و`isShrinkResources = true` وقواعد Room/DataStore/Firebase/Tink نشطة؛ توحيد نسخ المكتبات في `gradle/libs.versions.toml`؛ وترقية `kotlinOptions` إلى `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }` في `app/build.gradle.kts`.
  - `3.5` (`F-20`, `OPP-03`..`OPP-06`): إضافة استعلام البحث المفهرس `CatalogMovieDao.searchCatalog` وإضافة اختبارات `Phase3ArchitectureAndModernizationTest.kt` (تغطي `WeatherService` عبر `MockWebServer` و`computeAdaptiveWindowInfo`).
- **اختبارات الوحدة المضافة (pure-JVM):** `Phase3ArchitectureAndModernizationTest.kt`.
