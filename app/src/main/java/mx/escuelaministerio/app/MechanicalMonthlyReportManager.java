package mx.escuelaministerio.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class MechanicalMonthlyReportManager {
    private static final Locale ES = new Locale("es", "MX");
    private static final LocalDate CLEANING_ANCHOR = LocalDate.of(2026, 10, 7);
    private static final int[] CLEANING_SEQUENCE = {2, 3, 4, 5, 1};
    private final Context context;
    private final MinisterioDb db;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    MechanicalMonthlyReportManager(Context context, MinisterioDb db) {
        this.context = context;
        this.db = db;
    }

    File monthlyPdf(String month, String congregation, String locality, String circuit) throws IOException {
        YearMonth selected = YearMonth.parse(month);
        List<MeetingSlot> slots = monthSlots(selected);
        if (slots.isEmpty()) throw new IllegalArgumentException("No hay reuniones configuradas para el mes seleccionado.");

        Map<String, String> cleaning = cleaningAssignments(selected);
        Map<String, Models.LifeProgramRow> bookReaders = new HashMap<>();
        for (Models.LifeProgramRow row : db.lifeProgram(month)) {
            if (row.part != null && row.part.isBookStudy()) bookReaders.put(row.part.date, row);
        }

        PdfDocument pdf = new PdfDocument();
        PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(792, 612, 1).create());
        Canvas canvas = page.getCanvas();
        canvas.drawColor(Color.WHITE);

        final float left = 10f, right = 782f;
        float y = 10f;
        drawBox(canvas, left, y, right-left, 36, Color.rgb(8, 43, 94));
        drawFit(canvas, "ROL DE ASIGNACIONES VARONES " + monthName(selected), (left+right)/2, y+24,
                right-left-18, 16, Color.WHITE, true);
        y += 36;
        drawBox(canvas, left, y, right-left, 34, Color.rgb(89, 89, 89));
        String header = "CONGREGACIÓN " + clean(congregation);
        if (!clean(locality).isEmpty()) header += ", " + clean(locality);
        if (!clean(circuit).isEmpty()) header += ". CIRCUITO " + clean(circuit);
        drawFit(canvas, header, (left+right)/2, y+23, right-left-18, 15, Color.WHITE, true);
        y += 40;

        float labelWidth = 130f;
        float columnWidth = (right-left-labelWidth) / slots.size();
        float dateHeight = 49f;
        float[] heights = {48, 48, 48, 48, 48, 70, 70, 48};
        float bodyHeight=0;for(float height:heights)bodyHeight+=height;
        String[] labels = {"ORACIÓN INICIAL", "PRESIDENTE", "LECTOR", "ORACIÓN FINAL",
                "ACOMODADOR DE ENTRADA", "ACOMODADORES DE AUDITORIO", "MICRÓFONOS", "LIMPIEZA"};

        drawBox(canvas, left, y, labelWidth, dateHeight, Color.rgb(242, 242, 242));
        drawCell(canvas, selected.getMonth().getDisplayName(TextStyle.FULL, ES).toUpperCase(ES) + "\n" + selected.getYear(),
                left, y, labelWidth, dateHeight, 10.5f, Color.BLACK, true, 2);
        for (int i=0;i<slots.size();i++) {
            MeetingSlot slot=slots.get(i);
            int fill="MIDWEEK".equals(slot.type)?Color.rgb(0, 112, 192):Color.rgb(242, 242, 242);
            int textColor="MIDWEEK".equals(slot.type)?Color.WHITE:Color.BLACK;
            float x=left+labelWidth+i*columnWidth;
            drawBox(canvas,x,y,columnWidth,dateHeight,fill);
            String day=slot.date.getDayOfWeek().getDisplayName(TextStyle.FULL,ES).toUpperCase(ES);
            drawCell(canvas,day+"\n"+slot.date.getDayOfMonth(),x,y,columnWidth,dateHeight,
                    Math.max(6.2f,Math.min(10f,columnWidth/7f)),textColor,true,2);
        }
        y += dateHeight;

        for (int row=0;row<labels.length;row++) {
            float rowHeight=heights[row];
            drawBox(canvas,left,y,labelWidth,rowHeight,Color.rgb(47,57,70));
            drawCell(canvas,labels[row],left,y,labelWidth,rowHeight,8.1f,Color.WHITE,true,2);
            for (int i=0;i<slots.size();i++) {
                MeetingSlot slot=slots.get(i);
                float x=left+labelWidth+i*columnWidth;
                int fill="MIDWEEK".equals(slot.type)?Color.rgb(189,215,238):Color.rgb(242,242,242);
                if (slot.suspended) {
                    if(row==0){
                        drawBox(canvas,x,y,columnWidth,bodyHeight,fill);
                        drawCell(canvas,"ASAMBLEA / REUNIÓN SUSPENDIDA",x,y,columnWidth,bodyHeight,
                                Math.max(5.8f,Math.min(8.4f,columnWidth/8f)),Color.BLACK,true,5);
                    }
                    continue;
                }
                drawBox(canvas,x,y,columnWidth,rowHeight,fill);
                String value=rowValue(slot,row,bookReaders,cleaning);
                float size=Math.max(5.4f,Math.min(8.1f,columnWidth/8f));
                drawCell(canvas,value,x,y,columnWidth,rowHeight,size,Color.BLACK,true,rowHeight>40?3:2);
            }
            y += rowHeight;
        }

        pdf.finishPage(page);
        File directory=new File(context.getCacheDir(),"reportes");
        if(!directory.exists()&&!directory.mkdirs())throw new IOException("No fue posible preparar la carpeta de reportes.");
        File file=new File(directory,"Asignaciones_Mecanicas_"+month+".pdf");
        try(FileOutputStream output=new FileOutputStream(file)){pdf.writeTo(output);}finally{pdf.close();}
        return file;
    }

    private String rowValue(MeetingSlot slot, int row, Map<String,Models.LifeProgramRow> readers, Map<String,String> cleaning) {
        Models.MechanicalProgram program=db.mechanicalProgram(slot.date.toString(),slot.type);
        switch (row) {
            case 0: return mechanicalName(program,Models.MechanicalRole.ORACION_INICIAL);
            case 1:
                if("MIDWEEK".equals(slot.type)){
                    Models.President president=db.president(slot.presidentId);
                    return president==null?"PENDIENTE":president.name;
                }
                Models.Student president=db.student(slot.presidentId);
                return president==null?"PENDIENTE":president.name;
            case 2:
                if(slot.visit)return "CANCELADO POR VISITA";
                if("WEEKEND".equals(slot.type)){
                    Models.Student reader=db.student(slot.readerId);
                    return reader==null?"PENDIENTE":reader.name;
                }
                Models.LifeProgramRow book=readers.get(slot.date.toString());
                return book==null||book.reader==null?"PENDIENTE":book.reader.name;
            case 3: return mechanicalName(program,Models.MechanicalRole.ORACION_FINAL);
            case 4: return mechanicalName(program,Models.MechanicalRole.ENTRADA);
            case 5: return mechanicalName(program,Models.MechanicalRole.AUDITORIO_1)+"\n"+
                    mechanicalName(program,Models.MechanicalRole.AUDITORIO_2);
            case 6: return mechanicalName(program,Models.MechanicalRole.MICROFONO_1)+"\n"+
                    mechanicalName(program,Models.MechanicalRole.MICROFONO_2);
            case 7: return cleaning.getOrDefault(slot.key(),"PENDIENTE");
            default: return "";
        }
    }

    private String mechanicalName(Models.MechanicalProgram program, Models.MechanicalRole role) {
        if(program==null)return "PENDIENTE";
        Models.Student student=db.student(program.studentId(role));
        return student==null?"PENDIENTE":student.name;
    }

    private List<MeetingSlot> monthSlots(YearMonth month) {
        List<MeetingSlot> result=new ArrayList<>();
        for(Models.Meeting meeting:db.meetings(month.toString()))result.add(new MeetingSlot(meeting));
        for(Models.PublicMeeting meeting:db.publicMeetings(month.toString()))result.add(new MeetingSlot(meeting));
        result.sort(Comparator.comparing((MeetingSlot s)->s.date).thenComparingInt(s->"MIDWEEK".equals(s.type)?0:1));
        return result;
    }

    private Map<String,String> cleaningAssignments(YearMonth through) {
        Map<String,String> result=new HashMap<>();
        YearMonth start=YearMonth.from(CLEANING_ANCHOR);
        if(through.isBefore(start))return result;
        List<MeetingSlot> all=new ArrayList<>();
        for(YearMonth month=start;!month.isAfter(through);month=month.plusMonths(1))all.addAll(monthSlots(month));
        all.sort(Comparator.comparing((MeetingSlot s)->s.date).thenComparingInt(s->"MIDWEEK".equals(s.type)?0:1));
        int index=0;
        for(MeetingSlot slot:all){
            if(slot.date.isBefore(CLEANING_ANCHOR)||slot.suspended)continue;
            result.put(slot.key(),"GRUPO "+CLEANING_SEQUENCE[index%CLEANING_SEQUENCE.length]);
            index++;
        }
        return result;
    }

    private String monthName(YearMonth month){return month.getMonth().getDisplayName(TextStyle.FULL,ES).toUpperCase(ES)+" "+month.getYear();}
    private String clean(String value){return (value==null?"":value.trim()).toUpperCase(ES);}

    private void drawBox(Canvas canvas,float x,float y,float width,float height,int fill){
        paint.setStyle(Paint.Style.FILL);paint.setColor(fill);canvas.drawRect(x,y,x+width,y+height,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.8f);paint.setColor(Color.BLACK);canvas.drawRect(x,y,x+width,y+height,paint);paint.setStyle(Paint.Style.FILL);
    }

    private void prepareText(float size,int color,boolean bold,Paint.Align align){
        paint.setTextSize(size);paint.setColor(color);paint.setTextAlign(align);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF,bold?Typeface.BOLD:Typeface.NORMAL));paint.setStyle(Paint.Style.FILL);
    }

    private void drawFit(Canvas canvas,String value,float center,float baseline,float maxWidth,float size,int color,boolean bold){
        String text=clean(value);prepareText(size,color,bold,Paint.Align.CENTER);
        while(size>5.5f&&paint.measureText(text)>maxWidth){size-=.4f;paint.setTextSize(size);}
        canvas.drawText(text,center,baseline,paint);
    }

    private void drawCell(Canvas canvas,String value,float left,float top,float width,float height,float size,int color,boolean bold,int maxLines){
        int saved=canvas.save();canvas.clipRect(left+1,top+1,left+width-1,top+height-1);
        prepareText(size,color,bold,Paint.Align.CENTER);
        List<String> lines=new ArrayList<>();
        for(String paragraph:clean(value).split("\\n",-1)){
            String line="";
            for(String word:paragraph.split(" ")){
                String next=line.isEmpty()?word:line+" "+word;
                if(!line.isEmpty()&&paint.measureText(next)>width-6){lines.add(line);line=word;}else line=next;
            }
            if(!line.isEmpty())lines.add(line);
        }
        if(lines.isEmpty())lines.add("");
        if(lines.size()>maxLines){
            lines=new ArrayList<>(lines.subList(0,maxLines));int last=maxLines-1;String text=lines.get(last);
            while(text.length()>1&&paint.measureText(text+"…")>width-6)text=text.substring(0,text.length()-1);
            lines.set(last,text+"…");
        }
        Paint.FontMetrics fm=paint.getFontMetrics();float lineHeight=(fm.descent-fm.ascent)*1.05f;
        float baseline=top+(height-lines.size()*lineHeight)/2-fm.ascent;
        for(int i=0;i<lines.size();i++)canvas.drawText(lines.get(i),left+width/2,baseline+i*lineHeight,paint);
        canvas.restoreToCount(saved);
    }

    private static final class MeetingSlot {
        final LocalDate date;final String type;final boolean suspended;final boolean visit;final long presidentId;final long readerId;
        MeetingSlot(Models.Meeting meeting){date=LocalDate.parse(meeting.date);type="MIDWEEK";suspended=meeting.suspended;visit=meeting.visit;presidentId=meeting.presidentId;readerId=0;}
        MeetingSlot(Models.PublicMeeting meeting){date=LocalDate.parse(meeting.date);type="WEEKEND";suspended=meeting.suspended;visit=meeting.visit;presidentId=meeting.presidentId;readerId=meeting.readerId;}
        String key(){return date+"|"+type;}
    }
}
