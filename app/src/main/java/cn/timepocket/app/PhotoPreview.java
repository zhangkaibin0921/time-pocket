package cn.timepocket.app;

import android.app.*;
import android.os.Bundle;
import android.widget.*;

public final class PhotoPreview extends DialogFragment {
    static PhotoPreview create(String ref){PhotoPreview p=new PhotoPreview();Bundle b=new Bundle();b.putString("photo",ref);p.setArguments(b);return p;}
    @Override public Dialog onCreateDialog(Bundle state){ImageView image=new ImageView(getActivity());image.setImageBitmap(EntryPhotos.thumbnail(getActivity(),getArguments().getString("photo"),1600));image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setAdjustViewBounds(true);image.setContentDescription("手记图片预览");image.setMaxHeight((int)(getResources().getDisplayMetrics().heightPixels*.65f));return new PocketDialogs.Builder(getActivity()).setTitle("手记图片").setView(image).setPositiveButton("关闭",null).create();}
}
