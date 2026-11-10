import sqlite3
import requests
import time
from datetime import datetime

DB_NAME = "sports_db.sqlite"
BASE_URL = "https://openfootapi.com/v1"

# المسابقات المدعومة في OpenFootAPI
COMPETITIONS = [
    {"id": "comp_egyptian_prem_egy", "leagueId": 233, "name": "الدوري المصري الممتاز"},
    {"id": "comp_saudi_pro_league_sau", "leagueId": 307, "name": "دوري روشن السعودي"},
    {"id": "comp_uefa_champions_league", "leagueId": 2, "name": "دوري أبطال أوروبا"},
    {"id": "comp_uefa_europa_league", "leagueId": 3, "name": "الدوري الأوروبي"},
    {"id": "comp_uefa_conference_league", "leagueId": 0, "name": "دوري المؤتمر الأوروبي"},
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

def fetch_future_matches(api_key):
    headers = {"Authorization": f"Bearer {api_key}", "Accept": "application/json"}
    conn = sqlite3.connect(DB_NAME)
    cursor = conn.cursor()
    total_added = 0

    for comp in COMPETITIONS:
        print(f"\n🔄 [{comp['name']}] جلب المباريات المستقبلية...")
        url = f"{BASE_URL}/matches?competition={comp['id']}&status=scheduled&season=2026"
        added_for_comp = 0
        page = 0

        while url and page < 10:  # حد أقصى 10 صفحات لكل بطولة
            try:
                res = requests.get(url, headers=headers, timeout=15)
                if res.status_code != 200:
                    print(f"  ⚠️ HTTP {res.status_code}")
                    break

                data = res.json()
                matches = data.get('data', [])

                for m in matches:
                    home = m.get('homeTeam', {})
                    away = m.get('awayTeam', {})
                    kickoff = m.get('kickoffAt', '')
                    match_data = {
                        'id': hash(m.get('id', '')),
                        'leagueId': comp['leagueId'],
                        'leagueName': comp['name'],
                        'homeTeamId': hash(home.get('id', '')),
                        'homeTeamName': home.get('name', ''),
                        'homeTeamLogo': home.get('logo', ''),
                        'awayTeamId': hash(away.get('id', '')),
                        'awayTeamName': away.get('name', ''),
                        'awayTeamLogo': away.get('logo', ''),
                        'homeScore': None,
                        'awayScore': None,
                        'status': 'UPCOMING',
                        'date': kickoff[:10] if kickoff else '',
                        'time': kickoff[11:16] if kickoff else ''
                    }
                    if save_match_if_new(cursor, match_data):
                        added_for_comp += 1

                conn.commit()
                page += 1
                meta = data.get('meta', {})
                next_cursor = meta.get('nextCursor')
                if next_cursor:
                    url = f"{BASE_URL}/matches?competition={comp['id']}&status=scheduled&season=2026&cursor={next_cursor}"
                else:
                    break

            except Exception as e:
                print(f"  ❌ خطأ: {e}")
                break

        total_added += added_for_comp
        print(f"  ✅ تم حفظ {added_for_comp} مباراة مستقبلية.")

    conn.close()
    print(f"\n📊 إجمالي المباريات المستقبلية الجديدة: {total_added}")

if __name__ == "__main__":
    print("="*50)
    print("🏆 سحب المباريات المستقبلية من OpenFootAPI")
    print("="*50)
    print("📌 المفتاح التجريبي: of_demo_openfootapi_docs (30 طلب/دقيقة)")
    print("📌 يُنصح باستخدام مفتاحك الخاص (5000 طلب/شهر) لتجنب البطء.")
    print()

    api_key = input("🔑 أدخل مفتاح OpenFootAPI (Enter للتجريبي): ").strip()
    if not api_key:
        api_key = "of_demo_openfootapi_docs"
        print("ℹ️ استخدام المفتاح التجريبي.")

    start = time.time()
    fetch_future_matches(api_key)
    print(f"\n🎉 اكتمل السحب في {time.time()-start:.1f} ثانية.")

    conn = sqlite3.connect(DB_NAME)
    cursor = conn.cursor()
    cursor.execute("SELECT COUNT(*) FROM matches")
    print(f"📊 إجمالي المباريات في قاعدة البيانات الآن: {cursor.fetchone()[0]}")
    conn.close()
