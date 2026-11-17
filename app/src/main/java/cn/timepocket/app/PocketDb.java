package cn.timepocket.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

public final class PocketDb extends SQLiteOpenHelper {
    private static PocketDb instance;
    public static synchronized PocketDb get(Context c) {
        if (instance == null) instance = new PocketDb(c.getApplicationContext());
        return instance;
    }
    private PocketDb(Context c) { this(c,"time-pocket.db"); }
    PocketDb(Context c,String name) { super(c, name, null, 4); setWriteAheadLoggingEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE apps(pkg TEXT PRIMARY KEY,label TEXT NOT NULL,installed INTEGER NOT NULL DEFAULT 1,signature TEXT,first_install INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE sessions(pkg TEXT NOT NULL,start INTEGER NOT NULL,end INTEGER NOT NULL,PRIMARY KEY(pkg,start,end),CHECK(end>start))");
        db.execSQL("CREATE INDEX sessions_time ON sessions(start,end)");
        db.execSQL("CREATE TABLE lifecycle(id INTEGER PRIMARY KEY AUTOINCREMENT,pkg TEXT NOT NULL,label TEXT NOT NULL,kind TEXT NOT NULL,at INTEGER NOT NULL,approx INTEGER NOT NULL DEFAULT 0,source TEXT NOT NULL DEFAULT 'legacy')");
        db.execSQL("CREATE TABLE meta(k TEXT PRIMARY KEY,v TEXT NOT NULL)");
        db.execSQL("CREATE TABLE gaps(start INTEGER NOT NULL,end INTEGER NOT NULL,reason TEXT NOT NULL,PRIMARY KEY(start,end,reason))");
        createEvents(db);
        createActivities(db);
        createPhotos(db);
    }
    private void createActivities(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE activities(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,category TEXT NOT NULL,note TEXT NOT NULL DEFAULT '',start INTEGER NOT NULL,end INTEGER NOT NULL,CHECK(end>start))");
        db.execSQL("CREATE INDEX activities_time ON activities(start,end)");
    }
    private void createPhotos(SQLiteDatabase db){
        db.execSQL("CREATE TABLE activity_photos(id INTEGER PRIMARY KEY AUTOINCREMENT,activity_id INTEGER NOT NULL,position INTEGER NOT NULL,data BLOB NOT NULL)");
        db.execSQL("CREATE INDEX activity_photos_entry ON activity_photos(activity_id,position)");
        db.execSQL("CREATE TRIGGER activity_photos_delete AFTER DELETE ON activities BEGIN DELETE FROM activity_photos WHERE activity_id=OLD.id; END");
    }
    public List<Long> photoIds(long activityId){List<Long> ids=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM activity_photos WHERE activity_id=? ORDER BY position",new String[]{""+activityId})){while(c.moveToNext())ids.add(c.getLong(0));}return ids;}
    public byte[] photo(long photoId){try(Cursor c=getReadableDatabase().rawQuery("SELECT data FROM activity_photos WHERE id=?",new String[]{""+photoId})){return c.moveToFirst()?c.getBlob(0):null;}}
    public long saveActivityWithPhotos(long id,String title,String category,String note,long start,long end,String sleepDay,List<byte[]> photos){
        if(photos.size()>EntryPhotos.MAX_COUNT)throw new IllegalArgumentException("最多 3 张图片");for(byte[] bytes:photos)EntryPhotos.validate(bytes);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{long entry=saveActivity(id,title,category,note,start,end);db.delete("activity_photos","activity_id=?",new String[]{""+entry});
            int position=0;for(byte[] bytes:photos){ContentValues v=new ContentValues();v.put("activity_id",entry);v.put("position",position++);v.put("data",bytes);db.insertOrThrow("activity_photos",null,v);}
            if(sleepDay!=null)overrideSleep(sleepDay);db.setTransactionSuccessful();return entry;
        }finally{db.endTransaction();}
    }
    private void createEvents(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE raw_events(at INTEGER NOT NULL,pkg TEXT NOT NULL,activity TEXT NOT NULL,type INTEGER NOT NULL,PRIMARY KEY(at,pkg,activity,type))");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int old, int next) {
        if(old<2) {
            createEvents(db);
            db.execSQL("ALTER TABLE lifecycle ADD COLUMN source TEXT NOT NULL DEFAULT 'legacy'");
            db.execSQL("UPDATE lifecycle SET approx=1");
            db.execSQL("INSERT OR REPLACE INTO meta(k,v) VALUES('legacy_history','1')");
        }
        if(old<3)createActivities(db);
        if(old<4)createPhotos(db);
    }
    public String meta(String key, String fallback) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT v FROM meta WHERE k=?", new String[]{key})) {
            return c.moveToFirst() ? c.getString(0) : fallback;
        }
    }
    public long number(String key) { try { return Long.parseLong(meta(key,"0")); } catch (NumberFormatException e) { return 0; } }
    public void put(String key, Object value) {
        ContentValues v = new ContentValues(); v.put("k",key); v.put("v",String.valueOf(value));
        getWritableDatabase().insertWithOnConflict("meta",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public void gap(long start,long end,String reason) {
        if (end <= start) return;
        ContentValues v=new ContentValues(); v.put("start",start);v.put("end",end);v.put("reason",reason);
        getWritableDatabase().insertWithOnConflict("gaps",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }
    public void segment(UsageEngine.Segment s) {
        ContentValues v=new ContentValues(); v.put("pkg",s.pkg());v.put("start",s.start());v.put("end",s.end());
        getWritableDatabase().insertWithOnConflict("sessions",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        ContentValues app=new ContentValues();app.put("pkg",s.pkg());app.put("label",s.pkg());app.put("installed",0);
        getWritableDatabase().insertWithOnConflict("apps",null,app,SQLiteDatabase.CONFLICT_IGNORE);
    }
    public void lifecycle(String pkg,String label,String kind,long at,boolean approximate) {
        lifecycle(pkg,label,kind,at,approximate,approximate?"inventory":"broadcast");
    }
    public void lifecycle(String pkg,String label,String kind,long at,boolean approximate,String source) {
        ContentValues v=new ContentValues();v.put("pkg",pkg);v.put("label",label);v.put("kind",kind);v.put("at",at);v.put("approx",approximate?1:0);
        v.put("source",source);
        getWritableDatabase().insertOrThrow("lifecycle",null,v);
    }
    /** A notification supersedes a snapshot taken while that notification was queued. */
    void confirmFootprint(String pkg, String label, String kind, long at) {
        String observedKinds = kind.equals("remove")
                ? "('observed_missing')" : "('observed_present','install_time_changed')";
        getWritableDatabase().delete("lifecycle",
                "pkg=? AND source='inventory' AND kind IN " + observedKinds + " AND at>=?",
                new String[]{pkg, Long.toString(at)});
        lifecycle(pkg, label, kind, at, false, "broadcast");
    }

    public void event(UsageEngine.Event e) {
        ContentValues v=new ContentValues();v.put("at",e.at());v.put("pkg",e.pkg()==null?"":e.pkg());v.put("activity",e.activity()==null?"":e.activity());v.put("type",e.type());
        getWritableDatabase().insertWithOnConflict("raw_events",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }
    public List<UsageEngine.Event> events(long start,long end) {
        List<UsageEngine.Event> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT at,pkg,activity,type FROM raw_events WHERE at>=? AND at<? ORDER BY at,rowid",new String[]{""+start,""+end})) {
            while(c.moveToNext()) result.add(new UsageEngine.Event(c.getLong(0),c.getString(1),c.getString(2),c.getInt(3)));
        }return result;
    }
    /** Replace only packages evidenced by cached events, preserving absent/uninstalled history. Caller owns transaction. */
    public void replaceUsage(long start,long end,Set<String> packages,List<UsageEngine.Segment> replacement) {
        SQLiteDatabase db=getWritableDatabase();
        for(String pkg:packages) {
            List<UsageEngine.Segment> edges=new ArrayList<>();
            try(Cursor c=db.rawQuery("SELECT start,end FROM sessions WHERE pkg=? AND end>? AND start<?",new String[]{pkg,""+start,""+end})) {
                while(c.moveToNext()) {
                    if(c.getLong(0)<start)edges.add(new UsageEngine.Segment(pkg,c.getLong(0),start));
                    if(c.getLong(1)>end)edges.add(new UsageEngine.Segment(pkg,end,c.getLong(1)));
                }
            }
            db.delete("sessions","pkg=? AND end>? AND start<?",new String[]{pkg,""+start,""+end});
            for(UsageEngine.Segment edge:edges)segment(edge);
        }
        for(UsageEngine.Segment s:replacement) {
            long a=Math.max(start,s.start()),b=Math.min(end,s.end());
            if(b>a&&packages.contains(s.pkg()))segment(new UsageEngine.Segment(s.pkg(),a,b));
        }
    }
    void recoverUsage(UsageEngine.Segment segment) {
        SQLiteDatabase db = getWritableDatabase();
        String overlap = "pkg=? AND end>=? AND start<=?";
        String[] args = {segment.pkg(), Long.toString(segment.start()), Long.toString(segment.end())};
        long start = segment.start(), end = segment.end();
        try (Cursor cursor = db.rawQuery("SELECT MIN(start),MAX(end) FROM sessions WHERE " + overlap, args)) {
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                start = Math.min(start, cursor.getLong(0));
                end = Math.max(end, cursor.getLong(1));
            }
        }
        db.delete("sessions", overlap, args);
        segment(new UsageEngine.Segment(segment.pkg(), start, end));
    }

    public record AppUsage(String pkg,String label,long duration,boolean installed) {}
    public List<AppUsage> ranking(long begin,long end,String onlyPkg) {
        if(begin>=end) return List.of();
        String filter=onlyPkg==null?"":" AND s.pkg=?";
        String[] args=onlyPkg==null?new String[]{""+end,""+begin,""+begin,""+end}:new String[]{""+end,""+begin,""+begin,""+end,onlyPkg};
        List<AppUsage> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT s.pkg,COALESCE(a.label,s.pkg),SUM(MIN(s.end,CAST(? AS INTEGER))-MAX(s.start,CAST(? AS INTEGER))),COALESCE(a.installed,0) FROM sessions s LEFT JOIN apps a ON s.pkg=a.pkg WHERE s.end>? AND s.start<?"+filter+" GROUP BY s.pkg ORDER BY 3 DESC",args)) {
            while(c.moveToNext()) result.add(new AppUsage(c.getString(0),c.getString(1),c.getLong(2),c.getInt(3)!=0));
        }
        return result;
    }
    static long sumAppUsage(List<AppUsage> apps) {
        return apps.stream().mapToLong(AppUsage::duration).sum();
    }
    public long total(long begin,long end,String pkg) {
        return sumAppUsage(ranking(begin,end,pkg));
    }
    public record Footprint(String pkg,String label,String kind,long at,boolean approximate,String source) {
        public Footprint(String pkg,String label,String kind,long at,boolean approximate){this(pkg,label,kind,at,approximate,approximate?"inventory":"broadcast");}
    }
    public List<Footprint> footprints(String filter) {
        List<Footprint> rows=new ArrayList<>();
        // Filter before LIMIT so background observations cannot crowd out confirmed events.
        String where=filter==null?" WHERE source='broadcast' AND kind IN ('install','remove')":"pending".equals(filter)?" WHERE source!='broadcast'":" WHERE kind=? AND source='broadcast' AND kind IN ('install','remove')";
        try(Cursor c=getReadableDatabase().rawQuery("SELECT pkg,label,kind,at,approx,source FROM lifecycle"+where+" ORDER BY at DESC,id DESC LIMIT 500",filter==null||"pending".equals(filter)?null:new String[]{filter})) {
            while(c.moveToNext()) rows.add(new Footprint(c.getString(0),c.getString(1),c.getString(2),c.getLong(3),c.getInt(4)!=0,c.getString(5)));
        }return rows;
    }
    public boolean hasGap(long begin,long end) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM gaps WHERE end>? AND start<? LIMIT 1",new String[]{""+begin,""+end})) { return c.moveToFirst(); }
    }
    public record AppChoice(String pkg,String label) {}
    public List<AppChoice> findApps(String query,int limit){
        List<AppChoice> rows=new ArrayList<>();
        String sql="SELECT k.pkg,COALESCE(a.label,(SELECT label FROM lifecycle WHERE pkg=k.pkg ORDER BY at DESC,id DESC LIMIT 1),k.pkg) AS name FROM (SELECT pkg FROM apps UNION SELECT pkg FROM sessions UNION SELECT pkg FROM lifecycle) k LEFT JOIN apps a ON a.pkg=k.pkg";
        try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM ("+sql+") WHERE instr(lower(pkg),lower(?))>0 OR instr(lower(name),lower(?))>0 ORDER BY name,pkg LIMIT ?",new String[]{query.trim(),query.trim(),""+limit})){
            while(c.moveToNext())rows.add(new AppChoice(c.getString(0),c.getString(1)));
        }return rows;
    }
    public List<Footprint> findFootprints(String kind,String pkg,long begin,long end,int limit){
        List<Footprint> rows=new ArrayList<>();List<String> args=new ArrayList<>(List.of(""+begin,""+end));
        String where = """
                ((source='broadcast' AND kind IN ('install','remove'))
                 OR (source='inventory' AND kind IN ('observed_present','observed_missing')))
                 AND at>=? AND at<?
                """;
        if ("install".equals(kind)) {
            where += " AND kind IN ('install','observed_present')";
        } else if ("remove".equals(kind)) {
            where += " AND kind IN ('remove','observed_missing')";
        } else if (kind != null) {
            return rows;
        }
        if(pkg!=null){where+=" AND pkg=?";args.add(pkg);}args.add(""+limit);
        try(Cursor c=getReadableDatabase().rawQuery("SELECT pkg,label,kind,at,approx,source FROM lifecycle WHERE "+where+" ORDER BY at DESC,id DESC LIMIT ?",args.toArray(new String[0]))){while(c.moveToNext())rows.add(new Footprint(c.getString(0),c.getString(1),c.getString(2),c.getLong(3),c.getInt(4)!=0,c.getString(5)));}return rows;
    }
    public List<ActivityEntry> findActivities(String query,String category,long begin,long end,int limit){
        List<ActivityEntry> rows=new ArrayList<>();List<String> args=new ArrayList<>(List.of(""+begin,""+end,query.trim(),query.trim()));
        String where="end>? AND start<? AND (instr(lower(title),lower(?))>0 OR instr(lower(note),lower(?))>0)";
        if(category!=null){where+=" AND category=?";args.add(category);}args.add(""+limit);
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,title,category,note,start,end FROM activities WHERE "+where+" ORDER BY start DESC,id DESC LIMIT ?",args.toArray(new String[0]))){while(c.moveToNext())rows.add(new ActivityEntry(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4),c.getLong(5)));}return rows;
    }
    public void restoreSleep(String day){java.time.LocalDate.parse(day);getWritableDatabase().delete("meta","k=?",new String[]{"sleep_override:"+day});}
    public static final String[] CATEGORIES={"学习","工作","运动","吃饭","睡觉","出行","休闲","其他"};
    public boolean sleepOverridden(String day){return number("sleep_override:"+day)!=0;}
    public void overrideSleep(String day){java.time.LocalDate.parse(day);put("sleep_override:"+day,1);}
    public List<String> sleepOverrides(){
        List<String> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT k FROM meta WHERE k LIKE 'sleep_override:%' AND v='1'",null)){
            while(c.moveToNext())result.add(c.getString(0).substring("sleep_override:".length()));
        }return result;
    }
    public void saveSleepActivity(String day,String title,String category,String note,long start,long end){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{saveActivity(0,title,category,note,start,end);overrideSleep(day);db.setTransactionSuccessful();}
        finally{db.endTransaction();}
    }
    public record ActivityEntry(long id,String title,String category,String note,long start,long end) {}
    public long saveActivity(long id,String title,String category,String note,long start,long end) {
        title=title.trim();note=note.trim();
        if(title.isEmpty()||title.length()>80||note.length()>1000||!Arrays.asList(CATEGORIES).contains(category)||start<0||end<=start)
            throw new IllegalArgumentException("无效的活动记录");
        ContentValues v=new ContentValues();v.put("title",title);v.put("category",category);v.put("note",note);v.put("start",start);v.put("end",end);
        if(id==0)return getWritableDatabase().insertOrThrow("activities",null,v);
        if(getWritableDatabase().update("activities",v,"id=?",new String[]{""+id})!=1)throw new IllegalStateException("记录已不存在");
        return id;
    }
    public void deleteActivity(long id){getWritableDatabase().delete("activities","id=?",new String[]{""+id});}
    public ActivityEntry activity(long id){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,title,category,note,start,end FROM activities WHERE id=?",new String[]{""+id})){return c.moveToFirst()?new ActivityEntry(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4),c.getLong(5)):null;}
    }
    public List<ActivityEntry> activities(long begin,long end) {
        List<ActivityEntry> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,title,category,note,start,end FROM activities WHERE end>? AND start<? ORDER BY start,id",new String[]{""+begin,""+end})) {
            while(c.moveToNext())result.add(new ActivityEntry(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4),c.getLong(5)));
        }return result;
    }
    public List<DayTimeline.Item> dayUsage(long begin,long end) {
        List<DayTimeline.Item> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT s.pkg,COALESCE(a.label,s.pkg),s.start,s.end FROM sessions s LEFT JOIN apps a ON s.pkg=a.pkg WHERE s.end>? AND s.start<? ORDER BY s.start,s.end",new String[]{""+begin,""+end})) {
            while(c.moveToNext())result.add(new DayTimeline.Item(c.getString(0),c.getString(1),c.getLong(2),c.getLong(3)));
        }return result;
    }
}
