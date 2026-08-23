package com.dumuzeyn.mp3player;

import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

final class SwipeController {
    private static final int SWIPE_START_DP = 18;
    private static final int SWIPE_COMMIT_DP = 52;

    private final MainActivityCore host;
    private float startX;
    private float startY;
    private float currentOffset;
    private boolean startedOnTabs;
    private boolean startedOnMiniPlayer;
    private boolean consuming;
    private ScrollView previewScroll;
    private LinearLayout previewList;
    private View currentSurface;
    private View previewSurface;
    private boolean previewUsesSongsSurface;
    private MainRenderer.PreviewState previewState;
    private int targetIndex = -1;
    private int direction;
    private int width;
    private int transitionDistance;
    private ValueAnimator transitionAnimator;
    private boolean recordHistory = true;
    private String targetSearch = "";

    SwipeController(MainActivityCore host) {
        this.host = host;
    }

    boolean handle(MotionEvent event) {
        if (host.tabs == null || host.tabs.length == 0 || (host.navigationState.tabAnimating && !consuming)) {
            return false;
        }
        if (host.overlayHost != null && host.overlayHost.getChildCount() > 0) {
            return false;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            startedOnTabs = host.tabsController.isInsideTabs(event);
            startedOnMiniPlayer = host.playerUiController.isInsideMiniPlayer(event);
            consuming = false;
            startX = event.getX();
            startY = event.getY();
            currentOffset = 0.0f;
            host.tabsController.cancelScrollAnimation();
            return false;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (startedOnTabs || startedOnMiniPlayer) {
                return false;
            }
            float deltaX = event.getX() - startX;
            float deltaY = event.getY() - startY;
            if (!consuming && Math.abs(deltaX) > host.dp(SWIPE_START_DP)
                    && Math.abs(deltaX) > Math.abs(deltaY)) {
                consuming = true;
                if (host.root != null && host.root.getParent() != null) {
                    host.root.getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (host.appearanceState.animations) {
                    prepareAdjacentTransition(deltaX < 0.0f ? 1 : -1);
                }
            }
            if (!consuming) {
                return false;
            }
            if (host.appearanceState.animations) {
                int requestedDirection = deltaX < 0.0f ? 1 : -1;
                if (requestedDirection != direction && Math.abs(deltaX) > host.dp(36)) {
                    cleanupPreview();
                    prepareAdjacentTransition(requestedDirection);
                }
                updateOffset(clampOffset(deltaX));
            }
            return true;
        }
        if (action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL) {
            return false;
        }
        if (startedOnTabs || startedOnMiniPlayer) {
            startedOnTabs = false;
            startedOnMiniPlayer = false;
            return false;
        }
        if (!consuming) {
            return false;
        }
        consuming = false;
        float deltaX = event.getX() - startX;
        float deltaY = event.getY() - startY;
        boolean commit = action == MotionEvent.ACTION_UP
                && Math.abs(deltaX) > host.dp(SWIPE_COMMIT_DP)
                && Math.abs(deltaX) > Math.abs(deltaY);
        if (!host.appearanceState.animations) {
            if (commit) {
                int target = adjacentIndex(deltaX < 0.0f ? 1 : -1);
                host.tabTransitionCoordinator.complete(TabTransitionRequest.withoutPreview(
                        target, deltaX < 0.0f ? 1 : -1, true, ""));
            }
            return true;
        }
        if (commit) {
            animateOffset(currentOffset, -direction * transitionDistance, true);
        } else {
            animateOffset(currentOffset, 0.0f, false);
        }
        return true;
    }

    void animateToTab(int target, int requestedDirection, boolean saveHistory, String search) {
        if (host.tabs == null || target < 0 || target >= host.tabs.length) {
            return;
        }
        if (target == host.navigationState.tabIndex) {
            host.navigationState.search = search == null ? "" : search;
            host.render();
            return;
        }
        if (!host.appearanceState.animations || host.contentHost == null || host.contentHost.getWidth() <= 0) {
            host.tabTransitionCoordinator.complete(TabTransitionRequest.withoutPreview(
                    target, requestedDirection, saveHistory, search));
            return;
        }
        prepareTransition(target, requestedDirection, saveHistory, search);
        updateOffset(0.0f);
        animateOffset(0.0f, -direction * transitionDistance, true);
    }

    private void prepareAdjacentTransition(int requestedDirection) {
        prepareTransition(adjacentIndex(requestedDirection), requestedDirection, true, "");
    }

    private void prepareTransition(int target, int requestedDirection, boolean saveHistory, String search) {
        cleanupPreview();
        direction = requestedDirection >= 0 ? 1 : -1;
        targetIndex = target;
        recordHistory = saveHistory;
        targetSearch = search == null ? "" : search;
        width = Math.max(1, host.contentHost.getWidth());
        transitionDistance = width + host.dp(12);
        currentSurface = host.navigationState.tabIndex == 0 && host.songsView != null
                ? host.songsView : host.contentScroll;
        host.navigationState.tabAnimating = true;
        host.tabsController.beginTransition(
                host.navigationState.tabIndex, targetIndex, direction);
        if (targetIndex == 0 && host.songsView != null) {
            previewUsesSongsSurface = true;
            host.songsView.prepareForTransition(
                    host.libraryState.tracks, targetSearch);
            previewSurface = host.songsView;
            previewSurface.setTranslationX(direction * transitionDistance);
            return;
        }
        previewUsesSongsSurface = false;
        previewList = new LinearLayout(host);
        previewList.setOrientation(LinearLayout.VERTICAL);
        previewScroll = new ScrollView(host);
        previewScroll.addView(previewList, new FrameLayout.LayoutParams(-1, -2));
        previewScroll.setTranslationX(direction * transitionDistance);
        host.contentHost.addView(previewScroll, 0, new FrameLayout.LayoutParams(-1, -1));
        previewSurface = previewScroll;
        final ScrollView expectedScroll = previewScroll;
        final LinearLayout expectedList = previewList;
        final int expectedTarget = targetIndex;
        final String expectedSearch = targetSearch;
        host.contentHost.post(() -> {
            if (previewScroll != expectedScroll || previewList != expectedList
                    || targetIndex != expectedTarget || previewUsesSongsSurface) {
                return;
            }
            previewState = host.renderTabPreview(
                    expectedList, expectedTarget, expectedSearch);
            expectedScroll.post(() -> {
                if (previewScroll == expectedScroll && previewState != null) {
                    expectedScroll.scrollTo(0, previewState.scrollY);
                }
            });
        });
    }

    private void updateOffset(float offset) {
        currentOffset = offset;
        if (currentSurface != null) {
            currentSurface.setTranslationX(offset);
        }
        if (previewSurface != null) {
            previewSurface.setTranslationX(offset + (direction * transitionDistance));
        }
        host.tabsController.setTransitionProgress(Math.min(1.0f, Math.abs(offset) / Math.max(1.0f, transitionDistance)));
    }

    private void animateOffset(float from, float to, boolean commit) {
        if (transitionAnimator != null) {
            transitionAnimator.cancel();
        }
        float distance = Math.abs(to - from);
        long duration = Math.max(90L, Math.min(280L, (long) (260L * distance / Math.max(1, width))));
        transitionAnimator = ValueAnimator.ofFloat(from, to);
        transitionAnimator.setDuration(duration);
        transitionAnimator.setInterpolator(new DecelerateInterpolator());
        transitionAnimator.addUpdateListener(animator -> updateOffset((Float) animator.getAnimatedValue()));
        transitionAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (cancelled) {
                    return;
                }
                if (commit) {
                    finishCommittedTransition();
                } else {
                    finishCancelledTransition();
                }
            }
        });
        transitionAnimator.start();
    }

    private void finishCommittedTransition() {
        int completedTarget = targetIndex;
        int completedDirection = direction;
        boolean shouldRecord = recordHistory;
        String completedSearch = targetSearch;
        if (previewUsesSongsSurface && previewSurface != null) {
            previewSurface.setTranslationX(0.0f);
            previewSurface = null;
            previewUsesSongsSurface = false;
            previewScroll = null;
            previewList = null;
            previewState = null;
            targetIndex = -1;
            resetCurrentContent();
            host.tabTransitionCoordinator.complete(TabTransitionRequest.withoutPreview(
                    completedTarget, completedDirection, shouldRecord, completedSearch));
            return;
        }
        if (previewScroll != null && previewList != null
                && previewState != null) {
            ScrollView committedScroll = previewScroll;
            LinearLayout committedList = previewList;
            MainRenderer.PreviewState committedState = previewState;
            previewScroll = null;
            previewList = null;
            previewState = null;
            previewSurface = null;
            targetIndex = -1;
            resetCurrentContent();
            host.tabTransitionCoordinator.completeWithPreview(new TabTransitionRequest(
                    completedTarget, completedDirection, shouldRecord, completedSearch,
                    committedScroll, committedList, committedState));
            return;
        }
        cleanupPreview();
        resetCurrentContent();
        host.tabTransitionCoordinator.complete(TabTransitionRequest.withoutPreview(
                completedTarget, completedDirection, shouldRecord, completedSearch));
    }

    private void finishCancelledTransition() {
        cleanupPreview();
        resetCurrentContent();
        host.navigationState.tabAnimating = false;
        host.tabsController.cancelTransition();
    }

    private void cleanupPreview() {
        if (previewScroll != null && previewScroll.getParent() == host.contentHost) {
            host.contentHost.removeView(previewScroll);
        }
        if (previewUsesSongsSurface && host.songsView != null
                && host.navigationState.tabIndex != 0) {
            host.songsView.hide();
        }
        previewScroll = null;
        previewList = null;
        previewState = null;
        previewSurface = null;
        previewUsesSongsSurface = false;
        host.discardTabPreview();
        targetIndex = -1;
    }

    private void resetCurrentContent() {
        currentOffset = 0.0f;
        if (currentSurface != null) {
            currentSurface.setTranslationX(0.0f);
            currentSurface.setAlpha(1.0f);
        }
        currentSurface = null;
        if (host.contentScroll != null) {
            host.contentScroll.setTranslationX(0.0f);
            host.contentScroll.setAlpha(1.0f);
        }
    }

    private float clampOffset(float value) {
        return Math.max(-transitionDistance * 0.96f, Math.min(transitionDistance * 0.96f, value));
    }

    private int adjacentIndex(int requestedDirection) {
        int count = host.tabs.length;
        return requestedDirection > 0
                ? (host.navigationState.tabIndex + 1) % count
                : (host.navigationState.tabIndex - 1 + count) % count;
    }
}
