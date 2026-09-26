"""Exercise the Android QrSession protocol against official live endpoints."""

import base64
from datetime import datetime, timezone
from http.cookiejar import Cookie, CookieJar
import json
import re
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urljoin, urlsplit
from urllib.request import HTTPCookieProcessor, ProxyHandler, Request, build_opener

from douyin_debug import DebugError, NoRedirect, PRIVATE, direct_read, private_write, profile_identity, SELF


UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
      " Chrome/120.0.0.0 Safari/537.36")
LOGIN = "https://login.douyin.com"
QUERY = {
    "aid": "6383", "language": "zh", "passport_jssdk_version": "2.4.12",
    "passport_jssdk_type": "normal", "is_from_ttaccountsdk": "1",
    "is_new_login": "1", "next": "https://www.douyin.com", "need_logo": "false",
}


def allowed(url):
    parsed = urlsplit(url)
    host = parsed.hostname or ""
    return (parsed.scheme == "https" and parsed.port in {None, 443}
            and parsed.username is None and parsed.password is None
            and (host == "douyin.com" or host.endswith(".douyin.com")))


class Passport:
    def __init__(self):
        self.jar = CookieJar()
        self.opener = build_opener(ProxyHandler({}), NoRedirect(), HTTPCookieProcessor(self.jar))
        self.token = ""

    def value(self, name):
        # Only cookies actually eligible for login.douyin.com.
        from douyin_debug import applicable_cookies
        for cookie in applicable_cookies(self.cookies(), "login.douyin.com", "/passport/web/"):
            if cookie["name"] == name:
                return cookie["value"]
        return ""

    def request(self, url, body=None, redirects=0):
        if redirects > 5 or not allowed(url):
            raise DebugError("登录跳转地址无法验证，已停止。")
        data = None if body is None else urlencode(body).encode()
        headers = {"User-Agent": UA, "Referer": "https://www.douyin.com/"}
        if body is not None:
            headers["Content-Type"] = "application/x-www-form-urlencoded"
        if urlsplit(url).hostname == "login.douyin.com":
            csrf = self.value("passport_csrf_token") or self.value("passport_csrf_token_default")
            if csrf:
                headers["x-tt-passport-csrf-token"] = csrf
        try:
            response = self.opener.open(Request(url, data=data, headers=headers), timeout=20)
        except HTTPError as error:
            response = error
        except (URLError, TimeoutError, OSError):
            raise DebugError("Passport 连接失败或超时。") from None
        with response:
            if urlsplit(url).hostname == "login.douyin.com":
                ms = response.headers.get("x-ms-token", "")
                if re.fullmatch(r"[A-Za-z0-9_+=/.-]{1,4096}", ms):
                    self.jar.set_cookie(Cookie(0, "msToken", ms, None, False, ".douyin.com",
                        True, True, "/", True, True, None, True, None, None, {}, False))
            if 300 <= response.code < 400:
                location = response.headers.get("Location")
                if not location:
                    raise DebugError("登录跳转缺少地址。")
                return self.request(urljoin(url, location), redirects=redirects + 1)
            if response.code != 200:
                raise DebugError(f"Passport 返回 HTTP {response.code}。")
            raw = response.read(1024 * 1024 + 1)
            if len(raw) > 1024 * 1024:
                raise DebugError("Passport 响应过大。")
            return raw

    def passport(self, path, body=None):
        query = dict(QUERY)
        if body and "token" in body:
            query["token"] = body["token"]
        ms = self.value("msToken")
        if ms:
            query["msToken"] = ms
        try:
            result = json.loads(self.request(LOGIN + path + "?" + urlencode(query), body))
        except ValueError:
            raise DebugError("Passport 未返回 JSON。") from None
        data = result.get("data")
        if result.get("message") != "success" or not isinstance(data, dict):
            code = data.get("error_code") if isinstance(data, dict) else None
            code = code if type(code) is int else "unknown"
            raise DebugError(f"Passport 拒绝请求（{code}）；现有凭证未替换。")
        return data

    def create(self):
        data = self.passport("/passport/web/get_qrcode/")
        self.token = data.get("token", "")
        encoded = data.get("qrcode", "")
        if not self.token or not encoded or len(encoded) > 700000:
            raise DebugError("Passport 没有返回有效二维码。")
        if encoded.startswith("data:"):
            encoded = encoded.split(",", 1)[1]
        try:
            png = base64.b64decode(encoded, validate=True)
        except ValueError:
            raise DebugError("二维码编码无效。") from None
        if not png.startswith(b"\x89PNG\r\n\x1a\n"):
            raise DebugError("二维码不是 PNG 图片。")
        return "data:image/png;base64," + base64.b64encode(png).decode()

    def poll(self):
        return self.passport("/passport/web/check_qrconnect/", {
            "token": self.token, "need_logo": "false", "need_short_url": "false",
            "is_frontier": "true", "is_new_login": "1", "next": "https://www.douyin.com",
        })

    def cookies(self):
        return [{"name": c.name, "value": c.value, "domain": c.domain, "path": c.path,
                 "expires": c.expires, "secure": c.secure, "http_only": c.has_nonstandard_attr("HttpOnly")}
                for c in self.jar if not c.is_expired()]

    def finish(self, data):
        redirect = data.get("redirect_url")
        if redirect:
            self.request(redirect)
        cookies = self.cookies()
        from douyin_debug import applicable_cookies
        if not any(c["name"] in {"sessionid", "sessionid_ss"} and c["value"]
                   for c in applicable_cookies(cookies)):
            raise DebugError("手机已确认，但尚未取得有效会话 Cookie。")
        uid = profile_identity(direct_read(cookies, UA, SELF))
        private_write(PRIVATE / "credentials.json", {
            "captured_at": datetime.now(timezone.utc).isoformat(), "uid": uid,
            "user_agent": UA, "cookies": cookies, "verification": "passport-direct-profile-self",
        })
        return {"authenticated": True, "saved": ".local-debug/credentials.json",
                "cookie_names": sorted({c["name"] for c in cookies})}


