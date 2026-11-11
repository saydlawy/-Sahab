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
BASE_URL = "https://soccer.highlightly.net/matches"

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

def fetch_egypt_matches(api_key, days_ahead=30):
    headers = {"x-rapidapi-key": api_key, "Accept": "application/json"}
    conn = sqlite3.connect(DB_NAME)
    cursor = conn.cursor()
    total_added = 0
    today = datetime.now()

    # محاولة 1: استخدام countryCode=EG (أكثر شمولاً)
    print("\n🔄 محاولة جلب مباريات الدوري المصري باستخدام countryCode=EG...")
    for i in range(days_ahead):
        target_date = (today + timedelta(days=i)).strftime('%Y-%m-%d')
        params = {"countryCode": "EG", "date": target_date, "limit": 100}
        try:
            res = requests.get(BASE_URL, headers=headers, params=params, timeout=15)
            if res.status_code == 200:
                data = res.json()
                matches = data.get('data', []) if isinstance(data.get('data'), list) else data.get('matches', [])
                added_today = 0
                for m in matches:
                    # استخراج الحقول (تختلف حسب استجابة Highlightly)
                    home = m.get('homeTeam', {}) or m.get('home', {})
                    away = m.get('awayTeam', {}) or m.get('away', {})
                    league = m.get('league', {}) or m.get('competition', {})
                    state = m.get('state', {})
                    desc = state.get('description', '')
                    if desc not in ['Not started', 'To be announced']:
                        continue  # تجاهل المباريات المنتهية أو الجارية
                    kickoff = m.get('date', target_date) + 'T' + m.get('time', '00:00')
                    if save_match_if_new(cursor, {
                        'id': stable_id(str(m.get('id', ''))),
                        'leagueId': 233,
                        'leagueName': league.get('name', 'الدوري المصري الممتاز'),
                        'homeTeamId': stable_id(str(home.get('id', ''))),
                        'homeTeamName': home.get('name', ''),
                        'homeTeamLogo': home.get('logo', ''),
                        'awayTeamId': stable_id(str(away.get('id', ''))),
                        'awayTeamName': away.get('name', ''),
                        'awayTeamLogo': away.get('logo', ''),
                        'homeScore': None, 'awayScore': None, 'status': 'UPCOMING',
                        'date': target_date, 'time': m.get('time', '00:00')
                    }):
                        added_today += 1
                conn.commit()
                total_added += added_today
                print(f"  📅 {target_date}: {added_today} مباراة مستقبلية.")
            else:
                print(f"  ⚠️ {target_date}: HTTP {res.status_code}")
        except Exception as e:
            print(f"  ❌ {target_date}: {e}")
        time.sleep(1)  # تجنب الحظر

    # محاولة 2: إذا لم نجد شيئاً، نجرب leagueName
    if total_added == 0:
        print("\n🔄 لم يتم العثور على مباريات باستخدام countryCode، جاري تجربة leagueName...")
        for i in range(days_ahead):
            target_date = (today + timedelta(days=i)).strftime('%Y-%m-%d')
            params = {"leagueName": "Egyptian Premier League", "date": target_date, "limit": 100}
            try:
                res = requests.get(BASE_URL, headers=headers, params=params, timeout=15)
                if res.status_code == 200:
                    data = res.json()
                    matches = data.get('data', []) if isinstance(data.get('data'), list) else data.get('matches', [])
                    added_today = 0
                    for m in matches:
                        home = m.get('homeTeam', {}) or m.get('home', {})
                        away = m.get('awayTeam', {}) or m.get('away', {})
                        state = m.get('state', {})
                        desc = state.get('description', '')
                        if desc not in ['Not started', 'To be announced']:
                            continue
                        if save_match_if_new(cursor, {
                            'id': stable_id(str(m.get('id', ''))), 'leagueId': 233, 'leagueName': 'الدوري المصري الممتاز',
                            'homeTeamId': stable_id(str(home.get('id', ''))), 'homeTeamName': home.get('name', ''),
                            'homeTeamLogo': home.get('logo', ''), 'awayTeamId': stable_id(str(away.get('id', ''))),
                            'awayTeamName': away.get('name', ''), 'awayTeamLogo': away.get('logo', ''),
                            'homeScore': None, 'awayScore': None, 'status': 'UPCOMING',
                            'date': target_date, 'time': m.get('time', '00:00')
                        }): added_today += 1
                    conn.commit()
                    total_added += added_today
                    print(f"  📅 {target_date}: {added_today} مباراة مستقبلية.")
            except Exception as e:
                print(f"  ❌ {target_date}: {e}")
            time.sleep(1)

    conn.close()
    print(f"\n📊 إجمالي المباريات المستقبلية الجديدة: {total_added}")

if __name__ == "__main__":
    print("="*50)
    print("🏆 Highlightly API - جلب مباريات الدوري المصري المستقبلية")
    print("="*50)
    print("📌 احصل على مفتاحك المجاني من: https://highlightly.net/dashboard")
    print("📌 الخطة المجانية: 100 طلب/يوم.")
    print("📌 السكريبت يجرب 30 يوماً قادمة.")
    print()
    
    api_key = input("🔑 أدخل مفتاح Highlightly API: ").strip()
    if not api_key:
        print("❌ يجب إدخال المفتاح.")
    else:
        start = time.time()
        fetch_egypt_matches(api_key, days_ahead=30)
        print(f"\n🎉 اكتمل في {time.time()-start:.1f} ثانية.")
        conn = sqlite3.connect(DB_NAME)
        cursor = conn.cursor()
        cursor.execute("SELECT COUNT(*) FROM matches")
        print(f"📊 إجمالي المباريات في القاعدة الآن: {cursor.fetchone()[0]}")
        conn.close()
