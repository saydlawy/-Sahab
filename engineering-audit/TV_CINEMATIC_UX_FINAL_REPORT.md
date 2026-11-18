# Sahab IPTV (`com.saydlawy.sahab`) — Cinematic Android TV UX Redesign & Real-Device QA Final Report

**Date:** 2026-11-18  
**Branch:** `main`  
**Final Verified Commit:** `9fca249` (`Build #232` — `versionCode=232`, `versionName=1.0.232`)  
**Previous Iteration Commits:** `2e2bd32` (`Build #231`), `1fc311d` (`Build #230`)  
**Test Device:** TCL `Smart TV Pro` (`G09_4K_GB`), Android `12`, `1920x1080` RTL Arabic (`10.161.76.138:5555`)

---

## 1. Executive Summary

Transformed Sahab IPTV's Android TV browsing experience into a global-standard cinematic TV interface across **Prime Cinema** (`PRIME`), **Netflix Row** (`NETFLIX`), and **Settings Appearance**, while completely removing **TiviMate Pro** (`TIVIMATE`) from the codebase and UI. Every change was built on GitHub Actions, verified by SHA-256, installed directly on the live TCL Android TV (`10.161.76.138:5555`) via `adb install -r` (preserving live IPTV credentials with zero `pm clear`), and validated end-to-end with real D-Pad/remote events and visual screenshot inspection.

---

## 2. Device & APK Verification

| Item | Observed Value |
|---|---|
| **Device Model** | `Smart TV Pro` (TCL, `10.161.76.138:5555`) |
| **Android Version** | Android `12` (`minSdk=24`, `targetSdk=36`) |
| **Final APK Build** | GitHub Actions Run `#232` (`44326244885`) — `completed` / `success` |
| **Installed Version** | `versionCode=232`, `versionName=1.0.232` |
| **Release Asset** | `Sahab.apk` (`7,565,229` bytes) |
| **SHA-256** | `C22331276622CFE16E12B78A27EB22820EEC228178676DE62DEB3DD18481A258` |
| **Cold Start (`am start -W`)** | `TotalTime: 2402ms`, `WaitTime: 2429ms` (`LaunchState: COLD`) |
| **UI Frame Stats (`dumpsys gfxinfo`)** | `Total frames: 4033`, `Janky: 463 (11.48%)`, `90th: 19ms`, `95th: 24ms`, `99th: 109ms`, `GPU 90th/95th/99th: 8ms / 10ms / 15ms` |
| **Crash / Fatal Logcat Check** | `0` `AndroidRuntime`, `FATAL`, or `ClassNotFoundException` hits |

---

## 3. Proof of TiviMate Removal

- `VodLayoutStyle.TIVIMATE` removed from `app/src/main/java/com/saydlawy/sahab/data/Models.kt`.
- Deleted `TiviMateBrowseLayout.kt` and `TiviMateDetailsPane.kt` under `ui/browse/layouts/tivimate/`.
- Updated `BrowseScreen.kt` switch and `SettingsAppearancePanel.kt` to a balanced 3-card selector (`سحاب الكلاسيكي`, `Prime Cinema`, `Netflix Row`).
- Verified via `git grep -n -i "tivimate" app/` -> **0 matches** (exit code 1).
- Verified visually on TCL TV in `tv_b231_settings.png`: only 3 balanced layout cards render.

---

## 4. Before vs. After Visual & UX Comparison

| Area | Before (`Build #230`) | After (`Build #232`) |
|---|---|---|
| **Prime Cinema** (`PRIME`) | Top chips overlapped hero; vertical `2:3` cards clipped Top 10 numerals (`1..4`); raw `duration` showed `.648` ms (`01:25:23.648`); `Build #231` clipped hero title on Top 10 focus. | `#00A8E1` (`PrimeCyan`) accents, `16:9` landscape cards with unclipped giant `1..10` numerals, `PrimeHeroInfo` anchored above `LazyVerticalGrid` (zero clipping on focus), clean Arabic runtime (`1س 25د`), TMDB synopsis live updates. |
| **Netflix Row** (`NETFLIX`) | Giant 100%-wide SearchBox + small boxed billboard + missing category chips; `Build #231` clipped hero title/badge/plot above `LazyColumn` due to `isDefaultEntry` scroll. | Full-bleed `NetflixBackdrop`, compact `PrimeCategoryTabs` top bar, `NetflixHeroInfo` (`N` badge, green rating, year/genre/runtime, 2-line plot, `التفاصيل` & `+ قائمتي` buttons) anchored cleanly above `LazyColumn` with zero clipping. |
| **Settings Appearance** | 4-card layout including `TiviMate Pro`. | Clean 3-card row (`سحاب الكلاسيكي`, `Prime Cinema`, `Netflix Row`). |
| **SearchBox D-Pad Focus** | `SearchBox` (`ابحث بالاسم`) trapped D-Pad `UP` and `LEFT`. | `.onPreviewKeyEvent` in `Fields.kt` exits `SearchBox` cleanly on `DPAD_UP`/`DPAD_DOWN` and empty-field `DPAD_LEFT`/`DPAD_RIGHT` (verified on both Prime & Netflix). |
| **Player OSD Timeout** | `CONTROLS_HIDE_MS` was `2,000ms` (too fast for TV remote). | Increased to `4,500ms` in `PlayerScreen.kt`. |