def qr_login(webview):
    attempt = Passport()
    image = attempt.create()
    created = webview.call("/sessions", {"url": "about:blank", "visible": True,
        "width": 560, "height": 640, "title": "抖音抬头版 · 官方扫码登录"})
    path = "/sessions/" + created["session_id"]
    try:
        for _ in range(60):
            if (webview.call(path).get("window") or {}).get("ready"):
                break
            time.sleep(0.5)
        else:
            raise DebugError("二维码窗口未就绪。")
        webview.call(path + "/javascript/evaluate", {"code": """(() => {
          document.body.style.cssText='font-family:sans-serif;text-align:center;padding:24px;background:white;color:#222';
          const h=document.createElement('h2'); h.textContent='请用抖音 App 扫码并确认登录';
          const i=document.createElement('img'); i.src=""" + json.dumps(image) + """;
          i.style.cssText='width:320px;height:320px;image-rendering:pixelated';
          const p=document.createElement('p'); p.id='status'; p.textContent='等待扫码 · 三分钟内有效';
          document.body.replaceChildren(h,i,p); return true;
        })()""", "timeout": 15})
        print(json.dumps({"qr_ready": True, "source": "login.douyin.com", "expires_in_seconds": 180}), flush=True)
        deadline = time.monotonic() + 180
        previous = None
        while time.monotonic() < deadline:
            time.sleep(2)
            data = attempt.poll()
            state = str(data.get("status", ""))
            if state in {"confirmed", "3"}:
                return attempt.finish(data)
            if state not in {"new", "1", "scanned", "2"}:
                raise DebugError("二维码已失效、取消或需要官方进一步验证，请重新申请。")
            label = "已扫码，请在手机确认" if state in {"scanned", "2"} else "等待扫码"
            if label != previous:
                print(json.dumps({"state": label}, ensure_ascii=False), flush=True)
                webview.call(path + "/javascript/evaluate", {
                    "code": "document.getElementById('status').textContent=" + json.dumps(label), "timeout": 10})
                previous = label
        raise DebugError("二维码已过期；现有凭证未替换，请重新运行 qr。")
    finally:
        attempt.jar.clear()
        attempt.token = ""
        try:
            webview.call(path, method="DELETE")
        except DebugError:
            pass
