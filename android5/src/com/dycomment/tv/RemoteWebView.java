package com.dycomment.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.webkit.WebView;

/** Visible TV pointer operates the unchanged official website. */
final class RemoteWebView extends WebView {
    private final Paint pointer = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float x = -1, y = -1;
    private long pressed;

    RemoteWebView(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setContentDescription("抖音官方登录网页，方向键移动光标，确定点击，返回回到按钮");
    }

    private void locate() {
        if (x < 0) x = getWidth() / 2f;
        if (y < 0) y = getHeight() / 2f;
        x = Math.max(4, Math.min(Math.max(4, getWidth() - 4), x));
        y = Math.max(4, Math.min(Math.max(4, getHeight() - 4), y));
    }

    private void touch(int action) {
        MotionEvent event = MotionEvent.obtain(pressed, SystemClock.uptimeMillis(), action, x, y, 0);
        super.onTouchEvent(event);
        event.recycle();
    }

    void cancelPointer() {
        if (pressed != 0) touch(MotionEvent.ACTION_CANCEL);
        pressed = 0;
        invalidate();
    }

    @Override public boolean onKeyDown(int code, KeyEvent event) {
        locate();
        int step = Math.max(12, Math.round(20 * getResources().getDisplayMetrics().density));
        if (code >= KeyEvent.KEYCODE_DPAD_UP && code <= KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) x -= step;
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) x += step;
            if (code == KeyEvent.KEYCODE_DPAD_UP) {
                if (y <= step) scrollBy(0, -step * 3); else y -= step;
            }
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (y >= getHeight() - step) scrollBy(0, step * 3); else y += step;
            }
            locate();
            if (pressed != 0) touch(MotionEvent.ACTION_MOVE);
            invalidate();
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
            if (pressed == 0) { pressed = SystemClock.uptimeMillis(); touch(MotionEvent.ACTION_DOWN); }
            return true;
        }
        return super.onKeyDown(code, event);
    }

    @Override public boolean onKeyUp(int code, KeyEvent event) {
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
            if (pressed != 0) touch(MotionEvent.ACTION_UP);
            pressed = 0;
            return true;
        }
        if (code >= KeyEvent.KEYCODE_DPAD_UP && code <= KeyEvent.KEYCODE_DPAD_RIGHT) return true;
        return super.onKeyUp(code, event);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!hasFocus()) return;
        locate();
        float cx = x + getScrollX(), cy = y + getScrollY();
        pointer.setStyle(Paint.Style.STROKE);
        pointer.setStrokeWidth(4);
        pointer.setColor(0xff000000);
        canvas.drawCircle(cx, cy, 10, pointer);
        pointer.setStrokeWidth(2);
        pointer.setColor(0xffffffff);
        canvas.drawCircle(cx, cy, 8, pointer);
        canvas.drawLine(cx - 14, cy, cx + 14, cy, pointer);
        canvas.drawLine(cx, cy - 14, cx, cy + 14, pointer);
    }
}
