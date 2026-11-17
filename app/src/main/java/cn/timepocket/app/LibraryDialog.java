package cn.timepocket.app;

import android.app.*;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

/** Search saved history in a shared sheet; opening a result never changes stored data. */
public final class LibraryDialog extends DialogFragment {
    private String mode,query="",category;
    private LocalDate start,end;private int limit=50;
    private LinearLayout list,panel;private TextView dates,categoryButton,resultCount,reset;private EditText input;private View clearQuery;
    static LibraryDialog create(String mode){LibraryDialog d=new LibraryDialog();Bundle b=new Bundle();b.putString("mode",mode);d.setArguments(b);return d;}
    @Override public Dialog onCreateDialog(Bundle saved){
        Bundle b=saved==null?getArguments():saved;mode=b.getString("mode");query=b.getString("query","");category=b.getString("category");limit=b.getInt("limit",50);
        if(b.getString("start")!=null){start=LocalDate.parse(b.getString("start"));end=LocalDate.parse(b.getString("end"));}
        panel=column();panel.setPadding(dp(20),dp(16),dp(20),dp(16));panel.setBackground(surface(UiTokens.PAPER,26));panel.setFocusableInTouchMode(true);panel.setClipToOutline(true);
        LinearLayout header=row();TextView title=label(mode.equals("notes")?"查找手记":mode.equals("footprints")?"选择应用":"全部应用",20,UiTokens.INK,true);title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));header.addView(icon(ArtViews.ActionIcon.CLOSE,"关闭查找",this::dismiss),new LinearLayout.LayoutParams(dp(48),dp(48)));panel.addView(header);space(panel,8);
        LinearLayout search=row();search.setBackground(surface(UiTokens.SURFACE,16));search.setPadding(dp(12),0,0,0);search.addView(new ArtViews.ActionIcon(getActivity(),ArtViews.ActionIcon.SEARCH),new LinearLayout.LayoutParams(dp(20),dp(20)));
        input=new EditText(getActivity());input.setSingleLine(true);input.setTextSize(15);input.setHint(mode.equals("notes")?"搜索标题或备注":"应用名称或包名");input.setContentDescription(mode.equals("notes")?"搜索手记":"搜索全部应用");input.setText(query);input.setMinHeight(dp(52));input.setTextColor(UiTokens.INK);input.setHintTextColor(UiTokens.MUTED);input.setPadding(dp(10),dp(10),0,dp(10));input.setBackgroundColor(Color.TRANSPARENT);search.addView(input,new LinearLayout.LayoutParams(0,-2,1));clearQuery=icon(ArtViews.ActionIcon.CLOSE,"清除搜索内容",()->input.setText(""));search.addView(clearQuery,new LinearLayout.LayoutParams(dp(48),dp(48)));panel.addView(search);
        LinearLayout content=column();
        if(mode.equals("notes")){
            space(content,8);LinearLayout filters=compact()?column():row();
            categoryButton=action("",()->new PocketDialogs.Builder(getActivity()).setTitle("手记分类").setItems(categoryNames(),(d,w)->{category=w==0?null:PocketDb.CATEGORIES[w-1];limit=50;refresh();}).show());
            dates=action("",()->((MainActivity)getActivity()).pickHistoryRange("library",start,end));
            filters.addView(categoryButton,compact()?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1));View gap=new View(getActivity());filters.addView(gap,new LinearLayout.LayoutParams(compact()?1:dp(8),compact()?dp(2):1));filters.addView(dates,compact()?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1));content.addView(filters);
        }
        LinearLayout status=row();resultCount=label("",12,UiTokens.MUTED,false);resultCount.setMinHeight(dp(48));resultCount.setGravity(Gravity.CENTER_VERTICAL);status.addView(resultCount,new LinearLayout.LayoutParams(0,-2,1));reset=action("清除筛选",()->{category=null;start=end=null;limit=50;refresh();});reset.setTextSize(12);status.addView(reset);content.addView(status);
        ScrollView scroll=new ScrollView(getActivity()){@Override protected void onMeasure(int w,int h){int cap=dp(400);if(MeasureSpec.getMode(h)!=MeasureSpec.UNSPECIFIED)cap=Math.min(cap,MeasureSpec.getSize(h));super.onMeasure(w,MeasureSpec.makeMeasureSpec(cap,MeasureSpec.AT_MOST));}};scroll.setVerticalScrollBarEnabled(false);
        list=column();content.addView(list);scroll.addView(content);panel.addView(scroll,new LinearLayout.LayoutParams(-1,-2,1));
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int before,int count){query=s.toString();limit=50;refresh();}});
        refresh();Dialog dialog=new Dialog(getActivity());dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);dialog.getWindow().setWindowAnimations(R.style.PocketSheetMotion);dialog.setContentView(panel);return dialog;
    }
    @Override public void onStart(){super.onStart();Window w=getDialog().getWindow();w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(20),dp(520)),ViewGroup.LayoutParams.WRAP_CONTENT);w.setGravity(Gravity.BOTTOM);WindowManager.LayoutParams a=w.getAttributes();a.y=dp(10);a.dimAmount=.32f;w.setAttributes(a);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);panel.requestFocus();}
    void setRange(LocalDate a,LocalDate b){start=a;end=b;limit=50;refresh();}
    void refresh(){
        if(list==null)return;list.removeAllViews();PocketDb db=PocketDb.get(getActivity());boolean more;int count;
        clearQuery.setVisibility(query.isEmpty()?View.INVISIBLE:View.VISIBLE);reset.setVisibility(category!=null||start!=null?View.VISIBLE:View.GONE);
        if(mode.equals("notes")){
            categoryButton.setText(category==null?"全部分类 ▾":category+" ▾");dates.setText(start==null?"全部日期 ▾":shortDate(start)+" — "+shortDate(end)+" ▾");dates.setContentDescription(start==null?"筛选手记日期":start+" 至 "+end+"，更改日期");tone(categoryButton,category!=null);tone(dates,start!=null);
            var rows=db.findActivities(query,category,start==null?0:at(start),end==null?Long.MAX_VALUE:at(end.plusDays(1)),limit+1);more=rows.size()>limit;count=Math.min(rows.size(),limit);
            for(var item:rows.subList(0,count)){String date=Instant.ofEpochMilli(item.start()).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"));TextView badge=label(item.category().substring(0,1),15,UiTokens.FOREST,true);badge.setGravity(Gravity.CENTER);badge.setBackground(surface(UiTokens.GREEN,12));result(item.title(),date+" · "+item.category(),badge,()->((MainActivity)getActivity()).openFoundActivity(item));}
        }else{
            var rows=db.findApps(query,limit+1);more=rows.size()>limit;count=Math.min(rows.size(),limit);
            for(var item:rows.subList(0,count))result(item.label(),item.pkg(),((MainActivity)getActivity()).icon(item.pkg(),item.label()),()->{((MainActivity)getActivity()).chooseApp(item,mode);dismiss();});
        }
        resultCount.setText(count==0?"查找结果":"已显示 "+count+(mode.equals("notes")?" 条手记":" 个应用"));
        if(count==0){LinearLayout empty=column();empty.setPadding(dp(12),dp(24),dp(12),dp(28));boolean filtered=!query.isEmpty()||category!=null||start!=null;TextView title=label(filtered?"没有找到匹配记录":mode.equals("notes")?"还没有写下手记":"还没有应用记录",17,UiTokens.INK,true);title.setGravity(Gravity.CENTER);empty.addView(title);space(empty,10);TextView hint=label(filtered?"换个关键词，或清除筛选再试试。":mode.equals("notes")?"在“一天”中记下第一件小事吧。":"回到首页刷新，收集本机应用。",13,UiTokens.MUTED,false);hint.setGravity(Gravity.CENTER);empty.addView(hint);list.addView(empty);}
        if(more)list.addView(action("加载更多",()->{limit+=50;refresh();}));
    }
    private void result(String title,String detail,View badge,Runnable click){LinearLayout row=row();row.setPadding(dp(12),dp(14),dp(12),dp(14));row.setBackground(UiControls.background(getActivity(),UiTokens.SURFACE,0));row.addView(badge,new LinearLayout.LayoutParams(dp(38),dp(38)));LinearLayout words=column();words.setPadding(dp(12),0,dp(8),0);TextView name=label(title,15,UiTokens.INK,true);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);words.addView(name);space(words,5);TextView meta=label(detail,12,UiTokens.MUTED,false);meta.setMaxLines(2);meta.setEllipsize(TextUtils.TruncateAt.END);words.addView(meta);row.addView(words,new LinearLayout.LayoutParams(0,-2,1));row.addView(label("›",22,UiTokens.MUTED,false));row.setContentDescription(title+"，"+detail);row.setFocusable(true);row.setOnClickListener(v->click.run());list.addView(row,new LinearLayout.LayoutParams(-1,-2));space(list,8);}
    @Override public void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putString("mode",mode);b.putString("query",query);b.putString("category",category);b.putInt("limit",limit);if(start!=null){b.putString("start",start.toString());b.putString("end",end.toString());}}
    private long at(LocalDate d){return d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();}
    private String shortDate(LocalDate d){return d.format(DateTimeFormatter.ofPattern(d.getYear()==LocalDate.now().getYear()?"M/d":"yy/M/d"));}
    private String[] categoryNames(){String[] names=new String[PocketDb.CATEGORIES.length+1];names[0]="全部分类";System.arraycopy(PocketDb.CATEGORIES,0,names,1,PocketDb.CATEGORIES.length);return names;}
    private boolean compact(){return getResources().getConfiguration().screenWidthDp<320||getResources().getConfiguration().fontScale>1.5f;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(getActivity());l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(getActivity());l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView label(String s,int size,int color,boolean bold){TextView t=new TextView(getActivity());t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setIncludeFontPadding(false);if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;}
    private TextView action(String s,Runnable r){TextView t=label(s,13,UiTokens.INK,true);t.setMinHeight(dp(48));t.setGravity(Gravity.CENTER);t.setPadding(dp(10),dp(12),dp(10),dp(12));t.setBackground(UiControls.background(getActivity(),0,4));t.setFocusable(true);t.setOnClickListener(v->r.run());return t;}
    private void tone(TextView v,boolean selected){v.setSelected(selected);v.setBackground(UiControls.background(getActivity(),selected?UiTokens.GREEN:UiTokens.SURFACE,4));}
    private View icon(int kind,String description,Runnable action){FrameLayout hit=new FrameLayout(getActivity());hit.setBackground(UiControls.background(getActivity(),Color.TRANSPARENT,0));hit.setContentDescription(description);hit.setTooltipText(description);hit.setFocusable(true);hit.setOnClickListener(v->action.run());hit.addView(new ArtViews.ActionIcon(getActivity(),kind),new FrameLayout.LayoutParams(dp(20),dp(20),Gravity.CENTER));return hit;}
    private GradientDrawable surface(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private void space(LinearLayout parent,int n){parent.addView(new View(getActivity()),new LinearLayout.LayoutParams(1,dp(n)));}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
