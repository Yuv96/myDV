package com.dycomment.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class InteractionController {
    private static final int TAG = 0x7f0f7a51;
    final Activity activity;
    final Handler handler = new Handler(Looper.getMainLooper());
    ModernMenuHelper.Panel panel;
    SocialApi.State state;
    Object item;
    String id = "", secUid = "", session = "";
    int generation;
    boolean busy, stateLoading, resumePlayback;
    long lastUnread;
    int unread = -1;
    final SimpleDateFormat clockFormat = new SimpleDateFormat("HH:mm", Locale.CHINA);
    final Date clockDate = new Date();
    long clockMinute = -1;
    String clockTime = "";
    boolean unreadLoading, foreground;
    final java.util.concurrent.ExecutorService unreadWork =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    final Runnable unreadTick =
            new Runnable() {
                public void run() {
                    if (!foreground || activity.isFinishing() || activity.isDestroyed()) return;
                    updateClock(activity);
                    handler.postDelayed(this, 30000);
                }
            };

    public static void resumed(Activity a) {
        InteractionController c = get(a);
        c.foreground = true;
        c.lastUnread = 0;
        c.handler.removeCallbacks(c.unreadTick);
        c.handler.post(c.unreadTick);
        CredentialHealth.attach(a);
    }

    public static void paused(Activity a) {
        InteractionController c = get(a);
        c.foreground = false;
        c.handler.removeCallbacks(c.unreadTick);
        CredentialHealth.detach(a);
    }

    public static void destroyed(Activity a) {
        InteractionController c = get(a);
        c.foreground = false;
        c.handler.removeCallbacksAndMessages(null);
        c.unreadWork.shutdownNow();
        c.generation++;
        c.close(false);
        c.session = "";
        c.unreadSession = "";
        c.item = null;
        c.state = null;
        ModernMenuHelper.dismissCurrentMenu(a);
        PlaybackCoordinator.destroy(a);
        LiveChatController.destroy(a);
        CredentialHealth.detach(a);
        a.getWindow().getDecorView().setTag(TAG, null);
    }

    String unreadSession = "";

    InteractionController(Activity a) {
        activity = a;
    }

    static InteractionController get(Activity a) {
        View decor = a.getWindow().getDecorView();
        Object o = decor.getTag(TAG);
        if (o instanceof InteractionController) return (InteractionController) o;
        InteractionController c = new InteractionController(a);
        decor.setTag(TAG, c);
        return c;
    }

    static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    static void field(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    static String text(Object o, String name) {
        try {
            Object value = field(o, name);
            return value == null ? "" : value.toString();
        } catch (Exception e) {
            return "";
        }
    }

    static Object call(Object target, String name, Class<?>[] types, Object... args)
            throws Exception {
        Method m = target.getClass().getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    void invoke(String method) {
        try {
            call(activity, method, new Class<?>[0]);
        } catch (Exception e) {
            toast("该功能暂不可用");
        }
    }

    void toast(String s) {
        Toast.makeText(activity, s, Toast.LENGTH_LONG).show();
    }

    void showing(boolean value) {
        try {
            field(activity, "menuShowing", value);
        } catch (Exception ignored) {
        }
    }

    void pause() {
        try {
            if (!((Boolean) field(activity, "menuShowing")))
                resumePlayback = ((PlayerView) field(activity, "videoView")).isPlaying();
        } catch (Exception ignored) {
        }
        invoke("pauseForMenu");
        showing(true);
    }

    void close(boolean resume) {
        generation++;
        busy = false;
        stateLoading = false;
        if (panel != null) panel.close(false);
        panel = null;
        showing(false);
        if (resume && resumePlayback) invoke("resumeFromMenu");
    }

    public static void showQuick(Activity a) {
        get(a).quick();
    }

    public static void showSettings(Activity a) {
        get(a).settings();
    }

    /** Runs before focused views so MENU repeat never opens several overlays. */
    public static boolean handleKey(Activity a, KeyEvent e) {
        if (PlaybackCoordinator.retryKey(a, e)) return true;
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK
                && e.getAction() == KeyEvent.ACTION_DOWN
                && e.getRepeatCount() == 0) {
            try {
                Object comments = field(a, "commentOverlay");
                if (comments instanceof CommentsPanel) {
                    ((CommentsPanel) comments).close(true);
                    return true;
                }
                if (!ModernMenuHelper.isMenuShowing()
                        && !((Boolean) field(a, "menuShowing"))
                        && !((Boolean) field(a, "cameFromProfile"))) {
                    get(a).settings();
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        if (e.getKeyCode() != KeyEvent.KEYCODE_MENU) return false;
        if (e.getAction() != KeyEvent.ACTION_DOWN || e.getRepeatCount() != 0) return true;
        InteractionController c = get(a);
        if (c.panel != null && !c.panel.closed) c.comments();
        else {
            try {
                View comments = (View) field(a, "commentOverlay");
                if (comments instanceof CommentsPanel) {
                    ((CommentsPanel) comments).close(true);
                    return true;
                }
                if (comments != null && comments.getParent() != null) {
                    ((ViewGroup) comments.getParent()).removeView(comments);
                    field(a, "commentOverlay", null);
                    c.showing(false);
                    if (c.resumePlayback) c.invoke("resumeFromMenu");
                    return true;
                }
            } catch (Exception ignored) {
            }
            ModernMenuHelper.dismissCurrentMenu(a);
            c.quick();
        }
        return true;
    }

    void quick() {
        try {
            List<?> feed = (List<?>) field(activity, "feedList");
            int index = (Integer) field(activity, "currentIndex");
            if (index < 0 || index >= feed.size()) {
                toast("请等待视频加载");
                return;
            }
            item = feed.get(index);
            id = text(item, "awemeId");
            secUid = text(item, "secUid");
        } catch (Exception e) {
            toast("当前视频暂不可用");
            return;
        }
        pause();
        generation++;
        state = null;
        busy = false;
        stateLoading = false;
        session = SocialApi.cookie();
        panel =
                ModernMenuHelper.show(
                        activity,
                        "与作者互动",
                        new String[] {"喜欢", "关注", "收藏", "主页", "分享"},
                        true,
                        true,
                        this::select,
                        () -> close(true),
                        this::comments);
        loadState();
    }

    void comments() {
        close(false);
        CommentsPanel.show(activity);
    }

    boolean active(int token, String cookie) {
        return !activity.isFinishing()
                && !activity.isDestroyed()
                && token == generation
                && panel != null
                && !panel.closed
                && cookie.equals(SocialApi.cookie());
    }

    void loadState() {
        if (!SocialApi.personalCookie() || stateLoading) return;
        stateLoading = true;
        final int token = generation;
        final String video = id, author = secUid, cookie = session;
        if (!SocialApi.submit(
                () -> {
                    try {
                        final SocialApi.State result = SocialApi.state(video, author, cookie);
                        handler.post(
                                () -> {
                                    if (active(token, cookie)) {
                                        stateLoading = false;
                                        state = result;
                                        labels();
                                    }
                                });
                    } catch (Exception e) {
                        handler.post(
                                () -> {
                                    if (active(token, cookie)) {
                                        stateLoading = false;
                                        toast("互动状态读取失败，点击选项可重试");
                                    }
                                });
                    }
                })) {
            stateLoading = false;
            toast("请求较多，请稍后重试");
        }
    }

    void labels() {
        if (panel == null || panel.closed || state == null) return;
        panel.rows[0].setText(state.liked == 1 ? "已喜欢" : "喜欢");
        panel.rows[1].setText(
                state.followed == 1 || state.followed == 2
                        ? "已关注"
                        : state.followed == 4 ? "关注待确认" : "关注");
        panel.rows[2].setText(state.collected == 1 ? "已收藏" : "收藏");
    }

    void select(int action) {
        // With menu-pausing disabled, autoplay may change the video behind this panel.
        // Require a fresh choice instead of applying an action to the previous author/video.
        try {
            List<?> feed = (List<?>) field(activity, "feedList");
            int index = (Integer) field(activity, "currentIndex");
            if (index < 0 || index >= feed.size() || feed.get(index) != item) {
                close(false);
                quick();
                toast("视频已切换，请重新选择");
                return;
            }
        } catch (Exception e) {
            toast("无法确认当前视频，请重新打开菜单");
            return;
        }
        if (action == 3) {
            if (secUid.isEmpty()) {
                toast("无法获取作者主页");
                return;
            }
            close(false);
            try {
                call(activity, "openProfile", new Class<?>[] {String.class}, secUid);
            } catch (Exception e) {
                toast("无法打开作者主页");
            }
            return;
        }
        if (action == 4) {
            if (!SocialApi.personalCookie()) {
                toast("请先扫码登录");
                return;
            }
            if (!id.matches("[0-9]+") || text(item, "isLive").equals("true")) {
                toast("仅支持分享当前短视频");
                return;
            }
            close(false);
            Intent share = new Intent(activity, QuickShareActivity.class);
            share.putExtra("video_id", id);
            activity.startActivity(share);
            return;
        }
        if (busy) return;
        if (!SocialApi.personalCookie()) {
            toast("请先在账号与登录中扫码登录");
            return;
        }
        if (!session.equals(SocialApi.cookie())) {
            session = SocialApi.cookie();
            state = null;
        }
        if (state == null) {
            toast("正在读取互动状态，请稍候再按确定");
            loadState();
            return;
        }
        int current = action == 0 ? state.liked : action == 1 ? state.followed : state.collected;
        if (current < 0) {
            toast("暂时无法确认状态，请稍后重试");
            loadState();
            return;
        }
        if (action == 1 && current != 0) {
            toast(current == 4 ? "关注申请待确认" : "已经关注这位作者");
            return;
        }
        if (action == 1 && state.uid.isEmpty()) {
            toast("无法确认作者账号");
            return;
        }
        final boolean enable = current == 0;
        final String video = id, author = secUid, uid = state.uid, cookie = session;
        final int token = generation;
        busy = true;
        panel.rows[action].setText("处理中...");
        if (!SocialApi.submit(
                () -> {
                    SocialApi.State result = null;
                    String message;
                    try {
                        SocialApi.change(action, enable, video, uid, author, cookie);
                        result = SocialApi.state(video, author, cookie);
                        int now =
                                action == 0
                                        ? result.liked
                                        : action == 1 ? result.followed : result.collected;
                        boolean confirmed =
                                action == 1 ? now == 1 || now == 2 : now == (enable ? 1 : 0);
                        message =
                                confirmed
                                        ? (action == 0
                                                ? (enable ? "已喜欢" : "已取消喜欢")
                                                : action == 1 ? "已关注" : enable ? "已收藏" : "已取消收藏")
                                        : "请求已提交，状态尚未确认";
                    } catch (Exception e) {
                        message = "操作未确认：" + e.getMessage();
                    }
                    final SocialApi.State updated = result;
                    final String notice = message;
                    handler.post(
                            () -> {
                                if (!active(token, cookie)) return;
                                busy = false;
                                if (updated != null) state = updated;
                                labels();
                                toast(notice);
                            });
                })) {
            busy = false;
            labels();
            toast("请求较多，操作尚未提交，请稍后重试");
        }
    }

    void settings() {
        pause();
        String[] labels = {
            "内容与主页",
            "播放与弹幕",
            "界面显示",
            "账号与登录" + (CredentialHealth.needsRefresh() ? " · 需要更新" : ""),
            "操作说明与关于",
            "退出"
        };
        ModernMenuHelper.showMenu(
                activity,
                "设置",
                labels,
                index -> {
                    if (index == 0)
                        submenu(
                                "内容与主页",
                                new String[] {"关注的直播", "个人主页", "搜索", "精选", "刷新推荐"},
                                new String[] {
                                    "live",
                                    "openSelfProfile",
                                    "openSearch",
                                    "openFeatured",
                                    "loadFeed"
                                });
                    else if (index == 1)
                        submenu(
                                "播放与弹幕",
                                new String[] {"播放倍速", "播放画质", "弹幕开关", "弹幕透明度", "弹幕大小", "弹幕速度"},
                                new String[] {
                                    "showSpeedPicker",
                                    "showQualityPicker",
                                    "toggleDanmaku",
                                    "showOpacityPicker",
                                    "showSizePicker",
                                    "showDanmakuSpeedPicker"
                                });
                    else if (index == 2) displaySettings();
                    else if (index == 3)
                        submenu(
                                "账号与登录",
                                new String[] {SocialApi.personalCookie() ? "重新扫码登录" : "扫码登录"},
                                new String[] {"login"});
                    else if (index == 4) {
                        showing(false);
                        activity.startActivity(new Intent(activity, AboutActivity.class));
                    } else activity.finish();
                },
                () -> close(true));
    }

    void displaySettings() {
        String[] labels = {"资料卡显示", "时间显示", "菜单暂停", "自动连播", "过滤竖屏"};
        ModernMenuHelper.showMenu(
                activity,
                "界面与偏好",
                labels,
                index -> {
                    showing(false);
                    if (index == 0) {
                        invoke("showOverlayModePicker");
                        return;
                    }
                    String[] fields = {
                        "", "showClock", "pauseOnMenu", "autoPlayNext", "filterVertical"
                    };
                    try {
                        boolean enabled = !((Boolean) field(activity, fields[index]));
                        field(activity, fields[index], enabled);
                        invoke("saveDanmakuPrefs");
                        if (index == 1) updateClock(activity);
                        toast(labels[index] + (enabled ? "已开启" : "已关闭"));
                        if (resumePlayback) invoke("resumeFromMenu");
                    } catch (Exception e) {
                        toast("设置暂不可用");
                    }
                },
                () -> settings());
    }

    void submenu(String title, String[] labels, String[] methods) {
        ModernMenuHelper.showMenu(
                activity,
                title,
                labels,
                index -> {
                    showing(false);
                    String method = methods[index];
                    if (method.equals("live"))
                        activity.startActivity(new Intent(activity, FollowedLiveActivity.class));
                    else if (method.equals("login")) CredentialHealth.open(activity);
                    else invoke(method);
                },
                () -> settings());
    }

    public static void updateClock(Activity a) {
        InteractionController c = get(a);
        try {
            TextView clock = (TextView) field(a, "tvClock");
            if (clock == null) return;
            if (!((Boolean) field(a, "showClock"))) {
                clock.setVisibility(View.GONE);
                return;
            }
            clock.setVisibility(View.VISIBLE);
            String cookie = SocialApi.cookie();
            if (!cookie.equals(c.unreadSession)) {
                c.unreadSession = cookie;
                c.unread = -1;
                c.lastUnread = 0;
            }
            long wall = System.currentTimeMillis();
            if (c.clockMinute != wall / 60000) {
                c.clockMinute = wall / 60000;
                c.clockDate.setTime(wall);
                c.clockTime = c.clockFormat.format(c.clockDate);
            }
            String label = c.clockTime + (c.unread > 0 ? "  |  +" + c.unread : "");
            if (!label.contentEquals(clock.getText())) clock.setText(label);
            String description = c.unread > 0 ? "时间，抖音未读通知 " + c.unread : "时间";
            if (!description.equals(clock.getContentDescription()))
                clock.setContentDescription(description);
            long now = SystemClock.elapsedRealtime();
            if (!c.foreground
                    || !SocialApi.personalCookie()
                    || c.unreadLoading
                    || (c.lastUnread != 0 && now - c.lastUnread < 30000)) return;
            c.unreadLoading = true;
            c.lastUnread = now;
            c.unreadWork.execute(
                    () -> {
                        int count;
                        try {
                            count = SocialApi.unread(cookie);
                        } catch (Exception e) {
                            count = -1;
                        }
                        final int result = count;
                        c.handler.post(
                                () -> {
                                    c.unreadLoading = false;
                                    if (a.isFinishing()
                                            || a.isDestroyed()
                                            || !cookie.equals(SocialApi.cookie())) return;
                                    if (result >= 0) c.unread = result;
                                    updateClock(a);
                                });
                    });
        } catch (Exception ignored) {
        }
    }
}
