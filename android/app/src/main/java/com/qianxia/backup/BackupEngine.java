package com.qianxia.backup;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.Build;
import org.json.*;
import java.io.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;

public final class BackupEngine {
    public interface Status {void say(String s,int percent)throws IOException;}
    public static File backups(Context c){File f=new File(c.getFilesDir(),"backups");f.mkdirs();return f;}
    public static File restores(Context c){File f=new File(c.getFilesDir(),"restores");f.mkdirs();return f;}
    public static void writeJson(File f,JSONObject value)throws Exception{File tmp=new File(f.getPath()+".tmp");try(FileOutputStream out=new FileOutputStream(tmp)){out.write(value.toString(2).getBytes("UTF-8"));out.getFD().sync();}if(!tmp.renameTo(f))throw new IOException("无法保存备份记录");}
    public static JSONObject readJson(File f)throws Exception{if(!f.isFile()||f.length()>2*1024*1024)throw new IOException("备份清单无效");try(InputStream in=new FileInputStream(f)){ByteArrayOutputStream b=new ByteArrayOutputStream();FileOps.copy(in,b,2*1024*1024,null);return new JSONObject(new String(b.toByteArray(),"UTF-8"));}}
    public static File backup(Context c,char[] password,Status status)throws Exception{
        Set<String> selected=new TreeSet<>(Vault.prefs(c).getStringSet("packages",new HashSet<>()));
        JSONArray docs=new JSONArray(Vault.prefs(c).getString("documents","[]"));
        if(selected.isEmpty()&&docs.length()==0)throw new IOException("请先选择应用或添加数据文件");
        String id=new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(new Date())+"-"+UUID.randomUUID().toString().substring(0,8);
        File zip=File.createTempFile("backup-",".zip",c.getCacheDir());File encrypted=new File(backups(c),"迁匣-"+id+".qxb"),partial=new File(encrypted.getPath()+".part");
        JSONObject manifest=new JSONObject();JSONArray files=new JSONArray(),apps=new JSONArray();manifest.put("format",1).put("created",System.currentTimeMillis()).put("device",Build.MANUFACTURER+" "+Build.MODEL).put("scope","apk-and-exported-files").put("files",files).put("apps",apps);
        boolean ok=false;
        try{
            int total=selected.size()+docs.length(),done=0;
            try(ZipOutputStream out=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zip)))){
                out.setLevel(1);
                for(String pkg:selected){
                    PackageInfo info=c.getPackageManager().getPackageInfo(pkg,PackageManager.GET_SIGNING_CERTIFICATES);
                    if((info.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)!=0)throw new IOException("暂不备份系统应用："+pkg);
                    status.say("保存安装包："+info.applicationInfo.loadLabel(c.getPackageManager()),(done*45)/Math.max(1,total));
                    JSONObject app=new JSONObject().put("package",pkg).put("name",info.applicationInfo.loadLabel(c.getPackageManager()).toString()).put("version",info.versionName==null?"":info.versionName).put("versionCode",info.getLongVersionCode());
                    JSONArray paths=new JSONArray();String base="apps/"+pkg+"/base.apk";
                    try(InputStream in=new FileInputStream(info.applicationInfo.sourceDir)){add(out,in,base,"apk",pkg,"base.apk",files);}
                    paths.put(base);
                    String[] splits=info.applicationInfo.splitSourceDirs;
                    if(splits!=null)for(int i=0;i<splits.length;i++){String path="apps/"+pkg+"/split-"+i+".apk";try(InputStream in=new FileInputStream(splits[i])){add(out,in,path,"apk",pkg,"split-"+i+".apk",files);}paths.put(path);}
                    app.put("apks",paths);apps.put(app);done++;
                }
                for(int i=0;i<docs.length();i++){
                    JSONObject d=docs.getJSONObject(i);status.say("保存数据文件："+d.getString("name"),(done*45)/Math.max(1,total));
                    try(InputStream in=c.getContentResolver().openInputStream(Uri.parse(d.getString("uri")))){if(in==null)throw new IOException("数据文件授权已失效，请重新添加");add(out,in,"exports/"+i+".data","export",d.optString("package",""),d.getString("name"),files);}done++;
                }
                out.putNextEntry(new ZipEntry("manifest.json"));out.write(manifest.toString().getBytes("UTF-8"));out.closeEntry();
            }
            status.say("正在加密备份",48);BackupCrypto.encrypt(zip,partial,password,(n,t)->status.say("正在加密备份",48+(int)(n*45/Math.max(1,t))));
            if(!partial.renameTo(encrypted))throw new IOException("备份保存失败");
            JSONObject record=new JSONObject().put("file",encrypted.getName()).put("created",manifest.getLong("created")).put("device",manifest.getString("device")).put("bytes",encrypted.length()).put("apps",apps.length()).put("exports",docs.length()).put("cloud",false).put("status","本地备份完成");
            writeJson(new File(encrypted.getPath()+".json"),record);ok=true;status.say("本地备份完成",100);return encrypted;
        }finally{zip.delete();partial.delete();Arrays.fill(password,'\0');if(!ok){encrypted.delete();new File(encrypted.getPath()+".json").delete();}}
    }
    private static void add(ZipOutputStream out,InputStream in,String path,String kind,String pkg,String original,JSONArray files)throws Exception{
        out.putNextEntry(new ZipEntry(path));MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] b=new byte[65536];long size=0;int n;
        while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("备份已停止");size+=n;if(size>FileOps.MAX_BYTES)throw new IOException("文件过大");out.write(b,0,n);digest.update(b,0,n);}out.closeEntry();
        files.put(new JSONObject().put("path",path).put("kind",kind).put("package",pkg).put("name",original).put("bytes",size).put("sha256",FileOps.hex(digest.digest())));
    }
    public static File restore(Context c,File file,char[] password,Status status)throws Exception{
        File zip=File.createTempFile("restore-",".zip",c.getCacheDir());File dir=new File(restores(c),UUID.randomUUID().toString());boolean ok=false;
        try{status.say("验证密码与备份完整性",0);BackupCrypto.decrypt(file,zip,password,(n,t)->status.say("解密备份",(int)(n*55/Math.max(1,t))));status.say("展开备份",56);FileOps.unpack(zip,dir);verify(dir,status);ok=true;return dir;}
        finally{Arrays.fill(password,'\0');zip.delete();if(!ok)FileOps.delete(dir);}
    }
    public static JSONObject verify(File dir,Status status)throws Exception{
        JSONObject m=readJson(new File(dir,"manifest.json"));if(m.getInt("format")!=1||!m.getString("scope").equals("apk-and-exported-files"))throw new IOException("备份类型不支持");
        JSONArray files=m.getJSONArray("files"),apps=m.getJSONArray("apps");if(files.length()>10000||apps.length()>1000)throw new IOException("备份清单过大");Set<String>paths=new HashSet<>();Set<String>apkPaths=new HashSet<>();String root=dir.getCanonicalPath()+"/";
        for(int i=0;i<files.length();i++){JSONObject f=files.getJSONObject(i);String path=f.getString("path");String kind=f.getString("kind");
            if(!paths.add(path)||!(path.matches("apps/[a-zA-Z0-9_.]+/(base|split-[0-9]+)\\.apk")&&kind.equals("apk")||path.matches("exports/[0-9]+\\.data")&&kind.equals("export")))throw new IOException("备份清单路径非法");
            File local=new File(dir,path);if(!local.getCanonicalPath().startsWith(root)||!local.isFile()||local.length()!=f.getLong("bytes")||!FileOps.sha(local).equals(f.getString("sha256")))throw new IOException("文件校验失败："+f.optString("name"));
            if(kind.equals("apk"))apkPaths.add(path);status.say("校验文件 "+(i+1)+" / "+files.length(),60+(int)((i+1)*40/Math.max(1,files.length())));
        }
        Set<String>claimed=new HashSet<>(),packages=new HashSet<>();
        for(int i=0;i<apps.length();i++){JSONObject app=apps.getJSONObject(i);String pkg=app.getString("package");if(!pkg.matches("[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+")||!packages.add(pkg))throw new IOException("应用包名非法");JSONArray apks=app.getJSONArray("apks");if(apks.length()==0||!apks.getString(0).equals("apps/"+pkg+"/base.apk"))throw new IOException("缺少应用基础安装包");for(int j=0;j<apks.length();j++){String p=apks.getString(j);if(!p.startsWith("apps/"+pkg+"/")||!apkPaths.contains(p)||!claimed.add(p))throw new IOException("安装包清单异常");}}
        if(!claimed.equals(apkPaths))throw new IOException("安装包清单不完整");return m;
    }
    public static void markCloud(File file)throws Exception{File rec=new File(file.getPath()+".json");JSONObject r=readJson(rec);r.put("cloud",true).put("status","已上传并校验");writeJson(rec,r);}
}
