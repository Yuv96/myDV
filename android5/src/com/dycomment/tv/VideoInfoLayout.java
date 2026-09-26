package com.dycomment.tv;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

/** Content-sized metadata with a ceiling, never a fixed-width strip over the video. */
public final class VideoInfoLayout extends LinearLayout {
    public VideoInfoLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int available = MeasureSpec.getSize(widthSpec);
        if (MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED) {
            float density = getResources().getDisplayMetrics().density;
            // Small windows need the available width; TVs leave the right side unobscured.
            int ceiling = available < 600 * density ? available : Math.round(available * .72f);
            ceiling = Math.min(ceiling, Math.round(720 * density));
            widthSpec = MeasureSpec.makeMeasureSpec(ceiling, MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthSpec, heightSpec);
    }
}
