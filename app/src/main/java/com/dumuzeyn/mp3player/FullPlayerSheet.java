package com.dumuzeyn.mp3player;

import android.view.MotionEvent;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/** Swipe-to-dismiss surface used by the full player. */
final class FullPlayerSheet extends FrameLayout {
    interface CloseListener {
        void close(FrameLayout sheet);
    }

    private final MainActivityCore host;
    private final CloseListener closeListener;
    private boolean draggingDown;
    private boolean closingDown;
    private float startX;
    private float startY;
    private float startTranslationY;

    FullPlayerSheet(MainActivityCore host, CloseListener closeListener) {
        super(host);
        this.host = host;
        this.closeListener = closeListener;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            beginGesture(event);
            super.dispatchTouchEvent(event);
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            return moveGesture(event);
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            return finishGesture(event, action);
        }
        super.dispatchTouchEvent(event);
        return true;
    }

    private void beginGesture(MotionEvent event) {
        draggingDown = false;
        closingDown = false;
        startX = event.getRawX();
        startY = event.getRawY();
        startTranslationY = getTranslationY();
        animate().cancel();
        setAlpha(1.0f);
    }

    private boolean moveGesture(MotionEvent event) {
        if (closingDown) {
            return true;
        }
        float dx = event.getRawX() - startX;
        float dy = event.getRawY() - startY;
        if (!draggingDown && dy > host.dp(8) && dy > Math.abs(dx) * 0.75f) {
            draggingDown = true;
            MotionEvent cancelEvent = MotionEvent.obtain(event);
            cancelEvent.setAction(MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancelEvent);
            cancelEvent.recycle();
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        if (draggingDown) {
            float drag = Math.max(0.0f, startTranslationY + dy);
            setTranslationY(drag);
            setAlpha(Math.max(0.55f, 1.0f - drag / Math.max(1, getHeight())));
        } else {
            super.dispatchTouchEvent(event);
        }
        return true;
    }

    private boolean finishGesture(MotionEvent event, int action) {
        if (closingDown) {
            return true;
        }
        if (!draggingDown) {
            super.dispatchTouchEvent(event);
            return true;
        }
        draggingDown = false;
        float drag = Math.max(0.0f, startTranslationY + event.getRawY() - startY);
        if (action == MotionEvent.ACTION_UP && drag > host.dp(56)) {
            closingDown = true;
            closeListener.close(this);
        } else if (host.appearanceState.animations) {
            animate().translationY(0.0f).alpha(1.0f).setDuration(120L)
                    .setInterpolator(new DecelerateInterpolator()).start();
        } else {
            setTranslationY(0.0f);
            setAlpha(1.0f);
        }
        return true;
    }
}
