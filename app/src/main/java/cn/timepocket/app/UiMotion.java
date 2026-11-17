package cn.timepocket.app;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

final class UiMotion {
    private static final DecelerateInterpolator EASE_OUT = new DecelerateInterpolator();

    static boolean enabled() {
        return ValueAnimator.areAnimatorsEnabled();
    }

    static void reveal(View view, float horizontalDp, float verticalDp) {
        if (!enabled()) return;
        float density = view.getResources().getDisplayMetrics().density;
        view.animate().cancel();
        view.setAlpha(0.82f);
        view.setTranslationX(horizontalDp * density);
        view.setTranslationY(verticalDp * density);
        view.animate().alpha(1f).translationX(0f).translationY(0f)
                .setDuration(190).setInterpolator(EASE_OUT).start();
    }

    private UiMotion() {}
}
