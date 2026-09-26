package com.dycomment.tv;

import org.json.*;

import java.io.*;
import java.net.*;
import java.util.*;

/** Video cards only. Creating a conversation and sending happen only after a friend is selected. */
final class QuickShareApi {
    static final class Friend {
        String uid, name, avatar;
    }

    static final class Page {
        final List<Friend> friends = new ArrayList<>();
        String cursor;
        boolean more;
    }

    static Page friends(String cookie, String cursor) throws Exception {
        JSONObject r =
                SocialApi.requestAt(
                        "https://imdesktop.douyin.com",
                        "/aweme/v1/web/familiar/list/",
                        SocialApi.params(
                                "aid",
                                "339757",
                                "cursor",
                                cursor,
                                "count",
                                "30",
                                "need_all_friend",
                                "1",
                                "version_code",
                                "21.6.0"),
                        false,
                        cookie);
        return parseFriends(r);
    }

    static Page parseFriends(JSONObject r) throws Exception {
        JSONArray users = r.optJSONArray("user_list");
        if (users == null) throw new Exception("好友列表暂不可用");
        Page p = new Page();
        p.cursor = r.optString("cursor", "0");
        p.more = r.optBoolean("has_more", r.optInt("has_more", 0) == 1);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < users.length(); i++) {
            JSONObject u = users.optJSONObject(i);
            if (u == null || u.optBoolean("user_canceled", false)) continue;
            Friend f = new Friend();
            f.uid = u.optString("uid", "");
            if (!f.uid.matches("[0-9]+") || !seen.add(f.uid)) continue;
            f.name = u.optString("remark_name", "");
            if (f.name.isEmpty()) f.name = u.optString("nickname", "好友");
            f.avatar = SocialApi.imageUrl(u.optJSONObject("avatar_thumb"));
            p.friends.add(f);
        }
        return p;
    }

    static Wire request(int cmd, int inbox, String path, byte[] body, String uid, String cookie)
            throws Exception {
        Wire.Out o =
                new Wire.Out()
                        .number(1, cmd)
                        .number(2, System.currentTimeMillis())
                        .text(3, "1.2.1")
                        .text(4, "")
                        .number(5, 3)
                        .number(6, inbox)
                        .text(7, "eb11b84dd0eb26ae22321b53426d3f976b920862")
                        .bytes(8, new Wire.Out().bytes(cmd, body).done())
                        .text(9, uid)
                        .text(11, "mac")
                        .text(14, "1.2.1")
                        .number(18, 1)
                        .text(21, "douyin_im_pc")
                        .text(22, "cpp_sdk");
        byte[] payload = o.done();
        String query =
                SocialApi.encode(
                        SocialApi.params(
                                "aid",
                                "339757",
                                "app_name",
                                "aweme_im_desktop",
                                "device_platform",
                                "mac",
                                "version_code",
                                "1.2.1",
                                "device_id",
                                uid,
                                "did",
                                uid,
                                "iid",
                                "0"));
        HttpURLConnection c =
                (HttpURLConnection)
                        new URL("https://imapi3-normal.zijieapi.com" + path + "?" + query)
                                .openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Cookie", cookie);
        c.setRequestProperty("Referer", "https://imdesktop.douyin.com");
        c.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like"
                    + " Gecko) douyinim/1.2.1 Chrome/130.0.6723.58 Electron/33.2.0 Safari/537.36");
        c.setRequestProperty("Content-Type", "application/x-protobuf");
        c.setRequestProperty("Accept", "x-protobuf");
        byte[] digest = java.security.MessageDigest.getInstance("MD5").digest(payload);
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) hex.append(String.format(java.util.Locale.US, "%02x", b & 255));
        c.setRequestProperty("x-ss-stub", hex.toString());
        c.setFixedLengthStreamingMode(payload.length);
        try {
            try (OutputStream out = c.getOutputStream()) {
                out.write(payload);
            }
            if (c.getResponseCode() != 200) throw new Exception("聊天接口暂不可用");
            Wire result;
            try (InputStream in = c.getInputStream()) {
                result = new Wire(read(in));
            }
            if (result.number(1, -1) != cmd || result.number(3, -1) != 0)
                throw new Exception("聊天请求未完成（状态 " + result.number(3, -1) + "）");
            Wire envelope = result.child(6);
            if (envelope.first(cmd) == null) throw new Exception("聊天接口未返回结果");
            return envelope.child(cmd);
        } finally {
            c.disconnect();
        }
    }

    static byte[] read(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        long end = android.os.SystemClock.elapsedRealtime() + 15000;
        while ((n = in.read(b)) != -1) {
            if (out.size() + n > 2 * 1024 * 1024 || android.os.SystemClock.elapsedRealtime() > end)
                throw new Exception("聊天响应超时或过大");
            out.write(b, 0, n);
        }
        return out.toByteArray();
    }

    static JSONObject videoCard(JSONObject video) throws Exception {
        String id = video.optString("aweme_id", "");
        if (!id.matches("[0-9]+")) throw new Exception("视频标识无效");
        JSONObject author = video.optJSONObject("author"), media = video.optJSONObject("video");
        if (author == null || media == null) throw new Exception("仅支持分享短视频");
        JSONObject card =
                new JSONObject()
                        .put("aweType", 800)
                        .put("awemeType", 0)
                        .put("itemId", id)
                        .put("content_title", video.optString("desc", ""))
                        .put("uid", author.optString("uid", ""))
                        .put("secUID", author.optString("sec_uid", ""))
                        .put("content_name", author.optString("nickname", ""));
        // Desktop video-card schema, checked against zhinjs/douyin-im (see docs).
        JSONObject cover = media.optJSONObject("cover");
        if (cover == null) cover = new JSONObject().put("uri", "").put("url_list", new JSONArray());
        card.put("cover_url", cover)
                .put("content_thumb", cover)
                .put("cover_height", 0)
                .put("cover_width", 0)
                .put("share_with_timestamp", 0)
                .put(
                        "share_id",
                        author.optString("uid", "") + "_" + System.currentTimeMillis() + "_" + id)
                .put("ai_ext", "{}")
                .put("share_info", new JSONArray())
                .put("anchor_info", new JSONObject())
                .put("poi_track_params", new JSONObject());
        return card;
    }

    static String share(String id, String friend, String cookie) throws Exception {
        JSONObject self =
                SocialApi.request(
                                "/aweme/v1/web/user/profile/self/",
                                SocialApi.params(),
                                false,
                                cookie)
                        .optJSONObject("user");
        String uid = self == null ? "" : self.optString("uid", "");
        if (!uid.matches("[0-9]+") || !friend.matches("[0-9]+")) throw new Exception("登录或好友信息无效");
        JSONObject video =
                SocialApi.request(
                                "/aweme/v1/web/aweme/detail/",
                                SocialApi.params("aweme_id", id),
                                false,
                                cookie)
                        .optJSONObject("aweme_detail");
        if (video == null) throw new Exception("无法读取当前视频");
        JSONObject card = videoCard(video);
        if (!cookie.equals(SocialApi.cookie())) throw new Exception("账号已切换，请重新选择好友");
        byte[] biz =
                new Wire.Out()
                        .text(1, "create")
                        .text(2, "{\"source_app_id\":339757,\"source_type\":6}")
                        .done();
        Wire created =
                request(
                        609,
                        0,
                        "/v2/conversation/create",
                        new Wire.Out()
                                .number(1, 1)
                                .number(2, Long.parseLong(uid))
                                .number(2, Long.parseLong(friend))
                                .bytes(11, biz)
                                .done(),
                        uid,
                        cookie);
        if (created.number(5, 0) != 0 || created.number(2, 0) != 0) throw new Exception("无法建立好友会话");
        Wire conv = created.child(1);
        String conversation = conv.text(1);
        long shortId = conv.number(2, 0);
        if (conversation.isEmpty() || shortId <= 0 || conv.number(3, 0) != 1)
            throw new Exception("好友会话未确认");
        if (!cookie.equals(SocialApi.cookie())) throw new Exception("账号已切换，分享已停止");
        String client = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        Wire.Out message =
                new Wire.Out()
                        .text(1, conversation)
                        .number(2, 1)
                        .number(3, shortId)
                        .text(4, card.toString())
                        .number(6, 8)
                        .text(7, conv.text(4))
                        .text(8, client);
        for (Map.Entry<String, String> e :
                SocialApi.params(
                                "s:mentioned_users",
                                "",
                                "s:client_message_id",
                                client,
                                "s:stime",
                                now + ".0000")
                        .entrySet())
            message.bytes(5, new Wire.Out().text(1, e.getKey()).text(2, e.getValue()).done());
        Wire sent =
                request(
                        100,
                        (int) conv.number(9, 0),
                        "/v1/message/send",
                        message.done(),
                        uid,
                        cookie);
        return shareResult(sent, client);
    }

    static String shareResult(Wire sent, String client) throws Exception {
        String echoed = sent.text(4);
        if (sent.number(3, -1) != 0
                || sent.number(1, 0) <= 0
                || (!echoed.isEmpty() && !echoed.equals(client)))
            throw new Exception("分享未确认；请先在抖音检查，避免重复发送");
        long check = sent.number(5, 0);
        // check_message can carry a newer decision than check_code.
        String message = sent.text(6);
        if (!message.isEmpty()) {
            try {
                long nested = new JSONObject(message).optLong("status_code", 0);
                if (nested > 0) check = nested;
            } catch (JSONException ignored) {
                // Unstructured server text must not change a confirmed numeric decision.
            }
        }
        if (check == 0 || check == 8101) return "视频已提交给好友";
        if (check == 10502) return "视频已提交，等待平台审核";
        if (check == 8610) throw new Exception("平台未通过内容检查，视频未送达");
        throw new Exception("平台返回分享状态 " + check + "；请先在抖音检查，避免重复发送");
    }
}
