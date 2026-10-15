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
import java.util.List;
import java.util.Locale;

/** Genera la imagen de Reunión Pública y Atalaya con sus asignaciones mecánicas. */
final class PublicWeeklyReportManager {
    private static final Locale ES=new Locale("es","MX");
    private static final int WIDTH=1200,LEFT=24,RIGHT=1176;
    private final Context context;
    private final MinisterioDb db;
    private final SharedPreferences colors;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);

    PublicWeeklyReportManager(Context context,MinisterioDb db){
        this.context=context;this.db=db;
        colors=context.getSharedPreferences("palette",Context.MODE_PRIVATE);
    }

    File weeklyJpg(String date,String meetingTime)throws IOException {
        LocalDate.parse(date);
        Models.PublicMeeting meeting=db.publicMeeting(date);
        if(meeting==null)throw new IllegalArgumentException("Configure primero la Reunión Pública y Atalaya de esta fecha.");
        if(meeting.suspended)throw new IllegalArgumentException("La reunión fue cancelada por asamblea.");
        Models.MechanicalProgram mechanical=db.mechanicalProgram(date,"WEEKEND");
        String time=meetingTime==null||meetingTime.trim().isEmpty()?"10:00 a. m.":meetingTime.trim();

        Sheet measure=new Sheet(null);
        compose(measure,date,time,meeting,mechanical);
        int height=(int)Math.ceil(measure.y+24);
        if(height>9000)throw new IllegalArgumentException("El programa contiene demasiado texto para una sola imagen.");

        Bitmap bitmap;
        try{bitmap=Bitmap.createBitmap(WIDTH,height,Bitmap.Config.ARGB_8888);}
        catch(OutOfMemoryError error){throw new IOException("No hay suficiente memoria para generar la imagen.",error);}
        try{
            Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.WHITE);
            compose(new Sheet(canvas),date,time,meeting,mechanical);
            File directory=new File(context.getCacheDir(),"reportes");
            if(!directory.exists()&&!directory.mkdirs())throw new IOException("No se pudo preparar la carpeta de reportes.");
            File result=new File(directory,"Programa_fin_de_semana_"+date+".jpg");
            try(FileOutputStream stream=new FileOutputStream(result)){
                if(!bitmap.compress(Bitmap.CompressFormat.JPEG,92,stream))
                    throw new IOException("No se pudo guardar la imagen.");
            }
            return result;
        }finally{bitmap.recycle();}
    }

    private void compose(Sheet sheet,String date,String time,Models.PublicMeeting meeting,Models.MechanicalProgram mechanical){
        int heading=color("title","#174E69"),publicColor=color("main","#00B889"),watchtowerColor=color("aux","#EFA886");
        sheet.bar("REUNIÓN PÚBLICA Y ESTUDIO DE LA ATALAYA",54,heading);
        sheet.bar(longDate(date),46,heading);
        sheet.keyValue("Hora de reunión",time);
        sheet.keyValue("Presidente",studentName(meeting.presidentId));
        sheet.keyValue("Canción inicial",blank(mechanical==null?null:mechanical.openingSong));
        sheet.keyValue("Oración inicial",mechanicalName(mechanical,Models.MechanicalRole.ORACION_INICIAL));

        sheet.bar("DISCURSO PÚBLICO",50,publicColor);
        sheet.keyValue(meeting.visit?"Superintendente de circuito":"Orador",speakerName(meeting));
        String visitorInformation=speakerInformation(meeting);
        if(!visitorInformation.isEmpty())sheet.detail(visitorInformation);
        sheet.keyValue("Número de bosquejo",blank(meeting.outlineNumber));
        sheet.keyValue("Título del bosquejo",blank(meeting.outlineTitle));

        sheet.keyValue("Canción intermedio",blank(mechanical==null?null:mechanical.middleSong));
        sheet.bar("ESTUDIO DE LA ATALAYA",50,watchtowerColor);
        sheet.keyValue("Conductor",studentName(meeting.conductorId));
        sheet.keyValue("Tema",blank(meeting.watchtowerTopic));
        sheet.keyValue("Lector",meeting.visit?"Cancelado durante la visita":studentName(meeting.readerId));

        if(meeting.visit){
            sheet.bar("DISCURSO FINAL",50,publicColor);
            sheet.keyValue("Orador",speakerName(meeting));
            sheet.keyValue("Título",blank(meeting.finalTalkTitle));
        }

        sheet.keyValue("Canción final",blank(mechanical==null?null:mechanical.closingSong));
        sheet.keyValue("Oración final",mechanicalName(mechanical,Models.MechanicalRole.ORACION_FINAL));

        int mechanicalColor=color("source","#1638D3");
        sheet.bar("ACOMODADOR DE ENTRADA",42,mechanicalColor);
        sheet.detail(mechanicalName(mechanical,Models.MechanicalRole.ENTRADA));
        sheet.bar("ACOMODADORES DE AUDITORIO",42,mechanicalColor);
        sheet.detail("1: "+mechanicalName(mechanical,Models.MechanicalRole.AUDITORIO_1)+
                "     2: "+mechanicalName(mechanical,Models.MechanicalRole.AUDITORIO_2));
        sheet.bar("MICRÓFONOS",42,mechanicalColor);
        sheet.detail("1: "+mechanicalName(mechanical,Models.MechanicalRole.MICROFONO_1)+
                "     2: "+mechanicalName(mechanical,Models.MechanicalRole.MICROFONO_2));
        sheet.bar("PLATAFORMA",42,mechanicalColor);
        sheet.detail("1: "+mechanicalName(mechanical,Models.MechanicalRole.PLATAFORMA_1)+
                "     2: "+mechanicalName(mechanical,Models.MechanicalRole.PLATAFORMA_2));
    }

    private String speakerName(Models.PublicMeeting meeting){
        if("VISITOR".equals(meeting.speakerKind)){
            Models.VisitingSpeaker speaker=db.visitingSpeaker(meeting.visitorSpeakerId);
            return speaker==null?"Pendiente":speaker.name;
        }
        return studentName(meeting.localSpeakerId);
    }

    private String speakerInformation(Models.PublicMeeting meeting){
        if(!"VISITOR".equals(meeting.speakerKind))return "";
        Models.VisitingSpeaker speaker=db.visitingSpeaker(meeting.visitorSpeakerId);
        if(speaker==null)return "";
        List<String> details=new ArrayList<>();
        if(!speaker.congregation.trim().isEmpty())details.add("Congregación: "+speaker.congregation.trim());
        if(!speaker.locality.trim().isEmpty())details.add("Localidad: "+speaker.locality.trim());
        if(!speaker.appointment.trim().isEmpty())details.add("Nombramiento: "+speaker.appointment.trim());
        return String.join(" · ",details);
    }

    private String studentName(long id){
        Models.Student student=db.student(id);return student==null?"Pendiente":student.name;
    }

    private String mechanicalName(Models.MechanicalProgram program,Models.MechanicalRole role){
        return program==null?"Pendiente":studentName(program.studentId(role));
    }

    private static String blank(String value){
        return value==null||value.trim().isEmpty()?"Pendiente":value.trim();
    }

    private static String longDate(String value){
        LocalDate date=LocalDate.parse(value);
        return date.getDayOfWeek().getDisplayName(TextStyle.FULL,ES)+", "+date.getDayOfMonth()+" de "+
                date.getMonth().getDisplayName(TextStyle.FULL,ES)+" de "+date.getYear();
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

        private void simple(String value,int background){
            List<String> lines=wrap(value,RIGHT-LEFT-28,23,false);
            float height=Math.max(43,lines.size()*29+12);
            fill(canvas,LEFT,y,RIGHT-LEFT,height,background);
            for(int i=0;i<lines.size();i++)text(canvas,lines.get(i),LEFT+12,y+29+i*29,23,Color.DKGRAY,false);
            y+=height;
        }

        void keyValue(String label,String value){
            float labelWidth=300;
            List<String> lines=wrap(value,RIGHT-LEFT-labelWidth-25,23,false);
            float height=Math.max(43,lines.size()*29+12);
            int shade=color("title","#174E69");
            fill(canvas,LEFT,y,labelWidth,height,shade);
            fill(canvas,LEFT+labelWidth,y,RIGHT-LEFT-labelWidth,height,Color.rgb(238,241,244));
            int foreground=Color.luminance(shade)>.53?Color.BLACK:Color.WHITE;
            text(canvas,label+":",LEFT+11,y+30,22,foreground,true);
            for(int i=0;i<lines.size();i++)text(canvas,lines.get(i),LEFT+labelWidth+12,y+29+i*29,23,Color.DKGRAY,false);
            y+=height;
        }
    }
}
