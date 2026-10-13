package com.hgikvt.fizak;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class ApiKeyStore {
    private static final String ALIAS="FizAkGeminiKeyV1", PREF="fizak_secure", VALUE="gemini_key";
    private ApiKeyStore() {}
    public static boolean has(Context c){return !c.getSharedPreferences(PREF,0).getString(VALUE,"").isEmpty();}
    public static void save(Context c,String clear) throws Exception {
        if(clear==null||clear.trim().isEmpty())throw new IllegalArgumentException("Вставь API-ключ");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,getKey());byte[] enc=cipher.doFinal(clear.trim().getBytes("UTF-8"));
        ByteBuffer b=ByteBuffer.allocate(cipher.getIV().length+enc.length);b.put(cipher.getIV());b.put(enc);
        c.getSharedPreferences(PREF,0).edit().putString(VALUE,Base64.encodeToString(b.array(),Base64.NO_WRAP)).apply();
    }
    public static String get(Context c) throws Exception {
        String stored=c.getSharedPreferences(PREF,0).getString(VALUE,"");if(stored.isEmpty())return "";byte[] all=Base64.decode(stored,Base64.NO_WRAP);ByteBuffer b=ByteBuffer.wrap(all);byte[] iv=new byte[12];b.get(iv);byte[] enc=new byte[b.remaining()];b.get(enc);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,getKey(),new GCMParameterSpec(128,iv));return new String(cipher.doFinal(enc),"UTF-8");
    }
    public static void clear(Context c){c.getSharedPreferences(PREF,0).edit().remove(VALUE).apply();try{KeyStore k=KeyStore.getInstance("AndroidKeyStore");k.load(null);k.deleteEntry(ALIAS);}catch(Exception ignored){}}
    private static SecretKey getKey() throws Exception {KeyStore k=KeyStore.getInstance("AndroidKeyStore");k.load(null);if(k.containsAlias(ALIAS))return (SecretKey)k.getKey(ALIAS,null);KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());return g.generateKey();}
}
