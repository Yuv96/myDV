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
