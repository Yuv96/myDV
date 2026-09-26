package com.dycomment.tv;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;

import org.json.*;

import java.util.*;
import java.util.concurrent.*;

/** Read-only, recycled comments. The retained window does not limit server pagination. */
final class CommentsPanel extends FrameLayout {
    private static final int MAX_COMMENTS = 200;
    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService work = Executors.newSingleThreadExecutor();
    private final PreviewImages images = new PreviewImages();
    private final List<Comment> rows = new ArrayList<>();
    private final PageSource source;
    private final TextView status;
    private final ListView list;
    private final BaseAdapter adapter;
    private int cursor;
    private boolean busy, closed, retryBlocked, restoring, hasMore = true;

    interface PageSource {
        JSONObject load(int cursor) throws Exception;

        boolean ready();

        boolean current();
    }

    private static PageSource remote(Activity a, String video) {
        final String session = SocialApi.cookie();
        final String savedToken = a.getSharedPreferences("dy_config", 0).getString("ms_token", "");
        final String signature = a.getSharedPreferences("dy_config", 0).getString("a_bogus", "");
        return new PageSource() {
            public boolean ready() {
                return SocialApi.personalCookie() && CredentialStore.hasSession(session);
            }

            public boolean current() {
                return session.equals(SocialApi.cookie());
            }

            public JSONObject load(int cursor) throws Exception {
                Map<String, String> query = SocialApi.params(
                        "aweme_id", video, "cursor", String.valueOf(cursor), "count", "20");
                String token = CredentialStore.value(session, "msToken");
                if (token.isEmpty()) token = savedToken;
                if (!token.isEmpty()) query.put("msToken", token);
                if (!signature.isEmpty()) query.put("a_bogus", signature);
                return SocialApi.request("/aweme/v1/web/comment/list/", query, false, session);
            }
        };
    }

    CommentsPanel(Activity a, String id) {
        this(a, remote(a, id));
    }

