import sqlite3
import requests
import time
import json
from datetime import datetime, timedelta

# ==========================================
# إعدادات قاعدة البيانات (نفس الملف)
# ==========================================
DB_NAME = "sports_db.sqlite"

def get_db():
    conn = sqlite3.connect(DB_NAME, check_same_thread=False)
    return conn, conn.cursor()

def save_match(cursor, m):
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

# ==========================================
# 1. OpenLigaDB (بدون مفتاح - الدوري الألماني)
# ==========================================
def fetch_openligadb():
    print("\n🔄 [OpenLigaDB] جلب الدوري الألماني (الموسم الحالي 2025)...")
    conn, cursor = get_db()
    try:
        # جلب جميع مباريات الموسم الحالي
        res = requests.get("https://api.openligadb.de/getmatchdata/bl1/2025", timeout=15)
        if res.status_code == 200:
            matches = res.json()
            count = 0
            for m in matches:
                # استخراج النتيجة النهائية (آخر هدف مسجل)
                home_score, away_score = None, None
                if m.get('goals'):
                    last_goal = m['goals'][-1]
                    home_score = last_goal.get('scoreTeam1')
                    away_score = last_goal.get('scoreTeam2')
                
                date_str = m.get('matchDateTime', '')
                date_part = date_str[:10] if date_str else ''
                time_part = date_str[11:16] if date_str else ''
                
                save_match(cursor, {
                    'id': m.get('matchID'),
                    'leagueId': 78,
                    'leagueName': 'الدوري الألماني',
                    'homeTeamId': hash(m.get('team1', {}).get('teamName', '')),
                    'homeTeamName': m.get('team1', {}).get('teamName', ''),
                    'homeTeamLogo': m.get('team1', {}).get('teamIconUrl', ''),
                    'awayTeamId': hash(m.get('team2', {}).get('teamName', '')),
                    'awayTeamName': m.get('team2', {}).get('teamName', ''),
                    'awayTeamLogo': m.get('team2', {}).get('teamIconUrl', ''),
                    'homeScore': home_score,
                    'awayScore': away_score,
                    'status': 'FINISHED' if home_score is not None else 'UPCOMING',
                    'date': date_part,
                    'time': time_part
                })
                count += 1
            conn.commit()
            print(f"✅ [OpenLigaDB] {count} مباراة ألمانية تم حفظها.")
    except Exception as e:
        print(f"❌ [OpenLigaDB] فشل: {e}")
    finally:
        conn.close()

