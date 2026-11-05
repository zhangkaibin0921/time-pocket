package cn.timepocket.app;

import android.app.AppOpsManager;
import android.app.usage.*;
import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import org.json.*;
import java.io.*;
import java.util.*;

public final class Collector {
    private static final long WINDOW=2*24*60*60*1000L;
    public static boolean allowed(Context c) {
        AppOpsManager ops=(AppOpsManager)c.getSystemService(Context.APP_OPS_SERVICE);
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),c.getPackageName())==AppOpsManager.MODE_ALLOWED;
    }
    public static synchronized void sync(Context context) throws Exception {
        Context c=context.getApplicationContext();
        if (!c.getSystemService(UserManager.class).isUserUnlocked()) return;
        PocketDb db=PocketDb.get(c);
        long now=System.currentTimeMillis(), end=now-5000;
        SQLiteDatabase sql=db.getWritableDatabase();
        if (!allowed(c)) {
            long last=db.number("cursor");
            sql.beginTransaction();
            try {
                if(last>0) db.gap(last,now,"使用情况访问权限未开启");
                db.put("state","{}");
                if(last>0) db.put("cursor",now);
                db.put("permission_lost",1);
                sql.setTransactionSuccessful();
            } finally {sql.endTransaction();}
            inventory(c,now);
            return;
        }
        long last=db.number("cursor");
        if(last>end+60000) {
            db.gap(end,last,"系统时间回退，暂停采集直到时间恢复，避免重复累计");
            inventory(c,now);return;
        }
        if(last>=end) {inventory(c,now);return;}
        // Overlapping reads repair late-arriving events. Keep our own raw events even
        // if Android later removes them after another app is uninstalled.
        long from=Math.max(db.number("restore_floor"),end-WINDOW);
        long begin=Math.max(db.number("restore_floor"),from-UsageEngine.MAX_SESSION);
        UsageStatsManager manager=c.getSystemService(UsageStatsManager.class);
        UsageEvents events=manager.queryEvents(begin,end);
        if(events==null) return;
        List<UsageEngine.Event> input=new ArrayList<>();
        UsageEvents.Event event=new UsageEvents.Event();
        while(events.hasNextEvent()) {
            events.getNextEvent(event);
            // Activity instance identifiers are not exposed by the public SDK.
            String activity=String.valueOf(event.getClassName());
            int type=event.getEventType();
            if(type==1||type==2||type==23||type==16||type==17||type==26||type==27)
                input.add(new UsageEngine.Event(event.getTimeStamp(),event.getPackageName(),activity,type));
        }
        sql.beginTransaction();
        try {
            if(db.number("tracking_since")==0) {db.put("tracking_since",now);db.put("import_begin",from);}
            if(last>0&&last<from) db.gap(last,from,"后台间隔过长，明细可能已过期");
            for(UsageEngine.Event e:input)db.event(e);
            List<UsageEngine.Event> cached=db.events(begin,end);
            Map<String,Long> affected=new HashMap<>();
            for(UsageEngine.Event e:cached)if(e.type()==1&&!e.pkg().isEmpty())affected.merge(e.pkg(),e.at(),Math::min);
            UsageEngine.Result result=UsageEngine.confirmed(cached,begin,end);
            for(Map.Entry<String,Long> entry:affected.entrySet())
                db.replaceUsage(Math.max(from,entry.getValue()),end,Collections.singleton(entry.getKey()),result.segments());
            if(result.uncertain()) db.gap(begin,end,"重启或异常使用片段，部分时长无法确认");
            sql.delete("raw_events","at<?",new String[]{""+(now-7*86400000L)});
            db.put("state","{}");db.put("cursor",end);db.put("last_sync",now);db.put("permission_lost",0);
            db.put("last_event_count",cached.size());db.put("replay_from",from);
            sql.setTransactionSuccessful();
        } finally {sql.endTransaction();}
        inventory(c,now);
    }
    public static synchronized void inventory(Context c,long now) {
        inventory(c,now,PocketDb.get(c));
    }
    static synchronized void inventory(Context c,long now,PocketDb db) {
        SQLiteDatabase sql=db.getWritableDatabase();
        PackageManager pm=c.getPackageManager();
        List<PackageInfo> packages=pm.getInstalledPackages(0);
        Map<String,Boolean> old=new HashMap<>();Map<String,String> labels=new HashMap<>();Map<String,Long> installs=new HashMap<>();
        try(Cursor cur=sql.rawQuery("SELECT pkg,installed,label,first_install FROM apps",null)) {
            while(cur.moveToNext()) {old.put(cur.getString(0),cur.getInt(1)!=0);labels.put(cur.getString(0),cur.getString(2));installs.put(cur.getString(0),cur.getLong(3));}
        }
        boolean baseline=db.number("inventory_at")==0;
        Set<String> seen=new HashSet<>();
        sql.beginTransaction();
        try {
            for(PackageInfo p:packages) {
                String pkg=p.packageName;seen.add(pkg);
                String label=p.applicationInfo==null?pkg:pm.getApplicationLabel(p.applicationInfo).toString();
                boolean wasInstalled=Boolean.TRUE.equals(old.get(pkg));
                long previousInstall=installs.getOrDefault(pkg,0L);
                // Metadata changes are observations, not proof of uninstall/reinstall.
                boolean hiddenReinstall=wasInstalled&&previousInstall>0&&p.firstInstallTime>previousInstall;
                if(!baseline&&(!wasInstalled||hiddenReinstall)) {
                    db.lifecycle(pkg,label,hiddenReinstall?"install_time_changed":"observed_present",now,true,"inventory");
                }
                ContentValues values=new ContentValues();values.put("pkg",pkg);values.put("label",label);values.put("installed",1);values.put("first_install",p.firstInstallTime);
                sql.insertWithOnConflict("apps",null,values,SQLiteDatabase.CONFLICT_REPLACE);
                if(!wasInstalled||!iconFile(c,pkg).exists()) cacheIcon(c,pm,pkg);
            }
            for(String pkg:old.keySet()) if(Boolean.TRUE.equals(old.get(pkg))&&!seen.contains(pkg)) {
                sql.execSQL("UPDATE apps SET installed=0 WHERE pkg=?",new Object[]{pkg});
                if(!baseline) db.lifecycle(pkg,labels.get(pkg),"observed_missing",now,true,"inventory");
            }
            db.put("inventory_at",now);sql.setTransactionSuccessful();
        } finally {sql.endTransaction();}
    }
    public static synchronized void removed(Context c,String pkg,long at) {
        PocketDb db=PocketDb.get(c);SQLiteDatabase sql=db.getWritableDatabase();
        sql.beginTransaction();
        try(Cursor cur=sql.rawQuery("SELECT label,installed FROM apps WHERE pkg=?",new String[]{pkg})) {
            if(cur.moveToFirst()) {
                db.lifecycle(pkg,cur.getString(0),"remove",at,false,"broadcast");
                sql.execSQL("UPDATE apps SET installed=0 WHERE pkg=?",new Object[]{pkg});
            } else db.lifecycle(pkg,pkg,"remove",at,false,"broadcast");
            sql.setTransactionSuccessful();
        } finally {sql.endTransaction();}
    }
    public static synchronized void added(Context c,String pkg,long at) {
        PocketDb db=PocketDb.get(c);String label=pkg;
        try {label=c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(PackageManager.NameNotFoundException ignored){}
        SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
        try {
            db.lifecycle(pkg,label,"install",at,false,"broadcast");
            ContentValues v=new ContentValues();v.put("pkg",pkg);v.put("label",label);v.put("installed",1);
            try {v.put("first_install",c.getPackageManager().getPackageInfo(pkg,0).firstInstallTime);}catch(PackageManager.NameNotFoundException ignored){}
            sql.insertWithOnConflict("apps",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            sql.setTransactionSuccessful();
        }finally {sql.endTransaction();}
        cacheIcon(c,c.getPackageManager(),pkg);
    }
    public static File iconFile(Context c,String pkg) {return new File(new File(c.getFilesDir(),"icons"),pkg+".png");}
    private static void cacheIcon(Context c,PackageManager pm,String pkg) {
        try {
            File f=iconFile(c,pkg);f.getParentFile().mkdirs();
            Drawable d=pm.getApplicationIcon(pkg);Bitmap b=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(b);d.setBounds(0,0,96,96);d.draw(canvas);
            try(FileOutputStream out=new FileOutputStream(f)) {b.compress(Bitmap.CompressFormat.PNG,100,out);}
            b.recycle();
        } catch(Exception ignored) { /* A removed app may no longer expose its icon. */ }
    }
}
