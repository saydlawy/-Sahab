import sqlite3
import requests
import time
from datetime import datetime, timedelta

DB_NAME = "sports_db.sqlite"

def save_match_if_new(cursor, m):
    cursor.execute("SELECT id FROM matches WHERE id = ?", (m.get('id'),))
    if cursor.fetchone() is None:
        cursor.execute('''
            INSERT INTO matches 
            (id, leagueId, leagueName, leagueCountry, homeTeamId, homeTeamName, homeTeamLogo, 
             awayTeamId, awayTeamName, awayTeamLogo, homeScore, awayScore, status, minute, date, time, stadium, channel, commentator, savedAt)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ''', (
            m.get('id'), m.get('leagueId'), m.get('leagueName'), "", 
            m.get('homeTeamId'), m.get('homeTeamName'), m.get('homeTeamLogo', ""),
            m.get('awayTeamId'), m.get('awayTeamName'), m.get('awayTeamLogo', ""),
            m.get('homeScore'), m.get('awayScore'), m.get('status'), m.get('minute'),
            m.get('date'), m.get('time'), m.get('stadium', "غير محدد"), 
            m.get('channel', "غير محدد"), m.get('commentator', "غير محدد"), int(time.time() * 1000)
        ))
        return True
    return False

def fetch_espn():
    print("\n🔄 [ESPN] جلب مباريات اليوم...")
    conn = sqlite3.connect(DB_NAME); cursor = conn.cursor()
    today = datetime.now().strftime('%Y%m%d')
    leagues = {
        "egy.1": (233, "الدوري المصري الممتاز"), "ksa.1": (307, "دوري روشن السعودي"),
        "eng.1": (39, "الدوري الإنجليزي"), "esp.1": (140, "الدوري الإسباني"),
        "ita.1": (135, "الدوري الإيطالي"), "ger.1": (78, "الدوري الألماني"),
        "fra.1": (61, "الدوري الفرنسي"), "tur.1": (203, "الدوري التركي"),
        "uefa.champions": (2, "دوري أبطال أوروبا"), "uefa.europa": (3, "الدوري الأوروبي"),
        "caf.champions": (12, "دوري أبطال أفريقيا"),
    }
    total_added = 0
    for slug, (lid, lname) in leagues.items():
        try:
            url = f"https://site.api.espn.com/apis/site/v2/sports/soccer/{slug}/scoreboard?dates={today}"
            res = requests.get(url, timeout=10)
            if res.status_code == 200:
                events = res.json().get('events', [])
                added = 0
                for event in events:
                    comp = event.get('competitions', [{}])[0]
                    competitors = comp.get('competitors', [])
                    if len(competitors) < 2: continue
                    home = next((c for c in competitors if c.get('homeAway') == 'home'), None)
                    away = next((c for c in competitors if c.get('homeAway') == 'away'), None)
                    if not home or not away: continue
                    status_state = comp.get('status', {}).get('type', {}).get('state', 'pre')
                    status = 'LIVE' if status_state == 'in' else 'FINISHED' if status_state == 'post' else 'UPCOMING'
                    date_str = event.get('date', '')
                    if save_match_if_new(cursor, {
                        'id': hash(event.get('id', '')), 'leagueId': lid, 'leagueName': lname,
                        'homeTeamId': hash(home.get('team', {}).get('id', '')), 'homeTeamName': home.get('team', {}).get('displayName', ''),
                        'homeTeamLogo': home.get('team', {}).get('logo', ''), 'awayTeamId': hash(away.get('team', {}).get('id', '')),
                        'awayTeamName': away.get('team', {}).get('displayName', ''), 'awayTeamLogo': away.get('team', {}).get('logo', ''),
                        'homeScore': home.get('score'), 'awayScore': away.get('score'), 'status': status,
                        'date': date_str[:10], 'time': date_str[11:16]
                    }): added += 1
                conn.commit()
                total_added += added
                print(f"✅ [ESPN] {lname}: {added} جديدة (إجمالي {len(events)})")
        except Exception as e:
            print(f"❌ [ESPN] {lname}: {e}")
        time.sleep(0.5)
    conn.close()
    print(f"📊 إجمالي ESPN: {total_added}")