# ==========================================
# 2. ESPN Hidden API (بدون مفتاح - دوريات متعددة)
# ==========================================
def fetch_espn():
    print("\n🔄 [ESPN] جلب مباريات اليوم من الدوريات الكبرى...")
    conn, cursor = get_db()
    today = datetime.now().strftime('%Y%m%d')
    
    # قائمة الـ Slugs للدوريات التي ندعمها في ESPN
    espn_leagues = {
        "egy.1": (233, "الدوري المصري الممتاز"),
        "ksa.1": (307, "دوري روشن السعودي"),
        "eng.1": (39, "الدوري الإنجليزي"),
        "esp.1": (140, "الدوري الإسباني"),
        "ita.1": (135, "الدوري الإيطالي"),
        "ger.1": (78, "الدوري الألماني"),
        "fra.1": (61, "الدوري الفرنسي"),
        "tur.1": (203, "الدوري التركي"),
        "uefa.champions": (2, "دوري أبطال أوروبا"),
        "uefa.europa": (3, "الدوري الأوروبي"),
        "caf.champions": (12, "دوري أبطال أفريقيا"),
    }
    
    total_count = 0
    for slug, (lid, lname) in espn_leagues.items():
        try:
            url = f"https://site.api.espn.com/apis/site/v2/sports/soccer/{slug}/scoreboard?dates={today}"
            res = requests.get(url, timeout=10)
            if res.status_code == 200:
                data = res.json()
                events = data.get('events', [])
                for event in events:
                    comp = event.get('competitions', [{}])[0]
                    competitors = comp.get('competitors', [])
                    if len(competitors) < 2:
                        continue
                    
                    home = next((c for c in competitors if c.get('homeAway') == 'home'), None)
                    away = next((c for c in competitors if c.get('homeAway') == 'away'), None)
                    if not home or not away:
                        continue
                    
                    status_state = comp.get('status', {}).get('type', {}).get('state', 'pre')
                    status = 'LIVE' if status_state == 'in' else 'FINISHED' if status_state == 'post' else 'UPCOMING'
                    
                    date_str = event.get('date', '')
                    date_part = date_str[:10] if date_str else ''
                    time_part = date_str[11:16] if date_str else ''
                    
                    save_match(cursor, {
                        'id': hash(event.get('id', '')),
                        'leagueId': lid,
                        'leagueName': lname,
                        'homeTeamId': hash(home.get('team', {}).get('id', '')),
                        'homeTeamName': home.get('team', {}).get('displayName', ''),
                        'homeTeamLogo': home.get('team', {}).get('logo', ''),
                        'awayTeamId': hash(away.get('team', {}).get('id', '')),
                        'awayTeamName': away.get('team', {}).get('displayName', ''),
                        'awayTeamLogo': away.get('team', {}).get('logo', ''),
                        'homeScore': home.get('score'),
                        'awayScore': away.get('score'),
                        'status': status,
                        'date': date_part,
                        'time': time_part
                    })
                    total_count += 1
                print(f"✅ [ESPN] {lname}: {len(events)} مباراة.")
            else:
                print(f"⚠️ [ESPN] {lname}: HTTP {res.status_code}")
        except Exception as e:
            print(f"❌ [ESPN] {lname}: {e}")
        
        # تأخير بسيط لتجنب الحظر
        time.sleep(0.5)
    
    conn.commit()
    conn.close()
    print(f"\n✅ [ESPN] إجمالي {total_count} مباراة من الدوريات الكبرى.")

# ==========================================
# 3. TheSportsDB (مفتاح 123 - مباريات الفرق)
# ==========================================
def fetch_tsdb_teams():
    print("\n🔄 [TheSportsDB] جلب مباريات الفرق الكبرى القادمة...")
    conn, cursor = get_db()
    
    # معرفات الفرق الصحيحة في TheSportsDB (تم التحقق منها)
    teams = {
        "134363": "Al Ahly",
        "134365": "Zamalek",
        "133604": "Real Madrid",
        "133739": "Barcelona",
        "133664": "Bayern Munich",
        "133602": "Liverpool",
    }
    
    for tid, tname in teams.items():
        try:
            res = requests.get(
                f"https://www.thesportsdb.com/api/v1/json/123/eventsnext.php?id={tid}",
                timeout=10
            )
            if res.status_code == 200:
                events = res.json().get('events', [])
                if events:
                    for e in events:
                        save_match(cursor, {
                            'id': hash(e.get('idEvent')),
                            'leagueId': 39,
                            'leagueName': e.get('strLeague', 'بطولة'),
                            'homeTeamId': hash(e.get('idHomeTeam')),
                            'homeTeamName': e.get('strHomeTeam', ''),
                            'awayTeamId': hash(e.get('idAwayTeam')),
                            'awayTeamName': e.get('strAwayTeam', ''),
                            'homeScore': None,
                            'awayScore': None,
                            'status': 'UPCOMING',
                            'date': e.get('dateEvent', ''),
                            'time': e.get('strTime', '')[:5] if e.get('strTime') else ''
                        })
                    conn.commit()
                    print(f"✅ [TheSportsDB] {tname}: {len(events)} مباراة قادمة.")
                else:
                    print(f"⚠️ [TheSportsDB] {tname}: لا مباريات قادمة.")
        except Exception as e:
            print(f"❌ [TheSportsDB] {tname}: {e}")
        
        time.sleep(2)  # احترام حد 30 طلب/دقيقة
    
    conn.close()

