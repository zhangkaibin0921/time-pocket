package cn.timepocket.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

final class Backup {
    private static final String[] TABLES={"apps","sessions","lifecycle","gaps","raw_events","activities"};
    static void exportTo(Context c,Uri uri) throws Exception {
        exportTo(c,uri,PocketDb.get(c));
    }
    static void exportTo(Context c,Uri uri,PocketDb helper) throws Exception {
        synchronized(Collector.class) {
            File temporary=File.createTempFile("pocket-export-",".json",c.getCacheDir());
            try {
                SQLiteDatabase db=helper.getReadableDatabase();db.beginTransaction();
                try(android.util.JsonWriter writer=new android.util.JsonWriter(new OutputStreamWriter(new LimitedOutput(new FileOutputStream(temporary)),StandardCharsets.UTF_8))){
                    writer.beginObject().name("format").value("time-pocket").name("version").value(4).name("exportedAt").value(System.currentTimeMillis());
                    writer.name("legacyHistory").value(helper.number("legacy_history")).name("trackingSince").value(helper.number("tracking_since")).name("importBegin").value(helper.number("import_begin"));
                    writer.name("sleepOverrides").beginArray();for(String day:helper.sleepOverrides())writer.value(day);writer.endArray();
                    for(String table:TABLES){writer.name(table).beginArray();try(Cursor cursor=db.rawQuery("SELECT * FROM "+table,null)){
                        while(cursor.moveToNext()){writer.beginObject();for(int i=0;i<cursor.getColumnCount();i++){if(cursor.isNull(i))continue;writer.name(cursor.getColumnName(i));if(cursor.getType(i)==Cursor.FIELD_TYPE_INTEGER)writer.value(cursor.getLong(i));else writer.value(cursor.getString(i));}
                            if(table.equals("activities")){writer.name("photos").beginArray();for(long photo:helper.photoIds(cursor.getLong(cursor.getColumnIndexOrThrow("id"))))writer.value(android.util.Base64.encodeToString(helper.photo(photo),android.util.Base64.NO_WRAP));writer.endArray();}
                            writer.endObject();
                        }
                    }writer.endArray();}writer.endObject();writer.flush();db.setTransactionSuccessful();
                }finally{db.endTransaction();}
                try(InputStream in=new FileInputStream(temporary);OutputStream out=c.getContentResolver().openOutputStream(uri,"wt")){
                    if(out==null)throw new IOException("无法写入所选文件");byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                }
            }finally{temporary.delete();}
        }
    }
    private static final class LimitedOutput extends FilterOutputStream {
        private long size;
        LimitedOutput(OutputStream out){super(out);}
        @Override public void write(int b)throws IOException{check(1);out.write(b);}
        @Override public void write(byte[] b,int offset,int length)throws IOException{check(length);out.write(b,offset,length);}
        private void check(int length)throws IOException{if((size+=length)>50L*1024*1024)throw new IOException("备份文件超过 50 MB");}
    }
    static JSONObject read(Context c,Uri uri) throws Exception {
        try(InputStream in=c.getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            if(in==null)throw new IOException("无法打开备份");byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1) {out.write(buffer,0,n);if(out.size()>50*1024*1024)throw new IOException("备份文件超过 50 MB");}
            JSONObject data=new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
            int version=data.optInt("version");
            if(!"time-pocket".equals(data.optString("format"))||version<1||version>4)throw new IOException("这不是支持的时光口袋备份");
            for(String table:TABLES){if(table.equals("raw_events")&&version<2||table.equals("activities")&&version<3)continue;data.getJSONArray(table);}
            return data;
        }
    }
    static void restore(Context c,JSONObject data) throws Exception {
        restore(c,data,PocketDb.get(c));
    }
    static void restore(Context c,JSONObject data,PocketDb helper) throws Exception {
        synchronized(Collector.class) {
            SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();
            try {
                for(String table:TABLES) db.delete(table,null,null);
                db.delete("meta",null,null);
                JSONArray apps=data.getJSONArray("apps");
                for(int i=0;i<apps.length();i++) {
                    JSONObject r=apps.getJSONObject(i);ContentValues v=new ContentValues();
                    v.put("pkg",r.getString("pkg"));v.put("label",r.getString("label"));v.put("installed",0);v.put("first_install",0);
                    db.insertOrThrow("apps",null,v);
                }
                JSONArray sessions=data.getJSONArray("sessions");
                for(int i=0;i<sessions.length();i++) {
                    JSONObject r=sessions.getJSONObject(i);long start=r.getLong("start"),end=r.getLong("end");
                    if(start<0||end<=start)throw new IOException("备份包含无效使用片段");
                    helper.segment(new UsageEngine.Segment(r.getString("pkg"),start,end));
                }
                JSONArray lifecycle=data.getJSONArray("lifecycle");
                for(int i=0;i<lifecycle.length();i++) {
                    JSONObject r=lifecycle.getJSONObject(i);String kind=r.getString("kind");
                    if(!java.util.Arrays.asList("install","remove","observed_present","observed_missing","install_time_changed").contains(kind))throw new IOException("无效足迹类型");
                    String source=r.optString("source","legacy");
                    if(!java.util.Arrays.asList("legacy","inventory","broadcast").contains(source))throw new IOException("无效足迹来源");
                    helper.lifecycle(r.getString("pkg"),r.getString("label"),kind,r.getLong("at"),!source.equals("broadcast")||r.optInt("approx",1)!=0,source);
                }
                JSONArray gaps=data.getJSONArray("gaps");
                for(int i=0;i<gaps.length();i++) {JSONObject r=gaps.getJSONObject(i);helper.gap(r.getLong("start"),r.getLong("end"),r.getString("reason"));}
                JSONArray events=data.optJSONArray("raw_events");
                if(events!=null)for(int i=0;i<events.length();i++) {JSONObject r=events.getJSONObject(i);helper.event(new UsageEngine.Event(r.getLong("at"),r.getString("pkg"),r.getString("activity"),r.getInt("type")));}
                JSONArray activities=data.optJSONArray("activities");
                if(data.optInt("version")>=3&&activities==null)throw new IOException("备份缺少活动记录");
                if(activities!=null)for(int i=0;i<activities.length();i++){
                    JSONObject r=activities.getJSONObject(i);java.util.List<byte[]> photos=new java.util.ArrayList<>();
                    if(data.optInt("version")>=4){JSONArray images=r.getJSONArray("photos");if(images.length()>EntryPhotos.MAX_COUNT)throw new IOException("图片数量无效");for(int j=0;j<images.length();j++){String encoded=images.getString(j);if(encoded.length()>EntryPhotos.MAX_BYTES*4/3+8)throw new IOException("图片过大");photos.add(android.util.Base64.decode(encoded,android.util.Base64.DEFAULT));}}
                    helper.saveActivityWithPhotos(0,r.getString("title"),r.getString("category"),r.getString("note"),r.getLong("start"),r.getLong("end"),null,photos);
                }
                JSONArray overrides=data.optJSONArray("sleepOverrides");
                if(overrides!=null)for(int i=0;i<overrides.length();i++)helper.overrideSleep(overrides.getString(i));
                long now=System.currentTimeMillis();helper.put("cursor",now);helper.put("state","{}");
                helper.put("restore_floor",now);helper.put("legacy_history",data.optInt("version")==1?1:data.optInt("legacyHistory",0));
                long trackingSince=data.optLong("trackingSince",now);
                helper.put("tracking_since",trackingSince);
                // Older backups did not store the import boundary. Use the known tracking
                // start conservatively rather than claiming complete earlier history.
                long importBegin=data.has("importBegin")?data.getLong("importBegin"):(trackingSince>0?trackingSince:now);
                if(importBegin<0)throw new IOException("备份包含无效采集范围");
                if(importBegin==0)importBegin=trackingSince>0?trackingSince:now;
                helper.put("import_begin",importBegin);
                helper.gap(data.optLong("exportedAt",now),now,"备份导出至恢复期间未采集");
                db.setTransactionSuccessful();
            } finally {db.endTransaction();}
            // Inventory is refreshed on the next normal sync; the restored transaction is already committed.
        }
    }
}
