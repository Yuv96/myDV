package com.dycomment.tv;

import java.net.URL;
import java.util.Locale;

/** Compatibility URL policy; the visible official website now owns QR login. */
final class QrSession {
    static boolean allowed(URL url) {
        String host = url.getHost().toLowerCase(Locale.US);
        return "https".equals(url.getProtocol())
                && (url.getPort() == -1 || url.getPort() == 443)
                && url.getUserInfo() == null
                && (host.equals("douyin.com") || host.endsWith(".douyin.com"));
    }
}
