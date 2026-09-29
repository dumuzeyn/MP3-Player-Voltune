package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CoverAppearanceInstrumentedTest {
    private Instrumentation instrumentation;
    private MainActivityCore host;

    @Before
    public void setUp() throws Exception {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = ApplicationProvider.getApplicationContext();
        host = InstrumentedTestSupport.launchForPlayback(instrumentation, context);
    }

    @After
    public void tearDown() {
        if (host != null) InstrumentedTestSupport.finishActivity(instrumentation, host);
    }

    @Test
    public void selectedShapeClipsFallbackBackground() {
        instrumentation.runOnMainSync(() -> {
            ShapedCoverImageView cover = new ShapedCoverImageView(host);
            cover.setBackgroundColor(Color.RED);
            int size = 96;
            int spec = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY);
            cover.measure(spec, spec);
            cover.layout(0, 0, size, size);
            for (String shape : new String[]{"circle", "triangle", "star"}) {
                host.appearanceState.coverShape = shape;
                cover.invalidateCoverShape();
                Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                cover.draw(new Canvas(bitmap));
                assertEquals(shape + " leaked a square corner", 0,
                        Color.alpha(bitmap.getPixel(0, 0)));
                assertEquals(shape + " lost its fallback fill", Color.RED,
                        bitmap.getPixel(size / 2, size / 2));
                bitmap.recycle();
            }
        });
    }

    @Test
    public void cachedCoverReadsCurrentSpeedBeforeSeeking() {
        instrumentation.runOnMainSync(() -> {
            host.appearanceState.rotateCovers = true;
            RotatingCoverImageView cover = new RotatingCoverImageView(host);
            host.appearanceState.fullPlayerRotationSpeed = 200;
            cover.updatePlaybackState(null, false);
            cover.beginSeekSpin(0);
            cover.updateSeekSpin(4500);
            assertEquals(180f, cover.getRotation(), 0.01f);
            cover.endSeekSpin(0, false);

            host.appearanceState.fullPlayerRotationSpeed = 50;
            cover.updatePlaybackState(null, false);
            cover.beginSeekSpin(0);
            cover.updateSeekSpin(9000);
            assertEquals(90f, cover.getRotation(), 0.01f);
        });
    }
}
