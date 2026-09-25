package com.dycomment.tv;

import android.app.Activity;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared, API-21-safe black / pink / white components. */
final class UiTheme {
    static final int BLACK = 0xff000000, WHITE = 0xffffffff, MUTED = 0xb3ffffff, PINK = 0xffff5b79;

    static TextView text(Activity a, String label, int size) {
        TextView v = new TextView(a);
        v.setText(label);
        v.setTextColor(WHITE);
        v.setTextSize(size);
        return v;
    }

    static TextView button(Activity a, String label, Runnable action) {
        TextView v = text(a, label, 20);
        int p = ModernMenuHelper.dp(a, 12);
        v.setPadding(p, p, p, p);
        v.setGravity(Gravity.CENTER);
        v.setFocusable(true);
        v.setClickable(true);
        v.setBackground(ModernMenuHelper.background(false));
        v.setOnFocusChangeListener(
                (view, focus) -> view.setBackground(ModernMenuHelper.background(focus)));
        v.setOnClickListener(view -> action.run());
        return v;
    }

    static LinearLayout page(Activity a, String title) {
        a.getWindow().setStatusBarColor(BLACK);
        a.getWindow().setNavigationBarColor(BLACK);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(1);
        root.setBackgroundColor(BLACK);
        int p = ModernMenuHelper.dp(a, 24);
        root.setPadding(p, p, p, p);
        root.addView(text(a, title, 26));
        return root;
    }
}
