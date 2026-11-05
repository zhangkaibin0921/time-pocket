package cn.timepocket.app;

import android.app.*;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Saved entries open here; editing is an explicit secondary step. */
public final class ActivityDetail extends DialogFragment {
    private LinearLayout panel,content;
    private TextView edit;
    static ActivityDetail create(long id){ActivityDetail d=new ActivityDetail();Bundle b=new Bundle();b.putLong("id",id);d.setArguments(b);return d;}
    @Override public Dialog onCreateDialog(Bundle state){
        panel=column();panel.setPadding(dp(20),dp(20),dp(20),dp(16));panel.setBackground(surface(UiTokens.PAPER,26));panel.setClipToOutline(true);
        panel.addView(label("手记详情",20,UiTokens.INK,true));space(panel,18);
        content=column();ScrollView scroll=new ScrollView(getActivity()){@Override protected void onMeasure(int w,int h){int cap=dp(540);if(MeasureSpec.getMode(h)!=MeasureSpec.UNSPECIFIED)cap=Math.min(cap,MeasureSpec.getSize(h));super.onMeasure(w,MeasureSpec.makeMeasureSpec(cap,MeasureSpec.AT_MOST));}};scroll.setVerticalScrollBarEnabled(false);scroll.addView(content);panel.addView(scroll,new LinearLayout.LayoutParams(-1,-2,1));space(panel,12);
        LinearLayout footer=new LinearLayout(getActivity());footer.setGravity(Gravity.CENTER_VERTICAL);footer.addView(action("关闭",false,this::dismiss),new LinearLayout.LayoutParams(dp(72),dp(50)));
        edit=action("编辑手记",true,()->{PocketDb.ActivityEntry item=PocketDb.get(getActivity()).activity(getArguments().getLong("id"));if(item==null){refresh();return;}((MainActivity)getActivity()).editSavedActivity(item);});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(50),1);lp.leftMargin=dp(8);footer.addView(edit,lp);panel.addView(footer);refresh();
        Dialog dialog=new Dialog(getActivity());dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);dialog.setContentView(panel);return dialog;
    }
    void refresh(){
        if(content==null)return;content.removeAllViews();PocketDb db=PocketDb.get(getActivity());PocketDb.ActivityEntry item=db.activity(getArguments().getLong("id"));
        if(item==null){edit.setEnabled(false);content.addView(label("这条手记已不存在",16,UiTokens.MUTED,false));if(getDialog()!=null)dismiss();return;}
        edit.setEnabled(true);TextView title=label(item.title(),24,UiTokens.INK,true);title.setTextIsSelectable(true);content.addView(title);space(content,10);
        content.addView(label(item.category()+" · "+MainActivity.duration(item.end()-item.start()),13,UiTokens.FOREST,true));space(content,18);
        LinearLayout time=column();time.setPadding(dp(14),dp(12),dp(14),dp(12));time.setBackground(surface(UiTokens.GREEN,16));
        time.addView(label("开始  "+dateTime(item.start()),14,UiTokens.INK,false));space(time,8);time.addView(label("结束  "+dateTime(item.end()),14,UiTokens.INK,false));content.addView(time);
        if(!item.note().isEmpty()){space(content,20);content.addView(label("备注",13,UiTokens.MUTED,true));space(content,8);TextView note=label(item.note(),16,UiTokens.INK,false);note.setTextIsSelectable(true);note.setLineSpacing(dp(4),1);content.addView(note);}
        List<Long> photos=db.photoIds(item.id());if(!photos.isEmpty()){space(content,20);content.addView(label("图片 · "+photos.size()+" 张",13,UiTokens.MUTED,true));space(content,10);
            int index=0;for(long id:photos){String ref="saved:"+id;ImageView image=new ImageView(getActivity());image.setImageBitmap(EntryPhotos.thumbnail(getActivity(),ref,dp(320)));image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setBackground(surface(UiTokens.SURFACE,16));image.setClipToOutline(true);image.setContentDescription("查看图片 "+(++index));image.setFocusable(true);image.setOnClickListener(v->{if(getFragmentManager().findFragmentByTag("photo_preview")==null)PhotoPreview.create(ref).show(getFragmentManager(),"photo_preview");});content.addView(image,new LinearLayout.LayoutParams(-1,dp(210)));space(content,10);}
        }space(content,6);
    }
    @Override public void onStart(){super.onStart();Window w=getDialog().getWindow();w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(20),dp(520)),-2);w.setGravity(Gravity.BOTTOM);WindowManager.LayoutParams a=w.getAttributes();a.y=dp(10);a.dimAmount=.32f;w.setAttributes(a);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);}
    private String dateTime(long at){return Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"));}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(getActivity());l.setOrientation(1);return l;}
    private TextView label(String s,int size,int color,boolean bold){TextView t=new TextView(getActivity());t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setIncludeFontPadding(false);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    private TextView action(String text,boolean primary,Runnable click){TextView v=label(text,14,primary?Color.WHITE:UiTokens.MUTED,true);v.setGravity(Gravity.CENTER);v.setBackground(UiControls.background(getActivity(),primary?UiTokens.FOREST:Color.TRANSPARENT,4));v.setFocusable(true);v.setOnClickListener(w->click.run());return v;}
    private GradientDrawable surface(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private void space(LinearLayout l,int height){l.addView(new View(getActivity()),new LinearLayout.LayoutParams(1,dp(height)));}
}
