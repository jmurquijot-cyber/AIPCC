package mx.escuelaministerio.app;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;

/** Runs on the host JVM without modifying a user's database. */
public final class SupportCryptoSmokeTest {
    public static void main(String[] args) throws Exception {
        Method method=SupportMaintenance.class.getDeclaredMethod("cipher",int.class,char[].class,byte[].class,byte[].class);
        method.setAccessible(true);
        byte[] salt=new byte[16],iv=new byte[12];new SecureRandom().nextBytes(salt);new SecureRandom().nextBytes(iv);
        char[] correct="frase-de-respaldo-segura".toCharArray(),wrong="clave-que-no-coincide".toCharArray();
        byte[] original="SQLite format 3\0demo".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted=((Cipher)method.invoke(null,Cipher.ENCRYPT_MODE,correct,salt,iv)).doFinal(original);
        byte[] restored=((Cipher)method.invoke(null,Cipher.DECRYPT_MODE,correct,salt,iv)).doFinal(encrypted);
        if(!Arrays.equals(original,restored))throw new AssertionError("Respaldo cifrado no se puede descifrar.");
        boolean rejected=false;
        try{((Cipher)method.invoke(null,Cipher.DECRYPT_MODE,wrong,salt,iv)).doFinal(encrypted);}catch(Exception expected){rejected=true;}
        if(!rejected)throw new AssertionError("Se aceptó una clave incorrecta.");
        boolean firstRejected=false;
        try{SupportAuth.validateNewPassword("admin","root".toCharArray());}catch(IllegalArgumentException expected){firstRejected=true;}
        if(!firstRejected)throw new AssertionError("La contraseña inicial se puede reutilizar.");
        SupportAuth.validateNewPassword("admin","nueva-clave-segura".toCharArray());
        Arrays.fill(correct,'\0');Arrays.fill(wrong,'\0');
        System.out.println("AES-GCM y prohibición de reutilizar root: correctos.");
    }
}
