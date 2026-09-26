package com.dycomment.tv;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/** Deterministic API21 UI coverage; executed only by GitHub Actions. */
public final class CommentsSelfTestActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Fixture source = new Fixture(false);
    private CommentsPanel panel, detached;
    private Fixture delayed;
    private ViewGroup decor;
    private int stage, anchorPosition, anchorOffset, repeatsRemaining;
    private String anchorText;
    private long started, stageAt;
    private boolean done;

    protected void onCreate(Bundle state) {
        super.onCreate(state);
        decor = (ViewGroup) getWindow().getDecorView();
        panel = new CommentsPanel(this, source);
        decor.addView(panel, new ViewGroup.LayoutParams(-1, -1));
        started = stageAt = SystemClock.elapsedRealtime();
        handler.postDelayed(check, 200);
    }

    private static final class Fixture implements CommentsPanel.PageSource {
        private static final String[] TEXTS = {
            "这个镜头拍得真好，晚霞的颜色也很舒服。",
            "原来还可以这样，学到了。",
            "慢慢看完才发现，最打动人的是那些不起眼的小细节。",
            "配乐和画面刚刚好。",
            "周末也想出去走走，吹吹风。",
            "这段看了好几遍，还是觉得很有意思。",
            "隔着屏幕都能感受到当时的开心。",
            "谢谢分享，认真生活的样子真的很美。"
        };
        private static final String[] NAMES = {
            "山间清风", "晚风", "小满", "知夏", "白日梦", "向晴", "听海", "慢慢来"
        };
        volatile boolean current = true;
        volatile int requested = -1, requests;
        final boolean delayed;
        final CountDownLatch release = new CountDownLatch(1);

        Fixture(boolean wait) { delayed = wait; }

        public boolean ready() { return true; }

        public boolean current() { return current; }

        public JSONObject load(int cursor) throws Exception {
            requested = cursor;
            requests++;
            if (delayed || cursor == 220) {
                // Deliberately outlive interruption to model an already-running HTTP read.
                boolean released = false;
                while (!released) {
                    try {
                        release.await();
                        released = true;
                    } catch (InterruptedException ignored) {
                    }
                }
            }
            JSONArray comments = new JSONArray();
            // An empty but advancing page must not stop subsequent pages.
            if (cursor != 20) for (int i = cursor; i < cursor + 20; i++) {
                comments.put(new JSONObject()
                        .put("text", TEXTS[i % TEXTS.length])
                        .put("digg_count", i)
                        .put("user", new JSONObject().put("nickname", NAMES[i % NAMES.length])));
            }
            return new JSONObject().put("comments", comments)
                    .put("cursor", cursor + 20).put("has_more", cursor < 320 ? 1 : 0);
        }
    }

    private Object field(Object target, String name) throws Exception {
        return InteractionController.field(target, name);
    }

    private ListView list() throws Exception { return (ListView) field(panel, "list"); }

    private boolean busy() throws Exception { return (Boolean) field(panel, "busy"); }

    private void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private void key(int code) {
        panel.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        panel.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
    }

    private void passive(View view) {
        require(!view.isFocusable() && !view.isClickable() && !view.isLongClickable(),
                "comment content exposes focus or action");
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) passive(group.getChildAt(i));
        }
    }

    private String rowText(View row) throws Exception {
        // Likes are unique fixture data, so repeated natural text still identifies the same row.
        return ((TextView) field(row, "meta")).getText().toString()
                + "\n" + ((TextView) field(row, "body")).getText().toString();
    }

    private void savePopulatedScreenshot() throws Exception {
        Bitmap picture = Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888);
        try {
            decor.draw(new Canvas(picture));
            File directory = getExternalFilesDir(null);
            if (directory == null) directory = getFilesDir();
            File screenshot = new File(directory, "comments-populated.png");
            try (FileOutputStream out = new FileOutputStream(screenshot)) {
                require(picture.compress(Bitmap.CompressFormat.PNG, 100, out), "save populated screenshot");
            }
            require(screenshot.isFile() && screenshot.length() > 0, "populated screenshot exists");
            Log.i("Android5CommentsTest", "SCREENSHOT_PATH=" + screenshot.getCanonicalPath());
        } finally {
            picture.recycle();
        }
    }

    private void requireCircularPixels(ImageView avatar, int centerColor, boolean placeholder) {
        int size = avatar.getWidth();
        Bitmap rendered = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        try {
            avatar.draw(new Canvas(rendered));
            int[] outside = {0, size / 10, size - size / 10 - 1, size - 1};
            for (int x : outside) for (int y : outside) {
                require(Color.alpha(rendered.getPixel(x, y)) == 0,
                        "avatar background and image corners must be transparent");
            }
            int[] inside = {size / 4, size / 2, size * 3 / 4};
            for (int x : inside) {
                int pixel = rendered.getPixel(x, size / 2);
                require(placeholder ? Color.alpha(pixel) == 0x26 : pixel == centerColor,
                        placeholder ? "round placeholder remains visible"
                                : "loaded avatar remains visible and center cropped");
            }
            rendered.eraseColor(Color.BLUE);
            avatar.draw(new Canvas(rendered));
            for (int x : outside) for (int y : outside) {
                require(rendered.getPixel(x, y) == Color.BLUE,
                        "avatar mask must preserve the comment row underneath");
            }
        } finally {
            rendered.recycle();
        }
    }

    private void circularAvatar(ImageView avatar) {
        PreviewImages loader = new PreviewImages();
        Bitmap loaded = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888);
        try {
            loader.bind(avatar, "");
            requireCircularPixels(avatar, 0, true);
            loaded.eraseColor(Color.RED);
            Paint paint = new Paint();
            paint.setColor(Color.GREEN);
            new Canvas(loaded).drawRect(20, 0, 60, 40, paint);
            // Models the loader's completion, including its still-present square background.
            avatar.setImageBitmap(loaded);
            requireCircularPixels(avatar, Color.GREEN, false);
            loader.bind(avatar, "");
            require(avatar.getDrawable() == null, "recycled avatar clears the previous image");
            requireCircularPixels(avatar, 0, true);
        } finally {
            avatar.setImageBitmap(null);
            loader.close();
            loaded.recycle();
        }
    }

    private final Runnable rapidDown = new Runnable() {
        public void run() {
            if (done) return;
            long now = SystemClock.uptimeMillis();
            panel.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_DPAD_DOWN, 16 - repeatsRemaining));
            if (--repeatsRemaining > 0) handler.postDelayed(this, 25);
            else panel.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN));
        }
    };

    private final Runnable check = new Runnable() {
        public void run() {
            if (done) return;
            long now = SystemClock.elapsedRealtime();
            try {
                require(now - started < 65000, "timeout stage=" + stage);
                ListView list = list();
                if (stage == 0 && list.getChildCount() > 1 && !busy()) {
                    require(panel.isFocused() && !list.isFocusable(), "panel owns remote focus");
                    require(!list.getAdapter().areAllItemsEnabled(), "comments cannot be selected");
                    for (int i = 0; i < list.getChildCount(); i++) passive(list.getChildAt(i));
                    View row = list.getChildAt(0);
                    TextView body = (TextView) field(row, "body");
                    TextView meta = (TextView) field(row, "meta");
                    ImageView avatar = (ImageView) field(row, "avatar");
                    require(Math.abs(body.getTextSize() - 2 * meta.getTextSize()) < 0.1f,
                            "nickname and likes must be half the body size");
                    require(avatar.getWidth() == ModernMenuHelper.dp(CommentsSelfTestActivity.this, 40)
                            && avatar.getHeight() == avatar.getWidth(), "fixed square avatar");
                    require(avatar.getLeft() > row.getWidth() / 2
                            && Math.abs(avatar.getTop() + avatar.getHeight() / 2
                                    - row.getHeight() / 2) <= 1, "avatar on right and vertically centered");
                    circularAvatar(avatar);
                    require(((ViewGroup) list.getParent()).getChildCount() == 3,
                            "comment panel contains only title, status, list");
                    savePopulatedScreenshot();
                    anchorPosition = list.getFirstVisiblePosition();
                    anchorOffset = list.getChildAt(0).getTop();
                    key(KeyEvent.KEYCODE_DPAD_CENTER);
                    require(panel.getParent() == decor && panel.isFocused(), "center has no action");
                    key(KeyEvent.KEYCODE_DPAD_DOWN);
                    stage = 1;
                    stageAt = now;
                } else if (stage == 1 && now - stageAt > 200) {
                    require(list.getFirstVisiblePosition() > anchorPosition
                            || list.getChildAt(0).getTop() < anchorOffset, "remote down scrolls pixels");
                    key(KeyEvent.KEYCODE_DPAD_UP);
                    stage = 2;
                    stageAt = now;
                } else if (stage == 2 && now - stageAt > 200) {
                    require(list.getFirstVisiblePosition() == 0, "remote up returns to first row");
                    require(panel.isFocused(), "scrolling never moves focus to comments");
                    repeatsRemaining = 16;
                    handler.post(rapidDown);
                    stage = 11;
                    stageAt = now;
                } else if (stage == 11 && repeatsRemaining == 0 && now - stageAt > 900) {
                    require(list.getFirstVisiblePosition() > 0 || list.getChildAt(0).getTop() < 0,
                            "rapid remote repeat continues scrolling");
                    require(panel.isFocused() && list.getSelectedItemPosition() == -1,
                            "rapid remote repeat never selects a comment");
                    stage = 3;
                } else if (stage == 3) {
                    require(list.getCount() <= 200, "retained comments exceeded memory bound");
                    if (source.requested == 220) {
                        // Let the last smooth scroll finish before recording its viewport anchor.
                        stage = 4;
                        stageAt = now;
                    } else if (!busy()) key(KeyEvent.KEYCODE_PAGE_DOWN);
                } else if (stage == 4 && now - stageAt > 200) {
                    anchorText = rowText(list.getChildAt(0));
                    anchorOffset = list.getChildAt(0).getTop();
                    source.release.countDown();
                    stage = 5;
                } else if (stage == 5 && !busy() && (Integer) field(panel, "cursor") >= 240) {
                    require(anchorText.equals(rowText(list.getChildAt(0)))
                            && Math.abs(anchorOffset - list.getChildAt(0).getTop()) <= 1,
                            "eviction must preserve visible comment and pixel offset");
                    stage = 6;
                } else if (stage == 6) {
                    require(list.getCount() <= 200, "pagination retained too many rows");
                    if (!(Boolean) field(panel, "hasMore") && !busy()) {
                        require((Integer) field(panel, "cursor") == 340 && source.requests == 17,
                                "pagination must pass 200 and an empty advancing page");
                        require(list.getCount() == 200, "bounded rolling window retains 200 comments");
                        for (int i = 0; i < list.getChildCount(); i++) passive(list.getChildAt(i));
                        require(panel.isFocused() && list.getSelectedItemPosition() == -1,
                                "pagination keeps all rows unfocused and unselected");
                        source.current = false;
                        stage = 7;
                        stageAt = now;
                    } else if (!busy()) key(KeyEvent.KEYCODE_PAGE_DOWN);
                } else if (stage == 7 && panel.getParent() == null) {
                    require(((List<?>) field(panel, "rows")).isEmpty(), "account switch clears rows");
                    require((Boolean) field(panel, "closed"), "account switch disposes panel");
                    delayed = new Fixture(true);
                    detached = new CommentsPanel(CommentsSelfTestActivity.this, delayed);
                    decor.addView(detached, new ViewGroup.LayoutParams(-1, -1));
                    stage = 8;
                } else if (stage == 8 && delayed.requested == 0) {
                    decor.removeView(detached);
                    delayed.release.countDown();
                    stage = 9;
                    stageAt = now;
                } else if (stage == 9 && now - stageAt > 500) {
                    require(detached.getParent() == null && (Boolean) field(detached, "closed"),
                            "external removal disposes safely");
                    require(((List<?>) field(detached, "rows")).isEmpty(),
                            "late network callback must not repopulate removed panel");
                    CommentsPanel explicit = new CommentsPanel(CommentsSelfTestActivity.this,
                            new Fixture(false));
                    decor.addView(explicit, new ViewGroup.LayoutParams(-1, -1));
                    explicit.close(false);
                    require(explicit.getParent() == null && (Boolean) field(explicit, "closed"),
                            "explicit close disposes safely");
                    done = true;
                    Log.i("Android5CommentsTest", "PASS API21_COMMENTS_READ_ONLY_SCROLL_PAGING_LIFECYCLE");
                    return;
                }
            } catch (Exception e) {
                done = true;
                Log.e("Android5CommentsTest", "FAIL " + e.getMessage());
                cleanup();
                return;
            }
            handler.postDelayed(this, 180);
        }
    };

    private void cleanup() {
        handler.removeCallbacksAndMessages(null);
        source.release.countDown();
        if (delayed != null) delayed.release.countDown();
        if (panel != null) panel.close(false);
        if (detached != null) detached.close(false);
    }

    protected void onDestroy() {
        cleanup();
        super.onDestroy();
    }
}
