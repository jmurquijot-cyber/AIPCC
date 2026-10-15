#!/usr/bin/env python3
"""Host-only smoke test for the preaching-groups migration and history rules."""

import sqlite3


db = sqlite3.connect(":memory:")
db.executescript(
    """
    CREATE TABLE students (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL,
      rating INTEGER NOT NULL DEFAULT 1,
      pioneer INTEGER NOT NULL DEFAULT 0,
      group_number INTEGER NOT NULL DEFAULT 0 CHECK(group_number >= 0)
    );
    INSERT INTO students(name,rating,pioneer,group_number) VALUES
      ('ANA',8,1,2),('LUIS',7,0,2),('MARTA',6,1,0);
    CREATE TABLE IF NOT EXISTS preaching_groups (
      group_number INTEGER PRIMARY KEY CHECK(group_number > 0),
      name TEXT NOT NULL,
      captain_student_id INTEGER NOT NULL DEFAULT 0,
      assistant_student_id INTEGER NOT NULL DEFAULT 0,
      meeting_point_name TEXT NOT NULL DEFAULT '',
      meeting_point_address TEXT NOT NULL DEFAULT '',
      meeting_point_phone TEXT NOT NULL DEFAULT ''
    );
    INSERT OR IGNORE INTO preaching_groups(group_number,name)
      SELECT DISTINCT group_number,'Grupo '||group_number
      FROM students WHERE group_number>0;
    """
)

assert db.execute("SELECT name FROM preaching_groups WHERE group_number=2").fetchone() == ("Grupo 2",)

db.execute(
    "UPDATE preaching_groups SET name=?,captain_student_id=?,assistant_student_id=?,"
    "meeting_point_name=?,meeting_point_address=?,meeting_point_phone=? WHERE group_number=2",
    ("Centro", 1, 2, "Parque", "Avenida 1", "2290000000"),
)
row = db.execute(
    "SELECT g.name,c.name,a.name,g.meeting_point_name,g.meeting_point_address,g.meeting_point_phone "
    "FROM preaching_groups g JOIN students c ON c.id=g.captain_student_id "
    "JOIN students a ON a.id=g.assistant_student_id WHERE g.group_number=2"
).fetchone()
assert row == ("Centro", "ANA", "LUIS", "Parque", "Avenida 1", "2290000000")
assert db.execute("SELECT COUNT(*) FROM students WHERE group_number=2 AND pioneer=1").fetchone()[0] == 1

# Moving the captain out of the group is detected and can be repaired safely.
db.execute("UPDATE students SET group_number=0 WHERE id=1")
invalid = db.execute(
    "SELECT COUNT(*) FROM preaching_groups g LEFT JOIN students s ON s.id=g.captain_student_id "
    "WHERE g.captain_student_id!=0 AND (s.id IS NULL OR s.group_number!=g.group_number)"
).fetchone()[0]
assert invalid == 1
db.execute(
    "UPDATE preaching_groups SET captain_student_id=0 WHERE captain_student_id!=0 AND NOT EXISTS "
    "(SELECT 1 FROM students s WHERE s.id=preaching_groups.captain_student_id "
    "AND s.group_number=preaching_groups.group_number)"
)
assert db.execute("SELECT captain_student_id FROM preaching_groups WHERE group_number=2").fetchone() == (0,)

print("Grupos: migración, responsables, precursores y reparación de membresía correctos.")
