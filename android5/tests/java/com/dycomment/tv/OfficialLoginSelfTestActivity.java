package com.dycomment.tv;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import org.json.JSONObject;

/** No real account: offline browser isolation plus an optional anonymous official-page probe. */
public final class OfficialLoginSelfTestActivity extends QrLoginActivity {
    private static final String TAG = "Android5OfficialLoginTest";
    private final Handler test = new Handler(Looper.getMainLooper());
    private boolean probe, completed, screenshotSafe;
    private String original = "";
    private static final String OLD = "sessionid=fixture-old-native-account";

    private void require(boolean condition, String label) {
        if (!condition) throw new IllegalStateException(label);
    }

    private void fixtureCookie(String value) throws Exception {
        Class.forName("com.dycomment.tv.DouyinApi")
                .getMethod("setCookie", String.class, android.content.Context.class)
                .invoke(null, value, this);
    }

    @Override public void onCreate(Bundle state) {
        probe = getIntent().getBooleanExtra("official_probe", false);
        original = SocialApi.cookie();
        if (!probe) {
            try { fixtureCookie(OLD); }
            catch (Exception error) { Log.e(TAG, "FAIL fixture setup"); }
            CookieManager.getInstance().setCookie(OfficialLoginPolicy.HOME,
                    "sessionid=fixture-stale-browser; Secure; HttpOnly; Path=/");
        }
        super.onCreate(state);
        if (probe) test.postDelayed(() -> observeOfficialPage(), 35000);
    }

    @Override void openOfficialPage(RemoteWebView view) {
        if (probe) {
            super.openOfficialPage(view);
        } else {
            view.loadDataWithBaseURL(OfficialLoginPolicy.HOME,
                    "<!doctype html><html><head><meta name='viewport' content='width=device-width'></head>"
                    + "<body style='background:white;color:black;margin:0'>"
                    + "<button style='width:100%;height:100vh' onclick='window.clicked=(window.clicked||0)+1'>登录</button>"
                    + "</body></html>", "text/html", "UTF-8", OfficialLoginPolicy.HOME);
            test.postDelayed(() -> fixtures(), 1800);
        }
    }

