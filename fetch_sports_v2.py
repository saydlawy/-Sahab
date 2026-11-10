import zlib

def stable_id(val):
    if val is None:
        return 0
    try:
        iv = int(val)
        if 1 <= iv <= 2147483647:
            return iv
    except Exception:
        pass
    return zlib.crc32(str(val).encode('utf-8')) & 0x7FFFFFFF

import sqlite3
import requests
import time
from datetime import datetime, timedelta

class RateLimiter:
    def __init__(self, name, max_per_minute, max_per_day):
        self.name, self.max_per_minute, self.max_per_day = name, max_per_minute, max_per_day
        self.requests_this_minute, self.requests_today = 0, 0
        self.last_minute_reset = self.last_day_reset = time.time()

    def wait(self):
        now = time.time()
        if now - self.last_minute_reset >= 60:
            self.requests_this_minute = 0
            self.last_minute_reset = now
        if now - self.last_day_reset >= 86400:
            self.requests_today = 0
            self.last_day_reset = now
        if self.requests_this_minute >= self.max_per_minute:
            wait_time = 60 - (now - self.last_minute_reset)
            print(f"⏳ [{self.name}] انتظار {wait_time:.1f} ثانية...")
            time.sleep(wait_time)
            self.requests_this_minute = 0
            self.last_minute_reset = time.time()
        if self.requests_today >= self.max_per_day:
            print(f"🛑 [{self.name}] تم استهلاك الحد اليومي.")
            return False
        self.requests_this_minute += 1
        self.requests_today += 1
        return True

DB_NAME = "sports_db.sqlite"
conn = sqlite3.connect(DB_NAME)
cursor = conn.cursor()

# البطولات التي تملك جداول ترتيب فقط
STANDINGS_LEAGUES = {
    233: {"name": "الدوري المصري الممتاز", "tsdb": "4829"},
    307: {"name": "دوري روشن السعودي", "tsdb": "4668"},
    20: {"name": "كأس الكونفدرالية", "tsdb": "4817"},
    39: {"name": "الدوري الإنجليزي", "tsdb": "4328"},
    140: {"name": "الدوري الإسباني", "tsdb": "4335"},
    135: {"name": "الدوري الإيطالي", "tsdb": "4332"},
    78: {"name": "الدوري الألماني", "tsdb": "4331"},
    61: {"name": "الدوري الفرنسي", "tsdb": "4334"},
    203: {"name": "الدوري التركي", "tsdb": "4339"}
}

ALLOWED_LEAGUES = {
    233: "الدوري المصري الممتاز", 307: "دوري روشن السعودي",
    12: "دوري أبطال أفريقيا", 17: "دوري أبطال آسيا", 20: "كأس الكونفدرالية",
    2: "دوري أبطال أوروبا", 3: "الدوري الأوروبي", 6: "كأس الأمم الأفريقية",
    403: "كأس الأمم الأوروبية", 8: "كأس آسيا", 1: "كأس العالم", 452: "كأس العرب",
    39: "الدوري الإنجليزي", 140: "الدوري الإسباني", 135: "الدوري الإيطالي",
    78: "الدوري الألماني", 61: "الدوري الفرنسي", 203: "الدوري التركي"
}

