package cn.timepocket.app;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.ViewGroup;
import android.widget.TextView;

/** Shared surfaces and actions for platform message, list and calendar dialogs. */
final class PocketDialogs {
    static final class Builder extends AlertDialog.Builder {
        Builder(Context context){super(context,R.style.PocketDialogTheme);}
        @Override public AlertDialog create(){AlertDialog dialog=super.create();dialog.setOnShowListener(d->style(dialog));return dialog;}
    }
    static void style(AlertDialog dialog){
        Context c=dialog.getContext();float density=c.getResources().getDisplayMetrics().density;
        if(dialog.getWindow()!=null){GradientDrawable background=new GradientDrawable();background.setColor(UiTokens.PAPER);background.setCornerRadius(24*density);dialog.getWindow().setBackgroundDrawable(background);dialog.getWindow().setLayout(Math.min(c.getResources().getDisplayMetrics().widthPixels-Math.round(24*density),Math.round(420*density)),ViewGroup.LayoutParams.WRAP_CONTENT);}
        TextView message=dialog.findViewById(android.R.id.message);if(message!=null){message.setTextSize(14);message.setTextColor(UiTokens.MUTED);message.setLineSpacing(3*density,1);}
        for(int id:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}){
            TextView button=dialog.getButton(id);if(button==null)continue;button.setAllCaps(false);button.setTextSize(14);button.setMinHeight(Math.round(48*density));button.setTextColor(id==AlertDialog.BUTTON_POSITIVE?UiTokens.FOREST:UiTokens.MUTED);button.setBackground(UiControls.background(c,id==AlertDialog.BUTTON_POSITIVE?UiTokens.GREEN:Color.TRANSPARENT,4));button.setPadding(Math.round(14*density),0,Math.round(14*density),0);
        }
    }
    private PocketDialogs(){}
}
