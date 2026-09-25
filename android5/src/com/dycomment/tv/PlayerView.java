package com.dycomment.tv;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.interfaces.IVLCVout;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

/** API-21 player. Original callback descriptors are retained for the Lite UI. */
public class PlayerView extends FrameLayout implements IVLCVout.OnNewVideoLayoutListener {
    public static final int STATE_IDLE=0, STATE_PREPARING=1, STATE_PREPARED=2,
        STATE_PLAYING=3, STATE_PAUSED=4, STATE_COMPLETED=5, STATE_ERROR=6;
    public interface OnPreparedListener { void onPrepared(MediaPlayer p); }
    public interface OnCompletionListener { void onCompletion(MediaPlayer p); }
    public interface OnErrorListener { boolean onError(MediaPlayer p, int what, int extra); }
    public interface OnInfoListener { boolean onInfo(MediaPlayer p, int what, int extra); }
    public interface OnBufferingListener { void onBuffering(int percent); }
    private static LibVLC engine;
    private final SurfaceView surface;
    private final NextVideoCache cache;
    private org.videolan.libvlc.MediaPlayer player;
    private Uri pending;
    private int state, buffer, generation;
    private boolean wantPlay, prepared, completed;
    private OnPreparedListener onPrepared;
    private OnCompletionListener onCompletion;
    private OnErrorListener onError;
    private OnInfoListener onInfo;
    private OnBufferingListener onBuffering;
    private long openedAt;
    private int videoWidth, videoHeight, sarNum=1, sarDen=1;

