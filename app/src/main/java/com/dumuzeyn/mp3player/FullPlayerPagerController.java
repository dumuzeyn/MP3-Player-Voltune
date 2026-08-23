package com.dumuzeyn.mp3player;

import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

/** Caches three pages and maps the requested inverse swipe order. */
final class FullPlayerPagerController implements AutoCloseable {
    private final MainActivityCore host;
    private final FullPlayerPlaybackPage playerPage;
    private final LyricsPageController lyricsPage;
    private final QueuePageController queuePage;
    private final View[] segments = new View[3];
    private ViewPager2 pager;
    private int selected = FullPlayerPageOrder.PLAYER;
    private boolean hostVisible = true;

    FullPlayerPagerController(MainActivityCore host, PlaybackActions actions,
            PlaybackStateProvider playbackState) {
        this.host = host;
        playerPage = new FullPlayerPlaybackPage(host, actions, playbackState);
        lyricsPage = new LyricsPageController(host, playbackState);
        queuePage = new QueuePageController(host, playbackState);
    }

    View createPager() {
        pager = new ViewPager2(host);
        pager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        pager.setOffscreenPageLimit(1);
        pager.setAdapter(new PagesAdapter());
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                selected = position;
                updateActivePage();
                updateIndicator();
            }
        });
        pager.setCurrentItem(FullPlayerPageOrder.PLAYER, false);
        return pager;
    }

    View createIndicator() {
        LinearLayout row = new LinearLayout(host);
        row.setGravity(Gravity.CENTER);
        for (int logical = 0; logical < 3; logical++) {
            int position = 2 - logical;
            View segment = new View(host);
            segments[position] = segment;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    host.dp(22), host.dp(4));
            params.setMargins(host.dp(3), 0, host.dp(3), 0);
            row.addView(segment, params);
        }
        updateIndicator();
        return row;
    }

    void refresh() {
        playerPage.refresh(true);
        if (selected == FullPlayerPageOrder.LYRICS) {
            lyricsPage.refresh();
        } else if (selected == FullPlayerPageOrder.QUEUE) {
            queuePage.refresh();
        }
    }

    void setHostVisible(boolean visible) {
        hostVisible = visible;
        updateActivePage();
    }

    int selectedPage() {
        return selected;
    }

    private void updateActivePage() {
        playerPage.setActive(hostVisible && selected == FullPlayerPageOrder.PLAYER);
        lyricsPage.setActive(hostVisible && selected == FullPlayerPageOrder.LYRICS);
        queuePage.setActive(hostVisible && selected == FullPlayerPageOrder.QUEUE);
    }

    private void updateIndicator() {
        for (int position = 0; position < segments.length; position++) {
            View segment = segments[position];
            if (segment == null) {
                continue;
            }
            GradientDrawable background = new GradientDrawable();
            background.setCornerRadius(host.dp(3));
            background.setColor(position == selected ? host.purple : host.cardStroke);
            segment.setBackground(background);
        }
    }

    @Override public void close() {
        playerPage.close();
        lyricsPage.close();
        queuePage.close();
        if (pager != null) {
            pager.setAdapter(null);
            pager = null;
        }
    }

    private final class PagesAdapter extends RecyclerView.Adapter<PageHolder> {
        @NonNull @Override public PageHolder onCreateViewHolder(
                @NonNull ViewGroup parent, int viewType) {
            FrameLayout container = new FrameLayout(host);
            container.setLayoutParams(new RecyclerView.LayoutParams(-1, -1));
            return new PageHolder(container);
        }

        @Override public void onBindViewHolder(@NonNull PageHolder holder, int position) {
            holder.container.removeAllViews();
            View page = position == FullPlayerPageOrder.PLAYER ? playerPage.createView()
                    : position == FullPlayerPageOrder.LYRICS ? lyricsPage.createView()
                    : queuePage.createView();
            if (page.getParent() instanceof ViewGroup) {
                ((ViewGroup) page.getParent()).removeView(page);
            }
            holder.container.addView(page, new FrameLayout.LayoutParams(-1, -1));
        }

        @Override public int getItemCount() {
            return 3;
        }
    }

    static final class PageHolder extends RecyclerView.ViewHolder {
        final FrameLayout container;
        PageHolder(FrameLayout container) {
            super(container);
            this.container = container;
        }
    }
}
