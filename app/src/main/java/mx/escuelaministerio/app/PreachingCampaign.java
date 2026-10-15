package mx.escuelaministerio.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Campaign settings and publisher assignments use separate tables and screens. */
final class PreachingCampaign {
    static final class Campaign {
        long id;
        String startDate="",endDate="",module="",congregation="";
        int startMinute,endMinute,exhibitors=1;
        int slots(){return (endMinute-startMinute)/120;}
        String slotLabel(int slot){return time(startMinute+slot*120)+" – "+time(startMinute+(slot+1)*120);}
        @Override public String toString(){return startDate+" a "+endDate+" · "+exhibitors+" exhibidor"+(exhibitors==1?"":"es");}
    }
    static final class Team {
        final long[] publishers=new long[4];
    }
    static final class DayResponsibilities {
        long captain,transport;
        boolean finalized;
    }
    private final MinisterioDb db;
    PreachingCampaign(MinisterioDb db){this.db=db;}

    static String time(int minute){return String.format(Locale.ROOT,"%02d:%02d",minute/60,minute%60);}
    static int minute(String text){String[] parts=text.split(":");if(parts.length!=2)throw new IllegalArgumentException("Seleccione una hora válida.");int h=Integer.parseInt(parts[0]),m=Integer.parseInt(parts[1]);if(h<0||h>23||m<0||m>59)throw new IllegalArgumentException("Seleccione una hora válida.");return h*60+m;}
    static void validate(Campaign c){
        LocalDate first,last;
        try{first=LocalDate.parse(c.startDate);last=LocalDate.parse(c.endDate);}catch(Exception error){throw new IllegalArgumentException("Seleccione las fechas de inicio y fin.");}
        long days=ChronoUnit.DAYS.between(first,last);
        if(days<0||days>365)throw new IllegalArgumentException("El final debe ser posterior al inicio y la campaña no puede superar 366 días.");
        if(c.exhibitors<1||c.exhibitors>6)throw new IllegalArgumentException("Defina de uno a seis exhibidores.");
        int length=c.endMinute-c.startMinute;
        if(c.startMinute<0||c.endMinute>1440||length<120||length%120!=0)throw new IllegalArgumentException("La jornada debe terminar después de iniciar y durar un múltiplo exacto de dos horas.");
        if(c.slots()>8)throw new IllegalArgumentException("La jornada permite un máximo de ocho turnos de dos horas.");
    }
    static void validateDay(Campaign c,String day){LocalDate d;try{d=LocalDate.parse(day);}catch(Exception error){throw new IllegalArgumentException("Seleccione una fecha válida.");}if(d.isBefore(LocalDate.parse(c.startDate))||d.isAfter(LocalDate.parse(c.endDate)))throw new IllegalArgumentException("La fecha está fuera de la campaña.");}

