package com.dycomment.tv;

import android.app.Activity;
import android.os.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.Gravity;
import android.widget.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** One QR attempt at a time; no local HTTP server, manual secrets, hidden retries or background polling. */
public final class QrLoginActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService work=Executors.newSingleThreadExecutor();
    private QrSession session;
    private TextView status,refresh;
    private ImageView qr;
    private int generation;
    private long started;
    private Bitmap bitmap;
    private boolean running;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b); LinearLayout root=UiTheme.page(this,"扫码登录"); root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(UiTheme.text(this,"使用抖音 App 扫码，并在手机上确认",20));
        qr=new ImageView(this); qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int size=Math.min(ModernMenuHelper.dp(this,280),getResources().getDisplayMetrics().heightPixels/2);
        root.addView(qr,new LinearLayout.LayoutParams(size,size));
        status=UiTheme.text(this,"正在请求二维码…",17); status.setGravity(Gravity.CENTER); root.addView(status);
        refresh=UiTheme.button(this,"刷新二维码",()->start()); root.addView(refresh);
        root.addView(UiTheme.button(this,"返回",()->finish())); setContentView(root); refresh.requestFocus(); start();
    }
    private boolean valid(int token) { return token==generation && running && !isFinishing() && !isDestroyed(); }
    private void clearImage() { qr.setImageDrawable(null); if(bitmap!=null) { bitmap.recycle(); bitmap=null; } }
    private void cancel() { generation++; running=false; main.removeCallbacksAndMessages(null); if(session!=null) session.close(); session=null; }
    private void start() {
        if(running) return;
        cancel(); clearImage(); running=true; started=SystemClock.elapsedRealtime(); final int token=generation;
        session=new QrSession(); final QrSession attempt=session; status.setText("正在请求二维码…"); refresh.setEnabled(false);
        work.execute(()->{
            try {
                String encoded=attempt.create(); int comma=encoded.indexOf(','); if(encoded.startsWith("data:")) encoded=encoded.substring(comma+1);
                byte[] bytes=Base64.decode(encoded,Base64.DEFAULT); BitmapFactory.Options options=new BitmapFactory.Options(); options.inJustDecodeBounds=true;
                BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                if(options.outWidth<=0 || options.outHeight<=0 || options.outWidth>2048 || options.outHeight>2048) throw new Exception("二维码图片无效");
                options.inJustDecodeBounds=false; final Bitmap image=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                if(image==null) throw new Exception("无法显示二维码");
                main.post(()-> { if(!valid(token)) { image.recycle(); return; } bitmap=image; qr.setImageBitmap(image); status.setText("等待扫码，二维码三分钟内有效"); poll(token,attempt); });
            } catch(Exception e) { fail(token,e); }
        });
    }
    private void poll(int token,QrSession attempt) {
        if(!valid(token)) return;
        if(SystemClock.elapsedRealtime()-started>180000) { fail(token,new Exception("二维码已过期，请刷新")); return; }
        main.postDelayed(()->{
            if(!valid(token)) return;
            work.execute(()->{
                try {
                    JSONObject response=attempt.poll(); String state=response.optString("status");
                    if(state.equals("confirmed") || state.equals("3")) {
                        final String cookie=attempt.finish(response);
                        main.post(()-> { if(!valid(token)) return;
                            try { CredentialStore.save(this,cookie); cancel(); clearImage(); status.setText("登录成功。评论权限会在读取评论时验证"); refresh.setEnabled(true); }
                            catch(Exception e) { fail(token,e); }
                        }); return;
                    }
                    if(state.equals("expired") || state.equals("refused") || state.equals("4") || state.equals("5")) throw new Exception("二维码已失效或被取消，请刷新");
                    if(!state.equals("new") && !state.equals("1") && !state.equals("scanned") && !state.equals("2")) throw new Exception("登录需要进一步验证，请在抖音 App 查看");
                    main.post(()->{ if(!valid(token)) return; status.setText(state.equals("scanned") || state.equals("2") ? "已扫码，请在手机上确认" : "等待扫码"); poll(token,attempt); });
                } catch(Exception e) { fail(token,e); }
            });
        },2000);
    }
    private void fail(int token,Exception e) { main.post(()->{ if(!valid(token)) return; cancel(); clearImage(); status.setText(e.getMessage()==null ? "登录暂不可用，请稍后重试" : e.getMessage()); refresh.setEnabled(true); }); }
    @Override protected void onStop() { cancel(); refresh.setEnabled(true); clearImage(); super.onStop(); }
    @Override protected void onDestroy() { cancel(); work.shutdownNow(); clearImage(); super.onDestroy(); }
}
