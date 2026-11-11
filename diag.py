import sqlite3
import sys
from datetime import datetime

DB = "egypt_league_2026_2027.db"

LEAGUES = [
    ("Egyptian PL",            "fixtures",                   "standings"),
    ("CAF Champions",          "caf_champions_fixtures",     "caf_champions_standings"),
    ("CAF Confederation",      "caf_confederation_fixtures", "caf_confederation_standings"),
    ("AFCON",                  "afcon_fixtures",             "afcon_standings"),
    ("AFCON Qualifiers",       "afcon_qualifiers_fixtures",  "afcon_qualifiers_standings"),
    ("EPL",                    "epl_fixtures",               "epl_standings"),
    ("La Liga",                "laliga_fixtures",            "laliga_standings"),
    ("Serie A",                "serie_a_fixtures",           "serie_a_standings"),
    ("Bundesliga",             "bundesliga_fixtures",        "bundesliga_standings"),
    ("Ligue 1",                "ligue_1_fixtures",           "ligue_1_standings"),
    ("Super Lig",              "super_lig_fixtures",         "super_lig_standings"),
    ("Saudi PL",               "saudi_fixtures",             "saudi_standings"),
    ("AFC CL Elite",           "afc_cl_fixtures",            "afc_cl_standings"),
    ("UCL",                    "ucl_fixtures",               "ucl_standings"),
    ("UEL",                    "uel_fixtures",               "uel_standings"),
    ("Euro",                   "euro_fixtures",              "euro_standings"),
    ("Asian Cup",              "asian_cup_fixtures",         "asian_cup_standings"),
    ("World Cup",              "world_cup_fixtures",         "world_cup_standings"),
    ("Arab Cup",               "arab_cup_fixtures",          "arab_cup_standings"),
]

def table_exists(c, name):
    c.execute("SELECT name FROM sqlite_master WHERE type='table' AND name=?", (name,))
    return c.fetchone() is not None

def show_summary(conn):
    c = conn.cursor()
    print("=" * 78)
    print(f"{'COMPETITION':<22} {'FIXTURES':>8} {'STANDINGS':>9}  {'FIRST':<11} {'LAST':<11}")
    print("=" * 78)
    for name, ft, st in LEAGUES:
        fc = sc = 0
        first = last = "-"
        if table_exists(c, ft):
            c.execute(f"SELECT COUNT(*), MIN(match_date), MAX(match_date) FROM {ft}")
            fc, first, last = c.fetchone()
            first = first or "-"
            last = last or "-"
        if table_exists(c, st):
            c.execute(f"SELECT COUNT(*) FROM {st}")
            sc = c.fetchone()[0]
        print(f"{name:<22} {fc:>8} {sc:>9}  {first:<11} {last:<11}")
    print("=" * 78)

def show_standings(conn, table, title):
    c = conn.cursor()
    if not table_exists(c, table):
        print(f"Table {table} not found.")
        return
    c.execute(f"""
        SELECT rank, team_name, played, won, drawn, lost,
               goals_for, goals_against, goal_difference, points
        FROM {table} ORDER BY rank ASC
    """)
    rows = c.fetchall()
    if not rows:
        print(f"No standings in {table}.")
        return
    print("=" * 74)
    print(f"STANDINGS: {title} ({len(rows)} teams)")
    print("=" * 74)
    print(f"{'#':>2}  {'TEAM':<26} {'P':>2} {'W':>2} {'D':>2} {'L':>2} {'GF':>3} {'GA':>3} {'GD':>3} {'PTS':>3}")
    print("-" * 74)
    for r in rows:
        gd = f"{r[8]:+d}" if r[8] is not None else "0"
        print(f"{r[0]:>2}  {r[1][:26]:<26} {r[2] or 0:>2} {r[3] or 0:>2} {r[4] or 0:>2} {r[5] or 0:>2} {r[6] or 0:>3} {r[7] or 0:>3} {gd:>3} {r[9] or 0:>3}")
    print("=" * 74)

