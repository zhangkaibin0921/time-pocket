package cn.timepocket.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.*;

/** Slim visible surfaces inside full-height touch targets. */
final class UiControls {
    static Drawable background(Context c,int fill,int inset){
        float d=c.getResources().getDisplayMetrics().density;
        GradientDrawable surface=new GradientDrawable();surface.setColor(fill);surface.setCornerRadius(28*d);
        GradientDrawable mask=new GradientDrawable();mask.setColor(0xffffffff);mask.setCornerRadius(28*d);
        int pad=Math.round(inset*d);
        return new RippleDrawable(ColorStateList.valueOf(0x226b8d7c),new InsetDrawable(surface,0,pad,0,pad),new InsetDrawable(mask,0,pad,0,pad));
    }
    private UiControls() {}
}
