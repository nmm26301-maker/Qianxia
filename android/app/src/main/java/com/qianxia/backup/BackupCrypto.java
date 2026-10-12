package com.qianxia.backup;

import java.io.*;
import java.nio.ByteBuffer;
import java.security.*;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.*;

/** QXB v1: PBKDF2-SHA256 + independently authenticated AES-256-GCM chunks. */
public final class BackupCrypto {
    static final byte[] MAGIC = {'Q','X','B','A','C','K','0','1'};
    static final int CHUNK = 1024 * 1024, ITERATIONS = 210000, HEADER = 40;
    public interface Progress { void update(long done, long total) throws IOException; }
    private static SecretKey key(char[] password, byte[] salt, int rounds) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, rounds, 256);
        try { return new SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(), "AES"); }
        finally { spec.clearPassword(); }
    }
    private static byte[] crypt(int mode, SecretKey key, byte[] header, int counter, byte[] data, int length) throws Exception {
        byte[] nonce = ByteBuffer.allocate(12).put(header, 32, 8).putInt(counter).array();
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(mode, key, new GCMParameterSpec(128, nonce));
        c.updateAAD(header);
        c.updateAAD(ByteBuffer.allocate(8).putInt(counter).putInt(mode == Cipher.ENCRYPT_MODE ? length : length-16).array());
        return c.doFinal(data, 0, length);
    }
    public static void encrypt(File input, File output, char[] password, Progress progress) throws Exception {
        if (password.length < 8) throw new IOException("恢复密码至少需要 8 个字符");
        byte[] salt = new byte[16], prefix = new byte[8]; SecureRandom r = new SecureRandom();r.nextBytes(salt);r.nextBytes(prefix);
        byte[] header = ByteBuffer.allocate(HEADER).put(MAGIC).put(salt).putInt(ITERATIONS).putInt(CHUNK).put(prefix).array();
        SecretKey k = key(password, salt, ITERATIONS); boolean ok = false;
        try (InputStream in = new BufferedInputStream(new FileInputStream(input)); DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(output)))) {
            out.write(header); byte[] buffer = new byte[CHUNK]; int count = 0; long done = 0;
            while (true) {
                int n = 0, read;
                while (n < buffer.length && (read = in.read(buffer, n, buffer.length-n)) != -1) n += read;
                if (count == Integer.MAX_VALUE) throw new IOException("备份过大");
                // AAD authenticates the record index and plaintext length.
                byte[] sealed = crypt(Cipher.ENCRYPT_MODE, k, header, count++, buffer, n);
                out.writeInt(sealed.length);out.write(sealed);done += n;
                if (progress != null) progress.update(done, input.length());
                if (n == 0) break;
            }
            Arrays.fill(buffer, (byte)0);out.flush();ok = true;
        } finally { if(!ok) output.delete(); }
    }
    public static void decrypt(File input, File output, char[] password, Progress progress) throws Exception {
        boolean ok = false;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(input))); OutputStream out = new BufferedOutputStream(new FileOutputStream(output))) {
            byte[] header = new byte[HEADER];in.readFully(header);ByteBuffer b = ByteBuffer.wrap(header);
            byte[] magic = new byte[8];b.get(magic);if(!Arrays.equals(magic,MAGIC))throw new IOException("不是迁匣备份包，或版本不受支持");
            byte[] salt = new byte[16];b.get(salt);int rounds=b.getInt(),chunk=b.getInt();
            if(rounds != ITERATIONS || chunk != CHUNK)throw new IOException("备份头部损坏或版本不支持");
            SecretKey k=key(password,salt,rounds);int counter=0;long done=HEADER;
            while(true){
                int len=in.readInt();if(len<16||len>CHUNK+16)throw new IOException("备份块长度损坏");
                if(counter==Integer.MAX_VALUE)throw new IOException("备份过大");
                byte[] enc=new byte[len];in.readFully(enc);
                byte[] clear=crypt(Cipher.DECRYPT_MODE,k,header,counter++,enc,len);
                done+=4+len;
                if(clear.length==0){if(in.read()!=-1)throw new IOException("备份包含多余数据");break;}
                out.write(clear);Arrays.fill(clear,(byte)0);if(progress!=null)progress.update(done,input.length());
            }
            out.flush();ok=true;
        } catch (AEADBadTagException e) { throw new IOException("密码错误或备份已损坏", e); }
          catch (EOFException e) { throw new IOException("备份不完整，请重新下载", e); }
          finally { if(!ok)output.delete(); }
    }
}
