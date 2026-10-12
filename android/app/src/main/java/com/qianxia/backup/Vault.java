package com.qianxia.backup;
import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
public final class Vault {
    public static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("qianxia",Context.MODE_PRIVATE);}
    private static SecretKey key()throws Exception{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(!ks.containsAlias("qianxia-local")){KeyGenerator g=KeyGenerator.getInstance("AES","AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder("qianxia-local",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());g.generateKey();}return (SecretKey)ks.getKey("qianxia-local",null);}
    public static void put(Context c,String name,String value)throws Exception{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());cipher.updateAAD(name.getBytes("UTF-8"));String iv=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP);String data=Base64.encodeToString(cipher.doFinal(value.getBytes("UTF-8")),Base64.NO_WRAP);if(!prefs(c).edit().putString(name,iv+":"+data).commit())throw new java.io.IOException("凭证保存失败");}
    public static String get(Context c,String name)throws Exception{String raw=prefs(c).getString(name,"");if(raw.isEmpty())return "";String[] bits=raw.split(":",2);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(bits[0],Base64.NO_WRAP)));cipher.updateAAD(name.getBytes("UTF-8"));return new String(cipher.doFinal(Base64.decode(bits[1],Base64.NO_WRAP)),"UTF-8");}
    public static DavClient dav(Context c)throws Exception{String address=prefs(c).getString("dav_url","");if(address.isEmpty())throw new java.io.IOException("请先在网盘页面连接 WebDAV");return new DavClient(address,prefs(c).getString("dav_user",""),get(c,"dav_secret"),prefs(c).getBoolean("allow_http",false));}
}
