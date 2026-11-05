package cn.timepocket.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.util.HashSet;
import java.util.Set;

/** Identify home-screen providers, not ordinary apps with a launcher icon. */
final class HomeApps {
    static Set<String> packages(Context context){
        PackageManager pm=context.getPackageManager();Set<String> result=new HashSet<>();
        Intent home=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for(ResolveInfo info:pm.queryIntentActivities(home,0))if(info.activityInfo!=null)result.add(info.activityInfo.packageName);
        ResolveInfo current=pm.resolveActivity(home,PackageManager.MATCH_DEFAULT_ONLY);
        if(current!=null&&current.activityInfo!=null&&!"android".equals(current.activityInfo.packageName))result.add(current.activityInfo.packageName);
        return result;
    }
}
