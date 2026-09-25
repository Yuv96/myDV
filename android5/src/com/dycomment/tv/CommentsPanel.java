package com.dycomment.tv;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;

import org.json.*;

import java.util.*;
import java.util.concurrent.*;

/** Recycled comment rows with explicit paging and a bounded lifetime. */
final class CommentsPanel extends FrameLayout {
    private static final int MAX_COMMENTS = 200;
    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService work = Executors.newSingleThreadExecutor();
    private final List<String> rows = new ArrayList<>();
    private final String video, session;
    private final TextView status, more;
    private final BaseAdapter adapter;
    private int cursor;
    private boolean busy, closed, hasMore = true;

    CommentsPanel(Activity a, String id) {
        super(a);
        activity = a;
        video = id;
        session = SocialApi.cookie();
        setId(a.getResources().getIdentifier("android5_comments_panel", "id", a.getPackageName()));
        setBackgroundColor(0x80000000);
        setClickable(true);
        LinearLayout box = UiTheme.page(a, "评论");
        addView(
                box,
                new FrameLayout.LayoutParams(
                        Math.min(
                                ModernMenuHelper.dp(a, 420),
                                a.getResources().getDisplayMetrics().widthPixels * 3 / 4),
                        -1,
                        Gravity.RIGHT));
        status = UiTheme.text(a, "", 16);
        status.setTextColor(UiTheme.MUTED);
        box.addView(status);
        ListView list = new ListView(a);
        list.setDividerHeight(ModernMenuHelper.dp(a, 8));
        list.setSelector(ModernMenuHelper.background(true));
        adapter =
                new BaseAdapter() {
                    public int getCount() {
                        return rows.size();
                    }

                    public Object getItem(int p) {
                        return rows.get(p);
                    }

                    public long getItemId(int p) {
                        return p;
                    }

                    public View getView(int p, View old, ViewGroup parent) {
                        TextView row =
                                old instanceof TextView ? (TextView) old : UiTheme.text(a, "", 18);
                        row.setText(rows.get(p));
                        row.setPadding(12, 12, 12, 12);
                        return row;
                    }
                };
        list.setAdapter(adapter);
        box.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        more = UiTheme.button(a, "加载更多", () -> load());
        box.addView(more);
        box.addView(UiTheme.button(a, "重新扫码登录", () -> CredentialHealth.open(a)));
        box.addView(UiTheme.button(a, "返回", () -> close(true)));
        setOnClickListener(v -> close(true));
        box.setClickable(true);
        list.setFocusable(true);
        list.requestFocus();
        load();
    }

    private void load() {
        if (busy || closed || !hasMore) return;
        if (!SocialApi.personalCookie()) {
            status.setText("请先扫码登录");
            return;
        }
        busy = true;
        status.setText("读取评论中…");
        final int next = cursor;
        final String savedToken =
                activity.getSharedPreferences("dy_config", 0).getString("ms_token", "");
        final String savedSignature =
                activity.getSharedPreferences("dy_config", 0).getString("a_bogus", "");
        work.execute(
                () -> {
                    try {
                        Map<String, String> query =
                                SocialApi.params(
                                        "aweme_id",
                                        video,
                                        "cursor",
                                        String.valueOf(next),
                                        "count",
                                        "20");
                        String token = CredentialStore.value(session, "msToken");
                        if (token.isEmpty()) token = savedToken;
                        if (!token.isEmpty()) query.put("msToken", token);
                        // Preserve an installed user's working token until reauthentication; never
                        // bundle shared tokens.
                        if (!savedSignature.isEmpty()) query.put("a_bogus", savedSignature);
                        JSONObject data =
                                SocialApi.request(
                                        "/aweme/v1/web/comment/list/", query, false, session);
                        JSONArray comments = data.optJSONArray("comments");
                        if (comments == null) comments = new JSONArray();
                        List<String> page = new ArrayList<>();
                        for (int i = 0; i < Math.min(comments.length(), 20); i++) {
                            JSONObject c = comments.optJSONObject(i);
                            if (c == null) continue;
                            JSONObject user = c.optJSONObject("user");
                            String text = c.optString("text");
                            if (text.length() > 2000) text = text.substring(0, 2000);
                            page.add(
                                    (user == null ? "抖音用户" : user.optString("nickname", "抖音用户"))
                                            + "  ·  "
                                            + c.optLong("digg_count", 0)
                                            + " 喜欢\n"
                                            + text);
                        }
                        final int end = data.optInt("cursor", next + comments.length());
                        final boolean morePages =
                                (data.optBoolean("has_more", false)
                                                || data.optInt("has_more", 0) == 1)
                                        && end > next;
                        main.post(
                                () -> {
                                    if (!active()) return;
                                    busy = false;
                                    rows.addAll(page);
                                    cursor = end;
                                    hasMore = morePages && rows.size() < MAX_COMMENTS;
                                    adapter.notifyDataSetChanged();
                                    status.setText(
                                            rows.isEmpty()
                                                    ? "暂无评论"
                                                    : "已显示 " + rows.size() + " 条评论");
                                    more.setText(
                                            hasMore
                                                    ? "加载更多"
                                                    : rows.size() >= MAX_COMMENTS
                                                            ? "已达本页上限，返回可重新打开"
                                                            : "已显示全部评论");
                                    more.setEnabled(hasMore);
                                });
                    } catch (Exception e) {
                        main.post(
                                () -> {
                                    if (!active()) return;
                                    busy = false;
                                    status.setText(
                                            CredentialHealth.needsRefresh()
                                                    ? "评论权限可能失效，请重新扫码"
                                                    : "读取失败，可重试；持续失败时请重新扫码");
                                    more.setText("重试");
                                });
                    }
                });
    }

    private boolean active() {
        return !closed
                && !activity.isFinishing()
                && !activity.isDestroyed()
                && session.equals(SocialApi.cookie());
    }

    private void dispose() {
        if (closed) return;
        closed = true;
        work.shutdownNow();
        main.removeCallbacksAndMessages(null);
        rows.clear();
        try {
            if (InteractionController.field(activity, "commentOverlay") == this) {
                InteractionController.field(activity, "commentOverlay", null);
                InteractionController.field(activity, "menuShowing", false);
            }
        } catch (Exception ignored) {
        }
    }

    void close(boolean resume) {
        if (closed) return;
        dispose();
        if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
        if (resume) InteractionController.get(activity).close(true);
    }

    @Override
    protected void onDetachedFromWindow() {
        // ViewGroup is already removing this child. Reentrant removeView corrupts its traversal on
        // API21.
        dispose();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK || e.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) close(true);
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    static void show(Activity a) {
        try {
            List<?> feed = (List<?>) InteractionController.field(a, "feedList");
            int index = (Integer) InteractionController.field(a, "currentIndex");
            if (index < 0 || index >= feed.size()) return;
            InteractionController controller = InteractionController.get(a);
            controller.invoke("pauseForMenu");
            controller.showing(true);
            InteractionController.call(a, "stopDanmakuScheduler", new Class<?>[0]);
            CommentsPanel panel =
                    new CommentsPanel(a, InteractionController.text(feed.get(index), "awemeId"));
            InteractionController.field(a, "commentOverlay", panel);
            ((ViewGroup) a.getWindow().getDecorView())
                    .addView(panel, new ViewGroup.LayoutParams(-1, -1));
        } catch (Exception e) {
            Toast.makeText(a, "评论暂不可用", Toast.LENGTH_SHORT).show();
        }
    }
}
