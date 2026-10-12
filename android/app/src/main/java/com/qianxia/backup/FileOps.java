package com.qianxia.backup;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
public final class FileOps {
    public static final long MAX_BYTES=64L*1024*1024*1024;
    public static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(java.util.Locale.US,"%02x",b&255));return s.toString();}
    public static String sha(File f)throws Exception {MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}return hex(d.digest());}
    public static long copy(InputStream in,OutputStream out,long max,BackupCrypto.Progress p)throws IOException{byte[] b=new byte[65536];int n;long done=0;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("任务已停止");done+=n;if(done>max)throw new IOException("文件超出大小限制");out.write(b,0,n);if(p!=null)p.update(done,max);}return done;}
    public static void delete(File f){if(f.isDirectory()){File[] children=f.listFiles();if(children!=null)for(File c:children)delete(c);}f.delete();}
    public static void unpack(File zip,File dir)throws IOException{
        if(!dir.mkdirs()&&!dir.isDirectory())throw new IOException("无法创建恢复目录");
        String root=dir.getCanonicalPath()+File.separator;long total=0;Set<String>seen=new HashSet<>();int count=0;boolean success=false;
        try(ZipInputStream in=new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))){
            ZipEntry entry;while((entry=in.getNextEntry())!=null){
                String name=entry.getName();if(++count>10000||name.contains("\\")||name.startsWith("/")||name.contains("\u0000")||!seen.add(name))throw new IOException("备份目录异常");
                File f=new File(dir,name);if(!f.getCanonicalPath().startsWith(root))throw new IOException("备份包含非法路径");
                if(entry.isDirectory()){if(!f.mkdirs()&&!f.isDirectory())throw new IOException("目录创建失败");continue;}
                if(!f.getParentFile().mkdirs()&&!f.getParentFile().isDirectory())throw new IOException("目录创建失败");
                try(OutputStream out=new BufferedOutputStream(new FileOutputStream(f))){total+=copy(in,out,MAX_BYTES-total,null);}
                in.closeEntry();
            }success=true;
        }finally{if(!success)delete(dir);}
    }
}