    List<Campaign> all(){List<Campaign> result=new ArrayList<>();try(Cursor r=db.getReadableDatabase().rawQuery("SELECT id,start_date,end_date,start_minute,end_minute,exhibitor_count,module,congregation FROM preaching_campaigns ORDER BY start_date DESC,id DESC",null)){while(r.moveToNext()){Campaign c=new Campaign();c.id=r.getLong(0);c.startDate=r.getString(1);c.endDate=r.getString(2);c.startMinute=r.getInt(3);c.endMinute=r.getInt(4);c.exhibitors=r.getInt(5);c.module=r.getString(6);c.congregation=r.getString(7);result.add(c);}}return result;}
    Campaign byId(long id){for(Campaign c:all())if(c.id==id)return c;throw new IllegalArgumentException("La campaña ya no existe.");}
    long save(Campaign c){validate(c);SQLiteDatabase sql=db.getWritableDatabase();ContentValues v=new ContentValues();v.put("start_date",c.startDate);v.put("end_date",c.endDate);v.put("start_minute",c.startMinute);v.put("end_minute",c.endMinute);v.put("exhibitor_count",c.exhibitors);v.put("module",c.module.trim());v.put("congregation",c.congregation.trim());
        if(c.id==0){c.id=sql.insertOrThrow("preaching_campaigns",null,v);return c.id;}
        Campaign previous=byId(c.id);if(!previous.startDate.equals(c.startDate)||!previous.endDate.equals(c.endDate)||previous.startMinute!=c.startMinute||previous.endMinute!=c.endMinute||previous.exhibitors!=c.exhibitors){
            if(previous.startMinute!=c.startMinute||previous.endMinute!=c.endMinute)try(Cursor rows=sql.rawQuery("SELECT COUNT(*) FROM preaching_publishers WHERE campaign_id=?",new String[]{String.valueOf(c.id)})){if(rows.moveToFirst()&&rows.getInt(0)>0)throw new IllegalArgumentException("Quite las asignaciones antes de cambiar el horario de una campaña ya programada.");}
            try(Cursor rows=sql.rawQuery("SELECT COUNT(*) FROM preaching_publishers WHERE campaign_id=? AND (day<? OR day>? OR slot>=? OR exhibitor>?)",new String[]{String.valueOf(c.id),c.startDate,c.endDate,String.valueOf(c.slots()),String.valueOf(c.exhibitors)})){if(rows.moveToFirst()&&rows.getInt(0)>0)throw new IllegalArgumentException("Hay publicadores fuera del nuevo horario o número de exhibidores. Quite primero esas asignaciones.");}
            if(previous.exhibitors!=c.exhibitors){ContentValues draft=new ContentValues();draft.put("finalized",0);sql.update("preaching_day_captains",draft,"campaign_id=?",new String[]{String.valueOf(c.id)});}
            sql.delete("preaching_day_captains","campaign_id=? AND (day<? OR day>?)",new String[]{String.valueOf(c.id),c.startDate,c.endDate});
            sql.delete("preaching_transport","campaign_id=? AND (day<? OR day>?)",new String[]{String.valueOf(c.id),c.startDate,c.endDate});
        }
        sql.update("preaching_campaigns",v,"id=?",new String[]{String.valueOf(c.id)});return c.id;
    }
    void remove(long id){SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();try{String[] key={String.valueOf(id)};sql.delete("preaching_publishers","campaign_id=?",key);sql.delete("preaching_shift_captains","campaign_id=?",key);sql.delete("preaching_day_captains","campaign_id=?",key);sql.delete("preaching_transport","campaign_id=?",key);sql.delete("preaching_campaigns","id=?",key);sql.setTransactionSuccessful();}finally{sql.endTransaction();}}

