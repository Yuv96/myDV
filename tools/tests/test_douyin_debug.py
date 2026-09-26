"""Offline fixtures only. Run through GitHub Actions, never with real credentials."""

import io
import json
import os
from pathlib import Path
import stat
import sys
import tempfile
import unittest
from contextlib import ExitStack
from datetime import datetime, timezone
from unittest.mock import Mock, patch
from urllib.parse import parse_qs, urlsplit

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import douyin_debug as debug
import passport_debug as passport


NOW = datetime(2026, 9, 26, tzinfo=timezone.utc).timestamp()


def cookie(name="sessionid", **changes):
    value = {"name": name, "value": "synthetic-fixture", "domain": ".douyin.com",
             "path": "/", "secure": True, "http_only": True}
    value.update(changes)
    return value


class OfflineTest(unittest.TestCase):
    def setUp(self):
        self.contexts = ExitStack()
        self.addCleanup(self.contexts.close)
        # Any accidentally unmocked request fails before connecting.
        for target in ("socket.socket.connect", "socket.create_connection"):
            self.enterContext(patch(target, side_effect=AssertionError("network forbidden")))
        self.enterContext(patch.object(debug.time, "time", return_value=NOW))

    def enterContext(self, context):
        # unittest.TestCase.enterContext was only added in Python 3.11.
        return self.contexts.enter_context(context)


class CookieTests(OfflineTest):
    def test_domain_path_boundaries_and_longest_path_first(self):
        values = [cookie("root"), cookie("host", domain="www.douyin.com"),
                  cookie("specific", path="/aweme/v1/web/user"),
                  cookie("wrong_host", domain="login.douyin.com"),
                  cookie("host_only_root", domain="douyin.com"),
                  cookie("suffix_attack", domain=".evil-douyin.com"),
                  cookie("path_prefix", path="/aweme/v1/web/users"),
                  cookie("path_suffix", path=debug.SELF + "extra")]
        selected = debug.applicable_cookies(values)
        self.assertEqual([c["name"] for c in selected], ["specific", "root", "host"])
        self.assertTrue(all(c["http_only"] for c in selected))
        self.assertEqual(debug.applicable_cookies([cookie()], "douyin.com.evil.example"), [])
        self.assertEqual(debug.applicable_cookies([cookie(path="/exact")], path="/exact"),
                         [cookie(path="/exact")])
        self.assertEqual(debug.applicable_cookies([cookie(path="/exact")], path="/exactly"), [])

    def test_expiry_formats_include_wkwebview_nsdate(self):
        future = NOW + 86400
        valid = [None, future, str(future), "2026-11-25 03:18:24 +0000",
                 "Wed, 25 Nov 2026 03:18:24 GMT", "2026-11-25T03:18:24Z"]
        for expires in valid:
            with self.subTest(expires=expires):
                self.assertEqual(len(debug.applicable_cookies([cookie(expires=expires)])), 1)
        for expires in [NOW, NOW - 1, "2026-01-01 03:18:24 +0000",
                        "2026-11-25T03:18:24", "not a date"]:
            with self.subTest(expires=expires):
                self.assertEqual(debug.applicable_cookies([cookie(expires=expires)]), [])

    def test_cookie_header_excludes_injection_and_keeps_httponly(self):
        values = [cookie(), cookie("bad name"), cookie("bad", value="a; extra=b"),
                  cookie("newline", value="a\r\nInjected:yes")]
        self.assertEqual(debug.cookie_header(values), "sessionid=synthetic-fixture")


class IdentityTests(OfflineTest):
    def test_requires_integer_zero_status_and_positive_numeric_uid(self):
        for uid in ["123", 123]:
            self.assertEqual(debug.profile_identity({"status_code": 0, "user": {"uid": uid}}),
                             "123")
        for status in [None, False, True, "0", 0.0, 1]:
            with self.subTest(status=status):
                with self.assertRaises(debug.DebugError):
                    debug.profile_identity({"status_code": status, "user": {"uid": "123"}})
        for user in [None, [], {}, {"uid": "0"}, {"uid": -1}, {"uid": True},
                     {"uid": "123.0"}, {"uid": " 123"}, {"uid": "123abc"}]:
            with self.subTest(user=user):
                with self.assertRaises(debug.DebugError):
                    debug.profile_identity({"status_code": 0, "user": user})


