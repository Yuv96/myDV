package com.dycomment.tv;

import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.*;

/** A short-lived, isolated official Passport session. Never sends credentials to a relay service. */
final class QrSession implements AutoCloseable {
    private final CookieManager jar=new CookieManager(null,CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    private volatile HttpURLConnection active;
    private volatile boolean closed;
    String token="";
    private static final String ORIGIN="https://login.douyin.com";
    static boolean allowed(URL url) {
        String host=url.getHost().toLowerCase(Locale.US);
        return "https".equals(url.getProtocol()) && (url.getPort()==-1 || url.getPort()==443)
            && url.getUserInfo()==null && (host.equals("douyin.com") || host.endsWith(".douyin.com"));
    }
    private byte[] request(String address,Map<String,String> body,int redirects) throws Exception {
        if(closed) throw new IOException("登录已取消");
        URL url=new URL(address); if(!allowed(url) || redirects>5) throw new IOException("登录跳转地址无法验证");
        HttpURLConnection c=(HttpURLConnection)url.openConnection(); active=c;
        c.setConnectTimeout(8000); c.setReadTimeout(8000); c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent",SocialApi.UA); c.setRequestProperty("Referer","https://www.douyin.com/");
        for(Map.Entry<String,List<String>> e:jar.get(url.toURI(),Collections.<String,List<String>>emptyMap()).entrySet())
            for(String value:e.getValue()) c.addRequestProperty(e.getKey(),value);
        try {
            if(body!=null) {
                c.setRequestMethod("POST"); c.setDoOutput(true); c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
                byte[] bytes=SocialApi.encode(body).getBytes("UTF-8"); c.setFixedLengthStreamingMode(bytes.length);
                try(OutputStream out=c.getOutputStream()) { out.write(bytes); }
            }
            int status=c.getResponseCode(); jar.put(url.toURI(),c.getHeaderFields());
            if(status>=300 && status<400) {
                String location=c.getHeaderField("Location"); if(location==null) throw new IOException("登录跳转缺少地址");
                URL next=new URL(url,location); c.disconnect(); return request(next.toString(),null,redirects+1);
            }
            if(status!=200) throw new IOException("登录服务暂不可用（HTTP "+status+"）");
            try(InputStream in=c.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[8192]; int n; long deadline=android.os.SystemClock.elapsedRealtime()+15000;
                while((n=in.read(buffer))!=-1) {
                    if(closed) throw new IOException("登录已取消");
                    if(out.size()+n>1024*1024 || android.os.SystemClock.elapsedRealtime()>deadline) throw new IOException("登录响应超出限制");
                    out.write(buffer,0,n);
                }
                return out.toByteArray();
            }
        } finally { c.disconnect(); if(active==c) active=null; }
    }
    private JSONObject passport(String path,Map<String,String> body) throws Exception {
        Map<String,String> query=SocialApi.params("aid","6383","language","zh","passport_jssdk_version","2.4.12",
            "passport_jssdk_type","normal","is_from_ttaccountsdk","1","is_new_login","1","next","https://www.douyin.com","need_logo","false");
        JSONObject result=new JSONObject(new String(request(ORIGIN+path+"?"+SocialApi.encode(query),body,0),"UTF-8"));
        JSONObject data=result.optJSONObject("data");
        if(!"success".equals(result.optString("message")) || data==null) {
            // Verification challenges must be handled by Douyin, not bypassed or silently retried.
            int code=data==null ? -1 : data.optInt("error_code",-1);
            throw new IOException(code==4031 ? "抖音暂未允许此设备登录，请稍后重试；现有账号已保留"
                : "抖音未签发登录凭证（"+code+"），可能需要在抖音完成验证");
        }
        return data;
    }
    String create() throws Exception {
        JSONObject data=passport("/passport/web/get_qrcode/",null);
        token=data.optString("token"); String image=data.optString("qrcode");
        if(token.isEmpty() || image.isEmpty() || image.length()>700000) throw new IOException("登录服务没有返回二维码");
        return image;
    }
    JSONObject poll() throws Exception {
        return passport("/passport/web/check_qrconnect/",SocialApi.params("token",token,"need_logo","false",
            "need_short_url","false","is_frontier","true","is_new_login","1","next","https://www.douyin.com"));
    }
    String finish(JSONObject data) throws Exception {
        String redirect=data.optString("redirect_url");
        if(!redirect.isEmpty()) request(redirect,null,0);
        StringBuilder cookie=new StringBuilder();
        for(HttpCookie c:jar.getCookieStore().getCookies()) {
            String domain=c.getDomain();
            if(!c.hasExpired() && domain!=null && (domain.equals("douyin.com") || domain.equals(".douyin.com") || domain.equals("www.douyin.com"))) {
                if(cookie.length()>0) cookie.append("; "); cookie.append(c.getName()).append('=').append(c.getValue());
            }
        }
        String session=cookie.toString();
        if(!CredentialStore.hasSession(session)) throw new IOException("手机已确认，但未取得完整凭证；现有账号已保留");
        JSONObject self=SocialApi.request("/aweme/v1/web/user/profile/self/",SocialApi.params(),false,session).optJSONObject("user");
        if(self==null || self.optString("uid").isEmpty()) throw new IOException("新账号验证未通过；现有账号已保留");
        return session;
    }
    public void close() { closed=true; HttpURLConnection c=active; if(c!=null) c.disconnect(); jar.getCookieStore().removeAll(); token=""; }
}
