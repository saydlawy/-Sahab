import sqlite3
import os

DB = "egypt_league_2026_2027.db"

if not os.path.exists(DB):
    print(f"ERROR: {DB} not found.")
    exit(1)

size_kb = os.path.getsize(DB) / 1024
print("=" * 90)
print(f"DATABASE: {DB}")
print(f"SIZE: {size_kb:.1f} KB")
print("=" * 90)

conn = sqlite3.connect(DB)
c = conn.cursor()

# ============================================================
# SECTION 1: ALL TABLES
# ============================================================
print("\n[SECTION 1] ALL TABLES")
print("-" * 90)

c.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")
tables = [r[0] for r in c.fetchall()]

print(f"{'TABLE':<32} {'ROWS':>8}  {'COLUMNS':>8}  {'TYPE':<15}")
print("-" * 90)

for t in tables:
    if t.startswith("sqlite_"):
        continue
    c.execute(f"SELECT COUNT(*) FROM '{t}'")
    cnt = c.fetchone()[0]
    c.execute(f"PRAGMA table_info('{t}')")
    cols = len(c.fetchall())
    if t.endswith("_fixtures") or t == "fixtures":
        ttype = "fixtures"
    elif t.endswith("_standings") or t == "standings":
        ttype = "standings"
    else:
        ttype = "other"
    print(f"{t:<32} {cnt:>8}  {cols:>8}  {ttype:<15}")

# ============================================================
# SECTION 2: FIXTURES SUMMARY
# ============================================================
print("\n\n[SECTION 2] FIXTURES TABLES (date range + scores)")
print("-" * 90)
print(f"{'TABLE':<32} {'COUNT':>6}  {'FIRST':<11}  {'LAST':<11}  {'PLAYED':>7}  {'PENDING':>8}")
print("-" * 90)

fixture_tables = [t for t in tables if t.endswith("_fixtures") or t == "fixtures"]
for t in sorted(fixture_tables):
    try:
        c.execute(f"""
            SELECT COUNT(*), MIN(match_date), MAX(match_date),
                   SUM(CASE WHEN home_score IS NOT NULL THEN 1 ELSE 0 END)
            FROM {t}
        """)
        total, first, last, played = c.fetchone()
        pending = (total or 0) - (played or 0)
        print(f"{t:<32} {total or 0:>6}  {str(first or '-'):<11}  {str(last or '-'):<11}  {played or 0:>7}  {pending:>8}")
    except sqlite3.OperationalError as e:
        print(f"{t:<32} ERROR: {e}")

# ============================================================
# SECTION 3: STANDINGS AUDIT
# ============================================================
print("\n\n[SECTION 3] STANDINGS AUDIT (math validation)")
print("-" * 90)

standings_tables = [t for t in tables if t.endswith("_standings") or t == "standings"]
for t in sorted(standings_tables):
    try:
        c.execute(f"""
            SELECT rank, team_name, played, won, drawn, lost,
                   goals_for, goals_against, goal_difference, points
            FROM {t}
        """)
        rows = c.fetchall()
    except sqlite3.OperationalError:
        continue

    if not rows:
        print(f"\n{t}: EMPTY")
        continue

    bad_math = []
    dup_teams = {}
    for r in rows:
        rank, team, p, w, d, l, gf, ga, gd, pts = r
        p, w, d, l, pts = p or 0, w or 0, d or 0, l or 0, pts or 0
        issues = []
        if p != w + d + l:
            issues.append(f"P({p})!=W+D+L({w+d+l})")
        if pts != w * 3 + d:
            issues.append(f"PTS({pts})!=W*3+D({w*3+d})")
        if pts > p * 3:
            issues.append(f"PTS>{p*3}")
        if issues:
            bad_math.append((team, "; ".join(issues)))
        dup_teams[team] = dup_teams.get(team, 0) + 1

    dups = {k: v for k, v in dup_teams.items() if v > 1}
    status = "OK" if not bad_math and not dups else "ISSUES"
    print(f"\n{t}: {len(rows)} teams -> {status}")
    if dups:
        print(f"  DUPLICATE TEAMS: {dups}")
    if bad_math:
        print(f"  MATH ERRORS ({len(bad_math)}):")
        for team, reason in bad_math[:5]:
            print(f"    - {team}: {reason}")
        if len(bad_math) > 5:
            print(f"    ... and {len(bad_math) - 5} more")

