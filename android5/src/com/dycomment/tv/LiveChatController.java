package com.dycomment.tv;

import android.app.Activity;
import android.os.*;
import android.view.View;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Read-only live chat. No chat send, gifts, likes, or account writes. */
public final class LiveChatController {
    private static final int TAG=0x7f0f7a54;
    final Activity activity; final Handler main=new Handler(Looper.getMainLooper());
    final ThreadPoolExecutor work=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(1),new ThreadPoolExecutor.DiscardOldestPolicy());
    final LinkedHashSet<String> seen=new LinkedHashSet<>();
    String room="",cursor="",session="",uid=""; volatile int epoch; int empty; volatile boolean running; boolean closed;
    LiveChatController(Activity a) { activity=a; }
    static LiveChatController get(Activity a) {
        View decor=a.getWindow().getDecorView(); Object old=decor.getTag(TAG);
        if(old instanceof LiveChatController) return (LiveChatController)old;
        LiveChatController c=new LiveChatController(a); decor.setTag(TAG,c); return c;
    }
    public static void start(Activity a,String id) {
        LiveChatController c=get(a); stop(a); if(id==null || !id.matches("[0-9]+") || c.closed) return;
        c.room=id; c.session=SocialApi.cookie(); c.running=true; c.poll();
    }
    public static void stop(Activity a) {
        LiveChatController c=get(a); c.epoch++; c.running=false; c.cursor=""; c.uid=""; c.empty=0; c.seen.clear(); c.work.getQueue().clear(); c.main.removeCallbacksAndMessages(null);
    }
    public static void destroy(Activity a) { stop(a); LiveChatController c=get(a); c.closed=true; c.work.shutdownNow(); }
    boolean valid(int token) { return running && epoch==token && !activity.isFinishing() && !activity.isDestroyed() && session.equals(SocialApi.cookie()); }
    static final class Chat { String id,text; }
    static List<Chat> chats(Wire response) throws Exception {
        List<Chat> chats=new ArrayList<>();
        for(Object raw:response.all(1)) {
            if(!(raw instanceof byte[])) continue;
            Wire message=new Wire((byte[])raw);
            if(!message.text(1).equals("WebcastChatMessage")) continue;
            Wire chat=message.child(2); String content=chat.text(3); if(content.isEmpty()) continue;
            Chat out=new Chat(); out.id=Long.toString(message.number(3,chat.child(1).number(2,0)));
            String author=chat.child(2).text(3); out.text=(author.isEmpty() ? "" : author+"：")+content;
            if(out.text.length()>200) out.text=out.text.substring(0,200);
            if(out.id.equals("0")) out.id=out.text;
            chats.add(out); if(chats.size()>=30) break;
        }
        return chats;
    }
    void poll() {
        final int token=epoch; final String roomId=room,cookie=session,previous=cursor,user=uid;
        work.execute(() -> {
            if(!running || token!=epoch) return;
            String next=previous,account=user; List<Chat> chats=new ArrayList<>(); boolean ok=false;
            try {
                if(account.isEmpty()) {
                    org.json.JSONObject self=SocialApi.request("/aweme/v1/web/user/profile/self/",SocialApi.params(),false,cookie).optJSONObject("user");
                    if(self==null) throw new Exception(); account=self.optString("uid","");
                }
                String query=SocialApi.encode(SocialApi.params("aid","6383","app_name","douyin_web","version_code","180800",
                    "webcast_sdk_version","1.3.0","room_id",roomId,"user_unique_id",account,"live_id","1","device_platform","web",
                    "identity","audience","resp_content_type","protobuf","cursor",previous));
                HttpURLConnection c=(HttpURLConnection)new URL("https://live.douyin.com/webcast/im/fetch/?"+query).openConnection();
                c.setInstanceFollowRedirects(false); c.setConnectTimeout(5000); c.setReadTimeout(5000);
                c.setRequestProperty("Cookie",cookie); c.setRequestProperty("User-Agent",SocialApi.UA); c.setRequestProperty("Referer","https://live.douyin.com/");
                try {
                    if(c.getResponseCode()!=200) throw new Exception();
                    Wire response; try(InputStream in=c.getInputStream()) { response=new Wire(QuickShareApi.read(in)); }
                    if(response.first(2)==null) throw new Exception(); next=response.text(2); chats=chats(response); ok=true;
                } finally { c.disconnect(); }
            } catch(Exception ignored) {}
            final List<Chat> result=chats; final String nextCursor=next,knownUser=account; final boolean success=ok;
            main.post(() -> {
                if(!valid(token)) return; cursor=nextCursor; uid=knownUser;
                if(!success || result.isEmpty()) empty++; else empty=0;
                for(Chat chat:result) if(seen.add(chat.id)) {
                    if(seen.size()>500) seen.remove(seen.iterator().next());
                    try { Object view=InteractionController.field(activity,"danmakuView"); InteractionController.call(view,"addDanmaku",new Class<?>[]{String.class},chat.text); }
                    catch(Exception ignored) {}
                }
                if(empty==3) android.widget.Toast.makeText(activity,success ? "暂未收到直播弹幕" : "直播弹幕暂不可用",android.widget.Toast.LENGTH_SHORT).show();
                main.postDelayed(() -> { if(valid(token)) poll(); },success ? 2500 : 10000);
            });
        });
    }
}
