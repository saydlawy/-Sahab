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
BASE_URL = "https://openfootapi.com/v1"

COMPETITIONS = [
    {"id": "comp_egyptian_prem_egy", "leagueId": 233, "name": "الدوري المصري الممتاز"},
    {"id": "comp_saudi_pro_league_sau", "leagueId": 307, "name": "دوري روشن السعودي"},
    {"id": "comp_uefa_champions_league", "leagueId": 2, "name": "دوري أبطال أوروبا"},
    {"id": "comp_uefa_europa_league", "leagueId": 3, "name": "الدوري الأوروبي"},
    {"id": "comp_uefa_conference_league", "leagueId": 848, "name": "دوري المؤتمر الأوروبي"},
    {"id": "comp_fifa_world_cup", "leagueId": 1, "name": "كأس العالم"},
    {"id": "comp_premier_league_eng", "leagueId": 39, "name": "الدوري الإنجليزي الممتاز"},
    {"id": "comp_laliga_es", "leagueId": 140, "name": "الدوري الإسباني"},
    {"id": "comp_serie_a_it", "leagueId": 135, "name": "الدوري الإيطالي"},
    {"id": "comp_bundesliga_de", "leagueId": 78, "name": "الدوري الألماني"},
    {"id": "comp_ligue_1_fr", "leagueId": 61, "name": "الدوري الفرنسي"},
]

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

def try_fetch(url, headers):
    try:
        res = requests.get(url, headers=headers, timeout=15)
        if res.status_code == 200:
            return res.json()
        else:
            print(f"    ⚠️ HTTP {res.status_code}")
    except Exception as e:
        print(f"    ❌ {e}")
    return None

def fetch_future_matches(api_key):
    headers = {"Authorization": f"Bearer {api_key}", "Accept": "application/json"}
    conn = sqlite3.connect(DB_NAME)
    cursor = conn.cursor()
    total_added = 0

    today = datetime.now()
    date_str = today.strftime('%Y-%m-%d')
    # نطاق زمني: من اليوم حتى نهاية العام
    from_date = date_str
    to_date = f"{today.year}-12-31"

    for comp in COMPETITIONS:
        print(f"\n🔄 [{comp['name']}]...")
        added_for_comp = 0

        # المحاولة 1: استخدام date (تاريخ اليوم) مع status=scheduled
        url1 = f"{BASE_URL}/matches?competition={comp['id']}&date={date_str}&status=scheduled"
        data = try_fetch(url1, headers)
        if data and data.get('data'):
            matches = data['data']
            for m in matches:
                home = m.get('homeTeam', {})
                away = m.get('awayTeam', {})
                kickoff = m.get('kickoffAt', '')
                if save_match_if_new(cursor, {
                    'id': stable_id(m.get('id', '')), 'leagueId': comp['leagueId'], 'leagueName': comp['name'],
                    'homeTeamId': stable_id(home.get('id', '')), 'homeTeamName': home.get('name', ''),
                    'homeTeamLogo': home.get('logo', ''), 'awayTeamId': stable_id(away.get('id', '')),
                    'awayTeamName': away.get('name', ''), 'awayTeamLogo': away.get('logo', ''),
                    'homeScore': None, 'awayScore': None, 'status': 'UPCOMING',
                    'date': kickoff[:10] if kickoff else '', 'time': kickoff[11:16] if kickoff else ''
                }): added_for_comp += 1
            print(f"  ✅ (date) {added_for_comp} مباراة.")
        else:
            # المحاولة 2: استخدام from و to (نطاق زمني)
            url2 = f"{BASE_URL}/matches?competition={comp['id']}&from={from_date}&to={to_date}&status=scheduled"
            data = try_fetch(url2, headers)
            if data and data.get('data'):
                matches = data['data']
                for m in matches:
                    home = m.get('homeTeam', {})
                    away = m.get('awayTeam', {})
                    kickoff = m.get('kickoffAt', '')
                    if save_match_if_new(cursor, {
                        'id': stable_id(m.get('id', '')), 'leagueId': comp['leagueId'], 'leagueName': comp['name'],
                        'homeTeamId': stable_id(home.get('id', '')), 'homeTeamName': home.get('name', ''),
                        'homeTeamLogo': home.get('logo', ''), 'awayTeamId': stable_id(away.get('id', '')),
                        'awayTeamName': away.get('name', ''), 'awayTeamLogo': away.get('logo', ''),
                        'homeScore': None, 'awayScore': None, 'status': 'UPCOMING',
                        'date': kickoff[:10] if kickoff else '', 'time': kickoff[11:16] if kickoff else ''
                    }): added_for_comp += 1
                print(f"  ✅ (from/to) {added_for_comp} مباراة.")
            else:
                # المحاولة 3: استخدام season=2026 فقط (كما فعلنا سابقاً)
                url3 = f"{BASE_URL}/matches?competition={comp['id']}&season=2026&status=scheduled"
                data = try_fetch(url3, headers)
                if data and data.get('data'):
                    matches = data['data']
                    for m in matches:
                        home = m.get('homeTeam', {})
                        away = m.get('awayTeam', {})
                        kickoff = m.get('kickoffAt', '')
                        if save_match_if_new(cursor, {
                            'id': stable_id(m.get('id', '')), 'leagueId': comp['leagueId'], 'leagueName': comp['name'],
                            'homeTeamId': stable_id(home.get('id', '')), 'homeTeamName': home.get('name', ''),
                            'homeTeamLogo': home.get('logo', ''), 'awayTeamId': stable_id(away.get('id', '')),
                            'awayTeamName': away.get('name', ''), 'awayTeamLogo': away.get('logo', ''),
                            'homeScore': None, 'awayScore': None, 'status': 'UPCOMING',
                            'date': kickoff[:10] if kickoff else '', 'time': kickoff[11:16] if kickoff else ''
                        }): added_for_comp += 1
                    print(f"  ✅ (season) {added_for_comp} مباراة.")
                else:
                    print(f"  ⚠️ لم يتم العثور على مباريات مستقبلية.")

        conn.commit()
        total_added += added_for_comp

    conn.close()
    print(f"\n📊 إجمالي المباريات المستقبلية الجديدة: {total_added}")

if __name__ == "__main__":
    print("="*50)
    print("🏆 سحب المباريات المستقبلية (نسخة مصححة)")
    print("="*50)
    api_key = input("🔑 أدخل مفتاح OpenFootAPI (Enter للتجريبي): ").strip()
    if not api_key:
        api_key = "of_demo_openfootapi_docs"
    start = time.time()
    fetch_future_matches(api_key)
    print(f"\n🎉 اكتمل في {time.time()-start:.1f} ثانية.")
    conn = sqlite3.connect(DB_NAME)
    cursor = conn.cursor()
    cursor.execute("SELECT COUNT(*) FROM matches")
    print(f"📊 إجمالي المباريات: {cursor.fetchone()[0]}")
    conn.close()
