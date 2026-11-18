# Sahab IPTV — Final Engineering Execution Report (`engineering-audit/FINAL_EXECUTION_REPORT.md`)

**Branch / Base HEAD:** `main` @ `a81af30` (`test: implement Cursor interface directly in DatabaseMigrationRegressionTest`)  
**Workspace:** `c:\Users\drahm\Downloads\sahab\-Sahab`  
**Build & Git Guardrails Respected:** No `git commit`, `git push`, branch creation, or local Gradle/Java/SDK APK build attempts were executed. R8 (`isMinifyEnabled = true`), `isShrinkResources = true`, Firebase Crashlytics, and non-destructive Room migrations were preserved throughout.

---

## 1. Executive Status Matrix (Honest Verification State)

| Roadmap Phase | Audit Scope | Code Status | Verification State | Notes |
|---|---|---|---|---|
| **Phase 0** | `F-01`..`F-04` | Completed | **CI Verified** (`Run #228`, `a81af30`) + APK installed on `Smart TV Pro` | Already committed & passed `:app:lintRelease`, `:app:testReleaseUnitTest`, `:app:assembleRelease`. |
| **Phase 1** | `1.1`..`1.7` (`F-05`..`F-07`, `F-10`, `F-11`, `F-13`, `F-21`..`F-23`, `F-27`, `F-28`) | **Implemented** | **Locally Verified (Static)**; **Blocked** for CI & Device Runtime until Commit/Push | Security, Firestore rules, sync mutexes, stream redaction, encrypted prefs fail-closed recovery, release host allowlist, and pure-JVM unit tests added. |
| **Phase 2** | `2.1`..`2.6` (`F-08`, `F-09`, `F-16`..`F-19`, `F-24`..`F-26`, `F-30`..`F-32`) | **Implemented** | **Locally Verified (Static)**; **Blocked** for CI & Device Runtime until Commit/Push | Streaming Xtream/DiskCache JSON, off-Main filtering, Room `v2 -> v3` migration (`MIGRATION_2_3`), D-Pad focus restoration, adaptive phone/cutout/a11y fixes. |
| **Phase 3** | `3.1`..`3.5` (`F-12`, `F-14`, `F-15`, `F-20`, `F-29`, `F-33`, `F-34`, `OPP-01`..`OPP-06`) | **Implemented** | **Locally Verified (Static)**; **Blocked** for CI & Device Runtime until Commit/Push | Typed memory cache wrapper, `appViewModel(key = ...)`, `.gitignore` hygiene, `WeatherService` kotlinx.serialization migration, `WorkManager` removal, `libs.versions.toml` consolidation, Kotlin 2.2 `compilerOptions`. |

---

## 2. Phase 1 Implemented Changes (`1.1`..`1.7`)

1. **`1.1` MediaSession Caller Authorization (`F-05`):**
   - `app/src/main/java/com/saydlawy/sahab/ui/player/SahabPlaybackService.kt`: Added `isAllowedMediaController(uid, myUid, isTrusted)` rejecting untrusted external UIDs in `onGetSession`, and added `syncActiveSession()` across `onCreate`, `onStartCommand`, and `onGetSession` so a rebound service never holds a stale `Player`.
2. **`1.2` Firestore Rules & Catalog Redirect Integrity (`F-06`):**
   - `firestore.rules`: Added `isValidListElem`, tightened `originalEnrichmentUnchanged()` (`titleAr`, `novaStreamId`, `plot`, `plotSource`, `plotConfidence`, `identifiedTitle`, `redirectTo`, `hasAll(resource.data.aliases)`), and enforced `isValidGlobalCatalogRedirect`.
   - `app/src/main/java/com/saydlawy/sahab/data/catalog/FirebaseCatalogService.kt`: Restricted global redirects to `tmdb:` IDs, routed pending redirects into `users/{uid}/custom_catalog/{pendingId}`, and updated `saveCatalogItem` to inspect existing `tmdb:` docs, merge only blank fields, and append aliases via `FieldValue.arrayUnion`.
