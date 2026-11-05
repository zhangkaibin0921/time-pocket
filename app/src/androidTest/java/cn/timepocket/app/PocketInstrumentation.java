package cn.timepocket.app;

import android.app.*;
import android.os.Bundle;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import java.io.File;
import org.json.JSONObject;
import java.util.*;

/** On-device tests use a separate disposable database, never the user's records. */
public final class PocketInstrumentation extends Instrumentation {
    private int assertions;
    @Override public void onCreate(Bundle args) {super.onCreate(args);start();}
    private void eq(long actual,long expected,String label) {assertions++;if(actual!=expected)throw new AssertionError(label+": "+actual+" != "+expected);}
    @Override public void onStart() {
        Bundle report=new Bundle();PocketDb helper=null;
        try {
            getTargetContext().deleteDatabase("qa-accounting.db");helper=new PocketDb(getTargetContext(),"qa-accounting.db");
            SQLiteDatabase db=helper.getWritableDatabase();
            helper.segment(new UsageEngine.Segment("sample.app",100,200));
            helper.segment(new UsageEngine.Segment("sample.app",100,200));
            eq(helper.total(0,1000,null),100,"duplicate insertion is idempotent");
            eq(helper.total(120,150,null),30,"SQL clips custom bounds");
            eq(helper.total(200,300,null),0,"half-open boundaries");
            ContentValues app=new ContentValues();app.put("pkg","sample.app");app.put("label","Sample");app.put("installed",1);
            db.insertWithOnConflict("apps",null,app,SQLiteDatabase.CONFLICT_REPLACE);
            helper.lifecycle("sample.app","Sample","remove",250,false);
            db.execSQL("UPDATE apps SET installed=0 WHERE pkg='sample.app'");
            eq(helper.total(0,1000,null),100,"uninstall keeps historical usage");
            eq(helper.ranking(0,1000,null).get(0).installed()?1:0,0,"uninstalled flag visible");
            helper.lifecycle("sample.app","Sample","install",300,false);
            db.execSQL("UPDATE apps SET installed=1 WHERE pkg='sample.app'");
            helper.segment(new UsageEngine.Segment("sample.app",400,460));
            eq(helper.total(0,1000,"sample.app"),160,"reinstallation accumulates by package");
            eq(helper.footprints(null).size(),2,"installation history retained");
            eq(helper.footprints("install").size(),1,"installation filter");
            helper.segment(new UsageEngine.Segment("other.app",110,210));
            eq(helper.total(120,150,null),60,"per-app simultaneous foreground is summed");
            eq(helper.total(120,150,"sample.app"),30,"detail filter");
            db.beginTransaction();try {db.delete("sessions",null,null);}finally {db.endTransaction();}
            eq(helper.total(0,1000,null),260,"failed restore transaction rolls back");
            helper.gap(500,600,"test");eq(helper.hasGap(550,650)?1:0,1,"coverage gap overlaps");
            eq(helper.hasGap(600,650)?1:0,0,"coverage boundary is exclusive");
            helper.put("cursor",12345);helper.close();helper=new PocketDb(getTargetContext(),"qa-accounting.db");
            eq(helper.number("cursor"),12345,"sync cursor survives reopen");
            eq(helper.total(0,1000,null),260,"sessions persist on disk");
            File backupFile=new File(getTargetContext().getFilesDir(),"qa-backup.json");
            Backup.exportTo(getTargetContext(),Uri.fromFile(backupFile),helper);
            JSONObject backup=Backup.read(getTargetContext(),Uri.fromFile(backupFile));
            eq(backup.getJSONArray("sessions").length(),3,"backup exports all segments");
            helper.segment(new UsageEngine.Segment("new.app",700,800));
            Backup.restore(getTargetContext(),backup,helper);
            eq(helper.total(0,1000,null),260,"restore replaces instead of double counting");
            eq(helper.footprints(null).size(),2,"restore retains lifecycle records");
            backup.getJSONArray("sessions").getJSONObject(0).put("end",-1);
            boolean rejected=false;try{Backup.restore(getTargetContext(),backup,helper);}catch(Exception e){rejected=true;}
            eq(rejected?1:0,1,"invalid backup rejected");
            eq(helper.total(0,1000,null),260,"invalid backup rolls back original records");
            eq(helper.footprints(null).size(),2,"invalid backup keeps original footprint records");
            backupFile.delete();
            helper.segment(new UsageEngine.Segment("late",10,100));
            helper.event(new UsageEngine.Event(10,"late","A",1));
            helper.event(new UsageEngine.Event(10,"late","A",1));
            eq(helper.events(0,200).size(),1,"raw events deduplicated");
            helper.replaceUsage(0,200,Set.of("late"),UsageEngine.confirmed(helper.events(0,200),0,200).segments());
            eq(helper.total(0,200,"late"),0,"old invented tail is removed during replay");
            helper.event(new UsageEngine.Event(40,"late","A",2));
            helper.replaceUsage(0,200,Set.of("late"),UsageEngine.confirmed(helper.events(0,200),0,200).segments());
            eq(helper.total(0,200,"late"),30,"late close repairs duration");
            helper.replaceUsage(0,200,Set.of("late"),UsageEngine.confirmed(helper.events(0,200),0,200).segments());
            eq(helper.total(0,200,"late"),30,"overlapping replay remains idempotent");
            helper.replaceUsage(20,35,Set.of("late"),List.of(new UsageEngine.Segment("late",22,30)));
            eq(helper.total(0,200,"late"),23,"replacement preserves both interval edges");
            eq(helper.total(0,1000,"sample.app"),160,"unrelated and uninstalled history untouched");
            helper.lifecycle("legacy.app","Legacy","remove",100,true,"legacy");
            eq(helper.footprints("remove").size(),1,"legacy data excluded from confirmed uninstall filter");
            eq(helper.footprints("pending").size(),1,"legacy history visible as pending");
            eq(helper.footprints(null).size(),2,"all user footprints exclude unconfirmed legacy rows");
            helper.put("inventory_at",0);
            Collector.inventory(getTargetContext(),System.currentTimeMillis(),helper);
            int before=helper.footprints("install").size();
            helper.getWritableDatabase().execSQL("UPDATE apps SET first_install=1 WHERE pkg=?",new Object[]{getTargetContext().getPackageName()});
            Collector.inventory(getTargetContext(),System.currentTimeMillis(),helper);
            eq(helper.footprints("install").size(),before,"install timestamp change does not invent installation");
            eq(helper.footprints("remove").size(),1,"install timestamp change does not invent uninstall");
            eq(helper.footprints("pending").stream().filter(f->f.kind().equals("install_time_changed")).count(),1,"changed metadata classified as observation");
            int confirmed=helper.footprints(null).size();
            SQLiteDatabase observations=helper.getWritableDatabase();observations.beginTransaction();
            try{for(int i=0;i<501;i++)helper.lifecycle("qa.observation."+i,"Observation","observed_missing",System.currentTimeMillis()+i,true,"inventory");observations.setTransactionSuccessful();}finally{observations.endTransaction();}
            eq(helper.footprints(null).size(),confirmed,"newer observations do not consume confirmed list limit");
            eq(helper.footprints("pending").size(),500,"background observations are retained internally");
            helper.lifecycle("qa.update","Update","update",System.currentTimeMillis(),false,"broadcast");
            eq(helper.footprints(null).size(),confirmed,"all user footprints contain only install and remove");
            // Exercise the schema upgrade against a genuine version-1 SQLite file.
            getTargetContext().deleteDatabase("qa-v1.db");
            SQLiteDatabase old=SQLiteDatabase.openOrCreateDatabase(getTargetContext().getDatabasePath("qa-v1.db"),null);
            old.execSQL("CREATE TABLE sessions(pkg TEXT,start INTEGER,end INTEGER,PRIMARY KEY(pkg,start,end))");
            old.execSQL("CREATE TABLE lifecycle(id INTEGER PRIMARY KEY,pkg TEXT,label TEXT,kind TEXT,at INTEGER,approx INTEGER)");
            old.execSQL("CREATE TABLE meta(k TEXT PRIMARY KEY,v TEXT)");
            old.execSQL("INSERT INTO sessions VALUES('old.app',10,130)");
            old.execSQL("INSERT INTO lifecycle VALUES(1,'old.app','Old','install',10,0)");
            old.setVersion(1);old.close();
            try(PocketDb upgraded=new PocketDb(getTargetContext(),"qa-v1.db")) {
                eq(upgraded.getReadableDatabase().getVersion(),4,"v1 schema upgraded to v4");
                try(android.database.Cursor cursor=upgraded.getReadableDatabase().rawQuery("SELECT end-start FROM sessions",null)){cursor.moveToFirst();eq(cursor.getLong(0),120,"upgrade preserves historical duration");}
                eq(upgraded.footprints("install").size(),0,"upgrade does not certify old footprints");
                eq(upgraded.footprints("pending").size(),1,"upgrade preserves old footprints as pending");
            }
            getTargetContext().deleteDatabase("qa-v1.db");
            journalChecks();historyChecks();photoChecks();
            Set<String> homes=HomeApps.packages(getTargetContext());
            eq(homes.contains(getTargetContext().getPackageName())?1:0,0,"ordinary app with launcher icon is not hidden as home");
            android.content.pm.ResolveInfo home=getTargetContext().getPackageManager().resolveActivity(new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME),android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            if(home!=null&&home.activityInfo!=null&&!"android".equals(home.activityInfo.packageName))eq(homes.contains(home.activityInfo.packageName)?1:0,1,"current default desktop recognized");
            report.putString("stream","PASS: "+assertions+" Android SQLite assertions\n");finish(Activity.RESULT_OK,report);
        } catch(Throwable e){report.putString("stream","FAIL: "+e.toString());finish(Activity.RESULT_CANCELED,report);}
        finally {if(helper!=null)helper.close();getTargetContext().deleteDatabase("qa-accounting.db");}
    }
    private void journalChecks() throws Exception {
        String name="qa-journal.db";getTargetContext().deleteDatabase(name);
        File file=new File(getTargetContext().getFilesDir(),"qa-journal-backup.json");
        try(PocketDb helper=new PocketDb(getTargetContext(),name)){
            long id=helper.saveActivity(0,"学习","学习","备注",50,150);
            eq(helper.activities(100,200).size(),1,"cross-midnight entry appears on next day");
            eq(helper.activities(0,50).size(),0,"journal half-open bounds");
            helper.saveActivity(id,"阅读","休闲","修改后的备注",60,160);
            eq(helper.activities(0,200).size(),1,"edit replaces one entry");
            eq(helper.activities(0,200).get(0).title().equals("阅读")?1:0,1,"edited title persists");
            boolean rejected=false;try{helper.saveActivity(id,"invalid","工作","",170,160);}catch(IllegalArgumentException e){rejected=true;}
            eq(rejected?1:0,1,"invalid time rejected before mutation");
            eq(helper.activities(0,200).get(0).start(),60,"invalid edit preserves original");
            helper.segment(new UsageEngine.Segment("app",90,120));
            helper.overrideSleep("2026-11-01");
            eq(helper.sleepOverridden("2026-11-01")?1:0,1,"dismissed sleep remembered");
            eq(helper.sleepOverridden("2026-11-02")?1:0,0,"different night remains enabled");
            helper.replaceUsage(0,200,Set.of("app"),List.of(new UsageEngine.Segment("app",95,125)));
            eq(helper.activities(0,200).size(),1,"usage replay preserves manual activity");
            eq(helper.dayUsage(100,110).size(),1,"timeline reads saved app events");
            helper.put("tracking_since",80);helper.put("import_begin",40);
            Backup.exportTo(getTargetContext(),Uri.fromFile(file),helper);JSONObject backup=Backup.read(getTargetContext(),Uri.fromFile(file));
            eq(backup.getInt("version"),4,"new backup schema");eq(backup.getJSONArray("activities").length(),1,"backup includes manual activity");
            helper.deleteActivity(id);eq(helper.activities(0,200).size(),0,"delete manual activity");eq(helper.total(0,200,null),30,"delete leaves automatic history");
            Backup.restore(getTargetContext(),backup,helper);
            eq(helper.activities(0,200).get(0).note().equals("修改后的备注")?1:0,1,"backup roundtrip restores note");
            eq(helper.sleepOverridden("2026-11-01")?1:0,1,"backup restores sleep dismissal");
            eq(helper.number("import_begin"),40,"backup retains earliest known import coverage");
            JSONObject badCoverage=new JSONObject(backup.toString());badCoverage.put("importBegin",-1);
            boolean badRejected=false;try{Backup.restore(getTargetContext(),badCoverage,helper);}catch(Exception e){badRejected=true;}
            eq(badRejected?1:0,1,"invalid coverage metadata rejected");eq(helper.number("import_begin"),40,"invalid restore preserves original coverage");
            int count=helper.activities(0,200).size();
            boolean failed=false;try{helper.saveSleepActivity("invalid-date","睡眠","睡觉","",1,20);}catch(Exception e){failed=true;}
            eq(failed?1:0,1,"invalid sleep override rejected");eq(helper.activities(0,200).size(),count,"sleep edit and suppression are atomic");
            JSONObject invalid=new JSONObject(backup.toString());invalid.getJSONArray("activities").getJSONObject(0).put("end",0);
            rejected=false;try{Backup.restore(getTargetContext(),invalid,helper);}catch(Exception e){rejected=true;}
            eq(rejected?1:0,1,"bad activity backup rejected");eq(helper.activities(0,200).size(),1,"invalid restore rolls back manual entries");eq(helper.total(0,200,null),30,"invalid restore keeps app history");
            JSONObject oldBackup=new JSONObject(backup.toString());oldBackup.put("version",2);oldBackup.remove("activities");oldBackup.remove("importBegin");Backup.restore(getTargetContext(),oldBackup,helper);
            eq(helper.activities(0,200).size(),0,"v2 restore supported with no manual entries");eq(helper.total(0,200,null),30,"v2 usage preserved");
            eq(helper.number("import_begin"),80,"older backup uses conservative known tracking boundary");
            // Reopen a genuine schema-v2 file to exercise non-destructive upgrade.
            helper.getWritableDatabase().execSQL("DROP TABLE activity_photos");helper.getWritableDatabase().execSQL("DROP TABLE activities");helper.getWritableDatabase().setVersion(2);
        }
        try(PocketDb upgraded=new PocketDb(getTargetContext(),name)){
            eq(upgraded.getReadableDatabase().getVersion(),4,"v2 upgrades to v4");eq(upgraded.total(0,200,null),30,"v2 migration preserves history");
            upgraded.saveActivity(0,"迁移后手记","其他","",1,20);eq(upgraded.activities(0,200).size(),1,"new table writable after upgrade");
            upgraded.saveSleepActivity("2026-11-02","调整后的睡眠","睡觉","",20,40);
            eq(upgraded.activities(20,40).size(),1,"adjusted sleep saved as manual entry");eq(upgraded.sleepOverridden("2026-11-02")?1:0,1,"manual sleep edit suppresses regenerated guess");
        } finally {file.delete();getTargetContext().deleteDatabase(name);}
    }
    private void photoChecks() throws Exception {
        String name="qa-photos.db";getTargetContext().deleteDatabase(name);File file=new File(getTargetContext().getFilesDir(),"qa-photos.json");
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(64,48,android.graphics.Bitmap.Config.ARGB_8888);bitmap.eraseColor(android.graphics.Color.RED);java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,85,output);bitmap.recycle();byte[] image=output.toByteArray();
        try(PocketDb h=new PocketDb(getTargetContext(),name)){
            long id=h.saveActivityWithPhotos(0,"有图手记","其他","",10,20,null,List.of(image,image,image));eq(h.photoIds(id).size(),3,"three photos saved");long photo=h.photoIds(id).get(0);eq(Arrays.equals(h.photo(photo),image)?1:0,1,"image bytes persist");
            boolean rejected=false;try{h.saveActivityWithPhotos(id,"bad","其他","",10,20,null,List.of(new byte[]{1,2,3}));}catch(Exception e){rejected=true;}eq(rejected?1:0,1,"corrupt photo rejected");eq(h.photoIds(id).size(),3,"invalid photo preserves attachments");eq(h.activities(0,30).get(0).title().equals("有图手记")?1:0,1,"invalid photo preserves title");
            rejected=false;try{h.saveActivityWithPhotos(id,"bad","其他","",10,20,"invalid",List.of(image));}catch(Exception e){rejected=true;}eq(rejected?1:0,1,"late sleep validation fails");eq(h.photoIds(id).size(),3,"late failure rolls back replaced photos");
            rejected=false;try{h.saveActivityWithPhotos(id,"bad","其他","",10,20,null,List.of(image,image,image,image));}catch(Exception e){rejected=true;}eq(rejected?1:0,1,"photo limit enforced");
            Backup.exportTo(getTargetContext(),Uri.fromFile(file),h);JSONObject backup=Backup.read(getTargetContext(),Uri.fromFile(file));eq(backup.getJSONArray("activities").getJSONObject(0).getJSONArray("photos").length(),3,"backup includes photos");
            h.deleteActivity(id);eq(h.photo(photo)==null?1:0,1,"delete removes attached image bytes");Backup.restore(getTargetContext(),backup,h);long restored=h.activities(0,30).get(0).id();eq(h.photoIds(restored).size(),3,"restore reconnects images to remapped entry ID");eq(Arrays.equals(h.photo(h.photoIds(restored).get(0)),image)?1:0,1,"restore preserves image bytes");
            JSONObject bad=new JSONObject(backup.toString());bad.getJSONArray("activities").getJSONObject(0).getJSONArray("photos").put(1,"not an image");rejected=false;try{Backup.restore(getTargetContext(),bad,h);}catch(Exception e){rejected=true;}eq(rejected?1:0,1,"corrupt image backup rejected");eq(h.photoIds(restored).size(),3,"failed restore retains original photos");
            h.saveActivityWithPhotos(restored,"移除一张","其他","",10,20,null,List.of(image,image));eq(h.photoIds(restored).size(),2,"editing replaces attachment set");
            JSONObject oldBackup=new JSONObject(backup.toString());oldBackup.put("version",3);oldBackup.getJSONArray("activities").getJSONObject(0).remove("photos");Backup.restore(getTargetContext(),oldBackup,h);eq(h.photoIds(h.activities(0,30).get(0).id()).size(),0,"v3 backups still restore without images");
            h.getWritableDatabase().execSQL("DROP TRIGGER activity_photos_delete");h.getWritableDatabase().execSQL("DROP TABLE activity_photos");h.getWritableDatabase().setVersion(3);
        }
        try(PocketDb h=new PocketDb(getTargetContext(),name)){eq(h.getReadableDatabase().getVersion(),4,"v3 migration supported");long id=h.activities(0,30).get(0).id();eq(h.photoIds(id).size(),0,"upgrade preserves old notes without photos");h.saveActivityWithPhotos(id,"迁移后图片","其他","",10,20,null,List.of(image));eq(h.photoIds(id).size(),1,"upgraded database supports attachments");}
        finally{file.delete();getTargetContext().deleteDatabase(name);}
        File source=new File(getTargetContext().getCacheDir(),"qa-photo-source.jpg");java.nio.file.Files.write(source.toPath(),image);String ref=EntryPhotos.importImage(getTargetContext(),Uri.fromFile(source));source.delete();eq(EntryPhotos.read(getTargetContext(),ref).length>0?1:0,1,"imported copy survives deletion of source");EntryPhotos.discard(getTargetContext(),List.of(ref));eq(EntryPhotos.draft(getTargetContext(),ref).exists()?1:0,0,"discard removes draft copy");
    }
    private void historyChecks(){
        getTargetContext().deleteDatabase("qa-history.db");
        try(PocketDb h=new PocketDb(getTargetContext(),"qa-history.db")){
            h.lifecycle("old.app","同名应用","install",10,false);h.lifecycle("old.app","同名应用","remove",20,false);h.lifecycle("old.app","同名应用","install",30,false);
            h.lifecycle("other.app","同名应用","install",40,false);h.lifecycle("old.app","同名应用","remove",45,true);
            for(int i=0;i<510;i++)h.lifecycle("busy.app","Busy","install",100+i,false);
            eq(h.findFootprints(null,"old.app",0,Long.MAX_VALUE,50).size(),3,"package filter covers old history before limit");
            eq(h.findFootprints("remove","old.app",20,30,50).size(),1,"type and half-open date filters compose");
            eq(h.findFootprints(null,"old.app",21,30,50).size(),0,"range end is exclusive");
            eq(h.findFootprints(null,null,0,Long.MAX_VALUE,600).size(),514,"history beyond old 500-row cap remains accessible");
            eq(h.findApps("同名",50).size(),2,"same name applications stay separate by package");
            eq(h.findApps("old.app",50).size(),1,"app with only old lifecycle history is searchable");
            long id=h.saveActivity(0,"Night","睡觉","keyword %",100,300);
            h.saveActivity(0,"Exercise","运动","",310,400);
            eq(h.findActivities("keyword","睡觉",200,250,50).size(),1,"notes and category search includes crossing activities");
            eq(h.findActivities("%",null,0,1000,50).size(),1,"search treats wildcard as literal text");
            eq(h.findActivities("keyword","运动",0,1000,50).size(),0,"category filter excludes other activities");
            h.saveActivity(id,"Night","睡觉","keyword %",500,700);
            eq(h.activities(100,300).size(),0,"moving activity leaves old date range");eq(h.activities(500,700).size(),1,"moving activity retains its identity");
            h.overrideSleep("2026-11-04");h.restoreSleep("2026-11-04");eq(h.sleepOverridden("2026-11-04")?1:0,0,"sleep inference suppression can be restored");eq(h.activities(500,700).size(),1,"restoring inference never deletes manual sleep");
        }finally{getTargetContext().deleteDatabase("qa-history.db");}
    }
}
