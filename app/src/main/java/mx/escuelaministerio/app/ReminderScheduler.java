package mx.escuelaministerio.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

final class ReminderScheduler {
    private ReminderScheduler() {}

    static void schedule(Context context, Models.ProgramRow row) {
        LocalDate date = LocalDate.parse(row.presentation.date).minusDays(7);
        long when = LocalDateTime.of(date, java.time.LocalTime.of(9, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        if (when <= System.currentTimeMillis()) return;
        String president = row.president == null ? "el presidente de la reunión" : row.president.name;
        String body = "Tienes " + row.presentation.assignmentName + " el " + row.presentation.date + " en Sala " + row.movement.room.toLowerCase(Locale.ROOT) + ". Si no podrás presentarla, informa a " + president + ".";
        int id = (int) (row.movement.id & 0x7fffffff);
        Intent intent = new Intent(context, ReminderReceiver.class).putExtra("id", id).putExtra("title", "Asignación en siete días").putExtra("body", body);
        PendingIntent pending = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarm = context.getSystemService(AlarmManager.class);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms())
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
        else
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
    }

    static void cancel(Context context,long movementId){int id=(int)(movementId&0x7fffffff);Intent intent=new Intent(context,ReminderReceiver.class);PendingIntent pending=PendingIntent.getBroadcast(context,id,intent,PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);if(pending!=null){context.getSystemService(AlarmManager.class).cancel(pending);pending.cancel();}}

    static void scheduleLife(Context context,Models.LifeProgramRow row,boolean reader){
        if(row==null||row.assignment==null||(reader&&row.reader==null)||(row.meeting!=null&&row.meeting.visit&&row.part.isBookStudy()))return;
        LocalDate reminderDate=LocalDate.parse(row.part.date).minusDays(7);long when=LocalDateTime.of(reminderDate,java.time.LocalTime.of(9,0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();if(when<=System.currentTimeMillis())return;
        String president=row.president==null?"el presidente de la reunión":row.president.name;String role=reader?"como lector del Estudio del Libro":"en «"+row.part.assignmentName+"»";String body="Tienes una participación "+role+" el "+row.part.date+" en Sala Principal. Si no podrás presentarla, informa a "+president+".";int id=lifeRequestId(row.assignment.id,reader);schedule(context,id,when,"Participación en siete días",body);
    }

    static void cancelLife(Context context,long assignmentId){cancelById(context,lifeRequestId(assignmentId,false));cancelById(context,lifeRequestId(assignmentId,true));}

    private static int lifeRequestId(long assignmentId,boolean reader){return (reader?0x60000000:0x40000000)|(int)(assignmentId&0x1fffffff);}
    private static void schedule(Context context,int id,long when,String title,String body){Intent intent=new Intent(context,ReminderReceiver.class).putExtra("id",id).putExtra("title",title).putExtra("body",body);PendingIntent pending=PendingIntent.getBroadcast(context,id,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);AlarmManager alarm=context.getSystemService(AlarmManager.class);if(Build.VERSION.SDK_INT<Build.VERSION_CODES.S||alarm.canScheduleExactAlarms())alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pending);else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pending);}
    private static void cancelById(Context context,int id){Intent intent=new Intent(context,ReminderReceiver.class);PendingIntent pending=PendingIntent.getBroadcast(context,id,intent,PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);if(pending!=null){context.getSystemService(AlarmManager.class).cancel(pending);pending.cancel();}}
}
