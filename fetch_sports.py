import sqlite3
import requests
import time
import json
import os
from datetime import datetime, timedelta

class RateLimiter:
    def __init__(self, name, max_per_minute, max_per_day):
        self.name = name
        self.max_per_minute = max_per_minute
        self.max_per_day = max_per_day
        self.requests_this_minute = 0
        self.requests_today = 0
        self.last_minute_reset = time.time()
        self.last_day_reset = time.time()

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
            print(f"⏳ [{self.name}] انتظار {wait_time:.1f} ثانية لتفادي الحظر...")
            time.sleep(wait_time)
            self.requests_this_minute = 0
            self.last_minute_reset = time.time()
            
        if self.requests_today >= self.max_per_day:
            print(f"🛑 [{self.name}] تم استهلاك الحد اليومي. سيتم التوقف.")
            return False
            
        self.requests_this_minute += 1
        self.requests_today += 1
        return True

DB_NAME = "sports_db.sqlite"
conn = sqlite3.connect(DB_NAME)
cursor = conn.cursor()

cursor.execute('''
    CREATE TABLE IF NOT EXISTS matches (
        id INTEGER PRIMARY KEY,
        leagueId INTEGER,
        leagueName TEXT,
        leagueCountry TEXT,
        homeTeamId INTEGER,
        homeTeamName TEXT,
        homeTeamLogo TEXT,
        awayTeamId INTEGER,
        awayTeamName TEXT,
        awayTeamLogo TEXT,
        homeScore INTEGER,
        awayScore INTEGER,
        status TEXT,
        minute TEXT,
        date TEXT,
        time TEXT,
        stadium TEXT,
        channel TEXT,
        commentator TEXT,
        savedAt INTEGER
    )
''')
conn.commit()

cursor.execute('''
    CREATE TABLE IF NOT EXISTS standings (
        leagueId INTEGER,
        rank INTEGER,
        teamName TEXT,
        played INTEGER,
        win INTEGER,
        draw INTEGER,
        loss INTEGER,
        goalsFor INTEGER,
        goalsAgainst INTEGER,
        goalDifference INTEGER,
        points INTEGER
    )
''')
conn.commit()

ALLOWED_LEAGUES = {
    233: {"name": "الدوري المصري الممتاز", "tsdb": "4829"},
    307: {"name": "دوري روشن السعودي", "tsdb": "4668"},
    12: {"name": "دوري أبطال أفريقيا", "tsdb": "4490"},
    17: {"name": "دوري أبطال آسيا", "tsdb": "4508"},
    20: {"name": "كأس الكونفدرالية", "tsdb": "4817"},
    2: {"name": "دوري أبطال أوروبا", "tsdb": "4480"},
    3: {"name": "الدوري الأوروبي", "tsdb": "4481"},
    6: {"name": "كأس الأمم الأفريقية", "tsdb": "4497"},
    403: {"name": "كأس الأمم الأوروبية", "tsdb": "4502"},
    8: {"name": "كأس آسيا", "tsdb": "4509"},
    1: {"name": "كأس العالم", "tsdb": "4429"},
    452: {"name": "كأس العرب", "tsdb": "5128"},
    39: {"name": "الدوري الإنجليزي", "tsdb": "4328"},
    140: {"name": "الدوري الإسباني", "tsdb": "4335"},
    135: {"name": "الدوري الإيطالي", "tsdb": "4332"},
    78: {"name": "الدوري الألماني", "tsdb": "4331"},
    61: {"name": "الدوري الفرنسي", "tsdb": "4334"},
    203: {"name": "الدوري التركي", "tsdb": "4339"}
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
        m.get('channel', "غير محدد"), m.get('commentator', "غير محدد"),
        int(time.time() * 1000)
    ))

def fetch_morph_tv():
    print("\n🔄 جلب البيانات من Morph TV...")
    try:
        res = requests.get("https://morph-tv.com/api/matches", timeout=15)
        if res.status_code == 200:
            data = res.json()
            count = 0
            for match in data.get('matches', []):
                comp = match.get('competition', '')
                league_id = None
                for lid, info in ALLOWED_LEAGUES.items():
                    if info['name'] in comp or comp in info['name']:
                        league_id = lid
                        break
                if not league_id:
                    if "Champions" in comp and "CAF" not in comp: league_id = 2
                    elif "Europa" in comp: league_id = 3
                    elif "Egyptian" in comp: league_id = 233
                    elif "Saudi" in comp: league_id = 307
                    else: continue

                date_part = match.get('kickoff', '').split('T')[0] if 'T' in match.get('kickoff', '') else ''
                time_part = match.get('kickoff', '').split('T')[1][:5] if 'T' in match.get('kickoff', '') else ''

                status = 'FINISHED' if match.get('status') == 'finished' else 'UPCOMING'
                if match.get('status') in ['live', 'inprogress']: status = 'LIVE'

                save_match({
                    'id': hash(match.get('id', '')),
                    'leagueId': league_id,
                    'leagueName': ALLOWED_LEAGUES[league_id]['name'],
                    'homeTeamId': hash(match.get('home', {}).get('id', '')),
                    'homeTeamName': match.get('home', {}).get('name', ''),
                    'homeTeamLogo': match.get('home', {}).get('badge', ''),
                    'awayTeamId': hash(match.get('away', {}).get('id', '')),
                    'awayTeamName': match.get('away', {}).get('name', ''),
                    'awayTeamLogo': match.get('away', {}).get('badge', ''),
                    'homeScore': match.get('score', {}).get('home'),
                    'awayScore': match.get('score', {}).get('away'),
                    'status': status,
                    'date': date_part,
                    'time': time_part
                })
                count += 1
            conn.commit()
            print(f"✅ تم إضافة {count} مباراة من Morph TV.")
    except Exception as e:
        print(f"❌ فشل الاتصال بـ Morph TV: {e}")

