package cn.timepocket.app;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Shared single-day and range calendar. Draft dates apply only on confirmation. */
public final class DateRangeDialog extends DialogFragment {
    private static final int INK = UiTokens.INK;
    private static final int FOREST = UiTokens.FOREST;
    private static final int MUTED = UiTokens.MUTED;
    private static final int DISABLED = 0xff8b968d;
    private static final YearMonth FIRST_MONTH=YearMonth.of(1900,1);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA);
    private static final DateTimeFormatter FULL_DAY = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA);
    private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEEE", Locale.CHINA);
    private LocalDate start,end;
    private boolean single;
    private YearMonth month;
    private TextView summary,monthLabel,confirm;
    private View previous,next;
    private Drawable confirmReady,confirmWaiting;
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
        LinearLayout content=column();
        content.setPadding(dp(12),dp(18),dp(12),dp(4));
        TextView title=label(single?"选择日期":"选择日期区间",20,INK);
        title.setTypeface(null,Typeface.BOLD);
        if(Build.VERSION.SDK_INT>=28)title.setAccessibilityHeading(true);
        title.setPadding(dp(8),0,dp(8),0);
        content.addView(title);
        space(content,6);
        summary=label("",14,FOREST);
        summary.setPadding(dp(8),0,dp(8),0);
        summary.setLineSpacing(dp(2),1f);
        summary.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(summary);
        space(content,14);

        LinearLayout calendar=column();
        calendar.setPadding(dp(4),dp(2),dp(4),dp(4));
        calendar.setBackground(round(UiTokens.SURFACE,18));
        LinearLayout navigation=row();
        previous=nav(ArtViews.ActionIcon.PREVIOUS,"上个月",-1);
        next=nav(ArtViews.ActionIcon.NEXT,"下个月",1);
        navigation.addView(previous,new LinearLayout.LayoutParams(dp(48),dp(48)));
        monthLabel=label("",16,INK);
        monthLabel.setGravity(Gravity.CENTER);
        monthLabel.setTypeface(null,Typeface.BOLD);
        monthLabel.setFocusable(true);
        monthLabel.setBackground(UiControls.background(getActivity(),Color.TRANSPARENT,0));
        monthLabel.setOnClickListener(v->jumpMonth());
        navigation.addView(monthLabel,new LinearLayout.LayoutParams(0,dp(48),1));
        navigation.addView(next,new LinearLayout.LayoutParams(dp(48),dp(48)));
        calendar.addView(navigation);

        View divider=new View(getActivity());
        divider.setBackgroundColor(UiTokens.OUTLINE);
        LinearLayout.LayoutParams dividerPosition=new LinearLayout.LayoutParams(-1,dp(1));
        dividerPosition.setMarginStart(dp(10));
        dividerPosition.setMarginEnd(dp(10));
        calendar.addView(divider,dividerPosition);
        LinearLayout weekdays=row();
        for(String day:new String[]{"一","二","三","四","五","六","日"}){
            TextView name=label(day,12,MUTED);
            name.setGravity(Gravity.CENTER);
            weekdays.addView(name,new LinearLayout.LayoutParams(0,dp(30),1));
        }
        calendar.addView(weekdays);
        for(int week=0;week<6;week++){
            LinearLayout line=row();
            for(int day=0;day<7;day++){
                DayCell cell=new DayCell();cells[week*7+day]=cell;
                line.addView(cell,new LinearLayout.LayoutParams(0,dp(48),1));
            }
            calendar.addView(line);
        }
        content.addView(calendar);
        ScrollView scroll=new ScrollView(getActivity());
        scroll.setFillViewport(false);
        scroll.addView(content);
        AlertDialog dialog=new PocketDialogs.Builder(getActivity()).setView(scroll)
                .setNegativeButton("取消",null).setPositiveButton(single?"确定日期":getArguments().getString("target","stats").equals("stats")?"查看统计":"应用日期",null).create();
        confirmReady=UiControls.background(getActivity(),FOREST,4);
        confirmWaiting=UiControls.background(getActivity(),UiTokens.GREEN,4);
        dialog.setOnShowListener(d->{
            PocketDialogs.style(dialog);
            confirm=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            confirm.setOnClickListener(v->applyDates());
            updateConfirm();
        });
        update();
        return dialog;
    }
    private void applyDates(){
        if(end==null)return;
        MainActivity activity=(MainActivity)getActivity();
        if(single)activity.applySingleDate(getArguments().getString("target"),start);
        else activity.applyHistoryRange(getArguments().getString("target","stats"),start,end);
        dismiss();
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
        new PocketDialogs.Builder(getActivity()).setTitle("跳转到哪一月？").setView(panel).setNegativeButton("取消",null).setPositiveButton("跳转",(d,w)->{month=YearMonth.of(year.getValue(),number.getValue());update();}).show();
    }
    private void update(){
        LocalDate today=LocalDate.now();
        boolean showYear=start.getYear()!=today.getYear() || (end!=null&&start.getYear()!=end.getYear());
        DateTimeFormatter format=showYear?FULL_DAY:DAY;
        String rangeStart=start.format(format);
        if(single)summary.setText(rangeStart+" "+start.format(WEEKDAY));
        else if(end==null)summary.setText(rangeStart+"  ·  请选择结束日期");
        else summary.setText((start.equals(end)?rangeStart:rangeStart+" – "+end.format(format))
                +"  ·  "+(ChronoUnit.DAYS.between(start,end)+1)+" 天");
        String calendarMonth=month.format(MONTH);
        monthLabel.setText(calendarMonth+" ▾");
        monthLabel.setContentDescription(calendarMonth+"，跳转到指定年月");
        previous.setEnabled(month.isAfter(FIRST_MONTH));
        previous.setAlpha(previous.isEnabled()?1f:.30f);
        next.setEnabled(month.isBefore(YearMonth.from(today)));
        next.setAlpha(next.isEnabled()?1f:.30f);
        LocalDate first=month.atDay(1);int offset=first.getDayOfWeek().getValue()-1;
        for(int i=0;i<cells.length;i++){
            int number=i-offset+1;DayCell cell=cells[i];
            cell.date=number>0&&number<=month.lengthOfMonth()?month.atDay(number):null;
            if(cell.date==null){cell.setText("");cell.setContentDescription(null);cell.setEnabled(false);cell.setSelected(false);cell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
            else{
                LocalDate date=cell.date;boolean endpoint=date.equals(start)||date.equals(end);
                boolean selected=inSelection(date);
                cell.today=date.equals(today);
                cell.setText(String.valueOf(number));cell.setEnabled(!date.isAfter(today));cell.setSelected(selected);
                cell.setTextColor(!cell.isEnabled()?DISABLED:endpoint?Color.WHITE:cell.today?FOREST:INK);
                cell.setTypeface(null,endpoint||cell.today?Typeface.BOLD:Typeface.NORMAL);
                cell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
                cell.setContentDescription(date+ (date.equals(today)?"，今天":"")+
                        (single?(date.equals(start)?"，已选日期":""):(date.equals(start)?"，开始日期":"")+(date.equals(end)?"，结束日期":selected&&!date.equals(start)?"，已选区间":""))+
                        (date.isAfter(today)?"，不可选择":""));
            }cell.invalidate();
        }
        updateConfirm();
    }
    private boolean inSelection(LocalDate date){
        return end==null?date.equals(start):!date.isBefore(start)&&!date.isAfter(end);
    }
    private void updateConfirm(){
        if(confirm==null)return;
        boolean ready=end!=null;
        confirm.setEnabled(ready);
        confirm.setBackground(ready?confirmReady:confirmWaiting);
        confirm.setPadding(dp(14),0,dp(14),0);
        confirm.setTextColor(ready?Color.WHITE:MUTED);
    }
    private View nav(int icon,String description,int direction){
        FrameLayout button=new FrameLayout(getActivity());
        button.addView(new ArtViews.ActionIcon(getActivity(),icon),new FrameLayout.LayoutParams(dp(20),dp(20),Gravity.CENTER));
        button.setContentDescription(description);
        button.setFocusable(true);
        button.setBackground(UiControls.background(getActivity(),Color.TRANSPARENT,0));
        button.setOnClickListener(v->{month=month.plusMonths(direction);update();});
        return button;
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
        private boolean today;
        DayCell(){super(getActivity());setGravity(Gravity.CENTER);setTextSize(14);setIncludeFontPadding(false);
            setFocusable(true);
            setForeground(UiControls.background(getActivity(),Color.TRANSPARENT,5));
            setOnClickListener(v->{if(date!=null)select(date);});}
        @Override protected void onDraw(Canvas canvas){
            if(date!=null){
                float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(dp(19),cx-1);
                if(end!=null&&isSelected()){
                    int weekday=date.getDayOfWeek().getValue(),number=date.getDayOfMonth();
                    boolean connectsLeft=weekday>1&&number>1&&date.isAfter(start);
                    boolean connectsRight=weekday<7&&number<month.lengthOfMonth()&&date.isBefore(end);
                    if(connectsLeft||connectsRight){
                        float radius=Math.min(r,dp(14));
                        paint.setColor(UiTokens.GREEN);
                        canvas.drawRoundRect(connectsLeft?-radius:cx-radius,cy-radius,
                                connectsRight?getWidth()+radius:cx+radius,cy+radius,radius,radius,paint);
                    }
                }
                if(date.equals(start)||date.equals(end)){
                    paint.setColor(FOREST);
                    canvas.drawCircle(cx,cy,r,paint);
                }else if(today){
                    paint.setColor(FOREST);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(1));
                    canvas.drawCircle(cx,cy,r-1,paint);
                    paint.setStyle(Paint.Style.FILL);
                }
            }super.onDraw(canvas);
        }
    }
}
