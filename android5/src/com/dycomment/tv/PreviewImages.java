package com.dycomment.tv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.*;

/** Small, recycled list thumbnails; no credentials are sent to image hosts. */
final class PreviewImages {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor work=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,
        new ArrayBlockingQueue<Runnable>(24),new ThreadPoolExecutor.DiscardPolicy());
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(2*1024*1024) {
        protected int sizeOf(String key,Bitmap image) { return image.getByteCount(); }
    };
    private volatile int epoch;
    private volatile boolean closed;
    void bind(ImageView view,String url) {
        if(closed) return;
        view.setTag(url); view.setImageBitmap(null); view.setBackgroundColor(0x26ffffff);
        if(url==null || url.isEmpty()) return;
        Bitmap cached=cache.get(url);
        if(cached!=null) { view.setImageBitmap(cached); return; }
        final int token=epoch;
        final java.lang.ref.WeakReference<ImageView> target=new java.lang.ref.WeakReference<>(view);
        work.execute(() -> {
            if(closed || token!=epoch) return;
            Bitmap bitmap=null; HttpURLConnection c=null;
            try {
                c=(HttpURLConnection)new URL(url).openConnection(); c.setConnectTimeout(4000); c.setReadTimeout(4000);
                c.setInstanceFollowRedirects(false); c.setRequestProperty("User-Agent",SocialApi.UA);
                if(c.getResponseCode()!=200 || c.getContentLength()>2*1024*1024) return;
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                try(InputStream in=c.getInputStream()) {
                    byte[] buffer=new byte[8192]; int n; long deadline=android.os.SystemClock.elapsedRealtime()+6000;
                    while((n=in.read(buffer))!=-1) {
                        if(bytes.size()+n>2*1024*1024 || android.os.SystemClock.elapsedRealtime()>deadline) return;
                        bytes.write(buffer,0,n);
                    }
                }
                byte[] data=bytes.toByteArray(); BitmapFactory.Options opts=new BitmapFactory.Options(); opts.inJustDecodeBounds=true;
                BitmapFactory.decodeByteArray(data,0,data.length,opts);
                if(opts.outWidth<=0 || opts.outHeight<=0) return;
                opts.inSampleSize=1;
                while(opts.outWidth/opts.inSampleSize>320 || opts.outHeight/opts.inSampleSize>180) opts.inSampleSize*=2;
                opts.inJustDecodeBounds=false; opts.inPreferredConfig=Bitmap.Config.RGB_565;
                bitmap=BitmapFactory.decodeByteArray(data,0,data.length,opts);
            } catch(Exception ignored) {} finally { if(c!=null) c.disconnect(); }
            final Bitmap result=bitmap;
            main.post(() -> {
                if(token!=epoch || closed || result==null) return;
                ImageView image=target.get();
                cache.put(url,result);
                if(image!=null && url.equals(image.getTag())) image.setImageBitmap(result);
            });
        });
    }
    void clear() { epoch++; work.getQueue().clear(); cache.evictAll(); }
    void close() { closed=true; clear(); work.shutdownNow(); main.removeCallbacksAndMessages(null); }
}
