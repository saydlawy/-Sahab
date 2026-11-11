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
from datetime import datetime

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

def fetch_api_football(api_key):
    print("\n🔄 [API-Football] جلب أرشيف موسم 2024 بالكامل...")
    if not api_key:
        print("⚠️ تخطي: لا يوجد مفتاح")
        return
    limiter = RateLimiter("API-Football", 10, 100)
    headers = {"x-apisports-key": api_key}
    
    for lid, lname in ALLOWED_LEAGUES.items():
        if not limiter.wait(): break
        try:
            # تغيير الموسم إلى 2024 كما يطلب الـ API المجاني
            url = f"https://v3.football.api-sports.io/fixtures?league={lid}&season=2024"
            res = requests.get(url, headers=headers, timeout=15)
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
                print(f"✅ أرشيف {lname} ({len(fixtures)} مباراة) تم حفظها.")
        except Exception as e:
            print(f"❌ خطأ في {lname}: {e}")

if __name__ == "__main__":
    print("="*40 + "\n⚽ أداة تجميع البيانات (النسخة 3 - أرشيف 2024)\n" + "="*40)
    api_key = input("🔑 أدخل مفتاح API-Football (Enter للتخطي): ").strip()
    
    fetch_api_football(api_key)
    
    print("\n" + "="*40 + f"\n🎉 الانتهاء!\n" + "="*40)
    cursor.execute("SELECT COUNT(*) FROM matches")
    print(f"إجمالي المباريات في قاعدة البيانات الآن: {cursor.fetchone()[0]}")
    conn.close()