def fetch_tsdb_today():
    print("\n🔄 [TheSportsDB] جلب مباريات اليوم...")
    conn = sqlite3.connect(DB_NAME); cursor = conn.cursor()
    today = datetime.now().strftime('%Y-%m-%d')
    leagues = {"4829": (233, "الدوري المصري"), "4668": (307, "دوري روشن"), "4490": (12, "أبطال أفريقيا"), "4817": (20, "الكونفدرالية"), "4508": (17, "أبطال آسيا")}
    total = 0
    for tsdb_id, (lid, lname) in leagues.items():
        try:
            url = f"https://www.thesportsdb.com/api/v1/json/123/eventsday.php?d={today}&l={tsdb_id}"
            res = requests.get(url, timeout=10)
            if res.status_code == 200:
                events = res.json().get('events') or []
                added = 0
                for e in events:
                    if save_match_if_new(cursor, {
                        'id': hash(e.get('idEvent')), 'leagueId': lid, 'leagueName': lname,
                        'homeTeamId': hash(e.get('idHomeTeam')), 'homeTeamName': e.get('strHomeTeam', ''),
                        'homeTeamLogo': e.get('strHomeTeamBadge', ''), 'awayTeamId': hash(e.get('idAwayTeam')),
                        'awayTeamName': e.get('strAwayTeam', ''), 'awayTeamLogo': e.get('strAwayTeamBadge', ''),
                        'homeScore': int(e.get('intHomeScore')) if e.get('intHomeScore') else None,
                        'awayScore': int(e.get('intAwayScore')) if e.get('intAwayScore') else None,
                        'status': 'FINISHED' if e.get('intHomeScore') else 'UPCOMING',
                        'date': e.get('dateEvent', today), 'time': (e.get('strTime') or '')[:5]
                    }): added += 1
                conn.commit()
                total += added
                print(f"✅ [TSDB] {lname}: {added} جديدة")
        except Exception as e:
            print(f"❌ [TSDB] {lname}: {e}")
        time.sleep(2)
    conn.close()
    print(f"📊 إجمالي TheSportsDB: {total}")

def fetch_bzzoiro():
    print("\n🔄 [Bzzoiro] جلب المباريات القادمة...")
    token = input("🔑 مفتاح Bzzoiro (Enter للتخطي): ").strip()
    if not token:
        print("⚠️ تخطي"); return
    conn = sqlite3.connect(DB_NAME); cursor = conn.cursor()
    headers = {"Authorization": f"Token {token}"}
    try:
        res = requests.get("https://sports.bzzoiro.com/api/v2/events/?status=upcoming&limit=100", headers=headers, timeout=15)
        if res.status_code == 200:
            data = res.json()
            events = data if isinstance(data, list) else data.get('results', [])
            added = 0
            for e in events:
                if save_match_if_new(cursor, {
                    'id': hash(str(e.get('id'))), 'leagueId': e.get('league', {}).get('id', 0),
                    'leagueName': e.get('league', {}).get('name', ''), 'homeTeamId': hash(str(e.get('home_team', {}).get('id', ''))),
                    'homeTeamName': e.get('home_team', {}).get('name', ''), 'awayTeamId': hash(str(e.get('away_team', {}).get('id', ''))),
                    'awayTeamName': e.get('away_team', {}).get('name', ''), 'status': 'UPCOMING',
                    'date': e.get('start_time', '')[:10], 'time': e.get('start_time', '')[11:16]
                }): added += 1
            conn.commit()
            print(f"✅ [Bzzoiro] {added} مباراة قادمة جديدة.")
        else:
            print(f"⚠️ [Bzzoiro] HTTP {res.status_code}")
    except Exception as e:
        print(f"❌ [Bzzoiro] فشل: {e}")
    finally:
        conn.close()

def fetch_openligadb():
    print("\n🔄 [OpenLigaDB] جلب الدوري الألماني...")
    conn = sqlite3.connect(DB_NAME); cursor = conn.cursor()
    try:
        res = requests.get("https://api.openligadb.de/getmatchdata/bl1/2025", timeout=15)
        if res.status_code == 200:
            matches = res.json()
            added = 0
            for m in matches:
                if save_match_if_new(cursor, {
                    'id': m.get('matchID'), 'leagueId': 78, 'leagueName': 'الدوري الألماني',
                    'homeTeamId': hash(m.get('team1', {}).get('teamName', '')), 'homeTeamName': m.get('team1', {}).get('teamName', ''),
                    'homeTeamLogo': m.get('team1', {}).get('teamIconUrl', ''), 'awayTeamId': hash(m.get('team2', {}).get('teamName', '')),
                    'awayTeamName': m.get('team2', {}).get('teamName', ''), 'awayTeamLogo': m.get('team2', {}).get('teamIconUrl', ''),
                    'homeScore': m.get('goals', [{}])[-1].get('scoreTeam1') if m.get('goals') else None,
                    'awayScore': m.get('goals', [{}])[-1].get('scoreTeam2') if m.get('goals') else None,
                    'status': 'FINISHED' if m.get('goals') else 'UPCOMING',
                    'date': m.get('matchDateTime', '')[:10], 'time': m.get('matchDateTime', '')[11:16]
                }): added += 1
            conn.commit()
            print(f"✅ [OpenLigaDB] {added} مباراة ألمانية جديدة.")
    except Exception as e:
        print(f"❌ [OpenLigaDB] فشل: {e}")
    finally:
        conn.close()

if __name__ == "__main__":
    print("="*50 + "\n🔧 أداة سد الفجوات (مصادر متعددة)\n" + "="*50)
    start = time.time()
    fetch_espn()
    fetch_tsdb_today()
    fetch_bzzoiro()
    fetch_openligadb()
    print(f"\n🎉 اكتمل في {time.time()-start:.1f} ثانية.")
    conn = sqlite3.connect(DB_NAME); cursor = conn.cursor()
    cursor.execute("SELECT COUNT(*) FROM matches")
    print(f"📊 إجمالي المباريات: {cursor.fetchone()[0]}")
    conn.close()
