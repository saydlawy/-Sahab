# Sahab IPTV — Agent Guidelines & Governing Rules (القواعد الحاكمة)

> **PRIORITY OVERRIDE (أولوية قصوى فوق كافة ملفات SKILL.md):**
> Any external or bundled `SKILL.md` file (including `r8-analyzer`, `edge-to-edge`, etc.) that suggests running `./gradlew assembleRelease`, `./gradlew bundle*`, or `./gradlew build` locally **MUST BE OVERRIDDEN** by Section 0 below. **Never build APK/AAB locally with `./gradlew`. All APK builds MUST run on GitHub Actions only.**

## Project Overview
- **App Name:** سحاب (Sahab IPTV)
- **Package / applicationId:** `com.saydlawy.sahab`
- **Stack:** Kotlin, Jetpack Compose, Material 3, Room, DataStore, Firebase (Auth/Firestore/Messaging), Coil, Media3/ExoPlayer.
- **Form Factors:** Android TV (`TABLET_OR_TV`), Tablet, Phone Landscape (`PHONE_LANDSCAPE`), Phone Portrait (`PHONE_PORTRAIT`), RTL Arabic layout.

---

## 0. القواعد الحاكمة (غير قابلة للتفاوض)

1. **ممنوع إنشاء أي فرع جديد (`main` only):**
   - العمل والدفع على `main` مباشرة فقط. لا `git checkout -b` ولا `git switch -c` ولا `worktree` ولا فرع عبر واجهة GitHub.
2. **ممنوع بناء APK محلياً:**
   - لا `./gradlew assemble*` ولا `bundle*` ولا أي أمر يخرج APK/AAB على الجهاز المحلي.
   - مسموح محلياً: قراءة الكود، التعديل، `git`، `gh` (أو GitHub REST API)، `adb`.
   - التحقق من الترجمة والاختبارات يتم من نتيجة **GitHub Actions** فقط.
3. **البناء عبر GitHub Actions فقط:**
   - الـ Workflow يُنتج Release؛ يتم تحميل الـ APK من الـ Release وتثبيته عبر `adb install -r`.
4. **التحقق من الادعاء قبل الإصلاح:**
   - أي بند موسوم `[خارج الجزء المرئي من المرفق]` أو `[خارج المرفق]` يجب فتح ملفه الحقيقي أولاً، وإثبات صحة الادعاء (سطر "تم التأكيد / غير صحيح / صحيح جزئياً" مع رقم السطر في `CHANGELOG-AUDIT.md`)، ثم إصلاحه أو تخطيه بسبب مكتوب.
5. **القرارات الحرجة تُنفَّذ كما هي في التقرير بدون سؤال:**
   - (مثل `fallbackToDestructiveMigration` وإضافة `accountId` وتعديل `firestore.rules`)، مع تسجيل كل قرار وسببه المختصر في `CHANGELOG-AUDIT.md`.
6. **الحفاظ على الإيجابيات الموثّقة:**
   - لا تعدّل `utcDateToLocalYmd`، `formatUtcClockToLocal`، `CleartextGuardInterceptor`، `RetryInterceptor`، `DreamyBackground`، `Ic`، `restorableFocus`، `ChannelLogoResolver` إلا إذا كسر شيء.
7. **التزامات Git:**
   - Commit منفصل لكل مهمة، رسالته تبدأ بمعرّف المهمة (مثل: `fix(C-02): ...`).
   - `git pull --rebase origin main` قبل كل `push`.
   - ممنوع `--force` أو `reset --hard` على `main`.
   - Commit فقط ملفات الكود/الموارد/التكوين المقصودة؛ ممنوع رفع سكريبتات `.py` التشخيصية المؤقتة، ملفات `.bak*`، `_nova_diag/`، `firebase-service-account.json`، `*.jks`، `*.keystore`، أو `keystore.b64`.
8. **لا أسرار في الكود أو اللوجات:**
   - `google-services.json` ومفاتيح Gemini/TMDB تبقى في GitHub Secrets.

---

## 0.1 بيئة الاختبار (`ADB`)

| الجهاز | الاتصال / المعرف | الدور |
|---|---|---|
| شاشة تلفاز Android TV (`Smart_TV_Pro`) | `10.19.124.138:5555` (`adb connect <IP>:5555`) | اختبار D-Pad والتركيز وتجربة الشاشة الكبيرة |
| Samsung Galaxy A52s (`SM-A528B`) | `RZCT504YSEM` (USB أو لاسلكي) | اختبار الهاتف (Portrait / Landscape) |

- تحقق من الاتصال: `adb devices` واستخدم `adb -s <serial>` دائماً.
- لقطة شاشة: `adb -s <serial> exec-out screencap -p > shot.png`
- اللوج: `adb -s <serial> logcat -c` قبل الاختبار، ثم فحص `Sahab|AndroidRuntime|FATAL|ClassNotFoundException`.
- محاكاة D-Pad: `adb -s <serial> shell input keyevent KEYCODE_DPAD_DOWN|UP|LEFT|RIGHT|CENTER|BACK`
- تنظيف الحالة: `adb -s <serial> shell pm clear com.saydlawy.sahab`

---

## 0.2 دورة العمل لكل دفعة (Batch)

1. نفّذ المهام وCommit لكل واحدة بمعرّف المهمة.
2. `git pull --rebase origin main` ثم `git push origin main`.
3. راقب الـ Workflow حتى الاكتمال (`completed`).
4. فشل الـ Workflow: اقرأ اللوج الفاشل، أصلح، Commit، ادفع، وكرر. لا تنتقل للاختبار قبل نجاح البناء.
5. حمّل الـ APK من GitHub Release (`latest` / tag) وثبّته على الجهازين: `adb -s <serial> install -r <apk>`.
6. نفّذ سيناريوهات الاختبار على **الجهازين** ودوّن النتيجة في `CHANGELOG-AUDIT.md` (المعرّف، الحالة، Commit hash، نتيجة الاختبار على التلفاز، نتيجة الاختبار على A52s).
7. فشل أي اختبار: افتح مهمة إصلاح في نفس الدفعة قبل الانتقال لما بعدها.

---

## Agent skills

### Issue tracker
Local markdown files under `.scratch/<feature>/tickets/`, `CHANGELOG-AUDIT.md`, and `docs/`. See `docs/agents/issue-tracker.md`.

### Triage labels
Standard categories (`feature`, `ui-ux`, `refactor`, `bug`) and canonical triage roles. See `docs/agents/triage-labels.md`.

### Domain docs
Single-context layout with `docs/GLOSSARY.md`, `docs/adr/`, and `design-system/app/MASTER.md`. See `docs/agents/domain.md`.
