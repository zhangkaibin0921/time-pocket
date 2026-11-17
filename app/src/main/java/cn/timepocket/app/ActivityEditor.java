package cn.timepocket.app;

import android.app.*;
import android.content.res.ColorStateList;
import android.content.Intent;
import java.util.ArrayList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;

/** Draft form state is separate from persisted records until Save. */
public final class ActivityEditor extends DialogFragment {
    private long id;
    private String sleepDay;private String original;
    private LocalDate date;
    private LocalTime from,to;
    private EditText title,note;
    private String category;
    private boolean endsNextDay;
    private TextView startButton,endButton,endCaption,error,duration,dateLabel;
    private LinearLayout sheet,photoStrip;
    private ArrayList<String> photos=new ArrayList<>();
    private TextView addPhoto,categoryButton;
    private static final int INK=ArtViews.INK,MUTED=UiTokens.MUTED,WHITE=Color.WHITE,LINE=UiTokens.OUTLINE;

    static ActivityEditor create(PocketDb.ActivityEntry entry,long start,long end){
        ActivityEditor dialog=new ActivityEditor();Bundle args=new Bundle();
        ZonedDateTime a=Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()),b=Instant.ofEpochMilli(end).atZone(ZoneId.systemDefault());
        args.putLong("id",entry==null?0:entry.id());args.putString("date",a.toLocalDate().toString());
        args.putString("from",a.toLocalTime().withNano(0).toString());args.putString("to",b.toLocalTime().withNano(0).toString());
        args.putBoolean("next",b.toLocalDate().isAfter(a.toLocalDate()));
        args.putString("title",entry==null?"":entry.title());args.putString("note",entry==null?"":entry.note());args.putString("category",entry==null?"其他":entry.category());
        dialog.setArguments(args);return dialog;
    }
    static ActivityEditor sleep(String day,long start,long end){
        ActivityEditor dialog=create(new PocketDb.ActivityEntry(0,"睡眠","睡觉","",start,end),start,end);
        dialog.getArguments().putString("sleep_day",day);return dialog;
    }
    @Override public Dialog onCreateDialog(Bundle saved){
        Bundle state=saved==null?getArguments():saved;id=state.getLong("id");date=LocalDate.parse(state.getString("date"));
        sleepDay=state.getString("sleep_day");
        if(state.containsKey("photos"))photos=state.getStringArrayList("photos");else if(id!=0)for(long photo:PocketDb.get(getActivity()).photoIds(id))photos.add("saved:"+photo);
        from=LocalTime.parse(state.getString("from"));to=LocalTime.parse(state.getString("to"));
        category=state.getString("category","其他");if(!Arrays.asList(PocketDb.CATEGORIES).contains(category))category="其他";
        sheet=column();sheet.setBackground(round(ArtViews.PAPER,26,false));sheet.setClipToOutline(true);sheet.setFocusableInTouchMode(true);
        LinearLayout header=row();header.setPadding(dp(20),dp(16),dp(12),dp(10));
        LinearLayout heading=column();TextView name=label(sleepDay!=null?"调整睡眠":id==0?"记一件事":"编辑这段时光",20);name.setTypeface(null,Typeface.BOLD);heading.addView(name);
        dateLabel=label(date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd · EEEE",Locale.CHINA)),13);dateLabel.setTextColor(MUTED);dateLabel.setText(dateLabel.getText()+" ▾");dateLabel.setMinHeight(dp(48));dateLabel.setGravity(Gravity.CENTER_VERTICAL);dateLabel.setBackground(UiControls.background(getActivity(),Color.TRANSPARENT,0));dateLabel.setFocusable(true);dateLabel.setContentDescription("修改活动日期");dateLabel.setOnClickListener(v->((MainActivity)getActivity()).pickSingleDate("activity_date",date));
        header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        FrameLayout close=new FrameLayout(getActivity());close.setBackground(UiControls.background(getActivity(),Color.TRANSPARENT,0));close.addView(new ArtViews.ActionIcon(getActivity(),ArtViews.ActionIcon.CLOSE),new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));close.setFocusable(true);close.setOnClickListener(v->requestClose());close.setContentDescription("关闭编辑，取消修改");header.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));sheet.addView(header);
        LinearLayout form=column();form.setPadding(dp(20),0,dp(20),dp(6));
        title=input("这一刻，在做什么？","活动名称",80,true);title.setTextSize(22);title.setTypeface(null,Typeface.BOLD);title.setPadding(0,dp(12),0,dp(12));title.setBackgroundColor(Color.TRANSPARENT);title.setText(state.getString("title"));title.setMinHeight(dp(56));form.addView(title,new LinearLayout.LayoutParams(-1,-2));space(form,4);
        LinearLayout metadata=getResources().getConfiguration().fontScale>1.15f?column():row();metadata.addView(dateLabel,getResources().getConfiguration().fontScale>1.15f?new LinearLayout.LayoutParams(-1,dp(48)):new LinearLayout.LayoutParams(0,dp(48),1));
        categoryButton=action("",UiTokens.GREEN,UiTokens.FOREST,()->new PocketDialogs.Builder(getActivity()).setTitle("选择分类").setItems(PocketDb.CATEGORIES,(d,w)->{category=PocketDb.CATEGORIES[w];updateCategories();}).setNegativeButton("取消",null).show());categoryButton.setPadding(dp(12),0,dp(12),0);categoryButton.setContentDescription("选择活动分类");metadata.addView(categoryButton,new LinearLayout.LayoutParams(-2,dp(48)));form.addView(metadata);updateCategories();space(form,16);
        LinearLayout timing=row();TextView timingTitle=label("这段时间",13);timingTitle.setTypeface(null,Typeface.BOLD);timing.addView(timingTitle,new LinearLayout.LayoutParams(0,-2,1));duration=label("",12);duration.setTextColor(UiTokens.PRIMARY);timing.addView(duration);form.addView(timing);space(form,9);
        boolean stackTimes=getResources().getConfiguration().screenWidthDp<360||getResources().getConfiguration().fontScale>1.15f;
        LinearLayout times=stackTimes?column():row();times.setBackground(round(UiTokens.GREEN,18,false));times.addView(timeCard(true),stackTimes?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=label(stackTimes?"":"→",19);arrow.setGravity(Gravity.CENTER);arrow.setTextColor(MUTED);times.addView(arrow,new LinearLayout.LayoutParams(stackTimes?-1:dp(28),stackTimes?dp(4):-2));
        times.addView(timeCard(false),stackTimes?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1));form.addView(times);
        endsNextDay=state.getBoolean("next");space(form,18);
        section(form,"备注 · 选填");note=input("留下一点细节，方便以后回想","活动备注",1000,false);note.setMinLines(2);note.setMaxLines(4);note.setText(state.getString("note"));form.addView(note,new LinearLayout.LayoutParams(-1,-2));
        space(form, 10);
        LinearLayout attachments = row();
        TextView attachmentLabel = label("图片 · 最多 3 张", 12);
        attachmentLabel.setTextColor(MUTED);
        attachments.addView(attachmentLabel, new LinearLayout.LayoutParams(0, -2, 1));
        addPhoto = action("＋ 添加图片", Color.TRANSPARENT, UiTokens.FOREST, () -> {
            try {
                startActivityForResult(new Intent(getActivity(), PhotoImportActivity.class)
                        .putExtra("remaining", EntryPhotos.MAX_COUNT - photos.size()), 31);
            } catch (Exception e) {
                showError("暂时无法打开图片选择器");
            }
        });
        addPhoto.setPadding(dp(10), 0, dp(6), 0);
        attachments.addView(addPhoto, new LinearLayout.LayoutParams(-2, dp(48)));
        form.addView(attachments);
        HorizontalScrollView photoScroll = new HorizontalScrollView(getActivity());
        photoScroll.setHorizontalScrollBarEnabled(false);
        photoStrip = row();
        photoScroll.addView(photoStrip);
        form.addView(photoScroll);
        updatePhotos();
        if(sleepDay!=null||id!=0){space(form,8);form.addView(action(sleepDay!=null?"不作为睡眠":"删除这条手记",ArtViews.PAPER,UiTokens.PRIMARY,this::remove),new LinearLayout.LayoutParams(-1,dp(48)));}
        error=label("",12);error.setTextColor(0xffb04d35);error.setPadding(0,dp(8),0,0);error.setVisibility(View.GONE);error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);form.addView(error);updateTimes();
        ScrollView scroll=new ScrollView(getActivity()){
            @Override protected void onMeasure(int width,int height){int cap=dp(560);if(MeasureSpec.getMode(height)!=MeasureSpec.UNSPECIFIED)cap=Math.min(cap,MeasureSpec.getSize(height));super.onMeasure(width,MeasureSpec.makeMeasureSpec(cap,MeasureSpec.AT_MOST));}
        };scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);scroll.addView(form);sheet.addView(scroll,new LinearLayout.LayoutParams(-1,-2,1));
        LinearLayout footer=row();footer.setPadding(dp(20),dp(14),dp(20),dp(18));footer.addView(action("取消",Color.TRANSPARENT,MUTED,this::requestClose),new LinearLayout.LayoutParams(dp(76),dp(50)));
        TextView save=action("保存",UiTokens.FOREST,WHITE,this::save);save.setContentDescription("保存活动");LinearLayout.LayoutParams saveLp=new LinearLayout.LayoutParams(0,dp(50),1);saveLp.leftMargin=dp(10);footer.addView(save,saveLp);sheet.addView(footer);
        original=saved==null?fingerprint():saved.getString("original",fingerprint());
        Dialog dialog=new Dialog(getActivity()){@Override public void onBackPressed(){requestClose();}};dialog.setCanceledOnTouchOutside(false);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);dialog.getWindow().setWindowAnimations(R.style.PocketSheetMotion);dialog.setContentView(sheet);return dialog;
    }
    @Override public void onStart(){
        super.onStart();Window window=getDialog().getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(20),dp(520)),ViewGroup.LayoutParams.WRAP_CONTENT);window.setGravity(Gravity.BOTTOM);
        WindowManager.LayoutParams attrs=window.getAttributes();attrs.y=dp(10);attrs.dimAmount=.32f;window.setAttributes(attrs);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);sheet.requestFocus();
    }
    private void remove(){
        if(sleepDay!=null){
            try{PocketDb.get(getActivity()).overrideSleep(sleepDay);discardPhotos();((MainActivity)getActivity()).journalChanged();dismiss();}
            catch(Exception e){showError("暂未保存，请稍后重试");}return;
        }
        if(id!=0)new PocketDialogs.Builder(getActivity()).setTitle("删除这条手记？").setMessage("仅删除这条手动记录，应用使用记录会保留。").setNegativeButton("取消",null).setPositiveButton("删除手记",(d,w)->{
            try{PocketDb.get(getActivity()).deleteActivity(id);discardPhotos();((MainActivity)getActivity()).journalChanged();dismiss();}
            catch(Exception e){showError("删除失败，请稍后重试");}
        }).show();
    }
    private void save(){
        String name=title.getText().toString().trim();if(name.isEmpty()){title.setError("写下做了什么吧");title.requestFocus();title.post(()->title.requestRectangleOnScreen(new android.graphics.Rect(0,0,title.getWidth(),title.getHeight()),false));return;}
        long start=date.atTime(from).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long end=date.plusDays(endsNextDay?1:0).atTime(to).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        if(end<=start){showError("结束时间需要晚于开始时间；跨夜活动请在时间段中选择“次日”。");return;}
        if(end>System.currentTimeMillis()){showError("只能记录已过去的时间，请调整结束时间。");return;}
        try{ArrayList<byte[]> images=new ArrayList<>();for(String photo:photos)images.add(EntryPhotos.read(getActivity(),photo));
            PocketDb.get(getActivity()).saveActivityWithPhotos(id,name,category,note.getText().toString(),start,end,sleepDay,images);discardPhotos();
            if(!date.toString().equals(getArguments().getString("date")))((MainActivity)getActivity()).activitySaved(date);else ((MainActivity)getActivity()).journalChanged();dismiss();}
        catch(Exception e){showError("保存失败，原记录未修改，请稍后重试。");}
    }
    private View timeCard(boolean start){
        boolean narrow=getResources().getConfiguration().screenWidthDp<360||getResources().getConfiguration().fontScale>1.15f;LinearLayout card=narrow?row():column();card.setPadding(dp(16),dp(12),dp(12),dp(12));card.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x226b8d7c),round(Color.TRANSPARENT,18,false),round(WHITE,18,false)));card.setContentDescription(start?"设置开始时间":"设置结束时间");card.setFocusable(true);
        TextView caption=label(start?"开始时间":"结束时间",11);caption.setTextColor(MUTED);card.addView(caption,narrow?new LinearLayout.LayoutParams(0,-2,1):new LinearLayout.LayoutParams(-1,-2));if(!start)endCaption=caption;if(!narrow)space(card,6);
        TextView value=label("",22);value.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));card.addView(value);if(start)startButton=value;else endButton=value;
        card.setOnClickListener(v->{if(getFragmentManager().findFragmentByTag("time_range")==null)TimeRangeDialog.create(date,from,to,endsNextDay).show(getFragmentManager(),"time_range");});return card;
    }
    void applyTimeRange(LocalTime start,LocalTime end,boolean next){from=start;to=end;endsNextDay=next;updateTimes();if(error!=null)error.setVisibility(View.GONE);}
    void applyCalendarDate(LocalDate selected){date=selected;dateLabel.setText(date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd · EEEE",Locale.CHINA))+" ▾");updateTimes();}
    private void updateTimes(){startButton.setText(timeText(from));endButton.setText(timeText(to));endCaption.setText(endsNextDay?"结束 · 次日":"结束时间");long a=date.atTime(from).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),b=date.plusDays(endsNextDay?1:0).atTime(to).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();duration.setText(b>a?"共 "+MainActivity.duration(b-a):"待调整时间");}
    private void updateCategories(){if(categoryButton!=null)categoryButton.setText(category+"  ▾");}
    private String timeText(LocalTime time){return time.format(DateTimeFormatter.ofPattern(time.getSecond()==0?"HH:mm":"HH:mm:ss"));}
    @Override public void onSaveInstanceState(Bundle state){
        super.onSaveInstanceState(state);state.putLong("id",id);state.putString("date",date.toString());state.putString("from",from.toString());state.putString("to",to.toString());state.putBoolean("next",endsNextDay);state.putString("title",title.getText().toString());state.putString("note",note.getText().toString());state.putString("category",category);
        state.putString("sleep_day",sleepDay);state.putString("original",original);state.putStringArrayList("photos",new ArrayList<>(photos));
    }
    private String fingerprint(){return date+"|"+from+"|"+to+"|"+endsNextDay+"|"+category+"|"+title.getText()+"|"+note.getText()+"|"+photos;}
    private void requestClose(){if(fingerprint().equals(original)){discardPhotos();dismiss();return;}new PocketDialogs.Builder(getActivity()).setTitle("保存这次修改？").setMessage("还有未保存的内容。").setPositiveButton("保存",(d,w)->save()).setNeutralButton("继续编辑",null).setNegativeButton("放弃修改",(d,w)->{discardPhotos();dismiss();}).show();}
    @Override public void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=31||result!=Activity.RESULT_OK||data==null)return;ArrayList<String> added=data.getStringArrayListExtra("photos");if(added==null)return;
        if(photos.size()+added.size()>EntryPhotos.MAX_COUNT){EntryPhotos.discard(getActivity(),added);showError("每条手记最多 3 张图片");return;}photos.addAll(added);updatePhotos();photoStrip.post(()->photoStrip.requestRectangleOnScreen(new android.graphics.Rect(0,0,photoStrip.getWidth(),photoStrip.getHeight()),false));
    }
    private void discardPhotos(){EntryPhotos.discard(getActivity(),photos);}
    private void updatePhotos(){photoStrip.removeAllViews();int index=0;for(String ref:photos){final int number=++index;LinearLayout tile=column();ImageView image=new ImageView(getActivity());image.setImageBitmap(EntryPhotos.thumbnail(getActivity(),ref,dp(96)));image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackground(round(WHITE,12,true));image.setClipToOutline(true);image.setContentDescription("预览图片 "+number);image.setFocusable(true);image.setOnClickListener(v->{if(getFragmentManager().findFragmentByTag("photo_preview")==null)PhotoPreview.create(ref).show(getFragmentManager(),"photo_preview");});tile.addView(image,new LinearLayout.LayoutParams(dp(96),dp(96)));
        TextView remove=action("移除图片 "+number,Color.TRANSPARENT,UiTokens.PRIMARY,()->{photos.remove(ref);if(ref.startsWith("draft:"))EntryPhotos.discard(getActivity(),java.util.List.of(ref));updatePhotos();});remove.setTextSize(12);tile.addView(remove,new LinearLayout.LayoutParams(-1,dp(48)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(96),-2);lp.rightMargin=dp(10);photoStrip.addView(tile,lp);
        }addPhoto.setVisibility(photos.size()<EntryPhotos.MAX_COUNT?View.VISIBLE:View.GONE);
    }
    private void showError(String text){error.setText(text);error.setVisibility(View.VISIBLE);error.post(()->error.requestRectangleOnScreen(new android.graphics.Rect(0,0,error.getWidth(),error.getHeight()),false));}
    private TextView label(String value,int size){TextView v=new TextView(getActivity());v.setText(value);v.setTextSize(size);v.setTextColor(INK);v.setIncludeFontPadding(false);return v;}
    private GradientDrawable round(int color,int radius,boolean border){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(border)d.setStroke(dp(1),LINE);return d;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(getActivity());l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(getActivity());l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private void section(LinearLayout parent,String name){TextView label=label(name,13);label.setTypeface(null,Typeface.BOLD);parent.addView(label);space(parent,9);}
    private TextView action(String text,int fill,int color,Runnable action){TextView v=label(text,14);v.setGravity(Gravity.CENTER);v.setTextColor(color);v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));v.setBackground(UiControls.background(getActivity(),fill,4));v.setFocusable(true);v.setOnClickListener(w->action.run());return v;}
    private EditText input(String hint,String description,int limit,boolean single){EditText e=new EditText(getActivity());e.setTextSize(single?16:14);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setHint(hint);e.setContentDescription(description);e.setSingleLine(single);e.setPadding(dp(15),dp(14),dp(15),dp(14));e.setGravity(Gravity.TOP|Gravity.START);e.setBackground(round(WHITE,16,false));e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});if(!single)e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);return e;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void space(LinearLayout parent,int height){parent.addView(new View(getActivity()),new LinearLayout.LayoutParams(1,dp(height)));}
}