    private void fixtures() {
        try {
            require(browser != null, "browser created");
            require(OLD.equals(SocialApi.cookie()), "opening browser preserves native account");
            require(!OfficialLoginPolicy.candidate(CookieManager.getInstance().getCookie(OfficialLoginPolicy.SELF)),
                    "fresh browser does not inherit previous browser account");
            verifyLogin();
            require(OLD.equals(SocialApi.cookie()), "missing candidate does not save");
            require(OfficialLoginPolicy.navigation("https://login.douyin.com/passport/"), "official HTTPS");
            for (String denied : new String[] {"http://www.douyin.com/", "https://www.douyin.com.evil.test/",
                    "https://evil@www.douyin.com/", "https://www.douyin.com:444/", "javascript:alert(1)",
                    "file:///data/data/private", "intent://login", "content://private"})
                require(!OfficialLoginPolicy.navigation(denied), "navigation policy");
            require(!OfficialLoginPolicy.candidate("msToken=only"), "token is not login");
            require(!OfficialLoginPolicy.candidate("sessionid=x\r\nInjected: value"), "header newline");
            for (String invalid : new String[] {"{}", "{\"status_code\":8,\"user\":{\"uid\":\"123\"}}",
                    "{\"status_code\":0,\"user\":{\"uid\":\"0\"}}",
                    "{\"status_code\":\"0\",\"user\":{\"uid\":\"123\"}}"}) {
                boolean rejected = false;
                try { OfficialLoginPolicy.verified(new JSONObject(invalid)); }
                catch (Exception expected) { rejected = true; }
                require(rejected, "independent profile response required");
            }
            OfficialLoginPolicy.verified(new JSONObject("{\"status_code\":0,\"user\":{\"uid\":\"123\"}}"));
            String agent = OfficialLoginPolicy.desktopAgent("Mozilla/5.0 (Linux; Android 5.0; wv) AppleWebKit/537.36 Version/4.0 Chrome/37.0.0.0 Mobile Safari/537.36");
            require(agent.contains("Chrome/37.0.0.0") && !agent.contains("Android") && !agent.contains("Mobile"),
                    "desktop mode retains installed engine version");
            WebSettings s = browser.getSettings();
            require(!s.getAllowFileAccess() && !s.getAllowContentAccess()
                    && !s.getAllowUniversalAccessFromFileURLs()
                    && s.getMixedContentMode() == WebSettings.MIXED_CONTENT_NEVER_ALLOW,
                    "browser security settings");
            browser.evaluateJavascript("window.clicked||0", value -> {
                try {
                    require("1".equals(value), "official login button clicked once");
                    browser.requestFocus();
                    browser.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER));
                    test.postDelayed(() -> {
                        if (browser == null) { failed(new Exception("browser closed during remote press")); return; }
                        browser.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER));
                        test.postDelayed(() -> verifyRemote(), 400);
                    }, 120);
                } catch (Exception failure) { failed(failure); }
            });
        } catch (Exception failure) { failed(failure); }
    }

    private void verifyRemote() {
        if (browser == null) { failed(new Exception("browser closed early")); return; }
        browser.evaluateJavascript("window.clicked||0", count -> {
            if (!"2".equals(count)) {
                Log.i(TAG, "REMOTE_DIAGNOSTIC clicks=" + count + " " + browser.pointerDiagnostics());
                browser.evaluateJavascript("(function(){var b=document.querySelector('button'),r=b.getBoundingClientRect();"
                        + "var e=document.elementFromPoint(innerWidth/2,innerHeight/2);"
                        + "return {button_rect:[r.left,r.top,r.width,r.height],viewport:[innerWidth,innerHeight],"
                        + "center_is_button:e===b};})()", geometry -> {
                    Log.i(TAG, "REMOTE_FIXTURE_GEOMETRY " + geometry);
                    failed(new Exception("remote pointer activates real webpage button"));
                });
                return;
            }
            try {
                require("2".equals(count), "remote pointer activates real webpage button");
                dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
                dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK));
                require(!browser.hasFocus(), "back returns to native controls");
                CookieManager cookies = CookieManager.getInstance();
                cookies.setCookie(OfficialLoginPolicy.HOME,
                        "scope_only=excluded; Secure; Path=/private", ignored ->
                    cookies.setCookie(OfficialLoginPolicy.HOME,
                            "sessionid=fixture-browser-candidate; Secure; HttpOnly; Path=/", done -> {
                        try {
                            String candidate = cookies.getCookie(OfficialLoginPolicy.SELF);
                            require(OfficialLoginPolicy.candidate(candidate), "native HttpOnly extraction");
                            require(!candidate.contains("scope_only"), "candidate uses account endpoint scope");
                            browser.evaluateJavascript("document.cookie.indexOf('sessionid=')<0", hidden -> {
                                try {
                                    require("true".equals(hidden), "HttpOnly never read through JavaScript");
                                    require(OLD.equals(SocialApi.cookie()), "unverified cookie does not save");
                                    completed = true;
                                    finish();
                                } catch (Exception failure) { failed(failure); }
                            });
                        } catch (Exception failure) { failed(failure); }
                    }));
            } catch (Exception failure) { failed(failure); }
        });
    }

    private void failed(Exception failure) {
        Log.e(TAG, "FAIL " + failure.getMessage());
        finish();
    }

    private void observeOfficialPage() {
        if (browser == null) {
            Log.i(TAG, "LIVE_PROBE_UNAVAILABLE official page did not remain open; no QR/login claim");
            screenshotSafe = true;
            screenshot();
            return;
        }
        Log.i(TAG, "LIVE_ENGINE " + browser.getSettings().getUserAgentString());
        browser.evaluateJavascript("(function(){var a=document.images,n=0;for(var i=0;i<a.length;i++){"
                + "var r=a[i].getBoundingClientRect();if(a[i].src.indexOf('data:image/')===0"
                + "&&r.width>=100&&r.width<=400&&Math.abs(r.width-r.height)<3)n++;}"
                + "var style=document.createElement('style');style.textContent='img,canvas,svg{visibility:hidden!important}';document.head.appendChild(style);"
                + "return {redacted:true,official:location.hostname==='www.douyin.com',qr_candidate_count:n,"
                + "body_text_present:document.body.innerText.length>40};})()", observation -> {
            try {
                JSONObject result = new JSONObject(observation);
                screenshotSafe = result.optBoolean("redacted", false);
                if (!screenshotSafe) throw new IllegalStateException("redaction not acknowledged");
                Log.i(TAG, "LIVE_PROBE_OBSERVATION official=" + result.optBoolean("official", false)
                        + " qr_candidate_count=" + result.optInt("qr_candidate_count", -1)
                        + " body_text_present=" + result.optBoolean("body_text_present", false));
            } catch (Exception failure) {
                Log.e(TAG, "LIVE_PROBE_SCREENSHOT_UNAVAILABLE " + failure.getClass().getName());
                return;
            }
            // All artwork is masked before a CI image is written; no QR token/cookies are exported.
            if (browser != null) { browser.stopLoading(); browser.getSettings().setJavaScriptEnabled(false); }
            test.postDelayed(() -> screenshot(), 600);
        });
    }

    private void screenshot() {
        try {
            // Only this test activity can expose its already-redacted surface. Production stays
            // FLAG_SECURE. adb captures Chromium's hardware surface instead of software View.draw.
            if (!probe || !screenshotSafe) throw new IllegalStateException("screenshot requires redacted probe");
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
            test.postDelayed(() -> {
                Log.i(TAG, "LIVE_SCREENSHOT_READY adb-redacted-surface");
                Log.i(TAG, "LIVE_PROBE_DONE anonymous page observation only; login not validated");
            }, 400);
        } catch (Exception failure) {
            Log.e(TAG, "LIVE_PROBE_SCREENSHOT_UNAVAILABLE " + failure.getClass().getName());
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        test.removeCallbacksAndMessages(null);
        if (!probe) test.postDelayed(() -> {
            try {
                require(!OfficialLoginPolicy.candidate(CookieManager.getInstance().getCookie(OfficialLoginPolicy.SELF)),
                        "cancelled browser candidate cleared");
                require(OLD.equals(SocialApi.cookie()), "cancelled browser preserves native account");
                fixtureCookie(original);
                if (completed) Log.i(TAG, "PASS API21_OFFICIAL_LOGIN_ISOLATION_HTTPONLY_REMOTE_LIFECYCLE");
            } catch (Exception failure) {
                Log.e(TAG, "FAIL lifecycle " + failure.getMessage());
                try { fixtureCookie(original); } catch (Exception ignored) { }
            }
        }, 800);
    }
}
