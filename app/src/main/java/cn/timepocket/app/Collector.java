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
    private static final long DAY = 86_400_000L;
    private static final long REPLAY_WINDOW = 2 * DAY;
    private static final long CATCH_UP_LIMIT = 7 * DAY;
    public static boolean allowed(Context c) {
        AppOpsManager ops=(AppOpsManager)c.getSystemService(Context.APP_OPS_SERVICE);
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),c.getPackageName())==AppOpsManager.MODE_ALLOWED;
    }
    public static void sync(Context context) {
        sync(context, 5_000);
    }

    static void syncForeground(Context context) {
        sync(context, 0);
    }

    private static synchronized void sync(Context context, long eventDelay) {
        Context c=context.getApplicationContext();
        if (!c.getSystemService(UserManager.class).isUserUnlocked()) return;
        PocketDb db=PocketDb.get(c);
        long now = System.currentTimeMillis();
        try {
            syncUsage(c, db, now, now - eventDelay);
        } finally {
            inventory(c, System.currentTimeMillis(), db);
        }
    }

    private static void syncUsage(Context c, PocketDb db, long now, long end) {
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
            return;
        }
        long last=db.number("cursor");
        if(last>end+60000) {
            db.gap(end,last,"系统时间回退，暂停采集直到时间恢复，避免重复累计");
            return;
        }
        if (last >= end) return;

        // Regular reads overlap for late events. After a long pause or an upgrade,
        // also ask for older events that the device may still retain.
        long floor = db.number("restore_floor");
        long recentFrom = Math.max(floor, end - REPLAY_WINDOW);
        boolean firstCatchUp = db.number("usage_catchup_v1") == 0;
        long from = replayStart(end, last, floor, firstCatchUp);
        long begin = Math.max(floor, from - UsageEngine.MAX_SESSION);
        UsageStatsManager manager=c.getSystemService(UsageStatsManager.class);
        UsageEvents events=manager.queryEvents(begin,end);
        if(events==null) return;
        List<UsageEngine.Event> input=new ArrayList<>();
        UsageEvents.Event event=new UsageEvents.Event();
        long earliest = end;
        while(events.hasNextEvent()) {
            if (!events.getNextEvent(event)) break;
            earliest = Math.min(earliest, event.getTimeStamp());
            // Activity instance identifiers are not exposed by the public SDK.
            String activity=String.valueOf(event.getClassName());
            int type=event.getEventType();
            if(type==1||type==2||type==23||type==16||type==17||type==26||type==27)
                input.add(new UsageEngine.Event(event.getTimeStamp(),event.getPackageName(),activity,type));
        }
        sql.beginTransaction();
        try {
            if (db.number("tracking_since") == 0) {
                db.put("tracking_since", now);
                db.put("import_begin", Math.max(from, earliest));
            } else if (firstCatchUp && earliest < db.number("import_begin")) {
                db.put("import_begin", Math.max(from, earliest));
            }
            if (last > 0 && last < recentFrom) {
                db.gap(last, Math.max(from, Math.min(earliest, end)),
                        "较早的系统使用事件可能已过期");
            }
            for (UsageEngine.Event e : input) db.event(e);
            List<UsageEngine.Event> cached = db.events(begin, end);
            replayUsage(db, cached, begin, from, recentFrom, end);
            sql.delete("raw_events", "at<?",
                    new String[]{Long.toString(now - CATCH_UP_LIMIT - UsageEngine.MAX_SESSION)});
            db.put("state", "{}");
            db.put("cursor", end);
            db.put("last_sync", now);
            db.put("permission_lost", 0);
            db.put("usage_catchup_v1", 1);
            db.put("last_event_count", cached.size());
            db.put("replay_from", from);
            sql.setTransactionSuccessful();
        } finally {sql.endTransaction();}
    }

    static long replayStart(long end, long last, long floor, boolean firstCatchUp) {
        long oldest = end - CATCH_UP_LIMIT;
        long regular = end - REPLAY_WINDOW;
        long desired = firstCatchUp || last == 0 ? oldest : Math.min(last, regular);
        return Math.max(floor, Math.max(oldest, desired));
    }

    // Caller owns the transaction. Older backfill only adds closed intervals.
    static void replayUsage(PocketDb db, List<UsageEngine.Event> cached,
                            long begin, long from, long recentFrom, long end) {
        UsageEngine.Result result = UsageEngine.confirmed(cached, begin, end);
        Map<String, List<UsageEngine.Segment>> byPackage = new HashMap<>();
        for (UsageEngine.Segment segment : result.segments()) {
            long olderStart = Math.max(from, segment.start());
            long olderEnd = Math.min(recentFrom, segment.end());
            if (olderEnd > olderStart) {
                db.recoverUsage(new UsageEngine.Segment(segment.pkg(), olderStart, olderEnd));
            }
            byPackage.computeIfAbsent(segment.pkg(), key -> new ArrayList<>()).add(segment);
        }
        Map<String, Long> firstResume = new HashMap<>();
        for (UsageEngine.Event e : cached) {
            if (e.type() == UsageEngine.RESUME && !e.pkg().isEmpty()) {
                firstResume.putIfAbsent(e.pkg(), e.at());
            }
        }
        for (Map.Entry<String, Long> entry : firstResume.entrySet()) {
            String pkg = entry.getKey();
            db.replaceUsage(Math.max(recentFrom, entry.getValue()), end, Set.of(pkg),
                    byPackage.getOrDefault(pkg, List.of()));
        }
        if (result.uncertain()) {
            db.gap(begin, end, "重启或异常使用片段，部分时长无法确认");
        }
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
                db.confirmFootprint(pkg, cur.getString(0), "remove", at);
                sql.execSQL("UPDATE apps SET installed=0 WHERE pkg=?",new Object[]{pkg});
            } else db.confirmFootprint(pkg, pkg, "remove", at);
            sql.setTransactionSuccessful();
        } finally {sql.endTransaction();}
    }
    static synchronized void collectAfterRemoval(Context c) {
        if (!c.getSystemService(UserManager.class).isUserUnlocked() || !allowed(c)) return;
        long now = System.currentTimeMillis();
        syncUsage(c, PocketDb.get(c), now, now + 1);
    }

    public static synchronized void added(Context c,String pkg,long at) {
        PocketDb db=PocketDb.get(c);String label=pkg;
        try {label=c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(PackageManager.NameNotFoundException ignored){}
        SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
        try {
            db.confirmFootprint(pkg, label, "install", at);
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
