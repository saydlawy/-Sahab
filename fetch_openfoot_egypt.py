import sqlite3
import requests
import time
from datetime import datetime, timedelta

DB_NAME = "sports_db.sqlite"
BASE_URL = "https://openfootapi.com/v1"

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

def search_egypt_competition(api_key):
    """يبحث عن مسابقة الدوري المصري في OpenFootAPI."""
    headers = {"Authorization": f"Bearer {api_key}", "Accept": "application/json"}
    # محاولة البحث باستخدام نقطة النهاية /competitions أو /search
    try:
        # بعض الـ APIs تستخدم /competitions?search=Egypt
        res = requests.get(f"{BASE_URL}/competitions?search=Egypt", headers=headers, timeout=10)
        if res.status_code == 200:
            data = res.json()
            comps = data.get('data', [])
            for c in comps:
                name = c.get('name', '').lower()
                if 'egypt' in name or 'egyptian' in name:
                    return c
        # محاولة ثانية باستخدام /search
        res = requests.get(f"{BASE_URL}/search?q=Egypt", headers=headers, timeout=10)
        if res.status_code == 200:
            data = res.json()
            # قد ترجع النتائج في حقل different
            for item in data.get('data', []):
                if item.get('type') == 'competition':
                    name = item.get('name', '').lower()
                    if 'egypt' in name or 'egyptian' in name:
                        return item
    except Exception as e:
        print(f"⚠️ خطأ أثناء البحث: {e}")
    return None

def fetch_upcoming_matches(api_key, competition_id, competition_name):
    """يجلب المباريات القادمة لمسابقة معينة."""
    headers = {"Authorization": f"Bearer {api_key}", "Accept": "application/json"}
    today = datetime.now().strftime('%Y-%m-%d')
    # طلب المباريات القادمة (status=scheduled) لهذه المسابقة
    url = f"{BASE_URL}/matches?competition={competition_id}&status=scheduled&date={today}"
    try:
        res = requests.get(url, headers=headers, timeout=15)
        if res.status_code == 200:
            data = res.json()
            matches = data.get('data', [])
            if not matches:
                # ربما نحتاج إلى طلب بدون تاريخ (لجلب كل المباريات المجدولة)
                url = f"{BASE_URL}/matches?competition={competition_id}&status=scheduled"
                res = requests.get(url, headers=headers, timeout=15)
                if res.status_code == 200:
                    matches = res.json().get('data', [])
            return matches
        else:
            print(f"⚠️ فشل الطلب: HTTP {res.status_code}")
    except Exception as e:
        print(f"❌ خطأ في جلب المباريات: {e}")
    return []

def main():
    print("="*50)
    print("🔍 اختبار OpenFootAPI - البحث عن الدوري المصري")
    print("="*50)
    print("📌 المفتاح التجريبي: of_demo_openfootapi_docs (30 طلب/دقيقة)")
    print("📌 للحصول على مفتاح مجاني (5000 طلب/شهر): https://openfootapi.com/pricing")
    print()
    
    api_key = input("🔑 أدخل مفتاح OpenFootAPI (Enter لاستخدام المفتاح التجريبي): ").strip()
    if not api_key:
        api_key = "of_demo_openfootapi_docs"
        print("ℹ️ استخدام المفتاح التجريبي.")
    
    print("\n🔄 جاري البحث عن مسابقة الدوري المصري...")
    comp = search_egypt_competition(api_key)
    
    if not comp:
        print("\n❌ لم يتم العثور على الدوري المصري في OpenFootAPI.")
        print("💡 التوثيق يشير إلى أن التغطية الحالية تركز على الدوريات الأوروبية والكبرى.")
        print("💡 قد لا يكون الدوري المصري مدعومًا حاليًا.")
        return
    
    comp_id = comp.get('id')
    comp_name = comp.get('name', 'Egyptian Premier League')
    print(f"\n✅ تم العثور على المسابقة: {comp_name} (ID: {comp_id})")
    
    print("\n🔄 جاري جلب المباريات القادمة...")
    matches = fetch_upcoming_matches(api_key, comp_id, comp_name)
    
    if not matches:
        print("\n⚠️ لا توجد مباريات قادمة متاحة لهذه المسابقة حاليًا.")
        return
    
    print(f"\n✅ تم العثور على {len(matches)} مباراة قادمة.")
    
    # حفظ في قاعدة البيانات
    conn = sqlite3.connect(DB_NAME)
    cursor = conn.cursor()
    added = 0
    for m in matches:
        # استخراج الحقول من استجابة OpenFootAPI
        # البنية المتوقعة: { id, competitionId, status, kickoffAt, homeTeam: { id, name }, awayTeam: { name } }
        home = m.get('homeTeam', {})
        away = m.get('awayTeam', {})
        kickoff = m.get('kickoffAt', '')
        date_part = kickoff[:10] if kickoff else ''
        time_part = kickoff[11:16] if kickoff else ''
        
        match_data = {
            'id': hash(m.get('id', '')),
            'leagueId': 233,  # معرف الدوري المصري في نظامنا
            'leagueName': 'الدوري المصري الممتاز',
            'homeTeamId': hash(home.get('id', '')),
            'homeTeamName': home.get('name', ''),
            'awayTeamId': hash(away.get('id', '')),
            'awayTeamName': away.get('name', ''),
            'status': 'UPCOMING',
            'date': date_part,
            'time': time_part
        }
        if save_match_if_new(cursor, match_data):
            added += 1
    
    conn.commit()
    conn.close()
    print(f"\n📊 تم حفظ {added} مباراة جديدة في قاعدة البيانات.")
    
    # عرض عينة
    print("\n📋 عينة من المباريات القادمة:")
    for m in matches[:5]:
        home = m.get('homeTeam', {}).get('name', '?')
        away = m.get('awayTeam', {}).get('name', '?')
        kickoff = m.get('kickoffAt', '')
        print(f"  - {home} vs {away} | {kickoff[:10]} {kickoff[11:16]}")

if __name__ == "__main__":
    main()
