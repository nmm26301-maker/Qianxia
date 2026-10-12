package com.qianxia.backup;
import android.content.*;
import android.content.pm.PackageInstaller;
import java.io.*;
import org.json.*;
public final class InstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        int status=i.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
        if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){Intent confirm=i.getParcelableExtra(Intent.EXTRA_INTENT);if(confirm!=null){confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);try{c.startActivity(confirm);}catch(Exception e){WorkState.finish(c,"请保持迁匣打开，再次点击安装完成系统确认",null);}}return;}
        String pkg=i.getStringExtra("package_name"),path=i.getStringExtra("restore_dir");String result=status==PackageInstaller.STATUS_SUCCESS?"安装完成 · 数据文件需另行导入":"安装未完成："+i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        try{File dir=new File(path);if(!dir.getCanonicalPath().startsWith(BackupEngine.restores(c).getCanonicalPath()+"/"))return;File f=new File(dir,"installation.json");JSONObject r=f.exists()?BackupEngine.readJson(f):new JSONObject();r.put(pkg,result);BackupEngine.writeJson(f,r);}catch(Exception ignored){}
        WorkState.finish(c,(status==PackageInstaller.STATUS_SUCCESS?"安装完成：":"安装失败：")+pkg,null);
    }
}
