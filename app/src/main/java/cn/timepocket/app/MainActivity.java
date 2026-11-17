package cn.timepocket.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class MainActivity extends Activity {
    private static final long HIDDEN_GAP_MAX_MS=2*60_000L;
    private static final long RESUME_RECHECK_MS = 6_000;
    private static final long FOREGROUND_REFRESH_MS = 60_000;
    private static final long DISPLAY_IDLE_MS = 400;
    private static final int PAPER=ArtViews.PAPER,INK=ArtViews.INK,CORAL=ArtViews.CORAL,MINT=ArtViews.MINT,
            MUTED=UiTokens.MUTED,WHITE=UiTokens.SURFACE,LINE=UiTokens.OUTLINE,PEACH=UiTokens.PEACH,GREEN=UiTokens.GREEN,PRIMARY=UiTokens.PRIMARY;
    private PocketDb db;private LinearLayout root,body,rankList;private ScrollView scroll;
    private int tab=0,range=0;private boolean busy=false;
    private String footFilter=null,search="",detailPkg=null,detailLabel=null;
    private LocalDate customStart=LocalDate.now().minusDays(6),customEnd=LocalDate.now();
    private LocalDate journalDay=LocalDate.now();
    private LocalDate selectedFirst,selectedLast;
    private int selectedHour=-1;
    private int rankReturnScrollY = -1;
    private int pendingScrollY = -1;
    private ArtViews.Chart activeChart;private LinearLayout chartSelection;
    private TextView chartPeriod,rankTitle,rankScope,detailValue,detailScope;private EditText rankInput;
    private List<PocketDb.AppUsage> rankRows=List.of();private long rankTotal;
    private int dayLimit=60,footLimit=50;
    private String footPkg,footLabel;private LocalDate footStart,footEnd;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refreshTick = () -> refresh(false);
    private final Runnable displayTick = this::applyCollectedData;
    private boolean foreground, resumeRecheck, refreshQueued, manualRefreshQueued, displayPending;
    private boolean touching;
    private long lastTouch;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);db=PocketDb.get(this);
        if(saved!=null) {tab=saved.getInt("tab");range=saved.getInt("range");customStart=LocalDate.parse(saved.getString("start",customStart.toString()));customEnd=LocalDate.parse(saved.getString("end",customEnd.toString()));detailPkg=saved.getString("pkg");detailLabel=saved.getString("label");}
        if(saved!=null){journalDay=LocalDate.parse(saved.getString("journal_day",journalDay.toString()));footPkg=saved.getString("foot_pkg");footLabel=saved.getString("foot_label");footFilter=saved.getString("foot_kind");footLimit=saved.getInt("foot_limit",50);if(saved.getString("foot_start")!=null){footStart=LocalDate.parse(saved.getString("foot_start"));footEnd=LocalDate.parse(saved.getString("foot_end"));}}
        if(saved!=null){search=saved.getString("usage_search","");if(saved.getString("selected_first")!=null){selectedFirst=LocalDate.parse(saved.getString("selected_first"));selectedLast=LocalDate.parse(saved.getString("selected_last"));selectedHour=saved.getInt("selected_hour",-1);}}
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        getWindow().setStatusBarColor(PAPER);getWindow().setNavigationBarColor(PAPER);
        int rankingScroll = saved == null ? -1 : saved.getInt("ranking_scroll", -1);
        if (detailPkg != null) rankReturnScrollY = rankingScroll;
        render();
        if (tab == 0 && detailPkg == null) restoreScrollAfterRender(rankingScroll);
    }
    @Override protected void onSaveInstanceState(Bundle b) {
        super.onSaveInstanceState(b);
        b.putInt("tab",tab);b.putInt("range",range);b.putString("usage_search",search);
        if(selectedFirst!=null){b.putString("selected_first",selectedFirst.toString());b.putString("selected_last",selectedLast.toString());b.putInt("selected_hour",selectedHour);}
        b.putString("start",customStart.toString());b.putString("end",customEnd.toString());
        b.putString("pkg",detailPkg);b.putString("label",detailLabel);
        if (tab == 0) b.putInt("ranking_scroll", detailPkg == null ? currentScrollY() : rankReturnScrollY);
        b.putString("journal_day",journalDay.toString());b.putString("foot_pkg",footPkg);b.putString("foot_label",footLabel);b.putString("foot_kind",footFilter);b.putInt("foot_limit",footLimit);
        if(footStart!=null){b.putString("foot_start",footStart.toString());b.putString("foot_end",footEnd.toString());}
    }
    @Override protected void onResume() {
        super.onResume();
        foreground = true;
        applyCollectedData();
        resumeRecheck = true;
        refresh(false);
    }

    @Override protected void onPause() {
        super.onPause();
        foreground = false;
        touching = false;
        refreshQueued = false;
        manualRefreshQueued = false;
        handler.removeCallbacks(refreshTick);
        handler.removeCallbacks(displayTick);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && displayPending) handler.post(displayTick);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) touching = true;
        else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) touching = false;
        lastTouch = SystemClock.uptimeMillis();
        return super.dispatchTouchEvent(event);
    }

    @Override public void onBackPressed() {
        if (detailPkg != null) {
            returnFromAppDetails();
        } else if (tab != 0) {
            tab = 0;
            renderNavigation();
        } else {
            super.onBackPressed();
        }
    }
    private void refresh(boolean showToast) {
        if (!foreground) return;
        handler.removeCallbacks(refreshTick);
        if (busy) {
            refreshQueued = true;
            manualRefreshQueued |= showToast;
            return;
        }
        busy = true;
        PocketApp.IO.execute(() -> {
            boolean collected;
            try {
                Collector.syncForeground(this);
                collected = true;
            } catch (Exception error) {
                collected = false;
            }
            final boolean success = collected;
            runOnUiThread(() -> onRefreshFinished(success, showToast));
        });
    }

    private void onRefreshFinished(boolean success, boolean showToast) {
        busy = false;
        if (isFinishing() || isDestroyed()) return;
        displayPending |= success;
        if (!foreground) return;
        applyCollectedData();
        if (showToast) {
            String message = !success ? "暂未采集成功，请稍后重试"
                    : Collector.allowed(this) ? "已更新记录" : "先开启使用情况访问权限";
            toast(message);
        }
        if (refreshQueued) {
            boolean manual = manualRefreshQueued;
            refreshQueued = false;
            manualRefreshQueued = false;
            refresh(manual);
            return;
        }
        handler.postDelayed(refreshTick, resumeRecheck ? RESUME_RECHECK_MS : FOREGROUND_REFRESH_MS);
        resumeRecheck = false;
    }

    private void applyCollectedData() {
        handler.removeCallbacks(displayTick);
        if (!displayPending || !foreground || !hasWindowFocus() || isFinishing()) return;
        if (touching || SystemClock.uptimeMillis() - lastTouch < DISPLAY_IDLE_MS || keyboardVisible()) {
            handler.postDelayed(displayTick, DISPLAY_IDLE_MS);
            return;
        }
        int scrollY = currentScrollY();
        render();
        restoreScrollAfterRender(scrollY);
    }

    private int currentScrollY() {
        return pendingScrollY >= 0 ? pendingScrollY : scroll.getScrollY();
    }

    private void restoreScrollAfterRender(int scrollY) {
        if (scrollY <= 0) return;
        pendingScrollY = scrollY;
        ScrollView updated = scroll;
        updated.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View view, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                updated.removeOnLayoutChangeListener(this);
                if (scroll == updated) {
                    updated.scrollTo(0, scrollY);
                    pendingScrollY = -1;
                }
            }
        });
    }

    private boolean keyboardVisible() {
        WindowInsets insets = root.getRootWindowInsets();
        if (Build.VERSION.SDK_INT >= 30 && insets != null) {
            return insets.isVisible(WindowInsets.Type.ime());
        }
        Rect visible = new Rect();
        root.getWindowVisibleDisplayFrame(visible);
        return root.getRootView().getHeight() - visible.bottom > dp(120);
    }

    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private GradientDrawable border(int color,int radius){GradientDrawable d=bg(color,radius);d.setStroke(dp(1),LINE);return d;}
    private void ripple(View v,int fill,int radius){v.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x226b8d7c),bg(fill,radius),bg(WHITE,radius)));v.setClickable(true);v.setFocusable(true);}
    private void cardFeedback(View v){v.setForeground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x226b8d7c),null,bg(WHITE,20)));v.setFocusable(true);}
    private boolean compact(){return getResources().getConfiguration().screenWidthDp<360||getResources().getConfiguration().fontScale>1.15f;}
    private boolean stackMetrics() {
        var configuration = getResources().getConfiguration();
        return configuration.screenWidthDp < 310 || configuration.fontScale > 1.5f;
    }
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("tnum");t.setIncludeFontPadding(false);t.setLineSpacing(dp(3),1);if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;}
    private TextView durationValue(long millis,int maxSize,int color){
        TextView value=text(duration(millis),maxSize,color,true);value.setMaxLines(1);
        value.setAutoSizeTextTypeUniformWithConfiguration(16,maxSize,1,android.util.TypedValue.COMPLEX_UNIT_SP);return value;
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    private void space(LinearLayout l,int height){l.addView(new View(this),lp(1,height));}
    private void add(LinearLayout p,View v){p.addView(v,lp(-1,-2));}
    private TextView button(String label,int color,Runnable action){TextView t=text(label,14,color==CORAL?WHITE:INK,true);t.setGravity(Gravity.CENTER);t.setMinHeight(dp(48));t.setPadding(dp(14),dp(12),dp(14),dp(12));t.setBackground(UiControls.background(this,color==CORAL?UiTokens.FOREST:Color.TRANSPARENT,color==CORAL?4:0));t.setFocusable(true);t.setOnClickListener(v->action.run());return t;}
    private LinearLayout card(int color){LinearLayout c=column();c.setPadding(dp(20),dp(20),dp(20),dp(20));c.setBackground(color==WHITE?border(color,20):bg(color,24));return c;}
    private TextView pill(String label,int color,int fill){TextView t=text(label,12,color,true);t.setPadding(dp(9),dp(5),dp(9),dp(5));t.setBackground(bg(fill,9));return t;}
    private void heading(LinearLayout parent,String title,String caption){if(compact()){add(parent,text(title,18,INK,true));if(caption!=null){space(parent,5);add(parent,text(caption,12,MUTED,false));}return;}LinearLayout r=row();TextView h=text(title,18,INK,true);r.addView(h,new LinearLayout.LayoutParams(0,-2,1));if(caption!=null)r.addView(text(caption,11,MUTED,false));add(parent,r);}
    private View iconAction(int kind,String description,Runnable action){
        FrameLayout hit=new FrameLayout(this);hit.setMinimumWidth(dp(48));hit.setMinimumHeight(dp(48));
        hit.setBackground(UiControls.background(this,Color.TRANSPARENT,0));hit.setContentDescription(description);hit.setTooltipText(description);hit.setFocusable(true);
        hit.addView(new ArtViews.ActionIcon(this,kind),new FrameLayout.LayoutParams(dp(21),dp(21),Gravity.CENTER));hit.setOnClickListener(v->action.run());return hit;
    }
    private View helpButton(){return iconAction(ArtViews.ActionIcon.INFO,"查看页面说明",this::showPageHelp);}
    private void explain(String title,String message){AlertDialog dialog=new PocketDialogs.Builder(this).setTitle(title).setMessage(message).setPositiveButton("知道了",null).show();}
    private String usageHelp(){return "统计已确认结束的应用前台片段，缺少结束事件的尾段暂不计入。后台音乐和下载不计入，分屏时多个应用可能同时计时。\n\n今天从本地零点开始，近 7 天、近 30 天都包含今天，自选区间包含起止日期。平均每天按当前区间累计时长除以 7 或 30 个自然日计算，今天也计为一天；数据缺失会影响平均值。\n\n隔几天再打开时，最多向系统尝试补采近 7 天。系统可能只保留几天的事件，也可能清理已卸载应用的明细。本地已保存的用时会继续保留。\n\n"+coverage(bounds());}
    private void showPageHelp(){
        if(detailPkg!=null){explain("应用时长说明","卸载不会删除已保存的使用时长。同一包名重新安装后，会接着累计。应用分身、工作资料和不同包名版本暂不合并。\n\n"+usageHelp()+"\n\n应用包名："+detailPkg);return;}
        if(tab==0){explain("统计说明",usageHelp());return;}
        if(tab==3){explain("一天的记录说明","应用片段来自已保存的前台使用记录，可能延迟或遗漏。“应用使用时长”和“时光”采用同一口径：相加当天各应用的前台用时，包含未在时间线显示的短片段和桌面用时。分屏时应用各自计时。未记录不代表没用手机；有采集缺口的日期会提示记录不完整。\n\n点击未记录时段可以补记，也可点“记一件事”自由填写。手动记录可编辑、删除，可附最多 3 张图片，并随本地备份导出。图片以压缩副本保存在本机，取消编辑不会修改原记录。睡眠等活动可选择结束于次日。\n\n手动活动与应用使用可以重叠，两者分别保留。跨日片段只展示当天部分。\n\n睡眠默认按前晚 18:00 至当天 12:00 间最长的夜间停用段推测，也支持凌晨 6:00 前入睡。推测前与时间线一样，先忽略系统桌面及每天不足 1 分钟的应用片段；夜间短暂使用不会切断睡眠。需要入睡前和起床后都有筛选后保留的记录，缺少任一端时不推测；已知采集缺口或手动记录覆盖的夜晚不推测。点击睡眠可以调整或选择“不作为睡眠”，修改会保留并随备份保存。\n\n先隐藏系统桌面和当天不足 1 分钟的应用片段，再合并同一应用的相邻片段（间隔不超过 5 分钟）。中间仍有其他应用、手动活动或睡眠时不跨段合并。恰好 1 分钟的片段保留；手记和睡眠不受此筛选影响。卡片的用时按实际使用片段计算，合并间隔可在详情里补记。睡眠推测也采用时间线的筛选规则，手动调整过的睡眠保留。\n\n时间线按开始时间倒序排列，最近的时段在上方。今天只显示已过去的时间。两分钟以内（含两分钟）的未记录空隙收起显示，合并片段详情也不提供这些短间隔的补记入口；“时间线空白”是当天未被可见应用片段、手记或睡眠覆盖的时间。");return;}
        if(tab==1){explain("足迹说明","系统通知显示接收时间，清单补记显示发现安装或卸载的时间。实际操作时间可能更早。\n\n更新不计入足迹。两次检查之间装了又卸载的应用，只有收到系统通知才有机会记录。\n\n可按应用和日期筛选，点击单条足迹查看来源。");return;}
        explain("口袋说明","使用情况访问权限用于读取应用使用记录。后台约每 15 分钟尝试采集，系统省电可能延迟；可在“后台运行指引”中查看设置建议。\n\n导出备份可保存使用记录、足迹、手动活动、附带图片及近期原始事件，单份备份目前最多 50 MB。恢复会替换当前记录，操作前会再次确认。卸载时光口袋或清除数据前，请先导出备份。");
    }
    private View coverageHint(long[] b){
        String label=!Collector.allowed(this)?"等待授权":db.hasGap(b[0],b[1])||(db.number("import_begin")>0&&b[0]<db.number("import_begin"))?"记录不完整":db.number("last_sync")==0?"尚未采集":"更新于 "+Instant.ofEpochMilli(db.number("last_sync")).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"));
        TextView v=text(label+"  ⓘ",11,MUTED,false);v.setMinHeight(dp(36));v.setGravity(Gravity.CENTER_VERTICAL);v.setOnClickListener(w->showPageHelp());v.setContentDescription(label+"，点击查看说明");return v;
    }
    private void render(){
        displayPending = false;
        pendingScrollY = -1;
        if (detailPkg == null) rankReturnScrollY = -1;
        if(selectedFirst!=null){
            boolean valid=range==0
                    ? selectedHour>=0&&selectedHour<24&&selectedFirst.equals(LocalDate.now())
                    : selectedHour<0&&UsageBuckets.days(firstDay(),lastDay()).contains(new UsageBuckets.Days(selectedFirst,selectedLast));
            if(!valid) clearChartSelection();
        }
        root=column();root.setBackgroundColor(PAPER);root.setFitsSystemWindows(true);
        // Respect edge-to-edge enforced on Android 15 and gesture/navigation bars.
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        setContentView(root);root.requestApplyInsets();
        FrameLayout area=new FrameLayout(this);root.addView(area,new LinearLayout.LayoutParams(-1,0,1));
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
        int available=(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams centered=new FrameLayout.LayoutParams(Math.min(dp(600),getResources().getDisplayMetrics().widthPixels),-1,Gravity.CENTER_HORIZONTAL);area.addView(scroll,centered);
        body=column();body.setPadding(dp(available<350?16:24),dp(8),dp(available<350?16:24),dp(28));scroll.addView(body);
        if (detailPkg != null) detail();
        else if (tab == 0) dashboard();
        else if (tab == 1) footprints();
        else if (tab == 3) dayPage(area);
        else settings();
        LinearLayout nav=row();nav.setBaselineAligned(false);nav.setPadding(dp(8),dp(3),dp(8),dp(3));nav.setBackgroundColor(WHITE);
        String[] titles={"时光","一天","足迹","口袋"};int[] tabs={0,3,1,2};
        for(int i=0;i<4;i++){
            final int n=tabs[i];LinearLayout item=column();item.setGravity(Gravity.CENTER);item.setPadding(dp(4),dp(4),dp(4),dp(4));item.setMinimumHeight(dp(56));item.setSelected(tab==n);
            int color=tab==n?INK:MUTED;ripple(item,WHITE,16);
            FrameLayout indicator=new FrameLayout(this);indicator.setBackground(bg(tab==n?GREEN:WHITE,16));
            indicator.addView(new ArtViews.NavIcon(this,n,color),new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));
            LinearLayout.LayoutParams iconParams=lp(52,28);iconParams.gravity=Gravity.CENTER_HORIZONTAL;
            item.addView(indicator,iconParams);space(item,4);
            TextView label=text(titles[i],12,color,tab==n);label.setGravity(Gravity.CENTER);label.setSingleLine(true);label.setMinHeight(dp(18));label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            item.addView(label,lp(-1,-2));item.setContentDescription(titles[i]);
            item.setOnClickListener(v -> {
                boolean changedPage = tab != n || detailPkg != null;
                tab = n;
                detailPkg = null;
                search = "";
                if (changedPage) renderNavigation();
                else render();
            });nav.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        }
        divider(root, 0);
        root.addView(nav, lp(-1, -2));
    }
    private void renderNavigation() {
        render();
        UiMotion.reveal(scroll, 0, 6);
    }

    private View filterChip(String label,boolean selected,Runnable action){
        TextView chip=button(label,WHITE,action);chip.setGravity(Gravity.CENTER);chip.setTextSize(13);chip.setMaxLines(2);chip.setEllipsize(TextUtils.TruncateAt.END);chip.setSelected(selected);
        chip.setTextColor(selected?UiTokens.FOREST:INK);chip.setBackground(UiControls.background(this,selected?GREEN:WHITE,4));return chip;
    }
    private void filterPair(LinearLayout parent,View first,View second){
        LinearLayout pair=compact()?column():row();pair.addView(first,compact()?lp(-1,-2):new LinearLayout.LayoutParams(0,-2,1));
        pair.addView(new View(this),lp(compact()?1:8,compact()?2:1));pair.addView(second,compact()?lp(-1,-2):new LinearLayout.LayoutParams(0,-2,1));add(parent,pair);
    }
    private LinearLayout listPanel() {
        LinearLayout panel = column();
        panel.setBackground(bg(WHITE, 16));
        panel.setClipToOutline(true);
        return panel;
    }

    private void divider(LinearLayout parent, int inset) {
        View line = new View(this);
        line.setBackgroundColor(LINE);
        LinearLayout.LayoutParams position = new LinearLayout.LayoutParams(-1, 1);
        position.setMarginStart(dp(inset));
        parent.addView(line, position);
    }

    private void pageMenu(View anchor){
        PopupMenu menu=new PopupMenu(this,anchor);menu.getMenu().add(0,0,0,"页面说明");menu.getMenu().add(0,1,1,"刷新本地记录");
        if(tab==3)menu.getMenu().add(0,2,2,"睡眠推测设置");
        menu.setOnMenuItemClickListener(item->{if(item.getItemId()==0)showPageHelp();else if(item.getItemId()==1)refresh(true);else sleepSettings();return true;});menu.show();
    }
    private TextView pageTitle(String title){TextView label=text(title,23,INK,true);label.setSingleLine(true);label.setEllipsize(TextUtils.TruncateAt.END);label.setContentDescription(title);return label;}
    private void header(String title){
        LinearLayout r=row();r.setMinimumHeight(dp(48));r.addView(pageTitle(title),new LinearLayout.LayoutParams(0,-2,1));
        if(tab==0||tab==3)r.addView(iconAction(ArtViews.ActionIcon.SEARCH,tab==0?"查找全部应用（含已卸载）":"查找历史手记",()->openLibrary(tab==0?"apps":"notes")),lp(48,48));
        else r.addView(helpButton(),lp(48,48));
        View more=iconAction(ArtViews.ActionIcon.MORE,"更多操作",()->{});more.setOnClickListener(this::pageMenu);r.addView(more,lp(48,48));
        add(body,r);space(body,12);
    }
    private void dashboard(){
        header("时光口袋");
        if(!Collector.allowed(this)){
            LinearLayout welcome=card(GREEN);heading(welcome,"让时光，有迹可循",null);space(welcome,10);add(welcome,text("开启使用情况访问后开始统计。",14,INK,false));space(welcome,16);add(welcome,button("开启记录  →",CORAL,this::openUsage));add(body,welcome);space(body,20);
        }
        ranges();
        long[] bounds = bounds();
        List<PocketDb.AppUsage> ranks = ranking(bounds[0], bounds[1], null);
        long total = PocketDb.sumAppUsage(ranks);
        LinearLayout overview = card(WHITE);
        overview.setPadding(dp(18), dp(14), dp(18), dp(12));
        boolean stacked = stackMetrics();
        LinearLayout context = stacked ? column() : row();
        context.addView(text("应用使用时长", 13, MUTED, true),
                stacked ? lp(-1, -2) : new LinearLayout.LayoutParams(0, -2, 1));
        context.addView(coverageHint(bounds), lp(-2, -2));
        add(overview, context);
        add(overview, durationValue(total, compact() ? 32 : 38, UiTokens.FOREST));
        space(overview, 10);
        String companion = ranks.isEmpty() ? "暂无使用记录"
                : ranks.size() + " 个应用 · 最常相伴 " + ranks.get(0).label();
        TextView companionLabel = text(companion, 12, MUTED, false);
        companionLabel.setMaxLines(2);
        companionLabel.setEllipsize(TextUtils.TruncateAt.END);
        companionLabel.setTooltipText(companion);
        add(overview, companionLabel);
        space(overview, 10);
        divider(overview, 0);
        space(overview, 10);
        String chartLabel = range == 0 ? "每小时用时 · 点击筛选"
                : dayCount() > 30 ? "按日期段 · 点击筛选" : "每日用时 · 点击筛选";
        add(overview, text(chartLabel, 12, MUTED, true));
        overview.addView(chart(bounds, null), lp(-1, 136));
        addChartSelection(overview);
        if (range == 1 || range == 2) {
            space(overview, 4);
            add(overview, text("平均每天 " + duration(total / dayCount()), 12, MUTED, false));
        }
        add(body, overview);
        space(body, 12);
        LinearLayout titleRow = row();
        rankTitle = text("应用排行", 18, INK, true);
        titleRow.addView(rankTitle, new LinearLayout.LayoutParams(0, -2, 1));
        TextView allApps = button("全部应用 ›", WHITE, () -> openLibrary("apps"));
        allApps.setTextSize(12);
        allApps.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        titleRow.addView(allApps, lp(-2, -2));
        add(body, titleRow);
        rankScope=text("",12,MUTED,false);rankScope.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);add(body,rankScope);space(body,12);
        rankInput=new EditText(this);rankInput.setSingleLine(true);rankInput.setMinHeight(dp(48));rankInput.setTextSize(14);rankInput.setTextColor(INK);rankInput.setHintTextColor(MUTED);rankInput.setContentDescription("搜索当前时段应用");rankInput.setPadding(dp(16),dp(12),dp(16),dp(12));rankInput.setBackground(border(WHITE,15));rankInput.setText(search);add(body,rankInput);space(body,12);
        rankList=listPanel();add(body,rankList);refreshRanking();
        rankInput.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){search=s.toString();populateRanks(rankRows,rankTotal);}public void afterTextChanged(Editable e){}});
    }
    private void ranges(){
        LinearLayout r=row();r.setPadding(0,0,0,0);String[] labels={"今天","近7天","近30天","自选"};
        for(int i=0;i<4;i++){final int n=i;TextView t=text(labels[i],13,i==range?INK:MUTED,i==range);t.setGravity(Gravity.CENTER);t.setMinHeight(dp(48));t.setSelected(i==range);t.setBackground(UiControls.background(this,i==range?GREEN:Color.TRANSPARENT,6));t.setFocusable(true);t.setOnClickListener(v->{if(n==3)pickRange();else{range=n;clearChartSelection();render();}});r.addView(t,new LinearLayout.LayoutParams(0,-2,1));}add(body,r);space(body,10);
        if(range==3){add(body,text(customStart+" — "+customEnd+"  ·  包含起止日期",11,MUTED,false));space(body,10);}
    }
    private void pickRange(){
        if(getFragmentManager().findFragmentByTag("date_range")==null)
            DateRangeDialog.create(customStart,customEnd).show(getFragmentManager(),"date_range");
    }
    void applyDateRange(LocalDate start,LocalDate end){customStart=start;customEnd=end;range=3;clearChartSelection();render();}
    private long at(LocalDate date){return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();}
    private LocalDate firstDay(){return range==0?LocalDate.now():range==1?LocalDate.now().minusDays(6):range==2?LocalDate.now().minusDays(29):customStart;}
    private LocalDate lastDay(){return range==3?customEnd:LocalDate.now();}
    private long[] bounds(){return new long[]{at(firstDay()),Math.min(System.currentTimeMillis(),at(lastDay().plusDays(1)))};}
    private long dayCount(){return ChronoUnit.DAYS.between(firstDay(),lastDay())+1;}
    private String rangeTitle(){return range==0?"今天":range==1?"最近 7 天":range==2?"最近 30 天":"这段时光";}
    static String duration(long millis){long seconds=Math.max(0,millis/1000),minutes=seconds/60;if(seconds==0)return "0 分钟";if(minutes==0)return "不足 1 分钟";long hours=minutes/60;return hours==0?minutes+" 分钟":hours+"小时"+(minutes%60>0?" "+minutes%60+"分":"");}
    private List<PocketDb.AppUsage> ranking(long start,long end,String pkg){
        return db.ranking(start,end,pkg);
    }
    private void clearChartSelection(){selectedFirst=selectedLast=null;selectedHour=-1;}
    private String hourTitle(int hour){return String.format(Locale.ROOT,"%02d:00 — %02d:00",hour,hour+1);}
    private String selectedTitle(){
        if(selectedHour>=0) return hourTitle(selectedHour);
        DateTimeFormatter f=DateTimeFormatter.ofPattern(selectedFirst.getYear()==LocalDate.now().getYear()&&selectedLast.getYear()==LocalDate.now().getYear()?"M月d日":"yyyy年M月d日");
        return selectedFirst.equals(selectedLast)?selectedFirst.format(f):selectedFirst.format(f)+" — "+selectedLast.format(f);
    }
    private String selectedCaption(String noun){return selectedTitle()+(selectedHour>=0?" 的":"的")+noun;}
    private long[] selectedBounds(){
        if(selectedFirst==null) return bounds();
        long now=System.currentTimeMillis();
        if(selectedHour<0) return new long[]{at(selectedFirst),Math.min(now,at(selectedLast.plusDays(1)))};
        UsageBuckets.Hour hour=UsageBuckets.hour(selectedFirst,selectedHour,ZoneId.systemDefault(),now);
        return new long[]{hour.start(),hour.end()};
    }
    private void refreshRanking(){
        long[] b=selectedBounds();rankRows=ranking(b[0],b[1],null);rankTotal=PocketDb.sumAppUsage(rankRows);
        rankTitle.setText(selectedFirst == null ? "应用排行" : selectedCaption("应用"));
        rankScope.setText("合计 " + duration(rankTotal) + " · " + rankRows.size() + " 个应用");
        rankScope.setVisibility(selectedFirst == null ? View.GONE : View.VISIBLE);
        rankInput.setHint(selectedFirst==null?"搜索本时段的应用":selectedHour>=0?"搜索这个小时的应用":"搜索所选日期的应用");populateRanks(rankRows,rankTotal);
    }
    private String detailScopeTitle(){return selectedFirst==null?rangeTitle()+"的陪伴":selectedCaption("陪伴");}
    private void updateChartSelection(){
        chartSelection.setVisibility(selectedFirst==null?View.GONE:View.VISIBLE);if(selectedFirst!=null)chartPeriod.setText(selectedTitle() + " · " + duration(activeChart.selectedDuration()));
        if (detailPkg == null) {
            refreshRanking();
            UiMotion.reveal(rankList, 0, 4);
        } else {
            long[] bounds = selectedBounds();
            detailScope.setText(detailScopeTitle());
            detailValue.setText(duration(db.total(bounds[0], bounds[1], detailPkg)));
            UiMotion.reveal(detailValue, 0, 3);
        }
    }
    private void addChartSelection(LinearLayout parent){
        chartSelection=compact()?column():row();chartPeriod=text(selectedFirst==null?"":selectedTitle()+" · "+duration(activeChart.selectedDuration()),12,INK,true);chartPeriod.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        chartSelection.addView(chartPeriod,compact()?lp(-1,-2):new LinearLayout.LayoutParams(0,-2,1));
        TextView clear=button(range==0?"查看全天":"查看整个区间",WHITE,()->{clearChartSelection();activeChart.clearSelection();updateChartSelection();});clear.setTextSize(12);clear.setTextColor(UiTokens.FOREST);clear.setPadding(dp(8),dp(12),0,dp(12));chartSelection.addView(clear,compact()?lp(-1,-2):lp(-2,-2));
        chartSelection.setVisibility(selectedFirst==null?View.GONE:View.VISIBLE);add(parent,chartSelection);
    }
    private View chart(long[] rangeBounds,String pkg){
        boolean hourly=range==0;ZoneId zone=ZoneId.systemDefault();
        LocalDate chartDay=hourly?Instant.ofEpochMilli(rangeBounds[0]).atZone(zone).toLocalDate():null;
        List<UsageBuckets.Days> days=hourly?List.of():UsageBuckets.days(firstDay(),lastDay());
        int n=hourly?24:days.size(),selectedIndex=hourly?selectedHour:-1;long[] values=new long[n];String[] labels=new String[n],descriptions=new String[n];
        for(int i=0;i<n;i++){
            long a,b;if(hourly){UsageBuckets.Hour hour=UsageBuckets.hour(chartDay,i,zone,rangeBounds[1]);a=hour.start();b=hour.end();labels[i]=i%6==0?String.format(Locale.ROOT,"%02d",i):i==23?"23":null;descriptions[i]=hourTitle(i);}
            else{UsageBuckets.Days day=days.get(i);a=at(day.first());b=Math.min(rangeBounds[1],at(day.last().plusDays(1)));labels[i]=(n<=7||i==0||i==n-1||i%7==0)?day.first().format(DateTimeFormatter.ofPattern("M/d")):null;
                descriptions[i]=day.first()+(day.first().equals(day.last())?"":" 至 "+day.last());if(day.first().equals(selectedFirst)&&day.last().equals(selectedLast))selectedIndex=i;}
            values[i]=a<b?db.total(a,b,pkg):0;
        }
        activeChart = new ArtViews.Chart(this, values, labels, descriptions, selectedIndex,
                index -> {
                    if (hourly) {
                        selectedFirst = selectedLast = chartDay;
                        selectedHour = index;
                    } else {
                        UsageBuckets.Days day = days.get(index);
                        selectedFirst = day.first();
                        selectedLast = day.last();
                        selectedHour = -1;
                    }
                    updateChartSelection();
                    if (pkg != null) return;
                    ArtViews.Chart selectedChart = activeChart;
                    ScrollView currentScroll = scroll;
                    currentScroll.post(() -> {
                        if (scroll != currentScroll || selectedChart.getParent() == null) return;
                        View card = (View) selectedChart.getParent();
                        int offset = Math.max(0, card.getTop() + selectedChart.getTop() - dp(36));
                        if (UiMotion.enabled()) currentScroll.smoothScrollTo(0, offset);
                        else currentScroll.scrollTo(0, offset);
                    });
                });
        return activeChart;
    }
    private void populateRanks(List<PocketDb.AppUsage> ranks, long total) {
        rankList.removeAllViews();
        String query = search.toLowerCase(Locale.ROOT);
        for (PocketDb.AppUsage app : ranks) {
            if (!app.label().toLowerCase(Locale.ROOT).contains(query)
                    && !app.pkg().toLowerCase(Locale.ROOT).contains(query)) continue;
            if (rankList.getChildCount() > 0) divider(rankList, 56);
            LinearLayout item = row();
            item.setPadding(dp(12), dp(12), dp(14), dp(12));
            item.setMinimumHeight(dp(60));
            item.addView(icon(app.pkg(), app.label()), lp(34, 34));

            LinearLayout content = column();
            content.setPadding(dp(10), 0, 0, 0);
            LinearLayout title = row();
            TextView name = text(app.label(), 15, INK, true);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            title.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            String elapsed = duration(app.duration());
            TextView time = text(elapsed, 13, INK, true);
            time.setPadding(dp(8), 0, 0, 0);
            title.addView(time, lp(-2, -2));
            add(content, title);
            if (!app.installed()) {
                space(content, 4);
                add(content, text("未安装", 11, MUTED, false));
            }
            space(content, 7);
            ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            progress.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            progress.setMax(1000);
            progress.setProgress(total == 0 ? 0 : (int) (app.duration() * 1000 / total));
            progress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xff6d9d86));
            progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffeaf1eb));
            content.addView(progress, lp(-1, 4));
            item.addView(content, new LinearLayout.LayoutParams(0, -2, 1));
            ripple(item, WHITE, 0);
            String share = total > 0 ? "，占 " + Math.round(app.duration() * 100.0 / total) + "%" : "";
            item.setContentDescription(app.label() + "，" + elapsed + share
                    + (app.installed() ? "" : "，未安装") + "，查看详情");
            item.setOnClickListener(v -> {
                rankReturnScrollY = currentScrollY();
                detailPkg = app.pkg();
                detailLabel = app.label();
                renderNavigation();
            });
            add(rankList, item);
        }
        if (rankList.getChildCount() == 0) {
            String message = query.isEmpty()
                    ? (selectedFirst == null ? "这个时段暂无使用记录" : selectedHour>=0?"这个小时暂无使用记录":"所选日期暂无使用记录")
                    : "没有找到匹配的应用";
            TextView empty = text(message, 14, MUTED, false);
            empty.setPadding(dp(16), dp(22), dp(16), dp(22));
            add(rankList, empty);
        }
    }
    View icon(String pkg,String label){

        Bitmap b=BitmapFactory.decodeFile(Collector.iconFile(this,pkg).getAbsolutePath());if(b!=null){ImageView image=new ImageView(this);image.setImageBitmap(b);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setContentDescription(label);return image;}

        int[] colors={0xffe1eee3,0xffffe6db,0xffe8e4f7,0xfff7edce};TextView t=text(label.isEmpty()?"·":label.substring(0,1),21,INK,true);t.setGravity(Gravity.CENTER);t.setBackground(bg(colors[Math.floorMod(pkg.hashCode(),colors.length)],13));return t;

    }

    private String coverage(long[] b){
        String sync=db.number("last_sync")==0?"尚未采集":"最近采集 "+dateTime(db.number("last_sync"));
        if(!Collector.allowed(this))return sync+"\n权限尚未开启；已有历史记录会继续保留。";
        boolean imported=b[0]<db.number("tracking_since"),gap=db.hasGap(b[0],b[1]);
        return sync+"\n"+(gap?"此区间有采集缺口，当前时长可能不完整。":imported?"包含启用前的系统历史，可能不完整；空白不代表没有使用。":"按已获取的前台事件统计，系统或后台限制可能导致遗漏。")+"\n分屏时多个应用可能同时计时；后台播放不计入。";
    }
    private String dateTime(long time){return Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日 HH:mm"));}
    private void returnFromAppDetails() {
        int scrollY = rankReturnScrollY;
        detailPkg = null;
        renderNavigation();
        restoreScrollAfterRender(scrollY);
    }

    private void detail(){
        LinearLayout intro=row();intro.addView(iconAction(ArtViews.ActionIcon.PREVIOUS,"回到时光",this::returnFromAppDetails),lp(48,48));
        intro.addView(icon(detailPkg,detailLabel),lp(32,32));TextView title=pageTitle(detailLabel);title.setPadding(dp(10),0,dp(4),0);intro.addView(title,new LinearLayout.LayoutParams(0,-2,1));intro.addView(helpButton(),lp(48,48));add(body,intro);space(body,12);
        ranges();long[] b=selectedBounds();long selected=db.total(b[0],b[1],detailPkg);long lifetime=db.total(0,System.currentTimeMillis(),detailPkg);
        add(body,coverageHint(b));
        LinearLayout hero=card(PEACH);detailScope=text(detailScopeTitle(),13,0xff9b674f,false);add(hero,detailScope);space(hero,12);detailValue=durationValue(selected,32,INK);add(hero,detailValue);space(hero,18);add(hero,text("口袋里累计收藏了  "+duration(lifetime),14,INK,true));add(body,hero);space(body,20);
        LinearLayout cc=card(WHITE);heading(cc,"相伴的节奏",dayCount()>30?"每柱为多日合计":null);cc.addView(chart(bounds(),detailPkg),lp(-1,190));addChartSelection(cc);add(body,cc);space(body,20);add(body,button("查看这个应用的安装与卸载",WHITE,()->{footPkg=detailPkg;footLabel=detailLabel;footFilter=null;footStart=footEnd=null;footLimit=50;detailPkg=null;tab=1;render();}));
    }
    private void footprints(){
        header("应用足迹");
        LinearLayout filterPanel=card(WHITE);filterPanel.setPadding(dp(10),dp(4),dp(10),dp(6));
        LinearLayout filters=row();String[] titles={"全部","安装","卸载"};String[] kinds={null,"install","remove"};
        for(int i=0;i<titles.length;i++){final String kind=kinds[i];boolean selected=Objects.equals(kind,footFilter);TextView b=button(titles[i],selected?GREEN:WHITE,()->{footFilter=kind;footLimit=50;render();});b.setSelected(selected);b.setTextColor(selected?INK:MUTED);b.setBackground(UiControls.background(this,selected?GREEN:Color.TRANSPARENT,6));b.setPadding(dp(4),dp(12),dp(4),dp(12));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);if(i>0)p.leftMargin=dp(6);filters.addView(b,p);}add(filterPanel,filters);space(filterPanel,4);
        View appFilter=filterChip(footPkg==null?"筛选应用 ▾":footLabel+" ▾",footPkg!=null,()->openLibrary("footprints"));appFilter.setContentDescription(footPkg==null?"筛选应用":footLabel+"，更换应用");
        int currentYear = LocalDate.now().getYear();
        String dateFilterLabel = "全部日期";
        if (footStart != null) {
            boolean showYear = footStart.getYear() != currentYear || footEnd.getYear() != currentYear;
            DateTimeFormatter shortDate = DateTimeFormatter.ofPattern(showYear ? "yyyy/M/d" : "M/d");
            dateFilterLabel = footStart.format(shortDate);
            if (!footStart.equals(footEnd)) dateFilterLabel += " — " + footEnd.format(shortDate);
        }
        View dateFilter=filterChip(dateFilterLabel+" ▾",footStart!=null,()->pickHistoryRange("footprints",footStart,footEnd));
        dateFilter.setContentDescription(footStart==null?"筛选足迹日期":footStart+" 至 "+footEnd+"，更改日期");filterPair(filterPanel,appFilter,dateFilter);add(body,filterPanel);
        boolean filtered = footFilter != null || footPkg != null || footStart != null;
        if (filtered) {
            TextView clear = button("清除筛选",WHITE,() -> {
                footFilter = null;
                footPkg = footLabel = null;
                footStart = footEnd = null;
                footLimit = 50;
                render();
            });
            clear.setTextSize(12);
            clear.setTextColor(MUTED);
            clear.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
            add(body,clear);
        } else space(body,18);
        long begin=footStart==null?0:at(footStart),end=footEnd==null?Long.MAX_VALUE:at(footEnd.plusDays(1));
        List<PocketDb.Footprint> rows = db.findFootprints(footFilter, footPkg, begin, end, footLimit + 1);
        LocalDate previous = null;
        int count = 0;
        LinearLayout dateGroup = null;
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA);
        DateTimeFormatter yearDateFormat = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA);
        DateTimeFormatter clockFormat = DateTimeFormatter.ofPattern("HH:mm");
        for (PocketDb.Footprint f : rows) {
            if (count >= footLimit) break;
            count++;
            ZonedDateTime occurred = Instant.ofEpochMilli(f.at()).atZone(ZoneId.systemDefault());
            LocalDate date = occurred.toLocalDate();
            if (!date.equals(previous)) {
                if (count > 1) space(body, 18);
                add(body, text(date.format(date.getYear() == currentYear ? dateFormat : yearDateFormat), 13, MUTED, true));
                space(body, 8);
                dateGroup = listPanel();
                add(body, dateGroup);
                previous = date;
            } else {
                divider(dateGroup, 54);
            }

            LinearLayout item = row();
            item.setPadding(dp(12), dp(10), dp(12), dp(10));
            item.setMinimumHeight(dp(62));
            item.addView(icon(f.pkg(), f.label()), lp(32, 32));
            LinearLayout center = column();
            center.setPadding(dp(10), 0, dp(8), 0);
            TextView label = text(f.label(), 15, INK, true);
            label.setMaxLines(2);
            label.setEllipsize(TextUtils.TruncateAt.END);
            add(center, label);
            space(center, 4);
            String time = occurred.format(clockFormat);
            boolean notified = f.source().equals("broadcast");
            boolean removed = f.kind().equals("remove") || f.kind().equals("observed_missing");
            String eventLabel = removed ? "卸载" : "安装";
            String source = notified ? "系统通知" : "清单补记";
            String explanation = notified ? "系统通知接收时间"
                    : "这是核对应用清单时发现的变化，显示的是发现时间。";
            add(center, text(time + " · " + source, 11, MUTED, false));
            item.addView(center, new LinearLayout.LayoutParams(0, -2, 1));
            TextView event = text(eventLabel, 12, removed ? PRIMARY : UiTokens.FOREST, true);
            event.setGravity(Gravity.END);
            event.setMaxWidth(dp(90));
            item.addView(event, lp(-2, -2));
            ripple(item, WHITE, 0);
            item.setOnClickListener(v -> new PocketDialogs.Builder(this)
                    .setTitle(f.label())
                    .setMessage(eventLabel + " · " + dateTime(f.at()) + "\n" + explanation + "\n\n" + f.pkg())
                    .setPositiveButton("查看此应用全部足迹", (d,w) -> {
                        footPkg = f.pkg();
                        footLabel = f.label();
                        footStart = footEnd = null;
                        footFilter = null;
                        footLimit = 50;
                        render();
                    })
                    .setNegativeButton("关闭", null).show());
            item.setContentDescription(f.label() + "，" + eventLabel + "，" + source + "，" + time);
            add(dateGroup, item);
        }
        if(rows.size()>footLimit)add(body,button("加载更早记录",WHITE,()->{int y=scroll.getScrollY();footLimit+=50;render();scroll.post(()->scroll.scrollTo(0,y));}));
        if (count == 0) {
            LinearLayout empty = card(WHITE);
            add(empty, text("暂无足迹", 17, INK, true));
            if (filtered) {
                space(empty, 6);
                add(empty, text("可以清除筛选，查看全部记录。", 13, MUTED, false));
            }
            add(body, empty);
        }
    }
    private record SleepGuess(String day,long start,long end) {}
    private record DayRow(long start,long end,PocketDb.ActivityEntry activity,DayTimeline.Group app,SleepGuess sleep) {
        DayRow(long start,long end,PocketDb.ActivityEntry activity,DayTimeline.Group app){this(start,end,activity,app,null);}
    }
    private SleepGuess guessSleep(LocalDate wakingDay){
        long evening=wakingDay.minusDays(1).atTime(18,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long midnight=at(wakingDay),latest=wakingDay.atTime(6,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long noon=wakingDay.atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long now=System.currentTimeMillis();if(midnight>now||db.sleepOverridden(wakingDay.toString()))return null;
        List<DayTimeline.Item> phone=db.dayUsage(evening,Math.min(now,noon));
        Set<String> hidden=HomeApps.packages(this);
        DayTimeline.Span sleep=DayTimeline.sleepApps(phone,hidden,evening,midnight,latest,Math.min(now,noon));
        if(sleep==null)return null;
        // Manual entries take precedence for the whole night, including edits on the other date.
        if(db.hasGap(sleep.start(),sleep.end())||!db.activities(sleep.start(),sleep.end()).isEmpty())return null;
        return new SleepGuess(wakingDay.toString(),sleep.start(),sleep.end());
    }
    private void editSleep(SleepGuess sleep){
        if(getFragmentManager().findFragmentByTag("activity_editor")==null)ActivityEditor.sleep(sleep.day(),sleep.start(),sleep.end()).show(getFragmentManager(),"activity_editor");
    }
    private void changeJournalDay(LocalDate date) {
        int direction = Integer.signum(date.compareTo(journalDay));
        journalDay = date;
        dayLimit = 60;
        render();
        if (direction != 0) UiMotion.reveal(scroll, direction * 10, 0);
    }
    private void pickJournalDay(){pickSingleDate("journal",journalDay);}
    void pickSingleDate(String target,LocalDate date){
        if(getFragmentManager().findFragmentByTag("date_range")==null)DateRangeDialog.single(date,target).show(getFragmentManager(),"date_range");
    }
    void applySingleDate(String target,LocalDate date){
        if("journal".equals(target))changeJournalDay(date);
        else if("activity_date".equals(target)){ActivityEditor editor=(ActivityEditor)getFragmentManager().findFragmentByTag("activity_editor");if(editor!=null)editor.applyCalendarDate(date);}
    }
    private String dayTime(long value,long endOfDay){return value==endOfDay?"24:00":Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"));}
    private String dayTimes(long start,long end,long endOfDay){
        if(end-start<60000)return Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm:ss"))+" — "+Instant.ofEpochMilli(end).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        return dayTime(start,endOfDay)+" — "+dayTime(end,endOfDay);
    }
    void editSavedActivity(PocketDb.ActivityEntry entry){editActivity(entry,entry.start(),entry.end());}
    private void showActivity(PocketDb.ActivityEntry entry){if(getFragmentManager().findFragmentByTag("activity_detail")==null)ActivityDetail.create(entry.id()).show(getFragmentManager(),"activity_detail");}
    private void editActivity(PocketDb.ActivityEntry entry,long start,long end){
        if(getFragmentManager().findFragmentByTag("activity_editor")==null)ActivityEditor.create(entry,start,end).show(getFragmentManager(),"activity_editor");
    }
    void activitySaved(LocalDate date){journalDay=date;dayLimit=60;journalChanged();}
    void journalChanged(){ActivityDetail detail=(ActivityDetail)getFragmentManager().findFragmentByTag("activity_detail");if(detail!=null)detail.refresh();LibraryDialog library=(LibraryDialog)getFragmentManager().findFragmentByTag("library");if(library!=null)library.refresh();int y=scroll==null?0:scroll.getScrollY();render();scroll.post(()->scroll.scrollTo(0,y));}
    private void dayPage(FrameLayout area){
        if(journalDay.isAfter(LocalDate.now()))journalDay=LocalDate.now();
        long begin=at(journalDay),dayEnd=at(journalDay.plusDays(1)),end=Math.min(System.currentTimeMillis(),dayEnd);
        header("我的一天");
        LinearLayout dates=row();dates.setBackground(bg(WHITE,18));
        dates.addView(iconAction(ArtViews.ActionIcon.PREVIOUS,"前一天",()->changeJournalDay(journalDay.minusDays(1))),lp(48,52));
        TextView date=button(journalDay.format(DateTimeFormatter.ofPattern(journalDay.getYear()==LocalDate.now().getYear()?"M月d日 EEE":"yyyy/M/d",Locale.CHINA))+"  ▾",WHITE,this::pickJournalDay);
        date.setContentDescription("选择日记日期");dates.addView(date,new LinearLayout.LayoutParams(0,-2,1));
        View next=iconAction(ArtViews.ActionIcon.NEXT,"后一天",()->changeJournalDay(journalDay.plusDays(1)));next.setEnabled(journalDay.isBefore(LocalDate.now()));next.setAlpha(next.isEnabled()?1f:.3f);dates.addView(next,lp(48,52));add(body,dates);space(body,8);
        if(!journalDay.equals(LocalDate.now())){TextView today=button("回到今天",WHITE,()->changeJournalDay(LocalDate.now()));today.setTextSize(12);today.setTextColor(MUTED);today.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);add(body,today);}else space(body,4);
        TextView addEntry = button("＋ 记一件事", CORAL, () -> {
            long finish = (journalDay.equals(LocalDate.now()) ? System.currentTimeMillis()
                    : at(journalDay) + 13 * 3600000L) / 60000 * 60000;
            editActivity(null, Math.max(begin, finish - 30 * 60000L), finish);
        });
        addEntry.setPadding(dp(16), dp(10), dp(16), dp(10));
        addEntry.setElevation(dp(4));
        FrameLayout.LayoutParams entryPosition = new FrameLayout.LayoutParams(-2, -2,
                Gravity.BOTTOM | Gravity.END);
        entryPosition.setMarginEnd(dp(20) + Math.max(0,
                (getResources().getDisplayMetrics().widthPixels - dp(600)) / 2));
        entryPosition.bottomMargin = dp(14);
        area.addView(addEntry, entryPosition);
        body.setPadding(body.getPaddingLeft(), body.getPaddingTop(),
                body.getPaddingRight(), dp(80));

        List<DayTimeline.Item> apps=db.dayUsage(begin,end);
        List<PocketDb.ActivityEntry> manual=db.activities(begin,end);
        List<DayTimeline.Span> covered=new ArrayList<>();List<DayRow> rows=new ArrayList<>();
        for(PocketDb.ActivityEntry item:manual){long a=Math.max(begin,item.start()),b=Math.min(end,item.end());covered.add(new DayTimeline.Span(a,b));rows.add(new DayRow(a,b,item,null));}
        List<DayTimeline.Span> sleepSpans=new ArrayList<>();
        for(var activity:manual)if(activity.category().equals("睡觉"))sleepSpans.add(new DayTimeline.Span(activity.start(),activity.end()));
        for(LocalDate wakingDay:List.of(journalDay,journalDay.plusDays(1))){
            SleepGuess sleep=guessSleep(wakingDay);if(sleep==null)continue;
            long a=Math.max(begin,sleep.start()),b=Math.min(end,sleep.end());if(a>=b)continue;
            DayTimeline.Span span=new DayTimeline.Span(a,b);covered.add(span);sleepSpans.add(span);rows.add(new DayRow(a,b,null,null,sleep));
        }
        Set<String> hidden=HomeApps.packages(this);
        List<DayTimeline.Group> groups=DayTimeline.groupApps(apps,hidden,covered,begin,end);
        for(DayTimeline.Group app:groups){
            covered.addAll(app.segments());
            rows.add(new DayRow(app.start(),app.end(),null,app));
        }
        List<DayTimeline.Span> blanks=DayTimeline.blanks(covered,begin,end);
        List<DayTimeline.Span> displayed=new ArrayList<>(covered);for(DayTimeline.Group app:groups)displayed.add(new DayTimeline.Span(app.start(),app.end()));
        for(DayTimeline.Span blank:DayTimeline.blanks(displayed,begin,end))if(blank.end()-blank.start()>HIDDEN_GAP_MAX_MS)rows.add(new DayRow(blank.start(),blank.end(),null,null));
        LinearLayout summary=card(GREEN);summary.setPadding(dp(18),dp(16),dp(18),dp(14));
        boolean stacked = stackMetrics();
        LinearLayout numbers=stacked?column():row();LinearLayout phoneMetric=column(),notesMetric=column();
        add(phoneMetric,text("应用使用时长",12,MUTED,false));space(phoneMetric,6);
        long appTotal = db.total(begin,end,null);
        TextView phoneValue=text(duration(appTotal),24,INK,true);phoneValue.setMaxLines(1);phoneValue.setAutoSizeTextTypeUniformWithConfiguration(16,24,1,android.util.TypedValue.COMPLEX_UNIT_SP);add(phoneMetric,phoneValue);
        notesMetric.addView(text("生活手记",12,MUTED,false),lp(-2,-2));space(notesMetric,6);notesMetric.addView(text(manual.size()+" 条",24,INK,true),lp(-2,-2));
        numbers.addView(phoneMetric,stacked?lp(-1,-2):new LinearLayout.LayoutParams(0,-2,1));
        if(stacked)space(numbers,12);else notesMetric.setPadding(dp(16),0,0,0);
        numbers.addView(notesMetric,lp(stacked?-1:-2,-2));add(summary,numbers);space(summary,16);
        if(!sleepSpans.isEmpty()){add(summary,text("睡眠 "+duration(DayTimeline.duration(sleepSpans,begin,end))+" · 含手动与推测",12,UiTokens.PURPLE,true));space(summary,8);}
        add(summary,text("时间线空白 "+duration(DayTimeline.duration(blanks,begin,end)),12,MUTED,false));add(body,summary);space(body,4);
        String status=!Collector.allowed(this)?"应用记录未授权 · 可直接写手记":db.number("last_sync")==0?"应用记录尚未采集":db.hasGap(begin,end)||(db.number("import_begin")>0&&begin<db.number("import_begin"))?"应用记录可能不完整":"未记录时段可点击补充";
        if(!status.equals("未记录时段可点击补充")){TextView info=text(status+"  ⓘ",11,MUTED,false);info.setMinHeight(dp(48));info.setGravity(Gravity.CENTER_VERTICAL);info.setOnClickListener(v->showPageHelp());add(body,info);}else space(body,12);
        add(body, text("这一天的记录", 18, INK, true));
        space(body, 10);
        rows.sort(Comparator.comparingLong(DayRow::start).reversed()
                .thenComparingInt(r -> r.activity() != null ? 0 : r.app() != null ? 1 : 2));
        int shown = 0;
        for (DayRow record : rows) {
            if (shown++ >= dayLimit) break;
            add(body, timelineRow(record, begin, dayEnd));
        }
        if(rows.isEmpty()){add(body,text("这一天暂无可展示的片段，可以记下一件事。",14,MUTED,false));space(body,14);}
        if(rows.size()>dayLimit)add(body,button("查看更早（还有 "+(rows.size()-dayLimit)+" 段）",WHITE,()->{int y=scroll.getScrollY();dayLimit+=60;render();scroll.post(()->scroll.scrollTo(0,y));}));
    }
    private View timelineRow(DayRow record, long dayStart, long dayEnd) {
        PocketDb.ActivityEntry note = record.activity();
        DayTimeline.Group app = record.app();
        boolean inferred = record.sleep() != null;
        boolean blank = !inferred && note == null && app == null;
        String times = dayTimes(record.start(), record.end(), dayEnd);
        int accent = inferred ? UiTokens.PURPLE : blank ? MUTED : note != null ? PRIMARY : UiTokens.FOREST;
        LinearLayout tile = row();
        tile.setMinimumHeight(dp(blank ? 52 : 68));
        tile.setPadding(dp(10), dp(8), dp(10), dp(8));
        int fill = inferred ? UiTokens.LAVENDER : blank ? Color.TRANSPARENT : WHITE;
        ripple(tile, fill, 12);
        if (app != null) tile.addView(icon(app.pkg(), app.label()), lp(30, 30));
        LinearLayout details = column();
        if (app != null) details.setPadding(dp(10), 0, 0, 0);
        String title = inferred ? "睡眠" : blank ? "＋ 补记" : note != null ? note.title() : app.label();
        TextView name = text(title, blank ? 13 : 15, blank ? UiTokens.FOREST : INK, true);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        add(details, name);
        space(details, 4);
        String meta = times;
        if (app != null) meta += " · 用时 " + duration(app.used());
        else if (inferred) meta += " · 推测";
        else if (note != null) {
            meta += " · " + note.category();
            if (note.start() < dayStart || note.end() > dayEnd) meta += " · 跨日";
        }
        add(details, text(meta, 11, MUTED, false));
        List<Long> photos = note == null ? List.of() : db.photoIds(note.id());
        if (note != null && !note.note().isEmpty()) {
            space(details, 4);
            TextView preview = text(note.note(), 12, MUTED, false);
            preview.setMaxLines(1);
            preview.setEllipsize(TextUtils.TruncateAt.END);
            add(details, preview);
        }
        tile.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        if (!photos.isEmpty()) {
            ImageView thumbnail = new ImageView(this);
            thumbnail.setImageBitmap(EntryPhotos.thumbnail(this, "saved:" + photos.get(0), dp(48)));
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbnail.setBackground(bg(GREEN, 9));
            thumbnail.setClipToOutline(true);
            thumbnail.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams photoPosition = lp(44, 44);
            photoPosition.setMarginStart(dp(8));
            tile.addView(thumbnail, photoPosition);
        } else if (!blank) {
            TextView arrow = text("›", 18, MUTED, false);
            arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            arrow.setPadding(dp(8), 0, 0, 0);
            tile.addView(arrow, lp(-2, -2));
        }
        String description = inferred ? "调整推测的睡眠" : blank ? "补记未记录时段"
                : note != null ? "查看手记 " + title : "查看应用片段 " + title;
        tile.setContentDescription(description + "，" + meta
                + (photos.isEmpty() ? "" : "，" + photos.size() + " 张图片"));
        tile.setOnClickListener(v -> {
            if (inferred) editSleep(record.sleep());
            else if (note != null) showActivity(note);
            else if (blank) editActivity(null, record.start(), record.end());
            else showAppGroup(app, dayEnd);
        });

        LinearLayout track = row();
        track.setGravity(Gravity.TOP);
        track.addView(new ArtViews.TimelineRail(this, accent), lp(16, -1));
        LinearLayout holder = column();
        holder.setPadding(dp(5), 0, 0, dp(5));
        add(holder, tile);
        track.addView(holder, new LinearLayout.LayoutParams(0, -2, 1));
        return track;
    }
    private void showAppGroup(DayTimeline.Group app,long dayEnd){
        StringBuilder message=new StringBuilder("使用 "+duration(app.used())+"\n");
        if(app.segments().size()>1)message.append("合并 ").append(app.segments().size()).append(" 段，中间间隔 ").append(duration(app.end()-app.start()-app.used())).append("不计入使用时长。\n");
        message.append("\n");for(DayTimeline.Span span:app.segments())message.append(dayTimes(span.start(),span.end(),dayEnd)).append("  ·  ").append(duration(span.end()-span.start())).append("\n");
        message.append("\n应用包名：").append(app.pkg());
        AlertDialog.Builder dialog=new PocketDialogs.Builder(this).setTitle(app.label()).setMessage(message.toString()).setPositiveButton("知道了",null);
        List<DayTimeline.Span> breaks=app.breaks().stream().filter(span->span.end()-span.start()>HIDDEN_GAP_MAX_MS).collect(java.util.stream.Collectors.toList());if(!breaks.isEmpty())dialog.setNeutralButton("补记间隔",(d,w)->{
            String[] choices=new String[breaks.size()];for(int i=0;i<choices.length;i++)choices[i]=dayTimes(breaks.get(i).start(),breaks.get(i).end(),dayEnd);
            new PocketDialogs.Builder(this).setTitle("补记哪段时间？").setItems(choices,(pick,index)->editActivity(null,breaks.get(index).start(),breaks.get(index).end())).setNegativeButton("取消",null).show();
        });dialog.show();
    }
    private void openLibrary(String mode){
        if(getFragmentManager().findFragmentByTag("library")==null)LibraryDialog.create(mode).show(getFragmentManager(),"library");
    }
    void chooseApp(PocketDb.AppChoice app,String mode){if(mode.equals("footprints")){footPkg=app.pkg();footLabel=app.label();footLimit=50;tab=1;detailPkg=null;}else{clearChartSelection();detailPkg=app.pkg();detailLabel=app.label();tab=0;}render();}
    void openFoundActivity(PocketDb.ActivityEntry item){journalDay=Instant.ofEpochMilli(item.start()).atZone(ZoneId.systemDefault()).toLocalDate();tab=3;detailPkg=null;render();showActivity(item);}
    void pickHistoryRange(String target,LocalDate start,LocalDate end){if(getFragmentManager().findFragmentByTag("date_range")!=null)return;DateRangeDialog dialog=DateRangeDialog.create(start==null?LocalDate.now().minusDays(6):start,end==null?LocalDate.now():end);dialog.getArguments().putString("target",target);dialog.show(getFragmentManager(),"date_range");}
    void applyHistoryRange(String target,LocalDate start,LocalDate end){if(target.equals("footprints")){footStart=start;footEnd=end;footLimit=50;render();}else if(target.equals("library")){LibraryDialog library=(LibraryDialog)getFragmentManager().findFragmentByTag("library");if(library!=null)library.setRange(start,end);}else applyDateRange(start,end);}
    private void sleepSettings(){
        String[] days={journalDay.toString(),journalDay.plusDays(1).toString()};
        new PocketDialogs.Builder(this).setTitle("恢复哪一晚的睡眠推测？").setItems(new String[]{"昨晚至当天早晨（"+days[0]+"）","当晚至次日早晨（"+days[1]+"）"},(d,w)->new PocketDialogs.Builder(this).setTitle("恢复自动推测？").setMessage("会取消该晚的“不作为睡眠”标记。已有手动手记继续保留；若与手记重叠，仍以手记为准。").setPositiveButton("恢复推测",(p,q)->{db.restoreSleep(days[w]);journalChanged();toast("已恢复该晚的推测资格");}).setNegativeButton("取消",null).show()).setNegativeButton("关闭",null).show();
    }
    private void settings(){
        header("我的口袋");
        LinearLayout banner = row();
        banner.setBackground(bg(GREEN,22));
        banner.setPadding(dp(18),dp(6),dp(12),dp(6));
        TextView copy = text("看见时间，\n也看见生活",22,INK,true);
        copy.setLineSpacing(dp(3),1f);
        banner.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        banner.addView(new ArtViews.Mascot(this,true),lp(94,80));
        add(body,banner);
        space(body,18);
        heading(body,"记录与权限",null);space(body,12);LinearLayout recordSettings=settingsGroup();
        setting(recordSettings,"使用情况访问",Collector.allowed(this)?"已开启":"未开启",this::openUsage);
        setting(recordSettings,"后台运行指引","适配 OPPO / vivo / 一加的设置建议",this::backgroundHelp);
        setting(recordSettings,"现在收集一次",busy?"正在把时光收进口袋…":db.number("last_sync")==0?"等待第一次采集":"最近采集："+dateTime(db.number("last_sync")),()->refresh(true));
        space(body,24);heading(body,"备份与数据",null);space(body,12);LinearLayout backupSettings=settingsGroup();
        setting(backupSettings,"导出本地备份","包含使用记录、手记和图片",()->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"时光口袋-"+LocalDate.now()+".json");launch(i,20);});
        setting(backupSettings,"从文件恢复","恢复会替换当前记录，操作前会再次确认",()->{launch(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),21);});
        space(body,24);setting(settingsGroup(),"关于时光口袋","v0.6.11 · 使用与数据说明",()->explain("关于时光口袋 · v0.6.11","同一包名重装后继续累计，应用分身、工作资料和不同包名版本暂不合并。\n\n后台约每 15 分钟尝试采集，系统省电可能延迟。近期事件会反复核对，只有已确认结束的前台片段计入。未安装本工具前的完整历史无法保证恢复。\n\n卸载时光口袋或清除其数据会删除本地记录，请先导出备份。\n\n每页右上角可查看说明；刷新和其他选项在“更多操作”中。"));
    }
    private LinearLayout settingsGroup() {
        LinearLayout group = listPanel();
        add(body, group);
        return group;
    }
    private void setting(LinearLayout group,String title,String subtitle,Runnable action){
        if (group.getChildCount() > 0) divider(group, 60);
        LinearLayout item=row();item.setPadding(dp(16),dp(15),dp(16),dp(15));item.setMinimumHeight(dp(72));item.setBackground(UiControls.background(this,WHITE,0));
        FrameLayout badge=new FrameLayout(this);badge.setBackground(bg(GREEN,12));int kind=title.contains("备份")||title.contains("恢复")?0:title.contains("后台")||title.contains("收集")?1:2;
        badge.addView(new ArtViews.NavIcon(this,kind,INK),new FrameLayout.LayoutParams(dp(20),dp(20),Gravity.CENTER));item.addView(badge,lp(32,36));
        LinearLayout content=column();content.setPadding(dp(12),0,dp(8),0);add(content,text(title,15,INK,true));space(content,5);add(content,text(subtitle,12,MUTED,false));item.addView(content,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=text("›",22,MUTED,false);arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);item.addView(arrow);item.setFocusable(true);item.setContentDescription(title+"，"+subtitle);item.setOnClickListener(v->action.run());add(group,item);
    }
    private void openUsage(){
        try{startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS,Uri.parse("package:"+getPackageName())));}catch(ActivityNotFoundException e){try{startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));}catch(ActivityNotFoundException e2){toast("请在系统设置中搜索“使用情况访问权限”");}}
    }
    private void backgroundHelp(){new PocketDialogs.Builder(this).setTitle("让口袋在后台也能记住").setMessage("OPPO / 一加：在应用信息的耗电管理中，允许后台运行；若有“自启动”选项，可一并开启。\n\nvivo：在应用管理或 i管家中，检查自启动与后台耗电管理，允许时光口袋后台运行。\n\n不同系统版本的名称可能不同。也可以在最近任务中锁定本应用，并偶尔打开补采。\n\n这些设置有助于记录，但无法保证系统永不暂停后台任务。").setPositiveButton("打开应用信息",(d,w)->{try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}catch(Exception e){toast("请到系统应用管理中找到时光口袋");}}).setNegativeButton("知道了",null).show();}
    private void launch(Intent intent,int code){try{startActivityForResult(intent,code);}catch(ActivityNotFoundException e){toast("未找到系统文件选择器");}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();
        if(request==20){toast("正在导出…");PocketApp.IO.execute(()->{try{Backup.exportTo(this,uri);runOnUiThread(()->toast("备份已保存"));}catch(Exception e){runOnUiThread(()->toast("备份文件超过 50 MB".equals(e.getMessage())?"备份超过 50 MB，当前版本暂无法导出":"导出失败，请检查文件位置与剩余空间"));}});}
        if(request==21){PocketApp.IO.execute(()->{try{JSONObject backup=Backup.read(this,uri);int count=backup.getJSONArray("sessions").length();runOnUiThread(()->{if(isFinishing()||isDestroyed())return;new PocketDialogs.Builder(this).setTitle("恢复这份时光？").setMessage("备份包含 "+count+" 段使用记录、"+(backup.optJSONArray("activities")==null?0:backup.optJSONArray("activities").length())+" 条手记。\n\n恢复会替换本机现有记录，建议先导出当前备份。文件验证失败时会保留原数据。").setNegativeButton("取消",null).setPositiveButton("替换并恢复",(d,w)->PocketApp.IO.execute(()->{try{Backup.restore(this,backup);runOnUiThread(()->{toast("时光已回到口袋");render();});}catch(Exception e){runOnUiThread(()->toast("恢复失败，原记录已保留"));}})).show();});}catch(Exception e){runOnUiThread(()->toast("无法读取：请选择有效的时光口袋 JSON 备份"));}});}
    }
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
}
