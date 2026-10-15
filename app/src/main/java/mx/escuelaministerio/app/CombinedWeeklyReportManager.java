package mx.escuelaministerio.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Imagen semanal integrada de Vida y Ministerio, Escuela y Asignaciones Mecánicas. */
final class CombinedWeeklyReportManager {
    private static final Locale ES=new Locale("es","MX");
    private static final int WIDTH=1200,LEFT=24,RIGHT=1176;
    private final Context context;
    private final MinisterioDb db;
    private final SharedPreferences colors;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);

    CombinedWeeklyReportManager(Context context,MinisterioDb db){
        this.context=context;this.db=db;colors=context.getSharedPreferences("palette",Context.MODE_PRIVATE);
    }

    File weeklyJpg(String requestedDate,String meetingTime)throws IOException {
        String date=db.resolveLifeMinistryDate(requestedDate);
        if(db.meetings(date.substring(0,7)).stream().noneMatch(m->m.date.equals(date)))
            throw new IllegalArgumentException("Configure primero la semana seleccionada.");
        Models.Meeting meeting=db.meeting(date);
        if(meeting.suspended)throw new IllegalArgumentException("La reunión de esta semana está suspendida.");
        Models.MechanicalProgram mechanical=db.mechanicalProgram(date);
        Models.President president=db.president(meeting.presidentId);

        List<Models.LifePart> parts=db.lifeParts(date.substring(0,7));parts.removeIf(p->!date.equals(p.date));
        List<Models.LifePart> treasures=new ArrayList<>(),christianLife=new ArrayList<>();
        for(Models.LifePart part:parts){if(part.number==1||part.number==2)treasures.add(part);else christianLife.add(part);}
        Map<Long,Models.LifeProgramRow> lifeByPart=new HashMap<>();
        for(Models.LifeProgramRow row:db.lifeProgram(date.substring(0,7)))if(date.equals(row.part.date))lifeByPart.put(row.part.id,row);
        List<Models.Presentation> school=db.presentations(date.substring(0,7));school.removeIf(p->!date.equals(p.date));
        Map<String,Models.ProgramRow> schoolByRoom=new HashMap<>();
        for(Models.ProgramRow row:db.program(date.substring(0,7)))if(date.equals(row.presentation.date))schoolByRoom.put(row.presentation.id+"/"+row.movement.room,row);

        String time=meetingTime==null||meetingTime.trim().isEmpty()?"6:00 p. m.":meetingTime.trim();
        // Se mide primero el contenido: la imagen conserva todos los renglones sin cortarlos.
        Sheet measure=new Sheet(null);
        compose(measure,date,time,meeting,mechanical,president,treasures,christianLife,lifeByPart,school,schoolByRoom);
        int height=(int)Math.ceil(measure.y+24);
        if(height>12000)throw new IllegalArgumentException("La semana contiene demasiado texto para una sola imagen.");
        Bitmap bitmap;
        try{bitmap=Bitmap.createBitmap(WIDTH,height,Bitmap.Config.ARGB_8888);}
        catch(OutOfMemoryError error){throw new IOException("No hay suficiente memoria para generar esta imagen.",error);}
        try{
            Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.WHITE);
            Sheet output=new Sheet(canvas);
            compose(output,date,time,meeting,mechanical,president,treasures,christianLife,lifeByPart,school,schoolByRoom);
            File directory=new File(context.getCacheDir(),"reportes");
            if(!directory.exists()&&!directory.mkdirs())throw new IOException("No se pudo preparar la carpeta de reportes.");
            File result=new File(directory,"Programa_integrado_"+date+".jpg");
            try(FileOutputStream stream=new FileOutputStream(result)){
                if(!bitmap.compress(Bitmap.CompressFormat.JPEG,92,stream))throw new IOException("No se pudo guardar la imagen.");
            }
            return result;
        }finally{bitmap.recycle();}
    }

    private void compose(Sheet sheet,String date,String time,Models.Meeting meeting,Models.MechanicalProgram mechanical,
                         Models.President president,List<Models.LifePart> treasures,List<Models.LifePart> christianLife,
                         Map<Long,Models.LifeProgramRow> lifeByPart,List<Models.Presentation> school,
                         Map<String,Models.ProgramRow> schoolByRoom){
        sheet.bar(longDate(date),48,color("title","#174E69"));
        sheet.keyValue("Hora de reunión",time);
        sheet.bar("PRESIDENTE",43,color("title","#174E69"));
        sheet.detail(president==null?"Pendiente":president.name);
        sheet.keyValue("Canción inicial",blank(mechanical==null?null:mechanical.openingSong));
        sheet.keyValue("Oración inicial",person(mechanical,Models.MechanicalRole.ORACION_INICIAL));

        sheet.bar("TESOROS DE LA BIBLIA",48,color("title","#174E69"));
        if(treasures.isEmpty())sheet.note("Sin partes registradas en esta sección.");
        for(Models.LifePart part:treasures)
            sheet.part(part.number,part.assignmentName,lifeDetail(part,lifeByPart.get(part.id)),part.topic,color("main","#00B889"));

        sheet.bar("SEAMOS MEJORES MAESTROS",48,color("title","#174E69"));
        drawSchoolRoom(sheet,"SALA PRINCIPAL",school,schoolByRoom,"PRINCIPAL",color("main","#00B889"));
        if(!meeting.visit)drawSchoolRoom(sheet,"SALA AUXILIAR",school,schoolByRoom,"AUXILIAR",color("aux","#EFA886"));
        sheet.keyValue("Canción intermedio",blank(mechanical==null?null:mechanical.middleSong));

        sheet.bar("NUESTRA VIDA CRISTIANA",48,color("title","#174E69"));
        if(christianLife.isEmpty()&&!meeting.visit)sheet.note("Sin partes registradas en esta sección.");
        for(Models.LifePart part:christianLife)if(!meeting.visit||!part.isBookStudy())
            sheet.part(part.number,part.assignmentName,lifeDetail(part,lifeByPart.get(part.id)),part.topic,color("main","#00B889"));
        if(meeting.visit)sheet.part(0,"Discurso de servicio del superintendente de circuito",
                blank(meeting.circuitOverseer),"Tema: "+blank(meeting.serviceTalkTopic)+" · Canción: "+blank(meeting.serviceSong),color("main","#00B889"));
        sheet.keyValue("Canción final",blank(mechanical==null?null:mechanical.closingSong));
        sheet.keyValue("Oración final",person(mechanical,Models.MechanicalRole.ORACION_FINAL));

        int mechanicalColor=color("source","#1638D3");
        sheet.bar("ACOMODADOR DE ENTRADA",41,mechanicalColor);
        sheet.detail(person(mechanical,Models.MechanicalRole.ENTRADA));
        sheet.bar("ACOMODADORES DE AUDITORIO",41,mechanicalColor);
        sheet.detail("1: "+person(mechanical,Models.MechanicalRole.AUDITORIO_1)+"     2: "+person(mechanical,Models.MechanicalRole.AUDITORIO_2));
        sheet.bar("MICRÓFONOS",41,mechanicalColor);
        sheet.detail("1: "+person(mechanical,Models.MechanicalRole.MICROFONO_1)+"     2: "+person(mechanical,Models.MechanicalRole.MICROFONO_2));
        sheet.bar("PLATAFORMA",41,mechanicalColor);
        sheet.detail("1: "+person(mechanical,Models.MechanicalRole.PLATAFORMA_1)+"     2: "+person(mechanical,Models.MechanicalRole.PLATAFORMA_2));
    }

    private void drawSchoolRoom(Sheet sheet,String heading,List<Models.Presentation> presentations,
                                Map<String,Models.ProgramRow> assigned,String room,int tint){
        sheet.bar(heading,42,tint);
        if(presentations.isEmpty()){sheet.note("Sin presentaciones registradas.");return;}
        for(Models.Presentation part:presentations){
            Models.ProgramRow row=assigned.get(part.id+"/"+room);
            String names=row==null||row.student==null?"Pendiente":
                    row.student.name+(row.helper==null?"":"  ↔  "+row.helper.name);
            sheet.part(part.number,part.assignmentName,names,"",tint);
        }
    }

    private String person(Models.MechanicalProgram program,Models.MechanicalRole role){
        Models.Student student=program==null?null:db.student(program.studentId(role));
        return student==null?"Pendiente":student.name;
    }
    private static String blank(String text){return text==null||text.trim().isEmpty()?"Pendiente":text.trim();}
    private static String lifeDetail(Models.LifePart part,Models.LifeProgramRow assigned){
        if(assigned==null||assigned.student==null)return "Pendiente";
        return assigned.student.name+(part.isBookStudy()&&assigned.reader!=null?" · Lector: "+assigned.reader.name:"");
    }
    private static String longDate(String value){
        LocalDate day=LocalDate.parse(value);
        return day.getDayOfWeek().getDisplayName(TextStyle.FULL,ES)+", "+day.getDayOfMonth()+" de "+
                day.getMonth().getDisplayName(TextStyle.FULL,ES)+" de "+day.getYear();
    }
    private int color(String key,String fallback){
        try{return Color.parseColor(colors.getString(key,fallback));}
        catch(Exception ignored){return Color.parseColor(fallback);}
    }
    private void fill(Canvas canvas,float x,float y,float width,float height,int shade){
        if(canvas==null)return;
        paint.setStyle(Paint.Style.FILL);paint.setColor(shade);canvas.drawRect(x,y,x+width,y+height,paint);
    }
    private void text(Canvas canvas,String value,float x,float baseline,float size,int shade,boolean bold){
        if(canvas==null)return;
        paint.setStyle(Paint.Style.FILL);paint.setColor(shade);
        paint.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);paint.setTextSize(size);
        canvas.drawText(value,x,baseline,paint);
    }
    private List<String> wrap(String value,float width,float size,boolean bold){
        paint.setTextSize(size);paint.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);
        List<String> lines=new ArrayList<>();String source=value==null?"":value.trim().replace('\n',' ');
        if(source.isEmpty()){lines.add("Pendiente");return lines;}
        StringBuilder current=new StringBuilder();
        for(String original:source.split("\\s+")){
            String word=original,candidate=current.length()==0?word:current+" "+word;
            if(paint.measureText(candidate)<=width){current.setLength(0);current.append(candidate);continue;}
            if(current.length()>0){lines.add(current.toString());current.setLength(0);}
            while(paint.measureText(word)>width){
                int length=Math.max(1,Math.min(word.length(),paint.breakText(word,true,width,null)));
                lines.add(word.substring(0,length));word=word.substring(length);
            }
            if(!word.isEmpty())current.append(word);
        }
        if(current.length()>0)lines.add(current.toString());
        return lines;
    }

    private final class Sheet {
        private final Canvas canvas;
        private float y=24;
        Sheet(Canvas canvas){this.canvas=canvas;}
        void bar(String value,float minimumHeight,int shade){
            List<String> lines=wrap(value,RIGHT-LEFT-30,26,true);
            float height=Math.max(minimumHeight,lines.size()*31+10);
            fill(canvas,LEFT,y,RIGHT-LEFT,height,shade);
            int foreground=Color.luminance(shade)>.53?Color.BLACK:Color.WHITE;
            float baseline=y+height/2-(lines.size()-1)*15+9;
            for(String line:lines){text(canvas,line,LEFT+15,baseline,26,foreground,true);baseline+=31;}
            y+=height;
        }
        void detail(String value){simple(value,Color.rgb(238,241,244));}
        void note(String value){simple(value,Color.rgb(249,249,249));}
        private void simple(String value,int background){
            List<String> lines=wrap(value,RIGHT-LEFT-28,23,false);
            float height=Math.max(41,lines.size()*29+12);
            fill(canvas,LEFT,y,RIGHT-LEFT,height,background);
            for(int i=0;i<lines.size();i++)text(canvas,lines.get(i),LEFT+12,y+29+i*29,23,Color.DKGRAY,false);
            y+=height;
        }
        void keyValue(String label,String value){
            float labelWidth=272;
            List<String> lines=wrap(value,RIGHT-LEFT-labelWidth-25,23,false);
            float height=Math.max(43,lines.size()*29+12);
            int shade=color("title","#174E69");
            fill(canvas,LEFT,y,labelWidth,height,shade);
            fill(canvas,LEFT+labelWidth,y,RIGHT-LEFT-labelWidth,height,Color.rgb(238,241,244));
            text(canvas,label+":",LEFT+11,y+30,22,Color.luminance(shade)>.53?Color.BLACK:Color.WHITE,true);
            for(int i=0;i<lines.size();i++)text(canvas,lines.get(i),LEFT+labelWidth+12,y+29+i*29,23,Color.DKGRAY,false);
            y+=height;
        }
        void part(int number,String title,String people,String topic,int tint){
            float content=RIGHT-LEFT-85;
            List<String> names=wrap(people,content,23,false);
            List<String> heading=wrap(title,content,25,true);
            List<String> subject=topic==null||topic.trim().isEmpty()?new ArrayList<>():wrap(topic,content,21,false);
            float height=Math.max(72,14+heading.size()*31+names.size()*29+subject.size()*26);
            fill(canvas,LEFT,y,64,height,tint);
            fill(canvas,LEFT+64,y,RIGHT-LEFT-64,height,Color.rgb(241,244,246));
            int foreground=Color.luminance(tint)>.53?Color.BLACK:Color.WHITE;
            if(number>0)text(canvas,String.valueOf(number),LEFT+20,y+39,29,foreground,true);
            float baseline=y+30;
            for(String line:heading){text(canvas,line,LEFT+76,baseline,25,Color.rgb(19,64,84),true);baseline+=31;}
            for(String line:names){text(canvas,line,LEFT+76,baseline,23,Color.DKGRAY,false);baseline+=29;}
            for(String line:subject){text(canvas,line,LEFT+76,baseline,21,Color.DKGRAY,false);baseline+=26;}
            y+=height;
        }
    }
}
