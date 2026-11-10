import sqlite3
import requests
import time
from datetime import datetime, timedelta

DB_NAME = "sports_db.sqlite"

def get_db():
    return sqlite3.connect(DB_NAME, check_same_thread=False), sqlite3.connect(DB_NAME, check_same_thread=False).cursor()

def save_match_if_new(cursor, m):
    """يحفظ المباراة فقط إذا لم تكن موجودة (لا يمسح القديمة)"""
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
# المصدر 1: TheSportsDB eventsday.php (بدون مفتاح)
# ==========================================
def fetch_tsdb_today():
    print("\n🔄 [TheSportsDB] جلب مباريات اليوم من الدوريات المحددة...")
    conn = sqlite3.connect(DB_NAME)
    cursor = conn.cursor()
    
    today = datetime.now().strftime('%Y-%m-%d')
    # معرفات الدوريات في TheSportsDB التي نريد سد فجوتها
    leagues = {
        "4829": (233, "الدوري المصري الممتاز"),
        "4668": (307, "دوري روشن السعودي"),
        "4490": (12, "دوري أبطال أفريقيا"),
        "4817": (20, "كأس الكونفدرالية"),
        "4508": (17, "دوري أبطال آسيا"),
    }
    
    total_added = 0
    for tsdb_id, (lid, lname) in leagues.items():
        try:
            # eventsday.php يعطي جميع أحداث اليوم لدوري معين
            url = f"https://www.thesportsdb.com/api/v1/json/123/eventsday.php?d={today}&l={tsdb_id}"
            res = requests.get(url, timeout=10)
            if res.status_code == 200:
                data = res.json()
                events = data.get('events') or []
                added = 0
                for e in events:
                    match_data = {
                        'id': hash(e.get('idEvent')),
                        'leagueId': lid,
                        'leagueName': lname,
                        'homeTeamId': hash(e.get('idHomeTeam')),
                        'homeTeamName': e.get('strHomeTeam', ''),
                        'homeTeamLogo': e.get('strHomeTeamBadge', ''),
                        'awayTeamId': hash(e.get('idAwayTeam')),
                        'awayTeamName': e.get('strAwayTeam', ''),
                        'awayTeamLogo': e.get('strAwayTeamBadge', ''),
                        'homeScore': int(e.get('intHomeScore')) if e.get('intHomeScore') else None,
                        'awayScore': int(e.get('intAwayScore')) if e.get('intAwayScore') else None,
                        'status': 'FINISHED' if e.get('intHomeScore') else 'UPCOMING',
                        'date': e.get('dateEvent', today),
                        'time': (e.get('strTime') or '')[:5]
                    }
                    if save_match_if_new(cursor, match_data):
                        added += 1
                conn.commit()
                total_added += added
                print(f"✅ [TheSportsDB] {lname}: {added} مباراة جديدة (إجمالي اليوم {len(events)})")
            else:
                print(f"⚠️ [TheSportsDB] {lname}: HTTP {res.status_code}")
        except Exception as e:
            print(f"❌ [TheSportsDB] {lname}: {e}")
        time.sleep(2)  # احترام 30 طلب/دقيقة
    
    conn.close()
    print(f"\n📊 إجمالي الجديد من TheSportsDB: {total_added} مباراة.")

# ==========================================
# المصدر 2: Bzzoiro (لديك مفتاحه)
# ==========================================
def fetch_bzzoiro():
    print("\n🔄 [Bzzoiro] جلب المباريات الحية والقادمة...")
    token = input("🔑 أدخل مفتاح Bzzoiro (Enter للتخطي): ").strip()
    if not token:
        print("⚠️ تخطي Bzzoiro.")
        return
    
    conn = sqlite3.connect(DB_NAME)
    cursor = conn.cursor()
    headers = {"Authorization": f"Token {token}"}
    
    try:
        # جلب المباريات الحية
        res = requests.get("https://sports.bzzoiro.com/api/v2/events/live/", headers=headers, timeout=15)
        if res.status_code == 200:
            events = res.json() if isinstance(res.json(), list) else res.json().get('results', [])
            added = 0
            for e in events:
                match_data = {
                    'id': hash(str(e.get('id'))),
                    'leagueId': 39,  # افتراضي، يمكن تحسينه لاحقاً
                    'leagueName': e.get('league', {}).get('name', 'بطولة'),
                    'homeTeamId': hash(e.get('home_team', {}).get('name', '')),
                    'homeTeamName': e.get('home_team', {}).get('name', ''),
                    'awayTeamId': hash(e.get('away_team', {}).get('name', '')),
                    'awayTeamName': e.get('away_team', {}).get('name', ''),
                    'homeScore': e.get('home_score'),
                    'awayScore': e.get('away_score'),
                    'status': 'LIVE',
                    'date': e.get('start_time', '')[:10],
                    'time': e.get('start_time', '')[11:16]
                }
                if save_match_if_new(cursor, match_data):
                    added += 1
            conn.commit()
            print(f"✅ [Bzzoiro] {added} مباراة حية جديدة.")
        else:
            print(f"⚠️ [Bzzoiro] HTTP {res.status_code}")
    except Exception as e:
        print(f"❌ [Bzzoiro] فشل: {e}")
    finally:
        conn.close()

if __name__ == "__main__":
    print("="*50)
    print("🔧 أداة سد الفجوات (Gap Filler)")
    print("="*50)
    print("📌 هذه الأداة تضيف فقط المباريات الجديدة.")
    print("📌 لن تمسح أو تعدل أي بيانات موجودة.")
    print()
    
    start = time.time()
    fetch_tsdb_today()
    fetch_bzzoiro()
    
    elapsed = time.time() - start
    print("\n" + "="*50)
    print(f"🎉 اكتمل سد الفجوات في {elapsed:.1f} ثانية!")
    
    conn = sqlite3.connect(DB_NAME)
    cursor = conn.cursor()
    cursor.execute("SELECT COUNT(*) FROM matches")
    print(f"📊 إجمالي المباريات الآن: {cursor.fetchone()[0]}")
    conn.close()
    print("="*50)
