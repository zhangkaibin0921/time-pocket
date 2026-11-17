package cn.timepocket.app;

import android.app.*;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Edits both endpoints as one draft; only confirmation updates the activity editor. */
public final class TimeRangeDialog extends DialogFragment {
    private LocalDate date;
    private LocalTime originalFrom,originalTo;
    private boolean next;
    private final EditText[] fields=new EditText[4];
    private LinearLayout panel;
    private TextView sameDay,nextDay,summary,confirm;
    static TimeRangeDialog create(LocalDate date,LocalTime from,LocalTime to,boolean next){
        TimeRangeDialog dialog=new TimeRangeDialog();Bundle b=new Bundle();
        b.putString("date",date.toString());b.putString("from",from.toString());b.putString("to",to.toString());b.putBoolean("next",next);dialog.setArguments(b);return dialog;
    }
    @Override public Dialog onCreateDialog(Bundle saved){
        Bundle b=saved==null?getArguments():saved;date=LocalDate.parse(b.getString("date"));originalFrom=LocalTime.parse(b.getString("from"));originalTo=LocalTime.parse(b.getString("to"));next=b.getBoolean("next");
        panel=column();panel.setPadding(dp(20),dp(20),dp(20),dp(16));panel.setBackground(surface(UiTokens.PAPER,26));panel.setClipToOutline(true);panel.setFocusableInTouchMode(true);
        TextView title=label("选择时间段",20,UiTokens.INK);title.setTypeface(null,Typeface.BOLD);panel.addView(title);space(panel,6);
        panel.addView(label(date.format(DateTimeFormatter.ofPattern("M月d日 EEEE",Locale.CHINA))+" · 24 小时制",13,UiTokens.MUTED));space(panel,18);
        LinearLayout body=column();body.addView(timeRow("开始",0,originalFrom,b));space(body,10);body.addView(timeRow("结束",2,originalTo,b));space(body,14);
        LinearLayout days=row();sameDay=action("同一天",()->{next=false;update();});nextDay=action("次日",()->{next=true;update();});
        days.addView(sameDay,new LinearLayout.LayoutParams(0,dp(48),1));LinearLayout.LayoutParams nextLp=new LinearLayout.LayoutParams(0,dp(48),1);nextLp.leftMargin=dp(8);days.addView(nextDay,nextLp);body.addView(days);space(body,12);
        summary=label("",14,UiTokens.FOREST);summary.setPadding(dp(12),dp(12),dp(12),dp(12));summary.setBackground(surface(UiTokens.GREEN,14));summary.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(summary);space(body,10);
        body.addView(label("点数字修改时间",12,UiTokens.MUTED));
        ScrollView scroll=new ScrollView(getActivity()){@Override protected void onMeasure(int w,int h){int cap=dp(340);if(MeasureSpec.getMode(h)!=MeasureSpec.UNSPECIFIED)cap=Math.min(cap,MeasureSpec.getSize(h));super.onMeasure(w,MeasureSpec.makeMeasureSpec(cap,MeasureSpec.AT_MOST));}};
        scroll.setVerticalScrollBarEnabled(false);scroll.addView(body);panel.addView(scroll,new LinearLayout.LayoutParams(-1,-2,1));space(panel,16);
        LinearLayout footer=row();TextView cancel=action("取消",this::dismiss);footer.addView(cancel,new LinearLayout.LayoutParams(dp(72),dp(50)));
        confirm=action("确定时间段",()->{LocalTime a=time(0,originalFrom),z=time(2,originalTo);if(problem(a,z)!=null){update();return;}ActivityEditor editor=(ActivityEditor)getFragmentManager().findFragmentByTag("activity_editor");if(editor!=null)editor.applyTimeRange(a,z,next);dismiss();});
        confirm.setTextColor(Color.WHITE);confirm.setBackground(UiControls.background(getActivity(),UiTokens.FOREST,4));LinearLayout.LayoutParams confirmLp=new LinearLayout.LayoutParams(0,dp(50),1);confirmLp.leftMargin=dp(8);footer.addView(confirm,confirmLp);panel.addView(footer);
        for(EditText field:fields)field.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int before,int count){update();}public void afterTextChanged(Editable e){}});
        for(int i=0;i<fields.length;i++){final int index=i;fields[i].setImeOptions(i==3?android.view.inputmethod.EditorInfo.IME_ACTION_DONE:android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);fields[i].setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_NEXT&&index+1<fields.length){fields[index+1].requestFocus();return true;}if(action==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){((InputMethodManager)getActivity().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(),0);panel.requestFocus();return true;}return false;});}
        update();Dialog dialog=new Dialog(getActivity());dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);dialog.getWindow().setWindowAnimations(R.style.PocketSheetMotion);dialog.setContentView(panel);return dialog;
    }
    private View timeRow(String name,int index,LocalTime value,Bundle b){
        LinearLayout row=row();row.setPadding(dp(12),dp(12),dp(12),dp(12));row.setBackground(surface(UiTokens.SURFACE,18));
        TextView label=label(name,14,UiTokens.MUTED);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        for(int i=0;i<2;i++){
            if(i==1){TextView colon=label(":",24,UiTokens.MUTED);colon.setGravity(Gravity.CENTER);row.addView(colon,new LinearLayout.LayoutParams(dp(16),-2));}
            EditText field=new EditText(getActivity());field.setId(0x5050+index+i);field.setSingleLine(true);field.setInputType(InputType.TYPE_CLASS_NUMBER);field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2)});field.setSelectAllOnFocus(true);field.setGravity(Gravity.CENTER);field.setTextSize(24);field.setTextColor(UiTokens.INK);field.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));field.setPadding(0,dp(8),0,dp(8));field.setMinHeight(dp(56));field.setBackground(surface(UiTokens.PAPER,12));field.setContentDescription(name+(i==0?"小时":"分钟"));field.setText(b.getString("field"+(index+i),String.format(Locale.ROOT,"%02d",i==0?value.getHour():value.getMinute())));
            field.setOnFocusChangeListener((v,focus)->{field.setBackground(surface(focus?UiTokens.PEACH:UiTokens.PAPER,12));if(!focus&&field.getText().length()==1)field.setText("0"+field.getText());});fields[index+i]=field;row.addView(field,new LinearLayout.LayoutParams(dp(60),-2));
        }return row;
    }
    private LocalTime time(int index,LocalTime original){try{int h=Integer.parseInt(fields[index].getText().toString()),m=Integer.parseInt(fields[index+1].getText().toString());LocalTime time=LocalTime.of(h,m);return h==original.getHour()&&m==original.getMinute()?original:time;}catch(Exception e){return null;}}
    private String problem(LocalTime a,LocalTime b){
        if(a==null||b==null)return "小时填 00–23，分钟填 00–59";
        long start=date.atTime(a).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),end=date.plusDays(next?1:0).atTime(b).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        if(end<=start)return "结束需晚于开始；跨夜请选择“次日”";
        if(end>System.currentTimeMillis())return "这段时间还未结束，请调整结束时间";
        return null;
    }
    private void update(){
        if(confirm==null)return;for(TextView v:new TextView[]{sameDay,nextDay}){boolean selected=(v==nextDay)==next;v.setSelected(selected);v.setTextColor(selected?UiTokens.FOREST:UiTokens.MUTED);v.setBackground(UiControls.background(getActivity(),selected?UiTokens.GREEN:Color.TRANSPARENT,4));}
        LocalTime a=time(0,originalFrom),b=time(2,originalTo);String error=problem(a,b);confirm.setEnabled(error==null);confirm.setAlpha(error==null?1f:.4f);
        summary.setTextColor(error==null?UiTokens.FOREST:UiTokens.PRIMARY);summary.setBackground(surface(error==null?UiTokens.GREEN:UiTokens.PEACH,14));
        if(error!=null)summary.setText(error);else{long millis=Duration.between(date.atTime(a).atZone(ZoneId.systemDefault()),date.plusDays(next?1:0).atTime(b).atZone(ZoneId.systemDefault())).toMillis();summary.setText((next?"跨至次日 · ":"同一天 · ")+"共 "+MainActivity.duration(millis));}
    }
    @Override public void onStart(){super.onStart();Window w=getDialog().getWindow();w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(24),dp(420)),-2);w.setGravity(Gravity.BOTTOM);WindowManager.LayoutParams a=w.getAttributes();a.y=dp(10);a.dimAmount=.32f;w.setAttributes(a);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);panel.requestFocus();}
    @Override public void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putString("date",date.toString());b.putString("from",originalFrom.toString());b.putString("to",originalTo.toString());b.putBoolean("next",next);for(int i=0;i<4;i++)b.putString("field"+i,fields[i].getText().toString());}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(getActivity());l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(getActivity());l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView label(String text,int size,int color){TextView v=new TextView(getActivity());v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setIncludeFontPadding(false);return v;}
    private TextView action(String text,Runnable click){TextView v=label(text,14,UiTokens.MUTED);v.setGravity(Gravity.CENTER);v.setBackground(UiControls.background(getActivity(),Color.TRANSPARENT,0));v.setFocusable(true);v.setOnClickListener(w->click.run());return v;}
    private GradientDrawable surface(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private void space(LinearLayout l,int height){l.addView(new View(getActivity()),new LinearLayout.LayoutParams(1,dp(height)));}
}