# ============================================================
# SECTION 4: LOGO COVERAGE
# ============================================================
print("\n\n[SECTION 4] LOGO COVERAGE PER FIXTURES TABLE")
print("-" * 90)
print(f"{'TABLE':<32} {'TOTAL':>6}  {'WITH LOGO':>10}  {'MISSING':>8}  {'COVERAGE':>10}")
print("-" * 90)

for t in sorted(fixture_tables):
    try:
        c.execute(f"SELECT COUNT(*) FROM {t}")
        total = c.fetchone()[0]
        c.execute(f"SELECT COUNT(*) FROM {t} WHERE home_team_logo IS NOT NULL AND home_team_logo != ''")
        with_logo = c.fetchone()[0]
        missing = total - with_logo
        pct = f"{(with_logo/total*100):.1f}%" if total else "0%"
        print(f"{t:<32} {total:>6}  {with_logo:>10}  {missing:>8}  {pct:>10}")
    except sqlite3.OperationalError as e:
        print(f"{t:<32} ERROR")

# ============================================================
# SECTION 5: TEAMS TABLE
# ============================================================
print("\n\n[SECTION 5] TEAMS TABLE (Egyptian clubs)")
print("-" * 90)

if "teams" in tables:
    c.execute("SELECT COUNT(*) FROM teams")
    total = c.fetchone()[0]
    c.execute("SELECT COUNT(*) FROM teams WHERE logo_url IS NULL OR logo_url = ''")
    missing = c.fetchone()[0]
    print(f"Total teams: {total}")
    print(f"Teams with logo: {total - missing}")
    print(f"Teams missing logo: {missing}")
else:
    print("teams table not found.")

# ============================================================
# SECTION 6: ORPHAN FIXTURES (team not in teams table)
# ============================================================
print("\n\n[SECTION 6] ORPHAN CHECK (Egyptian PL only)")
print("-" * 90)

if "fixtures" in tables and "teams" in tables:
    c.execute("""
        SELECT COUNT(*) FROM fixtures
        WHERE home_team_id NOT IN (SELECT id FROM teams)
           OR away_team_id NOT IN (SELECT id FROM teams)
    """)
    orphans = c.fetchone()[0]
    print(f"Fixtures with broken team references: {orphans}")
else:
    print("Skipped (missing tables).")

# ============================================================
# SECTION 7: SAMPLE DATA (first row of each key table)
# ============================================================
print("\n\n[SECTION 7] SAMPLE ROW FROM EACH FIXTURES TABLE")
print("-" * 90)

for t in sorted(fixture_tables):
    try:
        c.execute(f"""
            SELECT match_date, match_time, home_team, away_team,
                   home_score, away_score, round
            FROM {t} ORDER BY match_date LIMIT 1
        """)
        row = c.fetchone()
        if row:
            print(f"{t}:")
            print(f"  {row[0]} {row[1]} | {row[2]} vs {row[3]} | {row[4]}-{row[5]} | Round {row[6]}")
    except sqlite3.OperationalError:
        pass

# ============================================================
# SECTION 8: SUMMARY COUNTS
# ============================================================
print("\n\n[SECTION 8] SUMMARY")
print("-" * 90)

total_fixtures = 0
total_standings = 0
for t in tables:
    if t.startswith("sqlite_"):
        continue
    c.execute(f"SELECT COUNT(*) FROM '{t}'")
    cnt = c.fetchone()[0]
    if t.endswith("_fixtures") or t == "fixtures":
        total_fixtures += cnt
    elif t.endswith("_standings") or t == "standings":
        total_standings += cnt

print(f"Total tables: {len(tables)}")
print(f"Total fixtures across all tables: {total_fixtures}")
print(f"Total standings rows across all tables: {total_standings}")
print(f"Database size: {size_kb:.1f} KB")
print("=" * 90)

conn.close()
print("\nAudit complete.")
