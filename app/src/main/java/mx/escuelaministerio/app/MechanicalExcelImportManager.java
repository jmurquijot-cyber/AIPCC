package mx.escuelaministerio.app;

import android.database.sqlite.SQLiteDatabase;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class MechanicalExcelImportManager {
    private static final long MAX_BYTES=16L*1024L*1024L;
    private MechanicalExcelImportManager(){}
    static final class Result {
        int added,updated;
        final List<String> errors=new ArrayList<>();
        String summary(){StringBuilder s=new StringBuilder("Importación de Asignaciones Mecánicas terminada.\n\nRegistros procesados: ").append(added+updated).append("\nNuevos: ").append(added).append("\nActualizados: ").append(updated);if(!errors.isEmpty()){s.append("\n\nFilas omitidas: ").append(errors.size());for(int i=0;i<Math.min(10,errors.size());i++)s.append("\n• ").append(errors.get(i));if(errors.size()>10)s.append("\n• … y ").append(errors.size()-10).append(" más.");}return s.toString();}
    }
    static Result importWorkbook(InputStream source,MinisterioDb db)throws Exception{
        Map<String,byte[]> entries=new LinkedHashMap<>();long total=0;
        try(ZipInputStream zip=new ZipInputStream(source)){ZipEntry e;byte[] buf=new byte[8192];while((e=zip.getNextEntry())!=null){String n=e.getName();if(!("xl/sharedStrings.xml".equals(n)||(n.startsWith("xl/worksheets/")&&n.endsWith(".xml"))))continue;ByteArrayOutputStream out=new ByteArrayOutputStream();int count;while((count=zip.read(buf))!=-1){total+=count;if(total>MAX_BYTES)throw new IllegalArgumentException("El archivo Excel es demasiado grande.");out.write(buf,0,count);}entries.put(n,out.toByteArray());}}
        if(entries.isEmpty())throw new IllegalArgumentException("El archivo no es un libro XLSX válido.");
        List<String> shared=sharedStrings(entries.get("xl/sharedStrings.xml"));Result result=new Result();boolean recognized=false;
        SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
        try{
            for(Map.Entry<String,byte[]> entry:entries.entrySet()){
                if(!entry.getKey().startsWith("xl/worksheets/")||!entry.getKey().endsWith(".xml"))continue;
                Sheet sheet=sheet(entry.getValue(),shared);Map<String,Integer> h=sheet.headers();
                if(!(has(h,"fecha")&&has(h,"tipo de reunion","tipo reunion")&&has(h,"acomodador de la entrada","acomodador entrada")))continue;
                recognized=true;
                for(int i=1;i<sheet.rows.size();i++){Row row=sheet.rows.get(i);if(row.blank())continue;try{
                    String rawDate=cell(row,h,"fecha");if(rawDate.isEmpty())throw new IllegalArgumentException("Fecha es obligatoria.");
                    Models.MechanicalProgram p=new Models.MechanicalProgram();p.date=date(rawDate);
                    String type=normalize(cell(row,h,"tipo de reunion","tipo reunion"));
                    if(type.equals("entre semana")||type.equals("midweek"))p.meetingType="MIDWEEK";
                    else if(type.equals("fin de semana")||type.equals("weekend")||type.equals("reunion publica y atalaya"))p.meetingType="WEEKEND";
                    else throw new IllegalArgumentException("Tipo de reunión debe ser Entre semana o Fin de semana.");
                    boolean exists=db.mechanicalProgram(p.date,p.meetingType)!=null;
                    for(Models.MechanicalRole role:Models.MechanicalRole.values()){
                        String value=cell(row,h,roleHeader(role));
                        long id=value.isEmpty()?0:db.importedLifeStudentId(value);
                        p.assigned.put(role,id);
                    }
                    p.openingSong=song(cell(row,h,"cancion inicial","cantico inicial"),db);
                    p.middleSong=song(cell(row,h,"cancion intermedia","cantico intermedio"),db);
                    p.closingSong=song(cell(row,h,"cancion final","cantico final"),db);
                    if("WEEKEND".equals(p.meetingType))db.saveWeekendMechanicalProgram(p);else db.saveMechanicalProgram(p);
                    if(exists)result.updated++;else result.added++;
                }catch(Exception ex){result.errors.add("Fila "+row.number+": "+(ex.getMessage()==null?"dato no válido":ex.getMessage()));}}
            }
            if(!recognized)throw new IllegalArgumentException("No se encontró una hoja con las columnas Fecha, Tipo de reunión y Acomodador de la entrada. Use la plantilla de Asignaciones Mecánicas.");
            sql.setTransactionSuccessful();
        }finally{sql.endTransaction();}
        return result;
    }
    private static String roleHeader(Models.MechanicalRole r){switch(r){
        case ENTRADA:return "acomodador de la entrada";case AUDITORIO_1:return "acomodador de auditorio 1";case AUDITORIO_2:return "acomodador de auditorio 2";
        case MICROFONO_1:return "microfono 1";case MICROFONO_2:return "microfono 2";case ORACION_INICIAL:return "oracion inicial";case ORACION_FINAL:return "oracion final";case PLATAFORMA_1:return "plataforma 1";default:return "plataforma 2";}}
    private static String song(String raw,MinisterioDb db){String value=raw==null?"":raw.trim();if(value.isEmpty())return "";for(Models.Song s:db.songs())if(value.equals(String.valueOf(s.number))||normalize(value).equals(normalize(s.title))||normalize(value).equals(normalize(s.toString())))return s.toString();throw new IllegalArgumentException("Canción no encontrada en el catálogo: "+value);}
    private static List<String> sharedStrings(byte[] xml)throws Exception{List<String> out=new ArrayList<>();if(xml==null)return out;XmlPullParser p=Xml.newPullParser();p.setInput(new ByteArrayInputStream(xml),"UTF-8");StringBuilder cur=null;int e;while((e=p.next())!=XmlPullParser.END_DOCUMENT){if(e==XmlPullParser.START_TAG&&"si".equals(p.getName()))cur=new StringBuilder();else if(e==XmlPullParser.START_TAG&&"t".equals(p.getName())&&cur!=null)cur.append(p.nextText());else if(e==XmlPullParser.END_TAG&&"si".equals(p.getName())&&cur!=null){out.add(cur.toString());cur=null;}}return out;}
    private static Sheet sheet(byte[] xml,List<String> shared)throws Exception{Sheet s=new Sheet();XmlPullParser p=Xml.newPullParser();p.setInput(new ByteArrayInputStream(xml),"UTF-8");Row row=null;String type=null,ref=null,raw="";boolean in=false;int next=0,e;while((e=p.next())!=XmlPullParser.END_DOCUMENT){String tag=local(p.getName());if(e==XmlPullParser.START_TAG&&"row".equals(tag)){row=new Row(pos(p.getAttributeValue(null,"r"),s.rows.size()+1));next=0;}else if(e==XmlPullParser.START_TAG&&"c".equals(tag)&&row!=null){in=true;type=p.getAttributeValue(null,"t");ref=p.getAttributeValue(null,"r");raw="";}else if(e==XmlPullParser.START_TAG&&"v".equals(tag)&&in)raw=p.nextText();else if(e==XmlPullParser.START_TAG&&"t".equals(tag)&&in&&"inlineStr".equals(type))raw+=p.nextText();else if(e==XmlPullParser.END_TAG&&"c".equals(tag)&&row!=null){int col=ref==null?next:column(ref);next=col+1;row.cells.put(col,decode(raw,type,shared));in=false;}else if(e==XmlPullParser.END_TAG&&"row".equals(tag)&&row!=null){s.rows.add(row);row=null;}}return s;}
    private static String decode(String raw,String type,List<String> shared){if(raw==null)return "";if("s".equals(type))try{int i=Integer.parseInt(raw.trim());return i>=0&&i<shared.size()?shared.get(i):"";}catch(Exception ignored){return "";}return raw.trim();}
    private static int column(String r){int n=0;for(int i=0;i<r.length();i++){char c=Character.toUpperCase(r.charAt(i));if(c<'A'||c>'Z')break;n=n*26+c-'A'+1;}return Math.max(0,n-1);}
    private static boolean has(Map<String,Integer> h,String...a){for(String x:a)if(h.containsKey(normalize(x)))return true;return false;}
    private static String cell(Row r,Map<String,Integer> h,String...a){for(String x:a){Integer c=h.get(normalize(x));if(c!=null)return r.cells.getOrDefault(c,"").trim();}return "";}
    private static String date(String v){String t=v.trim();try{double n=Double.parseDouble(t);if(n>0)return LocalDate.of(1899,12,30).plusDays((long)Math.floor(n)).toString();}catch(Exception ignored){}if(t.length()>=10&&t.charAt(4)=='-'&&t.charAt(7)=='-')t=t.substring(0,10);try{return LocalDate.parse(t).toString();}catch(Exception ignored){}for(DateTimeFormatter f:new DateTimeFormatter[]{DateTimeFormatter.ofPattern("d/M/uuuu"),DateTimeFormatter.ofPattern("d-M-uuuu")})try{return LocalDate.parse(t,f).toString();}catch(Exception ignored){}throw new IllegalArgumentException("Fecha no válida; use AAAA-MM-DD.");}
    private static String local(String n){if(n==null)return "";int i=n.indexOf(':');return i>=0?n.substring(i+1):n;}
    private static int pos(String v,int fallback){try{int n=Integer.parseInt(v);return n>0?n:fallback;}catch(Exception e){return fallback;}}
    private static String normalize(String v){return Normalizer.normalize(v==null?"":v,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim().replaceAll("\\s+"," ");}
    private static final class Row{final int number;final Map<Integer,String> cells=new HashMap<>();Row(int n){number=n;}boolean blank(){for(String v:cells.values())if(v!=null&&!v.trim().isEmpty())return false;return true;}}
    private static final class Sheet{final List<Row> rows=new ArrayList<>();Map<String,Integer> headers(){Map<String,Integer> h=new HashMap<>();if(!rows.isEmpty())for(Map.Entry<Integer,String> c:rows.get(0).cells.entrySet())h.put(normalize(c.getValue()),c.getKey());return h;}}
}
