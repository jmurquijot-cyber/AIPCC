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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Generates readable JPGs without requiring a PDF viewer or external templates. */
final class PreachingReportManager {
    private final Context context;
    private final PreachingCampaign repository;
    private final Map<Long,Models.Student> students=new LinkedHashMap<>();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int banner,text;
    private static final Locale SPANISH=new Locale("es","MX");

    PreachingReportManager(Context context,MinisterioDb db){this.context=context;repository=new PreachingCampaign(db);for(Models.Student s:db.students(true))students.put(s.id,s);
        SharedPreferences palette=context.getSharedPreferences("palette",Context.MODE_PRIVATE);
        banner=color(palette.getString("title","#174866"),Color.rgb(23,72,102));text=color(palette.getString("text","#1F2937"),Color.rgb(31,41,55));
    }
    private static int color(String code,int fallback){try{return Color.parseColor(code);}catch(Exception ignored){return fallback;}}
    private String name(long id){Models.Student s=students.get(id);return id==0?"PENDIENTE":s==null?"Alumno #"+id:s.name;}
    private String person(long id){Models.Student s=students.get(id);return id==0?"Por asignar":s==null?name(id):s.name+(s.phone.isEmpty()?"":" · "+s.phone);}
    private File save(Bitmap bitmap,String filename)throws Exception{File directory=new File(context.getCacheDir(),"reportes");if(!directory.exists()&&!directory.mkdirs())throw new IllegalStateException("No se pudo crear la carpeta de reportes.");File file=new File(directory,filename);try(FileOutputStream output=new FileOutputStream(file)){if(!bitmap.compress(Bitmap.CompressFormat.JPEG,94,output))throw new IllegalStateException("No se pudo guardar la imagen.");}finally{bitmap.recycle();}return file;}
    private void box(Canvas canvas,float x,float y,float width,float height,int shade){paint.setColor(shade);paint.setStyle(Paint.Style.FILL);canvas.drawRect(x,y,x+width,y+height,paint);}
    private void border(Canvas canvas,float x,float y,float width,float height){paint.setColor(Color.rgb(22,67,191));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);canvas.drawRect(x,y,x+width,y+height,paint);paint.setStyle(Paint.Style.FILL);}
    private void write(Canvas canvas,String value,float x,float baseline,float maxWidth,int size,int shade,boolean bold){paint.setColor(shade);paint.setTypeface(bold?Typeface.create("sans-serif",Typeface.BOLD):Typeface.create("sans-serif",Typeface.NORMAL));paint.setTextSize(size);
        String line=value==null?"":value;while(line.length()>1&&paint.measureText(line)>maxWidth)line=line.substring(0,line.length()-1);if(!line.equals(value)){while(line.length()>1&&paint.measureText(line+"…")>maxWidth)line=line.substring(0,line.length()-1);line+="…";}canvas.drawText(line,x,baseline,paint);
    }
    private static String dayLabel(LocalDate day){String label=day.getDayOfWeek().getDisplayName(TextStyle.FULL,SPANISH);return label.substring(0,1).toUpperCase(SPANISH)+label.substring(1)+" "+day.getDayOfMonth();}

    File weeklyJpg(PreachingCampaign.Campaign c,LocalDate date)throws Exception{
        LocalDate monday=date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<LocalDate> days=new ArrayList<>();for(int i=0;i<7;i++){LocalDate day=monday.plusDays(i);if(!day.isBefore(LocalDate.parse(c.startDate))&&!day.isAfter(LocalDate.parse(c.endDate)))days.add(day);}
        if(days.isEmpty())throw new IllegalArgumentException("La semana está fuera de la campaña.");
        int width=1700,top=235,heading=86,row=112,boothHeader=53,tail=36,height=top+c.exhibitors*(boothHeader+heading+c.slots()*row+24)+tail;
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.RGB_565);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.WHITE);
        box(canvas,0,0,width,93,Color.rgb(78,147,209));write(canvas,"PREDICACIÓN PÚBLICA · "+date.getMonth().getDisplayName(TextStyle.FULL,SPANISH).toUpperCase(SPANISH)+" "+date.getYear(),35,59,width-70,33,Color.BLACK,true);
        write(canvas,"MÓDULO: "+(c.module.isEmpty()?"—":c.module),30,122,width/2-50,25,text,true);
        write(canvas,"CONGREGACIÓN: "+(c.congregation.isEmpty()?"—":c.congregation),30,160,width/2-50,25,text,true);
        write(canvas,"SEMANA: "+monday+" · "+monday.plusDays(6),30,202,width/2-50,23,text,false);
        Map<Long,String> captains=new LinkedHashMap<>();Map<String,PreachingCampaign.DayResponsibilities> byDay=new LinkedHashMap<>();for(LocalDate day:days){PreachingCampaign.DayResponsibilities duties=repository.responsibilities(c,day.toString());byDay.put(day.toString(),duties);if(duties.captain!=0)captains.put(duties.captain,person(duties.captain));}
        StringBuilder captainLine=new StringBuilder("CAPITANES: ");for(String name:captains.values()){if(captainLine.length()>11)captainLine.append(" · ");captainLine.append(name);}if(captains.isEmpty())captainLine.append("Pendientes");write(canvas,captainLine.toString(),width/2,142,width/2-40,22,text,true);
        int left=192,dayWidth=(width-left)/days.size(),y=top;
        for(int booth=1;booth<=c.exhibitors;booth++){
            box(canvas,0,y,width,boothHeader,banner);write(canvas,"EXHIBIDOR "+booth,30,y+36,width-60,27,Color.WHITE,true);y+=boothHeader;
            box(canvas,0,y,left,heading,Color.rgb(244,176,132));write(canvas,"HORARIO",14,y+39,left-25,22,text,true);border(canvas,0,y,left,heading);
            for(int i=0;i<days.size();i++){int x=left+i*dayWidth;box(canvas,x,y,dayWidth,heading,Color.rgb(105,189,234));write(canvas,dayLabel(days.get(i)),x+7,y+38,dayWidth-14,22,text,true);PreachingCampaign.DayResponsibilities duties=byDay.get(days.get(i).toString());write(canvas,"Cap.: "+name(duties.captain),x+7,y+71,dayWidth-14,18,text,false);border(canvas,x,y,dayWidth,heading);}y+=heading;
            for(int slot=0;slot<c.slots();slot++){
                box(canvas,0,y,left,row,Color.rgb(204,243,212));write(canvas,c.slotLabel(slot),12,y+63,left-20,22,text,true);border(canvas,0,y,left,row);
                for(int i=0;i<days.size();i++){int x=left+i*dayWidth;box(canvas,x,y,dayWidth,row,Color.WHITE);border(canvas,x,y,dayWidth,row);
                    PreachingCampaign.Team team=repository.team(c,days.get(i).toString(),slot,booth);
                    long dayCaptain=byDay.get(days.get(i).toString()).captain;
                    for(int n=0;n<4;n++){String label=team.publishers[n]==0?"—":name(team.publishers[n])+(dayCaptain==team.publishers[n]&&byDay.get(days.get(i).toString()).finalized?" (cap. día)":"");write(canvas,label,x+7,y+25+n*25,dayWidth-15,18,text,false);}
                }y+=row;
            }y+=24;
        }
        write(canvas,"Cada módulo: hasta cuatro publicadores; con dos ya es viable · Un capitán por día",25,height-12,width-50,19,text,false);
        return save(bitmap,"Predicacion-semana-"+c.id+"-"+monday+".jpg");
    }
    File shiftJpg(PreachingCampaign.Campaign c,String day,int slot,int booth)throws Exception{
        PreachingCampaign.validateDay(c,day);PreachingCampaign.Team team=repository.team(c,day,slot,booth);PreachingCampaign.DayResponsibilities duties=repository.responsibilities(c,day);
        Bitmap bitmap=Bitmap.createBitmap(1080,845,Bitmap.Config.RGB_565);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.WHITE);
        box(canvas,0,0,1080,125,banner);write(canvas,"PREDICACIÓN PÚBLICA",45,82,1000,43,Color.WHITE,true);
        write(canvas,"CONGREGACIÓN: "+(c.congregation.isEmpty()?"—":c.congregation),43,183,995,29,text,true);
        write(canvas,"FECHA: "+dayLabel(LocalDate.parse(day))+" de "+LocalDate.parse(day).getMonth().getDisplayName(TextStyle.FULL,SPANISH)+" de "+LocalDate.parse(day).getYear(),43,239,995,29,text,true);
        write(canvas,"EXHIBIDOR "+booth+" · TURNO "+c.slotLabel(slot),43,295,995,30,text,true);
        box(canvas,30,328,1020,60,Color.rgb(105,189,234));write(canvas,"PUBLICADORES DEL TURNO",46,369,965,28,text,true);
        for(int i=0;i<4;i++){int y=434+i*60;write(canvas,(i+1)+". "+person(team.publishers[i]),46,y,985,29,text,false);}
        box(canvas,30,657,1020,2,Color.rgb(22,67,191));write(canvas,"CAPITÁN DEL DÍA: "+person(duties.captain),46,705,985,25,text,true);
        write(canvas,"TRANSPORTE DE MÓDULOS: "+person(duties.transport),46,752,985,23,text,false);
        write(canvas,"(inicio y final de la jornada)",46,801,985,20,text,false);
        return save(bitmap,"Predicacion-turno-"+c.id+"-"+day+"-"+(slot+1)+"-"+booth+".jpg");
    }
}
