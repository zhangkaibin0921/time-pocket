package cn.timepocket.app;

import android.app.Application;
import android.app.job.*;
import android.content.ComponentName;
import android.content.*;
import android.os.Build;
import java.util.concurrent.*;

public final class PocketApp extends Application {
    public static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private final PackageReceiver packages=new PackageReceiver();
    @Override public void onCreate() {
        super.onCreate();
        IntentFilter filter=new IntentFilter(Intent.ACTION_PACKAGE_ADDED);filter.addDataScheme("package");
        if(Build.VERSION.SDK_INT>=33)registerReceiver(packages,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(packages,filter);
        schedule();
    }
    private void schedule() {
        JobScheduler scheduler=getSystemService(JobScheduler.class);
        JobInfo existing=scheduler.getPendingJob(7101);
        if(existing==null||existing.getIntervalMillis()!=15*60*1000L)
            scheduler.schedule(new JobInfo.Builder(7101,new ComponentName(this,CollectJob.class))
                    .setPeriodic(15*60*1000L,5*60*1000L).setPersisted(true).build());
    }
}
