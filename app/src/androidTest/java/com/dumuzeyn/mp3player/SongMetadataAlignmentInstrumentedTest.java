package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SongMetadataAlignmentInstrumentedTest {
    @Test
    public void waveformAndDurationShareRaisedVerticalCenter() {
        Context context = ApplicationProvider.getApplicationContext();
        View item = LayoutInflater.from(context).inflate(R.layout.item_song,
                new FrameLayout(context), false);
        int width = dp(context, 360);
        int height = dp(context, 66);
        item.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        item.layout(0, 0, width, height);

        View card = item.findViewById(R.id.song_card);
        View waveform = item.findViewById(R.id.song_waveform_container);
        View duration = item.findViewById(R.id.song_duration);
        assertEquals(waveform.getTop(), duration.getTop());
        assertEquals(waveform.getBottom(), duration.getBottom());
        assertEquals(dp(context, 4), card.getHeight() - waveform.getBottom());
        assertTrue(waveform.getTop() >= item.findViewById(R.id.song_title).getBottom());

        View marker = item.findViewById(R.id.song_current_marker);
        assertEquals(context.getResources().getDimensionPixelSize(
                R.dimen.now_playing_indicator_width), marker.getWidth());
        assertEquals(context.getResources().getDimensionPixelSize(
                R.dimen.now_playing_indicator_height), marker.getHeight());
        NowPlayingIndicator.style(marker, Color.MAGENTA);
        assertTrue(marker.getBackground() instanceof GradientDrawable);
        assertTrue(((GradientDrawable) marker.getBackground()).getCornerRadius() > 0.0f);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
