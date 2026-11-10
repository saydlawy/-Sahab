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

def fetch_league_fixtures(slug, lid, lname, from_date, to_date):
    print(f"\n🔄 [worldcup26] جلب مباريات {lname}...")
    conn = sqlite3.connect(DB_NAME)
    cursor = conn.cursor()
    
    # تحويل التاريخ إلى صيغة YYYYMMDD
    from_str = from_date.strftime('%Y%m%d')
    to_str = to_date.strftime('%Y%m%d')
    
    url = f"https://worldcup26.ir/get/soccer/{slug}/fixtures?from={from_str}&to={to_str}&limit=1000"
    try:
        res = requests.get(url, timeout=15)
        if res.status_code == 200:
            data = res.json()
            events = data.get('events', [])
            added = 0
            for e in events:
                # استخراج الفريقين من competitions -> competitors
                comp = e.get('competitions', [{}])[0]
                competitors = comp.get('competitors', [])
                if len(competitors) < 2:
                    continue
                home = next((c for c in competitors if c.get('homeAway') == 'home'), None)
                away = next((c for c in competitors if c.get('homeAway') == 'away'), None)
                if not home or not away:
                    continue
                
                # تحديد الحالة
                status_state = comp.get('status', {}).get('type', {}).get('state', 'pre')
                status = 'LIVE' if status_state == 'in' else 'FINISHED' if status_state == 'post' else 'UPCOMING'
                
                # التاريخ والوقت
                date_str = e.get('date', '')
                date_part = date_str[:10] if date_str else ''
                time_part = date_str[11:16] if date_str else ''
                
                if save_match_if_new(cursor, {
                    'id': stable_id(e.get('id', '')),
                    'leagueId': lid,
                    'leagueName': lname,
                    'homeTeamId': stable_id(home.get('team', {}).get('id', '')),
                    'homeTeamName': home.get('team', {}).get('displayName', ''),
                    'homeTeamLogo': home.get('team', {}).get('logo', ''),
                    'awayTeamId': stable_id(away.get('team', {}).get('id', '')),
                    'awayTeamName': away.get('team', {}).get('displayName', ''),
                    'awayTeamLogo': away.get('team', {}).get('logo', ''),
                    'homeScore': home.get('score'),
                    'awayScore': away.get('score'),
                    'status': status,
                    'date': date_part,
                    'time': time_part,
                    'stadium': comp.get('venue', {}).get('fullName', 'غير محدد')
                }):
                    added += 1
            conn.commit()
            print(f"✅ {lname}: {added} مباراة جديدة (من أصل {len(events)})")
        else:
            print(f"⚠️ {lname}: HTTP {res.status_code}")
    except Exception as e:
        print(f"❌ {lname}: {e}")
    finally:
        conn.close()

if __name__ == "__main__":
    print("="*50)
    print("🔧 سد فجوة الدوريات الأوروبية (worldcup26.ir)")
    print("="*50)
    print("📌 هذا المصدر لا يحتاج مفتاحاً.")
    print("📌 يغطي حالياً: الدوري الإنجليزي، الدوري الإسباني.")
    print("📌 لن يمسح أي بيانات موجودة، بل يضيف الجديد فقط.\n")
    
    today = datetime.now()
    future = today + timedelta(days=90)  # 3 أشهر قادمة
    
    start = time.time()
    # الدوري الإنجليزي
    fetch_league_fixtures("eng.1", 39, "الدوري الإنجليزي", today, future)
    time.sleep(2)
    # الدوري الإسباني
    fetch_league_fixtures("esp.1", 140, "الدوري الإسباني", today, future)
    
    print(f"\n🎉 اكتمل في {time.time()-start:.1f} ثانية.")
    conn = sqlite3.connect(DB_NAME)
    cursor = conn.cursor()
    cursor.execute("SELECT COUNT(*) FROM matches")
    print(f"📊 إجمالي المباريات الآن: {cursor.fetchone()[0]}")
    conn.close()
