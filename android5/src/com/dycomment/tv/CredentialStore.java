package com.dycomment.tv;

import android.content.Context;

/** Keeps compatibility with installed builds without embedding an account in the APK. */
final class CredentialStore {
    static String value(String cookie,String name) {
        if(cookie==null) return "";
        for(String part:cookie.split(";")) {
            int at=part.indexOf('=');
            if(at>0 && name.equals(part.substring(0,at).trim())) return part.substring(at+1).trim();
        }
        return "";
    }
    static boolean hasSession(String cookie) {
        return !value(cookie,"sessionid").isEmpty() || !value(cookie,"sessionid_ss").isEmpty();
    }
    static void save(Context context,String cookie) throws Exception {
        if(!hasSession(cookie)) throw new Exception("尚未取得登录凭证，请在手机上确认登录");
        // Commit the new account only after the caller has verified it; never clear a working one on failure.
        Class.forName("com.dycomment.tv.DouyinApi").getMethod("setCookie",String.class,Context.class).invoke(null,cookie,context);
        android.content.SharedPreferences.Editor edit=context.getSharedPreferences("dy_config",0).edit();
        edit.remove("a_bogus").remove("ms_token").remove("ms_token_time");
        String token=value(cookie,"msToken");
        if(!token.isEmpty()) edit.putString("ms_token",token).putLong("ms_token_time",System.currentTimeMillis());
        edit.apply(); CredentialHealth.reset();
    }
}