class CaptureTests(OfflineTest):
    def setUp(self):
        super().setUp()
        self.temporary = self.enterContext(tempfile.TemporaryDirectory())
        self.private = Path(self.temporary) / "private"
        self.private.mkdir(mode=0o700)
        self.saved = self.private / "credentials.json"
        self.saved.write_bytes(b'{"previous":"synthetic-old-account"}')
        self.saved.chmod(0o600)
        self.enterContext(patch.object(debug, "PRIVATE", self.private))
        self.enterContext(patch.object(passport, "PRIVATE", self.private))
        self.webview = Mock()
        self.webview.evaluate.return_value = {
            "url": debug.ORIGIN + "/", "challenge": False, "user_agent": "fixture-agent"}
        self.webview.session.return_value = {"cookies": [cookie()]}

    def test_unverified_capture_does_not_replace_existing_credentials(self):
        previous = self.saved.read_bytes()
        failures = [{"status_code": 1, "user": {"uid": "123"}},
                    {"status_code": False, "user": {"uid": "123"}},
                    {"status_code": 0, "user": {}}, debug.DebugError("network unavailable")]
        for response in failures:
            with self.subTest(response=type(response).__name__):
                with patch.object(debug, "direct_read") as read, \
                        patch.object(debug, "private_write") as write:
                    if isinstance(response, Exception):
                        read.side_effect = response
                    else:
                        read.return_value = response
                    with self.assertRaises(debug.DebugError):
                        debug.capture(self.webview)
                    write.assert_not_called()
                self.assertEqual(self.saved.read_bytes(), previous)

    def test_challenge_or_missing_session_never_attempts_verification(self):
        previous = self.saved.read_bytes()
        with patch.object(debug, "direct_read") as read:
            self.webview.evaluate.return_value["challenge"] = True
            with self.assertRaises(debug.DebugError):
                debug.capture(self.webview)
            self.webview.evaluate.return_value["challenge"] = False
            self.webview.session.return_value = {"cookies": [cookie("msToken")]}
            with self.assertRaises(debug.DebugError):
                debug.capture(self.webview)
            read.assert_not_called()
        self.assertEqual(self.saved.read_bytes(), previous)

    def test_success_is_atomic_private_and_preserves_httponly_cookie(self):
        previous = self.saved.read_bytes()
        replace = os.replace

        def inspect_replace(source, destination):
            source, destination = Path(source), Path(destination)
            self.assertEqual(destination, self.saved)
            self.assertEqual(source.parent, self.private)
            self.assertEqual(destination.read_bytes(), previous)
            self.assertEqual(stat.S_IMODE(source.stat().st_mode), 0o600)
            self.assertEqual(json.loads(source.read_text())["uid"], "123")
            replace(source, destination)

        with patch.object(debug, "direct_read", return_value={
                "status_code": 0, "user": {"uid": "123"}}) as read, \
                patch.object(debug.os, "replace", side_effect=inspect_replace) as atomic:
            result = debug.capture(self.webview)
        atomic.assert_called_once()
        read.assert_called_once_with([cookie()], "fixture-agent", debug.SELF)
        saved = json.loads(self.saved.read_text())
        self.assertEqual(saved["cookies"], [cookie()])
        self.assertEqual(saved["verification"], "direct-profile-self")
        self.assertEqual(stat.S_IMODE(self.saved.stat().st_mode), 0o600)
        self.assertEqual(stat.S_IMODE(self.private.stat().st_mode), 0o700)
        self.assertEqual(self.private.stat().st_uid, os.getuid())
        self.assertEqual(list(self.private.glob(".pending-*")), [])
        self.assertTrue(result["authenticated"])
        self.assertNotIn("synthetic-fixture", json.dumps(result))

    def test_failed_atomic_replace_keeps_old_credentials_and_cleans_temporary(self):
        previous = self.saved.read_bytes()
        with patch.object(debug.os, "replace", side_effect=OSError("fixture disk failure")):
            with self.assertRaises(OSError):
                debug.private_write(self.saved, {"uid": "123"})
        self.assertEqual(self.saved.read_bytes(), previous)
        self.assertEqual(list(self.private.glob(".pending-*")), [])

    def test_passport_finish_requires_verified_profile_before_saving(self):
        previous = self.saved.read_bytes()
        attempt = passport.Passport()
        with patch.object(attempt, "cookies", return_value=[cookie()]), \
                patch.object(passport, "direct_read", return_value={"status_code": 0, "user": {}}), \
                patch.object(passport, "private_write") as write:
            with self.assertRaises(debug.DebugError):
                attempt.finish({})
            write.assert_not_called()
        self.assertEqual(self.saved.read_bytes(), previous)


