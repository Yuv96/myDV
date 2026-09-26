package com.dycomment.tv;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.WeakHashMap;

/** Shared drawing policy for the three pinned, programmatically built legacy pages. */
public final class LegacyTheme {
    private static final WeakHashMap<GradientDrawable, Boolean> ovals = new WeakHashMap<>();
    private static final WeakHashMap<GradientDrawable, Integer> colors = new WeakHashMap<>();
    private LegacyTheme() {}

    public static void attach(Activity activity) {
        activity.getWindow().setStatusBarColor(UiTheme.BLACK);
        activity.getWindow().setNavigationBarColor(UiTheme.BLACK);
        final ViewGroup content = activity.findViewById(android.R.id.content);
        content.setBackgroundColor(UiTheme.BLACK);
        final WeakHashMap<View, Boolean> styled = new WeakHashMap<>();
        ViewTreeObserver.OnGlobalLayoutListener listener = () -> styleNew(content, styled);
        content.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        content.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {}
            @Override public void onViewDetachedFromWindow(View view) {
                if (content.getViewTreeObserver().isAlive())
                    content.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
                styled.clear();
                content.removeOnAttachStateChangeListener(this);
            }
        });
        styleNew(content, styled);
    }

    private static void styleNew(View view, WeakHashMap<View, Boolean> styled) {
        if (!styled.containsKey(view)) {
            styled.put(view, Boolean.TRUE);
            if (view instanceof TextView) {
                TextView text = (TextView) view;
                textColor(text, text.getCurrentTextColor());
                if (text instanceof EditText) ((EditText) text).setHintTextColor(UiTheme.MUTED);
                if (view.isFocusable()) {
                    text.setMinimumHeight(ModernMenuHelper.dp(view.getContext(), 44));
                    // The old icon-only back and clear buttons need room for real labels.
                    if ("返回".contentEquals(text.getText()) || "清空".contentEquals(text.getText())) {
                        ViewGroup.LayoutParams params = view.getLayoutParams();
                        int width = ModernMenuHelper.dp(view.getContext(), 72);
                        if (params != null && params.width > 0 && params.width < width) {
                            params.width = width;
                            view.setLayoutParams(params);
                        }
                        text.setTextSize(18);
                    }
                }
            }
            if (view.isFocusable()) control(view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) styleNew(group.getChildAt(i), styled);
        }
    }

    private static boolean accent(int color) {
        return Color.red(color) > 180 && Color.red(color) > Color.green(color) * 1.25f
                && Color.red(color) > Color.blue(color) * 1.1f;
    }

    public static void textColor(TextView view, int color) {
        int brightness = Math.max(Color.red(color), Math.max(Color.green(color), Color.blue(color)));
        view.setTextColor(accent(color) ? UiTheme.PINK
                : brightness >= 210 && Color.alpha(color) >= 220 ? UiTheme.WHITE : UiTheme.MUTED);
    }

    public static void backgroundColor(View view, int color) {
        if (view.isFocusable()) control(view);
        else if (view.getClass() == View.class)
            view.setBackgroundColor(accent(color) ? UiTheme.PINK : 0x26ffffff);
        else view.setBackgroundColor(Color.alpha(color) == 0 ? Color.TRANSPARENT : UiTheme.BLACK);
    }

    public static void drawableColor(GradientDrawable drawable, int color) {
        colors.put(drawable, color);
        drawable.setColor(accent(color) ? UiTheme.PINK
                : Color.alpha(color) == 0 ? Color.TRANSPARENT : UiTheme.BLACK);
    }

    public static void drawableShape(GradientDrawable drawable, int shape) {
        ovals.put(drawable, shape == GradientDrawable.OVAL);
        drawable.setShape(shape);
    }

    public static void background(View view, Drawable drawable) {
        // Keep GradientDrawable: the upstream tab handlers cast getBackground() to it.
        if (drawable instanceof GradientDrawable) {
            GradientDrawable gradient = (GradientDrawable) drawable;
            if (view.getClass() == View.class && Boolean.TRUE.equals(ovals.get(gradient))) {
                gradient.setColor(Color.TRANSPARENT); // Remove decorative colored header bubbles.
                gradient.setStroke(0, Color.TRANSPARENT);
            } else if (view instanceof ViewGroup || view instanceof TextView) {
                Integer color = colors.get(gradient);
                if (view instanceof TextView && color != null) view.setSelected(accent(color));
                gradient.setColors(new int[] {UiTheme.BLACK, UiTheme.BLACK});
                gradient.setStroke(0, Color.TRANSPARENT);
            } else if (view instanceof ImageView) {
                gradient.setColor(0xff181818);
                gradient.setStroke(0, Color.TRANSPARENT);
            }
        }
        view.setBackground(drawable);
        if (view.isFocusable()) control(view);
    }

    public static void focusListener(View view, View.OnFocusChangeListener original) {
        view.setOnFocusChangeListener((target, focused) -> {
            if (original != null) original.onFocusChange(target, focused);
            // Preserve tab selection/navigation while replacing the old zoom-and-glow treatment.
            target.animate().cancel();
            target.setScaleX(1f);
            target.setScaleY(1f);
            target.setElevation(0f);
            control(target);
        });
    }

    static void control(View view) {
        GradientDrawable background = view.getBackground() instanceof GradientDrawable
                ? (GradientDrawable) view.getBackground() : new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColors(new int[] {view.isFocused() ? 0xff202020 : UiTheme.BLACK,
                view.isFocused() ? 0xff202020 : UiTheme.BLACK});
        background.setCornerRadius(ModernMenuHelper.dp(view.getContext(), 10));
        background.setStroke(ModernMenuHelper.dp(view.getContext(), view.isFocused() ? 2 : 1),
                view.isFocused() || view.isSelected() ? UiTheme.PINK : 0x26ffffff);
        view.setBackground(background);
    }
}