    Team team(Campaign c,String day,int slot,int booth){check(c,day,slot,booth);Team t=new Team();String[] key={String.valueOf(c.id),day,String.valueOf(slot),String.valueOf(booth)};try(Cursor r=db.getReadableDatabase().rawQuery("SELECT position,student_id FROM preaching_publishers WHERE campaign_id=? AND day=? AND slot=? AND exhibitor=?",key)){while(r.moveToNext())t.publishers[r.getInt(0)-1]=r.getLong(1);}return t;}
    void saveTeam(Campaign c,String day,int slot,int booth,Team t){check(c,day,slot,booth);Set<Long> unique=new HashSet<>();for(long student:t.publishers)if(student!=0&&!unique.add(student))throw new IllegalArgumentException("No repita un publicador en el mismo exhibidor y turno.");
        SQLiteDatabase sql=db.getWritableDatabase();String[] key={String.valueOf(c.id),day,String.valueOf(slot),String.valueOf(booth)};sql.beginTransaction();try{
            // A publisher may repeat in another turn, but not in another exhibitor at this hour.
            for(long student:unique)try(Cursor r=sql.rawQuery("SELECT COUNT(*) FROM preaching_publishers WHERE campaign_id=? AND day=? AND slot=? AND exhibitor<>? AND student_id=?",new String[]{key[0],key[1],key[2],key[3],String.valueOf(student)})){if(r.moveToFirst()&&r.getInt(0)>0)throw new IllegalArgumentException("Un publicador ya figura en otro exhibidor durante el mismo turno.");}
            sql.delete("preaching_publishers","campaign_id=? AND day=? AND slot=? AND exhibitor=?",key);
            for(int i=0;i<4;i++)if(t.publishers[i]!=0){ContentValues v=new ContentValues();v.put("campaign_id",c.id);v.put("day",day);v.put("slot",slot);v.put("exhibitor",booth);v.put("position",i+1);v.put("student_id",t.publishers[i]);sql.insertOrThrow("preaching_publishers",null,v);}
            ContentValues draft=new ContentValues();draft.put("finalized",0);sql.update("preaching_day_captains",draft,"campaign_id=? AND day=?",new String[]{key[0],key[1]});sql.setTransactionSuccessful();
        }finally{sql.endTransaction();}
    }
    int dayPublisherCount(Campaign c,String day){validateDay(c,day);try(Cursor r=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM preaching_publishers WHERE campaign_id=? AND day=?",new String[]{String.valueOf(c.id),day})){return r.moveToFirst()?r.getInt(0):0;}}
    boolean isDayPublisher(Campaign c,String day,long id){if(id==0)return false;try(Cursor r=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM preaching_publishers WHERE campaign_id=? AND day=? AND student_id=?",new String[]{String.valueOf(c.id),day,String.valueOf(id)})){return r.moveToFirst()&&r.getInt(0)>0;}}
    int legacyCaptainCount(Campaign c,String day){try(Cursor r=db.getReadableDatabase().rawQuery("SELECT COUNT(DISTINCT student_id) FROM preaching_shift_captains WHERE campaign_id=? AND day=?",new String[]{String.valueOf(c.id),day})){return r.moveToFirst()?r.getInt(0):0;}}
    DayResponsibilities responsibilities(Campaign c,String day){validateDay(c,day);DayResponsibilities t=new DayResponsibilities();String[] key={String.valueOf(c.id),day};try(Cursor r=db.getReadableDatabase().rawQuery("SELECT student_id,finalized FROM preaching_day_captains WHERE campaign_id=? AND day=?",key)){if(r.moveToFirst()){t.captain=r.getLong(0);t.finalized=r.getInt(1)!=0;}}try(Cursor r=db.getReadableDatabase().rawQuery("SELECT transport_student_id,bring_student_id,collect_student_id FROM preaching_transport WHERE campaign_id=? AND day=?",key)){if(r.moveToFirst()){
                t.transport=r.getLong(0);
                // Legacy campaigns used separate bring/collect transporters. They remain
                // stored for historical compatibility, but the campaign UI now uses one
                // responsible publisher for both movements.
                if(t.transport==0 && r.getLong(1)!=0 && r.getLong(1)==r.getLong(2)) t.transport=r.getLong(1);
            }}return t;}
    boolean modulesViable(Campaign c,String day){
        validateDay(c,day);
        for(int slot=0;slot<c.slots();slot++)for(int booth=1;booth<=c.exhibitors;booth++){
            if(assignedCount(team(c,day,slot,booth))<2)return false;
        }
        return true;
    }
    int nonViableModules(Campaign c,String day){
        validateDay(c,day);int count=0;
        for(int slot=0;slot<c.slots();slot++)for(int booth=1;booth<=c.exhibitors;booth++)
            if(assignedCount(team(c,day,slot,booth))<2)count++;
        return count;
    }
    private static int assignedCount(Team t){int n=0;for(long id:t.publishers)if(id!=0)n++;return n;}
    void saveDayResponsibilities(Campaign c,String day,DayResponsibilities t){
        validateDay(c,day);SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();try{
            if(!modulesViable(c,day))throw new IllegalArgumentException("Cada módulo debe tener al menos dos publicadores para ser viable.");
            if(t.captain==0||!isDayPublisher(c,day,t.captain))throw new IllegalArgumentException("El capitán del día debe ser uno de los publicadores asignados para ese día.");
            if(t.transport==0)throw new IllegalArgumentException("Seleccione un publicador encargado de transportar los módulos al inicio y al final del día.");
            if(db.student(t.transport)==null)throw new IllegalArgumentException("El publicador encargado del transporte ya no existe.");
            ContentValues captain=new ContentValues();captain.put("campaign_id",c.id);captain.put("day",day);captain.put("student_id",t.captain);captain.put("finalized",0);
            sql.insertWithOnConflict("preaching_day_captains",null,captain,SQLiteDatabase.CONFLICT_REPLACE);
            ContentValues transport=new ContentValues();transport.put("campaign_id",c.id);transport.put("day",day);transport.put("transport_student_id",t.transport);transport.put("bring_student_id",t.transport);transport.put("collect_student_id",t.transport);
            sql.insertWithOnConflict("preaching_transport",null,transport,SQLiteDatabase.CONFLICT_REPLACE);sql.setTransactionSuccessful();
        }finally{sql.endTransaction();}
    }
    private static void check(Campaign c,String day,int slot,int booth){validateDay(c,day);if(slot<0||slot>=c.slots()||booth<1||booth>c.exhibitors)throw new IllegalArgumentException("Turno o exhibidor fuera de la campaña.");}
}