    CommentsPanel(Activity a, PageSource pages) {
        super(a);
        activity = a;
        source = pages;
        setId(a.getResources().getIdentifier("android5_comments_panel", "id", a.getPackageName()));
        setBackgroundColor(0x80000000);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        LinearLayout box = UiTheme.page(a, "评论");
        addView(box, new FrameLayout.LayoutParams(
                Math.min(ModernMenuHelper.dp(a, 420),
                        a.getResources().getDisplayMetrics().widthPixels * 3 / 4),
                -1, Gravity.RIGHT));
        status = UiTheme.text(a, "", 14);
        status.setTextColor(UiTheme.MUTED);
        status.setVisibility(GONE);
        box.addView(status);
        list = new ReadingList(a);
        list.setDivider(new ColorDrawable(0x18ffffff));
        list.setDividerHeight(ModernMenuHelper.dp(a, 1));
        list.setSelector(new ColorDrawable(0x00000000));
        list.setCacheColorHint(0x00000000);
        list.setFocusable(false);
        list.setFocusableInTouchMode(false);
        list.setItemsCanFocus(false);
        list.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        adapter = new BaseAdapter() {
            public int getCount() { return rows.size(); }

            public Object getItem(int p) { return rows.get(p); }

            public long getItemId(int p) { return p; }

            public boolean areAllItemsEnabled() { return false; }

            public boolean isEnabled(int p) { return false; }

            public View getView(int p, View old, ViewGroup parent) {
                CommentRow row = old instanceof CommentRow ? (CommentRow) old : new CommentRow(a);
                Comment comment = rows.get(p);
                row.meta.setText(comment.nickname + "  ·  " + comment.likes + " 赞");
                row.body.setText(comment.text);
                images.bind(row.avatar, comment.avatar);
                return row;
            }
        };
        list.setAdapter(adapter);
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScrollStateChanged(AbsListView view, int state) {
                if (state == SCROLL_STATE_TOUCH_SCROLL) retryBlocked = false;
            }

            public void onScroll(AbsListView view, int first, int visible, int total) {
                loadAtBottom();
            }
        });
        list.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) retryBlocked = false;
            if (event.getActionMasked() == MotionEvent.ACTION_UP) main.post(() -> loadAtBottom());
            return false;
        });
        box.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        setOnClickListener(v -> close(true));
        box.setClickable(true);
        requestFocus();
        load();
    }

    /** Disabled rows must still support viewport anchoring after an eviction on a remote-only TV. */
    private static final class ReadingList extends ListView {
        ReadingList(Activity a) { super(a); }

        @Override
        public boolean isInTouchMode() {
            // ListView otherwise refuses setSelectionFromTop when every row is disabled.
            // Touch-mode positioning changes the viewport without creating a selected item.
            return true;
        }
    }

    private static final class Comment {
        final String nickname, text, avatar;
        final long likes;

        Comment(JSONObject comment) {
            JSONObject user = comment.optJSONObject("user");
            nickname = clipped(user == null ? "抖音用户" : user.optString("nickname", "抖音用户"), 100);
            text = clipped(comment.optString("text"), 2000);
            likes = comment.optLong("digg_count", 0);
            avatar = user == null ? "" : SocialApi.imageUrl(user.optJSONObject("avatar_thumb"));
        }
    }

    private static String clipped(String text, int limit) {
        return text.length() > limit ? text.substring(0, limit) : text;
    }

    private static final class CommentRow extends LinearLayout {
        final TextView meta, body;
        final ImageView avatar;

        CommentRow(Activity a) {
            super(a);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(0, ModernMenuHelper.dp(a, 14), 0, ModernMenuHelper.dp(a, 14));
            setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            setFocusable(false);
            setClickable(false);
            setLongClickable(false);
            LinearLayout words = new LinearLayout(a);
            words.setOrientation(VERTICAL);
            meta = UiTheme.text(a, "", 10);
            meta.setTextColor(UiTheme.MUTED);
            body = UiTheme.text(a, "", 20);
            body.setPadding(0, ModernMenuHelper.dp(a, 5), 0, 0);
            words.addView(meta);
            words.addView(body);
            addView(words, new LinearLayout.LayoutParams(0, -2, 1));
            avatar = new CircularAvatar(a);
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avatar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams picture = new LinearLayout.LayoutParams(
                    ModernMenuHelper.dp(a, 40), ModernMenuHelper.dp(a, 40));
            picture.leftMargin = ModernMenuHelper.dp(a, 14);
            addView(avatar, picture);
        }
    }

    /** Mask the whole view, including the placeholder background installed by PreviewImages. */
    private static final class CircularAvatar extends ImageView {
        private final Path corners = new Path();
        private final Paint erase = new Paint(Paint.ANTI_ALIAS_FLAG);

        CircularAvatar(Activity a) {
            super(a);
            erase.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            corners.reset();
            corners.setFillType(Path.FillType.EVEN_ODD);
            corners.addRect(0, 0, w, h, Path.Direction.CW);
            corners.addCircle(w / 2f, h / 2f, Math.min(w, h) / 2f, Path.Direction.CW);
        }

        @Override
        public void draw(Canvas canvas) {
            // An isolated layer keeps the mask from erasing the comment row beneath it.
            // Unlike outline clipping, this also works when API21 screenshots use a software Canvas.
            int layer = canvas.saveLayer(0, 0, getWidth(), getHeight(), null, Canvas.ALL_SAVE_FLAG);
            super.draw(canvas);
            canvas.drawPath(corners, erase);
            canvas.restoreToCount(layer);
        }
    }

    private void loadAtBottom() {
        if (closed || restoring || retryBlocked || list.getHeight() == 0) return;
        if (rows.isEmpty() || !list.canScrollVertically(1)) load();
    }

    private void status(String text) {
        status.setText(text);
        status.setVisibility(text.isEmpty() ? GONE : VISIBLE);
    }

    private void load() {
        if (busy || !hasMore || !active()) return;
        if (!source.ready()) {
            // Account state lives in the time capsule, not in the reading area.
            status("");
            retryBlocked = true;
            return;
        }
        busy = true;
        if (rows.isEmpty()) status("读取评论中…");
        final int next = cursor;
        work.execute(() -> {
            try {
                JSONObject data = source.load(next);
                JSONArray comments = data.optJSONArray("comments");
                if (comments == null) comments = new JSONArray();
                List<Comment> page = new ArrayList<>();
                for (int i = 0; i < Math.min(comments.length(), 20); i++) {
                    JSONObject comment = comments.optJSONObject(i);
                    if (comment != null) page.add(new Comment(comment));
                }
                final int end = data.optInt("cursor", next + comments.length());
                final boolean morePages = (data.optBoolean("has_more", false)
                        || data.optInt("has_more", 0) == 1) && end > next;
                main.post(() -> {
                    if (!active()) return;
                    // Keep the current first visible row at exactly the same vertical offset.
                    int first = list.getFirstVisiblePosition();
                    View top = list.getChildAt(0);
                    int offset = top == null ? 0 : top.getTop() - list.getPaddingTop();
                    rows.addAll(page);
                    int removed = Math.max(0, rows.size() - MAX_COMMENTS);
                    if (removed > 0) rows.subList(0, removed).clear();
                    cursor = end;
                    hasMore = morePages;
                    restoring = true;
                    adapter.notifyDataSetChanged();
                    if (removed > 0) list.setSelectionFromTop(Math.max(0, first - removed), offset);
                    status(rows.isEmpty() ? (hasMore ? "读取评论中…" : "暂无评论") : "");
                    // Let ListView lay out the appended rows before deciding if it is still at bottom.
                    list.postOnAnimation(() -> {
                        if (!active()) return;
                        restoring = false;
                        busy = false;
                        loadAtBottom();
                    });
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (!active()) return;
                    busy = false;
                    retryBlocked = true;
                    status(CredentialHealth.needsRefresh() ? "" : "评论暂时无法读取，向下滚动重试");
                });
            }
        });
    }

    private boolean active() {
        if (closed) return false;
        if (activity.isFinishing() || activity.isDestroyed() || !source.current()) {
            close(true);
            return false;
        }
        return true;
    }

    private final Runnable sessionWatch = new Runnable() {
        public void run() {
            if (active()) main.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        requestFocus();
        main.post(sessionWatch);
    }

    private void dispose() {
        if (closed) return;
        closed = true;
        work.shutdownNow();
        images.close();
        main.removeCallbacksAndMessages(null);
        rows.clear();
        adapter.notifyDataSetChanged();
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
        // Do not reenter removeView while its API21 traversal is already removing this child.
        dispose();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int key = e.getKeyCode();
        if (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_MENU) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) close(true);
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_DOWN || key == KeyEvent.KEYCODE_DPAD_UP
                || key == KeyEvent.KEYCODE_PAGE_DOWN || key == KeyEvent.KEYCODE_PAGE_UP) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && active()) {
                retryBlocked = false;
                boolean down = key == KeyEvent.KEYCODE_DPAD_DOWN || key == KeyEvent.KEYCODE_PAGE_DOWN;
                int distance = (key == KeyEvent.KEYCODE_PAGE_DOWN || key == KeyEvent.KEYCODE_PAGE_UP)
                        ? list.getHeight() * 3 / 4 : ModernMenuHelper.dp(activity, 88);
                list.smoothScrollBy(down ? distance : -distance, 120);
                if (down) loadAtBottom();
            }
            return true;
        }
        // Center/enter and horizontal keys never select comments or reach the underlying video.
        if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
                || key == KeyEvent.KEYCODE_NUMPAD_ENTER || key == KeyEvent.KEYCODE_DPAD_LEFT
                || key == KeyEvent.KEYCODE_DPAD_RIGHT) return true;
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
            CommentsPanel panel = new CommentsPanel(a,
                    InteractionController.text(feed.get(index), "awemeId"));
            InteractionController.field(a, "commentOverlay", panel);
            ((ViewGroup) a.getWindow().getDecorView()).addView(panel, new ViewGroup.LayoutParams(-1, -1));
        } catch (Exception e) {
            Toast.makeText(a, "评论暂不可用", Toast.LENGTH_SHORT).show();
        }
    }
}