def fetch_tsdb_standings():
    print("\n🔄 جلب جداول الترتيب من TheSportsDB...")
    limiter = RateLimiter("TheSportsDB", max_per_minute=30, max_per_day=1000)
    
    for lid, info in ALLOWED_LEAGUES.items():
        if not limiter.wait(): break
        tsdb_id = info['tsdb']
        try:
            url = f"https://www.thesportsdb.com/api/v1/json/123/lookuptable.php?l={tsdb_id}&s=2024-2025"
            res = requests.get(url, timeout=10)
            if res.status_code == 200:
                table = res.json().get('table')
                if table:
                    cursor.execute("DELETE FROM standings WHERE leagueId = ?", (lid,))
                    for row in table:
                        cursor.execute('''
                            INSERT INTO standings (leagueId, rank, teamName, played, win, draw, loss, goalsFor, goalsAgainst, goalDifference, points)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ''', (
                            lid,
                            int(row.get('intRank', 0)),
                            row.get('strTeam', ''),
                            int(row.get('intPlayed', 0)),
                            int(row.get('intWin', 0)),
                            int(row.get('intDraw', 0)),
                            int(row.get('intLoss', 0)),
                            int(row.get('intGoalsFor', 0)),
                            int(row.get('intGoalsAgainst', 0)),
                            int(row.get('intGoalDifference', 0)),
                            int(row.get('intPoints', 0))
                        ))
                    conn.commit()
                    print(f"✅ جدول {info['name']}")
                else:
                    print(f"⚠️ لا يوجد جدول لـ {info['name']}")
        except Exception as e:
            print(f"❌ خطأ في {info['name']}: {e}")

def fetch_api_football(api_key):
    print("\n🔄 جلب المباريات من API-Football (آخر وأول 15 يوم)...")
    if not api_key:
        print("⚠️ تم تخطي API-Football (لا يوجد مفتاح)")
        return
        
    limiter = RateLimiter("API-Football", max_per_minute=10, max_per_day=100)
    headers = {"x-apisports-key": api_key}
    
    today = datetime.now()
    from_date = (today - timedelta(days=15)).strftime('%Y-%m-%d')
    to_date = (today + timedelta(days=15)).strftime('%Y-%m-%d')
    
    for lid, info in ALLOWED_LEAGUES.items():
        if not limiter.wait(): break
        try:
            url = f"https://v3.football.api-sports.io/fixtures?league={lid}&season=2024&from={from_date}&to={to_date}"
            res = requests.get(url, headers=headers, timeout=15)
            if res.status_code == 200:
                data = res.json()
                if 'errors' in data and data['errors']:
                    print(f"⚠️ خطأ API-Football لـ {info['name']}: {data['errors']}")
                    continue
                    
                fixtures = data.get('response', [])
                for f in fixtures:
                    fix = f.get('fixture', {})
                    teams = f.get('teams', {})
                    goals = f.get('goals', {})
                    status_short = fix.get('status', {}).get('short', '')
                    
                    status = 'UPCOMING'
                    if status_short in ['1H', '2H', 'HT', 'ET', 'P', 'LIVE']: status = 'LIVE'
                    elif status_short in ['FT', 'AET', 'PEN']: status = 'FINISHED'
                    
                    date_str = fix.get('date', '')
                    
                    save_match({
                        'id': fix.get('id'),
                        'leagueId': lid,
                        'leagueName': info['name'],
                        'homeTeamId': teams.get('home', {}).get('id'),
                        'homeTeamName': teams.get('home', {}).get('name', ''),
                        'homeTeamLogo': teams.get('home', {}).get('logo', ''),
                        'awayTeamId': teams.get('away', {}).get('id'),
                        'awayTeamName': teams.get('away', {}).get('name', ''),
                        'awayTeamLogo': teams.get('away', {}).get('logo', ''),
                        'homeScore': goals.get('home'),
                        'awayScore': goals.get('away'),
                        'status': status,
                        'minute': str(fix.get('status', {}).get('elapsed', '')),
                        'date': date_str.split('T')[0] if 'T' in date_str else '',
                        'time': date_str.split('T')[1][:5] if 'T' in date_str else '',
                        'stadium': fix.get('venue', {}).get('name', 'غير محدد') if fix.get('venue') else 'غير محدد'
                    })
                conn.commit()
                print(f"✅ مباريات {info['name']} ({len(fixtures)} مباراة)")
        except Exception as e:
            print(f"❌ خطأ في {info['name']}: {e}")

if __name__ == "__main__":
    print("="*40)
    print("⚽ أهلاً بك في أداة تجميع بيانات كرة القدم")
    print("="*40)
    
    api_football_key = input("🔑 أدخل مفتاح API-Football (أو اضغط Enter للتخطي): ").strip()
    
    fetch_morph_tv()
    fetch_tsdb_standings()
    fetch_api_football(api_football_key)
    
    print("\n" + "="*40)
    print(f"🎉 تم الانتهاء! تم إنشاء قاعدة البيانات: {DB_NAME}")
    print("="*40)
    
    cursor.execute("SELECT COUNT(*) FROM matches")
    print(f"إجمالي المباريات: {cursor.fetchone()[0]}")
    cursor.execute("SELECT COUNT(*) FROM standings")
    print(f"إجمالي سجلات الترتيب: {cursor.fetchone()[0]}")
    
    conn.close()
