import sqlite3, time, zlib, os
SEED='egypt_league_2026_2027.db'; OUT='sports_db.sqlite'
LEAGUES=[
('fixtures','standings',233,'الدوري المصري الممتاز','Egypt'),
('saudi_fixtures','saudi_standings',307,'دوري روشن السعودي','Saudi Arabia'),
('epl_fixtures','epl_standings',39,'الدوري الإنجليزي الممتاز','England'),
('laliga_fixtures','laliga_standings',140,'الدوري الإسباني','Spain'),
('serie_a_fixtures','serie_a_standings',135,'الدوري الإيطالي','Italy'),
('bundesliga_fixtures','bundesliga_standings',78,'الدوري الألماني','Germany'),
('ligue_1_fixtures','ligue_1_standings',61,'الدوري الفرنسي','France'),
('super_lig_fixtures','super_lig_standings',203,'الدوري التركي','Turkey'),
('ucl_fixtures','ucl_standings',2,'دوري أبطال أوروبا','Europe'),
('uel_fixtures','uel_standings',3,'الدوري الأوروبي','Europe'),
('caf_champions_fixtures','caf_champions_standings',12,'دوري أبطال أفريقيا','Africa'),
('caf_confederation_fixtures','caf_confederation_standings',20,'كأس الكونفدرالية الأفريقية','Africa'),
('afc_cl_fixtures','afc_cl_standings',17,'دوري أبطال آسيا للنخبة','Asia'),
('afcon_fixtures','afcon_standings',6,'كأس الأمم الأفريقية','Africa'),
('afcon_qualifiers_fixtures','afcon_qualifiers_standings',32,'تصفيات كأس الأمم الأفريقية','Africa'),
('euro_fixtures','euro_standings',403,'كأس الأمم الأوروبية','Europe'),
('asian_cup_fixtures','asian_cup_standings',8,'كأس آسيا','Asia'),
('world_cup_fixtures','world_cup_standings',1,'كأس العالم','World'),
('arab_cup_fixtures','arab_cup_standings',452,'كأس العرب','Arab')]
def nid(l,o): return zlib.crc32(f'{l}_{o}'.encode())&0x7FFFFFFF
def sid(s):   return zlib.crc32((s or '').encode())&0x7FFFFFFF
def mstat(s):
    s=(s or '').upper().strip()
    if s in ('FINISHED','FT','AET','PEN','AFTER_PEN','AFTER_ET','AWARDED'): return 'FINISHED'
    if s in ('LIVE','1H','2H','HT','ET'): return 'LIVE'
    if s in ('POSTPONED','PST','SUSP'): return 'POSTPONED'
    if s in ('CANCELLED','CANC','ABD','ABANDONED','WO'): return 'CANCELLED'
    return 'UPCOMING'
def hc(c,t,col): return any(d[1]==col for d in c.execute(f'PRAGMA table_info("{t}")'))
def ht(c,t): return c.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",(t,)).fetchone() is not None
assert os.path.exists(SEED)
src=sqlite3.connect(f'file:{SEED}?mode=ro',uri=True); dst=sqlite3.connect(OUT)
dst.executescript('''DROP TABLE IF EXISTS matches; DROP TABLE IF EXISTS standings;
CREATE TABLE matches(id INTEGER NOT NULL,leagueId INTEGER NOT NULL,leagueName TEXT NOT NULL,leagueCountry TEXT NOT NULL,
homeTeamId INTEGER NOT NULL,homeTeamName TEXT NOT NULL,homeTeamLogo TEXT NOT NULL,awayTeamId INTEGER NOT NULL,
awayTeamName TEXT NOT NULL,awayTeamLogo TEXT NOT NULL,homeScore INTEGER,awayScore INTEGER,status TEXT NOT NULL,
minute TEXT,date TEXT NOT NULL,time TEXT NOT NULL,stadium TEXT NOT NULL,channel TEXT NOT NULL,commentator TEXT NOT NULL,savedAt INTEGER NOT NULL,PRIMARY KEY(id));
CREATE TABLE standings(leagueId INTEGER NOT NULL,rank INTEGER NOT NULL,teamName TEXT NOT NULL,played INTEGER NOT NULL,
win INTEGER NOT NULL,draw INTEGER NOT NULL,loss INTEGER NOT NULL,goalsFor INTEGER NOT NULL,goalsAgainst INTEGER NOT NULL,
goalDifference INTEGER NOT NULL,points INTEGER NOT NULL,PRIMARY KEY(leagueId,rank));''')
now,nm,ns=int(time.time()*1000),0,0
for ft,st,lid,lname,country in LEAGUES:
    if not ht(src,ft): print(f'SKIP {ft}'); continue
    legacy=hc(src,ft,'home_team_id')
    if legacy:
        sql=f'''SELECT f.id,f.home_team_id,f.home_team_name,f.away_team_id,f.away_team_name,f.match_date,f.match_time,
        f.venue,f.status,f.home_score,f.away_score,COALESCE(t1.logo_url,''),COALESCE(t2.logo_url,'')
        FROM {ft} f LEFT JOIN teams t1 ON t1.id=f.home_team_id LEFT JOIN teams t2 ON t2.id=f.away_team_id'''
        for r in src.execute(sql):
            dst.execute('INSERT OR REPLACE INTO matches VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)',
                (nid(lid,r[0]),lid,lname,country,r[1],r[2] or '',r[11],r[3],r[4] or '',r[12],r[9],r[10],
                 mstat(r[8]),None,r[5] or '',r[6] or '',r[7] or '','غير محدد','غير محدد',now)); nm+=1
    else:
        sql=f'SELECT id,home_team,away_team,home_team_logo,away_team_logo,match_date,match_time,stadium,status,home_score,away_score FROM {ft}'
        for r in src.execute(sql):
            dst.execute('INSERT OR REPLACE INTO matches VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)',
                (nid(lid,r[0]),lid,lname,country,sid(r[1]),r[1] or '',r[3] or '',sid(r[2]),r[2] or '',r[4] or '',
                 r[9],r[10],mstat(r[8]),None,r[5] or '',r[6] or '',r[7] or '','غير محدد','غير محدد',now)); nm+=1
    if ht(src,st):
        rows=src.execute(f'SELECT team_name,played,won,drawn,lost,goals_for,goals_against,goal_difference,points FROM {st} ORDER BY points DESC,goal_difference DESC,goals_for DESC').fetchall()
        seen=set()
        for r in rows:
            tn=(r[0] or '').strip()
            if not tn or tn in seen: continue
            seen.add(tn); ns+=1
            dst.execute('INSERT OR REPLACE INTO standings VALUES(?,?,?,?,?,?,?,?,?,?,?)',
                (lid,len(seen),tn,r[1] or 0,r[2] or 0,r[3] or 0,r[4] or 0,r[5] or 0,r[6] or 0,r[7] or 0,r[8] or 0))
dst.commit()
print(f'TOTAL: {nm}M {ns}S')
for lid, in dst.execute('SELECT DISTINCT leagueId FROM matches ORDER BY leagueId'):
    m=dst.execute('SELECT COUNT(*) FROM matches WHERE leagueId=?',(lid,)).fetchone()[0]
    s=dst.execute('SELECT COUNT(*) FROM standings WHERE leagueId=?',(lid,)).fetchone()[0]
    print(f'  {lid:3}: {m:4}M {s:2}S')
src.close(); dst.close()
print(f'OUT {OUT} {os.path.getsize(OUT)//1024}KB | SEED {os.path.getsize(SEED)//1024}KB')