class BoundaryTests(OfflineTest):
    def test_controller_rejects_remote_targets_and_url_credentials(self):
        urls = ["http://example.com", "https://localhost", "http://127.0.0.1.evil.example",
                "http://user:secret@localhost", "http://@localhost", "http://localhost/path",
                "http://localhost?token=secret", "http://localhost/#secret"]
        for url in urls:
            with self.subTest(url=url), patch.object(debug, "read_json", return_value={
                    "base_url": url, "token": "synthetic-controller"}):
                with self.assertRaises(debug.DebugError):
                    debug.Webview(Path("unused-runtime.json"))
        for url in ["http://127.0.0.1:1234", "http://localhost:1234/", "http://[::1]:1234"]:
            with self.subTest(url=url), patch.object(debug, "read_json", return_value={
                    "base_url": url, "token": "synthetic-controller"}):
                self.assertEqual(debug.Webview(Path("unused-runtime.json")).base, url.rstrip("/"))

    def test_official_page_rejects_foreign_origins_and_userinfo(self):
        for url in ["http://www.douyin.com/", "https://www.douyin.com.evil.example/",
                    "https://user@www.douyin.com/", "https://@www.douyin.com/"]:
            with self.subTest(url=url), self.assertRaises(debug.DebugError):
                debug.official_page({"url": url})

    def test_event_summary_excludes_query_headers_bodies_and_unknown_paths(self):
        for field in ["payload", "data"]:
            with self.subTest(schema=field):
                payload = {
                    "url": "https://login.douyin.com/passport/web/check_qrconnect/?token=query-secret",
                    "method": "POST", "status": 200, "headers": {"Cookie": "header-secret"},
                    "request_body": "request-secret", "response_body": "response-secret"}
                event = {"sequence": 42, "timestamp": "2026-09-26T00:00:00Z",
                         "kind": "network.response", field: payload}
                self.assertEqual(debug.summarize_event(event), {
                    "host": "login.douyin.com", "path": "/passport/web/check_qrconnect/",
                    "method": "POST", "http_status": 200})
                payload.update(method="method-secret", status="status-secret")
                summary = debug.summarize_event(event)
                self.assertIsNone(summary["method"])
                self.assertIsNone(summary["http_status"])
                self.assertNotIn("secret", json.dumps(summary))
                for url in ["https://evil.example/passport/web/check_qrconnect/",
                            "https://login.douyin.com/private-account-path"]:
                    payload["url"] = url
                    self.assertIsNone(debug.summarize_event(event))

    def test_event_public_payload_takes_precedence_over_legacy_data(self):
        event = {"sequence": 43, "timestamp": "2026-09-26T00:00:01Z",
                 "kind": "network.response",
                 "payload": {"url": "https://www.douyin.com" + debug.SELF,
                             "method": "GET", "status": 200},
                 "data": {"url": "https://evil.example/legacy-secret"}}
        self.assertEqual(debug.summarize_event(event), {
            "host": "www.douyin.com", "path": debug.SELF,
            "method": "GET", "http_status": 200})

    def test_passport_redirect_allowlist(self):
        for url in ["https://douyin.com/", "https://login.douyin.com/path",
                    "https://www.douyin.com:443/path"]:
            self.assertTrue(passport.allowed(url), url)
        for url in ["http://login.douyin.com/", "https://douyin.com.evil.example/",
                    "https://evil-douyin.com/", "https://www.douyin.com:444/",
                    "https://user@douyin.com/", "https://@douyin.com/",
                    "https://user:secret@douyin.com/"]:
            self.assertFalse(passport.allowed(url), url)

    def test_disallowed_redirect_never_reaches_second_request(self):
        attempt = passport.Passport()
        response = Mock(code=302, headers={"Location": "https://evil.example/steal"})
        response.__enter__ = Mock(return_value=response)
        response.__exit__ = Mock(return_value=False)
        attempt.opener = Mock()
        attempt.opener.open.return_value = response
        with self.assertRaises(debug.DebugError):
            attempt.request(passport.LOGIN + "/passport/web/check_qrconnect/", {"token": "fixture"})
        attempt.opener.open.assert_called_once()


class PassportProtocolTests(OfflineTest):
    def test_poll_uses_post_and_same_token_in_query_and_form(self):
        attempt = passport.Passport()
        attempt.token = "synthetic-qr-token"
        response = io.BytesIO(b'{"message":"success","data":{"status":"new"}}')
        response.code = 200
        response.headers = {"x-ms-token": "synthetic-rotated-token"}
        attempt.opener = Mock()
        attempt.opener.open.return_value = response
        self.assertEqual(attempt.poll(), {"status": "new"})
        request = attempt.opener.open.call_args.args[0]
        self.assertEqual(request.get_method(), "POST")
        self.assertEqual(parse_qs(urlsplit(request.full_url).query)["token"], [attempt.token])
        self.assertEqual(parse_qs(request.data.decode())["token"], [attempt.token])
        self.assertEqual(attempt.value("msToken"), "synthetic-rotated-token")

    def test_http_200_does_not_turn_business_rejection_into_success(self):
        attempt = passport.Passport()
        with patch.object(attempt, "request", return_value=(
                b'{"message":"error","data":{"error_code":4031}}')):
            with self.assertRaises(debug.DebugError):
                attempt.poll()


if __name__ == "__main__":
    unittest.main()
