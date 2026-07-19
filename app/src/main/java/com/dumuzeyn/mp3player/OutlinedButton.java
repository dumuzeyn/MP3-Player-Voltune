package com.dumuzeyn.mp3player;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.widget.Button;

/** Button counterpart of OutlinedTextView for a consistent crisp text stroke. */
final class OutlinedButton extends Button {
    private boolean outlineEnabled;
    private int outlineColor;
    private float outlineWidth;

    OutlinedButton(Context context) {
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
        if (!outlineEnabled || TextOutlinePolicy.isInsideCard(this)) {
            super.onDraw(canvas);
            return;
        }
        drawOutlined(canvas);
    }

    @SuppressLint("WrongCall")
    private void drawOutlined(Canvas canvas) {
        ColorStateList originalColors = getTextColors();
        float offset = Math.max(1.0f, outlineWidth * 0.65f);
        setTextColor(outlineColor);
        float[] directions = {-offset, 0.0f, offset};
        for (float dx : directions) {
            for (float dy : directions) {
                if (dx == 0.0f && dy == 0.0f) {
                    continue;
                }
                int saveCount = canvas.save();
                canvas.translate(dx, dy);
                super.onDraw(canvas);
                canvas.restoreToCount(saveCount);
            }
        }
        setTextColor(originalColors);
        super.onDraw(canvas);
    }
}
