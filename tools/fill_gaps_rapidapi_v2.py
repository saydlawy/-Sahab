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

def parse_standard(m, lid, lname):
    """محاولة استخراج الحقول من هياكل JSON المختلفة"""
    home = m.get('home') or m.get('homeTeam') or m.get('home_team') or {}
    away = m.get('away') or m.get('awayTeam') or m.get('away_team') or {}
    if isinstance(home, dict) and 'team' in home: home = home['team']
    if isinstance(away, dict) and 'team' in away: away = away['team']
    league = m.get('league') or m.get('tournament') or m.get('competition') or {}
    if isinstance(league, dict) and 'uniqueTournament' in league: league = league['uniqueTournament']

    date_str = m.get('date') or m.get('startTime') or m.get('start_time') or m.get('utcDate') or ''
    return {
        'id': stable_id(str(m.get('id') or m.get('idEvent') or m.get('match_id') or '')),
        'leagueId': lid, 'leagueName': lname or league.get('name', 'بطولة'),
        'homeTeamId': stable_id(str(home.get('id', ''))), 'homeTeamName': home.get('name') or home.get('shortName', ''),
        'homeTeamLogo': home.get('logo') or home.get('crest') or home.get('badge', ''),
        'awayTeamId': stable_id(str(away.get('id', ''))), 'awayTeamName': away.get('name') or away.get('shortName', ''),
        'awayTeamLogo': away.get('logo') or away.get('crest') or away.get('badge', ''),
        'homeScore': m.get('homeScore') or m.get('home_score'), 'awayScore': m.get('awayScore') or m.get('away_score'),
        'status': 'UPCOMING',
        'date': date_str[:10] if date_str else '',
        'time': date_str[11:16] if date_str else ''
    }

# 1. SportAPI (Sofascore)
def fetch_sportapi(api_key, days=7):
    print("\n🔄 [SportAPI] جلب مباريات الأيام القادمة...")
    if not api_key: print("⚠️ تخطي"); return
    conn = sqlite3.connect(DB_NAME); cursor = conn.cursor()
    headers = {"X-RapidAPI-Key": api_key, "X-RapidAPI-Host": "sportapi7.p.rapidapi.com"}
    total = 0
    for i in range(days):
        date_str = (datetime.now() + timedelta(days=i)).strftime('%Y-%m-%d')
        try:
            res = requests.get(f"https://sportapi7.p.rapidapi.com/api/v1/sport/football/scheduled-events/{date_str}", headers=headers, timeout=15)
            if res.status_code == 200:
                added = 0
                for event in res.json().get('events', []):
                    tournament = event.get('tournament', {}).get('uniqueTournament', {})
                    if save_match_if_new(cursor, parse_standard(event, 0, tournament.get('name', 'بطولة'))): added += 1
                conn.commit(); total += added
                print(f"  ✅ {date_str}: {added} مباراة جديدة.")
        except Exception as e: print(f"  ❌ {date_str}: {e}")
        time.sleep(1)
    conn.close(); print(f"📊 إجمالي SportAPI: {total}")

# 2. SofaScore API
def fetch_sofascore(api_key, days=7):
    print("\n🔄 [SofaScore] جلب مباريات الأيام القادمة...")
    if not api_key: print("⚠️ تخطي"); return
    conn = sqlite3.connect(DB_NAME); cursor = conn.cursor()
    headers = {"X-RapidAPI-Key": api_key, "X-RapidAPI-Host": "sofascore.p.rapidapi.com"}
    total = 0
    for i in range(days):
        date_str = (datetime.now() + timedelta(days=i)).strftime('%Y-%m-%d')
        try:
            res = requests.get(f"https://sofascore.p.rapidapi.com/api/sofascore/v1/match/list?date={date_str}", headers=headers, timeout=15)
            if res.status_code == 200:
                added = 0
                for event in res.json().get('events', []):
                    tournament = event.get('tournament', {}).get('uniqueTournament', {})
                    if save_match_if_new(cursor, parse_standard(event, 0, tournament.get('name', 'بطولة'))): added += 1
                conn.commit(); total += added
                print(f"  ✅ {date_str}: {added} مباراة جديدة.")
        except Exception as e: print(f"  ❌ {date_str}: {e}")
        time.sleep(1)
    conn.close(); print(f"📊 إجمالي SofaScore: {total}")

# 3. Football - Soccer (SteadyAPI)
def fetch_steadyapi(api_key):
    print("\n🔄 [Football - Soccer] جلب المباريات القادمة...")
    if not api_key: print("⚠️ تخطي"); return
    conn = sqlite3.connect(DB_NAME); cursor = conn.cursor()
    headers = {"X-RapidAPI-Key": api_key, "X-RapidAPI-Host": "football-soccer4.p.rapidapi.com"}
    try:
        res = requests.get("https://football-soccer4.p.rapidapi.com/v1/matches-upcoming", headers=headers, timeout=15)
        if res.status_code == 200:
            data = res.json()
            matches = data.get('data', []) if isinstance(data, dict) else data
            added = 0
            for m in matches:
                if save_match_if_new(cursor, parse_standard(m, 0, m.get('tournament', {}).get('name', 'بطولة'))): added += 1
            conn.commit(); print(f"✅ [Football - Soccer] {added} مباراة قادمة جديدة.")
    except Exception as e: print(f"❌ [Football - Soccer] فشل: {e}")
    finally: conn.close()

if __name__ == "__main__":
    print("="*50 + "\n🔧 سد الفجوات عبر RapidAPI (مباريات مستقبلية)\n" + "="*50)
    print("📌 احصل على مفاتيحك من: https://rapidapi.com/")
    print("📌 ابحث عن: SportAPI, SofaScore, Football - Soccer\n")
    
    sport_key = input("🔑 مفتاح SportAPI (Enter للتخطي): ").strip()
    sofa_key = input("🔑 مفتاح SofaScore (Enter للتخطي): ").strip()
    steady_key = input("🔑 مفتاح Football - Soccer (Enter للتخطي): ").strip()
    
    start = time.time()
    fetch_sportapi(sport_key, days=7)
    fetch_sofascore(sofa_key, days=7)
    fetch_steadyapi(steady_key)
    
    print("\n" + "="*50 + f"\n🎉 اكتمل في {time.time()-start:.1f} ثانية.")
    conn = sqlite3.connect(DB_NAME); cursor = conn.cursor()
    cursor.execute("SELECT COUNT(*) FROM matches")
    print(f"📊 إجمالي المباريات: {cursor.fetchone()[0]}")
    conn.close()
