"""Validates transport schema and reciprocal history without user data."""
from pathlib import Path
import re
import sqlite3

source = Path("app/src/main/java/mx/escuelaministerio/app/MinisterioDb.java").read_text()
body = source.split("private static void createTravelTables(SQLiteDatabase sql){", 1)[1].split("\n    }", 1)[0]
statements = re.findall(r'sql\.execSQL\("([^"]+)"\);', body)
assert len(statements) == 8, statements
db = sqlite3.connect(":memory:")
for statement in statements:
    db.execute(statement)
db.execute("INSERT INTO transport_trips VALUES (1,'2026-10-04','06:30','Asamblea','Salón del Reino',1,0)")
db.execute("INSERT INTO transport_buses VALUES (10,1,1,41,0)")
db.execute("INSERT INTO transport_interested_people VALUES (20,'Persona invitada')")
db.execute("INSERT INTO transport_passengers VALUES (30,1,'INTERESTED',20,'Persona invitada')")
db.execute("INSERT INTO transport_seats VALUES (10,7,30)")
db.execute("UPDATE transport_buses SET captain_passenger_id=30 WHERE id=10")
row = db.execute("SELECT t.reason,p.passenger_name,b.bus_number,s.seat_number,b.captain_passenger_id=p.id FROM transport_passengers p JOIN transport_trips t ON t.id=p.trip_id JOIN transport_seats s ON s.passenger_id=p.id JOIN transport_buses b ON b.id=s.bus_id").fetchone()
assert row == ("Asamblea", "Persona invitada", 1, 7, 1), row
try:
    db.execute("INSERT INTO transport_seats VALUES (10,7,31)")
except sqlite3.IntegrityError:
    pass
else:
    raise AssertionError("Se permitió duplicar el asiento 7")
print("Transporte: esquema, asiento único, capitán pasajero e histórico recíproco correctos.")
