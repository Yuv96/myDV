package com.dycomment.tv;
import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
public final class AboutActivity extends Activity {
    @Override public void onCreate(Bundle b) {
        super.onCreate(b); LinearLayout root=UiTheme.page(this,"抖音抬头版 0.1.0");
        ScrollView scroll=new ScrollView(this);
        scroll.addView(UiTheme.text(this,"在 My DV 的基础上重构开发。\n\n操作说明\n\n上 / 下：切换视频\n确定：暂停或继续\n左 / 右：推荐面板、播放进度及快进快退\n菜单：喜欢、关注、收藏、作者主页、分享\n再次按菜单：打开评论\n返回：关闭面板或打开设置\n\n账号与登录：显示二维码后，用抖音 App 扫描并在手机确认；读取持续失败时重新扫码。\n\n分享：选择好友即发送当前短视频。\n\n反馈与联系\ngithub.com/Yuv96/myDV/issues",20));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); root.addView(UiTheme.button(this,"返回",()->finish())); setContentView(root);
    }
}
