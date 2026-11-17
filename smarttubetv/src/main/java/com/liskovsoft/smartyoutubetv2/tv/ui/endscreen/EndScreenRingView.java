package com.liskovsoft.smartyoutubetv2.tv.ui.endscreen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * leanview: the countdown of the "Up next" end screen: a ring that empties as the seconds go by, with
 * the seconds left in the middle. Drawn in code (no assets), white on a dark disc.
 */
public class EndScreenRingView extends View {
    private final Paint mDisc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mArc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mBounds = new RectF();
    private float mProgress = 1f; // 1 = full ring, 0 = empty
    private String mLabel = "";

    public EndScreenRingView(Context context) {
        this(context, null);
    }

    public EndScreenRingView(Context context, AttributeSet attrs) {
        super(context, attrs);

        float density = getResources().getDisplayMetrics().density;
        float stroke = 4 * density;

        mDisc.setStyle(Paint.Style.FILL);
        mDisc.setColor(0xB3000000);

        mTrack.setStyle(Paint.Style.STROKE);
        mTrack.setStrokeWidth(stroke);
        mTrack.setColor(0x55FFFFFF);

        mArc.setStyle(Paint.Style.STROKE);
        mArc.setStrokeWidth(stroke);
        mArc.setStrokeCap(Paint.Cap.ROUND);
        mArc.setColor(0xFFFFFFFF);

        mText.setColor(0xFFFFFFFF);
        mText.setTextAlign(Paint.Align.CENTER);
        mText.setFakeBoldText(true);
        mText.setTextSize(24 * getResources().getDisplayMetrics().scaledDensity);
    }

    /** The middle shows secondsLeft; the ring is secondsLeft / totalSeconds full. */
    public void setSeconds(int secondsLeft, int totalSeconds) {
        float progress = totalSeconds > 0 ? Math.max(0f, Math.min(1f, secondsLeft / (float) totalSeconds)) : 0f;
        String label = String.valueOf(Math.max(0, secondsLeft));

        if (progress != mProgress || !label.equals(mLabel)) {
            mProgress = progress;
            mLabel = label;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float inset = mArc.getStrokeWidth() / 2f + 2;

        mBounds.set(inset, inset, getWidth() - inset, getHeight() - inset);

        canvas.drawCircle(cx, cy, Math.min(cx, cy), mDisc);
        canvas.drawOval(mBounds, mTrack);
        canvas.drawArc(mBounds, -90f, 360f * mProgress, false, mArc);
        canvas.drawText(mLabel, cx, cy - (mText.descent() + mText.ascent()) / 2f, mText);
    }
}
