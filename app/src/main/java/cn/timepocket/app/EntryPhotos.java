package cn.timepocket.app;

import android.content.Context;
import android.graphics.*;
import android.media.ExifInterface;
import android.net.Uri;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** Small, private image copies; database BLOBs commit atomically with the note. */
final class EntryPhotos {
    static final int MAX_COUNT=3,MAX_BYTES=256*1024;
    static File directory(Context c){File f=new File(c.getCacheDir(),"entry-photos");f.mkdirs();return f;}
    static void cleanStale(Context c){File[] files=directory(c).listFiles();if(files!=null)for(File f:files)if(f.getName().endsWith(".jpg")&&f.lastModified()<System.currentTimeMillis()-7L*24*3600*1000)f.delete();}
    static File draft(Context c,String ref){if(!ref.matches("draft:[a-f0-9-]{36}"))throw new IllegalArgumentException("无效图片");return new File(directory(c),ref.substring(6)+".jpg");}
    static byte[] read(Context c,String ref) throws Exception {
        byte[] data=ref.startsWith("saved:")?PocketDb.get(c).photo(Long.parseLong(ref.substring(6))):Files.readAllBytes(draft(c,ref).toPath());
        if(data==null||data.length==0||data.length>MAX_BYTES)throw new IOException("图片不存在，请重新选择");return data;
    }
    static void discard(Context c,Collection<String> refs){for(String ref:refs)if(ref.startsWith("draft:"))draft(c,ref).delete();}
    static String importImage(Context c,Uri uri) throws Exception {
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        try(InputStream in=c.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,options);}
        if(options.outWidth<=0||options.outHeight<=0)throw new IOException("无法读取这张图片");
        options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>1600)options.inSampleSize*=2;
        options.inJustDecodeBounds=false;Bitmap bitmap;
        try(InputStream in=c.getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(in,null,options);}
        if(bitmap==null)throw new IOException("无法读取这张图片");
        try {
            int orientation=1;try(InputStream in=c.getContentResolver().openInputStream(uri)){orientation=new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);}catch(IOException ignored){}
            Matrix matrix=new Matrix();switch(orientation){case 2:matrix.setScale(-1,1);break;case 3:matrix.setRotate(180);break;case 4:matrix.setScale(1,-1);break;case 5:matrix.setRotate(90);matrix.postScale(-1,1);break;case 6:matrix.setRotate(90);break;case 7:matrix.setRotate(-90);matrix.postScale(-1,1);break;case 8:matrix.setRotate(-90);break;}
            if(!matrix.isIdentity()){Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);if(rotated!=bitmap){bitmap.recycle();bitmap=rotated;}}
            // Flatten transparent images against white before JPEG encoding.
            Bitmap flat=Bitmap.createBitmap(bitmap.getWidth(),bitmap.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(flat);canvas.drawColor(Color.WHITE);canvas.drawBitmap(bitmap,0,0,null);bitmap.recycle();bitmap=flat;
            byte[] bytes=null;for(int attempt=0;attempt<7;attempt++){
                ByteArrayOutputStream out=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,attempt==0?85:75,out);bytes=out.toByteArray();if(bytes.length<=MAX_BYTES)break;
                Bitmap reduced=Bitmap.createScaledBitmap(bitmap,Math.max(1,bitmap.getWidth()*3/4),Math.max(1,bitmap.getHeight()*3/4),true);bitmap.recycle();bitmap=reduced;
            }
            validate(bytes);String ref="draft:"+UUID.randomUUID();Files.write(draft(c,ref).toPath(),bytes);return ref;
        } finally {bitmap.recycle();}
    }
    static void validate(byte[] bytes){
        if(bytes==null||bytes.length==0||bytes.length>MAX_BYTES)throw new IllegalArgumentException("图片大小无效");
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);
        if(o.outWidth<1||o.outHeight<1||o.outWidth>1600||o.outHeight>1600)throw new IllegalArgumentException("图片内容无效");
        Bitmap check=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(check==null)throw new IllegalArgumentException("图片内容无效");check.recycle();
    }
    static Bitmap thumbnail(Context c,String ref,int size){try{byte[] bytes=read(c,ref);BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>size*2)o.inSampleSize*=2;o.inJustDecodeBounds=false;return BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);}catch(Exception e){return null;}}
    private EntryPhotos(){}
}
