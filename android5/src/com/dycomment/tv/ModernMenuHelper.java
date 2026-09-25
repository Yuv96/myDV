package com.dycomment.tv;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/** Plain text, remote-friendly replacement retaining the Lite menu ABI. */
public final class ModernMenuHelper {
    public interface OnCancelListener { void onCancel(); }
    public interface OnItemSelectedListener { void onSelected(int index); }
    private static final ArrayList<WeakReference<Panel>> panels = new ArrayList<>();

    public static boolean isMenuShowing() {
        boolean found = false;
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel p = panels.get(i).get();
            if (p == null || p.getParent() == null) panels.remove(i); else found = true;
        }
        return found;
    }
    public static void showMenu(Activity a, String title, String[] labels, String[] ignoredIcons,
                                OnItemSelectedListener selected, OnCancelListener cancel) {
        show(a, title, labels, false, false, selected, cancel, null);
    }
    public static void showMenu(Activity a, String title, String[] labels,
                                OnItemSelectedListener selected, OnCancelListener cancel) {
        showMenu(a, title, labels, null, selected, cancel);
    }
    public static void showPicker(Activity a, String title, String[] labels, int current,
                                  OnItemSelectedListener selected, OnCancelListener cancel) {
        String[] text = labels.clone();
        if (current >= 0 && current < text.length) text[current] += "（当前）";
        showMenu(a, title, text, selected, cancel);
    }
    public static Panel show(Activity a, String title, String[] labels, boolean large, boolean keepOpen,
                             OnItemSelectedListener selected, OnCancelListener cancel, Runnable menu) {
        dismissCurrentMenu(a);
        Panel p = new Panel(a, title, labels, large, keepOpen, selected, cancel, menu);
        ((ViewGroup) a.getWindow().getDecorView()).addView(p, new ViewGroup.LayoutParams(-1, -1));
        panels.add(new WeakReference<>(p));
        if (p.rows.length > 0) p.rows[0].requestFocus();
        return p;
    }
    public static void dismissCurrentMenu(Activity a) {
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel p = panels.get(i).get();
            if (p != null && p.getContext() == a) p.close(false);
        }
        isMenuShowing();
    }
    static int dp(Context c, int n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }
    static GradientDrawable background(boolean focused) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(focused ? Color.rgb(48, 65, 88) : Color.TRANSPARENT);
        d.setCornerRadius(10);
        if (focused) d.setStroke(2, Color.rgb(255, 91, 121));
        return d;
    }
    public static final class Panel extends FrameLayout {
        public final TextView[] rows;
        final OnCancelListener cancel;
        final Runnable menu;
        final View previousFocus;
        boolean closed;
        Panel(Activity a, String title, String[] labels, boolean large, boolean keepOpen,
              OnItemSelectedListener selected, OnCancelListener cancel, Runnable menu) {
            super(a); this.cancel = cancel; this.menu = menu;
            setId(a.getResources().getIdentifier(large ? "android5_interaction_panel" : "android5_menu_panel", "id", a.getPackageName()));
            previousFocus = a.getCurrentFocus();
            setTag("android5-menu"); setFocusable(true); setClickable(true);
            setBackgroundColor(0x80000000);
            setOnClickListener(v -> close(true));
            LinearLayout box = new LinearLayout(a);
            box.setOrientation(LinearLayout.VERTICAL); box.setClickable(true);
            box.setBackgroundColor(0xff141b26);
            box.setPadding(dp(a, 24), dp(a, 18), dp(a, 24), dp(a, 18));
            int width = Math.min(dp(a, large ? 380 : 360), a.getResources().getDisplayMetrics().widthPixels * 3 / 4);
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(width, -1, Gravity.RIGHT);
            addView(box, bp);
            TextView heading = new TextView(a); heading.setText(title); heading.setTextColor(0xffaaaaaa);
            heading.setTextSize(16); heading.setPadding(0, 0, 0, dp(a, 12)); box.addView(heading);
            LinearLayout list = new LinearLayout(a); list.setOrientation(LinearLayout.VERTICAL);
            if (large) box.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
            else {
                ScrollView scroller = new ScrollView(a); scroller.setFillViewport(true); scroller.addView(list);
                box.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1));
            }
            rows = new TextView[labels.length];
            for (int i = 0; i < labels.length; i++) {
                final int index = i;
                TextView row = new TextView(a); rows[i] = row;
                row.setId(a.getResources().getIdentifier("android5_menu_row_" + i, "id", a.getPackageName()));
                row.setText(labels[i]); row.setTextColor(Color.WHITE); row.setTextSize(large ? 32 : 20);
                row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(a, 18), dp(a, 10), dp(a, 10), dp(a, 10));
                row.setFocusable(true); row.setClickable(true); row.setSingleLine(true);
                row.setBackground(background(false)); row.setTag("android5-menu-row-" + i);
                row.setOnFocusChangeListener((v, focus) -> v.setBackground(background(focus)));
                row.setOnClickListener(v -> { if (!keepOpen) close(false); if (selected != null) selected.onSelected(index); });
                LinearLayout.LayoutParams rp = large ? new LinearLayout.LayoutParams(-1, 0, 1) : new LinearLayout.LayoutParams(-1, dp(a, 56));
                rp.bottomMargin = dp(a, 4); list.addView(row, rp);
            }
            TextView hint = new TextView(a); hint.setText(large ? "菜单键：评论    返回键：关闭" : "上下选择    确定进入    返回关闭");
            hint.setTextSize(12); hint.setTextColor(0xffaaaaaa); hint.setPadding(0, dp(a, 8), 0, 0); box.addView(hint);
        }
        public void close(boolean notify) {
            if (closed) return;
            closed = true;
            if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
            if (previousFocus != null && previousFocus.isAttachedToWindow()) previousFocus.requestFocus();
            if (notify && cancel != null) cancel.onCancel();
        }
        @Override public boolean dispatchKeyEvent(KeyEvent e) {
            int key = e.getKeyCode();
            if (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_MENU) {
                if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                    if (key == KeyEvent.KEYCODE_MENU && menu != null) menu.run(); else close(true);
                }
                return true;
            }
            return super.dispatchKeyEvent(e);
        }
    }
}
