package mx.escuelaministerio.app;

import android.content.Context;
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
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** JPG and letter-size PDF reports matching the supplied bus seat-map model. */
final class TransportReportManager {
    private static final int JW_BLUE=Color.rgb(62,91,145),NUMBER_BLUE=Color.rgb(103,201,235);
    private final Context context;private final TransportProgram repository;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    TransportReportManager(Context context,MinisterioDb db){this.context=context;repository=new TransportProgram(db);}

    File pdf(TransportProgram.Trip trip)throws Exception{
        check(trip);PdfDocument document=new PdfDocument();int pageNumber=0;
        try{for(TransportProgram.Bus bus:repository.buses(trip.id)){Bitmap image=draw(trip,bus);PdfDocument.Page page=document.startPage(new PdfDocument.PageInfo.Builder(612,792,++pageNumber).create());Canvas canvas=page.getCanvas();canvas.drawColor(Color.WHITE);float scale=Math.min(576f/image.getWidth(),756f/image.getHeight()),w=image.getWidth()*scale,h=image.getHeight()*scale;canvas.drawBitmap(image,null,new RectF((612-w)/2,(792-h)/2,(612+w)/2,(792+h)/2),paint);document.finishPage(page);image.recycle();}File file=output("Programa_Transporte_"+trip.date+".pdf");try(FileOutputStream stream=new FileOutputStream(file)){document.writeTo(stream);}return file;}finally{document.close();}
    }
    File jpg(TransportProgram.Trip trip,TransportProgram.Bus bus)throws Exception{
        check(trip);Bitmap image=draw(trip,bus);File file=output("Transporte_"+trip.date+"_Autobus_"+bus.number+".jpg");try(FileOutputStream stream=new FileOutputStream(file)){if(!image.compress(Bitmap.CompressFormat.JPEG,96,stream))throw new IllegalStateException("No se pudo guardar la imagen.");}finally{image.recycle();}return file;
    }
    private void check(TransportProgram.Trip trip){if(!trip.finalized)throw new IllegalArgumentException("Finalice la asignación de asientos antes de generar el reporte.");for(TransportProgram.Bus bus:repository.buses(trip.id))if(bus.captainPassengerId==0||bus.assistantPassengerId==0||bus.captainPassengerId==bus.assistantPassengerId)throw new IllegalArgumentException("Reabra el programa y asigne capitán y auxiliar diferentes al autobús "+bus.number+".");}

    private Bitmap draw(TransportProgram.Trip trip,TransportProgram.Bus bus){
        int regular=regularSeats(bus.capacity),remaining=bus.capacity-regular,listHeight=regular/4*104+(remaining==0?0:(int)Math.ceil(remaining/2f)*46+22),height=Math.max(1860,580+listHeight);
        Bitmap bitmap=Bitmap.createBitmap(1400,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.WHITE);grid(canvas,1400,height);
        Map<Integer,TransportProgram.Passenger> seats=new HashMap<>();for(TransportProgram.Passenger p:repository.passengersOnBus(bus.id))seats.put(p.seatNumber,p);
        jwLogo(canvas,60,18);
        String congregation=empty(trip.congregation)?"CONGREGACIÓN":("CONGREGACIÓN "+trip.congregation.trim()).toUpperCase(new Locale("es","MX"));
        centerFit(canvas,congregation,875,68,970,48,Color.rgb(5,28,207),false);
        centerFit(canvas,trip.locality,875,116,850,30,Color.BLACK,true);
        centerFit(canvas,"("+trip.reason+")",875,182,930,35,Color.rgb(126,24,112),false);
        centerFit(canvas,"("+longDate(trip.date)+")",875,228,930,27,Color.BLACK,true);
        centerFit(canvas,"HORA DE SALIDA: ("+time12(trip.time)+")  ("+trip.departurePlace+")",875,275,980,30,Color.RED,true);line(canvas,540,294,1372,294,Color.BLACK,2);
        float listX=545,listW=827;box(canvas,listX,318,listW,164,Color.rgb(204,255,204));stroke(canvas,listX,318,listW,164,Color.BLACK,3);text(canvas,"AUTOBÚS No. "+bus.number,listX+listW/2,363,40,Color.BLACK,true,Paint.Align.CENTER);centerFit(canvas,"Capitán: "+repository.passengerName(bus.captainPassengerId),listX+listW/2,411,listW-42,27,Color.BLACK,true);centerFit(canvas,"Auxiliar: "+repository.passengerName(bus.assistantPassengerId),listX+listW/2,454,listW-42,27,Color.BLACK,true);
        bus(canvas,new RectF(30,318,515,height-48),bus,seats,regular,remaining);passengerList(canvas,listX,505,listW,bus,seats,regular,remaining);return bitmap;
    }

    private void jwLogo(Canvas c,float x,float y){box(c,x,y,260,235,JW_BLUE);text(c,"JW",x+130,y+100,82,Color.WHITE,false,Paint.Align.CENTER);text(c,".ORG",x+130,y+190,70,Color.WHITE,false,Paint.Align.CENTER);}

