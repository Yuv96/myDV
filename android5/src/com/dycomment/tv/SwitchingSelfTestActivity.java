package com.dycomment.tv;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Exercises the real patched MainActivity, using only generated loopback fixtures. */
public final class SwitchingSelfTestActivity extends Activity implements Application.ActivityLifecycleCallbacks {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final List<Socket> sockets=Collections.synchronizedList(new ArrayList<Socket>());
    private final AtomicInteger largeRequests=new AtomicInteger(), slowRequests=new AtomicInteger();
    private ServerSocket server;
    private volatile boolean running;
    private Activity feed;
    private PlayerView player;
    private String root;
    private int stage, cycles, outputs, target, staleToken, returns, nativeGeneration, beforeReturn;
    private long started, stageAt, heartbeatAt, worstGap, heapBefore;
    private boolean done;
    private Object staleAvatar;
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            getSharedPreferences("dy_config",0).edit().putBoolean("danmaku_enabled",false)
                .putBoolean("auto_play_next",false).putString("overlay_mode","permanent").apply();
            File[] cached=new File(getCacheDir(),"next-video-v1").listFiles();
            if(cached!=null) for(File f:cached) f.delete();
            server=new ServerSocket(0,20,InetAddress.getByName("127.0.0.1")); running=true;
            root="http://127.0.0.1:"+server.getLocalPort();
            new Thread(() -> serve(),"switch-fixture").start();
            Class<?> mainClass=Class.forName("com.dycomment.tv.MainActivity");
            Class<?> itemClass=Class.forName("com.dycomment.tv.DouyinApi$FeedItem");
            List<Object> items=new ArrayList<Object>();
            for(int i=0;i<20;i++) {
                Object item=itemClass.getConstructor(String.class,String.class,String.class,String.class)
                    .newInstance("fixture-"+i,"fixture "+i,"local test","");
                InteractionController.field(item,"videoUrl",root+(i==0 ? "/good.mp4" : i==1 ? "/large.mp4" : "/stall.mp4")+"?via=douyinvod.com");
                InteractionController.field(item,"videoWidth",1280); InteractionController.field(item,"videoHeight",720);
                items.add(item);
            }
            mainClass.getField("profileVideoList").set(null,items);
            mainClass.getField("profilePlayIndex").setInt(null,0);
            getApplication().registerActivityLifecycleCallbacks(this);
            started=stageAt=heartbeatAt=SystemClock.elapsedRealtime();
            handler.post(heartbeat); handler.postDelayed(check,100);
            Intent intent=new Intent(this,mainClass); intent.putExtra("from_profile",true); startActivity(intent);
        } catch(Exception e) { fail("setup "+e.getClass().getSimpleName()); }
    }
    private final Runnable heartbeat=new Runnable() {
        public void run() {
            if(done) return;
            long now=SystemClock.elapsedRealtime(); worstGap=Math.max(worstGap,now-heartbeatAt); heartbeatAt=now;
            handler.postDelayed(this,25);
        }
    };
    private Object field(String name) throws Exception { return InteractionController.field(feed,name); }
    private void key(int key) {
        feed.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,key));
        feed.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,key));
    }
    private void checkLoading() throws Exception {
        if(((View)field("infoOverlay")).getVisibility()==View.VISIBLE) throw new Exception("metadata visible before video output");
        if(((View)field("loadingContainer")).getVisibility()!=View.VISIBLE) throw new Exception("missing loading feedback");
    }
    private final Runnable check=new Runnable() {
        public void run() {
            if(done) return;
            long now=SystemClock.elapsedRealtime();
            if(now-started>115000) { fail("timeout stage="+stage); return; }
            try {
                if(feed==null || player==null) { handler.postDelayed(this,100); return; }
                if(stage==0 && outputs>0 && player.getCurrentPosition()>2500 && largeRequests.get()>0) {
                    if(((View)field("infoOverlay")).getVisibility()!=View.VISIBLE) throw new Exception("metadata not committed on video output");
                    heapBefore=android.os.Debug.getNativeHeapAllocatedSize();
                    staleToken=PlaybackCoordinator.token(feed);
                    Constructor<?> ctor=Class.forName("com.dycomment.tv.MainActivity$26").getDeclaredConstructors()[0];
                    ctor.setAccessible(true); staleAvatar=ctor.newInstance(feed);
                    key(KeyEvent.KEYCODE_DPAD_DOWN); target=1; checkLoading();
                    stage=1; stageAt=now;
                    Log.i("Android5SwitchTest","OVERSIZE_PREFETCH_SKIPPED_AND_LOADING_UI_OK");
                } else if(stage==1 && now-stageAt>220) {
                    // Repeated down/up while a 512 MiB response never delivers a frame.
                    key(target==1 ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN);
                    target=1-target; checkLoading();
                    cycles++; stageAt=now;
                    if(cycles>=32) {
                        if(target==1) key(KeyEvent.KEYCODE_DPAD_UP);
                        if(PlaybackCoordinator.valid(feed,staleToken)) throw new Exception("selection epoch survived roundtrip");
                        android.widget.ImageView avatar=(android.widget.ImageView)field("ivAuthorAvatar");
                        Object before=avatar.getDrawable();
                        Method loaded=staleAvatar.getClass().getDeclaredMethod("onLoaded",android.graphics.Bitmap.class); loaded.setAccessible(true);
                        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(1,1,android.graphics.Bitmap.Config.ARGB_8888);
                        loaded.invoke(staleAvatar,bitmap);
                        if(avatar.getDrawable()!=before) throw new Exception("late old avatar changed new selection");
                        bitmap.recycle();
                        outputs=0; stage=2; stageAt=now;
                    }
                } else if(stage==2 && outputs>0 && player.getCurrentPosition()>800) {
                    if((Integer)field("currentIndex")!=0) throw new Exception("returned to wrong video");
                    if(((View)field("infoOverlay")).getVisibility()!=View.VISIBLE) throw new Exception("returned metadata missing");
                    Log.i("Android5SwitchTest","RAPID_ROUNDTRIP_RECOVERED cycles="+cycles);
                    // A small advertised file with a stalled body exercises in-progress cancellation.
                    List<?> items=(List<?>)field("feedList");
                    InteractionController.field(items.get(1),"videoUrl",root+"/stall.mp4?via=douyinvod.com");
                    key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_UP);
                    stage=3; stageAt=now;
                } else if(stage==3 && now-stageAt>4500 && slowRequests.get()>0 && player.getCurrentPosition()>1000) {
                    key(KeyEvent.KEYCODE_DPAD_DOWN); checkLoading(); stage=4; stageAt=now;
                } else if(stage==4 && now-stageAt>24000) {
                    if(!((android.widget.TextView)field("tvLoading")).getText().toString().contains("确定键重试"))
                        throw new Exception("stalled startup did not reach recoverable timeout");
                    checkLoading(); outputs=0; key(KeyEvent.KEYCODE_DPAD_UP); stage=5; stageAt=now;
                } else if(stage==5 && outputs>0 && player.getCurrentPosition()>1000) {
                    if((Integer)field("currentIndex")!=0) throw new Exception("timeout recovery selected wrong item");
                    if(worstGap>2000) throw new Exception("UI blocked for "+worstGap+"ms");
                    int stops=0; for(Thread t:Thread.getAllStackTraces().keySet()) if(t.getName().equals("player-stop")) stops++;
                    if(stops>1) throw new Exception("native stops accumulated");
                    long growth=android.os.Debug.getNativeHeapAllocatedSize()-heapBefore;
                    if(growth>64L*1024*1024) throw new Exception("native heap grew "+growth);
                    File[] files=new File(getCacheDir(),"next-video-v1").listFiles(); long total=0;
                    if(files!=null) for(File f:files) { total+=f.length(); if(f.length()>32L*1024*1024) throw new Exception("oversized cache entry"); }
                    if(total>64L*1024*1024) throw new Exception("cache budget exceeded");
                    Log.i("Android5SwitchTest","SWITCHING_OK heartbeat_ms="+worstGap+" heap_growth="+growth);
                    View author=(View)field("tvAuthor"), stats=(View)field("tvStats");
                    if(author.getParent()!=stats.getParent() || stats.getBackground()!=null) throw new Exception("metadata card hierarchy");
                    if(((View)field("infoOverlay")).getBackground()==null) throw new Exception("missing single material card");
                    stage=6; stageAt=now; outputs=0; beforeReturn=player.getCurrentPosition();
                    nativeGeneration=(Integer)InteractionController.field(player,"generation");
                    feed.startActivity(new Intent(feed,SurfaceCoverTestActivity.class));
                } else if(stage==6 && now-stageAt>3000 && outputs>0) {
                    int position=player.getCurrentPosition();
                    if(position<beforeReturn || position>beforeReturn+6000) throw new Exception("return lost playback position");
                    if((Integer)InteractionController.field(player,"generation")!=nativeGeneration) throw new Exception("return reloaded instead of reattaching surface");
                    if((Integer)field("currentIndex")!=0 || !player.isPlaying()) throw new Exception("return did not resume selected video");
                    if(++returns<3) {
                        stageAt=now; outputs=0; beforeReturn=position;
                        feed.startActivity(new Intent(feed,SurfaceCoverTestActivity.class));
                    } else {
                        Log.i("Android5SwitchTest","PASS API21_REAL_FEED_OVERSIZE_STALL_RAPID_RETURN_TIMEOUT_STALE_CALLBACKS_SURFACE_RETURN_CARD returns="+returns+" heartbeat_ms="+worstGap);
                        done=true; cleanup(); return;
                    }
                }
            } catch(Exception e) { fail(e.getMessage()); return; }
            handler.postDelayed(this,100);
        }
    };
    private void serve() {
        while(running) try { Socket s=server.accept(); sockets.add(s); new Thread(() -> respond(s),"switch-fixture-request").start(); }
        catch(IOException e) { return; }
    }
    private void respond(Socket socket) {
        try(Socket s=socket) {
            s.setSoTimeout(30000);
            BufferedReader reader=new BufferedReader(new InputStreamReader(s.getInputStream(),"UTF-8"));
            String first=reader.readLine(); if(first==null) return;
            String path=new URI(first.split(" ")[1]).getPath(),line,range=null;
            while((line=reader.readLine())!=null && !line.isEmpty()) if(line.toLowerCase(Locale.US).startsWith("range:")) range=line.substring(6).trim();
            OutputStream out=s.getOutputStream();
            if(!path.equals("/good.mp4")) {
                boolean large=path.equals("/large.mp4");
                if(large) largeRequests.incrementAndGet(); else slowRequests.incrementAndGet();
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: video/mp4\r\nContent-Length: "+(large ? 512L*1024*1024 : 8L*1024*1024)+"\r\nConnection: close\r\n\r\n").getBytes("UTF-8")); out.flush();
                // The response body is deliberately stalled; no huge allocation/download is needed.
                while(running && reader.read()!=-1) {}
                return;
            }
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream in=getAssets().open("selftest-high.mp4")) {
                byte[] chunk=new byte[8192]; int n; while((n=in.read(chunk))>0) bytes.write(chunk,0,n);
            }
            byte[] body=bytes.toByteArray(); int start=0,end=body.length-1;
            if(range!=null && range.startsWith("bytes=")) {
                String[] parts=range.substring(6).split("-",-1);
                if(!parts[0].isEmpty()) start=Integer.parseInt(parts[0]);
                if(parts.length>1 && !parts[1].isEmpty()) end=Math.min(end,Integer.parseInt(parts[1]));
            }
            String headers="HTTP/1.1 "+(range==null ? "200 OK" : "206 Partial Content")+"\r\nContent-Type: video/mp4\r\nAccept-Ranges: bytes\r\nContent-Length: "+(end-start+1)+"\r\n";
            if(range!=null) headers+="Content-Range: bytes "+start+"-"+end+"/"+body.length+"\r\n";
            out.write((headers+"Connection: close\r\n\r\n").getBytes("UTF-8")); out.write(body,start,end-start+1); out.flush();
        } catch(Exception ignored) {} finally { sockets.remove(socket); }
    }
    private void fail(String reason) { done=true; Log.e("Android5SwitchTest","FAIL "+reason); cleanup(); }
    private void cleanup() {
        running=false; handler.removeCallbacksAndMessages(null);
        try { if(server!=null) server.close(); } catch(Exception ignored) {}
        synchronized(sockets) { for(Socket s:sockets) try { s.close(); } catch(Exception ignored) {} }
        getApplication().unregisterActivityLifecycleCallbacks(this);
    }
    public void onActivityResumed(Activity a) {
        if(!a.getClass().getName().equals("com.dycomment.tv.MainActivity")) return;
        feed=a;
        try { player=(PlayerView)field("videoView"); player.setOnInfoListener((p,w,e) -> { if(w==3) outputs++; return false; }); }
        catch(Exception e) { fail("real feed hook"); }
    }
    public void onActivityCreated(Activity a,Bundle b) {}
    public void onActivityStarted(Activity a) {}
    public void onActivityPaused(Activity a) {}
    public void onActivityStopped(Activity a) {}
    public void onActivitySaveInstanceState(Activity a,Bundle b) {}
    public void onActivityDestroyed(Activity a) {}
    protected void onDestroy() { cleanup(); super.onDestroy(); }
}
