package com.dycomment.tv;

import android.app.Activity;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** The visible official page owns QR creation, signing, polling and verification challenges. */
public class QrLoginActivity extends Activity {
    // API21 has an app-wide browser cookie jar, not independent WebView profiles.
    private static boolean cookieBusy;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor work = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(1), new ThreadPoolExecutor.AbortPolicy());
    private Future<?> pending;
    private FrameLayout container;
    private TextView status, operate, verify, refresh;
    RemoteWebView browser;
    private boolean foreground, first = true, ownsCookies, checking, saved, loginClicked, loginClickPending;
    private int generation;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout root = UiTheme.page(this, "抖音官方扫码登录");
        status = UiTheme.text(this, "请用抖音 App 扫码并在手机确认，然后选择“验证登录”", 16);
        root.addView(status);
        container = new FrameLayout(this);
        root.addView(container, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout controls = new LinearLayout(this);
        operate = UiTheme.button(this, "网页操作", () -> {
            if (browser != null) browser.requestFocus();
            status.setText("方向键移动光标，确定点击；返回回到按钮。请在官方页面完成扫码");
        });
        verify = UiTheme.button(this, "验证登录", () -> verifyLogin());
        refresh = UiTheme.button(this, "重新打开", () -> restart());
        for (TextView button : new TextView[] {operate, verify, refresh,
                UiTheme.button(this, "返回", () -> finish())})
            controls.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(controls);
        setContentView(root);
        operate.requestFocus();
    }

    @Override protected void onStart() {
        super.onStart();
        foreground = true;
        if (first) { first = false; restart(); }
    }

    private boolean valid(int token) {
        return token == generation && foreground && !isFinishing() && !isDestroyed();
    }

    private void destroyBrowser() {
        RemoteWebView old = browser;
        browser = null;
        if (old == null) return;
        old.cancelPointer();
        old.stopLoading();
        old.setWebViewClient(new WebViewClient());
        old.getSettings().setJavaScriptEnabled(false);
        old.clearHistory();
        old.clearCache(true);
        container.removeView(old);
        old.destroy();
    }

    private void stopSession(Runnable cleaned) {
        generation++;
        checking = false;
        main.removeCallbacksAndMessages(null);
        if (pending != null) pending.cancel(true);
        pending = null;
        work.purge();
        destroyBrowser();
        if (!ownsCookies) {
            if (cleaned != null) cleaned.run();
            return;
        }
        ownsCookies = false;
        try {
            WebStorage.getInstance().deleteAllData();
            CookieManager.getInstance().removeAllCookies(removed -> {
                boolean flushed = false;
                try {
                    CookieManager.getInstance().flush();
                    flushed = true;
                } catch (RuntimeException unavailable) {
                    if (!isDestroyed()) {
                        status.setText("系统网页组件无法清理登录会话，请更新 WebView 后重试");
                        refresh.setEnabled(true);
                    }
                } finally {
                    cookieBusy = false;
                }
                if (flushed && cleaned != null) cleaned.run();
            });
        } catch (RuntimeException unavailable) {
            cookieBusy = false;
            // Do not open a new page when the browser could not clear its candidate account.
            if (status != null) status.setText("系统网页组件无法清理登录会话，请更新 WebView 后重试");
            if (refresh != null) refresh.setEnabled(true);
        }
    }

    private void restart() {
        refresh.setEnabled(false);
        verify.setEnabled(false);
        final int expectedGeneration = generation + 1;
        stopSession(() -> {
            if (!valid(expectedGeneration)) return;
            if (cookieBusy) {
                status.setText("上一登录窗口正在清理，请稍后重新打开；现有账号已保留");
                refresh.setEnabled(true);
                return;
            }
            cookieBusy = true;
            ownsCookies = true;
            final int token = generation;
            status.setText("正在打开官方登录页…");
            try {
                WebStorage.getInstance().deleteAllData();
                CookieManager.getInstance().removeAllCookies(removed -> {
                    if (!valid(token)) return;
                    try { showPage(token); }
                    catch (RuntimeException unsupported) {
                        fail("当前系统网页组件无法打开登录页，请更新系统 WebView 后重试");
                    }
                });
            } catch (RuntimeException unsupported) {
                fail("当前系统网页组件不可用，请更新系统 WebView 后重试");
            }
        });
    }

    private void showPage(int token) {
        saved = false;
        loginClicked = false;
        loginClickPending = false;
        browser = new RemoteWebView(this);
        OfficialLoginPolicy.configure(browser.getSettings());
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(browser, true);
        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String address) {
                if (OfficialLoginPolicy.navigation(address)) return false;
                status.setText("已阻止离开官方登录网站；请用抖音 App 扫码");
                return true;
            }
            @Override public void onPageStarted(WebView view, String address, Bitmap icon) {
                if (valid(token) && !OfficialLoginPolicy.navigation(address))
                    fail("登录页面跳转地址无法验证；现有账号已保留");
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                if (valid(token)) fail("官方网页安全连接失败；现有账号已保留");
            }
            @Override public void onReceivedError(WebView view, int code, String description, String address) {
                if (valid(token)) fail("官方登录页加载失败，请检查网络或更新系统 WebView；现有账号已保留");
            }
            @Override public void onPageFinished(WebView view, String address) {
                if (!valid(token)) return;
                status.setText("请扫描官方二维码；未显示时用“网页操作”点击官网登录。手机确认后选择“验证登录”");
                maybeOpenLogin(token, 0);
            }
        });
        container.addView(browser, new FrameLayout.LayoutParams(-1, -1));
        refresh.setEnabled(true);
        verify.setEnabled(true);
        openOfficialPage(browser);
        main.postDelayed(() -> {
            if (valid(token)) fail("本次登录窗口已超时，请重新打开；现有账号已保留");
        }, 300000);
    }

    // A test-only subclass supplies an inert offline page; production always opens this URL.
    void openOfficialPage(RemoteWebView view) { view.loadUrl(OfficialLoginPolicy.HOME); }

    private void maybeOpenLogin(int token, int tries) {
        if (!valid(token) || browser == null || loginClicked || loginClickPending || tries >= 12
                || !OfficialLoginPolicy.home(browser.getUrl())) return;
        loginClickPending = true;
        // Operate one visible official button, without implementing signing or reading cookies in JS.
        browser.evaluateJavascript("(function(){var b=document.querySelectorAll('button');"
                + "for(var i=0;i<b.length;i++){if(b[i].textContent.trim()==='登录'"
                + "&&b[i].getBoundingClientRect().width>0){b[i].click();return true;}}return false;})()",
                result -> {
                    if (!valid(token)) return;
                    loginClickPending = false;
                    if ("true".equals(result)) loginClicked = true;
                    else main.postDelayed(() -> maybeOpenLogin(token, tries + 1), 750);
                });
    }

    void verifyLogin() {
        if (checking || browser == null || !foreground) return;
        if (!OfficialLoginPolicy.home(browser.getUrl())) {
            status.setText("请先在抖音官网完成扫码及手机确认，再验证登录");
            return;
        }
        // Native CookieManager includes HttpOnly and applies URL scope to the candidate header.
        final String candidate = CookieManager.getInstance().getCookie(OfficialLoginPolicy.SELF);
        if (!OfficialLoginPolicy.candidate(candidate)) {
            status.setText("尚未取得完整登录会话，请先扫码并在手机确认；现有账号已保留");
            return;
        }
        final int token = generation;
        final String previous = SocialApi.cookie();
        checking = true;
        verify.setEnabled(false);
        status.setText("正在独立验证新账号…");
        try {
            pending = work.submit(() -> {
                try {
                    OfficialLoginPolicy.verified(SocialApi.request(OfficialLoginPolicy.SELF_PATH,
                            SocialApi.params(), false, candidate));
                    main.post(() -> {
                        if (!valid(token)) return;
                        checking = false;
                        if (!previous.equals(SocialApi.cookie())) {
                            fail("账号已在其他窗口改变，请重新登录；现有账号已保留");
                            return;
                        }
                        try {
                            CredentialStore.save(this, candidate);
                            saved = true;
                            stopSession(null);
                            status.setText("登录成功；评论与互动权限仍以对应接口结果为准");
                            refresh.setEnabled(true);
                            verify.setEnabled(false);
                        } catch (Exception failed) { fail("新账号保存失败，请重试"); }
                    });
                } catch (Exception rejected) {
                    main.post(() -> {
                        if (!valid(token)) return;
                        checking = false;
                        verify.setEnabled(true);
                        status.setText("新账号未通过独立验证，请在官网完成登录后再试；现有账号已保留");
                    });
                }
            });
        } catch (RuntimeException busy) {
            checking = false;
            verify.setEnabled(true);
            status.setText("上次账号验证正在结束，请稍后重试；现有账号已保留");
        }
    }

    private void fail(String message) {
        stopSession(null);
        status.setText(message);
        refresh.setEnabled(true);
        verify.setEnabled(false);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if ((event.getKeyCode() == KeyEvent.KEYCODE_BACK || event.getKeyCode() == KeyEvent.KEYCODE_MENU)
                && browser != null && browser.hasFocus()) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                browser.cancelPointer();
                operate.requestFocus();
                browser.invalidate();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override protected void onStop() {
        foreground = false;
        stopSession(null);
        refresh.setEnabled(true);
        verify.setEnabled(false);
        if (!saved) status.setText("登录窗口已暂停，请重新打开；现有账号已保留");
        super.onStop();
    }

    @Override protected void onDestroy() {
        foreground = false;
        stopSession(null);
        work.shutdownNow();
        super.onDestroy();
    }
}
