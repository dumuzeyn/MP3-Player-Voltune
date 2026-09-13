package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@RunWith(AndroidJUnit4.class)
public class CrashReportStoreInstrumentedTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        CrashReportStore.clear(context);
    }

    @After
    public void tearDown() {
        CrashReportStore.clear(context);
    }

    @Test
    public void reportIsStoredLocallyAndSensitivePathsAreRedacted() throws Exception {
        IllegalStateException error = new IllegalStateException(
                "Cannot read content://music/private/song.mp3 from /storage/emulated/0/Music/song.mp3");
        File report = CrashReportStore.record(context, Thread.currentThread(), error);

        assertNotNull(report);
        assertTrue(report.isFile());
        assertEquals(1, CrashReportStore.count(context));

        String body = new String(Files.readAllBytes(report.toPath()), StandardCharsets.UTF_8);
        assertTrue(body.contains("content://<redacted>"));
        assertTrue(body.contains("/storage/<redacted>"));
        assertFalse(body.contains("private/song.mp3"));
        assertFalse(body.contains("emulated/0/Music"));
    }

    @Test
    public void latestSummaryAndClearReflectStoredReport() {
        CrashReportStore.record(context, Thread.currentThread(),
                new IllegalArgumentException("broken metadata"));

        assertTrue(CrashReportStore.latestSummary(context)
                .contains("IllegalArgumentException: broken metadata"));
        CrashReportStore.clear(context);
        assertEquals(0, CrashReportStore.count(context));
        assertEquals("", CrashReportStore.latestSummary(context));
    }

    @Test
    public void recordingPrunesOldReportsToFive() throws Exception {
        File directory = new File(context.getFilesDir(), "crash-reports");
        assertTrue(directory.mkdirs() || directory.isDirectory());
        for (int index = 0; index < 6; index++) {
            File report = new File(directory, "crash-old-" + index + ".txt");
            Files.write(report.toPath(), ("exception=old-" + index)
                    .getBytes(StandardCharsets.UTF_8));
            // Older Android filesystems round modification times to whole seconds.
            assertTrue(report.setLastModified(1_000L + index * 2_000L));
        }

        assertNotNull(CrashReportStore.record(context, Thread.currentThread(),
                new IllegalStateException("new report")));

        assertEquals(5, CrashReportStore.count(context));
        assertFalse(new File(directory, "crash-old-0.txt").exists());
        assertFalse(new File(directory, "crash-old-1.txt").exists());
    }
}
