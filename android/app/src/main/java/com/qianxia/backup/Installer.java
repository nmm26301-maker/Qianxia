package com.qianxia.backup;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import org.json.*;
import java.io.*;
public final class Installer {
    public static void install(Context c,File dir,JSONObject app)throws Exception{
        PackageManager pm=c.getPackageManager();if(!pm.canRequestPackageInstalls())throw new IOException("请先允许迁匣安装未知来源应用");
        String pkg=app.getString("package");JSONArray apks=app.getJSONArray("apks");File base=new File(dir,apks.getString(0));
        PackageInfo parsed=pm.getPackageArchiveInfo(base.getPath(),0);if(parsed==null||!pkg.equals(parsed.packageName))throw new IOException("安装包与备份清单不一致");
        PackageInstaller installer=pm.getPackageInstaller();PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);params.setAppPackageName(pkg);
        long total=0;for(int i=0;i<apks.length();i++)total+=new File(dir,apks.getString(i)).length();params.setSize(total);int id=installer.createSession(params);boolean committed=false;
        try(PackageInstaller.Session session=installer.openSession(id)){
            for(int i=0;i<apks.length();i++){File f=new File(dir,apks.getString(i));try(InputStream in=new FileInputStream(f);OutputStream out=session.openWrite(f.getName(),0,f.length())){FileOps.copy(in,out,FileOps.MAX_BYTES,null);session.fsync(out);}}
            Intent callback=new Intent(c,InstallReceiver.class).setAction("com.qianxia.backup.INSTALL_RESULT").putExtra("restore_dir",dir.getAbsolutePath()).putExtra("package_name",pkg);
            PendingIntent pi=PendingIntent.getBroadcast(c,id,callback,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE);
            session.commit(pi.getIntentSender());committed=true;
        }finally{if(!committed)installer.abandonSession(id);}
    }
}
