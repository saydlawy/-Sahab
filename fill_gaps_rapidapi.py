import sqlite3
import requests
import time
import json
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

# ==========================================
# 1. Free API Live Football Data (RapidAPI)
# ==========================================
def fetch_free_live_football(api_key):
    print("\n🔄 [Free Live Football] جلب مباريات اليوم...")
    if not api_key:
        print("⚠️ تخطي (لا يوجد مفتاح)")
        return
    conn = sqlite3.connect(DB_NAME); cursor = conn.cursor()
    today = datetime.now().strftime('%Y-%m-%d')
    
    headers = {
        "X-RapidAPI-Key": api_key,
        "X-RapidAPI-Host": "free-api-live-football-data.p.rapidapi.com"
    }
    
    try:
        # جلب مباريات اليوم
        url = f"https://free-api-live-football-data.p.rapidapi.com/football-get-matches-by-date?date={today}"
        res = requests.get(url, headers=headers, timeout=15)
        
        if res.status_code == 200:
            data = res.json()
            matches = data.get('response', {}).get('matches', []) if isinstance(data.get('response'), dict) else data.get('matches', [])
            added = 0
            for m in matches:
                # تحويل هيكل البيانات (يختلف حسب الـ API)
                home = m.get('home', {}) or m.get('homeTeam', {})
                away = m.get('away', {}) or m.get('awayTeam', {})
                league = m.get('league', {}) or m.get('competition', {})
                
                if save_match_if_new(cursor, {
                    'id': hash(str(m.get('id', ''))),
                    'leagueId': league.get('id', 0),
                    'leagueName': league.get('name', 'بطولة'),
                    'homeTeamId': hash(str(home.get('id', ''))),
                    'homeTeamName': home.get('name', ''),
                    'homeTeamLogo': home.get('logo', ''),
                    'awayTeamId': hash(str(away.get('id', ''))),
                    'awayTeamName': away.get('name', ''),
                    'awayTeamLogo': away.get('logo', ''),
                    'homeScore': m.get('homeScore') or m.get('score', {}).get('home'),
                    'awayScore': m.get('awayScore') or m.get('score', {}).get('away'),
                    'status': 'UPCOMING',
                    'date': m.get('date', today),
                    'time': m.get('time', '')
                }):
                    added += 1
            conn.commit()
            print(f"✅ [Free Live Football] {added} مباراة جديدة (من أصل {len(matches)})")
        else:
            print(f"⚠️ [Free Live Football] HTTP {res.status_code}: {res.text[:200]}")
    except Exception as e:
        print(f"❌ [Free Live Football] فشل: {e}")
    finally:
        conn.close()

# ==========================================
# 2. Big Balls Sports Data (RapidAPI)
# ==========================================
def fetch_bigballs(api_key):
    print("\n🔄 [Big Balls Sports] جلب مباريات اليوم...")
    if not api_key:
        print("⚠️ تخطي (لا يوجد مفتاح)")
        return
    conn = sqlite3.connect(DB_NAME); cursor = conn.cursor()
    today = datetime.now().strftime('%Y-%m-%d')
    
    headers = {
        "X-RapidAPI-Key": api_key,
        "X-RapidAPI-Host": "big-balls-sports-data.p.rapidapi.com"
    }
    
    try:
        # محاولة جلب المباريات (قد تحتاج تعديل الـ endpoint)
        url = f"https://big-balls-sports-data.p.rapidapi.com/soccer/matches?date={today}"
        res = requests.get(url, headers=headers, timeout=15)
        
        if res.status_code == 200:
            data = res.json()
            matches = data.get('data', []) or data.get('matches', [])
            added = 0
            for m in matches:
                home = m.get('home_team', {})
                away = m.get('away_team', {})
                league = m.get('league', {})
                
                if save_match_if_new(cursor, {
                    'id': hash(str(m.get('id', ''))),
                    'leagueId': league.get('id', 0),
                    'leagueName': league.get('name', 'بطولة'),
                    'homeTeamId': hash(str(home.get('id', ''))),
                    'homeTeamName': home.get('name', ''),
                    'homeTeamLogo': home.get('logo', ''),
                    'awayTeamId': hash(str(away.get('id', ''))),
                    'awayTeamName': away.get('name', ''),
                    'awayTeamLogo': away.get('logo', ''),
                    'homeScore': m.get('score', {}).get('home'),
                    'awayScore': m.get('score', {}).get('away'),
                    'status': 'UPCOMING',
                    'date': m.get('date', today),
                    'time': m.get('time', '')
                }):
                    added += 1
            conn.commit()
            print(f"✅ [Big Balls] {added} مباراة جديدة.")
        else:
            print(f"⚠️ [Big Balls] HTTP {res.status_code}: {res.text[:200]}")
    except Exception as e:
        print(f"❌ [Big Balls] فشل: {e}")
    finally:
        conn.close()

