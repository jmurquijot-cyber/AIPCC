package mx.escuelaministerio.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Catalog and membership rules for congregation field-service groups. */
final class PreachingGroups {
    static final class Group {
        int number;
        String name="",meetingPointName="",meetingPointAddress="",meetingPointPhone="";
        long captainId,assistantId;
        @Override public String toString(){return number+" · "+name;}
    }

    private final MinisterioDb db;
    PreachingGroups(MinisterioDb db){this.db=db;}

    static void validate(Group group){
        if(group.number<1)throw new IllegalArgumentException("Indique un número de grupo mayor que cero.");
        if(clean(group.name).isEmpty())throw new IllegalArgumentException("Indique el nombre del grupo.");
        if(clean(group.meetingPointName).isEmpty())throw new IllegalArgumentException("Indique el nombre del punto de encuentro.");
        if(clean(group.meetingPointAddress).isEmpty())throw new IllegalArgumentException("Indique la dirección del punto de encuentro.");
    }

    List<Group> all(){
        List<Group> result=new ArrayList<>();
        try(Cursor cursor=db.getReadableDatabase().rawQuery("SELECT group_number,name,captain_student_id,assistant_student_id,meeting_point_name,meeting_point_address,meeting_point_phone FROM preaching_groups ORDER BY group_number",null)){
            while(cursor.moveToNext())result.add(read(cursor));
        }
        return result;
    }

    Group get(int number){
        try(Cursor cursor=db.getReadableDatabase().rawQuery("SELECT group_number,name,captain_student_id,assistant_student_id,meeting_point_name,meeting_point_address,meeting_point_phone FROM preaching_groups WHERE group_number=?",new String[]{String.valueOf(number)})){
            return cursor.moveToFirst()?read(cursor):null;
        }
    }

    void save(Group group,boolean creating){
        validate(group);SQLiteDatabase sql=db.getWritableDatabase();ContentValues values=values(group);
        if(creating){if(get(group.number)!=null)throw new IllegalArgumentException("Ya existe el grupo número "+group.number+".");sql.insertOrThrow("preaching_groups",null,values);}
        else if(sql.update("preaching_groups",values,"group_number=?",new String[]{String.valueOf(group.number)})==0)throw new IllegalArgumentException("El grupo ya no existe.");
    }

    void delete(Group group){
        SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
        try{ContentValues clear=new ContentValues();clear.put("group_number",0);sql.update("students",clear,"group_number=?",new String[]{String.valueOf(group.number)});sql.delete("preaching_groups","group_number=?",new String[]{String.valueOf(group.number)});sql.setTransactionSuccessful();}
        finally{sql.endTransaction();}
    }

    List<Models.Student> members(int number,boolean includeSuspended){
        List<Models.Student> result=new ArrayList<>();for(Models.Student student:db.students(includeSuspended))if(student.groupNumber==number)result.add(student);return result;
    }

    List<Models.Student> unassigned(){
        List<Models.Student> result=new ArrayList<>();for(Models.Student student:db.students(true))if(student.groupNumber==0)result.add(student);return result;
    }

    int pioneerCount(int number){int count=0;for(Models.Student member:members(number,true))if(member.pioneer)count++;return count;}

    void replaceMembers(Group group,List<Long> memberIds){
        if(get(group.number)==null)throw new IllegalArgumentException("Guarde primero los datos del grupo.");
        Set<Long> selected=new HashSet<>(memberIds);SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
        try{
            for(Models.Student student:db.students(true)){
                boolean chosen=selected.contains(student.id);
                if(chosen||student.groupNumber==group.number){ContentValues value=new ContentValues();value.put("group_number",chosen?group.number:0);sql.update("students",value,"id=?",new String[]{String.valueOf(student.id)});}
            }
            sql.execSQL("UPDATE preaching_groups SET captain_student_id=0 WHERE captain_student_id!=0 AND NOT EXISTS (SELECT 1 FROM students s WHERE s.id=preaching_groups.captain_student_id AND s.group_number=preaching_groups.group_number)");
            sql.execSQL("UPDATE preaching_groups SET assistant_student_id=0 WHERE assistant_student_id!=0 AND NOT EXISTS (SELECT 1 FROM students s WHERE s.id=preaching_groups.assistant_student_id AND s.group_number=preaching_groups.group_number)");
            sql.setTransactionSuccessful();
        }finally{sql.endTransaction();}
    }

    void setLeaders(Group group,long captainId,long assistantId){
        if(captainId==0||assistantId==0)throw new IllegalArgumentException("Seleccione capitán y auxiliar del grupo.");
        if(captainId==assistantId)throw new IllegalArgumentException("El capitán y el auxiliar deben ser personas diferentes.");
        Models.Student captain=db.student(captainId),assistant=db.student(assistantId);
        if(captain==null||assistant==null||captain.groupNumber!=group.number||assistant.groupNumber!=group.number)throw new IllegalArgumentException("Los responsables deben pertenecer a este grupo.");
        if(captain.isSuspended()||assistant.isSuspended())throw new IllegalArgumentException("Los responsables deben ser alumnos activos.");
        ContentValues values=new ContentValues();values.put("captain_student_id",captainId);values.put("assistant_student_id",assistantId);db.getWritableDatabase().update("preaching_groups",values,"group_number=?",new String[]{String.valueOf(group.number)});group.captainId=captainId;group.assistantId=assistantId;
    }

    String studentName(long id){Models.Student student=db.student(id);return student==null?"PENDIENTE":student.name;}

    private static Group read(Cursor cursor){Group group=new Group();group.number=cursor.getInt(0);group.name=cursor.getString(1);group.captainId=cursor.getLong(2);group.assistantId=cursor.getLong(3);group.meetingPointName=cursor.getString(4);group.meetingPointAddress=cursor.getString(5);group.meetingPointPhone=cursor.getString(6);return group;}
    private static ContentValues values(Group group){ContentValues values=new ContentValues();values.put("group_number",group.number);values.put("name",clean(group.name));values.put("captain_student_id",group.captainId);values.put("assistant_student_id",group.assistantId);values.put("meeting_point_name",clean(group.meetingPointName));values.put("meeting_point_address",clean(group.meetingPointAddress));values.put("meeting_point_phone",clean(group.meetingPointPhone));return values;}
    private static String clean(String value){return value==null?"":value.trim();}
}