3. **`1.3` Gemini Header Auth, Cleartext Guard & HTTP Warning Banner (`F-07`):**
   - `app/src/main/java/com/saydlawy/sahab/data/ai/GeminiStoryService.kt`: Moved API key from `?key=` query string to the `x-goog-api-key` HTTP header (with configurable `baseUrl` default for tests).
   - `app/src/main/res/xml/network_security_config.xml` & `app/src/main/java/com/saydlawy/sahab/SahabApp.kt`: Enforced TLS (`cleartextTrafficPermitted="false"`, `STRICT_HTTPS_DOMAINS`) for `generativelanguage.googleapis.com`.
   - `app/src/main/java/com/saydlawy/sahab/ui/components/Fields.kt`, `LoginScreen.kt`, `SettingsSourcesPanel.kt`: Consolidated `HttpSecurityWarningBanner()` in `Fields.kt` and wired it into both `LoginScreen` and `AddSourceDialog`.
4. **`1.4` Thread-Safe Repository Caching & M3U Isolation (`F-10`):**
   - `app/src/main/java/com/saydlawy/sahab/data/Repository.kt` & `ChannelSync.kt`: Replaced shared mutable `forceRefresh` with explicit `forceNetwork: Boolean = false` parameters, added `invalidateLiveChannelCache()`, salted M3U disk keys with `a.id` and `m3uUrl` SHA-256 prefix, recovered independent `inFlight` awaiters if a leader coroutine cancels, and switched `_syncing` to `compareAndSet(false, true)`.
5. **`1.5` Atomic Sync Locks & Lifecycle Safety (`F-11`):**
   - `app/src/main/java/com/saydlawy/sahab/data/catalog/CatalogSync.kt`: Switched `syncMovies` and `populateFromSeed` locks to `compareAndSet(false, true)`.
   - `app/src/main/java/com/saydlawy/sahab/data/SyncCoordinator.kt`, `SahabApp.kt`, `SettingsScreen.kt`: Added `syncMutex`, `stopActiveSync()`, orphaned-job prevention in `triggerFullSync`, and `triggerManualSync(context)` on `appScope` (moving manual sync out of Settings UI scope and removing dead `Toast`/`Dispatchers`/`scope` code).
6. **`1.6` Prefs Flow Deduplication & Atomic Pending Playback (`F-13`, `F-28`):**
   - `app/src/main/java/com/saydlawy/sahab/data/Prefs.kt` & `SahabApp.kt`: Applied `.distinctUntilChanged()` before/after JSON decoding in `Prefs` and in `startAutoSync()`, cached last uploaded snapshots, and synchronized atomic `.tmp` + rename writes on `pendingPlaybackFile`.
7. **`1.7` Cloud Redaction, Background Crypto, Fail-Closed Key Storage & Update Integrity (`F-21`..`F-23`, `F-27`):**
   - `app/src/main/java/com/saydlawy/sahab/data/auth/UserSyncService.kt`: Added `redactCredentialsFromStreamUrl()` before uploading favorites/recents and dispatched Tink/PBKDF2 crypto on `Dispatchers.Default`.
   - `app/src/main/java/com/saydlawy/sahab/data/SyncCoordinator.kt`: Skipped startup `restoreAccountsWithPin()` when a local account already exists.
   - `app/src/main/java/com/saydlawy/sahab/data/ai/ApiKeyManager.kt`, `Prefs.kt`, `SettingsAccountAndAiPanels.kt`: Removed plaintext fallback file (`sahab_ai_keys_fb`), deleted corrupted encrypted store and recreated cleanly or failed closed to a synchronized RAM-only `InMemorySharedPreferences`.
   - `app/src/main/java/com/saydlawy/sahab/data/GithubReleaseUpdater.kt` & `ui/AppRoot.kt`: Enforced `isTrustedReleaseUrl()` HTTPS GitHub allowlist and parsed release `sha256` digest.

---

## 3. Phase 2 Implemented Changes (`2.1`..`2.6`)

1. **`2.1` Streaming JSON & Bounded Concurrency (`F-08`):**
   - `XtreamApi.kt` (`streamArray` via `PushbackInputStream` + `decodeToSequence`), `DiskCache.kt` (`decodeFromStream`/`encodeToStream`), and `Repository.kt` (`Semaphore(2)` bounding `prewarmCatalog`/`syncCatalog`, plus tolerant DTO streaming). Avoids loading 15MB+ strings and full DOM trees, while rethrowing `IOException`/`CancellationException` cleanly.
