"""Comprueba que la migración conserva los registros mecánicos existentes."""
from pathlib import Path
import re
import sqlite3


source = Path(__file__).resolve().parents[1] / "app/src/main/java/mx/escuelaministerio/app/MinisterioDb.java"
java = source.read_text(encoding="utf-8")
theme = (source.parents[4] / "res/values/themes.xml").read_text(encoding="utf-8")


def statements(method, next_marker):
    section = java.split(f"private static void {method}(", 1)[1].split(next_marker, 1)[0]
    return re.findall(r'db\.execSQL\("([^\"]+)"\)', section)


db = sqlite3.connect(":memory:")
db.execute(
    "CREATE TABLE mechanical_programs (meeting_date TEXT PRIMARY KEY, "
    "acomodador_entrada INTEGER NOT NULL DEFAULT 0, acomodador_auditorio_1 INTEGER NOT NULL DEFAULT 0, "
    "acomodador_auditorio_2 INTEGER NOT NULL DEFAULT 0, microfono_1 INTEGER NOT NULL DEFAULT 0, "
    "microfono_2 INTEGER NOT NULL DEFAULT 0, oracion_inicial INTEGER NOT NULL DEFAULT 0, "
    "oracion_final INTEGER NOT NULL DEFAULT 0, plataforma_1 INTEGER NOT NULL DEFAULT 0, "
    "plataforma_2 INTEGER NOT NULL DEFAULT 0, cantico_inicial TEXT NOT NULL DEFAULT '', "
    "cantico_intermedio TEXT NOT NULL DEFAULT '', cantico_final TEXT NOT NULL DEFAULT '')"
)
db.execute("INSERT INTO mechanical_programs(meeting_date,acomodador_entrada,cantico_inicial) VALUES ('2026-09-13',42,'27')")
creation = statements("createMechanicalTable", "private static void migrateMechanicalTable(")
migration = statements("migrateMechanicalTable", "private static void createPublicMeetingTables(")
public_tables = statements("createPublicMeetingTables", "long saveStudent(")
assert len(creation) == 1 and len(migration) == 3 and len(public_tables) == 5
for query in migration:
    if query.startswith("INSERT INTO mechanical_programs"):
        db.execute(creation[0])
    db.execute(query)
for query in public_tables:
    db.execute(query)
db.execute("INSERT INTO mechanical_programs(meeting_date,meeting_type,acomodador_entrada) VALUES ('2026-09-13','WEEKEND',99)")
rows = db.execute(
    "SELECT meeting_type,acomodador_entrada,cantico_inicial FROM mechanical_programs "
    "WHERE meeting_date='2026-09-13' ORDER BY meeting_type"
).fetchall()
assert rows == [("MIDWEEK", 42, "27"), ("WEEKEND", 99, "")], rows
db.execute("DELETE FROM mechanical_programs WHERE meeting_date='2026-09-13' AND meeting_type='MIDWEEK'")
assert db.execute("SELECT meeting_type FROM mechanical_programs").fetchall() == [("WEEKEND",)]
weekend_save = java.split("void saveWeekendMechanicalProgram(", 1)[1].split("private void saveMechanicalValues(", 1)[0]
assert "publicMeeting(program.date)" in weekend_save
assert "resolveLifeMinistryDate" not in weekend_save
assert 'name="android:windowCloseOnTouchOutside">false' in theme
print("Migración y separación de reuniones mecánicas: correctas.")