# ==========================================
# 4. Sofascore (بدون مفتاح - محاولة بسيطة)
# ==========================================
def fetch_sofascore():
    print("\n🔄 [Sofascore] محاولة جلب مباريات اليوم...")
    conn, cursor = get_db()
    today = datetime.now().strftime('%Y-%m-%d')
    try:
        # Sofascore قد يحظر الطلبات بدون headers مناسبة
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Accept": "application/json"
        }
        res = requests.get(
            f"https://api.sofascore.com/api/v1/sport/football/scheduled-events/{today}",
            headers=headers,
            timeout=10
        )
        if res.status_code == 200:
            data = res.json()
            events = data.get('events', [])
            count = 0
            for e in events:
                # Sofascore يعطي بيانات كثيرة، نحتاج فلترتها
                # هنا نحفظ فقط الأحداث من دوريات معروفة
                tournament = e.get('tournament', {})
                unique_tournament = tournament.get('uniqueTournament', {})
                tid = unique_tournament.get('id')
                
                # خريطة تقريبية لبعض الدوريات (يمكن توسيعها)
                lid_map = {17: 39, 8: 140, 23: 135, 35: 78, 34: 61, 52: 2}
                lid = lid_map.get(tid)
                if not lid:
                    continue
                
                home = e.get('homeTeam', {})
                away = e.get('awayTeam', {})
                score = e.get('score', {})
                
                save_match(cursor, {
                    'id': e.get('id'),
                    'leagueId': lid,
                    'leagueName': tournament.get('name', 'بطولة'),
                    'homeTeamId': home.get('id'),
                    'homeTeamName': home.get('name', ''),
                    'awayTeamId': away.get('id'),
                    'awayTeamName': away.get('name', ''),
                    'homeScore': score.get('current'),
                    'awayScore': score.get('current'),
                    'status': 'LIVE' if e.get('status', {}).get('type') == 'inprogress' else 'UPCOMING',
                    'date': datetime.fromtimestamp(e.get('startTimestamp', 0)).strftime('%Y-%m-%d'),
                    'time': datetime.fromtimestamp(e.get('startTimestamp', 0)).strftime('%H:%M')
                })
                count += 1
            conn.commit()
            print(f"✅ [Sofascore] {count} مباراة من دوريات معروفة.")
        else:
            print(f"⚠️ [Sofascore] HTTP {res.status_code} - قد يكون محمياً بـ Cloudflare.")
    except Exception as e:
        print(f"❌ [Sofascore] فشل: {e}")
    finally:
        conn.close()

# ==========================================
# التشغيل الرئيسي
# ==========================================
if __name__ == "__main__":
    print("="*50)
    print("⚽ أداة تحديث قاعدة البيانات (مصادر مجانية - بدون مفاتيح)")
    print("="*50)
    print("📌 هذا السكريبت يعمل في جلسة Termux منفصلة.")
    print("📌 يمكن تشغيله بالتوازي مع السكريبت الأصلي.")
    print()
    
    start = time.time()
    
    # تنفيذ المصادر بالتتابع (لتجنب الحظر)
    fetch_openligadb()
    fetch_espn()
    fetch_tsdb_teams()
    fetch_sofascore()
    
    elapsed = time.time() - start
    print("\n" + "="*50)
    print(f"🎉 اكتمل التحديث في {elapsed:.1f} ثانية!")
    
    # إحصائيات نهائية
    conn, cursor = get_db()
    cursor.execute("SELECT COUNT(*) FROM matches")
    print(f"📊 إجمالي المباريات في قاعدة البيانات: {cursor.fetchone()[0]}")
    cursor.execute("SELECT COUNT(*) FROM standings")
    print(f"📊 إجمالي سجلات الترتيب: {cursor.fetchone()[0]}")
    conn.close()
    print("="*50)
