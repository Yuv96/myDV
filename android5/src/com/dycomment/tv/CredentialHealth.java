package com.dycomment.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

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
        currentAccount(session);
        failures.remove(endpoint);
        if (endpoint.equals(suspect)) {
            suspect = "";
            MAIN.post(() -> render());
        }
    }

    static synchronized void rejected(String endpoint, String session, boolean explicit) {
        if (session.isEmpty() || !session.equals(SocialApi.cookie())) return;
        currentAccount(session);
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
        currentAccount(SocialApi.cookie());
        return !suspect.isEmpty();
    }

    private static void currentAccount(String session) {
        if (session.equals(account)) return;
        failures.clear();
        suspect = "";
        account = session;
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
        // Remove a banner left by an existing host; account state now belongs to the clock capsule.
        View banner = decor.findViewById(BANNER);
        if (banner != null && banner.getParent() instanceof ViewGroup)
            ((ViewGroup) banner.getParent()).removeView(banner);
        InteractionController.updateClock(a);
    }
}
