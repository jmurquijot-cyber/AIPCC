package mx.escuelaministerio.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Makes portable encrypted backups and restores validated SQLite files atomically. */
final class SupportMaintenance {
    private static final byte[] MAGIC={'A','A','P','R','C','-','D','B','-','1','\n'};
    private static final byte[] SQLITE={'S','Q','L','i','t','e',' ','f','o','r','m','a','t',' ','3',0};
    private static final int KDF_ROUNDS=150_000;
    private final Context context;
    private final MinisterioDb helper;

    static final class Restored {
        final MinisterioDb database;
        final String rollbackName;
        Restored(MinisterioDb database,String rollbackName){this.database=database;this.rollbackName=rollbackName;}
    }

    SupportMaintenance(Context context,MinisterioDb helper){this.context=context.getApplicationContext();this.helper=helper;}

    private static Cipher cipher(int mode,char[] pass,byte[] salt,byte[] iv) throws Exception{
        PBEKeySpec keySpec=new PBEKeySpec(pass,salt,KDF_ROUNDS,256);
        byte[] raw;
        try{raw=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).getEncoded();}
        finally{keySpec.clearPassword();}
        try{Cipher result=Cipher.getInstance("AES/GCM/NoPadding");result.init(mode,new SecretKeySpec(raw,"AES"),new GCMParameterSpec(128,iv));return result;}
        finally{Arrays.fill(raw,(byte)0);}
    }

    private static void copy(InputStream in,OutputStream out) throws Exception{
        byte[] buffer=new byte[8192];int count;
        while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
    }

    private static void readExactly(InputStream input,byte[] bytes) throws Exception{
        int offset=0;
        while(offset<bytes.length){int read=input.read(bytes,offset,bytes.length-offset);if(read<0)throw new IllegalArgumentException("Archivo de respaldo incompleto.");offset+=read;}
    }

    private void checkpoint(){
        try(Cursor cursor=helper.getWritableDatabase().rawQuery("PRAGMA wal_checkpoint(TRUNCATE)",null)){
            if(cursor.moveToFirst()&&cursor.getInt(0)!=0)throw new IllegalStateException("La base de datos está ocupada; cierre otras operaciones e intente de nuevo.");
        }
    }

    File encryptedBackup(char[] pass) throws Exception{
        if(pass.length<8)throw new IllegalArgumentException("La clave del respaldo debe tener al menos 8 caracteres.");
        checkpoint();
        verifyDatabase(context.getDatabasePath(MinisterioDb.DB_NAME));
        File directory=new File(context.getCacheDir(),"reportes");
        if(!directory.exists()&&!directory.mkdirs())throw new IllegalStateException("No se pudo preparar el respaldo.");
        File file=new File(directory,"AAPRC_respaldo_"+System.currentTimeMillis()+".aaprc");
        byte[] salt=new byte[16],iv=new byte[12];SecureRandom random=new SecureRandom();random.nextBytes(salt);random.nextBytes(iv);
        try(FileInputStream source=new FileInputStream(context.getDatabasePath(MinisterioDb.DB_NAME));FileOutputStream target=new FileOutputStream(file)){
            target.write(MAGIC);target.write(salt);target.write(iv);
            try(CipherOutputStream encrypted=new CipherOutputStream(target,cipher(Cipher.ENCRYPT_MODE,pass,salt,iv))){copy(source,encrypted);}
        }catch(Exception error){Files.deleteIfExists(file.toPath());throw error;}
        finally{Arrays.fill(salt,(byte)0);Arrays.fill(iv,(byte)0);}
        return file;
    }

    private static void materialize(InputStream input,File staged,char[] pass) throws Exception{
        PushbackInputStream stream=new PushbackInputStream(input,MAGIC.length);
        byte[] prefix=new byte[MAGIC.length];readExactly(stream,prefix);
        if(Arrays.equals(prefix,MAGIC)){
            byte[] salt=new byte[16],iv=new byte[12];
            readExactly(stream,salt);readExactly(stream,iv);
            if(pass.length==0)throw new IllegalArgumentException("Indique la clave utilizada para crear este respaldo.");
            try(CipherInputStream decoded=new CipherInputStream(stream,cipher(Cipher.DECRYPT_MODE,pass,salt,iv));FileOutputStream target=new FileOutputStream(staged)){
                copy(decoded,target);
            }catch(Exception error){throw new IllegalArgumentException("Clave incorrecta o respaldo dañado.",error);}
            finally{Arrays.fill(salt,(byte)0);Arrays.fill(iv,(byte)0);}
        }else{
            stream.unread(prefix);
            try(FileOutputStream target=new FileOutputStream(staged)){copy(stream,target);}
        }
        try(FileInputStream check=new FileInputStream(staged)){
            byte[] header=new byte[SQLITE.length];
            readExactly(check,header);
            if(!Arrays.equals(header,SQLITE))throw new IllegalArgumentException("El archivo no contiene una base de datos válida.");
        }
    }

    private static String integrity(SQLiteDatabase sql){
        try(Cursor cursor=sql.rawQuery("PRAGMA integrity_check",null)){
            if(!cursor.moveToFirst())throw new IllegalArgumentException("No se pudo comprobar la base de datos.");
            String result=cursor.getString(0);
            if(!"ok".equalsIgnoreCase(result))throw new IllegalArgumentException("La base de datos presenta daños: "+result);
            return result;
        }
    }

    private static void verifyDatabase(File file){
        SQLiteDatabase sql=SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);
        try{
            integrity(sql);
            int version;
            try(Cursor cursor=sql.rawQuery("PRAGMA user_version",null)){version=cursor.moveToFirst()?cursor.getInt(0):0;}
            if(version<1||version>MinisterioDb.DB_VERSION)throw new IllegalArgumentException("Versión de respaldo incompatible: "+version+".");
            for(String table:new String[]{"students","presidents","presentations","meetings","movements"}){
                try(Cursor cursor=sql.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",new String[]{table})){
                    if(!cursor.moveToFirst())throw new IllegalArgumentException("Falta la tabla «"+table+"» en este respaldo.");
                }
            }
        }finally{sql.close();}
    }

    Restored restore(InputStream input,char[] pass) throws Exception{
        File current=context.getDatabasePath(MinisterioDb.DB_NAME);
        File staged=File.createTempFile("soporte-entrada-",".db",current.getParentFile());
        String rollback=null;
        boolean swapped=false;
        try{
            materialize(input,staged,pass);
            verifyDatabase(staged);
            List<ContentValues> accounts=new SupportAuth(helper).accountSnapshot();
            checkpoint();
            rollback=helper.backupBeforeHistoricalImport();
            SQLiteDatabase imported=SQLiteDatabase.openDatabase(staged.getAbsolutePath(),null,SQLiteDatabase.OPEN_READWRITE);
            try{
                try(Cursor mode=imported.rawQuery("PRAGMA journal_mode=DELETE",null)){mode.moveToFirst();}
                SupportAuth.preserveAccounts(imported,accounts);
            }finally{imported.close();}
            verifyDatabase(staged);
            helper.close();
            Files.deleteIfExists(new File(current.getAbsolutePath()+"-wal").toPath());
            Files.deleteIfExists(new File(current.getAbsolutePath()+"-shm").toPath());
            try{Files.move(staged.toPath(),current.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException error){Files.move(staged.toPath(),current.toPath(),StandardCopyOption.REPLACE_EXISTING);}
            swapped=true;
            MinisterioDb restored=new MinisterioDb(context);
            try{restored.getWritableDatabase();verifyDatabase(current);return new Restored(restored,rollback);}
            catch(Exception error){restored.close();throw error;}
        }catch(Exception error){
            if(swapped&&rollback!=null){
                File old=new File(new File(context.getFilesDir(),"respaldos"),rollback);
                Files.copy(old.toPath(),current.toPath(),StandardCopyOption.REPLACE_EXISTING);
            }
            throw error;
        }finally{Files.deleteIfExists(staged.toPath());}
    }

    List<File> localBackups(){
        File directory=new File(context.getFilesDir(),"respaldos");
        File[] list=directory.listFiles((dir,name)->name.startsWith("escuela_ministerio_")&&name.endsWith(".db"));
        List<File> result=new ArrayList<>();if(list!=null)result.addAll(Arrays.asList(list));
        result.sort(Comparator.comparingLong(File::lastModified).reversed());return result;
    }

    String healthReport(){
        SQLiteDatabase sql=helper.getReadableDatabase();
        integrity(sql);
        StringBuilder result=new StringBuilder("Integridad SQLite: correcta\nTablas y registros:");
        for(String table:new String[]{"students","presidents","presentations","movements","life_ministry_parts","life_ministry_assignments","mechanical_programs","public_meetings","visiting_speakers","historical_movements","CANCIONES","preaching_campaigns","preaching_publishers","preaching_day_captains","preaching_shift_captains","preaching_transport","preaching_groups","transport_interested_people","transport_trips","transport_buses","transport_passengers","transport_seats","support_users"}){
            try(Cursor cursor=sql.rawQuery("SELECT COUNT(*) FROM "+table,null)){
                result.append("\n").append(table).append(": ").append(cursor.moveToFirst()?cursor.getLong(0):"?");
            }
        }
        result.append("\n\nReferencias a revisar:");
        String[][] associations={
            {"Escuela: alumno o presentación inexistente","SELECT COUNT(*) FROM movements m LEFT JOIN students s ON s.id=m.student_id LEFT JOIN presentations p ON p.id=m.presentation_id WHERE s.id IS NULL OR p.id IS NULL"},
            {"Escuela: ayudante inexistente","SELECT COUNT(*) FROM movements m LEFT JOIN students s ON s.id=m.helper_id WHERE m.helper_id!=0 AND s.id IS NULL"},
            {"Vida y Ministerio: participante o parte inexistente","SELECT COUNT(*) FROM life_ministry_assignments a LEFT JOIN students s ON s.id=a.student_id LEFT JOIN life_ministry_parts p ON p.id=a.part_id WHERE s.id IS NULL OR p.id IS NULL"},
            {"Vida y Ministerio: lector inexistente","SELECT COUNT(*) FROM life_ministry_assignments a LEFT JOIN students s ON s.id=a.reader_id WHERE a.reader_id!=0 AND s.id IS NULL"},
            {"Predicación: publicador o campaña inexistente","SELECT COUNT(*) FROM preaching_publishers a LEFT JOIN students s ON s.id=a.student_id LEFT JOIN preaching_campaigns c ON c.id=a.campaign_id WHERE s.id IS NULL OR c.id IS NULL"},
            {"Predicación: capitán diario sin publicador o campaña","SELECT COUNT(*) FROM preaching_day_captains a LEFT JOIN preaching_campaigns c ON c.id=a.campaign_id WHERE c.id IS NULL OR (a.student_id!=0 AND NOT EXISTS (SELECT 1 FROM preaching_publishers p WHERE p.campaign_id=a.campaign_id AND p.day=a.day AND p.student_id=a.student_id))"},
            {"Predicación: transporte sin campaña","SELECT COUNT(*) FROM preaching_transport t LEFT JOIN preaching_campaigns c ON c.id=t.campaign_id WHERE c.id IS NULL"},
            {"Predicación: transportistas inexistentes","SELECT COUNT(*) FROM preaching_transport t LEFT JOIN students b ON b.id=t.bring_student_id LEFT JOIN students r ON r.id=t.collect_student_id WHERE (t.bring_student_id!=0 AND b.id IS NULL) OR (t.collect_student_id!=0 AND r.id IS NULL)"},
            {"Grupos: alumno con número sin catálogo","SELECT COUNT(*) FROM students s LEFT JOIN preaching_groups g ON g.group_number=s.group_number WHERE s.group_number>0 AND g.group_number IS NULL"},
            {"Grupos: capitán fuera de su grupo","SELECT COUNT(*) FROM preaching_groups g LEFT JOIN students s ON s.id=g.captain_student_id WHERE g.captain_student_id!=0 AND (s.id IS NULL OR s.group_number!=g.group_number)"},
            {"Grupos: auxiliar fuera de su grupo","SELECT COUNT(*) FROM preaching_groups g LEFT JOIN students s ON s.id=g.assistant_student_id WHERE g.assistant_student_id!=0 AND (s.id IS NULL OR s.group_number!=g.group_number)"},
            {"Grupos: capitán y auxiliar repetidos","SELECT COUNT(*) FROM preaching_groups WHERE captain_student_id!=0 AND captain_student_id=assistant_student_id"},
            {"Viajes: autobús sin viaje","SELECT COUNT(*) FROM transport_buses b LEFT JOIN transport_trips t ON t.id=b.trip_id WHERE t.id IS NULL"},
            {"Viajes: pasajero sin viaje","SELECT COUNT(*) FROM transport_passengers p LEFT JOIN transport_trips t ON t.id=p.trip_id WHERE t.id IS NULL"},
            {"Viajes: asiento sin autobús o pasajero","SELECT COUNT(*) FROM transport_seats s LEFT JOIN transport_buses b ON b.id=s.bus_id LEFT JOIN transport_passengers p ON p.id=s.passenger_id WHERE b.id IS NULL OR p.id IS NULL OR b.trip_id!=p.trip_id"},
            {"Viajes: capitán sin asiento en su autobús","SELECT COUNT(*) FROM transport_buses b WHERE b.captain_passenger_id!=0 AND NOT EXISTS (SELECT 1 FROM transport_seats s WHERE s.bus_id=b.id AND s.passenger_id=b.captain_passenger_id)"},
            {"Viajes: auxiliar sin asiento en su autobús","SELECT COUNT(*) FROM transport_buses b WHERE b.assistant_passenger_id!=0 AND NOT EXISTS (SELECT 1 FROM transport_seats s WHERE s.bus_id=b.id AND s.passenger_id=b.assistant_passenger_id)"},
            {"Viajes: capitán y auxiliar repetidos","SELECT COUNT(*) FROM transport_buses WHERE captain_passenger_id!=0 AND captain_passenger_id=assistant_passenger_id"}
        };
        for(String[] association:associations){
            try(Cursor cursor=sql.rawQuery(association[1],null)){
                result.append("\n").append(association[0]).append(": ").append(cursor.moveToFirst()?cursor.getLong(0):"?");
            }
        }
        return result.toString();
    }
}
