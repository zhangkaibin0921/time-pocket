package cn.timepocket.app;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.net.Uri;
import android.view.Gravity;
import android.widget.*;
import java.util.*;

/** Owns temporary picker grants while images are copied off the UI thread. */
public final class PhotoImportActivity extends Activity {
    private ArrayList<String> uris;private boolean importing;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setGravity(Gravity.CENTER);panel.setBackgroundColor(UiTokens.PAPER);panel.addView(new ProgressBar(this));TextView text=new TextView(this);text.setText("正在准备图片…");text.setTextSize(16);text.setTextColor(UiTokens.INK);text.setPadding(24,24,24,24);panel.addView(text);setContentView(panel);
        if(state!=null){uris=state.getStringArrayList("uris");if(uris!=null)copy();return;}
        Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true).putExtra(Intent.EXTRA_LOCAL_ONLY,true);
        try{startActivityForResult(picker,1);}catch(ActivityNotFoundException e){Toast.makeText(this,"未找到系统图片选择器",Toast.LENGTH_LONG).show();finish();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=1)return;if(result!=RESULT_OK||data==null){finish();return;}
        LinkedHashSet<String> unique=new LinkedHashSet<>();if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)unique.add(data.getClipData().getItemAt(i).getUri().toString());else if(data.getData()!=null)unique.add(data.getData().toString());
        int max=Math.min(EntryPhotos.MAX_COUNT,getIntent().getIntExtra("remaining",EntryPhotos.MAX_COUNT));if(unique.size()>max){Toast.makeText(this,"这次最多可添加 "+max+" 张，请重新选择",Toast.LENGTH_LONG).show();finish();return;}
        uris=new ArrayList<>(unique);copy();
    }
    private void copy(){importing=true;new Thread(()->{ArrayList<String> refs=new ArrayList<>();String error=null;
        try{EntryPhotos.cleanStale(this);for(String uri:uris)refs.add(EntryPhotos.importImage(this,Uri.parse(uri)));}catch(Exception|OutOfMemoryError e){EntryPhotos.discard(this,refs);refs.clear();error="图片读取失败，请换一张后重试";}
        String message=error;runOnUiThread(()->{if(isFinishing()||isDestroyed()){EntryPhotos.discard(this,refs);return;}importing=false;if(message!=null)Toast.makeText(this,message,Toast.LENGTH_LONG).show();else setResult(RESULT_OK,new Intent().putStringArrayListExtra("photos",refs));finish();});},"entry-photo-import").start();}
    @Override public void onBackPressed(){if(!importing)super.onBackPressed();}
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);if(uris!=null)b.putStringArrayList("uris",uris);}
}
