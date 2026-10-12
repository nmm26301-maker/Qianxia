package com.qianxia.backup;
import android.content.Context;
public final class WorkState {
    public static volatile boolean busy=false;
    public static volatile String text="",resultDir="";
    public static volatile int percent=0;
    public static synchronized boolean begin(Context c,String label){if(busy)return false;busy=true;text=label;percent=0;save(c);return true;}
    public static synchronized void update(Context c,String label,int value){text=label;percent=Math.max(0,Math.min(100,value));save(c);}
    public static synchronized void finish(Context c,String label,String restored){text=label;percent=100;busy=false;if(restored!=null)resultDir=restored;save(c);}
    private static void save(Context c){Vault.prefs(c).edit().putString("task_status",text).putString("restore_dir",resultDir).apply();}
}