# ==========================================
# 3. API-Football (عبر RapidAPI - موسم 2024 كما هو مقيد)
# ==========================================
def fetch_api_football_rapid(api_key):
    print("\n🔄 [API-Football] (RapidAPI) جلب أرشيف إضافي...")
    if not api_key:
        print("⚠️ تخطي (لا يوجد مفتاح)")
        return
    conn = sqlite3.connect(DB_NAME); cursor = conn.cursor()
    
    headers = {
        "X-RapidAPI-Key": api_key,
        "X-RapidAPI-Host": "api-football-v1.p.rapidapi.com"
    }
    
    leagues = {
        233: "الدوري المصري الممتاز", 307: "دوري روشن السعودي",
        12: "دوري أبطال أفريقيا", 17: "دوري أبطال آسيا",
        20: "كأس الكونفدرالية", 2: "دوري أبطال أوروبا",
        3: "الدوري الأوروبي", 39: "الدوري الإنجليزي",
        140: "الدوري الإسباني", 135: "الدوري الإيطالي",
        78: "الدوري الألماني", 61: "الدوري الفرنسي",
        203: "الدوري التركي"
    }
    
    for lid, lname in leagues.items():
        try:
            url = f"https://api-football-v1.p.rapidapi.com/v3/fixtures?league={lid}&season=2024"
            res = requests.get(url, headers=headers, timeout=15)
            if res.status_code == 200:
                data = res.json()
                fixtures = data.get('response', [])
                for f in fixtures:
                    fix = f.get('fixture', {})
                    teams = f.get('teams', {})
                    goals = f.get('goals', {})
                    st = fix.get('status', {}).get('short', '')
                    status = 'LIVE' if st in ['1H','2H','HT','ET'] else 'FINISHED' if st in ['FT','AET','PEN'] else 'UPCOMING'
                    d = fix.get('date', '')
                    save_match_if_new(cursor, {
                        'id': fix.get('id'), 'leagueId': lid, 'leagueName': lname,
                        'homeTeamId': teams.get('home', {}).get('id'), 'homeTeamName': teams.get('home', {}).get('name', ''),
                        'homeTeamLogo': teams.get('home', {}).get('logo', ''), 'awayTeamId': teams.get('away', {}).get('id'),
                        'awayTeamName': teams.get('away', {}).get('name', ''), 'awayTeamLogo': teams.get('away', {}).get('logo', ''),
                        'homeScore': goals.get('home'), 'awayScore': goals.get('away'), 'status': status,
                        'minute': str(fix.get('status', {}).get('elapsed', '')),
                        'date': d.split('T')[0] if 'T' in d else '', 'time': d.split('T')[1][:5] if 'T' in d else ''
                    })
                conn.commit()
                print(f"✅ [API-Football] أرشيف {lname}: {len(fixtures)} مباراة.")
        except Exception as e:
            print(f"❌ [API-Football] {lname}: {e}")
        time.sleep(6)  # احترام حدود RapidAPI
    
    conn.close()

if __name__ == "__main__":
    print("="*50)
    print("🔧 سد الفجوات عبر RapidAPI (مصادر متعددة)")
    print("="*50)
    print("📌 احصل على مفاتيحك من: https://rapidapi.com/")
    print("📌 ابحث عن: Free API Live Football Data، Big Balls Sports Data")
    print()
    
    free_key = input("🔑 مفتاح Free API Live Football Data (Enter للتخطي): ").strip()
    bigballs_key = input("🔑 مفتاح Big Balls Sports Data (Enter للتخطي): ").strip()
    apifootball_key = input("🔑 مفتاح API-Football على RapidAPI (Enter للتخطي): ").strip()
    
    start = time.time()
    fetch_free_live_football(free_key)
    fetch_bigballs(bigballs_key)
    fetch_api_football_rapid(apifootball_key)
    
    print("\n" + "="*50)
    print(f"🎉 اكتمل في {time.time()-start:.1f} ثانية.")
    conn = sqlite3.connect(DB_NAME); cursor = conn.cursor()
    cursor.execute("SELECT COUNT(*) FROM matches")
    print(f"📊 إجمالي المباريات: {cursor.fetchone()[0]}")
    conn.close()
