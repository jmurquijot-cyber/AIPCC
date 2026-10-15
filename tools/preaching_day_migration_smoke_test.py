"""Host-only DB migration check; never reads or modifies a user's database."""
from pathlib import Path
import re
import sqlite3

source = Path("app/src/main/java/mx/escuelaministerio/app/MinisterioDb.java").read_text()
create = source.split("private static void createCampaignTables(SQLiteDatabase sql){", 1)[1].split("\n    }", 1)[0]
daily = source.split("private static void createDayCaptainTable(SQLiteDatabase sql){", 1)[1].split("\n    }", 1)[0]
migrate = source.split("private static void migrateDailyCaptains(SQLiteDatabase sql){", 1)[1].split("\n    }", 1)[0]
statements = re.findall(r'sql\.execSQL\("([^"]+)"\);', create + daily)
migration = re.findall(r'sql\.execSQL\("([^"]+)"\);', migrate)
assert len(statements) == 6 and len(migration) == 1
connection = sqlite3.connect(":memory:")
for statement in statements:
    connection.execute(statement)
for day, captains in [("2026-09-15", [5, 5, 5]), ("2026-09-16", [5, 6])]:
    for slot, student in enumerate(captains):
        connection.execute("INSERT INTO preaching_shift_captains VALUES (1,?,?,1,?)", (day, slot, student))
connection.execute(migration[0])
assert connection.execute("SELECT day,student_id,finalized FROM preaching_day_captains").fetchall() == [("2026-09-15", 5, 0)]
assert connection.execute("SELECT COUNT(*) FROM preaching_shift_captains").fetchone() == (5,)
print("Migración 11→12: capitán unánime como borrador; divergentes preservados sin elección arbitraria.")