2. **`2.2` Off-Main Filtering, Ordered Channels & Account Isolation (`F-09`, `F-25`, `F-30`):**
   - `LiveViewModel.kt`: Converted `LiveUiState.visibleChannels` from a computed getter into a stored field filtered on `Dispatchers.Default` under `visibleMutex` using `_state.update` so concurrent metadata updates are never overwritten.
   - `ChannelEntities.kt`, `ChannelDao.kt`, `ChannelDatabase.kt`: Added `visibleOrdered()`, sorted `all()` by `sortOrder ASC, displayName ASC`, wrapped custom-list mutations in `@Transaction` methods, scoped `ChannelListEntity` by `accountId` via non-destructive `ChannelDatabase.MIGRATION_2_3` (`version = 3`), and updated `SettingsCustomTvPanel.kt` & `LiveViewModel.kt` to observe lists for the active account.
   - `SettingsAppearancePanel.kt`: Changed clear recents to `prefs.clearRecentsForAccount(prefs.currentActiveAccountId())`.
   - `util/ServerUrl.kt`, `LoginViewModel.kt`, `SettingsSourcesPanel.kt`: Shared `ServerUrl.normalizeServer()` across initial Login and Settings `AddSourceDialog`.
3. **`2.3` Micro-Optimizations & Lifecycle Polling (`F-24`, `F-26`):**
   - Pre-compiled `Regex` instances in `ChannelSync.kt` and `FirebaseCatalogService.kt`; bounded newest-additions selection with a top-K `PriorityQueue`; skipped EPG Room queries when `channel.epgId.isBlank()`; rendered `LiveBadge()` pulse via `graphicsLayer` (`LiveChannelList.kt`); stopped 500ms UI polling ticks when OSD controls are hidden (`PlayerContent.kt`); loaded `restorePendingPlayback()` off the main thread in `PlayerScreen.kt` with a single-exit guard (`exited`).
4. **`2.4` Focus Key Parity (`F-16`, `F-17`):**
   - Added `BrowseLayoutCallbacks.onOpenPosterWithKey` across `BrowseContract.kt`, `BrowseScreen.kt`, `NetflixBrowseLayout.kt`, and `TiviMateBrowseLayout.kt` (fixing `Top10`/`Netflix`/`TiviMate` key mismatches), and prefixed `TrendingMovies.kt` keys with `rowKey` to eliminate Trending vs. Top Rated key collisions.
5. **`2.5` D-Pad Navigation & Focus Visibility (`F-18`, `F-19`):**
   - Added `inputModifier` forwarding and `FocusDirection.Next` fallback to `GlassTextField` (`Fields.kt`); wired `toggleModeFocus` and visual border in `CloudLoginScreen.kt`; removed duplicate `.focusable()` in `OledNavigationChrome.kt`; attached anchor index `0` fallback in `PlayerChannelsDrawer.kt` + season chip borders; added visible focus highlights to `SettingsAppearancePanel.kt`, `LivePreviewPanel.kt`, and `HomeContentRows.kt`.
6. **`2.6` Adaptive Layouts, Accessibility & Real Telemetry (`F-31`, `F-32`):**
   - Replaced hardcoded fake durations/progress values (`45:00`/`0.45f`/`0.5f`/`25`/`0.85f`/`0.95f`) in `PlayerGestures.kt`; stacked `PrimeCategoryTabs.kt` cleanly on portrait phones; removed nested clickable `GlassCard`/`GlassButton` wrappers in `SettingsSourcesPanel.kt`; added `progressBarRangeInfo` semantics in `PlayerControlsOverlay.kt`; added `.verticalScroll().imePadding()` to multi-field Settings dialogs; applied `WindowInsets.displayCutout` padding in `PlayerContent.kt`; and wired custom list chips + `"إدارة القوائم"` into phone `LiveScreen.kt` layouts.

---

## 4. Phase 3 Implemented Changes (`3.1`..`3.5`)

1. **`3.1` Typed Cache & Background Category Filtering (`F-12`, `OPP-01`):**
   - Replaced raw `ConcurrentHashMap<String, Any>` in `Repository.kt` with `CacheEntry(typeTag, value)` + `getTypedCache`/`putTypedCache` (including `streamUrl`), and moved large (`> 400`) category filtering in `BrowseViewModel.kt` onto `Dispatchers.Default`.
2. **`3.2` ViewModel Key Scoping (`F-14`, `F-33`):**
   - Extended `appViewModel(key: String? = null, ...)` in `Locals.kt` and supplied route/item keys in `BrowseScreen`, `MovieDetailScreen`, and `SeriesDetailScreen`.
3. **`3.3` Repository Ignore Hygiene (`F-15`):**
   - Updated `.gitignore` to exclude build outputs, IDE files, `.apk`/`.aab`/`.zip` artifacts, Python caches, and temporary archive directories without touching user documentation or audit reports.
