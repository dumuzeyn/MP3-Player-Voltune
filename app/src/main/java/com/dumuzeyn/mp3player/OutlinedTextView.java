package com.dumuzeyn.mp3player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.view.Gravity;
import android.widget.TextView;

/** Draws a crisp text stroke without the blur produced by a shadow layer. */
final class OutlinedTextView extends TextView {
    private boolean outlineEnabled;
    private int outlineColor;
    private float outlineWidth;

    OutlinedTextView(Context context) {
        super(context);
    }

    void setTextOutline(boolean enabled, int color, float width) {
        outlineEnabled = enabled;
        outlineColor = color;
        outlineWidth = Math.max(1.0f, width);
        setShadowLayer(0.0f, 0.0f, 0.0f, 0);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (outlineEnabled && getLayout() != null && !TextOutlinePolicy.isInsideCard(this)) {
            drawOutline(canvas, getLayout());
        }
        super.onDraw(canvas);
    }

    private void drawOutline(Canvas canvas, Layout layout) {
        Paint paint = getPaint();
        Paint.Style previousStyle = paint.getStyle();
        float previousWidth = paint.getStrokeWidth();
        Paint.Join previousJoin = paint.getStrokeJoin();
        int previousColor = paint.getColor();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(outlineWidth);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(outlineColor);

        int availableHeight = getHeight() - getCompoundPaddingTop() - getCompoundPaddingBottom();
        int verticalOffset = 0;
        int verticalGravity = getGravity() & Gravity.VERTICAL_GRAVITY_MASK;
        if (verticalGravity == Gravity.BOTTOM) {
            verticalOffset = availableHeight - layout.getHeight();
        } else if (verticalGravity == Gravity.CENTER_VERTICAL) {
            verticalOffset = (availableHeight - layout.getHeight()) / 2;
        }
        verticalOffset = Math.max(0, verticalOffset);

        int saveCount = canvas.save();
        canvas.translate(
                getCompoundPaddingLeft() - getScrollX(),
                getExtendedPaddingTop() + verticalOffset - getScrollY());
        layout.draw(canvas);
        canvas.restoreToCount(saveCount);

        paint.setStyle(previousStyle);
        paint.setStrokeWidth(previousWidth);
        paint.setStrokeJoin(previousJoin);
        paint.setColor(previousColor);
    }
}