### Captured Real-TV Evidence Artifacts
- Before (`Build #230`): `tv_b230_home.png`, `tv_b230_settings3.png`, `tv_b230_settings_open.png`, `tv_b230_prime_movies.png`, `tv_b230_netflix.png`, `tv_b230_player_osd.png`
- Intermediate (`Build #231`): `tv_b231_settings.png`, `tv_b231_prime.png`, `tv_b231_prime_madden.png`, `tv_b231_netflix.png`, `tv_b231_details.png`, `tv_b231_vod_player.png`, `tv_b231_live.png`, `tv_b231_series.png`
- Final (`Build #232`): `tv_b232_home.png`, `tv_b232_prime.png`, `tv_b232_prime_focus.png`, `tv_b232_netflix.png`, `tv_b232_netflix_focus.png`, `tv_b232_live_player.png`

---

## 5. Prime vs. Netflix Template Comparison

| Dimension | Prime Cinema (`PRIME`) | Netflix Row (`NETFLIX`) |
|---|---|---|
| **Accent Color** | `PrimeCyan` (`#00A8E1`) | `NetflixRed` + `NetflixMatchGreen` |
| **Card Aspect Ratio** | `16:9` Landscape tiles | `2:3` Poster cards |
| **Top 10 Presentation** | Giant rank numerals (`1..10`) beside `16:9` cards | Numbered Top 10 horizontal shelf |
| **Hero Action Bar** | Clean metadata + `#Rank في الأعلى تقييماً` + `متاح ضمن اشتراكك` | `N` badge + rating/meta + `التفاصيل` & `+ قائمتي` buttons |
| **Hero Anchoring** | Anchored above `LazyVerticalGrid` (updates live on focus) | Anchored above `LazyColumn` (updates live on focus) |

---

## 6. Remote Control Button Test Table (TCL Smart TV Pro)

| Remote Key | Android Keycode | Tested Screen(s) | Status | Observed Behavior |
|---|---|---|---|---|
| `DPAD_UP` | `19` | Home, Prime, Netflix, SearchBox | **PASS** | Exits `SearchBox` upward and navigates rows cleanly |
| `DPAD_DOWN` | `20` | Prime, Netflix, Top 10 | **PASS** | Moves from Top Bar -> Chips -> Top 10 shelf without clipping hero |
| `DPAD_LEFT` | `21` | Prime, Netflix, SearchBox | **PASS** | Exits empty `SearchBox` to category chips; moves across cards in RTL |
| `DPAD_RIGHT` | `22` | Prime, Netflix, Rail | **PASS** | Moves right across cards and back to right navigation rail |
| `DPAD_CENTER` / `OK` | `23` | Details, VOD Player, Live Player | **PASS** | Opens details, launches playback, toggles Player OSD cleanly |
| `BACK` | `4` | Details, VOD Player, Live Player | **PASS** | Dismisses OSD / returns from Player -> Details -> Browse with focus restored |
| `MEDIA_PLAY_PAUSE` | `85` | VOD Player (`Build #231`) | **PASS** | Toggles play/pause cleanly with zero logcat errors |
| `MEDIA_FAST_FORWARD` | `90` | VOD Player (`Build #231`) | **PASS** | Seeks forward cleanly |
| `MEDIA_REWIND` | `89` | VOD Player (`Build #231`) | **PASS** | Seeks backward cleanly |
| `CHANNEL_UP` / `CHANNEL_DOWN` | `166` / `167` | Live Player (`Build #232`) | **PASS** | Sent cleanly in Live Player without crashes or fatal logcat errors |
| `VOLUME_UP` / `VOLUME_DOWN` / `MUTE` | `24` / `25` / `164` | Live Player (`Build #232`) | **PASS** | Handled by system audio without disrupting playback |
| `MENU` | `82` | Live Player (`Build #232`) | **PASS** | Handled without crash; OSD remained responsive |
| `MEDIA_STOP` / `MEDIA_NEXT` / `MEDIA_PREVIOUS` | `86` / `87` / `88` | — | **NOT TESTED** | Not dispatched via `adb` in this session (OSD `Prev`/`Next` buttons present in `tv_b231_vod_player.png` & `tv_b232_live_player.png`) |
| Numeric `0..9` | `7..16` | — | **NOT TESTED** | Not dispatched via `adb` in this session |

