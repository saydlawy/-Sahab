# أدوات وسكريبتات قاعدة بيانات الرياضة (`tools/`)

- **السكريبت الأحدث المعتمد لتوليد `app/src/main/assets/databases/sports_db`:** `tools/build_assets_db.py` (تم بناؤه ليتوافق مع Room `version = 1` وقاعدة `matches.id NOT NULL PRIMARY KEY`).
- **سكريبتات الفحص والتشخيص في الجذر:**
  - `audit_db.py` — فحص شمولي لعدد المباريات والترتيب وسلامة القاعدة المدمجة.
  - `diag.py` — تشخيص سريع لبيانات الدوريات في القاعدة.
- **السكريبتات المؤرشفة:** بقية ملفات `fetch_*.py` و`fill_gaps*.py` و`harvest_*.py` و`update_sports_db.py` محفوظة هنا كمرجع تاريخي فقط، بينما يتم تحديث البيانات لاحقاً عبر **Goal API** من خلال `MonthlySyncWorker`.

