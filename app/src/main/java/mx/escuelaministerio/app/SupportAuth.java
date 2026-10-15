package mx.escuelaministerio.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Credentials stay in the backed-up SQLite database; no plaintext password is stored. */
final class SupportAuth {
    private static final int ROUNDS=150_000;
    private static final long LOCK_MS=60_000;
    private final MinisterioDb helper;

    static final class User {
        final String username;
        final boolean mustChange;
        User(String username,boolean mustChange){this.username=username;this.mustChange=mustChange;}
    }

    SupportAuth(MinisterioDb helper){this.helper=helper;}

    static void createTable(SQLiteDatabase sql){
        sql.execSQL("CREATE TABLE IF NOT EXISTS support_users (username TEXT PRIMARY KEY COLLATE NOCASE, salt TEXT NOT NULL, password_hash TEXT NOT NULL, rounds INTEGER NOT NULL, must_change INTEGER NOT NULL DEFAULT 0, failed_attempts INTEGER NOT NULL DEFAULT 0, blocked_until INTEGER NOT NULL DEFAULT 0)");
    }

    static void initialize(SQLiteDatabase sql){
        createTable(sql);
        try(Cursor cursor=sql.rawQuery("SELECT COUNT(*) FROM support_users",null)){
            if(cursor.moveToFirst()&&cursor.getInt(0)>0)return;
        }
        char[] first="root".toCharArray();
        try{sql.insertOrThrow("support_users",null,credentials("admin",first,true));}
        finally{Arrays.fill(first,'\0');}
    }

    private static byte[] derive(char[] password,byte[] salt,int rounds){
        PBEKeySpec spec=new PBEKeySpec(password,salt,rounds,256);
        try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}
        catch(Exception error){throw new IllegalStateException("No se pudo proteger la contraseña.",error);}
        finally{spec.clearPassword();}
    }

    static ContentValues credentials(String username,char[] password,boolean mustChange){
        byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);
        byte[] hash=derive(password,salt,ROUNDS);
        try{
            ContentValues values=new ContentValues();values.put("username",username);values.put("salt",Base64.encodeToString(salt,Base64.NO_WRAP));values.put("password_hash",Base64.encodeToString(hash,Base64.NO_WRAP));values.put("rounds",ROUNDS);values.put("must_change",mustChange?1:0);values.put("failed_attempts",0);values.put("blocked_until",0L);return values;
        }finally{Arrays.fill(hash,(byte)0);Arrays.fill(salt,(byte)0);}
    }

    private static String cleanName(String username){
        String normalized=username==null?"":username.trim().toLowerCase(Locale.ROOT);
        if(!normalized.matches("[a-z0-9._-]{3,32}"))throw new IllegalArgumentException("Usuario: 3 a 32 caracteres; use letras, números, punto, guion o guion bajo.");
        return normalized;
    }

    static void validateNewPassword(String username,char[] password){
        if(password.length<8)throw new IllegalArgumentException("La nueva contraseña debe tener al menos 8 caracteres.");
        String value=new String(password);
        if(value.equalsIgnoreCase("root")||value.equalsIgnoreCase(username))throw new IllegalArgumentException("No use la contraseña inicial ni el nombre de usuario.");
    }

    User login(String username,char[] password){
        String name;try{name=cleanName(username);}catch(IllegalArgumentException error){return null;}
        SQLiteDatabase sql=helper.getWritableDatabase();
        try(Cursor cursor=sql.query("support_users",new String[]{"salt","password_hash","rounds","must_change","failed_attempts","blocked_until"},"username=?",new String[]{name},null,null,null)){
            if(!cursor.moveToFirst())return null;
            long until=cursor.getLong(5);
            if(until>System.currentTimeMillis())throw new IllegalStateException("Demasiados intentos. Espere un minuto para volver a ingresar.");
            byte[] salt=Base64.decode(cursor.getString(0),Base64.NO_WRAP);
            byte[] stored=Base64.decode(cursor.getString(1),Base64.NO_WRAP);
            byte[] candidate=derive(password,salt,cursor.getInt(2));
            boolean valid=MessageDigest.isEqual(stored,candidate);
            Arrays.fill(candidate,(byte)0);Arrays.fill(stored,(byte)0);Arrays.fill(salt,(byte)0);
            if(!valid){int failed=cursor.getInt(4)+1;ContentValues update=new ContentValues();update.put("failed_attempts",failed>=5?0:failed);update.put("blocked_until",failed>=5?System.currentTimeMillis()+LOCK_MS:0);sql.update("support_users",update,"username=?",new String[]{name});return null;}
            ContentValues cleared=new ContentValues();cleared.put("failed_attempts",0);cleared.put("blocked_until",0);sql.update("support_users",cleared,"username=?",new String[]{name});
            return new User(name,cursor.getInt(3)!=0);
        }
    }

    void addUser(String username,char[] password){
        String name=cleanName(username);validateNewPassword(name,password);
        if(helper.getWritableDatabase().insertWithOnConflict("support_users",null,credentials(name,password,true),SQLiteDatabase.CONFLICT_IGNORE)<0)
            throw new IllegalArgumentException("Ese usuario ya existe.");
    }

    void setPassword(String username,char[] newPassword,boolean requireChange){
        String name=cleanName(username);validateNewPassword(name,newPassword);
        ContentValues values=credentials(name,newPassword,requireChange);values.remove("username");
        if(helper.getWritableDatabase().update("support_users",values,"username=?",new String[]{name})!=1)throw new IllegalArgumentException("Usuario no encontrado.");
    }

    String renameUser(String previous,String requested){
        String old=cleanName(previous),fresh=cleanName(requested);
        if(old.equals(fresh))throw new IllegalArgumentException("Escriba un nombre de usuario diferente.");
        SQLiteDatabase sql=helper.getWritableDatabase();
        ContentValues values=new ContentValues();values.put("username",fresh);
        try{if(sql.update("support_users",values,"username=?",new String[]{old})!=1)throw new IllegalArgumentException("Usuario no encontrado.");}
        catch(android.database.sqlite.SQLiteConstraintException error){throw new IllegalArgumentException("Ese nombre de usuario ya existe.",error);}
        return fresh;
    }

    List<String> usernames(){
        List<String> result=new ArrayList<>();
        try(Cursor cursor=helper.getReadableDatabase().rawQuery("SELECT username FROM support_users ORDER BY username",null)){
            while(cursor.moveToNext())result.add(cursor.getString(0));
        }
        return result;
    }

    List<ContentValues> accountSnapshot(){
        List<ContentValues> result=new ArrayList<>();
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT username,salt,password_hash,rounds,must_change,failed_attempts,blocked_until FROM support_users",null)){
            while(c.moveToNext()){
                ContentValues values=new ContentValues();values.put("username",c.getString(0));values.put("salt",c.getString(1));values.put("password_hash",c.getString(2));values.put("rounds",c.getInt(3));values.put("must_change",c.getInt(4));values.put("failed_attempts",0);values.put("blocked_until",0L);result.add(values);
            }
        }
        return result;
    }

    static void preserveAccounts(SQLiteDatabase target,List<ContentValues> accounts){
        if(accounts.isEmpty())throw new IllegalStateException("No se pueden restaurar datos sin un usuario de SOPORTE activo.");
        createTable(target);
        target.beginTransaction();
        try{
            target.delete("support_users",null,null);
            for(ContentValues account:accounts)target.insertOrThrow("support_users",null,account);
            target.setTransactionSuccessful();
        }finally{target.endTransaction();}
    }
}