    private void bus(Canvas c,RectF area,TransportProgram.Bus bus,Map<Integer,TransportProgram.Passenger> seats,int regular,int remaining){
        float x=area.left+38,y=area.top+72,w=area.width()-76,h=area.height()-105;paint.setStyle(Paint.Style.FILL);paint.setColor(Color.rgb(205,205,205));c.drawRoundRect(new RectF(x,y,x+w,y+h),w*.22f,w*.22f,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(9);paint.setColor(Color.BLACK);c.drawRoundRect(new RectF(x,y,x+w,y+h),w*.22f,w*.22f,paint);paint.setStyle(Paint.Style.FILL);
        Path glass=new Path();glass.moveTo(x+55,y+54);glass.quadTo(x+w/2,y+9,x+w-55,y+54);glass.lineTo(x+w-78,y+175);glass.lineTo(x+78,y+175);glass.close();paint.setColor(Color.rgb(238,245,248));c.drawPath(glass,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(5);paint.setColor(Color.BLACK);c.drawPath(glass,paint);paint.setStyle(Paint.Style.FILL);paint.setColor(Color.BLACK);c.drawCircle(x+w-75,y+135,32,paint);paint.setColor(Color.LTGRAY);c.drawCircle(x+w-75,y+135,20,paint);box(c,x-22,y+105,27,90,Color.BLACK);box(c,x+w-5,y+105,27,90,Color.BLACK);
        float top=y+205,bottom=y+h-75;int visualRows=regular/4+(remaining>0?1:0);float step=visualRows<=1?100:(bottom-top)/visualRows,seatW=58,seatH=Math.min(70,step-11),left=x+43,leftInner=x+w*.31f,rightInner=x+w*.54f,right=x+w-43-seatW;int number=1;
        for(int row=0;row<regular/4;row++){float sy=top+row*step;busSeat(c,left,sy,seatW,seatH,number++,seats,bus.captainPassengerId,bus.assistantPassengerId);busSeat(c,leftInner,sy,seatW,seatH,number++,seats,bus.captainPassengerId,bus.assistantPassengerId);busSeat(c,rightInner,sy,seatW,seatH,number++,seats,bus.captainPassengerId,bus.assistantPassengerId);busSeat(c,right,sy,seatW,seatH,number++,seats,bus.captainPassengerId,bus.assistantPassengerId);}
        if(remaining>0){float sy=top+regular/4*step;if(remaining==5){float gap=(w-5*seatW-26)/4f;for(int i=0;i<5;i++)busSeat(c,x+13+i*(seatW+gap),sy,seatW,seatH,number++,seats,bus.captainPassengerId,bus.assistantPassengerId);}else{float total=remaining*seatW+(remaining-1)*20,start=x+(w-total)/2;for(int i=0;i<remaining;i++)busSeat(c,start+i*(seatW+20),sy,seatW,seatH,number++,seats,bus.captainPassengerId,bus.assistantPassengerId);}}box(c,x+46,y+h-43,w-92,22,Color.rgb(224,25,25));
    }
    private void busSeat(Canvas c,float x,float y,float w,float h,int number,Map<Integer,TransportProgram.Passenger> seats,long captain,long assistant){TransportProgram.Passenger p=seats.get(number);boolean isCaptain=p!=null&&p.id==captain,isAssistant=p!=null&&p.id==assistant;paint.setStyle(Paint.Style.FILL);paint.setColor(isCaptain?Color.rgb(255,210,76):isAssistant?Color.rgb(166,230,160):p==null?Color.rgb(232,232,232):Color.WHITE);c.drawRoundRect(new RectF(x,y+12,x+w,y+h),8,8,paint);stroke(c,x,y+12,w,h-12,Color.DKGRAY,3);box(c,x+7,y,w-14,18,Color.rgb(65,65,65));text(c,String.valueOf(number),x+w/2,y+h-13,20,Color.BLACK,true,Paint.Align.CENTER);}

    private void passengerList(Canvas c,float x,float y,float width,TransportProgram.Bus bus,Map<Integer,TransportProgram.Passenger> seats,int regular,int remaining){float gap=26,column=(width-gap)/2,numberW=48,current=y;for(int base=1;base<=regular;base+=4){listSeat(c,x,current,column,numberW,base,seats,bus.captainPassengerId,bus.assistantPassengerId);listSeat(c,x+column+gap,current,column,numberW,base+2,seats,bus.captainPassengerId,bus.assistantPassengerId);listSeat(c,x,current+44,column,numberW,base+1,seats,bus.captainPassengerId,bus.assistantPassengerId);listSeat(c,x+column+gap,current+44,column,numberW,base+3,seats,bus.captainPassengerId,bus.assistantPassengerId);current+=104;}if(remaining>0){int leftCount=(remaining+1)/2,rightStart=regular+leftCount+1;for(int row=0;row<leftCount;row++){listSeat(c,x,current+row*44,column,numberW,regular+1+row,seats,bus.captainPassengerId,bus.assistantPassengerId);int right=rightStart+row;if(right<=bus.capacity)listSeat(c,x+column+gap,current+row*44,column,numberW,right,seats,bus.captainPassengerId,bus.assistantPassengerId);}}}
    private void listSeat(Canvas c,float x,float y,float width,float numberW,int seat,Map<Integer,TransportProgram.Passenger> seats,long captain,long assistant){TransportProgram.Passenger p=seats.get(seat);boolean isCaptain=p!=null&&p.id==captain,isAssistant=p!=null&&p.id==assistant;box(c,x,y,numberW,40,isCaptain?Color.rgb(255,210,76):isAssistant?Color.rgb(166,230,160):NUMBER_BLUE);stroke(c,x,y,numberW,40,Color.BLACK,2);box(c,x+numberW,y,width-numberW,40,Color.rgb(250,250,250));stroke(c,x+numberW,y,width-numberW,40,Color.BLACK,2);text(c,String.valueOf(seat),x+numberW/2,y+29,23,Color.BLACK,false,Paint.Align.CENTER);fit(c,p==null?"":p.name+(isCaptain?"  ·  CAPITÁN":isAssistant?"  ·  AUXILIAR":""),x+numberW+10,y+28,width-numberW-18,20,isCaptain||isAssistant);}

    private static int regularSeats(int capacity){return capacity>=5&&capacity%4==1?capacity-5:capacity-capacity%4;}
    private static boolean empty(String value){return value==null||value.trim().isEmpty();}
    private static String longDate(String iso){LocalDate d=LocalDate.parse(iso);Locale es=new Locale("es","MX");String day=d.getDayOfWeek().getDisplayName(TextStyle.FULL,es),month=d.getMonth().getDisplayName(TextStyle.FULL,es);return day.substring(0,1).toUpperCase(es)+day.substring(1)+", "+d.getDayOfMonth()+" de "+month+" de "+d.getYear();}
    private static String time12(String value){try{String[] p=value.split(":");int hour=Integer.parseInt(p[0]),minute=Integer.parseInt(p[1]);return String.format(Locale.ROOT,"%d:%02d %s",hour%12==0?12:hour%12,minute,hour<12?"A.M.":"P.M.");}catch(Exception ignored){return value;}}
    private void grid(Canvas c,int w,int h){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(Color.rgb(225,225,225));for(int x=0;x<w;x+=44)c.drawLine(x,0,x,h,paint);for(int y=0;y<h;y+=44)c.drawLine(0,y,w,y,paint);paint.setStyle(Paint.Style.FILL);}
    private File output(String name)throws Exception{File folder=new File(context.getCacheDir(),"reportes");if(!folder.exists()&&!folder.mkdirs())throw new IllegalStateException("No se pudo preparar la carpeta de reportes.");return new File(folder,name);}
    private void box(Canvas c,float x,float y,float w,float h,int color){paint.setStyle(Paint.Style.FILL);paint.setColor(color);c.drawRect(x,y,x+w,y+h,paint);}
    private void stroke(Canvas c,float x,float y,float w,float h,int color,float size){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(size);paint.setColor(color);c.drawRect(x,y,x+w,y+h,paint);paint.setStyle(Paint.Style.FILL);}
    private void line(Canvas c,float x1,float y1,float x2,float y2,int color,float size){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(size);paint.setColor(color);c.drawLine(x1,y1,x2,y2,paint);paint.setStyle(Paint.Style.FILL);}
    private void text(Canvas c,String value,float x,float y,float size,int color,boolean bold,Paint.Align align){paint.setTextSize(size);paint.setColor(color);paint.setTextAlign(align);paint.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));c.drawText(value==null?"":value,x,y,paint);}
    private void centerFit(Canvas c,String value,float center,float y,float max,float size,int color,boolean bold){paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));paint.setTextSize(size);String line=value==null?"":value;while(line.length()>1&&paint.measureText(line)>max&&paint.getTextSize()>16)paint.setTextSize(paint.getTextSize()-1);if(paint.measureText(line)>max)line=shorten(line,max);paint.setColor(color);c.drawText(line,center,y,paint);}
    private void fit(Canvas c,String value,float x,float y,float max,float size,boolean bold){paint.setTextAlign(Paint.Align.LEFT);paint.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));paint.setTextSize(size);paint.setColor(Color.BLACK);c.drawText(shorten(value==null?"":value,max),x,y,paint);}
    private String shorten(String value,float max){String line=value;while(line.length()>1&&paint.measureText(line)>max)line=line.substring(0,line.length()-1);if(!line.equals(value)){while(line.length()>1&&paint.measureText(line+"…")>max)line=line.substring(0,line.length()-1);line+="…";}return line;}
}
