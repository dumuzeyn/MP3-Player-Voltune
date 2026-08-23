package com.dumuzeyn.mp3player;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

final class TabsController {
    private final MainActivityCore host;
    private ValueAnimator scrollAnimator;
    private FrameLayout tabTrack;
    private View indicator;
    private Button transitionFrom;
    private Button transitionTo;
    private float transitionFromX;
    private float transitionToX;

    TabsController(MainActivityCore host) {
        this.host = host;
    }

    void buildTabs(LinearLayout page) {
        LinearLayout container = new LinearLayout(host);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(host.uiFactory.lineView(), new LinearLayout.LayoutParams(-1, 1));

        HorizontalScrollView scrollView = new HorizontalScrollView(host);
        host.tabsScroll = scrollView;
        scrollView.setHorizontalScrollBarEnabled(false);

        tabTrack = new FrameLayout(host);
        host.tabRow = new LinearLayout(host);
        host.tabRow.setOrientation(LinearLayout.HORIZONTAL);
        indicator = new View(host);
        GradientDrawable indicatorBackground = new GradientDrawable();
        indicatorBackground.setColor(host.purple);
        indicatorBackground.setCornerRadius(host.dp(14));
        indicator.setBackground(indicatorBackground);
        tabTrack.addView(indicator, new FrameLayout.LayoutParams(host.dp(132), host.dp(48)));
        tabTrack.addView(host.tabRow, new FrameLayout.LayoutParams(-2, host.dp(48)));
        scrollView.addView(tabTrack, new HorizontalScrollView.LayoutParams(-2, host.dp(48)));
        container.addView(scrollView, new LinearLayout.LayoutParams(-1, host.dp(48)));
        container.addView(host.uiFactory.lineView(), new LinearLayout.LayoutParams(-1, 1));
        page.addView(container, new LinearLayout.LayoutParams(-1, host.dp(50)));

        addTabButtons();
        scrollView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (right <= left || host.tabRow.getWidth() <= 0) {
                    return;
                }
                view.removeOnLayoutChangeListener(this);
                scrollView.post(() -> {
                    attachInfiniteScrollLoop();
                    positionIndicatorToActive();
                });
            }
        });
    }

    void refreshTabs() {
        if (host.tabRow == null) {
            return;
        }
        for (int i = 0; i < host.tabRow.getChildCount(); i++) {
            View child = host.tabRow.getChildAt(i);
            if ((child instanceof Button) && (child.getTag() instanceof Integer)) {
                styleTab((Button) child, ((Integer) child.getTag()).intValue());
            }
        }
        if (transitionFrom == null) {
            positionIndicatorToActive();
        }
    }

    void beginTransition(int fromIndex, int targetIndex, int direction) {
        transitionFrom = findNearestButton(fromIndex);
        transitionTo = findDirectionalButton(targetIndex, transitionFrom, direction);
        if (transitionFrom == null || transitionTo == null || indicator == null) {
            return;
        }
        transitionFromX = transitionFrom.getLeft();
        transitionToX = transitionTo.getLeft();
        indicator.setTranslationX(transitionFromX);
        setTransitionProgress(0.0f);
    }

    void setTransitionProgress(float progress) {
        float bounded = Math.max(0.0f, Math.min(1.0f, progress));
        if (indicator != null && transitionFrom != null && transitionTo != null) {
            indicator.setTranslationX(transitionFromX + ((transitionToX - transitionFromX) * bounded));
            transitionFrom.setTextColor(ThemeManager.mixColor(host.secondaryText, Color.WHITE, bounded));
            transitionTo.setTextColor(ThemeManager.mixColor(Color.WHITE, host.secondaryText, bounded));
        }
    }

    void finishTransition(int targetIndex) {
        transitionFrom = null;
        transitionTo = null;
        refreshTabs();
        scrollToActive(true, targetIndex);
        if (host.tabsScroll != null) {
            host.tabsScroll.post(this::positionIndicatorToActive);
        }
    }

    void cancelTransition() {
        setTransitionProgress(0.0f);
        transitionFrom = null;
        transitionTo = null;
        refreshTabs();
    }

    int directionTo(int targetIndex) {
        if (host.tabs == null || host.tabs.length == 0 || targetIndex == host.navigationState.tabIndex) {
            return 1;
        }
        int length = host.tabs.length;
        int forward = (targetIndex - host.navigationState.tabIndex + length) % length;
        int backward = (host.navigationState.tabIndex - targetIndex + length) % length;
        return forward <= backward ? 1 : -1;
    }

    boolean isInsideTabs(MotionEvent event) {
        if (host.tabsScroll == null) {
            return false;
        }
        int[] location = new int[2];
        host.tabsScroll.getLocationOnScreen(location);
        float rawX = event.getRawX();
        float rawY = event.getRawY();
        return rawX >= location[0]
                && rawX <= location[0] + host.tabsScroll.getWidth()
                && rawY >= location[1]
                && rawY <= location[1] + host.tabsScroll.getHeight();
    }

    void scrollToActive(boolean smooth, int targetIndex) {
        if (host.tabsScroll == null || host.tabRow == null || host.tabs == null || host.tabs.length == 0) {
            return;
        }
        final boolean smoothScroll = smooth;
        final int requestedIndex = targetIndex;
        host.tabsScroll.post(new Runnable() {
            @Override
            public void run() {
                scrollToActiveNow(smoothScroll, requestedIndex);
            }
        });
    }

    void cancelScrollAnimation() {
        if (scrollAnimator != null) {
            scrollAnimator.cancel();
        }
    }

    private void addTabButtons() {
        for (int cycle = 0; cycle < MainActivityCore.TAB_CYCLES; cycle++) {
            for (int index = 0; index < host.tabs.length; index++) {
                final int tabIndex = index;
                Button button = host.uiFactory.button(host.tabs[index]);
                button.setTag(Integer.valueOf(index));
                styleTab(button, index);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        host.switchTabAnimated(tabIndex, directionTo(tabIndex));
                    }
                });
                host.tabRow.addView(button, new LinearLayout.LayoutParams(host.dp(132), host.dp(48)));
            }
        }
    }

    private void attachInfiniteScrollLoop() {
        int cycleWidth = Math.max(1, host.tabRow.getWidth() / MainActivityCore.TAB_CYCLES);
        scrollToActiveNow(false, host.navigationState.tabIndex);
        positionIndicatorToActive();
        host.tabsScroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View view, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                keepScrollInsideMiddleCycles(cycleWidth, scrollX);
                if (transitionFrom == null) {
                    positionIndicatorToActive();
                }
            }
        });
    }

    private void keepScrollInsideMiddleCycles(int cycleWidth, int scrollX) {
        int middleCycle = MainActivityCore.TAB_CYCLES / 2;
        int leftBoundary = cycleWidth * Math.max(0, middleCycle - 1);
        int rightBoundary = cycleWidth * Math.min(MainActivityCore.TAB_CYCLES - 1, middleCycle + 1);
        if (scrollX < leftBoundary) {
            host.tabsScroll.scrollTo(scrollX + cycleWidth, 0);
        } else if (scrollX > rightBoundary) {
            host.tabsScroll.scrollTo(scrollX - cycleWidth, 0);
        }
    }

    private void styleTab(Button button, int index) {
        button.setTextSize(15.0f);
        button.setGravity(17);
        button.setPadding(host.dp(14), 0, host.dp(14), 0);

        if (button instanceof OutlinedButton) {
            ((OutlinedButton) button).setTextOutline(false, Color.TRANSPARENT, 1.0f);
        }

        button.setBackgroundColor(Color.TRANSPARENT);
        button.setTextColor(index == host.navigationState.tabIndex ? Color.WHITE : host.secondaryText);
    }

    private void positionIndicatorToActive() {
        Button active = findNearestButton(host.navigationState.tabIndex);
        if (active != null && indicator != null) {
            indicator.setTranslationX(active.getLeft());
        }
    }

    private Button findNearestButton(int tabIndex) {
        if (host.tabsScroll == null || host.tabRow == null) {
            return null;
        }
        int center = host.tabsScroll.getScrollX() + (host.tabsScroll.getWidth() / 2);
        Button closest = null;
        int distance = Integer.MAX_VALUE;
        for (int i = 0; i < host.tabRow.getChildCount(); i++) {
            View child = host.tabRow.getChildAt(i);
            if (!(child instanceof Button) || !(child.getTag() instanceof Integer)
                    || ((Integer) child.getTag()).intValue() != tabIndex) {
                continue;
            }
            int candidateDistance = Math.abs((child.getLeft() + child.getWidth() / 2) - center);
            if (candidateDistance < distance) {
                distance = candidateDistance;
                closest = (Button) child;
            }
        }
        return closest;
    }

    private Button findDirectionalButton(int tabIndex, Button from, int direction) {
        if (from == null) {
            return findNearestButton(tabIndex);
        }
        Button closest = null;
        int distance = Integer.MAX_VALUE;
        for (int i = 0; i < host.tabRow.getChildCount(); i++) {
            View child = host.tabRow.getChildAt(i);
            if (!(child instanceof Button) || !(child.getTag() instanceof Integer)
                    || ((Integer) child.getTag()).intValue() != tabIndex) {
                continue;
            }
            int delta = child.getLeft() - from.getLeft();
            if ((direction > 0 && delta <= 0) || (direction < 0 && delta >= 0)) {
                continue;
            }
            if (Math.abs(delta) < distance) {
                distance = Math.abs(delta);
                closest = (Button) child;
            }
        }
        return closest == null ? findNearestButton(tabIndex) : closest;
    }

    private void scrollToActiveNow(boolean smooth, int targetIndex) {
        int boundedIndex = Math.max(0, Math.min(targetIndex, host.tabs.length - 1));
        int left = -1;
        if (smooth) {
            left = findSmoothTargetLeft(boundedIndex);
        }
        if (left < 0) {
            left = centeredCycleTargetLeft(boundedIndex);
        }
        if (left < 0) {
            return;
        }
        if (smooth) {
            animateScrollTo(left);
        } else {
            host.tabsScroll.scrollTo(left, 0);
        }
    }

    private int findSmoothTargetLeft(int targetIndex) {
        int scrollCenter = host.tabsScroll.getScrollX() + (host.tabsScroll.getWidth() / 2);
        int closestDistance = Integer.MAX_VALUE;
        int left = -1;
        for (int i = 0; i < host.tabRow.getChildCount(); i++) {
            View child = host.tabRow.getChildAt(i);
            Object tag = child.getTag();
            if (!(tag instanceof Integer) || ((Integer) tag).intValue() != targetIndex) {
                continue;
            }
            int childCenter = child.getLeft() + (child.getWidth() / 2);
            int childLeft = child.getLeft() - Math.max(0, (host.tabsScroll.getWidth() - child.getWidth()) / 2);
            if ((host.navigationState.preferredTabDirection > 0 && childCenter < scrollCenter)
                    || (host.navigationState.preferredTabDirection < 0 && childCenter > scrollCenter)) {
                continue;
            }
            int distance = Math.abs(childCenter - scrollCenter);
            if (distance < closestDistance) {
                closestDistance = distance;
                left = childLeft;
            }
        }
        return left;
    }

    private int centeredCycleTargetLeft(int targetIndex) {
        int childIndex = (host.tabs.length * (MainActivityCore.TAB_CYCLES / 2)) + targetIndex;
        if (childIndex >= host.tabRow.getChildCount()) {
            return -1;
        }
        View child = host.tabRow.getChildAt(childIndex);
        return child.getLeft() - Math.max(0, (host.tabsScroll.getWidth() - child.getWidth()) / 2);
    }

    private void animateScrollTo(int left) {
        if (host.tabsScroll == null) {
            return;
        }
        cancelScrollAnimation();
        int scrollX = host.tabsScroll.getScrollX();
        if (Math.abs(left - scrollX) < 2 || !host.appearanceState.animations) {
            host.tabsScroll.scrollTo(left, 0);
            return;
        }
        scrollAnimator = ValueAnimator.ofInt(scrollX, left);
        scrollAnimator.setDuration(96L);
        scrollAnimator.setInterpolator(new DecelerateInterpolator());
        scrollAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                if (host.tabsScroll != null) {
                    host.tabsScroll.scrollTo(((Integer) valueAnimator.getAnimatedValue()).intValue(), 0);
                }
            }
        });
        scrollAnimator.start();
    }
}
