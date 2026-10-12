package com.qianxia.backup;
public final class App extends android.app.Application {
    @Override public void onCreate(){super.onCreate();okhttp3.OkHttp.INSTANCE.initialize(this);
        WorkState.text=Vault.prefs(this).getString("task_status","");WorkState.resultDir=Vault.prefs(this).getString("restore_dir","");
        java.io.File[] cached=getCacheDir().listFiles();if(cached!=null)for(java.io.File f:cached)if((f.getName().startsWith("backup-")||f.getName().startsWith("restore-"))&&f.getName().endsWith(".zip"))f.delete();
        if(!WorkState.text.isEmpty()&&!WorkState.text.contains("完成")&&!WorkState.text.contains("失败")&&!WorkState.text.contains("已保存"))WorkState.text="上次任务已结束或中断，请查看本地记录后重试";
    }
}
