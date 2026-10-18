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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class LifeMinistryExcelImportManager {
    private static final long MAX_BYTES=16L*1024L*1024L;
    private LifeMinistryExcelImportManager(){}

    static final class Result {
        int added,updated;
        String lastMonth;
        final List<String> errors=new ArrayList<>(),warnings=new ArrayList<>();
        String summary(){StringBuilder text=new StringBuilder("Importación de Vida y Ministerio terminada.\n\nAsignaciones nuevas: ").append(added).append("\nAsignaciones actualizadas: ").append(updated);if(!errors.isEmpty()){text.append("\n\nFilas omitidas: ").append(errors.size());for(int i=0;i<Math.min(10,errors.size());i++)text.append("\n• ").append(errors.get(i));if(errors.size()>10)text.append("\n• … y ").append(errors.size()-10).append(" más.");}if(!warnings.isEmpty()){text.append("\n\nObservaciones:");for(String warning:warnings)text.append("\n• ").append(warning);}return text.toString();}
    }

    static Result importWorkbook(InputStream source,MinisterioDb db)throws Exception{
        Map<String,byte[]> entries=entries(source);List<String> shared=sharedStrings(entries.get("xl/sharedStrings.xml"));List<Sheet> sheets=new ArrayList<>();for(Map.Entry<String,byte[]> entry:entries.entrySet())if(entry.getKey().startsWith("xl/worksheets/")&&entry.getKey().endsWith(".xml"))sheets.add(sheet(entry.getValue(),shared));
        Result result=new Result();boolean recognized=false;SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();try{for(Sheet sheet:sheets){Map<String,Integer> h=sheet.headers();if(!(hasHeader(h,"fecha")&&hasHeader(h,"numero de asignacion","numero")&&hasHeader(h,"nombre de asignacion","asignacion","nombre")&&hasHeader(h,"tiempo minutos","tiempo","minutos")))continue;recognized=true;String previousDate=null;int inherited=0;for(int i=1;i<sheet.rows.size();i++){Row row=sheet.rows.get(i);if(dataBlank(row,h))continue;try{Models.LifePart part=new Models.LifePart();String rawDate=cell(row,h,"fecha");if(rawDate.isEmpty()){if(previousDate==null)throw new IllegalArgumentException("Fecha es obligatoria en la primera fila");part.date=previousDate;inherited++;}else{part.date=date(rawDate);previousDate=part.date;}part.number=integer(required(cell(row,h,"numero de asignacion","numero"),"Número de asignación"),"Número de asignación");part.assignmentName=required(cell(row,h,"nombre de asignacion","asignacion"),"Nombre de asignación");part.minutes=integer(required(cell(row,h,"tiempo minutos","tiempo","minutos"),"Tiempo"),"Tiempo");part.topic=cell(row,h,"tema");if(part.number<1)throw new IllegalArgumentException("Número de asignación debe ser mayor que cero");if(part.minutes<1)throw new IllegalArgumentException("Tiempo debe ser mayor que cero");int status=db.upsertImportedLifePart(part);if(status==MinisterioDb.IMPORT_ADDED)result.added++;else result.updated++;result.lastMonth=part.date.substring(0,7);
                        boolean hasStudentHeader=hasHeader(h,"alumno asignado","alumno","estudiante","participante");
                        boolean hasReaderHeader=hasHeader(h,"lector","lector asignado");
                        String studentName=cell(row,h,"alumno asignado","alumno","estudiante","participante");
                        String readerName=cell(row,h,"lector asignado","lector");
                        if((hasStudentHeader&&!studentName.isEmpty())||(hasReaderHeader&&!readerName.isEmpty())){
                            Long studentId=studentName.isEmpty()?null:db.importedLifeStudentId(studentName);
                            Long readerId=readerName.isEmpty()?null:db.importedLifeStudentId(readerName);
                            db.upsertImportedLifeAssignment(part.id,studentId,readerId);
                        }}catch(Exception error){result.errors.add("Fila "+row.number+": "+message(error));}}if(inherited>0)result.warnings.add(inherited+" filas usaron la fecha de la fila anterior.");}if(!recognized)throw new IllegalArgumentException("No se encontró la hoja esperada. Use la plantilla separada de Vida y Ministerio.");sql.setTransactionSuccessful();}finally{sql.endTransaction();}return result;
    }

    private static Map<String,byte[]> entries(InputStream source)throws Exception{Map<String,byte[]> result=new LinkedHashMap<>();long total=0;try(ZipInputStream zip=new ZipInputStream(source)){ZipEntry entry;byte[] buffer=new byte[8192];while((entry=zip.getNextEntry())!=null){String name=entry.getName();boolean wanted="xl/sharedStrings.xml".equals(name)||(name.startsWith("xl/worksheets/")&&name.endsWith(".xml"));if(!wanted||entry.isDirectory())continue;ByteArrayOutputStream data=new ByteArrayOutputStream();int read;while((read=zip.read(buffer))!=-1){total+=read;if(total>MAX_BYTES)throw new IllegalArgumentException("El archivo Excel es demasiado grande.");data.write(buffer,0,read);}result.put(name,data.toByteArray());}}if(result.isEmpty())throw new IllegalArgumentException("El archivo no es un libro XLSX válido.");return result;}
    private static List<String> sharedStrings(byte[] xml)throws Exception{List<String> result=new ArrayList<>();if(xml==null)return result;XmlPullParser parser=Xml.newPullParser();parser.setInput(new ByteArrayInputStream(xml),"UTF-8");StringBuilder current=null;int event;while((event=parser.next())!=XmlPullParser.END_DOCUMENT){if(event==XmlPullParser.START_TAG&&"si".equals(parser.getName()))current=new StringBuilder();else if(event==XmlPullParser.START_TAG&&"t".equals(parser.getName())&&current!=null)current.append(parser.nextText());else if(event==XmlPullParser.END_TAG&&"si".equals(parser.getName())&&current!=null){result.add(current.toString());current=null;}}return result;}
    private static Sheet sheet(byte[] xml,List<String> shared)throws Exception{Sheet result=new Sheet();XmlPullParser parser=Xml.newPullParser();parser.setInput(new ByteArrayInputStream(xml),"UTF-8");Row row=null;String type=null,reference=null,raw="";boolean inCell=false;int nextColumn=0,event;while((event=parser.next())!=XmlPullParser.END_DOCUMENT){String tag=localName(parser.getName());if(event==XmlPullParser.START_TAG&&"row".equals(tag)){row=new Row(parsePositive(parser.getAttributeValue(null,"r"),result.rows.size()+1));nextColumn=0;}else if(event==XmlPullParser.START_TAG&&"c".equals(tag)&&row!=null){inCell=true;type=parser.getAttributeValue(null,"t");reference=parser.getAttributeValue(null,"r");raw="";}else if(event==XmlPullParser.START_TAG&&"v".equals(tag)&&inCell)raw=parser.nextText();else if(event==XmlPullParser.START_TAG&&"t".equals(tag)&&inCell&&"inlineStr".equals(type))raw+=parser.nextText();else if(event==XmlPullParser.END_TAG&&"c".equals(tag)&&row!=null){int column=reference==null?nextColumn:column(reference);nextColumn=column+1;row.cells.put(column,decode(raw,type,shared));inCell=false;}else if(event==XmlPullParser.END_TAG&&"row".equals(tag)&&row!=null){result.rows.add(row);row=null;}}return result;}
    private static String decode(String raw,String type,List<String> shared){if(raw==null)return "";if("s".equals(type)){try{int index=Integer.parseInt(raw.trim());return index>=0&&index<shared.size()?shared.get(index):"";}catch(Exception ignored){return "";}}return raw.trim();}
    private static int column(String ref){int value=0;for(int i=0;i<ref.length();i++){char c=Character.toUpperCase(ref.charAt(i));if(c<'A'||c>'Z')break;value=value*26+c-'A'+1;}return Math.max(0,value-1);}
    private static boolean hasHeader(Map<String,Integer> headers,String...aliases){for(String alias:aliases)if(headers.containsKey(normalize(alias)))return true;for(String key:headers.keySet())for(String alias:aliases){String normalized=normalize(alias);if(normalized.length()>3&&(key.equals(normalized)||key.contains(normalized)))return true;}return false;}
    private static String cell(Row row,Map<String,Integer> headers,String...aliases){
        // Primero intenta coincidencia exacta normalizada.
        for(String alias:aliases){
            Integer column=headers.get(normalize(alias));
            if(column!=null){
                String value=row.cells.getOrDefault(column,"").trim();
                if(!value.isEmpty()) return value;
            }
        }
        // Compatibilidad con encabezados extendidos o con texto adicional
        // (por ejemplo, "Nombre de la asignación (obligatorio)").
        for(Map.Entry<String,Integer> header:headers.entrySet()){
            String key=normalize(header.getKey());
            for(String alias:aliases){
                String candidate=normalize(alias);
                if(key.equals(candidate) || (candidate.length()>7 && key.contains(candidate)) || (candidate.length()<=7 && key.startsWith(candidate))){
                    String value=row.cells.getOrDefault(header.getValue(),"").trim();
                    if(!value.isEmpty()) return value;
                }
            }
        }
        return "";
    }
    private static boolean dataBlank(Row row,Map<String,Integer> headers){return cell(row,headers,"fecha").isEmpty()&&cell(row,headers,"numero de asignacion","numero").isEmpty()&&cell(row,headers,"nombre de asignacion","asignacion").isEmpty()&&cell(row,headers,"tiempo minutos","tiempo","minutos").isEmpty()&&cell(row,headers,"tema").isEmpty();}
    private static String required(String value,String name){if(value==null||value.trim().isEmpty())throw new IllegalArgumentException(name+" es obligatorio");return value.trim();}
    private static int integer(String value,String name){try{return(int)Math.round(Double.parseDouble(value.trim()));}catch(Exception error){throw new IllegalArgumentException(name+" debe ser numérico");}}
    private static String date(String value){String text=value.trim();try{double serial=Double.parseDouble(text);if(serial>0)return LocalDate.of(1899,12,30).plusDays((long)Math.floor(serial)).toString();}catch(Exception ignored){}if(text.length()>=10&&text.charAt(4)=='-'&&text.charAt(7)=='-')text=text.substring(0,10);try{return LocalDate.parse(text).toString();}catch(Exception ignored){}for(DateTimeFormatter format:new DateTimeFormatter[]{DateTimeFormatter.ofPattern("d/M/uuuu"),DateTimeFormatter.ofPattern("d-M-uuuu")})try{return LocalDate.parse(text,format).toString();}catch(Exception ignored){}throw new IllegalArgumentException("Fecha no válida; use AAAA-MM-DD");}
    private static String localName(String name){if(name==null)return "";int colon=name.indexOf(':');return colon>=0?name.substring(colon+1):name;}
    private static int parsePositive(String value,int fallback){try{int parsed=Integer.parseInt(value);return parsed>0?parsed:fallback;}catch(Exception ignored){return fallback;}}
    private static String message(Exception error){return error.getMessage()==null?"dato no válido":error.getMessage();}
    private static String normalize(String value){return Normalizer.normalize(value==null?"":value,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim().replaceAll("\\s+"," ");}
    private static final class Row{final int number;final Map<Integer,String> cells=new HashMap<>();Row(int number){this.number=number;}boolean blank(){for(String value:cells.values())if(value!=null&&!value.trim().isEmpty())return false;return true;}}
    private static final class Sheet{final List<Row> rows=new ArrayList<>();Map<String,Integer> headers(){Map<String,Integer> result=new HashMap<>();if(rows.isEmpty())return result;for(Map.Entry<Integer,String> cell:rows.get(0).cells.entrySet())result.put(normalize(cell.getValue()),cell.getKey());return result;}}
}
