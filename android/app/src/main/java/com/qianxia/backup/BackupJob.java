package com.qianxia.backup;
import android.app.job.*;
import android.content.*;
import java.io.*;
import java.util.Arrays;
public final class BackupJob extends JobService {
    private Thread worker;
    public static void enable(Context c)throws Exception{
        if(Vault.get(c,"auto_password").length()<8)throw new IOException("请先设置自动备份恢复密码");
        JobInfo j=new JobInfo.Builder(21,new ComponentName(c,BackupJob.class)).setPeriodic(24*60*60*1000L).setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED).setRequiresCharging(true).setPersisted(true).build();
        if(((JobScheduler)c.getSystemService(JOB_SCHEDULER_SERVICE)).schedule(j)!=JobScheduler.RESULT_SUCCESS)throw new IOException("系统未接受自动备份计划");Vault.prefs(c).edit().putBoolean("auto_enabled",true).apply();
    }
    public static void disable(Context c){((JobScheduler)c.getSystemService(JOB_SCHEDULER_SERVICE)).cancel(21);Vault.prefs(c).edit().putBoolean("auto_enabled",false).remove("auto_password").remove("auto_pending").apply();}
    @Override public boolean onStartJob(JobParameters p){if(!WorkState.begin(this,"自动备份准备中"))return false;
        worker=new Thread(()->{boolean retry=false;File file=null;char[]pass=null;try{
            pass=Vault.get(this,"auto_password").toCharArray();String pending=Vault.prefs(this).getString("auto_pending","");File candidate=new File(BackupEngine.backups(this),pending);if(!pending.isEmpty()&&!pending.contains("/")&&candidate.isFile())file=candidate;else file=BackupEngine.backup(this,pass,(s,n)->{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("自动备份等待下次执行");WorkState.update(this,s,n);});Vault.prefs(this).edit().putString("auto_pending",file.getName()).commit();
            Vault.dav(this).upload(file,(n,t)->{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();WorkState.update(this,"自动上传与校验",(int)(n*100/Math.max(1,t)));});BackupEngine.markCloud(file);Vault.prefs(this).edit().remove("auto_pending").commit();WorkState.finish(this,"自动备份完成，已上传并校验",null);
        }catch(Exception e){retry=true;WorkState.finish(this,(file==null?"自动备份失败：":"本地备份已保存；自动上传失败：")+TransferService.clean(e),null);}finally{if(pass!=null)Arrays.fill(pass,'\0');jobFinished(p,retry);}},"qianxia-auto");worker.start();return true;}
    @Override public boolean onStopJob(JobParameters p){if(worker!=null)worker.interrupt();return true;}
}
