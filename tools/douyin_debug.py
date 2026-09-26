"""Local live API debugging only; automated tests run in GitHub Actions.

Uses agent-webview's public HTTP API and the Python standard library.
Never prints credential values, raw responses, query values or friend names.
"""

import argparse
import json
import os
from pathlib import Path
import re
import sys
import tempfile
import time
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlsplit
from urllib.request import HTTPRedirectHandler, ProxyHandler, Request, build_opener


ROOT = Path(__file__).resolve().parents[1]
PRIVATE = ROOT / ".local-debug"
ORIGIN = "https://www.douyin.com"
SELF = "/aweme/v1/web/user/profile/self/"
COMMON = {
    "device_platform": "webapp", "aid": "6383", "channel": "channel_pc_web",
    "version_code": "170400", "version_name": "17.4.0", "cookie_enabled": "true",
}


class DebugError(Exception):
    pass


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def private_write(path, value):
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    os.chmod(path.parent, 0o700)
    fd, temporary = tempfile.mkstemp(dir=path.parent, prefix=".pending-")
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as stream:
            json.dump(value, stream, ensure_ascii=False, indent=2)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def read_json(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        raise DebugError("无法读取本地运行文件；请先启动控制器和调试会话。") from None


def request_json(url, headers=None, body=None, method=None):
    data = None if body is None else json.dumps(body).encode()
    request = Request(url, data=data, headers=headers or {}, method=method)
    if data is not None:
        request.add_header("Content-Type", "application/json")
    try:
        # Do not send controller tokens through environment proxies or redirects.
        with build_opener(ProxyHandler({}), NoRedirect()).open(request, timeout=35) as response:
            raw = response.read(4 * 1024 * 1024 + 1)
            if len(raw) > 4 * 1024 * 1024:
                raise DebugError("接口响应超出 4 MiB 限制。")
            try:
                result = json.loads(raw)
            except (ValueError, UnicodeError):
                raise DebugError("接口未返回有效 JSON，可能处于验证中间页。") from None
            if not isinstance(result, dict):
                raise DebugError("接口响应不是 JSON 对象。")
            return result
    except HTTPError as error:
        raise DebugError(f"接口返回 HTTP {error.code}；未跟随重定向。") from None
    except (URLError, TimeoutError, OSError):
        raise DebugError("接口连接失败或超时；检查服务、DNS 和网络。") from None


class Webview:
    def __init__(self, runtime):
        config = read_json(runtime)
        base = config.get("base_url", "")
        parsed = urlsplit(base)
        if (parsed.scheme != "http" or parsed.hostname not in {"127.0.0.1", "localhost", "::1"}
                or parsed.username is not None or parsed.password is not None or parsed.query or parsed.fragment
                or parsed.path not in {"", "/"}):
            raise DebugError("控制器地址必须是本机 HTTP 地址。")
        self.base = base.rstrip("/")
        self.headers = {"Authorization": "Bearer " + config["token"]}

    def call(self, path, body=None, method=None):
        return request_json(self.base + "/v1" + path, self.headers, body, method)

    def session(self, path="", body=None, method=None):
        session_id = read_json(PRIVATE / "session.json").get("session_id", "")
        if not re.fullmatch(r"[a-f0-9]{32}", session_id):
            raise DebugError("本地会话标识无效。")
        return self.call("/sessions/" + session_id + path, body, method)

    def evaluate(self, code):
        return self.session("/javascript/evaluate", {"code": code, "timeout": 20})["value"]


def official_page(info):
    parsed = urlsplit(info.get("url", ""))
    if (parsed.scheme != "https" or parsed.hostname != "www.douyin.com" or parsed.port not in {None, 443}
            or parsed.username is not None or parsed.password is not None):
        raise DebugError("请先在 www.douyin.com 官方页面完成登录。")


def page_info(webview):
    return webview.evaluate("""(() => ({url: location.origin + location.pathname,
      title: document.title, user_agent: navigator.userAgent,
      challenge: /验证码|安全验证/.test(document.title),
      qr_images: [...document.images].filter(e => e.src.startsWith('data:image/') &&
        e.width >= 100 && e.width <= 400 && e.width === e.height &&
        e.getBoundingClientRect().width > 0).length}))()""")


def applicable_cookies(cookies, host="www.douyin.com", path=SELF):
    selected = []
    for cookie in cookies:
        domain = (cookie.get("domain") or "").lower()
        # A leading dot permits subdomains; a host-only cookie is exact-match.
        matches = (host == domain or (domain.startswith(".") and
                   (host == domain[1:] or host.endswith(domain))))
        cookie_path = cookie.get("path") or "/"
        if not matches or not (path == cookie_path or path.startswith(cookie_path.rstrip("/") + "/")):
            continue
        expires = cookie.get("expires")
        if expires:
            try:
                deadline = float(expires)
            except (TypeError, ValueError):
                try:
                    deadline = parsedate_to_datetime(str(expires)).timestamp()
                except (ValueError, TypeError, OverflowError):
                    try:
                        # WKWebView exposes NSDate as "2026-11-25 03:18:24 +0000".
                        deadline = datetime.strptime(str(expires), "%Y-%m-%d %H:%M:%S %z").timestamp()
                    except ValueError:
                        try:
                            parsed = datetime.fromisoformat(str(expires).replace("Z", "+00:00"))
                            if parsed.tzinfo is None:
                                continue
                            deadline = parsed.timestamp()
                        except ValueError:
                            continue
            if deadline <= time.time():
                continue
        name, value = cookie.get("name", ""), cookie.get("value", "")
        if (not re.fullmatch(r"[A-Za-z0-9_.-]+", name)
                or any(ord(c) < 33 or ord(c) > 126 or c == ";" for c in value)):
            continue
        selected.append(cookie)
    return sorted(selected, key=lambda c: -len(c.get("path") or "/"))


def cookie_header(cookies, host="www.douyin.com", path=SELF):
    return "; ".join(c["name"] + "=" + c["value"] for c in applicable_cookies(cookies, host, path))


def profile_identity(response):
    user = response.get("user")
    if (type(response.get("status_code")) is not int or response["status_code"] != 0
            or not isinstance(user, dict) or not re.fullmatch(r"[1-9][0-9]*", str(user.get("uid", "")))):
        raise DebugError("账号接口未确认有效登录；现有凭证未替换。")
    return str(user["uid"])


def direct_read(cookies, user_agent, path, values=None, host="www.douyin.com"):
    params = dict(COMMON)
    params.update(values or {})
    return request_json("https://" + host + path + "?" + urlencode(params), {
        "User-Agent": user_agent, "Referer": ORIGIN + "/", "Accept": "application/json",
        "Cookie": cookie_header(cookies, host, path),
    })


def capture(webview):
    info = page_info(webview)
    official_page(info)
    if info["challenge"]:
        raise DebugError("当前为官方验证中间页，尚不能导出有效凭证。")
    cookies = applicable_cookies(webview.session("/cookies")["cookies"])
    if not any(c["name"] in {"sessionid", "sessionid_ss"} and c["value"] for c in cookies):
        raise DebugError("尚未取得 sessionid；请扫描官方二维码并在手机确认。")
    # Verify the exported Cookie outside the browser, as the Android HTTP client does.
    uid = profile_identity(direct_read(cookies, info["user_agent"], SELF))
    private_write(PRIVATE / "credentials.json", {
        "captured_at": datetime.now(timezone.utc).isoformat(), "uid": uid,
        "user_agent": info["user_agent"], "cookies": cookies,
        "verification": "direct-profile-self",
    })
    return {"authenticated": True, "saved": ".local-debug/credentials.json",
            "cookie_names": sorted({c["name"] for c in cookies})}


def summarize_event(event):
    data = event.get("data") or {}
    parsed = urlsplit(str(data.get("url", "")))
    host = parsed.hostname or ""
    known_paths = {
        "/passport/web/get_qrcode/", "/passport/web/check_qrconnect/", SELF,
        "/aweme/v1/web/commit/item/digg/", "/aweme/v1/web/commit/follow/user/",
        "/aweme/v1/web/aweme/detail/", "/aweme/v1/web/familiar/list/",
        "/v2/conversation/create", "/v1/message/send",
    }
    if host not in {"www.douyin.com", "login.douyin.com", "imdesktop.douyin.com",
                    "imapi3-normal.zijieapi.com"} or parsed.path not in known_paths:
        return None
    # Deliberately omit query strings, headers, payloads and response bodies.
    status = data.get("status")
    return {"host": host, "path": parsed.path,
            "method": data.get("method") if data.get("method") in {"GET", "POST"} else None,
            "http_status": status if type(status) is int else None}


def start(webview, proxy=None):
    if (PRIVATE / "session.json").exists():
        raise DebugError("已有调试会话记录；先使用 status 或 close，避免丢失当前扫码会话。")
    body = {"url": ORIGIN + "/", "visible": True, "title": "抖音抬头版 · 扫码接口调试"}
    if proxy:
        body["proxy"] = proxy
    created = webview.call("/sessions", body)
    private_write(PRIVATE / "session.json", {"session_id": created["session_id"]})
    for _ in range(60):
        if (webview.session().get("window") or {}).get("ready"):
            webview.session("/instrumentation", {"network": True,
                "capture_request_bodies": False, "capture_response_bodies": False}, "PUT")
            return {"opened": True, "next": "在官方页面点击登录，再用手机扫码确认。"}
        time.sleep(0.5)
    raise DebugError("页面尚未就绪；会话已保留，可稍后运行 status。")


def probe(args):
    saved = read_json(PRIVATE / "credentials.json")
    cookies, ua = saved["cookies"], saved["user_agent"]
    if profile_identity(direct_read(cookies, ua, SELF)) != saved["uid"]:
        raise DebugError("账号已变化；请重新 capture。")
    if args.target == "self":
        return {"endpoint": SELF, "authenticated": True}
    if args.target == "video":
        if not args.id or not re.fullmatch(r"[0-9]+", args.id):
            raise DebugError("video 需要 --id 指定视频数字 ID。")
        response = direct_read(cookies, ua, "/aweme/v1/web/aweme/detail/", {"aweme_id": args.id})
        item = response.get("aweme_detail")
        if response.get("status_code") != 0 or not isinstance(item, dict):
            raise DebugError("视频接口未返回可确认的状态。")
        author = item.get("author") or {}
        return {"liked": item.get("user_digged"), "followed": author.get("follow_status")}
    response = direct_read(cookies, ua, "/aweme/v1/web/familiar/list/", {
        "aid": "339757", "cursor": "0", "count": "30", "need_all_friend": "1",
        "version_code": "21.6.0",
    }, host="imdesktop.douyin.com")
    if response.get("status_code") != 0 or not isinstance(response.get("user_list"), list):
        raise DebugError("好友列表接口未确认成功。")
    return {"friends_on_page": len(response["user_list"]), "message_sent": False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime", type=Path, default=PRIVATE / "controller.json")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("start").add_argument("--proxy", help="可选的 agent-webview HTTP 代理")
    for name in ("status", "login", "capture", "events", "close", "qr"):
        sub.add_parser(name)
    read = sub.add_parser("probe")
    read.add_argument("target", choices=["self", "video", "friends"])
    read.add_argument("--id")
    args = parser.parse_args()
    try:
        if args.command == "probe":
            result = probe(args)
        else:
            webview = Webview(args.runtime)
            if args.command == "qr":
                from passport_debug import qr_login
                result = qr_login(webview)
            elif args.command == "start":
                result = start(webview, args.proxy)
            elif args.command == "status":
                result = page_info(webview)
                result.pop("user_agent", None)
                # Titles may contain account names; expose only login/challenge state.
                result.pop("title", None)
                result["cookie_names"] = sorted({c["name"] for c in
                    applicable_cookies(webview.session("/cookies")["cookies"])})
            elif args.command == "login":
                official_page(page_info(webview))
                result = webview.evaluate("""(() => {
                  const button = [...document.querySelectorAll('button')].find(e => e.innerText.trim() === '登录');
                  if (!button) return {login_button_found: false};
                  button.click(); return {login_button_found: true};
                })()""")
            elif args.command == "capture":
                result = capture(webview)
            elif args.command == "events":
                events = webview.session("/events?after=0&limit=200&timeout=0")
                result = {"events": [summary for e in events.get("events", [])
                                      if (summary := summarize_event(e)) is not None],
                          "note": "仅页面探针可见的近期请求；HTTP 200 不代表业务成功。"}
            else:
                webview.session(method="DELETE")
                (PRIVATE / "session.json").unlink()
                result = {"closed": True}
        print(json.dumps(result, ensure_ascii=False, indent=2))
    except DebugError as error:
        parser.exit(1, str(error) + "\n")
    except (KeyError, TypeError, ValueError, OSError):
        parser.exit(1, "运行文件或接口结构异常；未输出原始数据，请检查本地服务。\n")


if __name__ == "__main__":
    # Keep Passport's imports and CLI exception handling on the same module instance.
    sys.modules["douyin_debug"] = sys.modules[__name__]
    main()