def save_match(m):
    cursor.execute('''
        INSERT OR REPLACE INTO matches 
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

def fetch_morph_tv():
    print("\n🔄 [Morph TV] جلب المباريات...")
    try:
        res = requests.get("https://morph-tv.com/api/matches", timeout=15)
        if res.status_code == 200:
            count = 0
            for match in res.json().get('matches', []):
                comp = match.get('competition', '')
                lid = next((k for k, v in ALLOWED_LEAGUES.items() if v in comp or comp in v), None)
                if not lid:
                    if "Champions" in comp and "CAF" not in comp: lid = 2
                    elif "Europa" in comp: lid = 3
                    elif "Egyptian" in comp: lid = 233
                    elif "Saudi" in comp: lid = 307
                    else: continue
                
                date_part = match.get('kickoff', '').split('T')[0] if 'T' in match.get('kickoff', '') else ''
                time_part = match.get('kickoff', '').split('T')[1][:5] if 'T' in match.get('kickoff', '') else ''
                status = 'FINISHED' if match.get('status') == 'finished' else 'UPCOMING'
                if match.get('status') in ['live', 'inprogress']: status = 'LIVE'
                
                save_match({
                    'id': stable_id(match.get('id', '')), 'leagueId': lid, 'leagueName': ALLOWED_LEAGUES[lid],
                    'homeTeamId': stable_id(match.get('home', {}).get('id', '')), 'homeTeamName': match.get('home', {}).get('name', ''),
                    'homeTeamLogo': match.get('home', {}).get('badge', ''), 'awayTeamId': stable_id(match.get('away', {}).get('id', '')),
                    'awayTeamName': match.get('away', {}).get('name', ''), 'awayTeamLogo': match.get('away', {}).get('badge', ''),
                    'homeScore': match.get('score', {}).get('home'), 'awayScore': match.get('score', {}).get('away'),
                    'status': status, 'date': date_part, 'time': time_part
                })
                count += 1
            conn.commit()
            print(f"✅ [Morph TV] {count} مباراة.")
    except Exception as e:
        print(f"❌ [Morph TV] فشل: {e}")

def fetch_tsdb_standings():
    print("\n🔄 [TheSportsDB] جلب جداول الترتيب...")
    limiter = RateLimiter("TheSportsDB", 30, 1000)
    for lid, info in STANDINGS_LEAGUES.items():
        if not limiter.wait(): break
        try:
            res = requests.get(f"https://www.thesportsdb.com/api/v1/json/123/lookuptable.php?l={info['tsdb']}&s=2024-2025", timeout=10)
            if res.status_code == 200:
                table = res.json().get('table')
                if table:
                    cursor.execute("DELETE FROM standings WHERE leagueId = ?", (lid,))
                    for row in table:
                        cursor.execute('INSERT INTO standings VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)', (
                            lid, int(row.get('intRank', 0)), row.get('strTeam', ''), int(row.get('intPlayed', 0)),
                            int(row.get('intWin', 0)), int(row.get('intDraw', 0)), int(row.get('intLoss', 0)),
                            int(row.get('intGoalsFor', 0)), int(row.get('intGoalsAgainst', 0)),
                            int(row.get('intGoalDifference', 0)), int(row.get('intPoints', 0))
                        ))
                    conn.commit()
                    print(f"✅ جدول {info['name']}")
        except Exception:
            pass # تجاهل البطولات التي لا تملك جداول

def fetch_tsdb_teams():
    print("\n🔄 [TheSportsDB] جلب مباريات الفرق الكبرى...")
    limiter = RateLimiter("TheSportsDB", 30, 1000)
    # معرفات فرق الأهلي والزمالك وريال وبرشلونة وبايرن وليفربول
    teams = {"134363": "Al Ahly", "134365": "Zamalek", "133604": "Real Madrid", "133739": "Barcelona", "133664": "Bayern Munich", "133602": "Liverpool"}
    for tid, tname in teams.items():
        if not limiter.wait(): break
        try:
            res = requests.get(f"https://www.thesportsdb.com/api/v1/json/123/eventsnext.php?id={tid}", timeout=10)
            if res.status_code == 200:
                events = res.json().get('events')
                if events:
                    count = 0
                    for e in events:
                        save_match({
                            'id': stable_id(e.get('idEvent')), 'leagueId': 39, 'leagueName': e.get('strLeague', 'بطولة'),
                            'homeTeamId': stable_id(e.get('idHomeTeam')), 'homeTeamName': e.get('strHomeTeam', ''),
                            'awayTeamId': stable_id(e.get('idAwayTeam')), 'awayTeamName': e.get('strAwayTeam', ''),
                            'homeScore': None, 'awayScore': None, 'status': 'UPCOMING',
                            'date': e.get('dateEvent', ''), 'time': e.get('strTime', '')[:5] if e.get('strTime') else ''
                        })
                        count += 1
                    conn.commit()
                    print(f"✅ مباريات {tname} القادمة ({count})")
        except Exception:
            pass

def fetch_api_football(api_key):
    print("\n🔄 [API-Football] جلب مباريات الموسم الحالي (آخر وأول 15 يوم)...")
    if not api_key:
        print("⚠️ تخطي: لا يوجد مفتاح")
        return
    limiter = RateLimiter("API-Football", 10, 100)
    headers = {"x-apisports-key": api_key}
    today = datetime.now()
    from_date = (today - timedelta(days=15)).strftime('%Y-%m-%d')
    to_date = (today + timedelta(days=15)).strftime('%Y-%m-%d')
    
    for lid, lname in ALLOWED_LEAGUES.items():
        if not limiter.wait(): break
        try:
            # تجربة الموسم الحالي 2025 أو 2026
            res = requests.get(f"https://v3.football.api-sports.io/fixtures?league={lid}&season=2025&from={from_date}&to={to_date}", headers=headers, timeout=15)
            if res.status_code == 200:
                data = res.json()
                if data.get('errors'):
                    print(f"⚠️ تخطي {lname}: {data['errors']}")
                    continue
                fixtures = data.get('response', [])
                for f in fixtures:
                    fix, teams, goals = f.get('fixture', {}), f.get('teams', {}), f.get('goals', {})
                    st = fix.get('status', {}).get('short', '')
                    status = 'LIVE' if st in ['1H', '2H', 'HT', 'ET'] else 'FINISHED' if st in ['FT', 'AET', 'PEN'] else 'UPCOMING'
                    d = fix.get('date', '')
                    save_match({
                        'id': fix.get('id'), 'leagueId': lid, 'leagueName': lname,
                        'homeTeamId': teams.get('home', {}).get('id'), 'homeTeamName': teams.get('home', {}).get('name', ''),
                        'homeTeamLogo': teams.get('home', {}).get('logo', ''), 'awayTeamId': teams.get('away', {}).get('id'),
                        'awayTeamName': teams.get('away', {}).get('name', ''), 'awayTeamLogo': teams.get('away', {}).get('logo', ''),
                        'homeScore': goals.get('home'), 'awayScore': goals.get('away'), 'status': status, 'minute': str(fix.get('status', {}).get('elapsed', '')),
                        'date': d.split('T')[0] if 'T' in d else '', 'time': d.split('T')[1][:5] if 'T' in d else '',
                        'stadium': fix.get('venue', {}).get('name', 'غير محدد') if fix.get('venue') else 'غير محدد'
                    })
                conn.commit()
                print(f"✅ مباريات {lname} ({len(fixtures)})")
        except Exception as e:
            print(f"❌ خطأ في {lname}: {e}")

if __name__ == "__main__":
    print("="*40 + "\n⚽ أداة تجميع البيانات (النسخة 2)\n" + "="*40)
    api_key = input("🔑 أدخل مفتاح API-Football (Enter للتخطي): ").strip()
    
    fetch_morph_tv()
    fetch_tsdb_standings()
    fetch_tsdb_teams()
    fetch_api_football(api_key)
    
    print("\n" + "="*40 + f"\n🎉 الانتهاء! قاعدة البيانات: {DB_NAME}\n" + "="*40)
    cursor.execute("SELECT COUNT(*) FROM matches")
    print(f"إجمالي المباريات: {cursor.fetchone()[0]}")
    conn.close()
