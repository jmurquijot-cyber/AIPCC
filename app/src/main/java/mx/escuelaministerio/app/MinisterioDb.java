package mx.escuelaministerio.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;

final class MinisterioDb extends SQLiteOpenHelper {
    static final String DB_NAME = "escuela_ministerio.db";
    static final int DB_VERSION = 17;
    static final int IMPORT_ADDED=1, IMPORT_UPDATED=2, IMPORT_MATCHED=3, IMPORT_REVIEW=4;
    private final Context context;

    MinisterioDb(Context context) { super(context, DB_NAME, null, DB_VERSION); this.context=context.getApplicationContext(); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE students (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT NOT NULL DEFAULT '', baptized INTEGER NOT NULL DEFAULT 0, sex TEXT NOT NULL DEFAULT '', appointed INTEGER NOT NULL DEFAULT 0, pioneer INTEGER NOT NULL DEFAULT 0, rating INTEGER NOT NULL DEFAULT 1 CHECK(rating BETWEEN 0 AND 10), vida_ministerio INTEGER NOT NULL DEFAULT 0, discurso_publico INTEGER NOT NULL DEFAULT 0, atalaya INTEGER NOT NULL DEFAULT 0, lector_atalaya INTEGER NOT NULL DEFAULT 0, acomodador_entrada INTEGER NOT NULL DEFAULT 0, acomodador_auditorio_1 INTEGER NOT NULL DEFAULT 0, acomodador_auditorio_2 INTEGER NOT NULL DEFAULT 0, microfono_1 INTEGER NOT NULL DEFAULT 0, microfono_2 INTEGER NOT NULL DEFAULT 0, plataforma_1 INTEGER NOT NULL DEFAULT 0, plataforma_2 INTEGER NOT NULL DEFAULT 0, oracion_inicial INTEGER NOT NULL DEFAULT 0, oracion_final INTEGER NOT NULL DEFAULT 0, estudio_libro INTEGER NOT NULL DEFAULT 0, lector_estudio_libro INTEGER NOT NULL DEFAULT 0, group_number INTEGER NOT NULL DEFAULT 0 CHECK(group_number >= 0))");
        db.execSQL("CREATE TABLE presidents (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE presentations (id INTEGER PRIMARY KEY AUTOINCREMENT, meeting_date TEXT NOT NULL, number INTEGER NOT NULL, material TEXT NOT NULL DEFAULT '', minutes INTEGER NOT NULL, assignment_name TEXT NOT NULL, framework TEXT NOT NULL DEFAULT '', lesson TEXT NOT NULL DEFAULT '', source TEXT NOT NULL DEFAULT '', topic TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE meetings (meeting_date TEXT PRIMARY KEY, president_id INTEGER NOT NULL DEFAULT 0, suspended INTEGER NOT NULL DEFAULT 0, visit INTEGER NOT NULL DEFAULT 0, circuit_overseer TEXT NOT NULL DEFAULT '', service_talk_topic TEXT NOT NULL DEFAULT '', service_song TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE movements (id INTEGER PRIMARY KEY AUTOINCREMENT, student_id INTEGER NOT NULL, presentation_id INTEGER NOT NULL, room TEXT NOT NULL, role TEXT NOT NULL, helper_id INTEGER NOT NULL DEFAULT 0, emergency INTEGER NOT NULL DEFAULT 0, actual_minutes INTEGER NOT NULL DEFAULT 0, lesson TEXT NOT NULL DEFAULT '', visit INTEGER NOT NULL DEFAULT 0, evaluated INTEGER NOT NULL DEFAULT 0, actual_seconds INTEGER NOT NULL DEFAULT 0, student_presented INTEGER NOT NULL DEFAULT 1, student_absence_reason TEXT NOT NULL DEFAULT '', replacement_student_id INTEGER NOT NULL DEFAULT 0, helper_presented INTEGER NOT NULL DEFAULT 1, helper_absence_reason TEXT NOT NULL DEFAULT '', material_adhered INTEGER NOT NULL DEFAULT 0, lesson_fulfilled INTEGER NOT NULL DEFAULT 0, lesson_comments TEXT NOT NULL DEFAULT '', evaluation_comments TEXT NOT NULL DEFAULT '', UNIQUE(presentation_id, room))");
        createHistoricalTable(db);
        createLifeMinistryTables(db);
        createMechanicalTable(db);
        createPublicMeetingTables(db);
        createSongsTable(db);
        SupportAuth.initialize(db);
        createCampaignTables(db);
        createDayCaptainTable(db);
        createTravelTables(db);
        createPreachingGroupTables(db);
        db.execSQL("CREATE INDEX idx_presentations_date ON presentations(meeting_date)");
        db.execSQL("CREATE INDEX idx_movements_student ON movements(student_id)");
        db.execSQL("CREATE INDEX idx_movements_helper ON movements(helper_id)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {if(oldVersion<2)createHistoricalTable(db);if(oldVersion<3){db.execSQL("ALTER TABLE movements ADD COLUMN evaluated INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE movements ADD COLUMN actual_seconds INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE movements ADD COLUMN student_presented INTEGER NOT NULL DEFAULT 1");db.execSQL("ALTER TABLE movements ADD COLUMN student_absence_reason TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE movements ADD COLUMN replacement_student_id INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE movements ADD COLUMN helper_presented INTEGER NOT NULL DEFAULT 1");db.execSQL("ALTER TABLE movements ADD COLUMN helper_absence_reason TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE movements ADD COLUMN material_adhered INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE movements ADD COLUMN lesson_fulfilled INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE movements ADD COLUMN lesson_comments TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE movements ADD COLUMN evaluation_comments TEXT NOT NULL DEFAULT ''");}if(oldVersion<4)addStudentClassificationColumns(db);if(oldVersion<5)createLifeMinistryTables(db);if(oldVersion<6){db.execSQL("ALTER TABLE meetings ADD COLUMN circuit_overseer TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE meetings ADD COLUMN service_talk_topic TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE meetings ADD COLUMN service_song TEXT NOT NULL DEFAULT ''");}if(oldVersion<7)createMechanicalTable(db);if(oldVersion<8){createPublicMeetingTables(db);migrateMechanicalTable(db);}if(oldVersion<9)createSongsTable(db);if(oldVersion<10)SupportAuth.initialize(db);if(oldVersion<11)createCampaignTables(db);if(oldVersion<12){createDayCaptainTable(db);migrateDailyCaptains(db);}if(oldVersion<13)createTravelTables(db);if(oldVersion<14)createPreachingGroupTables(db);if(oldVersion<15){db.execSQL("ALTER TABLE transport_trips ADD COLUMN congregation TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE transport_trips ADD COLUMN locality TEXT NOT NULL DEFAULT ''");}if(oldVersion<16)db.execSQL("ALTER TABLE transport_buses ADD COLUMN assistant_passenger_id INTEGER NOT NULL DEFAULT 0");if(oldVersion<17){db.execSQL("ALTER TABLE preaching_transport ADD COLUMN transport_student_id INTEGER NOT NULL DEFAULT 0");}}

    private static void createCampaignTables(SQLiteDatabase sql){
        sql.execSQL("CREATE TABLE IF NOT EXISTS preaching_campaigns (id INTEGER PRIMARY KEY AUTOINCREMENT, start_date TEXT NOT NULL, end_date TEXT NOT NULL, start_minute INTEGER NOT NULL, end_minute INTEGER NOT NULL, exhibitor_count INTEGER NOT NULL, module TEXT NOT NULL DEFAULT '', congregation TEXT NOT NULL DEFAULT '')");
        sql.execSQL("CREATE TABLE IF NOT EXISTS preaching_publishers (campaign_id INTEGER NOT NULL, day TEXT NOT NULL, slot INTEGER NOT NULL, exhibitor INTEGER NOT NULL, position INTEGER NOT NULL CHECK(position BETWEEN 1 AND 4), student_id INTEGER NOT NULL, PRIMARY KEY(campaign_id,day,slot,exhibitor,position))");
        sql.execSQL("CREATE TABLE IF NOT EXISTS preaching_shift_captains (campaign_id INTEGER NOT NULL, day TEXT NOT NULL, slot INTEGER NOT NULL, exhibitor INTEGER NOT NULL, student_id INTEGER NOT NULL, PRIMARY KEY(campaign_id,day,slot,exhibitor))");
        sql.execSQL("CREATE TABLE IF NOT EXISTS preaching_transport (campaign_id INTEGER NOT NULL, day TEXT NOT NULL, bring_student_id INTEGER NOT NULL DEFAULT 0, collect_student_id INTEGER NOT NULL DEFAULT 0, transport_student_id INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(campaign_id,day))");
        sql.execSQL("CREATE INDEX IF NOT EXISTS idx_preaching_day ON preaching_publishers(campaign_id,day)");
    }

    private static void createDayCaptainTable(SQLiteDatabase sql){
        sql.execSQL("CREATE TABLE IF NOT EXISTS preaching_day_captains (campaign_id INTEGER NOT NULL, day TEXT NOT NULL, student_id INTEGER NOT NULL, finalized INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(campaign_id,day))");
    }
    private static void migrateDailyCaptains(SQLiteDatabase sql){
        // Unambiguous historical captains are copied as drafts. Divergent captains
        // remain in the legacy table for review instead of choosing one arbitrarily.
        sql.execSQL("INSERT OR IGNORE INTO preaching_day_captains (campaign_id,day,student_id,finalized) SELECT campaign_id,day,MIN(student_id),0 FROM preaching_shift_captains GROUP BY campaign_id,day HAVING COUNT(DISTINCT student_id)=1");
    }

    private static void createTravelTables(SQLiteDatabase sql){
        sql.execSQL("CREATE TABLE IF NOT EXISTS transport_interested_people (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)");
        sql.execSQL("CREATE TABLE IF NOT EXISTS transport_trips (id INTEGER PRIMARY KEY AUTOINCREMENT, travel_date TEXT NOT NULL, departure_time TEXT NOT NULL, reason TEXT NOT NULL, departure_place TEXT NOT NULL, congregation TEXT NOT NULL DEFAULT '', locality TEXT NOT NULL DEFAULT '', registration_closed INTEGER NOT NULL DEFAULT 0, finalized INTEGER NOT NULL DEFAULT 0)");
        sql.execSQL("CREATE TABLE IF NOT EXISTS transport_buses (id INTEGER PRIMARY KEY AUTOINCREMENT, trip_id INTEGER NOT NULL, bus_number INTEGER NOT NULL, capacity INTEGER NOT NULL CHECK(capacity > 0), captain_passenger_id INTEGER NOT NULL DEFAULT 0, assistant_passenger_id INTEGER NOT NULL DEFAULT 0, UNIQUE(trip_id,bus_number))");
        sql.execSQL("CREATE TABLE IF NOT EXISTS transport_passengers (id INTEGER PRIMARY KEY AUTOINCREMENT, trip_id INTEGER NOT NULL, source_kind TEXT NOT NULL CHECK(source_kind IN ('STUDENT','INTERESTED')), source_id INTEGER NOT NULL, passenger_name TEXT NOT NULL, UNIQUE(trip_id,source_kind,source_id))");
        sql.execSQL("CREATE TABLE IF NOT EXISTS transport_seats (bus_id INTEGER NOT NULL, seat_number INTEGER NOT NULL CHECK(seat_number > 0), passenger_id INTEGER NOT NULL UNIQUE, PRIMARY KEY(bus_id,seat_number))");
        sql.execSQL("CREATE INDEX IF NOT EXISTS idx_transport_trip_date ON transport_trips(travel_date)");
        sql.execSQL("CREATE INDEX IF NOT EXISTS idx_transport_passengers_trip ON transport_passengers(trip_id)");
        sql.execSQL("CREATE INDEX IF NOT EXISTS idx_transport_buses_trip ON transport_buses(trip_id)");
    }

    private static void createPreachingGroupTables(SQLiteDatabase sql){
        sql.execSQL("CREATE TABLE IF NOT EXISTS preaching_groups (group_number INTEGER PRIMARY KEY CHECK(group_number > 0), name TEXT NOT NULL, captain_student_id INTEGER NOT NULL DEFAULT 0, assistant_student_id INTEGER NOT NULL DEFAULT 0, meeting_point_name TEXT NOT NULL DEFAULT '', meeting_point_address TEXT NOT NULL DEFAULT '', meeting_point_phone TEXT NOT NULL DEFAULT '')");
        sql.execSQL("INSERT OR IGNORE INTO preaching_groups (group_number,name) SELECT DISTINCT group_number,'Grupo '||group_number FROM students WHERE group_number>0");
    }

    private static void createSongsTable(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS CANCIONES (numero INTEGER PRIMARY KEY CHECK(numero > 0), titulo TEXT NOT NULL)");
        for(int i=0;i<SongCatalog.TITLES.length;i++){
            ContentValues values=new ContentValues();values.put("numero",i+1);values.put("titulo",SongCatalog.TITLES[i]);
            db.insertWithOnConflict("CANCIONES",null,values,SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    List<Models.Song> songs(){
        List<Models.Song> result=new ArrayList<>();
        try(Cursor cursor=getReadableDatabase().rawQuery("SELECT numero,titulo FROM CANCIONES ORDER BY numero",null)){
            while(cursor.moveToNext())result.add(new Models.Song(cursor.getInt(0),cursor.getString(1)));
        }
        return result;
    }
    private static void addStudentClassificationColumns(SQLiteDatabase db){String[] columns={"vida_ministerio","discurso_publico","atalaya","lector_atalaya","acomodador_entrada","acomodador_auditorio_1","acomodador_auditorio_2","microfono_1","microfono_2","plataforma_1","plataforma_2","oracion_inicial","oracion_final","estudio_libro","lector_estudio_libro"};for(String column:columns)db.execSQL("ALTER TABLE students ADD COLUMN "+column+" INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE students ADD COLUMN group_number INTEGER NOT NULL DEFAULT 0 CHECK(group_number >= 0)");}
    private static void createHistoricalTable(SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS historical_movements (id INTEGER PRIMARY KEY AUTOINCREMENT, meeting_date TEXT NOT NULL, assignment_number INTEGER NOT NULL, room TEXT NOT NULL, student_id INTEGER NOT NULL, helper_id INTEGER NOT NULL DEFAULT 0, visit INTEGER NOT NULL DEFAULT 0, UNIQUE(meeting_date,assignment_number,room))");db.execSQL("CREATE INDEX IF NOT EXISTS idx_historical_student ON historical_movements(student_id)");db.execSQL("CREATE INDEX IF NOT EXISTS idx_historical_helper ON historical_movements(helper_id)");}
    private static void createLifeMinistryTables(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS life_ministry_parts (id INTEGER PRIMARY KEY AUTOINCREMENT, meeting_date TEXT NOT NULL, number INTEGER NOT NULL, assignment_name TEXT NOT NULL, minutes INTEGER NOT NULL, topic TEXT NOT NULL DEFAULT '', UNIQUE(meeting_date,number))");
        db.execSQL("CREATE TABLE IF NOT EXISTS life_ministry_assignments (id INTEGER PRIMARY KEY AUTOINCREMENT, part_id INTEGER NOT NULL UNIQUE, student_id INTEGER NOT NULL, reader_id INTEGER NOT NULL DEFAULT 0, evaluated INTEGER NOT NULL DEFAULT 0, actual_seconds INTEGER NOT NULL DEFAULT 0, student_presented INTEGER NOT NULL DEFAULT 1, student_absence_reason TEXT NOT NULL DEFAULT '', replacement_student_id INTEGER NOT NULL DEFAULT 0, reader_presented INTEGER NOT NULL DEFAULT 1, reader_absence_reason TEXT NOT NULL DEFAULT '', topic_adhered INTEGER NOT NULL DEFAULT 0, evaluation_comments TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_life_parts_date ON life_ministry_parts(meeting_date)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_life_assignments_student ON life_ministry_assignments(student_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_life_assignments_reader ON life_ministry_assignments(reader_id)");
    }

    private static void createMechanicalTable(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS mechanical_programs (meeting_date TEXT NOT NULL, meeting_type TEXT NOT NULL DEFAULT 'MIDWEEK', acomodador_entrada INTEGER NOT NULL DEFAULT 0, acomodador_auditorio_1 INTEGER NOT NULL DEFAULT 0, acomodador_auditorio_2 INTEGER NOT NULL DEFAULT 0, microfono_1 INTEGER NOT NULL DEFAULT 0, microfono_2 INTEGER NOT NULL DEFAULT 0, oracion_inicial INTEGER NOT NULL DEFAULT 0, oracion_final INTEGER NOT NULL DEFAULT 0, plataforma_1 INTEGER NOT NULL DEFAULT 0, plataforma_2 INTEGER NOT NULL DEFAULT 0, cantico_inicial TEXT NOT NULL DEFAULT '', cantico_intermedio TEXT NOT NULL DEFAULT '', cantico_final TEXT NOT NULL DEFAULT '', PRIMARY KEY(meeting_date,meeting_type))");
    }

    private static void migrateMechanicalTable(SQLiteDatabase db){
        db.execSQL("ALTER TABLE mechanical_programs RENAME TO mechanical_programs_previous");
        createMechanicalTable(db);
        db.execSQL("INSERT INTO mechanical_programs (meeting_date,meeting_type,acomodador_entrada,acomodador_auditorio_1,acomodador_auditorio_2,microfono_1,microfono_2,oracion_inicial,oracion_final,plataforma_1,plataforma_2,cantico_inicial,cantico_intermedio,cantico_final) SELECT meeting_date,'MIDWEEK',acomodador_entrada,acomodador_auditorio_1,acomodador_auditorio_2,microfono_1,microfono_2,oracion_inicial,oracion_final,plataforma_1,plataforma_2,cantico_inicial,cantico_intermedio,cantico_final FROM mechanical_programs_previous");
        db.execSQL("DROP TABLE mechanical_programs_previous");
    }

    private static void createPublicMeetingTables(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS visiting_speakers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, congregation TEXT NOT NULL DEFAULT '', locality TEXT NOT NULL DEFAULT '', appointment TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE IF NOT EXISTS public_meetings (meeting_date TEXT PRIMARY KEY, president_student_id INTEGER NOT NULL DEFAULT 0, suspended INTEGER NOT NULL DEFAULT 0, visit INTEGER NOT NULL DEFAULT 0, speaker_kind TEXT NOT NULL DEFAULT 'LOCAL', local_speaker_id INTEGER NOT NULL DEFAULT 0, visitor_speaker_id INTEGER NOT NULL DEFAULT 0, outline_number TEXT NOT NULL DEFAULT '', outline_title TEXT NOT NULL DEFAULT '', conductor_id INTEGER NOT NULL DEFAULT 0, watchtower_topic TEXT NOT NULL DEFAULT '', reader_id INTEGER NOT NULL DEFAULT 0, final_talk_title TEXT NOT NULL DEFAULT '', evaluated INTEGER NOT NULL DEFAULT 0, speaker_presented INTEGER NOT NULL DEFAULT 1, evaluation_comments TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_public_local_speaker ON public_meetings(local_speaker_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_public_visitor_speaker ON public_meetings(visitor_speaker_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_public_reader ON public_meetings(reader_id)");
    }

    long saveStudent(Models.Student s) {
        ContentValues v = studentValues(s);
        if(s.groupNumber>0){ContentValues group=new ContentValues();group.put("group_number",s.groupNumber);group.put("name","Grupo "+s.groupNumber);getWritableDatabase().insertWithOnConflict("preaching_groups",null,group,SQLiteDatabase.CONFLICT_IGNORE);}
        if (s.id == 0) return getWritableDatabase().insertOrThrow("students", null, v);
        getWritableDatabase().update("students", v, "id=?", new String[]{String.valueOf(s.id)});
        return s.id;
    }

    int upsertImportedStudent(Models.Student s) {
        SQLiteDatabase db = getWritableDatabase();
        // Legacy templates update core fields only; retain classifications already stored in the database.
        if (!s.extendedClassificationsProvided) {
            Models.Student oldStudent = null;
            if (s.id > 0) {
                for (Models.Student existing : students(true)) if (existing.id == s.id) { oldStudent = existing; break; }
            }
            if (oldStudent == null) {
                String targetName = key(s.name), targetPhone = phoneKey(s.phone);
                for (Models.Student existing : students(true)) {
                    if (key(existing.name).equals(targetName)
                            && (targetPhone.isEmpty() ? phoneKey(existing.phone).isEmpty() : phoneKey(existing.phone).equals(targetPhone))) {
                        oldStudent = existing; break;
                    }
                }
            }
            if (oldStudent != null) copyStudentClassifications(oldStudent, s);
        }
        SQLiteDatabase dbForValues = db;
        ContentValues v = s.extendedClassificationsProvided ? studentValues(s) : studentCoreValues(s);
        if (s.id > 0 && rowExists(db, "students", s.id)) {
            db.update("students", v, "id=?", new String[]{String.valueOf(s.id)}); return 2;
        }
        List<Models.Student> sameName=new ArrayList<>(), natural=new ArrayList<>();
        String name=key(s.name), phone=phoneKey(s.phone);
        for(Models.Student old:students(true))if(key(old.name).equals(name)){
            sameName.add(old);
            if(!phone.isEmpty()?phoneKey(old.phone).equals(phone):(phoneKey(old.phone).isEmpty()&&sameStudentCore(old,s)))natural.add(old);
        }
        if(!natural.isEmpty()){Models.Student old=natural.get(0);s.id=old.id;db.update("students",v,"id=?",new String[]{String.valueOf(old.id)});return IMPORT_MATCHED;}
        if(!sameName.isEmpty())return IMPORT_REVIEW;
        if (s.id > 0 && !rowExists(db,"students",s.id)) v.put("id", s.id);
        s.id = db.insertOrThrow("students", null, v); return 1;
    }

    private void copyStudentClassifications(Models.Student from, Models.Student to) {
        to.vidaMinisterio=from.vidaMinisterio; to.discursoPublico=from.discursoPublico; to.atalaya=from.atalaya;
        to.lectorAtalaya=from.lectorAtalaya; to.acomodadorEntrada=from.acomodadorEntrada;
        to.acomodadorAuditorio1=from.acomodadorAuditorio1; to.acomodadorAuditorio2=from.acomodadorAuditorio2;
        to.microfono1=from.microfono1; to.microfono2=from.microfono2; to.plataforma1=from.plataforma1;
        to.plataforma2=from.plataforma2; to.oracionInicial=from.oracionInicial; to.oracionFinal=from.oracionFinal;
        to.estudioLibro=from.estudioLibro; to.lectorEstudioLibro=from.lectorEstudioLibro; to.groupNumber=from.groupNumber;
    }

    private ContentValues studentValues(Models.Student s) {
        ContentValues v = studentCoreValues(s);
        v.put("vida_ministerio", s.vidaMinisterio ? 1 : 0);
        v.put("discurso_publico", s.discursoPublico ? 1 : 0);
        v.put("atalaya", s.atalaya ? 1 : 0);
        v.put("lector_atalaya", s.lectorAtalaya ? 1 : 0);
        v.put("acomodador_entrada", s.acomodadorEntrada ? 1 : 0);
        v.put("acomodador_auditorio_1", s.acomodadorAuditorio1 ? 1 : 0);
        v.put("acomodador_auditorio_2", s.acomodadorAuditorio2 ? 1 : 0);
        v.put("microfono_1", s.microfono1 ? 1 : 0);
        v.put("microfono_2", s.microfono2 ? 1 : 0);
        v.put("plataforma_1", s.plataforma1 ? 1 : 0);
        v.put("plataforma_2", s.plataforma2 ? 1 : 0);
        v.put("oracion_inicial", s.oracionInicial ? 1 : 0);
        v.put("oracion_final", s.oracionFinal ? 1 : 0);
        v.put("estudio_libro", s.estudioLibro ? 1 : 0);
        v.put("lector_estudio_libro", s.lectorEstudioLibro ? 1 : 0);
        v.put("group_number", s.groupNumber);
        return v;
    }

    private ContentValues studentCoreValues(Models.Student s) {
        ContentValues v = new ContentValues();
        v.put("name", s.name.trim()); v.put("phone", s.phone.trim());
        v.put("baptized", s.baptized ? 1 : 0); v.put("sex", s.sex);
        v.put("appointed", s.appointed ? 1 : 0); v.put("pioneer", s.pioneer ? 1 : 0);
        v.put("rating", s.rating); return v;
    }

    List<Models.Student> students(boolean includeSuspended) {
        List<Models.Student> out = new ArrayList<>();
        String where = includeSuspended ? null : "rating>0";
        try (Cursor c = getReadableDatabase().query("students", null, where, null, null, null, "name COLLATE NOCASE")) {
            while (c.moveToNext()) out.add(readStudent(c));
        }
        return out;
    }

    Models.Student student(long id) {
        try (Cursor c = getReadableDatabase().query("students", null, "id=?", new String[]{String.valueOf(id)}, null, null, null)) {
            return c.moveToFirst() ? readStudent(c) : null;
        }
    }

    void deleteStudent(long id) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues noLeader=new ContentValues();noLeader.put("captain_student_id",0);db.update("preaching_groups",noLeader,"captain_student_id=?",new String[]{String.valueOf(id)});ContentValues noAssistant=new ContentValues();noAssistant.put("assistant_student_id",0);db.update("preaching_groups",noAssistant,"assistant_student_id=?",new String[]{String.valueOf(id)});
        for(String field:new String[]{"president_student_id","local_speaker_id","conductor_id","reader_id"}){
            ContentValues cleared=new ContentValues();cleared.put(field,0);
            db.update("public_meetings",cleared,field+"=?",new String[]{String.valueOf(id)});
        }
        for(Models.MechanicalRole role:Models.MechanicalRole.values()){
            ContentValues cleared=new ContentValues();cleared.put(role.column,0);
            db.update("mechanical_programs",cleared,role.column+"=?",new String[]{String.valueOf(id)});
        }
        db.delete("movements", "student_id=? OR helper_id=?", new String[]{String.valueOf(id), String.valueOf(id)});
        db.delete("historical_movements", "student_id=? OR helper_id=?", new String[]{String.valueOf(id), String.valueOf(id)});
        db.delete("life_ministry_assignments", "student_id=? OR reader_id=? OR replacement_student_id=?", new String[]{String.valueOf(id), String.valueOf(id), String.valueOf(id)});
        db.delete("students", "id=?", new String[]{String.valueOf(id)});
    }

    private Models.Student readStudent(Cursor c) {
        Models.Student s = new Models.Student();
        s.id = c.getLong(c.getColumnIndexOrThrow("id"));
        s.name = c.getString(c.getColumnIndexOrThrow("name"));
        s.phone = c.getString(c.getColumnIndexOrThrow("phone"));
        s.baptized = c.getInt(c.getColumnIndexOrThrow("baptized")) != 0;
        s.sex = c.getString(c.getColumnIndexOrThrow("sex"));
        s.appointed = c.getInt(c.getColumnIndexOrThrow("appointed")) != 0;
        s.pioneer = c.getInt(c.getColumnIndexOrThrow("pioneer")) != 0;
        s.rating = c.getInt(c.getColumnIndexOrThrow("rating"));
        s.vidaMinisterio = c.getInt(c.getColumnIndexOrThrow("vida_ministerio")) != 0;
        s.discursoPublico = c.getInt(c.getColumnIndexOrThrow("discurso_publico")) != 0;
        s.atalaya = c.getInt(c.getColumnIndexOrThrow("atalaya")) != 0;
        s.lectorAtalaya = c.getInt(c.getColumnIndexOrThrow("lector_atalaya")) != 0;
        s.acomodadorEntrada = c.getInt(c.getColumnIndexOrThrow("acomodador_entrada")) != 0;
        s.acomodadorAuditorio1 = c.getInt(c.getColumnIndexOrThrow("acomodador_auditorio_1")) != 0;
        s.acomodadorAuditorio2 = c.getInt(c.getColumnIndexOrThrow("acomodador_auditorio_2")) != 0;
        s.microfono1 = c.getInt(c.getColumnIndexOrThrow("microfono_1")) != 0;
        s.microfono2 = c.getInt(c.getColumnIndexOrThrow("microfono_2")) != 0;
        s.plataforma1 = c.getInt(c.getColumnIndexOrThrow("plataforma_1")) != 0;
        s.plataforma2 = c.getInt(c.getColumnIndexOrThrow("plataforma_2")) != 0;
        s.oracionInicial = c.getInt(c.getColumnIndexOrThrow("oracion_inicial")) != 0;
        s.oracionFinal = c.getInt(c.getColumnIndexOrThrow("oracion_final")) != 0;
        s.estudioLibro = c.getInt(c.getColumnIndexOrThrow("estudio_libro")) != 0;
        s.lectorEstudioLibro = c.getInt(c.getColumnIndexOrThrow("lector_estudio_libro")) != 0;
        s.groupNumber = c.getInt(c.getColumnIndexOrThrow("group_number"));
        return s;
    }

    long savePresident(Models.President p) {
        ContentValues v = presidentValues(p);
        if (p.id == 0) return getWritableDatabase().insertOrThrow("presidents", null, v);
        getWritableDatabase().update("presidents", v, "id=?", new String[]{String.valueOf(p.id)}); return p.id;
    }

    int upsertImportedPresident(Models.President p) {
        SQLiteDatabase db = getWritableDatabase(); ContentValues v = presidentValues(p);
        if (p.id > 0 && rowExists(db, "presidents", p.id)) {
            db.update("presidents", v, "id=?", new String[]{String.valueOf(p.id)}); return 2;
        }
        List<Models.President> sameName=new ArrayList<>(), natural=new ArrayList<>();String name=key(p.name),phone=phoneKey(p.phone);
        for(Models.President old:presidents())if(key(old.name).equals(name)){sameName.add(old);if(phoneKey(old.phone).equals(phone))natural.add(old);}
        if(!natural.isEmpty()){Models.President old=natural.get(0);p.id=old.id;db.update("presidents",v,"id=?",new String[]{String.valueOf(old.id)});return IMPORT_MATCHED;}
        if(!sameName.isEmpty())return IMPORT_REVIEW;
        if (p.id > 0 && !rowExists(db,"presidents",p.id)) v.put("id", p.id);
        p.id = db.insertOrThrow("presidents", null, v); return 1;
    }

    static class DuplicateReview { int studentDuplicates,presidentDuplicates;final List<String> ambiguous=new ArrayList<>(); }
    static final class DuplicateCleanupResult extends DuplicateReview { String backupName; }
    DuplicateReview reviewDuplicates(){return duplicateReview(false,new DuplicateReview());}
    DuplicateCleanupResult cleanupExactDuplicates() throws Exception {DuplicateCleanupResult r=new DuplicateCleanupResult();r.backupName=backupDatabase();SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{duplicateReview(true,r);db.setTransactionSuccessful();}finally{db.endTransaction();}return r;}
private <T extends DuplicateReview> T duplicateReview(boolean merge,T out){SQLiteDatabase db=getWritableDatabase();Map<String,List<Models.Student>> sg=new LinkedHashMap<>(),names=new LinkedHashMap<>();for(Models.Student s:students(true)){sg.computeIfAbsent(studentFingerprint(s),x->new ArrayList<>()).add(s);names.computeIfAbsent(key(s.name),x->new ArrayList<>()).add(s);}for(List<Models.Student> group:sg.values())if(group.size()>1){out.studentDuplicates+=group.size()-1;if(merge){long keep=group.get(0).id;for(int i=1;i<group.size();i++){long drop=group.get(i).id;ContentValues v=new ContentValues();v.put("student_id",keep);db.update("movements",v,"student_id=?",new String[]{String.valueOf(drop)});db.update("life_ministry_assignments",v,"student_id=?",new String[]{String.valueOf(drop)});v.clear();v.put("helper_id",keep);db.update("movements",v,"helper_id=?",new String[]{String.valueOf(drop)});v.clear();v.put("reader_id",keep);db.update("life_ministry_assignments",v,"reader_id=?",new String[]{String.valueOf(drop)});v.clear();v.put("replacement_student_id",keep);db.update("life_ministry_assignments",v,"replacement_student_id=?",new String[]{String.valueOf(drop)});for(Models.MechanicalRole role:Models.MechanicalRole.values()){v.clear();v.put(role.column,keep);db.update("mechanical_programs",v,role.column+"=?",new String[]{String.valueOf(drop)});}remapPublicStudent(db,keep,drop);db.delete("students","id=?",new String[]{String.valueOf(drop)});}}}for(List<Models.Student> group:names.values())if(group.size()>1){java.util.Set<String> f=new java.util.HashSet<>();for(Models.Student s:group)f.add(studentFingerprint(s));if(f.size()>1)out.ambiguous.add("Alumno: "+group.get(0).name);}Map<String,List<Models.President>> pg=new LinkedHashMap<>(),pn=new LinkedHashMap<>();for(Models.President p:presidents()){pg.computeIfAbsent(key(p.name)+"|"+phoneKey(p.phone),x->new ArrayList<>()).add(p);pn.computeIfAbsent(key(p.name),x->new ArrayList<>()).add(p);}for(List<Models.President> group:pg.values())if(group.size()>1){out.presidentDuplicates+=group.size()-1;if(merge){long keep=group.get(0).id;for(int i=1;i<group.size();i++){long drop=group.get(i).id;ContentValues v=new ContentValues();v.put("president_id",keep);db.update("meetings",v,"president_id=?",new String[]{String.valueOf(drop)});db.delete("presidents","id=?",new String[]{String.valueOf(drop)});}}}for(List<Models.President> group:pn.values())if(group.size()>1){java.util.Set<String> phones=new java.util.HashSet<>();for(Models.President p:group)phones.add(phoneKey(p.phone));if(phones.size()>1)out.ambiguous.add("Presidente: "+group.get(0).name);}return out;}
    private static void remapPublicStudent(SQLiteDatabase sql,long keep,long drop){
        for(String field:new String[]{"president_student_id","local_speaker_id","conductor_id","reader_id"}){
            ContentValues values=new ContentValues();values.put(field,keep);
            sql.update("public_meetings",values,field+"=?",new String[]{String.valueOf(drop)});
        }
    }
    private String backupDatabase() throws Exception {SQLiteDatabase db=getWritableDatabase();db.rawQuery("PRAGMA wal_checkpoint(FULL)",null).close();File dir=new File(context.getFilesDir(),"respaldos");if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("No se pudo crear la carpeta de respaldos");String stamp=java.time.LocalDateTime.now().toString().replace(":","-");File target=new File(dir,"escuela_ministerio_"+stamp+".db");try(FileInputStream in=new FileInputStream(context.getDatabasePath(DB_NAME));FileOutputStream out=new FileOutputStream(target)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}return target.getName();}
    String backupBeforeHistoricalImport() throws Exception{return backupDatabase();}
    long studentIdByImportedName(String name){long found=0;for(Models.Student s:students(true))if(key(s.name).equals(key(name))){if(found!=0)return -1;found=s.id;}return found;}
    int upsertHistoricalMovement(String date,int number,String room,long studentId,long helperId,boolean visit){SQLiteDatabase db=getWritableDatabase();try(Cursor c=db.query("historical_movements",new String[]{"student_id","helper_id","visit"},"meeting_date=? AND assignment_number=? AND room=?",new String[]{date,String.valueOf(number),room},null,null,null)){if(c.moveToFirst())return c.getLong(0)==studentId&&c.getLong(1)==helperId&&(c.getInt(2)!=0)==visit?2:3;}ContentValues v=new ContentValues();v.put("meeting_date",date);v.put("assignment_number",number);v.put("room",room);v.put("student_id",studentId);v.put("helper_id",helperId);v.put("visit",visit?1:0);db.insertOrThrow("historical_movements",null,v);return 1;}
    private static boolean sameStudentCore(Models.Student a,Models.Student b){return key(a.sex).equals(key(b.sex))&&a.baptized==b.baptized&&a.appointed==b.appointed&&a.pioneer==b.pioneer&&a.rating==b.rating;}
    private static String studentFingerprint(Models.Student s){return key(s.name)+"|"+phoneKey(s.phone)+"|"+key(s.sex)+"|"+s.baptized+"|"+s.appointed+"|"+s.pioneer+"|"+s.rating;}
    private static String phoneKey(String value){return value==null?"":value.replaceAll("[^0-9]","");}
    private static String key(String value){String text=Normalizer.normalize(value==null?"":value.trim().toLowerCase(Locale.ROOT),Normalizer.Form.NFD).replaceAll("\\p{M}+","").replaceAll("[^a-z0-9]+"," ").trim();return text.replaceAll("\\s+"," ");}

    private ContentValues presidentValues(Models.President p) {
        ContentValues v = new ContentValues(); v.put("name", p.name.trim()); v.put("phone", p.phone.trim()); return v;
    }

    List<Models.President> presidents() {
        List<Models.President> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("presidents", null, null, null, null, null, "name COLLATE NOCASE")) {
            while (c.moveToNext()) { Models.President p = new Models.President(); p.id=c.getLong(0); p.name=c.getString(1); p.phone=c.getString(2); out.add(p); }
        }
        return out;
    }

    Models.President president(long id) {
        try (Cursor c = getReadableDatabase().query("presidents", null, "id=?", new String[]{String.valueOf(id)}, null, null, null)) {
            if (!c.moveToFirst()) return null; Models.President p = new Models.President(); p.id=c.getLong(0); p.name=c.getString(1); p.phone=c.getString(2); return p;
        }
    }

    void deletePresident(long id) {
        getWritableDatabase().delete("presidents", "id=?", new String[]{String.valueOf(id)});
        ContentValues v = new ContentValues(); v.put("president_id", 0); getWritableDatabase().update("meetings", v, "president_id=?", new String[]{String.valueOf(id)});
    }

    long savePresentation(Models.Presentation p) {
        ContentValues v = presentationValues(p);
        if (p.id == 0) return getWritableDatabase().insertOrThrow("presentations", null, v);
        getWritableDatabase().update("presentations", v, "id=?", new String[]{String.valueOf(p.id)}); return p.id;
    }

    int upsertImportedPresentation(Models.Presentation p) {
        SQLiteDatabase db = getWritableDatabase(); ContentValues v = presentationValues(p);
        long existing = presentationId(db,p.date,p.number);
        if(existing>0){p.id=existing;db.update("presentations",v,"id=?",new String[]{String.valueOf(existing)});return 2;}
        if (p.id > 0 && rowExists(db, "presentations", p.id)) p.id = 0;
        if (p.id > 0) v.put("id", p.id);
        p.id = db.insertOrThrow("presentations", null, v); return 1;
    }

    private long presentationId(SQLiteDatabase db,String date,int number){
        try(Cursor c=db.query("presentations",new String[]{"id"},"meeting_date=? AND number=?",new String[]{date,String.valueOf(number)},null,null,null,"1")){return c.moveToFirst()?c.getLong(0):0;}
    }

    private ContentValues presentationValues(Models.Presentation p) {
        ContentValues v = new ContentValues();
        v.put("meeting_date", p.date); v.put("number", p.number); v.put("material", p.material);
        v.put("minutes", p.minutes); v.put("assignment_name", p.assignmentName); v.put("framework", p.framework);
        v.put("lesson", p.lesson); v.put("source", p.source); v.put("topic", p.topic); return v;
    }

    private boolean rowExists(SQLiteDatabase db, String table, long id) {
        try (Cursor c = db.query(table, new String[]{"id"}, "id=?", new String[]{String.valueOf(id)}, null, null, null)) {
            return c.moveToFirst();
        }
    }

    List<Models.Presentation> presentations(String month) {
        List<Models.Presentation> out = new ArrayList<>();
        String where = month == null ? null : "meeting_date LIKE ?";
        String[] args = month == null ? null : new String[]{month + "%"};
        try (Cursor c = getReadableDatabase().query("presentations", null, where, args, null, null, "meeting_date, number")) {
            while (c.moveToNext()) out.add(readPresentation(c));
        }
        return out;
    }

    Models.Presentation presentation(long id) {
        try (Cursor c = getReadableDatabase().query("presentations", null, "id=?", new String[]{String.valueOf(id)}, null, null, null)) {
            return c.moveToFirst() ? readPresentation(c) : null;
        }
    }

    private Models.Presentation readPresentation(Cursor c) {
        Models.Presentation p = new Models.Presentation();
        p.id=c.getLong(c.getColumnIndexOrThrow("id")); p.date=c.getString(c.getColumnIndexOrThrow("meeting_date"));
        p.number=c.getInt(c.getColumnIndexOrThrow("number")); p.material=c.getString(c.getColumnIndexOrThrow("material"));
        p.minutes=c.getInt(c.getColumnIndexOrThrow("minutes")); p.assignmentName=c.getString(c.getColumnIndexOrThrow("assignment_name"));
        p.framework=c.getString(c.getColumnIndexOrThrow("framework")); p.lesson=c.getString(c.getColumnIndexOrThrow("lesson"));
        p.source=c.getString(c.getColumnIndexOrThrow("source")); p.topic=c.getString(c.getColumnIndexOrThrow("topic")); return p;
    }

    void deletePresentation(long id) {
        getWritableDatabase().delete("movements", "presentation_id=?", new String[]{String.valueOf(id)});
        getWritableDatabase().delete("presentations", "id=?", new String[]{String.valueOf(id)});
    }

    static final class WeekDeleteInfo { int presentations; int movements; int lifeParts; int lifeAssignments; int mechanicalPrograms; boolean meeting; final List<Long> movementIds=new ArrayList<>(); final List<Long> lifeAssignmentIds=new ArrayList<>(); boolean isEmpty(){return presentations==0&&movements==0&&lifeParts==0&&lifeAssignments==0&&mechanicalPrograms==0&&!meeting;} }

    WeekDeleteInfo weekDeleteInfo(String date){WeekDeleteInfo info=new WeekDeleteInfo();SQLiteDatabase db=getReadableDatabase();try(Cursor c=db.rawQuery("SELECT m.id FROM movements m JOIN presentations p ON p.id=m.presentation_id WHERE p.meeting_date=?",new String[]{date})){while(c.moveToNext())info.movementIds.add(c.getLong(0));}info.movements=info.movementIds.size();try(Cursor c=db.rawQuery("SELECT a.id FROM life_ministry_assignments a JOIN life_ministry_parts p ON p.id=a.part_id WHERE p.meeting_date=?",new String[]{date})){while(c.moveToNext())info.lifeAssignmentIds.add(c.getLong(0));}info.lifeAssignments=info.lifeAssignmentIds.size();try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM presentations WHERE meeting_date=?",new String[]{date})){if(c.moveToFirst())info.presentations=c.getInt(0);}try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM life_ministry_parts WHERE meeting_date=?",new String[]{date})){if(c.moveToFirst())info.lifeParts=c.getInt(0);}try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM mechanical_programs WHERE meeting_date=? AND meeting_type='MIDWEEK'",new String[]{date})){if(c.moveToFirst())info.mechanicalPrograms=c.getInt(0);}try(Cursor c=db.rawQuery("SELECT 1 FROM meetings WHERE meeting_date=? LIMIT 1",new String[]{date})){info.meeting=c.moveToFirst();}return info;}

    void deleteWeek(String date){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{db.delete("movements","presentation_id IN (SELECT id FROM presentations WHERE meeting_date=?)",new String[]{date});db.delete("presentations","meeting_date=?",new String[]{date});db.delete("life_ministry_assignments","part_id IN (SELECT id FROM life_ministry_parts WHERE meeting_date=?)",new String[]{date});db.delete("life_ministry_parts","meeting_date=?",new String[]{date});db.delete("mechanical_programs","meeting_date=? AND meeting_type='MIDWEEK'",new String[]{date});db.delete("meetings","meeting_date=?",new String[]{date});db.setTransactionSuccessful();}finally{db.endTransaction();}}

    void saveMeeting(Models.Meeting m) {
        ContentValues v = new ContentValues(); v.put("meeting_date",m.date); v.put("president_id",m.presidentId);
        v.put("suspended",m.suspended?1:0); v.put("visit",m.visit?1:0);v.put("circuit_overseer",m.circuitOverseer);v.put("service_talk_topic",m.serviceTalkTopic);v.put("service_song",m.serviceSong);
        getWritableDatabase().insertWithOnConflict("meetings",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        if (m.visit) getWritableDatabase().delete("movements", "room='AUXILIAR' AND presentation_id IN (SELECT id FROM presentations WHERE meeting_date=?)", new String[]{m.date});
    }

    void moveWeekDate(String from,String to){if(from.equals(to))return;SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{ContentValues v=new ContentValues();v.put("meeting_date",to);db.update("presentations",v,"meeting_date=?",new String[]{from});db.update("life_ministry_parts",v,"meeting_date=?",new String[]{from});db.update("mechanical_programs",v,"meeting_date=? AND meeting_type='MIDWEEK'",new String[]{from});Models.Meeting previous=meeting(from);if(meetingConfigured(from)&&!meetingConfigured(to)){previous.date=to;saveMeeting(previous);}db.delete("meetings","meeting_date=?",new String[]{from});db.setTransactionSuccessful();}finally{db.endTransaction();}}

    Models.Meeting meeting(String date) {
        try (Cursor c = getReadableDatabase().query("meetings",null,"meeting_date=?",new String[]{date},null,null,null)) {
            Models.Meeting m = new Models.Meeting(); m.date=date;
            if (c.moveToFirst()) { m.presidentId=c.getLong(c.getColumnIndexOrThrow("president_id")); m.suspended=c.getInt(c.getColumnIndexOrThrow("suspended"))!=0; m.visit=c.getInt(c.getColumnIndexOrThrow("visit"))!=0;m.circuitOverseer=c.getString(c.getColumnIndexOrThrow("circuit_overseer"));m.serviceTalkTopic=c.getString(c.getColumnIndexOrThrow("service_talk_topic"));m.serviceSong=c.getString(c.getColumnIndexOrThrow("service_song")); }
            return m;
        }
    }

    List<Models.Meeting> meetings(String month) {
        List<Models.Meeting> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("meetings",null,"meeting_date LIKE ?",new String[]{month+"%"},null,null,"meeting_date")){
            while(c.moveToNext()){ Models.Meeting m=new Models.Meeting(); m.date=c.getString(c.getColumnIndexOrThrow("meeting_date")); m.presidentId=c.getLong(c.getColumnIndexOrThrow("president_id")); m.suspended=c.getInt(c.getColumnIndexOrThrow("suspended"))!=0; m.visit=c.getInt(c.getColumnIndexOrThrow("visit"))!=0;m.circuitOverseer=c.getString(c.getColumnIndexOrThrow("circuit_overseer"));m.serviceTalkTopic=c.getString(c.getColumnIndexOrThrow("service_talk_topic"));m.serviceSong=c.getString(c.getColumnIndexOrThrow("service_song"));out.add(m); }
        } return out;
    }

    Models.MechanicalProgram mechanicalProgram(String date){return mechanicalProgram(date,"MIDWEEK");}

    Models.MechanicalProgram mechanicalProgram(String date,String type){
        try(Cursor c=getReadableDatabase().query("mechanical_programs",null,"meeting_date=? AND meeting_type=?",new String[]{date,type},null,null,null)){
            if(!c.moveToFirst())return null;
            Models.MechanicalProgram program=new Models.MechanicalProgram();program.date=date;program.meetingType=type;
            for(Models.MechanicalRole role:Models.MechanicalRole.values())program.assigned.put(role,c.getLong(c.getColumnIndexOrThrow(role.column)));
            program.openingSong=c.getString(c.getColumnIndexOrThrow("cantico_inicial"));
            program.middleSong=c.getString(c.getColumnIndexOrThrow("cantico_intermedio"));
            program.closingSong=c.getString(c.getColumnIndexOrThrow("cantico_final"));
            return program;
        }
    }

    void saveMechanicalProgram(Models.MechanicalProgram program){
        LocalDate.parse(program.date);
        String resolved=resolveLifeMinistryDate(program.date);
        if(!meetingConfigured(resolved))throw new IllegalArgumentException("Configure primero esta semana en Escuela del Ministerio Teocrático.");
        if(meeting(resolved).suspended)throw new IllegalArgumentException("No se programan asignaciones para una reunión suspendida.");
        saveMechanicalValues(program,resolved,"MIDWEEK");
    }

    void saveWeekendMechanicalProgram(Models.MechanicalProgram program){
        LocalDate.parse(program.date);
        Models.PublicMeeting weekend=publicMeeting(program.date);
        if(weekend==null)throw new IllegalArgumentException("Configure primero la Reunión Pública y Atalaya de esta fecha.");
        if(weekend.suspended)throw new IllegalArgumentException("La reunión fue suspendida por asamblea.");
        saveMechanicalValues(program,program.date,"WEEKEND");
    }

    private void saveMechanicalValues(Models.MechanicalProgram program,String resolved,String meetingType){
        ContentValues values=new ContentValues();values.put("meeting_date",resolved);values.put("meeting_type",meetingType);
        for(Models.MechanicalRole role:Models.MechanicalRole.values()){
            long id=program.studentId(role);
            if(id!=0){Models.Student s=student(id);if(s==null||s.isSuspended()||!s.isMale()||!role.authorized(s))throw new IllegalArgumentException("Seleccione un varón activo autorizado para «"+role.label+"».");}
            values.put(role.column,id);
        }
        values.put("cantico_inicial",program.openingSong.trim());
        values.put("cantico_intermedio",program.middleSong.trim());
        values.put("cantico_final",program.closingSong.trim());
        getWritableDatabase().insertWithOnConflict("mechanical_programs",null,values,SQLiteDatabase.CONFLICT_REPLACE);
        program.date=resolved;program.meetingType=meetingType;
    }

    void deleteMechanicalProgram(String date){deleteMechanicalProgram(date,"MIDWEEK");}
    void deleteMechanicalProgram(String date,String type){getWritableDatabase().delete("mechanical_programs","meeting_date=? AND meeting_type=?",new String[]{date,type});}

    List<Models.VisitingSpeaker> visitingSpeakers(){
        List<Models.VisitingSpeaker> result=new ArrayList<>();
        try(Cursor cursor=getReadableDatabase().query("visiting_speakers",null,null,null,null,null,"name COLLATE NOCASE")){
            while(cursor.moveToNext())result.add(readVisitingSpeaker(cursor));
        }
        return result;
    }

    Models.VisitingSpeaker visitingSpeaker(long id){
        if(id==0)return null;
        try(Cursor cursor=getReadableDatabase().query("visiting_speakers",null,"id=?",new String[]{String.valueOf(id)},null,null,null)){
            return cursor.moveToFirst()?readVisitingSpeaker(cursor):null;
        }
    }

    private static Models.VisitingSpeaker readVisitingSpeaker(Cursor cursor){
        Models.VisitingSpeaker speaker=new Models.VisitingSpeaker();
        speaker.id=cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        speaker.name=cursor.getString(cursor.getColumnIndexOrThrow("name"));
        speaker.congregation=cursor.getString(cursor.getColumnIndexOrThrow("congregation"));
        speaker.locality=cursor.getString(cursor.getColumnIndexOrThrow("locality"));
        speaker.appointment=cursor.getString(cursor.getColumnIndexOrThrow("appointment"));
        return speaker;
    }

    long saveVisitingSpeaker(Models.VisitingSpeaker speaker){
        if(speaker.name.trim().isEmpty())throw new IllegalArgumentException("Indique el nombre del orador.");
        ContentValues values=new ContentValues();
        values.put("name",speaker.name.trim());values.put("congregation",speaker.congregation.trim());
        values.put("locality",speaker.locality.trim());values.put("appointment",speaker.appointment.trim());
        if(speaker.id==0)return getWritableDatabase().insertOrThrow("visiting_speakers",null,values);
        getWritableDatabase().update("visiting_speakers",values,"id=?",new String[]{String.valueOf(speaker.id)});
        return speaker.id;
    }

    boolean deleteVisitingSpeaker(long id){
        try(Cursor cursor=getReadableDatabase().rawQuery("SELECT 1 FROM public_meetings WHERE visitor_speaker_id=? LIMIT 1",new String[]{String.valueOf(id)})){
            if(cursor.moveToFirst())return false;
        }
        getWritableDatabase().delete("visiting_speakers","id=?",new String[]{String.valueOf(id)});
        return true;
    }

    Models.PublicMeeting publicMeeting(String date){
        try(Cursor cursor=getReadableDatabase().query("public_meetings",null,"meeting_date=?",new String[]{date},null,null,null)){
            return cursor.moveToFirst()?readPublicMeeting(cursor):null;
        }
    }

    List<Models.PublicMeeting> publicMeetings(String month){
        List<Models.PublicMeeting> result=new ArrayList<>();
        try(Cursor cursor=getReadableDatabase().query("public_meetings",null,"meeting_date LIKE ?",new String[]{month+"%"},null,null,"meeting_date")){
            while(cursor.moveToNext())result.add(readPublicMeeting(cursor));
        }
        return result;
    }

    private static Models.PublicMeeting readPublicMeeting(Cursor cursor){
        Models.PublicMeeting meeting=new Models.PublicMeeting();
        meeting.date=cursor.getString(cursor.getColumnIndexOrThrow("meeting_date"));
        meeting.presidentId=cursor.getLong(cursor.getColumnIndexOrThrow("president_student_id"));
        meeting.suspended=cursor.getInt(cursor.getColumnIndexOrThrow("suspended"))!=0;
        meeting.visit=cursor.getInt(cursor.getColumnIndexOrThrow("visit"))!=0;
        meeting.speakerKind=cursor.getString(cursor.getColumnIndexOrThrow("speaker_kind"));
        meeting.localSpeakerId=cursor.getLong(cursor.getColumnIndexOrThrow("local_speaker_id"));
        meeting.visitorSpeakerId=cursor.getLong(cursor.getColumnIndexOrThrow("visitor_speaker_id"));
        meeting.outlineNumber=cursor.getString(cursor.getColumnIndexOrThrow("outline_number"));
        meeting.outlineTitle=cursor.getString(cursor.getColumnIndexOrThrow("outline_title"));
        meeting.conductorId=cursor.getLong(cursor.getColumnIndexOrThrow("conductor_id"));
        meeting.watchtowerTopic=cursor.getString(cursor.getColumnIndexOrThrow("watchtower_topic"));
        meeting.readerId=cursor.getLong(cursor.getColumnIndexOrThrow("reader_id"));
        meeting.finalTalkTitle=cursor.getString(cursor.getColumnIndexOrThrow("final_talk_title"));
        meeting.evaluated=cursor.getInt(cursor.getColumnIndexOrThrow("evaluated"))!=0;
        meeting.speakerPresented=cursor.getInt(cursor.getColumnIndexOrThrow("speaker_presented"))!=0;
        meeting.evaluationComments=cursor.getString(cursor.getColumnIndexOrThrow("evaluation_comments"));
        return meeting;
    }

    void savePublicMeeting(Models.PublicMeeting meeting){
        LocalDate.parse(meeting.date);
        if(meeting.visit){meeting.speakerKind="VISITOR";meeting.localSpeakerId=0;meeting.readerId=0;}
        if(!"LOCAL".equals(meeting.speakerKind)&&!"VISITOR".equals(meeting.speakerKind))
            throw new IllegalArgumentException("Seleccione el origen del orador.");
        if(!meeting.suspended){
            Models.Student president=student(meeting.presidentId);
            if(president==null||president.isSuspended()||!president.isMale())
                throw new IllegalArgumentException("Seleccione un presidente varón activo del catálogo de alumnos.");
            if("LOCAL".equals(meeting.speakerKind)){
                if(meeting.localSpeakerId!=0){
                    Models.Student speaker=student(meeting.localSpeakerId);
                    if(speaker==null||speaker.isSuspended()||!speaker.isMale()||!speaker.discursoPublico)
                        throw new IllegalArgumentException("El orador local debe ser un varón autorizado para Discurso Público.");
                }
                meeting.visitorSpeakerId=0;
            }else{
                if(meeting.visitorSpeakerId!=0&&visitingSpeaker(meeting.visitorSpeakerId)==null)
                    throw new IllegalArgumentException("Seleccione un orador visitante registrado.");
                meeting.localSpeakerId=0;
            }
            if(meeting.conductorId!=0){
                Models.Student conductor=student(meeting.conductorId);
                if(conductor==null||conductor.isSuspended()||!conductor.isMale()||!conductor.atalaya)
                    throw new IllegalArgumentException("El conductor debe ser un varón autorizado para Atalaya.");
            }
            if(meeting.readerId!=0){
                Models.Student reader=student(meeting.readerId);
                if(reader==null||reader.isSuspended()||!reader.isMale()||!reader.lectorAtalaya)
                    throw new IllegalArgumentException("El lector debe ser un varón autorizado para Lector de la Atalaya.");
                if(meeting.readerId==meeting.conductorId)
                    throw new IllegalArgumentException("Conductor y lector deben ser personas distintas.");
            }
        }
        ContentValues values=new ContentValues();
        values.put("meeting_date",meeting.date);values.put("president_student_id",meeting.presidentId);
        values.put("suspended",meeting.suspended?1:0);values.put("visit",meeting.visit?1:0);
        values.put("speaker_kind",meeting.speakerKind);values.put("local_speaker_id",meeting.localSpeakerId);
        values.put("visitor_speaker_id",meeting.visitorSpeakerId);
        values.put("outline_number",meeting.outlineNumber.trim());values.put("outline_title",meeting.outlineTitle.trim());
        values.put("conductor_id",meeting.conductorId);values.put("watchtower_topic",meeting.watchtowerTopic.trim());
        values.put("reader_id",meeting.readerId);values.put("final_talk_title",meeting.finalTalkTitle.trim());
        values.put("evaluated",meeting.evaluated?1:0);values.put("speaker_presented",meeting.speakerPresented?1:0);
        values.put("evaluation_comments",meeting.evaluationComments.trim());
        getWritableDatabase().insertWithOnConflict("public_meetings",null,values,SQLiteDatabase.CONFLICT_REPLACE);
    }

    void savePublicSpeakerEvaluation(String date,boolean presented,String comments){
        ContentValues values=new ContentValues();values.put("evaluated",1);
        values.put("speaker_presented",presented?1:0);values.put("evaluation_comments",comments.trim());
        getWritableDatabase().update("public_meetings",values,"meeting_date=?",new String[]{date});
    }

    void deletePublicMeeting(String date){
        SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();
        try{
            sql.delete("mechanical_programs","meeting_date=? AND meeting_type='WEEKEND'",new String[]{date});
            sql.delete("public_meetings","meeting_date=?",new String[]{date});
            sql.setTransactionSuccessful();
        }finally{sql.endTransaction();}
    }

    List<Models.PublicMeeting> publicSpeakerHistory(String kind,long id){
        List<Models.PublicMeeting> result=new ArrayList<>();
        if(id<=0)return result;
        String field="VISITOR".equals(kind)?"visitor_speaker_id":"local_speaker_id";
        try(Cursor cursor=getReadableDatabase().query("public_meetings",null,"suspended=0 AND speaker_kind=? AND "+field+"=?",
                new String[]{kind,String.valueOf(id)},null,null,"meeting_date DESC")){
            while(cursor.moveToNext())result.add(readPublicMeeting(cursor));
        }
        return result;
    }

    List<Models.PublicMeeting> publicReaderHistory(long id){
        List<Models.PublicMeeting> result=new ArrayList<>();
        try(Cursor cursor=getReadableDatabase().query("public_meetings",null,"suspended=0 AND visit=0 AND reader_id=?",
                new String[]{String.valueOf(id)},null,null,"meeting_date DESC")){
            while(cursor.moveToNext())result.add(readPublicMeeting(cursor));
        }
        return result;
    }

    String validateMovement(Models.Movement movement) {
        Models.Student student = student(movement.studentId);
        Models.Presentation presentation = presentation(movement.presentationId);
        if (student == null || student.isSuspended()) return "El alumno no está disponible.";
        if (presentation == null) return "La presentación no existe.";
        Models.Meeting meeting = meeting(presentation.date);
        if (meeting.suspended) return "La reunión está suspendida.";
        if (meeting.visit && "AUXILIAR".equals(movement.room)) return "Durante la visita solo se usa la Sala Principal.";
        if (personIsUsedInOtherRoom(movement.studentId, movement.presentationId, movement.room, movement.id))
            return "El estudiante ya participa en la otra sala en esta misma asignación.";
        if (presentation.isIndividualMaleAssignment()) {
            if (student.isFemale()) return "Esta asignación está disponible únicamente para hombres.";
            movement.helperId = 0; movement.role = presentation.isBibleReading() ? "LECTOR" : "MAESTRO";
        } else {
            if (movement.helperId == 0) return "Seleccione un ayudante.";
            if (movement.helperId == movement.studentId) return "El estudiante no puede ser su propio ayudante.";
            Models.Student helper = student(movement.helperId);
            if (helper == null || helper.isSuspended()) return "El ayudante no está disponible.";
            if (personIsUsedInOtherRoom(movement.helperId, movement.presentationId, movement.room, movement.id))
                return "El ayudante ya participa en la otra sala en esta misma asignación.";
            if (student.rating <= 5 && student.rating == helper.rating) return "No se permiten parejas con el mismo calificador cuando el valor es 5 o menor.";
        }
        return null;
    }

    boolean personIsUsedInOtherRoom(long studentId, long presentationId, String room, long ignoredMovementId) {
        String sql = "SELECT 1 FROM movements m " +
                "JOIN presentations previous ON previous.id=m.presentation_id " +
                "JOIN presentations selected ON selected.id=? " +
                "WHERE m.id<>? AND m.room<>? " +
                "AND previous.meeting_date=selected.meeting_date AND previous.number=selected.number " +
                "AND (m.student_id=? OR m.helper_id=?) LIMIT 1";
        String[] args = {String.valueOf(presentationId), String.valueOf(ignoredMovementId), room, String.valueOf(studentId), String.valueOf(studentId)};
        try (Cursor c = getReadableDatabase().rawQuery(sql, args)) { return c.moveToFirst(); }
    }

    long saveMovement(Models.Movement m) {
        Models.Presentation p = presentation(m.presentationId); Models.Meeting meeting = meeting(p.date); m.visit = meeting.visit;
        ContentValues v=new ContentValues(); v.put("student_id",m.studentId);v.put("presentation_id",m.presentationId);v.put("room",m.room);v.put("role",m.role);v.put("helper_id",m.helperId);v.put("emergency",m.emergency?1:0);v.put("actual_minutes",m.actualMinutes);v.put("lesson",m.lesson);v.put("visit",m.visit?1:0);
        if(m.id==0) return getWritableDatabase().insertWithOnConflict("movements",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        getWritableDatabase().update("movements",v,"id=?",new String[]{String.valueOf(m.id)}); return m.id;
    }

    void deleteMovement(long id){getWritableDatabase().delete("movements","id=?",new String[]{String.valueOf(id)});}

    List<Models.ProgramRow> program(String month) {
        List<Models.ProgramRow> out=new ArrayList<>();
        String sql="SELECT m.id,m.student_id,m.presentation_id,m.room,m.role,m.helper_id,m.emergency,m.actual_minutes,m.lesson,m.visit,m.evaluated,m.actual_seconds,m.student_presented,m.student_absence_reason,m.replacement_student_id,m.helper_presented,m.helper_absence_reason,m.material_adhered,m.lesson_fulfilled,m.lesson_comments,m.evaluation_comments FROM movements m JOIN presentations p ON p.id=m.presentation_id WHERE p.meeting_date LIKE ? ORDER BY p.meeting_date,p.number,m.room DESC";
        try(Cursor c=getReadableDatabase().rawQuery(sql,new String[]{month+"%"})){
            while(c.moveToNext()){
                Models.Movement m=new Models.Movement();m.id=c.getLong(0);m.studentId=c.getLong(1);m.presentationId=c.getLong(2);m.room=c.getString(3);m.role=c.getString(4);m.helperId=c.getLong(5);m.emergency=c.getInt(6)!=0;m.actualMinutes=c.getInt(7);m.lesson=c.getString(8);m.visit=c.getInt(9)!=0;m.evaluated=c.getInt(10)!=0;m.actualSeconds=c.getInt(11);m.studentPresented=c.getInt(12)!=0;m.studentAbsenceReason=c.getString(13);m.replacementStudentId=c.getLong(14);m.helperPresented=c.getInt(15)!=0;m.helperAbsenceReason=c.getString(16);m.materialAdhered=c.getInt(17)!=0;m.lessonFulfilled=c.getInt(18)!=0;m.lessonComments=c.getString(19);m.evaluationComments=c.getString(20);
                Models.ProgramRow r=new Models.ProgramRow();r.movement=m;r.presentation=presentation(m.presentationId);r.student=student(m.studentId);r.helper=m.helperId==0?null:student(m.helperId);r.meeting=meeting(r.presentation.date);r.president=president(r.meeting.presidentId);out.add(r);
            }
        } return out;
    }

    void saveEvaluation(Models.Movement m){ContentValues v=new ContentValues();v.put("evaluated",1);v.put("actual_seconds",Math.max(0,m.actualSeconds));v.put("actual_minutes",Math.max(0,m.actualSeconds)/60);v.put("student_presented",m.studentPresented?1:0);v.put("student_absence_reason",m.studentAbsenceReason);v.put("replacement_student_id",m.replacementStudentId);v.put("helper_presented",m.helperPresented?1:0);v.put("helper_absence_reason",m.helperAbsenceReason);v.put("material_adhered",m.materialAdhered?1:0);v.put("lesson_fulfilled",m.lessonFulfilled?1:0);v.put("lesson_comments",m.lessonComments);v.put("evaluation_comments",m.evaluationComments);getWritableDatabase().update("movements",v,"id=?",new String[]{String.valueOf(m.id)});}

    List<Models.ProgramRow> history(long studentId) {
        List<Models.ProgramRow> all=program("____-__");
        List<Models.ProgramRow> out=new ArrayList<>();
        for(Models.ProgramRow r:all) if(r.movement.studentId==studentId || r.movement.helperId==studentId) out.add(r);
        String sql="SELECT id,meeting_date,assignment_number,room,student_id,helper_id,visit FROM historical_movements WHERE student_id=? OR helper_id=?";
        try(Cursor c=getReadableDatabase().rawQuery(sql,new String[]{String.valueOf(studentId),String.valueOf(studentId)})){while(c.moveToNext()){Models.Presentation p=new Models.Presentation();p.date=c.getString(1);p.number=c.getInt(2);p.assignmentName="Asignación histórica núm. "+p.number;Models.Movement m=new Models.Movement();m.id=-c.getLong(0);m.room=c.getString(3);m.studentId=c.getLong(4);m.helperId=c.getLong(5);m.visit=c.getInt(6)!=0;m.role="MAESTRO";Models.ProgramRow r=new Models.ProgramRow();r.presentation=p;r.movement=m;r.student=student(m.studentId);r.helper=m.helperId==0?null:student(m.helperId);r.meeting=meeting(p.date);out.add(r);}}
        out.sort((a,b)->b.presentation.date.compareTo(a.presentation.date)); return out;
    }

    static final class HelperSuggestion { Models.Student student; long days; String lastTogetherDate; boolean newPair; }

    List<HelperSuggestion> helperSuggestions(long mainStudentId, String assignmentDate, long presentationId, String room, long ignoredMovementId) {
        Map<Long,String> lastTogetherDate=new HashMap<>(); Map<Long,String> lastDate=new HashMap<>();
        String sql="SELECT student_id,helper_id,meeting_date FROM (SELECT m.student_id,m.helper_id,p.meeting_date FROM movements m JOIN presentations p ON p.id=m.presentation_id UNION ALL SELECT student_id,helper_id,meeting_date FROM historical_movements) WHERE meeting_date<? ORDER BY meeting_date DESC";
        try(Cursor c=getReadableDatabase().rawQuery(sql,new String[]{assignmentDate})){
            while(c.moveToNext()){
                long main=c.getLong(0), helper=c.getLong(1); String date=c.getString(2);
                if(main==mainStudentId && helper>0 && !lastTogetherDate.containsKey(helper)) lastTogetherDate.put(helper,date);
                if(helper==mainStudentId && !lastTogetherDate.containsKey(main)) lastTogetherDate.put(main,date);
                if(!lastDate.containsKey(main)) lastDate.put(main,date);
                if(helper>0 && !lastDate.containsKey(helper)) lastDate.put(helper,date);
            }
        }
        LocalDate target=LocalDate.parse(assignmentDate); List<HelperSuggestion> out=new ArrayList<>();
        for(Models.Student s:students(false)) if(s.id!=mainStudentId && !personIsUsedInOtherRoom(s.id,presentationId,room,ignoredMovementId)){
            HelperSuggestion h=new HelperSuggestion();h.student=s;h.lastTogetherDate=lastTogetherDate.get(s.id);h.newPair=h.lastTogetherDate==null;
            h.days=lastDate.containsKey(s.id)?ChronoUnit.DAYS.between(LocalDate.parse(lastDate.get(s.id)),target):Long.MAX_VALUE;out.add(h);
        }
        out.sort(Comparator.<HelperSuggestion,Boolean>comparing(h->!h.newPair).thenComparing((a,b)->Long.compare(b.days,a.days)).thenComparing(h->h.student.name));return out;
    }

    String resolveLifeMinistryDate(String requestedDate) {
        LocalDate date = LocalDate.parse(requestedDate);
        if (meetingConfigured(date.toString()) || schoolProgramDateExists(date.toString())) return date.toString();
        if (date.getDayOfWeek() == java.time.DayOfWeek.WEDNESDAY) {
            String tuesday = date.minusDays(1).toString();
            Models.Meeting meeting = meeting(tuesday);
            if (meetingConfigured(tuesday) && meeting.visit) return tuesday;
        }
        throw new IllegalArgumentException("Configure primero esta semana en Escuela del Ministerio Teocrático.");
    }

    private boolean meetingConfigured(String date) {
        try (Cursor c=getReadableDatabase().query("meetings",new String[]{"meeting_date"},"meeting_date=?",new String[]{date},null,null,null,"1")) { return c.moveToFirst(); }
    }

    private boolean schoolProgramDateExists(String date){try(Cursor c=getReadableDatabase().query("presentations",new String[]{"id"},"meeting_date=?",new String[]{date},null,null,null,"1")){return c.moveToFirst();}}

    long saveLifePart(Models.LifePart part) {
        part.date = resolveLifeMinistryDate(part.date);
        ContentValues values=lifePartValues(part);
        if(part.id==0)return getWritableDatabase().insertOrThrow("life_ministry_parts",null,values);
        getWritableDatabase().update("life_ministry_parts",values,"id=?",new String[]{String.valueOf(part.id)});return part.id;
    }

    int upsertImportedLifePart(Models.LifePart part) {
        part.date=resolveLifeMinistryDate(part.date);SQLiteDatabase sql=getWritableDatabase();long existing=0;
        try(Cursor c=sql.query("life_ministry_parts",new String[]{"id"},"meeting_date=? AND number=?",new String[]{part.date,String.valueOf(part.number)},null,null,null,"1")){if(c.moveToFirst())existing=c.getLong(0);}
        ContentValues values=lifePartValues(part);if(existing>0){part.id=existing;sql.update("life_ministry_parts",values,"id=?",new String[]{String.valueOf(existing)});return IMPORT_UPDATED;}
        part.id=sql.insertOrThrow("life_ministry_parts",null,values);return IMPORT_ADDED;
    }

    private ContentValues lifePartValues(Models.LifePart part){ContentValues values=new ContentValues();values.put("meeting_date",part.date);values.put("number",part.number);values.put("assignment_name",part.assignmentName.trim());values.put("minutes",part.minutes);values.put("topic",part.topic.trim());return values;}
    long importedLifeStudentId(String importedName) {
        String name=importedName==null?"":importedName.trim();
        if(name.isEmpty())return 0;
        List<Long> ids=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("students",new String[]{"id"},"name=? COLLATE NOCASE AND rating>0",new String[]{name},null,null,"name COLLATE NOCASE")){
            while(c.moveToNext())ids.add(c.getLong(0));
        }
        if(ids.isEmpty())throw new IllegalArgumentException("No se encontró un alumno activo llamado: "+name);
        if(ids.size()>1)throw new IllegalArgumentException("Hay más de un alumno activo llamado "+name+"; use un nombre que permita distinguirlo.");
        return ids.get(0);
    }

    void upsertImportedLifeAssignment(long partId, Long studentId, Long readerId) {
        SQLiteDatabase sql=getWritableDatabase();
        long existingId=0,existingStudent=0,existingReader=0;
        try(Cursor c=sql.query("life_ministry_assignments",new String[]{"id","student_id","reader_id"},"part_id=?",new String[]{String.valueOf(partId)},null,null,null,"1")){
            if(c.moveToFirst()){existingId=c.getLong(0);existingStudent=c.getLong(1);existingReader=c.getLong(2);}
        }
        long finalStudent=studentId==null?existingStudent:studentId;
        long finalReader=readerId==null?existingReader:readerId;
        if(finalStudent<=0)throw new IllegalArgumentException("Indique el alumno en la columna Alumno asignado para crear la asignación.");
        if(finalReader>0&&finalReader==finalStudent)throw new IllegalArgumentException("El alumno y el lector deben ser personas distintas.");
        if(existingId>0){
            ContentValues values=new ContentValues();
            if(studentId!=null)values.put("student_id",finalStudent);
            if(readerId!=null)values.put("reader_id",finalReader);
            if(values.size()>0)sql.update("life_ministry_assignments",values,"id=?",new String[]{String.valueOf(existingId)});
        }else{
            ContentValues values=new ContentValues();values.put("part_id",partId);values.put("student_id",finalStudent);values.put("reader_id",finalReader);
            sql.insertOrThrow("life_ministry_assignments",null,values);
        }
    }


    List<Models.LifePart> lifeParts(String month){List<Models.LifePart> out=new ArrayList<>();String where=month==null?null:"meeting_date LIKE ?";String[] args=month==null?null:new String[]{month+"%"};try(Cursor c=getReadableDatabase().query("life_ministry_parts",null,where,args,null,null,"meeting_date,number")){while(c.moveToNext()){Models.LifePart part=readLifePart(c);if(!meeting(part.date).visit||!part.isBookStudy())out.add(part);}}return out;}

    Models.LifePart lifePart(long id){try(Cursor c=getReadableDatabase().query("life_ministry_parts",null,"id=?",new String[]{String.valueOf(id)},null,null,null)){return c.moveToFirst()?readLifePart(c):null;}}

    List<Long> visitBookAssignmentIds(String date){List<Long> ids=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT a.id,p.assignment_name FROM life_ministry_assignments a JOIN life_ministry_parts p ON p.id=a.part_id WHERE p.meeting_date=?",new String[]{date})){while(c.moveToNext()){Models.LifePart part=new Models.LifePart();part.assignmentName=c.getString(1);if(part.isBookStudy())ids.add(c.getLong(0));}}return ids;}

    private Models.LifePart readLifePart(Cursor c){Models.LifePart p=new Models.LifePart();p.id=c.getLong(c.getColumnIndexOrThrow("id"));p.date=c.getString(c.getColumnIndexOrThrow("meeting_date"));p.number=c.getInt(c.getColumnIndexOrThrow("number"));p.assignmentName=c.getString(c.getColumnIndexOrThrow("assignment_name"));p.minutes=c.getInt(c.getColumnIndexOrThrow("minutes"));p.topic=c.getString(c.getColumnIndexOrThrow("topic"));return p;}

    void deleteLifePart(long id){SQLiteDatabase sql=getWritableDatabase();sql.delete("life_ministry_assignments","part_id=?",new String[]{String.valueOf(id)});sql.delete("life_ministry_parts","id=?",new String[]{String.valueOf(id)});}

    String validateLifeAssignment(Models.LifeAssignment assignment){Models.LifePart part=lifePart(assignment.partId);Models.Student student=student(assignment.studentId);if(part==null)return "La asignación del programa no existe.";if(meeting(part.date).suspended)return "La reunión está suspendida.";if(meeting(part.date).visit&&part.isBookStudy())return "Durante la visita se cancela el Estudio del Libro.";if(student==null||student.isSuspended()||!student.vidaMinisterio)return "El participante no está activo para Vida y Ministerio.";if(part.isBookStudy()&&!student.estudioLibro)return "El conductor debe tener activa la clasificación Estudio del Libro.";if(part.isBookStudy()){Models.Student reader=student(assignment.readerId);if(reader==null||reader.isSuspended()||!reader.lectorEstudioLibro)return "Seleccione un lector activo para el Estudio del Libro.";if(reader.id==student.id)return "El conductor y el lector deben ser personas distintas.";}else assignment.readerId=0;return null;}

    long saveLifeAssignment(Models.LifeAssignment assignment){String error=validateLifeAssignment(assignment);if(error!=null)throw new IllegalArgumentException(error);ContentValues values=new ContentValues();values.put("part_id",assignment.partId);values.put("student_id",assignment.studentId);values.put("reader_id",assignment.readerId);if(assignment.id==0)return getWritableDatabase().insertOrThrow("life_ministry_assignments",null,values);getWritableDatabase().update("life_ministry_assignments",values,"id=?",new String[]{String.valueOf(assignment.id)});return assignment.id;}

    void deleteLifeAssignment(long id){getWritableDatabase().delete("life_ministry_assignments","id=?",new String[]{String.valueOf(id)});}

    List<Models.LifeProgramRow> lifeProgram(String month){List<Models.LifeProgramRow> out=new ArrayList<>();String sql="SELECT a.id,a.part_id,a.student_id,a.reader_id,a.evaluated,a.actual_seconds,a.student_presented,a.student_absence_reason,a.replacement_student_id,a.reader_presented,a.reader_absence_reason,a.topic_adhered,a.evaluation_comments FROM life_ministry_assignments a JOIN life_ministry_parts p ON p.id=a.part_id WHERE p.meeting_date LIKE ? ORDER BY p.meeting_date,p.number";try(Cursor c=getReadableDatabase().rawQuery(sql,new String[]{month+"%"})){while(c.moveToNext()){Models.LifeAssignment a=new Models.LifeAssignment();a.id=c.getLong(0);a.partId=c.getLong(1);a.studentId=c.getLong(2);a.readerId=c.getLong(3);a.evaluated=c.getInt(4)!=0;a.actualSeconds=c.getInt(5);a.studentPresented=c.getInt(6)!=0;a.studentAbsenceReason=c.getString(7);a.replacementStudentId=c.getLong(8);a.readerPresented=c.getInt(9)!=0;a.readerAbsenceReason=c.getString(10);a.topicAdhered=c.getInt(11)!=0;a.evaluationComments=c.getString(12);Models.LifeProgramRow row=new Models.LifeProgramRow();row.assignment=a;row.part=lifePart(a.partId);row.meeting=meeting(row.part.date);if(row.meeting.visit&&row.part.isBookStudy())continue;row.student=student(a.studentId);row.reader=a.readerId==0?null:student(a.readerId);row.president=president(row.meeting.presidentId);out.add(row);}}return out;}

    Models.LifeProgramRow lifeProgramRow(long assignmentId){for(Models.LifeProgramRow row:lifeProgram("____-__"))if(row.assignment.id==assignmentId)return row;return null;}

    List<Models.LifeProgramRow> lifeHistory(long studentId){List<Models.LifeProgramRow> out=new ArrayList<>();for(Models.LifeProgramRow row:lifeProgram("____-__"))if(row.assignment.studentId==studentId||row.assignment.readerId==studentId)out.add(row);out.sort((a,b)->b.part.date.compareTo(a.part.date));return out;}

    void saveLifeEvaluation(Models.LifeAssignment assignment){ContentValues values=new ContentValues();values.put("evaluated",1);values.put("actual_seconds",Math.max(0,assignment.actualSeconds));values.put("student_presented",assignment.studentPresented?1:0);values.put("student_absence_reason",assignment.studentAbsenceReason);values.put("replacement_student_id",assignment.replacementStudentId);values.put("reader_presented",assignment.readerPresented?1:0);values.put("reader_absence_reason",assignment.readerAbsenceReason);values.put("topic_adhered",assignment.topicAdhered?1:0);values.put("evaluation_comments",assignment.evaluationComments);getWritableDatabase().update("life_ministry_assignments",values,"id=?",new String[]{String.valueOf(assignment.id)});}
}
