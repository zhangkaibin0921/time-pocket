package cn.timepocket.app;

import android.content.Context;
import android.graphics.*;
import android.view.*;

final class ArtViews {
    static final int INK=Color.rgb(40,61,56),CORAL=Color.rgb(239,116,85),MINT=Color.rgb(107,173,151),PAPER=Color.rgb(250,247,240);
    static class Mascot extends View {
        private final Paint p=new Paint(3);
        private final boolean animated;
        Mascot(Context c,boolean animated) {super(c);this.animated=animated;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas raw) {
            super.onDraw(raw);Canvas c=raw;float scale=Math.min(getWidth()/160f,getHeight()/160f);
            c.save();c.translate((getWidth()-160*scale)/2,(getHeight()-160*scale)/2);c.scale(scale,scale);
            boolean move=animated&&android.animation.ValueAnimator.areAnimatorsEnabled();
            float bob=move?(float)Math.sin(System.currentTimeMillis()/800.0)*3:0;c.translate(0,bob);
            p.setColor(Color.parseColor("#F6C5AF"));c.drawOval(23,133,140,145,p);
            p.setColor(INK);p.setStrokeWidth(6);p.setStrokeCap(Paint.Cap.ROUND);
            c.drawLine(54,123,47,137,p);c.drawLine(110,123,119,137,p);
            c.drawLine(30,86,18,76,p);c.drawLine(132,86,145,68,p);
            p.setColor(CORAL);c.drawRoundRect(28,24,134,132,34,34,p);
            p.setColor(Color.parseColor("#FFF9EC"));c.drawCircle(81,75,43,p);
            p.setColor(INK);p.setStrokeWidth(4);c.drawLine(81,42,81,69,p);c.drawLine(81,69,99,58,p);
            boolean blink=move&&System.currentTimeMillis()%4800<180;
            if(blink) {c.drawLine(62,83,69,83,p);c.drawLine(94,83,101,83,p);}else {c.drawOval(63,78,68,85,p);c.drawOval(95,78,100,85,p);}
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.5f);c.drawArc(74,84,90,95,10,160,false,p);p.setStyle(Paint.Style.FILL);
            p.setColor(Color.parseColor("#F5B6A4"));c.drawOval(53,87,66,93,p);c.drawOval(99,87,112,93,p);
            p.setColor(MINT);c.drawCircle(22,30,5,p);c.drawCircle(143,110,4,p);
            p.setColor(Color.parseColor("#DBB752"));c.drawCircle(135,22,6,p);
            c.restore();if(move&&isShown()) postInvalidateDelayed(70);
        }
    }
    /** Decorative timeline rail; the adjacent card supplies the accessible content. */
    static class TimelineRail extends View {
        private final Paint p=new Paint(3);private final int color;
        TimelineRail(Context context,int color){super(context);this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){
            float d=getResources().getDisplayMetrics().density,x=getWidth()/2f,y=24*d;
            p.setColor(UiTokens.OUTLINE);p.setStrokeWidth(1.5f*d);c.drawLine(x,0,x,getHeight(),p);
            p.setColor(UiTokens.PAPER);c.drawCircle(x,y,7*d,p);p.setColor(color);c.drawCircle(x,y,4*d,p);
        }
    }
    static class Chart extends View {
        final long[] values;final String[] labels,descriptions;final Paint p=new Paint(3);int selected;
        private final java.util.function.IntConsumer listener;private float downX,downY;private boolean moved;
        Chart(Context c,long[] values,String[] labels,String[] descriptions,int selected,java.util.function.IntConsumer listener) {
            super(c);this.values=values;this.labels=labels;this.descriptions=descriptions;this.selected=selected;this.listener=listener;
            setFocusable(true);setClickable(true);describe();setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        }
        void clearSelection(){selected=-1;describe();invalidate();}
        private void describe(){setContentDescription("使用时长柱状图"+(selected<0?"，未选择日期":"，已选 "+descriptions[selected]+"，"+MainActivity.duration(values[selected])));}
        private void choose(int index){selected=index;describe();invalidate();if(listener!=null)listener.accept(index);}
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);float d=getResources().getDisplayMetrics().density;
            float left=6*d,right=getWidth()-6*d,top=29*d,bottom=getHeight()-29*d;
            long max=1;for(long n:values)max=Math.max(max,n);
            p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));p.setTextSize(11*getResources().getDisplayMetrics().scaledDensity);
            p.setColor(Color.parseColor("#E9E8DF"));p.setStrokeWidth(d);
            for(int i=0;i<3;i++) {float y=top+(bottom-top)*i/2;c.drawLine(left,y,right,y,p);}
            float cell=(right-left)/values.length;float bar=Math.min(24*d,cell*.55f);
            int highest=0;for(int i=1;i<values.length;i++)if(values[i]>values[highest])highest=i;
            int lastLabel=labels.length-1;while(lastLabel>=0&&labels[lastLabel]==null)lastLabel--;
            float lastTextRight=-Float.MAX_VALUE,finalLabelLeft=Float.MAX_VALUE;
            if(lastLabel>=0){float w=p.measureText(labels[lastLabel]),x=left+cell*(lastLabel+.5f);finalLabelLeft=Math.max(left+w/2,Math.min(x,right-w/2))-w/2;}
            for(int i=0;i<values.length;i++) {
                float x=left+cell*(i+.5f),h=(bottom-top)*(float)values[i]/max;
                p.setColor(i==selected?CORAL:Color.parseColor("#B7D6C8"));
                c.drawRoundRect(x-bar/2,bottom-Math.max(3*d,h),x+bar/2,bottom,bar/2,bar/2,p);
                if(labels[i]!=null) {float w=p.measureText(labels[i]),tx=Math.max(left+w/2,Math.min(x,right-w/2));
                    if(i==lastLabel||(tx-w/2>=lastTextRight+4*d&&tx+w/2+4*d<=finalLabelLeft)){p.setColor(INK);p.setTextAlign(Paint.Align.CENTER);c.drawText(labels[i],tx,bottom+19*d,p);lastTextRight=tx+w/2;}
                }
                if(i==selected||(selected<0&&i==highest&&values[i]>0)) {
                    p.setColor(UiTokens.PRIMARY);p.setTextAlign(Paint.Align.CENTER);String tip=MainActivity.duration(values[i]);
                    float tx=Math.max(p.measureText(tip)/2+left,Math.min(x,right-p.measureText(tip)/2));
                    c.drawText(tip,tx,Math.max(14*d,bottom-h-9*d),p);
                }
            }
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if(e.getAction()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();moved=false;return true;}
            if(e.getAction()==MotionEvent.ACTION_MOVE){int slop=ViewConfiguration.get(getContext()).getScaledTouchSlop();if(Math.abs(e.getX()-downX)>slop||Math.abs(e.getY()-downY)>slop)moved=true;return true;}
            if(e.getAction()==MotionEvent.ACTION_CANCEL){moved=true;return true;}
            if(e.getAction()==MotionEvent.ACTION_UP){if(!moved&&values.length>0){float pad=6*getResources().getDisplayMetrics().density;int index=(int)((e.getX()-pad)/Math.max(1,getWidth()-2*pad)*values.length);choose(Math.max(0,Math.min(values.length-1,index)));super.performClick();}return true;}
            return true;
        }
        @Override public boolean performClick() {
            super.performClick();String[] choices=new String[values.length];for(int i=0;i<values.length;i++)choices[i]=descriptions[i]+" · "+MainActivity.duration(values[i]);
            new PocketDialogs.Builder(getContext()).setTitle("选择图表时段").setItems(choices,(d,w)->choose(w)).setNegativeButton("取消",null).show();return true;
        }
    }
    static class ActionIcon extends View {
        static final int INFO=0,REFRESH=1,PREVIOUS=2,NEXT=3,CLOSE=4,SEARCH=5,MORE=6;
        private final int kind;private final Paint p=new Paint(3);
        ActionIcon(Context c,int kind){super(c);this.kind=kind;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){
            c.save();c.scale(getWidth()/24f,getHeight()/24f);p.setColor(UiTokens.INK);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            if(kind==INFO){c.drawCircle(12,12,8.5f,p);c.drawLine(12,11,12,16,p);c.drawPoint(12,7.5f,p);}
            else if(kind==REFRESH){c.drawArc(4,4,20,20,30,300,false,p);c.drawLine(19,3,19,8,p);c.drawLine(14,8,19,8,p);}
            else if(kind==CLOSE){c.drawLine(7,7,17,17,p);c.drawLine(17,7,7,17,p);}
            else if(kind==SEARCH){c.drawCircle(10.5f,10.5f,6.5f,p);c.drawLine(15.5f,15.5f,21,21,p);}
            else if(kind==MORE){p.setStyle(Paint.Style.FILL);c.drawCircle(5,12,1.6f,p);c.drawCircle(12,12,1.6f,p);c.drawCircle(19,12,1.6f,p);}
            else {float a=kind==PREVIOUS?14:10,b=kind==PREVIOUS?9:15;c.drawLine(a,7,b,12,p);c.drawLine(b,12,a,17,p);}
            c.restore();
        }
    }
    static class NavIcon extends View {
        final int kind,color;final Paint p=new Paint(3);
        NavIcon(Context c,int kind,int color) {super(c);this.kind=kind;this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas raw) {
            Canvas c=raw;c.save();c.scale(getWidth()/26f,getHeight()/26f);p.setColor(color);p.setStrokeWidth(2);p.setStrokeCap(Paint.Cap.ROUND);p.setStyle(Paint.Style.STROKE);
            if(kind==0) {c.drawRoundRect(3,3,23,23,6,6,p);c.drawLine(8,17,8,13,p);c.drawLine(13,17,13,8,p);c.drawLine(18,17,18,11,p);}
            else if(kind==1) {c.drawCircle(13,13,10,p);c.drawLine(13,6,13,13,p);c.drawLine(13,13,18,16,p);}
            else if(kind==3) {c.drawRoundRect(3,5,23,23,4,4,p);c.drawLine(8,2,8,7,p);c.drawLine(18,2,18,7,p);c.drawLine(3,10,23,10,p);c.drawLine(8,16,11,19,p);c.drawLine(11,19,18,14,p);}
            else {c.drawRoundRect(3,3,23,23,6,6,p);c.drawLine(7,9,19,9,p);c.drawLine(7,17,19,17,p);p.setStyle(Paint.Style.FILL);c.drawCircle(10,9,3,p);c.drawCircle(16,17,3,p);}
            c.restore();
        }
    }
}
