package cn.timepocket.app;

import android.content.*;
import android.util.Log;

public final class PackageReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent) {
        if(intent.getBooleanExtra(Intent.EXTRA_REPLACING,false)||intent.getData()==null) return;
        String pkg=intent.getData().getSchemeSpecificPart();
        String action=intent.getAction();long at=System.currentTimeMillis();
        PendingResult result=goAsync();
        PocketApp.IO.execute(()-> {
            try {
                if (Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(action)) {
                    Collector.removed(c, pkg, at);
                    Collector.collectAfterRemoval(c);
                } else if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
                    Collector.added(c, pkg, at);
                }
            } catch (RuntimeException error) {
                Log.w("TimePocket", "Could not finish recording a package change", error);
            } finally {result.finish();}
        });
    }
}
