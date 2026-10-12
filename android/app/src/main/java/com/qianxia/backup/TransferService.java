package com.qianxia.backup;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.util.*;
import org.json.*;
public final class TransferService extends Service {
    private Thread worker;private boolean accepted=false;private long lastUpdate;
    @Override public IBinder onBind(Intent i){return null;}
    public static void start(Context c,String action,Intent extra){if(WorkState.busy)throw new IllegalStateException("已有任务进行中");Intent i=extra==null?new Intent():extra;i.setClass(c,TransferService.class).setAction(action);c.startForegroundService(i);}
    private Notification notice(String label,int p){Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);return new Notification.Builder(this,"transfer").setSmallIcon(R.drawable.ic_notify).setContentTitle("迁匣 · 备份与恢复").setContentText(label).setContentIntent(pi).setOngoing(true).setProgress(100,p,p==0).build();}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel("transfer","备份与恢复进度",NotificationManager.IMPORTANCE_LOW));
        startForeground(8,notice("准备任务",0));
        if(intent==null){stopForeground(true);stopSelf();return START_NOT_STICKY;}
        if(accepted)return START_NOT_STICKY;accepted=true;
        if(!WorkState.begin(this,"准备任务")){stopForeground(true);stopSelf();return START_NOT_STICKY;}
        worker=new Thread(()->runTask(intent),"qianxia-transfer");worker.start();return START_NOT_STICKY;
    }
    private void status(String label,int p)throws IOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("任务被系统停止，请重试");long now=System.currentTimeMillis();if(now-lastUpdate<350&&p!=100)return;lastUpdate=now;WorkState.update(this,label,p);((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(8,notice(label,p));}
    private File selected(Intent i)throws Exception{File f=new File(i.getStringExtra("path"));if(!f.getCanonicalPath().startsWith(getFilesDir().getCanonicalPath()+"/"))throw new IOException("文件位置无效");return f;}
    private void runTask(Intent i){
        try{
            String action=i.getAction();
            if("backup".equals(action)){
                File file=BackupEngine.backup(this,i.getStringExtra("password").toCharArray(),this::status);
                if(i.getBooleanExtra("upload",false)){
                    try{status("上传并校验网盘备份",0);Vault.dav(this).upload(file,(n,t)->status("上传或校验网盘备份",(int)(n*100/Math.max(1,t))));BackupEngine.markCloud(file);WorkState.finish(this,"备份完成，已上传网盘并校验",null);}
                    catch(Exception e){WorkState.finish(this,"本地备份已保存；网盘上传失败："+clean(e),null);}
                }else WorkState.finish(this,"本地备份完成，可以导出或上传网盘",null);
            }else if("upload".equals(action)){
                File f=selected(i);Vault.dav(this).upload(f,(n,t)->status("上传或校验网盘备份",(int)(n*100/Math.max(1,t))));BackupEngine.markCloud(f);WorkState.finish(this,"上传完成，云端校验通过",null);
            }else if("download".equals(action)){
                String name=i.getStringExtra("name");if(name==null||name.contains("/")||name.contains("\\")||!name.endsWith(".qxb"))throw new IOException("备份名称无效");
                File f=new File(BackupEngine.backups(this),name);Vault.dav(this).download(name,f,(n,t)->status("从网盘下载备份",t>0?(int)(n*100/t):0));
                JSONObject r=new JSONObject().put("file",name).put("created",System.currentTimeMillis()).put("bytes",f.length()).put("device","云端下载").put("cloud",true).put("status","已下载；等待解密校验");BackupEngine.writeJson(new File(f.getPath()+".json"),r);WorkState.finish(this,"下载完成，请在本地备份中输入密码恢复",null);
            }else if("restore".equals(action)){
                File dir=BackupEngine.restore(this,selected(i),i.getStringExtra("password").toCharArray(),this::status);WorkState.finish(this,"解密与校验完成，请安装应用并导入数据文件",dir.getAbsolutePath());
            }else if("import".equals(action)){
                File f=new File(BackupEngine.backups(this),"导入-"+System.currentTimeMillis()+"-"+UUID.randomUUID().toString().substring(0,6)+".qxb");File partial=new File(f.getPath()+".part");
                try(InputStream in=getContentResolver().openInputStream(Uri.parse(i.getStringExtra("uri")));OutputStream out=new FileOutputStream(partial)){if(in==null)throw new IOException("无法读取文件");FileOps.copy(in,out,FileOps.MAX_BYTES,(n,t)->status("导入备份 "+MainActivity.size(n),0));}
                catch(Exception e){partial.delete();throw e;}
                if(!partial.renameTo(f))throw new IOException("无法保存导入文件");
                JSONObject r=new JSONObject().put("file",f.getName()).put("created",System.currentTimeMillis()).put("bytes",f.length()).put("device","本地导入").put("cloud",false).put("status","已导入；等待解密校验");BackupEngine.writeJson(new File(f.getPath()+".json"),r);WorkState.finish(this,"导入完成，请输入密码恢复",null);
            }else if("export".equals(action)){
                File f=selected(i);try(InputStream in=new FileInputStream(f);OutputStream out=getContentResolver().openOutputStream(Uri.parse(i.getStringExtra("uri")),"wt")){if(out==null)throw new IOException("无法写入目标位置");FileOps.copy(in,out,FileOps.MAX_BYTES,(n,t)->status("保存文件",(int)(n*100/Math.max(1,f.length()))));}WorkState.finish(this,"文件已保存到你选择的位置",null);
            }else throw new IOException("任务类型无效");
        }catch(Exception e){WorkState.finish(this,"任务失败："+clean(e),null);}
        finally{stopForeground(true);stopSelf();}
    }
    static String clean(Exception e){String msg=e.getMessage();return msg==null?"请检查权限、空间、网络和恢复密码":msg.length()>180?msg.substring(0,180):msg;}
    @Override public void onTimeout(int startId,int type){if(worker!=null)worker.interrupt();stopSelf();}
    @Override public void onDestroy(){if(worker!=null&&worker.isAlive())worker.interrupt();super.onDestroy();}
}