def show_fixtures(conn, table, title, limit=20, upcoming_only=False):
    c = conn.cursor()
    if not table_exists(c, table):
        print(f"Table {table} not found.")
        return
    where = ""
    if upcoming_only:
        today = datetime.now().strftime("%Y-%m-%d")
        where = f"WHERE match_date >= '{today}' AND home_score IS NULL"
    c.execute(f"""
        SELECT match_date, match_time, home_team, away_team,
               home_score, away_score, round, status
        FROM {table} {where}
        ORDER BY match_date, match_time
        LIMIT {limit}
    """)
    rows = c.fetchall()
    if not rows:
        print(f"No fixtures in {table}.")
        return
    print("=" * 74)
    print(f"FIXTURES: {title} (showing {len(rows)})")
    print("=" * 74)
    print(f"{'DATE':<11} {'TIME':<5} {'HOME':<20} {'AWAY':<20} {'SCORE':<7} {'RD':<4}")
    print("-" * 74)
    for r in rows:
        score = f"{r[4]}-{r[5]}" if r[4] is not None and r[5] is not None else "vs"
        print(f"{r[0]:<11} {r[1] or '':<5} {r[2][:20]:<20} {r[3][:20]:<20} {score:<7} {str(r[6] or ''):<4}")
    print("=" * 74)

def show_logos(conn):
    c = conn.cursor()
    print("=" * 62)
    print(f"{'TABLE':<26} {'WITH LOGO':>10} {'MISSING':>8} {'COVERAGE':>10}")
    print("=" * 62)
    for name, ft, st in LEAGUES:
        if not table_exists(c, ft):
            continue
        c.execute(f"SELECT COUNT(*) FROM {ft} WHERE home_team_logo != '' AND home_team_logo IS NOT NULL")
        with_logo = c.fetchone()[0]
        c.execute(f"SELECT COUNT(*) FROM {ft}")
        total = c.fetchone()[0]
        missing = total - with_logo
        pct = f"{(with_logo/total*100):.1f}%" if total else "0%"
        print(f"{ft:<26} {with_logo:>10} {missing:>8} {pct:>10}")
    print("=" * 62)

def find_table(alias):
    alias_lower = alias.lower()
    for name, ft, st in LEAGUES:
        if alias_lower in name.lower() or alias_lower == ft.lower() or alias_lower == st.lower():
            return name, ft, st
    return None, None, None

def main():
    conn = sqlite3.connect(DB)

    if len(sys.argv) == 1:
        show_summary(conn)
    elif sys.argv[1] == "logos":
        show_logos(conn)
    elif sys.argv[1] == "standings" and len(sys.argv) > 2:
        name, ft, st = find_table(sys.argv[2])
        if not name:
            print(f"Unknown competition: {sys.argv[2]}")
        else:
            show_standings(conn, st, name)
    elif sys.argv[1] == "fixtures" and len(sys.argv) > 2:
        name, ft, st = find_table(sys.argv[2])
        if not name:
            print(f"Unknown competition: {sys.argv[2]}")
        else:
            limit = int(sys.argv[3]) if len(sys.argv) > 3 else 20
            show_fixtures(conn, ft, name, limit=limit)
    elif sys.argv[1] == "upcoming" and len(sys.argv) > 2:
        name, ft, st = find_table(sys.argv[2])
        if not name:
            print(f"Unknown competition: {sys.argv[2]}")
        else:
            limit = int(sys.argv[3]) if len(sys.argv) > 3 else 10
            show_fixtures(conn, ft, name, limit=limit, upcoming_only=True)
    else:
        print("Usage:")
        print("  python diag.py                       -> summary of all competitions")
        print("  python diag.py logos                 -> logo coverage per table")
        print("  python diag.py standings <comp>      -> standings for a competition")
        print("  python diag.py fixtures <comp> [N]   -> first N fixtures")
        print("  python diag.py upcoming <comp> [N]   -> next N unplayed fixtures")
        print()
        print("Competition aliases (partial match works):")
        for name, ft, st in LEAGUES:
            print(f"  {name}")

    conn.close()

if __name__ == "__main__":
    main()