4. **`3.4` Build Modernization, Serialization & Safe ProGuard (`F-29`, `F-34`):**
   - Migrated `WeatherService.kt` from `org.json.JSONObject` to `@Serializable` DTOs via `kotlinx.serialization.json`.
   - Removed unused `androidx.work.runtime.ktx` dependency and dead `CoroutineWorker` ProGuard rule (`app/proguard-rules.pro`); centralized dependency versions in `gradle/libs.versions.toml`; migrated `kotlinOptions` to `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }` in `app/build.gradle.kts` while keeping `isMinifyEnabled = true`, `isShrinkResources = true`, and R8 active.
5. **`3.5` DAO Indexed Search & Regression Test Coverage (`F-20`, `OPP-03`..`OPP-06`):**
   - Added `CatalogMovieDao.searchCatalog` SQL query and pure-JVM test files (`Phase1SecurityAndSyncRegressionTest.kt`, `Phase2PerformanceAndUxRegressionTest.kt`, `Phase3ArchitectureAndModernizationTest.kt`, plus expanded `DatabaseMigrationRegressionTest.kt`, `RepositoryTest.kt`, `PrefsTest.kt`).

---

## 5. Independent Subagent Review & Resolution Log

Two independent read-only `research` subagents reviewed all 59 modified files + 4 new files and flagged 12 concrete defects prior to sign-off, **all 12 of which were fixed in working tree**:
- **Compose/UI Reviewer:** `appDp(2.5)` type mismatch + duplicate `onFocusChanged` import in `LivePreviewPanel.kt` (fixed with `appDp(Float)` overload in `LiveScreen.kt` and cleaned imports), double `onBack()` / restore-after-clear race in `PlayerScreen.kt` (fixed by snapshotting `initialPlayback` and adding `exited` guard), and concurrent `_state.update` overwrite window in `LiveViewModel.updateWithVisible` (fixed using `_state.update { latest -> ... }`).
- **Data/Security/Build Reviewer:** Unresolved `setShowBottomNavLabels` in `PrefsTest.kt` (replaced with `setShowRecents`), raw `cache` cast in `Repository.streamUrl` (fixed with `getTypedCache`), un-scoped `dao.observeLists()` in `SettingsCustomTvPanel.kt` (wired to active `accountId`), swallowed `IOException` in `XtreamApi.streamArray` (rethrown before JSON error mapping), orphaned job possibility in `SyncCoordinator.triggerFullSync`, stale `Player` on rebound `SahabPlaybackService`, existing-doc `aliases`/`immutableIfPopulated` collision in `FirebaseCatalogService.saveCatalogItem` (fixed with server snapshot diff + `FieldValue.arrayUnion`), unsynchronized `InMemorySharedPreferences` in `ApiKeyManager.kt`, and `forceNetwork = true` bypassing `inFlight` in `RepositoryTest.kt`.

---

## 6. Pending Verification & Next User Actions (Ready for Git Approval)

Because local Gradle/Java/SDK compilation and ADB release execution are prohibited by project policy until changes are pushed to GitHub Actions:
- **Blocked until Commit & Push:**
  1. GitHub Actions CI run (`./gradlew :app:lintRelease :app:testReleaseUnitTest :app:assembleRelease`)
  2. Installing the resulting Release APK on **Android TV (`Smart_TV_Pro`)** and **Samsung Galaxy A52s (`SM-A528B`)** to execute live E2E, D-Pad focus, M3U/Xtream switching, PiP, and Room upgrade verification.

### Recommended Clean Stage / Commit / Push Sequence (Only Intended Source, Resource, Config & Test Files)

```powershell
git add .gitignore firestore.rules gradle/libs.versions.toml EXECUTION_PHASES_LOG.md engineering-audit/FINAL_EXECUTION_REPORT.md `
  app/build.gradle.kts app/proguard-rules.pro app/src/main/res/xml/network_security_config.xml `
  app/src/main/java/com/saydlawy/sahab/ app/src/test/java/com/saydlawy/sahab/

git commit -m "feat(roadmap): complete phases 1-3 security, performance, tv-ux, and modernization hardening"
git pull --rebase origin main
git push origin main
```

> **Note:** Check `git status -s` for stray untracked root files (`", purge sports..."`, `"electable Sahab themes..."`, `preview.html`, etc.) and do **not** run `git add .` blindly.
