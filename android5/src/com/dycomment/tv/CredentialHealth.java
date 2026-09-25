package com.dycomment.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.HashMap;

/**
 * Request-based suspicion, not an arbitrary Cookie expiry timer. Network errors are not login
 * errors.
 */
final class CredentialHealth {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<Activity> host = new WeakReference<>(null);
    private static final HashMap<String, Window> failures = new HashMap<>();
    private static String suspect = "", account = "";
    private static final int BANNER = 0x7f0f7a58;

    static final class Window {
        int count;
        long first, last;

        boolean record(long now) {
            if (count == 0 || now - first > 300000) {
                count = 0;
                first = now;
            }
            if (count == 0 || now - last >= 10000) {
                count++;
                last = now;
            }
            return count >= 3;
        }
    }

    static synchronized void reset() {
        failures.clear();
        suspect = "";
        account = SocialApi.cookie();
        MAIN.post(() -> render());
    }

    static synchronized void success(String endpoint, String session) {
        if (!session.equals(SocialApi.cookie())) return;
        failures.remove(endpoint);
        if (endpoint.equals(suspect)) {
            suspect = "";
            MAIN.post(() -> render());
        }
    }

    static synchronized void rejected(String endpoint, String session, boolean explicit) {
        if (session.isEmpty() || !session.equals(SocialApi.cookie())) return;
        if (!session.equals(account)) {
            failures.clear();
            suspect = "";
            account = session;
        }
        long now = SystemClock.elapsedRealtime();
        Window w = failures.get(endpoint);
        if (w == null) {
            w = new Window();
            failures.put(endpoint, w);
        }
        boolean repeated = w.record(now);
        if (explicit || repeated) {
            suspect = endpoint;
            MAIN.post(() -> render());
        }
    }

    static synchronized boolean needsRefresh() {
        return !suspect.isEmpty();
    }

    static void attach(Activity a) {
        host = new WeakReference<>(a);
        render();
    }

    static void detach(Activity a) {
        if (host.get() == a) {
            host.clear();
            MAIN.removeCallbacksAndMessages(null);
        }
    }

    static void open(Activity a) {
        a.startActivity(new Intent(a, QrLoginActivity.class));
    }

    private static void render() {
        Activity a = host.get();
        if (a == null || a.isFinishing() || a.isDestroyed()) return;
        ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();
        TextView banner = decor.findViewById(BANNER);
        String label =
                !SocialApi.personalCookie()
                        ? "尚未登录 · 返回键 → 账号与登录 → 扫码登录"
                        : needsRefresh() ? "登录或评论权限可能失效 · 返回键 → 账号与登录 → 重新扫码" : "";
        if (label.isEmpty()) {
            if (banner != null) decor.removeView(banner);
            return;
        }
        if (banner == null) {
            banner = UiTheme.text(a, label, 14);
            banner.setId(BANNER);
            banner.setTextColor(UiTheme.PINK);
            banner.setBackgroundColor(0xe6000000);
            int p = ModernMenuHelper.dp(a, 10);
            banner.setPadding(p, p, p, p);
            banner.setOnClickListener(v -> open(a));
            FrameLayout.LayoutParams lp =
                    new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            lp.topMargin = ModernMenuHelper.dp(a, 18);
            decor.addView(banner, lp);
        }
        if (!label.contentEquals(banner.getText())) banner.setText(label);
    }
}
