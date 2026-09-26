package com.dycomment.tv;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Pixel-level API-21 checks for the legacy drawing adapter, without network data. */
final class LegacyThemeSelfTest {
    private static void require(boolean condition, String detail) {
        if (!condition) throw new IllegalStateException("legacy theme: " + detail);
    }

    static void run(Activity activity) {
        LinearLayout header = new LinearLayout(activity);
        GradientDrawable old = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0xff2444ff, 0xffa622ff});
        LegacyTheme.background(header, old);
        require(pixel(header.getBackground(), 32, 32) == UiTheme.BLACK,
                "colored header gradient becomes black");

        TextView title = new TextView(activity);
        String userText = "作者😀 · 喜欢❤ 原标题";
        title.setText(userText);
        LegacyTheme.textColor(title, 0xffffaa00);
        require(title.getText().toString().equals(userText), "dynamic emoji text is preserved");
        require(title.getCurrentTextColor() == UiTheme.PINK, "warm legacy accent uses pink");
        LegacyTheme.textColor(title, 0xff777777);
        require(title.getCurrentTextColor() == UiTheme.MUTED, "secondary text remains legible");

        View bubble = new View(activity);
        GradientDrawable circle = new GradientDrawable();
        LegacyTheme.drawableColor(circle, 0xff3399ff);
        LegacyTheme.drawableShape(circle, GradientDrawable.OVAL);
        LegacyTheme.background(bubble, circle);
        require(Color.alpha(pixel(bubble.getBackground(), 32, 32)) == 0,
                "decorative bubbles are removed");

        TextView tab = new TextView(activity);
        tab.setFocusable(true);
        GradientDrawable selected = new GradientDrawable();
        LegacyTheme.drawableColor(selected, 0xfffe2c55);
        LegacyTheme.background(tab, selected);
        require(tab.isSelected(), "selected tab survives flattening");
        require(tab.getBackground() instanceof GradientDrawable,
                "legacy getBackground casts remain valid");
        final boolean[] called = {false};
        LegacyTheme.focusListener(tab, (view, focused) -> {
            called[0] = true;
            view.setScaleX(1.05f);
            view.setElevation(10);
        });
        tab.getOnFocusChangeListener().onFocusChange(tab, true);
        require(called[0] && tab.getScaleX() == 1f && tab.getElevation() == 0,
                "original focus behavior runs without zoom or glow");
        require(pixel(tab.getBackground(), 32, 32) == UiTheme.BLACK,
                "selected control fill stays black");
        android.util.Log.i("Android5InteractionTest", "LEGACY_THEME_GRADIENT_FOCUS_USER_TEXT_OK");
    }

    private static int pixel(android.graphics.drawable.Drawable drawable, int x, int y) {
        Bitmap bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, 64, 64);
        drawable.draw(new Canvas(bitmap));
        int color = bitmap.getPixel(x, y);
        bitmap.recycle();
        return color;
    }
}
