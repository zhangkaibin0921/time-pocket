package cn.timepocket.app;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** Shared single-day and range calendar. Draft dates apply only on confirmation. */
public final class DateRangeDialog extends DialogFragment {
    private static final int INK=ArtViews.INK,CORAL=UiTokens.PRIMARY,WHITE=0xffffffff,
            MUTED=UiTokens.MUTED,PEACH=0xffffe6d8;
    private static final YearMonth FIRST_MONTH=YearMonth.of(1900,1);
    private LocalDate start,end;
    private boolean single;
    private YearMonth month;
    private TextView startLabel,endLabel,hint,monthLabel,previous,next;
    private LinearLayout grid;
    private final DayCell[] cells=new DayCell[42];

    public static DateRangeDialog create(LocalDate start,LocalDate end){
        DateRangeDialog dialog=new DateRangeDialog();Bundle args=new Bundle();
        args.putString("start",start.toString());args.putString("end",end.toString());
        dialog.setArguments(args);return dialog;
    }
    static DateRangeDialog single(LocalDate date,String target){
        DateRangeDialog dialog=create(date,date);dialog.getArguments().putBoolean("single",true);dialog.getArguments().putString("target",target);return dialog;
    }
    @Override public Dialog onCreateDialog(Bundle saved){
        Bundle state=saved==null?getArguments():saved;single=getArguments().getBoolean("single",false);
        start=LocalDate.parse(state.getString("start"));
        String last=state.getString("end");end=last==null?null:LocalDate.parse(last);
        month=YearMonth.parse(state.getString("month",YearMonth.from(end==null?start:end).toString()));
        LinearLayout content=column();content.setPadding(dp(16),dp(20),dp(16),dp(4));
        TextView title=label(single?"选择日期":"选一段时光",20,INK);title.setTypeface(null,Typeface.BOLD);
        content.addView(title);space(content,16);
        LinearLayout dates=row();startLabel=label("",14,INK);endLabel=label("",14,INK);
        for(TextView value:new TextView[]{startLabel,endLabel}){value.setPadding(dp(12),dp(10),dp(8),dp(10));value.setBackground(round(PEACH,14));}
        dates.addView(startLabel,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=label("→",18,MUTED);arrow.setGravity(Gravity.CENTER);if(!single)dates.addView(arrow,new LinearLayout.LayoutParams(dp(28),-2));
        if(!single)dates.addView(endLabel,new LinearLayout.LayoutParams(0,-2,1));content.addView(dates);space(content,12);
        hint=label("",12,MUTED);hint.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);content.addView(hint);space(content,12);
        LinearLayout navigation=row();navigation.setBackground(round(ArtViews.PAPER,18));previous=nav("‹","上个月",-1);next=nav("›","下个月",1);
        navigation.addView(previous,new LinearLayout.LayoutParams(dp(48),dp(48)));
        monthLabel=label("",17,INK);monthLabel.setGravity(Gravity.CENTER);monthLabel.setContentDescription("跳转到指定年月");monthLabel.setBackground(UiControls.background(getActivity(),android.graphics.Color.TRANSPARENT,0));monthLabel.setOnClickListener(v->jumpMonth());navigation.addView(monthLabel,new LinearLayout.LayoutParams(0,dp(48),1));
        navigation.addView(next,new LinearLayout.LayoutParams(dp(48),dp(48)));content.addView(navigation);
        LinearLayout weekdays=row();for(String day:new String[]{"一","二","三","四","五","六","日"}){
            TextView name=label(day,12,MUTED);name.setGravity(Gravity.CENTER);weekdays.addView(name,new LinearLayout.LayoutParams(0,dp(30),1));
        }content.addView(weekdays);
        grid=column();for(int week=0;week<6;week++){
            LinearLayout line=row();for(int day=0;day<7;day++){
                DayCell cell=new DayCell();cells[week*7+day]=cell;
                line.addView(cell,new LinearLayout.LayoutParams(0,dp(48),1));
            }grid.addView(line);
        }content.addView(grid);
        ScrollView scroll=new ScrollView(getActivity());scroll.setFillViewport(false);scroll.addView(content);
        AlertDialog dialog=new PocketDialogs.Builder(getActivity()).setView(scroll)
                .setNegativeButton("取消",null).setPositiveButton(single?"确定日期":getArguments().getString("target","stats").equals("stats")?"查看统计":"应用日期",null).create();
        update();return dialog;
    }
    @Override public void onStart(){
        super.onStart();AlertDialog dialog=(AlertDialog)getDialog();
        if(dialog.getWindow()!=null){
            dialog.getWindow().setBackgroundDrawable(round(UiTokens.PAPER,24));
            dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(24),dp(420)),ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(end==null)return;
            if(single)((MainActivity)getActivity()).applySingleDate(getArguments().getString("target"),start);
            else ((MainActivity)getActivity()).applyHistoryRange(getArguments().getString("target","stats"),start,end);dismiss();
        });update();
    }
    @Override public void onSaveInstanceState(Bundle state){
        super.onSaveInstanceState(state);state.putString("start",start.toString());
        state.putString("end",end==null?null:end.toString());state.putString("month",month.toString());
    }
    private void select(LocalDate date){
        if(single){start=date;end=date;}
        else if(end!=null){start=date;end=null;}
        else if(date.isBefore(start)){end=start;start=date;}
        else end=date;
        update();
    }
    private void jumpMonth(){
        YearMonth latest=YearMonth.now();LinearLayout panel=row();panel.setPadding(dp(20),dp(12),dp(20),0);
        NumberPicker year=new NumberPicker(getActivity()),number=new NumberPicker(getActivity());
        year.setMinValue(FIRST_MONTH.getYear());year.setMaxValue(latest.getYear());year.setValue(month.getYear());year.setWrapSelectorWheel(false);year.setContentDescription("选择年份");
        number.setMinValue(1);number.setMaxValue(month.getYear()==latest.getYear()?latest.getMonthValue():12);number.setValue(month.getMonthValue());number.setWrapSelectorWheel(false);number.setContentDescription("选择月份");
        // Wheel input avoids a transient invalid year/month while editing text.
        year.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);number.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        year.setOnValueChangedListener((picker,old,value)->number.setMaxValue(value==latest.getYear()?latest.getMonthValue():12));
        panel.addView(year,new LinearLayout.LayoutParams(0,-2,1));panel.addView(label("年",14,MUTED));panel.addView(number,new LinearLayout.LayoutParams(0,-2,1));panel.addView(label("月",14,MUTED));
        AlertDialog picker=new PocketDialogs.Builder(getActivity()).setTitle("跳转到哪一月？").setView(panel).setNegativeButton("取消",null).setPositiveButton("跳转",(d,w)->{month=YearMonth.of(year.getValue(),number.getValue());update();}).show();

    }
    private void update(){
        DateTimeFormatter format=DateTimeFormatter.ofPattern("yyyy.MM.dd");
        startLabel.setText((single?"选中日期\n":"开始日期\n")+start.format(format));endLabel.setText("结束日期\n"+(end==null?"待选择":end.format(format)));
        hint.setText(single?"点击日期选择，确认后生效":end==null?"再点一天，选定结束日期":"共 "+(ChronoUnit.DAYS.between(start,end)+1)+" 天 · 点日期可重新选择");
        monthLabel.setText(month.getYear()+"年 "+month.getMonthValue()+"月 ▾");
        previous.setEnabled(month.isAfter(FIRST_MONTH));previous.setAlpha(previous.isEnabled()?1f:.25f);
        next.setEnabled(month.isBefore(YearMonth.now()));next.setAlpha(next.isEnabled()?1f:.25f);
        LocalDate first=month.atDay(1);int offset=first.getDayOfWeek().getValue()-1;
        LocalDate today=LocalDate.now();
        for(int i=0;i<cells.length;i++){
            int number=i-offset+1;DayCell cell=cells[i];
            cell.date=number>0&&number<=month.lengthOfMonth()?month.atDay(number):null;
            if(cell.date==null){cell.setText("");cell.setContentDescription(null);cell.setEnabled(false);cell.setSelected(false);cell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
            else{
                LocalDate date=cell.date;boolean endpoint=date.equals(start)||date.equals(end);
                boolean selected=end==null?date.equals(start):!date.isBefore(start)&&!date.isAfter(end);
                cell.setText(String.valueOf(number));cell.setEnabled(!date.isAfter(today));cell.setSelected(selected);
                cell.setTextColor(!cell.isEnabled()?0xffc8ccc7:endpoint?WHITE:INK);
                cell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
                cell.setContentDescription(date+ (date.equals(today)?"，今天":"")+
                        (single?(date.equals(start)?"，已选日期":""):(date.equals(start)?"，开始日期":"")+(date.equals(end)?"，结束日期":selected&&!date.equals(start)?"，已选区间":""))+
                        (date.isAfter(today)?"，不可选择":""));
            }cell.invalidate();
        }
        AlertDialog dialog=(AlertDialog)getDialog();
        if(dialog!=null&&dialog.getButton(AlertDialog.BUTTON_POSITIVE)!=null){
            TextView confirm=dialog.getButton(AlertDialog.BUTTON_POSITIVE);confirm.setEnabled(end!=null);confirm.setAlpha(end==null?.35f:1f);
        }
    }
    private TextView nav(String text,String description,int direction){
        TextView button=label(text,28,INK);button.setGravity(Gravity.CENTER);button.setContentDescription(description);
        button.setBackground(UiControls.background(getActivity(),android.graphics.Color.TRANSPARENT,0));button.setOnClickListener(v->{month=month.plusMonths(direction);update();});return button;
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView label(String text,int size,int color){TextView v=new TextView(getActivity());v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setIncludeFontPadding(false);return v;}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private LinearLayout column(){LinearLayout v=new LinearLayout(getActivity());v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(getActivity());v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private void space(LinearLayout parent,int size){parent.addView(new View(getActivity()),new LinearLayout.LayoutParams(1,dp(size)));}

    private final class DayCell extends TextView {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private LocalDate date;
        DayCell(){super(getActivity());setGravity(Gravity.CENTER);setTextSize(14);setIncludeFontPadding(false);
            setFocusable(true);setOnClickListener(v->{if(date!=null)select(date);});}
        @Override protected void onDraw(Canvas canvas){
            if(date!=null){
                float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(dp(19),cx-1);
                boolean first=date.equals(start),last=date.equals(end);
                if(end!=null&&!date.isBefore(start)&&!date.isAfter(end)&&!start.equals(end)){
                    paint.setColor(PEACH);canvas.drawRect(first?cx:0,cy-r,last?cx:getWidth(),cy+r,paint);
                }
                if(first||last){paint.setColor(CORAL);canvas.drawCircle(cx,cy,r,paint);}
                else if(date.equals(LocalDate.now())){
                    paint.setColor(CORAL);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));canvas.drawCircle(cx,cy,r-1,paint);paint.setStyle(Paint.Style.FILL);
                }
            }super.onDraw(canvas);
        }
    }
}