---

## 7. Screen & D-Pad Navigation Matrix

| Screen | Build Tested | Visual Layout | D-Pad Focus & Exit | Status |
|---|---|---|---|---|
| **HomeScreen** | `#230`, `#232` | OLED purple theme, Live 8-channel grid, `"أفلام مقترحة لك"` row | Focus restores cleanly on `Back` from Details | **PASS** |
| **Movies — Prime Cinema** | `#232` | `16:9` cards, `1..10` numerals, `1س 25د` runtime, unclipped Hero | Trap-free `SearchBox`, smooth Top 10 focus + backdrop crossfade | **PASS** |
| **Movies — Netflix Row** | `#232` | Full-bleed backdrop, `N` badge, green rating, anchored Hero, Top 10 row | Trap-free `SearchBox`, zero Hero clipping | **PASS** |
| **Movies / Series — Classic** | `#230`, `#231` | 6-column TV grid (`18,065` movies, `7,021` series) | Clean focus ring & scrolling | **PASS** |
| **Movie Details** | `#231` | Poster, `تشغيل` glowing pill, Favorite/Refresh, AI plot generator | `DPAD_CENTER` launches VOD playback | **PASS** |
| **Live TV (3-Pane RTL)** | `#231`, `#232` | Categories (right), `1,529` channels (middle), Preview + EPG (left) | Tap/Select launches Fullscreen Live Player | **PASS** |
| **VOD & Live Video Player** | `#231`, `#232` | `4.5s` OSD, Seek `-10/+10`, Aspect/Speed/Subtitles/Quality, `قائمة القنوات` | `DPAD_CENTER`, `BACK`, Seek, `Play/Pause`, `CH+/CH-` verified | **PASS** |
| **Settings — Appearance** | `#231` | 3 balanced layout cards (`CLASSIC`, `PRIME`, `NETFLIX`), TiviMate removed | Switching between Prime & Netflix applies immediately | **PASS** |
| **Favorites Screen** | — | Rail icon verified; dedicated Favorites screen not re-captured after Live Player | — | **NOT TESTED** |

---

## 8. Key Files Modified in Final UX Cycle (`2e2bd32` & `9fca249`)

- `app/src/main/java/com/saydlawy/sahab/data/Models.kt` — Removed `VodLayoutStyle.TIVIMATE`
- `app/src/main/java/com/saydlawy/sahab/ui/browse/BrowseScreen.kt` — Removed TiviMate branch
- `app/src/main/java/com/saydlawy/sahab/ui/browse/BrowseViewModel.kt` — Pre-fetch hero metadata on initial load
- `app/src/main/java/com/saydlawy/sahab/ui/settings/SettingsAppearancePanel.kt` — Balanced 3-card layout selector
- `app/src/main/java/com/saydlawy/sahab/ui/components/Fields.kt` — D-Pad focus-trap fix in `GlassTextField` / `SearchBox`
- `app/src/main/java/com/saydlawy/sahab/ui/player/PlayerScreen.kt` — Increased `CONTROLS_HIDE_MS` to `4_500L`
- `app/src/main/java/com/saydlawy/sahab/ui/browse/layouts/prime/PrimeHeroBanner.kt` — `PrimeCyan`, compact hero typography, `formatHeroRuntime()`
- `app/src/main/java/com/saydlawy/sahab/ui/browse/layouts/prime/PrimeBrowseLayout.kt` — `16:9` cards, Top 10 numerals, anchored Hero above `LazyVerticalGrid`
- `app/src/main/java/com/saydlawy/sahab/ui/browse/layouts/netflix/NetflixBillboard.kt` — Full-bleed `NetflixBackdrop` + `NetflixHeroInfo` + `formatHeroRuntime()`
- `app/src/main/java/com/saydlawy/sahab/ui/browse/layouts/netflix/NetflixBrowseLayout.kt` — Anchored `NetflixHeroInfo` above `LazyColumn`, compact category chips
- `app/src/main/java/com/saydlawy/sahab/ui/browse/layouts/netflix/NetflixTop10Row.kt` — Top 10 horizontal shelf