    public PlayerView(Context c) { this(c,null); }
    public PlayerView(Context c, AttributeSet a) { this(c,a,0); }
    public PlayerView(Context c, AttributeSet a, int style) {
        super(c,a,style);
        setBackgroundColor(0xff000000);
        setFocusable(true); setFocusableInTouchMode(true);
        surface=new SurfaceView(c);
        addView(surface,new LayoutParams(-1,-1,Gravity.CENTER));
        cache=new NextVideoCache(c);
    }
    private synchronized LibVLC engine() {
        if (engine==null) {
            engine=new LibVLC(getContext(),new ArrayList<String>(Arrays.asList(
                "--avcodec-hw=none", "--network-caching=1000", "--file-caching=200",
                "--no-video-title-show", "--no-snapshot-preview")));
            engine.setUserAgent("myDV Android5",NextVideoCache.USER_AGENT);
        }
        return engine;
    }
    public void setVideoURI(Uri uri) { setVideoURI(uri,null); }
    public void setVideoURI(Uri uri, Map<String,String> headers) {
        releasePlayer();
        pending=uri; prepared=false; completed=false; wantPlay=true; buffer=0;
        state=STATE_PREPARING; openedAt=android.os.SystemClock.elapsedRealtime();
        cache.onSelection();
        final int token=generation;
        try {
            File cached=cache.lookup(NextVideoCache.originalUrl(uri.toString()));
            Uri source=cached==null ? uri : Uri.fromFile(cached);
            player=new org.videolan.libvlc.MediaPlayer(engine());
            IVLCVout vout=player.getVLCVout();
            vout.setVideoView(surface);
            vout.attachViews(this);
            if(getWidth()>0 && getHeight()>0) vout.setWindowSize(getWidth(),getHeight());
            player.setEventListener(event -> { if(token==generation) handleEvent(event); });
            Media media=new Media(engine(),source);
            media.setHWDecoderEnabled(false,false);
            media.addOption(":avcodec-hw=none");
            String ref=headers==null ? "https://www.douyin.com/" : headers.get("Referer");
            if(ref!=null) media.addOption(":http-referrer="+ref.replace("\r","").replace("\n",""));
            player.setMedia(media);
            media.release();
            setKeepScreenOn(true);
            player.play();
            Log.i("Android5Player","OPEN decoder=software cache="+(cached!=null));
        } catch(Exception | LinkageError e) {
            Log.e("Android5Player","OPEN_FAILED "+e.getClass().getSimpleName());
            state=STATE_ERROR;
            if(onError!=null) onError.onError(null,1,-1);
        }
    }
    private void handleEvent(org.videolan.libvlc.MediaPlayer.Event e) {
        switch(e.type) {
        case org.videolan.libvlc.MediaPlayer.Event.Playing:
            state=STATE_PLAYING;
            if(!prepared) {
                prepared=true;
                setPlaybackSpeed(getContext().getSharedPreferences("dy_config",0).getFloat("playback_speed",1f));
                if(onPrepared!=null) onPrepared.onPrepared(null);
                Log.i("Android5Player","PLAYING startup_ms="+(android.os.SystemClock.elapsedRealtime()-openedAt));
            }
            if(!wantPlay) pause(); else cache.scheduleNext(getContext());
            break;
        case org.videolan.libvlc.MediaPlayer.Event.Vout:
            if(e.getVoutCount()>0) {
                Log.i("Android5Player","VIDEO_OUTPUT count="+e.getVoutCount());
                if(onInfo!=null) onInfo.onInfo(null,3,0);
            }
            break;
        case org.videolan.libvlc.MediaPlayer.Event.Buffering:
            buffer=Math.round(e.getBuffering());
            if(onBuffering!=null) onBuffering.onBuffering(buffer);
            if(onInfo!=null) onInfo.onInfo(null,buffer<100 ? 701 : 702,0);
            if(prepared && buffer<100) cache.suspend();
            else if(prepared && wantPlay) cache.scheduleNext(getContext());
            break;
        case org.videolan.libvlc.MediaPlayer.Event.EndReached:
            if(!completed) {
                completed=true; state=STATE_COMPLETED; setKeepScreenOn(false);
                if(onCompletion!=null) onCompletion.onCompletion(null);
            }
            break;
        case org.videolan.libvlc.MediaPlayer.Event.EncounteredError:
            state=STATE_ERROR; setKeepScreenOn(false); cache.suspend();
            Log.e("Android5Player","DECODE_OR_NETWORK_ERROR");
            if(onError!=null) onError.onError(null,1,-1);
            break;
        }
    }
    public void setPlaybackSpeed(float rate) { if(player!=null) player.setRate(Math.max(0.25f,Math.min(3f,rate))); }
    public void start() {
        wantPlay=true;
        if(player==null) { if(pending!=null) setVideoURI(pending); return; }
        if(state==STATE_COMPLETED) { setVideoURI(pending); return; }
        if(state==STATE_PAUSED || state==STATE_PREPARED) { player.play(); state=STATE_PLAYING; setKeepScreenOn(true); }
    }
    public void pause() { wantPlay=false; if(player!=null && prepared) { player.pause(); state=STATE_PAUSED; } setKeepScreenOn(false); cache.suspend(); }
    public void stopPlayback() { pending=null; wantPlay=false; releasePlayer(); state=STATE_IDLE; cache.onSelection(); }
    public void resumeIfNeeded() { start(); }
    public void seekTo(int ms) { if(player!=null && prepared) player.setTime(Math.max(0,ms)); }
    public int getCurrentPosition() { return player==null ? 0 : (int)Math.max(0,player.getTime()); }
    public int getDuration() { return player==null ? 0 : (int)Math.max(0,player.getLength()); }
    public boolean isPlaying() { return player!=null && player.isPlaying(); }
    public int getBufferPercent() { return buffer; }
    public int getPlayState() { return state; }
    public void resetCompletion() { completed=false; }
    public void setOnPreparedListener(OnPreparedListener l) { onPrepared=l; }
    public void setOnCompletionListener(OnCompletionListener l) { onCompletion=l; }
    public void setOnErrorListener(OnErrorListener l) { onError=l; }
    public void setOnInfoListener(OnInfoListener l) { onInfo=l; }
    public void setOnBufferingListener(OnBufferingListener l) { onBuffering=l; }
    private void releasePlayer() {
        generation++;
        if(player!=null) {
            player.setEventListener(null);
            player.getVLCVout().detachViews();
            player.release(); player=null;
        }
        setKeepScreenOn(false);
    }
    public void onNewVideoLayout(IVLCVout v,int w,int h,int visibleW,int visibleH,int sn,int sd) {
        videoWidth=visibleW>0 ? visibleW : w; videoHeight=visibleH>0 ? visibleH : h;
        sarNum=sn>0 ? sn : 1; sarDen=sd>0 ? sd : 1;
        fitVideo();
    }
    protected void onSizeChanged(int w,int h,int oldW,int oldH) { super.onSizeChanged(w,h,oldW,oldH); fitVideo(); }
    private void fitVideo() {
        int w=getWidth(), h=getHeight();
        if(w<=0 || h<=0 || videoWidth<=0 || videoHeight<=0) return;
        if(player!=null) player.getVLCVout().setWindowSize(w,h);
        float ratio=(float)videoWidth*sarNum/(videoHeight*sarDen);
        if((float)w/h>ratio) w=Math.round(h*ratio); else h=Math.round(w/ratio);
        surface.setLayoutParams(new LayoutParams(w,h,Gravity.CENTER));
    }
    protected void onDetachedFromWindow() { releasePlayer(); cache.close(); super.onDetachedFromWindow(); }
}
