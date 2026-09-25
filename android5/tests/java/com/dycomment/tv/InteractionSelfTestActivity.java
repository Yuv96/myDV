package com.dycomment.tv;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.TextView;
import org.json.JSONObject;
import java.util.List;

/** Local fixtures only: no account, cookies, messages or network writes. */
public final class InteractionSelfTestActivity extends Activity {
    void require(boolean ok, String detail) { if (!ok) throw new IllegalStateException(detail); }
    @Override public void onCreate(Bundle b) {
        super.onCreate(b); TextView result = new TextView(this); setContentView(result);
        try {
            require(QrSession.allowed(new java.net.URL("https://login.douyin.com/path")),"official QR origin");
            require(!QrSession.allowed(new java.net.URL("https://douyin.com.evil.example/path")),"no credential relay");
            require(!QrSession.allowed(new java.net.URL("http://www.douyin.com/path")),"no plaintext login redirect");
            require(!QrSession.allowed(new java.net.URL("https://evil@www.douyin.com/path")),"no userinfo login redirect");
            require(CredentialStore.value("a=1; msToken=x=y; sessionid=s","msToken").equals("x=y"),"Cookie token preserves equals");
            require(!CredentialStore.hasSession("msToken=not-a-login"),"SDK token alone is not login");
            CredentialHealth.Window failures=new CredentialHealth.Window();
            require(!failures.record(1000) && !failures.record(1001) && failures.count==1,"do not count bursts as expiry");
            require(!failures.record(11000) && failures.record(21000),"repeated spaced failures prompt refresh");
            require(!failures.record(400000) && failures.count==1,"failure window resets");
            require(!new VideoProxyServer().isReady(),"no obsolete local playback proxy");
            int unread = SocialApi.parseUnread(new JSONObject("{\"notice_count\":[{\"group\":1,\"count\":2},{\"group\":2,\"count\":1},{\"group\":1,\"count\":2}]}"));
            require(unread == 3, "unread deduplication");
            require(SocialApi.parseUnread(new JSONObject("{\"notice_count\":[]}")) == 0, "zero unread");
            boolean rejected = false;
            try { SocialApi.parseLive(new JSONObject("{}")); } catch (Exception e) { rejected = true; }
            require(rejected, "invalid live response must not become an empty list");
            String room = "{\"id_str\":\"123\",\"owner\":{\"nickname\":\"测试作者\",\"follow_info\":{\"follow_status\":1}},\"stream_url\":{\"hls_pull_url_map\":{\"SD1\":\"https://example.com/sd.m3u8\",\"FULL_HD1\":\"https://example.com/hd.m3u8\"}}}";
            List<SocialApi.Live> lives = SocialApi.parseLive(new JSONObject("{\"data\":{\"data\":[{\"room\":" + room + ",\"is_recommend\":1},{\"room\":" + room + ",\"is_recommend\":0},{\"room\":" + room + ",\"is_recommend\":0}]}}"));
            require(lives.size() == 1 && lives.get(0).stream.endsWith("sd.m3u8"), "follow-only live and older-TV resolution");
            JSONObject compact = SocialApi.readLiveResponse(new java.io.ByteArrayInputStream(("{\"status_code\":0,\"discarded\":{\"large_metadata\":[]},\"data\":{\"data\":[{\"room\":" + room + "}]}}").getBytes("UTF-8")));
            require(!compact.has("discarded") && compact.optInt("status_code", -1) == 0 && SocialApi.parseLive(compact).size() == 1, "streamed live metadata filtering");
            JSONObject coverRoom=new JSONObject(room).put("cover",new JSONObject("{\"url_list\":[\"https://example.com/preview.jpg\"]}"));
            JSONObject covered=SocialApi.readLiveResponse(new java.io.ByteArrayInputStream(("{\"status_code\":0,\"data\":{\"data\":[{\"room\":"+coverRoom+"}]}}").getBytes("UTF-8")));
            require(SocialApi.parseLive(covered).get(0).preview.endsWith("preview.jpg"),"live preview preserved through streaming parser");
            QuickShareApi.Page friends=QuickShareApi.parseFriends(new JSONObject("{\"user_list\":[{\"uid\":\"12\",\"nickname\":\"测试好友\"},{\"uid\":\"12\"}],\"cursor\":30,\"has_more\":true}"));
            require(friends.friends.size()==1 && friends.more && friends.cursor.equals("30"),"friends paging and dedup");
            JSONObject card=QuickShareApi.videoCard(new JSONObject("{\"aweme_id\":\"123\",\"desc\":\"测试视频\",\"author\":{\"uid\":\"9\"},\"video\":{}}"));
            require(card.getInt("aweType")==800 && card.getString("itemId").equals("123") && !card.has("text"),"share is video card only");
            Wire parsed=new Wire(new Wire.Out().number(1,Long.MAX_VALUE).text(2,"你好").done());
            require(parsed.number(1,0)==Long.MAX_VALUE && parsed.text(2).equals("你好"),"protobuf preserves IDs and UTF8");
            rejected=false; try { new Wire(new byte[]{18,9,1}); } catch(Exception e) { rejected=true; }
            require(rejected,"truncated wire response rejected");
            byte[] chat=new Wire.Out().bytes(2,new Wire.Out().text(3,"测试观众").done()).text(3,"测试弹幕").done();
            byte[] event=new Wire.Out().text(1,"WebcastChatMessage").bytes(2,chat).number(3,123).done();
            require(LiveChatController.chats(new Wire(new Wire.Out().bytes(1,event).done())).get(0).text.equals("测试观众：测试弹幕"),"live chat wire decoding");
            final int[] selected = {-1}, cancelled = {0}, menu = {0};
            ModernMenuHelper.Panel panel = ModernMenuHelper.show(this, "互动测试", new String[]{"喜欢", "关注", "收藏", "主页", "分享"},
                true, true, i -> selected[0] = i, () -> cancelled[0]++, () -> menu[0]++);
            require(panel.rows.length == 5 && ModernMenuHelper.isMenuShowing(), "five large rows");
            panel.rows[2].performClick(); require(selected[0] == 2 && !panel.closed, "actions keep panel open");
            panel.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU));
            panel.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU));
            require(menu[0] == 1, "MENU callback exactly once");
            panel.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
            require(cancelled[0] == 1 && !ModernMenuHelper.isMenuShowing(), "BACK closes and clears showing state");
            ModernMenuHelper.showMenu(this, "纯文字菜单", new String[]{"画质设置"}, new String[]{"BAD_ICON"}, i -> {}, () -> {});
            require(ModernMenuHelper.isMenuShowing(), "legacy menu ABI"); ModernMenuHelper.dismissCurrentMenu(this);
            require(!ModernMenuHelper.isMenuShowing(), "legacy dismiss");
            result.setText("PASS Android 5.0 menu, key routing and social data parsing");
            Log.i("Android5InteractionTest", "PASS API21_MENU_KEYS_SOCIAL_PARSERS");
        } catch (Exception e) { result.setText("FAIL " + e.getMessage()); Log.e("Android5InteractionTest", "FAIL " + e.getMessage()); }
    }
}
