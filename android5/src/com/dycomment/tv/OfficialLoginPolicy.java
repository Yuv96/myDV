package com.dycomment.tv;

import android.webkit.WebSettings;
import org.json.JSONObject;
import java.net.URL;

/** Account and navigation policy for the official browser and API21 fixtures. */
final class OfficialLoginPolicy {
    static final String HOME = "https://www.douyin.com/";
    static final String SELF_PATH = "/aweme/v1/web/user/profile/self/";
    static final String SELF = "https://www.douyin.com" + SELF_PATH;

    static boolean navigation(String address) {
        try { return address != null && QrSession.allowed(new URL(address)); }
        catch (Exception invalid) { return false; }
    }

    static boolean home(String address) {
        try {
            return navigation(address) && "www.douyin.com".equalsIgnoreCase(new URL(address).getHost());
        } catch (Exception invalid) { return false; }
    }

    static String desktopAgent(String actual) {
        // Desktop presentation, retaining the installed WebView engine's real version.
        return actual.replaceFirst("\\([^)]*\\)", "(X11; Linux x86_64)")
                .replace(" Version/4.0", "").replace(" Mobile", "");
    }

    static void configure(WebSettings s) {
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setSaveFormData(false);
        s.setUserAgentString(desktopAgent(s.getUserAgentString()));
    }

    static boolean candidate(String cookie) {
        return cookie != null && cookie.length() <= 65536
                && cookie.indexOf('\r') < 0 && cookie.indexOf('\n') < 0
                && CredentialStore.hasSession(cookie);
    }

    static void verified(JSONObject result) throws Exception {
        JSONObject user = result.optJSONObject("user");
        if (!(result.opt("status_code") instanceof Number)
                || ((Number) result.opt("status_code")).doubleValue() != 0d || user == null
                || !user.optString("uid").matches("[1-9][0-9]*"))
            throw new Exception("新账号未通过独立验证；现有账号已保留");
    }
}
