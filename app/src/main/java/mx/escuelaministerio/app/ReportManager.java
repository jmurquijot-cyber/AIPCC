package mx.escuelaministerio.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ReportManager {
    private static final Locale ES = new Locale("es", "MX");
    private final Context context;
    private final MinisterioDb db;
    private final SharedPreferences colors;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    ReportManager(Context context, MinisterioDb db) {
        this.context = context; this.db = db;
        colors = context.getSharedPreferences("palette", Context.MODE_PRIVATE);
    }

    private int color(String key, String fallback) {
        try { return Color.parseColor(colors.getString(key, fallback)); }
        catch (Exception ignored) { return Color.parseColor(fallback); }
    }

    private File output(String name) {
        File dir = new File(context.getCacheDir(), "reportes");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, name.replaceAll("[^A-Za-z0-9._-]", "_"));
    }

    File monthlyPdf(String month) throws IOException {
        List<Models.Presentation> presentations = db.presentations(month);
        List<Models.ProgramRow> assigned = db.program(month);
        Map<Long, Map<String, Models.ProgramRow>> movementByPresentation = new LinkedHashMap<>();
        for (Models.ProgramRow row : assigned) movementByPresentation.computeIfAbsent(row.presentation.id, k -> new LinkedHashMap<>()).put(row.movement.room, row);
        Map<String, List<Models.Presentation>> byDate = new LinkedHashMap<>();
        for (Models.Presentation p : presentations) byDate.computeIfAbsent(p.date, k -> new ArrayList<>()).add(p);
        for (Models.Meeting meeting : db.meetings(month)) byDate.computeIfAbsent(meeting.date, k -> new ArrayList<>());

        PdfDocument pdf = new PdfDocument();
        PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(792, 612, 1).create());
        Canvas c = page.getCanvas(); c.drawColor(Color.WHITE);
        float y = 0;
        box(c, 0, y, 792, 30, color("title", "#00B050"), Color.BLACK);
        text(c, "PROGRAMA DE ACTIVIDADES PARA LA REUNIÓN VIDA Y MINISTERIO CRISTIANOS", 396, y + 20, 12, Color.WHITE, true, Paint.Align.CENTER);
        y += 30;
        int contentRows = 0;
        for (Map.Entry<String,List<Models.Presentation>> entry : byDate.entrySet())
            contentRows += db.meeting(entry.getKey()).suspended ? 1 : entry.getValue().size();
        float sectionH = 18f;
        float available = 612f - y - 2f;
        float rowH = contentRows == 0 ? 22f : Math.min(24f,(available - byDate.size() * sectionH * 2f) / contentRows);
        if (rowH < 14f) {
            float unit = available / Math.max(1,contentRows + byDate.size() * 2f);
            sectionH = Math.max(12f,unit); rowH = Math.max(10f,unit);
        }
        for (Map.Entry<String, List<Models.Presentation>> entry : byDate.entrySet()) {
            String date = entry.getKey(); Models.Meeting meeting = db.meeting(date); Models.President president = db.president(meeting.presidentId);
            box(c, 0, y, 792, sectionH, color("date", "#0D6EFD"), Color.BLACK);
            String dateTitle = humanDate(date) + (president == null ? "" : "  ·  Presidente: " + president.name);
            fitText(c,dateTitle,396,y+sectionH*.72f,780,Math.min(10,sectionH*.58f),Color.WHITE,true); y += sectionH;
            drawMonthlyHeaders(c, y, sectionH); y += sectionH;
            if (meeting.suspended) {
                box(c, 0, y, 792, rowH, color("title", "#00B050"), Color.BLACK);
                text(c, "REUNIÓN SUSPENDIDA / ASAMBLEA", 396, y + rowH * .72f, Math.min(12, rowH * .65f), Color.WHITE, true, Paint.Align.CENTER); y += rowH;
                continue;
            }
            for (Models.Presentation p : entry.getValue()) {
                Map<String, Models.ProgramRow> rooms = movementByPresentation.get(p.id);
                Models.ProgramRow main = rooms == null ? null : rooms.get("PRINCIPAL");
                Models.ProgramRow auxiliary = rooms == null ? null : rooms.get("AUXILIAR");
                drawProgramRow(c, y, rowH, p, main, meeting.visit ? null : auxiliary); y += rowH;
            }
        }
        pdf.finishPage(page);
        File file = output("Programa_" + month + ".pdf");
        try (FileOutputStream out = new FileOutputStream(file)) { pdf.writeTo(out); }
        pdf.close(); return file;
    }

    File weeklyRoomPdf(String date,String room) throws IOException {
        LocalDate.parse(date);String month=date.substring(0,7);List<Models.Presentation> presentations=db.presentations(month);presentations.removeIf(p->!date.equals(p.date));
        if(presentations.isEmpty())throw new IllegalArgumentException("No hay presentaciones capturadas para la semana seleccionada.");Models.Meeting meeting=db.meeting(date);if(meeting.visit&&"AUXILIAR".equals(room))throw new IllegalArgumentException("Durante la visita no existe programa para la Sala Auxiliar.");
        Map<Long,Models.ProgramRow> assigned=new LinkedHashMap<>();for(Models.ProgramRow row:db.program(month))if(date.equals(row.presentation.date)&&room.equals(row.movement.room))assigned.put(row.presentation.id,row);
        PdfDocument pdf=new PdfDocument();PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(792,612,1).create());Canvas c=page.getCanvas();c.drawColor(Color.WHITE);float left=18f,right=774f,y=18f;
        box(c,left,y,right-left,36,color("title","#00B050"),Color.BLACK);text(c,"PROGRAMA SEMANAL - "+("PRINCIPAL".equals(room)?"SALA PRINCIPAL":"SALA AUXILIAR"),(left+right)/2,y+24,15,Color.WHITE,true,Paint.Align.CENTER);y+=36;
        Models.President president=db.president(meeting.presidentId);box(c,left,y,right-left,30,color("date","#0D6EFD"),Color.BLACK);fitText(c,humanDate(date)+(president==null?"":"  ·  Presidente: "+president.name),(left+right)/2,y+20,right-left-12,11,Color.WHITE,true);y+=36;
        float[] x={left,left+54,left+187,left+347,left+497,left+686,right};float headerH=30;String[] headers={"NÚM. / TIEMPO","ASIGNACIÓN","ESTUDIANTE","AYUDANTE","FUENTE / TEMA","LECCIÓN"};String[] keys={"assignment","assignment","main","aux","source","assignment"};String[] fallbacks={"#BFBFBF","#BFBFBF","#ED7D31","#FFC000","#955FEF","#BFBFBF"};for(int i=0;i<headers.length;i++){box(c,x[i],y,x[i+1]-x[i],headerH,color(keys[i],fallbacks[i]),Color.BLACK);drawWrappedCell(c,headers[i],x[i],y,x[i+1]-x[i],headerH,7.8f,i==4?Color.WHITE:Color.BLACK,true,2);}y+=headerH;
        float rowH=Math.min(88f,(594f-y)/Math.max(1,presentations.size()));for(Models.Presentation p:presentations){Models.ProgramRow row=assigned.get(p.id);String helper=p.isIndividualMaleAssignment()?"NO APLICA":row==null||row.helper==null?"PENDIENTE":row.helper.name.toUpperCase(ES);String source=(p.source.isEmpty()?p.material:p.source)+(p.topic.isEmpty()?"":" · "+p.topic);for(int i=0;i<x.length-1;i++)box(c,x[i],y,x[i+1]-x[i],rowH,Color.rgb(248,248,248),Color.BLACK);drawWrappedCell(c,p.number+" · "+p.minutes+" min.",x[0],y,x[1]-x[0],rowH,7.5f,Color.BLACK,true,3);drawWrappedCell(c,p.assignmentName,x[1],y,x[2]-x[1],rowH,8f,Color.BLACK,true,4);drawWrappedCell(c,row==null||row.student==null?"PENDIENTE":row.student.name.toUpperCase(ES),x[2],y,x[3]-x[2],rowH,8f,Color.BLACK,true,4);drawWrappedCell(c,helper,x[3],y,x[4]-x[3],rowH,8f,Color.BLACK,true,4);drawWrappedCell(c,source,x[4],y,x[5]-x[4],rowH,7.3f,Color.BLACK,false,6);drawWrappedCell(c,p.lesson.isEmpty()?"—":p.lesson,x[5],y,x[6]-x[5],rowH,7.5f,Color.BLACK,false,4);y+=rowH;}
        pdf.finishPage(page);File file=output("Programa_semanal_"+date+"_"+room+".pdf");try(FileOutputStream out=new FileOutputStream(file)){pdf.writeTo(out);}pdf.close();return file;
    }

    private void drawMonthlyHeaders(Canvas c, float y, float h) {
        float[] x = {0, 38, 136, 342, 548, 792};
        box(c,x[0],y,x[2]-x[0],h,color("assignment","#BFBFBF"),Color.BLACK);
        box(c,x[2],y,x[3]-x[2],h,color("main","#ED7D31"),Color.BLACK);
        box(c,x[3],y,x[4]-x[3],h,color("aux","#FFC000"),Color.BLACK);
        box(c,x[4],y,x[5]-x[4],h,color("source","#955FEF"),Color.BLACK);
        text(c,"ASIGNACIÓN",68,y+h*.7f,Math.min(9,h*.55f),Color.BLACK,true,Paint.Align.CENTER);
        text(c,"SALA PRINCIPAL",239,y+h*.7f,Math.min(9,h*.55f),Color.BLACK,true,Paint.Align.CENTER);
        text(c,"SALA AUXILIAR",445,y+h*.7f,Math.min(9,h*.55f),Color.BLACK,true,Paint.Align.CENTER);
        text(c,"FUENTE DE INFORMACIÓN",670,y+h*.7f,Math.min(9,h*.55f),Color.WHITE,true,Paint.Align.CENTER);
    }

    private void drawProgramRow(Canvas c,float y,float h,Models.Presentation p,Models.ProgramRow main,Models.ProgramRow aux){
        float[] x={0,38,136,342,548,792};
        for(int i=0;i<x.length-1;i++)box(c,x[i],y,x[i+1]-x[i],h,Color.rgb(248,248,248),Color.BLACK);
        drawWrappedCell(c,"("+p.minutes+" min.)",x[0],y,x[1]-x[0],h,Math.max(5.2f,Math.min(7f,h*.34f)),Color.BLACK,false,2);
        drawWrappedCell(c,p.assignmentName,x[1],y,x[2]-x[1],h,Math.max(5.3f,Math.min(7.5f,h*.34f)),Color.BLACK,false,2);
        drawWrappedCell(c,names(main),x[2],y,x[3]-x[2],h,Math.max(5.2f,Math.min(7.2f,h*.32f)),Color.BLACK,true,2);
        drawWrappedCell(c,names(aux),x[3],y,x[4]-x[3],h,Math.max(5.2f,Math.min(7.2f,h*.32f)),Color.BLACK,true,2);
        String source=(p.source.isEmpty()?p.material:p.source)+(p.topic.isEmpty()?"":" · "+p.topic)+(p.lesson.isEmpty()?"":" · Lección: "+p.lesson);
        drawWrappedCell(c,source,x[4],y,x[5]-x[4],h,Math.max(5.0f,Math.min(6.5f,h*.29f)),Color.BLACK,false,3);
    }

    private String names(Models.ProgramRow row){
        if(row==null||row.student==null)return "PENDIENTE";
        return row.student.name.toUpperCase(ES)+(row.helper==null?"":"  ↔  "+row.helper.name.toUpperCase(ES));
    }

    File assignmentPdf(String month) throws IOException {
        List<Models.ProgramRow> rows=db.program(month); PdfDocument pdf=new PdfDocument(); int pageNumber=0;
        if(rows.isEmpty()) rows.add(null);
        for(int offset=0;offset<rows.size();offset+=4){
            pageNumber++;PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(612,792,pageNumber).create());Canvas c=page.getCanvas();c.drawColor(Color.WHITE);
            paint.setColor(Color.GRAY);paint.setStrokeWidth(.8f);c.drawLine(306,0,306,792,paint);c.drawLine(0,396,612,396,paint);
            for(int i=0;i<4;i++){int index=offset+i;float left=(i%2)*306+16,top=(i/2)*396+18;drawCard(c,left,top,274,360,index<rows.size()?rows.get(index):null,1f);}
            pdf.finishPage(page);
        }
        File file=output("Asignaciones_"+month+".pdf");try(FileOutputStream out=new FileOutputStream(file)){pdf.writeTo(out);}pdf.close();return file;
    }

    File assignmentJpg(Models.ProgramRow row) throws IOException {
        Bitmap bitmap=Bitmap.createBitmap(1000,1400,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(bitmap);c.drawColor(Color.WHITE);drawCard(c,35,35,930,1330,row,3.35f);
        File file=output("Asignacion_"+row.presentation.date+"_"+row.student.name+".jpg");try(FileOutputStream out=new FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.JPEG,94,out);}bitmap.recycle();return file;
    }

    private void drawCard(Canvas c,float left,float top,float width,float height,Models.ProgramRow row,float scale){
        paint.setStyle(Paint.Style.STROKE);paint.setColor(Color.GRAY);paint.setStrokeWidth(scale*.7f);c.drawRect(left,top,left+width,top+height,paint);paint.setStyle(Paint.Style.FILL);
        float x=left+11*scale,y=top+19*scale;
        text(c,"ASIGNACIÓN PARA LA REUNIÓN",x,y,11*scale,Color.DKGRAY,true,Paint.Align.LEFT);y+=13*scale;
        text(c,"VIDA Y MINISTERIO CRISTIANOS",x,y,11*scale,Color.DKGRAY,true,Paint.Align.LEFT);y+=19*scale;
        String student=row==null?"":row.student.name;String helper=row==null||row.helper==null?"":row.helper.name;
        field(c,"Nombre:",student,x,y,width-22*scale,scale);y+=18*scale;
        field(c,"Ayudante:",helper,x,y,width-22*scale,scale);y+=18*scale;
        field(c,"Fecha:",row==null?"":humanDate(row.presentation.date),x,y,width-22*scale,scale);y+=18*scale;
        field(c,"Intervención núm.:",row==null?"":row.presentation.number+". "+row.presentation.assignmentName+" ("+row.presentation.minutes+" min.)",x,y,width-22*scale,scale);y+=15*scale;
        if(row!=null){
            String info=(row.presentation.source.isEmpty()?row.presentation.material:row.presentation.source);
            if(!row.presentation.topic.isEmpty()) info+=" · "+row.presentation.topic;
            fitText(c,info,x+(width-22*scale)/2,y,(width-22*scale),6.3f*scale,Color.BLACK,false);y+=8*scale;
            fitText(c,"Lección: "+(row.presentation.lesson.isEmpty()?"—":row.presentation.lesson),x+(width-22*scale)/2,y,(width-22*scale),6.3f*scale,Color.BLACK,true);y+=22*scale;
        } else y+=30*scale;
        text(c,"Se presentará en:",x,y,8.2f*scale,Color.DKGRAY,true,Paint.Align.LEFT);y+=15*scale;
        checkbox(c,x+8*scale,y,"Sala principal",row!=null&&"PRINCIPAL".equals(row.movement.room),scale);y+=14*scale;
        checkbox(c,x+8*scale,y,"Sala auxiliar núm. 1",row!=null&&"AUXILIAR".equals(row.movement.room),scale);y+=14*scale;
        checkbox(c,x+8*scale,y,"Sala auxiliar núm. 2",false,scale);
        float noteY=top+height-70*scale;
        text(c,"Nota al estudiante:",x,noteY,7.3f*scale,Color.DKGRAY,true,Paint.Align.LEFT);
        drawWrapped(c,"En la Guía de actividades encontrará la información que necesita para su intervención. Repase también las indicaciones que se describen en las Instrucciones para la reunión Vida y Ministerio Cristianos (S-38).",x,noteY+9*scale,width-22*scale,7.1f*scale,Color.DKGRAY,6);
        text(c,"S-89-S   11/23",x,top+height-7*scale,5.8f*scale,Color.GRAY,false,Paint.Align.LEFT);
        text(c,"Impreso en México",left+width-9*scale,top+height-7*scale,5.8f*scale,Color.GRAY,false,Paint.Align.RIGHT);
    }

    private void field(Canvas c,String label,String value,float x,float y,float width,float scale){
        text(c,label,x,y,8.4f*scale,Color.DKGRAY,true,Paint.Align.LEFT);float labelW=paint.measureText(label)+3*scale;
        paint.setColor(Color.LTGRAY);paint.setStrokeWidth(.4f*scale);c.drawLine(x+labelW,y+2*scale,x+width,y+2*scale,paint);
        fitText(c,value,x+labelW+(width-labelW)/2,y-1*scale,width-labelW-2*scale,6.7f*scale,Color.BLACK,false);
    }

    private void checkbox(Canvas c,float x,float y,String label,boolean checked,float scale){
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.7f*scale);paint.setColor(Color.GRAY);c.drawRect(x,y-7*scale,x+7*scale,y,paint);
        if(checked){paint.setColor(Color.BLACK);paint.setStrokeWidth(1.1f*scale);Path path=new Path();path.moveTo(x+1*scale,y-4*scale);path.lineTo(x+3*scale,y-1*scale);path.lineTo(x+6*scale,y-6*scale);c.drawPath(path,paint);}paint.setStyle(Paint.Style.FILL);
        text(c,label,x+12*scale,y,7.5f*scale,Color.GRAY,false,Paint.Align.LEFT);
    }

    private String humanDate(String iso){
        try{LocalDate d=LocalDate.parse(iso);String day=d.getDayOfWeek().getDisplayName(TextStyle.FULL,ES);String month=d.getMonth().getDisplayName(TextStyle.FULL,ES);return day+", "+d.getDayOfMonth()+" de "+month+" de "+d.getYear();}catch(Exception e){return iso;}
    }

    private void box(Canvas c,float x,float y,float w,float h,int fill,int stroke){paint.setStyle(Paint.Style.FILL);paint.setColor(fill);c.drawRect(x,y,x+w,y+h,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.7f);paint.setColor(stroke);c.drawRect(x,y,x+w,y+h,paint);paint.setStyle(Paint.Style.FILL);}
    private void text(Canvas c,String value,float x,float y,float size,int color,boolean bold,Paint.Align align){paint.setTextSize(size);paint.setColor(color);paint.setTypeface(bold?Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD):Typeface.create(Typeface.SANS_SERIF,Typeface.NORMAL));paint.setTextAlign(align);paint.setStyle(Paint.Style.FILL);c.drawText(value==null?"":value,x,y,paint);}
    private void fitText(Canvas c,String value,float center,float y,float maxWidth,float size,int color,boolean bold){String v=value==null?"":value;text(c,"",center,y,size,color,bold,Paint.Align.CENTER);if(paint.measureText(v)>maxWidth){while(v.length()>1&&paint.measureText(v+"…")>maxWidth)v=v.substring(0,v.length()-1);v+="…";}c.drawText(v,center,y,paint);}
    private void drawWrappedCell(Canvas c,String value,float left,float top,float width,float height,float size,int color,boolean bold,int requestedLines){
        int saved=c.save();c.clipRect(left+1,top+1,left+width-1,top+height-1);text(c,"",left+width/2,top,size,color,bold,Paint.Align.CENTER);
        Paint.FontMetrics metrics=paint.getFontMetrics();float lineHeight=(metrics.descent-metrics.ascent)*1.04f;
        int maxLines=Math.max(1,Math.min(requestedLines,(int)((height-2)/Math.max(1f,lineHeight))));
        List<String> lines=wrapLines(value,width-6,maxLines);float first=top+(height-lines.size()*lineHeight)/2f-metrics.ascent;
        for(int i=0;i<lines.size();i++)c.drawText(lines.get(i),left+width/2,first+i*lineHeight,paint);c.restoreToCount(saved);
    }
    private List<String> wrapLines(String value,float width,int maxLines){
        String clean=value==null?"":value.trim().replaceAll("\\s+"," ");List<String> all=new ArrayList<>();
        if(clean.isEmpty()){all.add("");return all;}String line="";
        for(String word:clean.split(" ")){String next=line.isEmpty()?word:line+" "+word;if(!line.isEmpty()&&paint.measureText(next)>width){all.add(line);line=word;}else line=next;}
        if(!line.isEmpty())all.add(line);boolean truncated=all.size()>maxLines;List<String> result=new ArrayList<>(all.subList(0,Math.min(maxLines,all.size())));
        for(int i=0;i<result.size();i++){String current=result.get(i);while(current.length()>1&&paint.measureText(current+(i==result.size()-1&&truncated?"…":""))>width)current=current.substring(0,current.length()-1);result.set(i,current+(i==result.size()-1&&truncated?"…":""));}
        return result;
    }
    private void drawWrapped(Canvas c,String value,float x,float y,float width,float size,int color,int maxLines){text(c,"",x,y,size,color,false,Paint.Align.LEFT);String[] words=value.split(" ");String line="";int lines=0;for(String word:words){String next=line.isEmpty()?word:line+" "+word;if(paint.measureText(next)>width){c.drawText(line,x,y+lines*size*1.15f,paint);lines++;if(lines>=maxLines)return;line=word;}else line=next;}if(lines<maxLines)c.drawText(line,x,y+lines*size*1.15f,paint);}
}
