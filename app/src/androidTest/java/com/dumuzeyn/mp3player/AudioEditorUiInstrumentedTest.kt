package com.dumuzeyn.mp3player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AudioEditorUiInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var activity: MainActivityCore? = null
    private var wave: File? = null
    private var savedUri: Uri? = null

    @After fun cleanup() {
        activity?.let { InstrumentedTestSupport.finishActivity(instrumentation, it) }
        context.getSharedPreferences("audio_editor", 0).edit().clear().commit()
        wave?.delete()
        savedUri?.let { context.contentResolver.delete(it, null, null) }
    }

    @Test fun exportedAudioIsSavedAndImportedBackIntoLibrary() {
        context.getSharedPreferences("audio_editor", 0).edit().clear().commit()
        wave = InstrumentedTestSupport.createTestWave(context, "editor-save.wav", 6)
        val track = Track(Uri.fromFile(wave).toString(), "Export save test", "Voltune", "Test", "Test", 6000)
        TrackStore.save(context, listOf(track))
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, "voltune-editor-test-${System.nanoTime()}.m4a")
            put(android.provider.MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
        }
        val uri = checkNotNull(context.contentResolver.insert(
            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values))
        savedUri = uri
        val host = launch()
        val filter = android.content.IntentFilter(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            addDataType("audio/mp4")
        }
        val monitor = instrumentation.addMonitor(filter,
            android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK,
                Intent().setData(uri).setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)), true)
        try {
            instrumentation.runOnMainSync {
                host.switchTabAnimated(LibraryTabs.EDITOR, 1)
                host.audioEditorController.load()
                host.audioEditorController.add(track, 0)
                host.audioEditorController.export()
            }
            InstrumentedTestSupport.waitFor("Export was not saved and imported", 30000) {
                var ready = false
                instrumentation.runOnMainSync {
                    ready = !host.audioEditorController.busy && host.findTrack(uri.toString()) != null
                }
                ready
            }
            assertTrue("Document picker was not invoked", monitor.hits > 0)
            val size = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().size }
            assertTrue("Saved document is empty", size > 1000)
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun editUndoRedoRestoreAndSliderKeepEditorTab() {
        context.getSharedPreferences("audio_editor", 0).edit().clear().commit()
        context.getSharedPreferences("mp3_player_ui", 0).edit()
            .putBoolean("animations", false).putBoolean("particlesEnabled", false).commit()
        wave = InstrumentedTestSupport.createTestWave(context, "editor-ui.wav", 6)
        val track = Track(Uri.fromFile(wave).toString(), "Монтаж тестовой песни", "Voltune", "Test", "Test", 6000)
        TrackStore.save(context, listOf(track))
        val host = launch()
        instrumentation.runOnMainSync {
            host.switchTabAnimated(LibraryTabs.EDITOR, 1)
            host.audioEditorController.add(track, 0)
        }
        awaitLayout(host)
        instrumentation.runOnMainSync {
            assertEquals(LibraryTabs.EDITOR, host.navigationState.tabIndex)
            val location = IntArray(2)
            host.tabsScroll.getLocationOnScreen(location)
            val topInset = androidx.core.view.ViewCompat.getRootWindowInsets(host.root)
                ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())?.top ?: 0
            assertTrue("Tabs overlap status bar", location[1] >= topInset)
        }
        assertEquals("Редактор", host.tabs[LibraryTabs.EDITOR])
        capture("audio-editor.png")
        instrumentation.runOnMainSync {
            val controller = host.audioEditorController
            val clip = controller.project.clips.single()
            controller.change { it.split(clip.id, 3000) }
            assertEquals(2, controller.project.clips.size)
            controller.undo()
            assertEquals(1, controller.project.clips.size)
            controller.redo()
            assertEquals(2, controller.project.clips.size)
            AudioEditorDialogs(host).edit(controller.project.clips.first())
        }
        awaitLayout(host)
        capture("audio-editor-clip.png")
        val slider = descendants(host.overlayHost).filterIsInstance<SeekBar>().first()
        instrumentation.runOnMainSync {
            val time = android.os.SystemClock.uptimeMillis()
            slider.dispatchTouchEvent(MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 10f, 15f, 0))
            slider.dispatchTouchEvent(MotionEvent.obtain(time, time + 20, MotionEvent.ACTION_MOVE,
                slider.width * 0.8f, 15f, 0))
            slider.dispatchTouchEvent(MotionEvent.obtain(time, time + 30, MotionEvent.ACTION_UP,
                slider.width * 0.8f, 15f, 0))
            assertEquals(LibraryTabs.EDITOR, host.navigationState.tabIndex)
            descendants(host.overlayHost).filterIsInstance<TextView>()
                .first { it.text.toString() == "Применить обрезку и настройки" }.performClick()
        }
        awaitLayout(host)
        assertEquals(0, host.overlayHost.childCount)
        InstrumentedTestSupport.finishActivity(instrumentation, host)
        val restored = launch()
        instrumentation.runOnMainSync {
            restored.switchTabAnimated(LibraryTabs.EDITOR, 1)
            assertEquals(2, restored.audioEditorController.project.clips.size)
        }
    }

    @Test fun waveformHandlesUpdateTrimWithoutSwitchingTabs() {
        context.getSharedPreferences("audio_editor", 0).edit().clear().commit()
        wave = InstrumentedTestSupport.createTestWave(context, "editor-waveform-ui.wav", 6)
        val track = Track(Uri.fromFile(wave).toString(), "Звуковая волна", "Voltune", "Test", "Test", 6000)
        TrackStore.save(context, listOf(track))
        val host = launch()
        instrumentation.runOnMainSync {
            host.switchTabAnimated(LibraryTabs.EDITOR, 1)
            host.audioEditorController.add(track, 0)
            AudioEditorDialogs(host).edit(host.audioEditorController.project.clips.single())
        }
        awaitLayout(host)
        val waveform = descendants(host.overlayHost).filterIsInstance<AudioEditorWaveformView>().single()
        InstrumentedTestSupport.waitFor("Waveform did not decode", 15000) {
            var ready = false
            instrumentation.runOnMainSync { ready = waveform.waveform != null }
            ready
        }
        instrumentation.runOnMainSync {
            val scroll = descendants(host.overlayHost).filterIsInstance<android.widget.ScrollView>().single()
            assertTrue("Waveform clipped by short viewport", waveform.height <= scroll.height)
            val margin = host.dp(16).toFloat()
            val span = waveform.width - 2 * margin
            fun drag(from: Float, to: Float) {
                val time = android.os.SystemClock.uptimeMillis()
                for ((index, action) in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE,
                        MotionEvent.ACTION_UP).withIndex()) {
                    val event = MotionEvent.obtain(time, time + index * 20L, action,
                        if (index == 0) from else to, waveform.height / 2f, 0)
                    waveform.dispatchTouchEvent(event)
                    event.recycle()
                }
            }
            drag(margin, margin + span / 6)
            drag(waveform.width - margin, margin + span * 5 / 6)
            drag(margin + span / 2, margin + span / 2)
            assertEquals(3000, waveform.cursorMs)
            assertEquals(LibraryTabs.EDITOR, host.navigationState.tabIndex)
            val fields = descendants(host.overlayHost).filterIsInstance<android.widget.EditText>()
            assertEquals("1.000", fields.first { it.contentDescription == "Начало, с" }.text.toString())
            assertEquals("5.000", fields.first { it.contentDescription == "Конец, с" }.text.toString())
        }
        capture("audio-editor-waveform.png")
        instrumentation.runOnMainSync {
            val scroll = descendants(host.overlayHost).filterIsInstance<android.widget.ScrollView>().single()
            val time = android.os.SystemClock.uptimeMillis()
            val startY = minOf(waveform.height, scroll.height) * 0.8f
            for (index in 0..4) {
                val action = when (index) { 0 -> MotionEvent.ACTION_DOWN; 4 -> MotionEvent.ACTION_UP
                    else -> MotionEvent.ACTION_MOVE }
                val event = MotionEvent.obtain(time, time + index * 30L, action,
                    scroll.width / 2f, startY * (1f - index * 0.23f), 0)
                scroll.dispatchTouchEvent(event)
                event.recycle()
            }
            assertTrue("Waveform prevents vertical scrolling", scroll.scrollY > 0)
            descendants(host.overlayHost).filterIsInstance<TextView>()
                .first { it.text.toString() == "Применить обрезку и настройки" }.performClick()
            val clip = host.audioEditorController.project.clips.single()
            assertEquals(1000, clip.startMs)
            assertEquals(5000, clip.endMs)
        }
    }

    @Test fun previewControlsPauseSeekAndRestoreMusicAndCancelPreparation() {
        context.getSharedPreferences("audio_editor", 0).edit().clear().commit()
        wave = InstrumentedTestSupport.createTestWave(context, "editor-preview-ui.wav", 6)
        val track = Track(Uri.fromFile(wave).toString(), "Preview UI", "Voltune", "Test", "Test", 6000)
        TrackStore.save(context, listOf(track))
        val host = launch()
        instrumentation.runOnMainSync {
            host.playbackController.submitQueue(listOf(track), 0, 1200, 0, false)
            host.switchTabAnimated(LibraryTabs.EDITOR, 1)
            host.audioEditorController.add(track, 0)
        }
        InstrumentedTestSupport.waitFor("Music session not ready", 15000) {
            var ready = false
            instrumentation.runOnMainSync { ready = host.playbackSnapshot().phase == PlaybackPhase.READY }
            ready
        }
        awaitLayout(host)
        instrumentation.runOnMainSync {
            descendants(host.list).first { it.contentDescription == "Прослушать аудио" }.performClick()
        }
        fun awaitPreview(phase: AudioEditorPreviewController.Phase) {
            InstrumentedTestSupport.waitFor("Preview did not reach $phase", 20000) {
                var ready = false
                instrumentation.runOnMainSync { ready = host.audioEditorController.preview.phase == phase }
                ready
            }
        }
        awaitPreview(AudioEditorPreviewController.Phase.PLAYING)
        instrumentation.runOnMainSync {
            assertTrue(host.audioEditorController.busy)
            host.audioEditorController.preview.toggle()
        }
        awaitPreview(AudioEditorPreviewController.Phase.PAUSED)
        instrumentation.runOnMainSync { host.audioEditorController.preview.seek(1000) }
        InstrumentedTestSupport.waitFor("Preview did not seek", 5000) {
            var ready = false
            instrumentation.runOnMainSync { ready = host.audioEditorController.preview.positionMs == 1000L }
            ready
        }
        capture("audio-editor-preview.png")
        instrumentation.runOnMainSync { host.audioEditorController.preview.stop() }
        awaitPreview(AudioEditorPreviewController.Phase.IDLE)
        InstrumentedTestSupport.waitFor("Music not restored", 5000) {
            var ready = false
            instrumentation.runOnMainSync {
                ready = host.playbackController.currentPosition() == 1200L && !host.playbackSnapshot().playWhenReady
            }
            ready
        }
        instrumentation.runOnMainSync {
            val editor = host.audioEditorController
            editor.preview.start(editor.project)
            assertEquals("Unchanged project was encoded again", AudioEditorPreviewController.Phase.STARTING, editor.preview.phase)
            editor.preview.stop()
            editor.change { it.replace(it.clips.single().copy(endMs = 4000)) }
            editor.preview.start(editor.project)
            editor.preview.stop()
        }
        Thread.sleep(500)
        instrumentation.runOnMainSync {
            assertFalse(host.audioEditorController.preview.active)
            assertFalse(host.audioEditorController.busy)
            host.playbackController.clearQueue()
        }
    }

    @Test fun speechCleanupCanBeUndoneRestoredAndCancelled() {
        context.getSharedPreferences("audio_editor", 0).edit().clear().commit()
        wave = InstrumentedTestSupport.createTestWave(context, "editor-speech-ui.wav", 6)
        val track = Track(Uri.fromFile(wave).toString(), "Речь для очистки", "Voltune", "Test", "Test", 6000)
        TrackStore.save(context, listOf(track))
        val host = launch()
        val originalBytes = wave!!.readBytes()
        instrumentation.runOnMainSync {
            host.switchTabAnimated(LibraryTabs.EDITOR, 1)
            host.audioEditorController.add(track, 0)
            AudioEditorDialogs(host).edit(host.audioEditorController.project.clips.single())
        }
        awaitLayout(host)
        instrumentation.runOnMainSync {
            descendants(host.overlayHost).filterIsInstance<TextView>()
                .first { it.text.toString() == "Очистить речь" }.performClick()
            assertTrue(host.audioEditorController.busy)
            assertEquals(0, host.overlayHost.childCount)
            assertFalse(host.audioEditorController.canUndo)
        }
        InstrumentedTestSupport.waitFor("Speech processing did not finish", 30000) {
            var done = false
            instrumentation.runOnMainSync { done = !host.audioEditorController.processing.active }
            done
        }
        var processed: AudioEditClip? = null
        instrumentation.runOnMainSync {
            val editor = host.audioEditorController
            processed = editor.project.clips.single()
            assertNotEquals(track.uri, processed!!.uri)
            assertEquals(6000L, processed!!.durationMs)
            editor.undo()
            assertEquals(track.uri, editor.project.clips.single().uri)
            editor.redo()
            assertEquals(processed, editor.project.clips.single())
            assertTrue(editor.processing.cleanSpeech(editor.project.clips.single()))
            editor.processing.cancel()
            assertFalse(editor.busy)
            assertEquals(processed, editor.project.clips.single())
        }
        assertArrayEquals(originalBytes, wave!!.readBytes())
        InstrumentedTestSupport.finishActivity(instrumentation, host)
        val restored = launch()
        instrumentation.runOnMainSync {
            restored.audioEditorController.load()
            assertEquals(processed, restored.audioEditorController.project.clips.single())
        }
        File(Uri.parse(processed!!.uri).path!!).delete()
    }

    @Test fun stemButtonsCreateFourLanesAndUndoRestoresOriginal() {
        context.getSharedPreferences("audio_editor", 0).edit().clear().commit()
        wave = File(context.cacheDir, "editor-stems-ui.wav")
        PcmWaveWriter(wave!!, 44100, 2).use { writer ->
            repeat(88200) { frame ->
                val value = (.2 * kotlin.math.sin(2 * Math.PI * 110 * frame / 44100)).toFloat()
                writer.sample(value)
                writer.sample(-value)
            }
        }
        val track = Track(Uri.fromFile(wave).toString(), "Разделение тест", "Voltune", "Test", "Test", 2000)
        TrackStore.save(context, listOf(track))
        val host = launch()
        instrumentation.runOnMainSync {
            host.switchTabAnimated(LibraryTabs.EDITOR, 1)
            host.audioEditorController.add(track, 0)
            AudioEditorDialogs(host).edit(host.audioEditorController.project.clips.single())
        }
        awaitLayout(host)
        instrumentation.runOnMainSync {
            descendants(host.overlayHost).filterIsInstance<android.widget.Spinner>().single().setSelection(2)
            descendants(host.overlayHost).filterIsInstance<TextView>()
                .first { it.text.toString() == "Разделить на четыре дорожки" }.performClick()
            assertTrue(host.audioEditorController.processing.status, host.audioEditorController.busy)
        }
        InstrumentedTestSupport.waitFor("Stem processing did not finish", 45000) {
            var done = false
            instrumentation.runOnMainSync { done = !host.audioEditorController.processing.active }
            done
        }
        val outputs = ArrayList<File>()
        try {
            instrumentation.runOnMainSync {
                val editor = host.audioEditorController
                assertEquals(editor.processing.status, 4, editor.project.clips.size)
                assertEquals(listOf(2, 0, 1, 3), editor.project.clips.map { it.lane })
                editor.project.clips.forEach { clip ->
                    assertEquals(2000L, clip.durationMs)
                    outputs.add(File(Uri.parse(clip.uri).path!!))
                }
                editor.undo()
                assertEquals(track.uri, editor.project.clips.single().uri)
                editor.redo()
                assertEquals(4, editor.project.clips.size)
                assertTrue(outputs.all { it.isFile })
            }
            awaitLayout(host)
            capture("audio-editor-stems.png")
        } finally { outputs.forEach { it.delete() } }
    }

    @Test fun separationRejectsOccupiedLanesAndCancellationKeepsDraft() {
        context.getSharedPreferences("audio_editor", 0).edit().clear().commit()
        wave = InstrumentedTestSupport.createTestWave(context, "editor-stems-cancel.wav", 6)
        val track = Track(Uri.fromFile(wave).toString(), "Stem cancellation", "Voltune", "Test", "Test", 6000)
        TrackStore.save(context, listOf(track))
        val host = launch()
        instrumentation.runOnMainSync {
            host.switchTabAnimated(LibraryTabs.EDITOR, 1)
            val editor = host.audioEditorController
            repeat(6) { editor.add(track, it) }
            val occupied = editor.project
            assertFalse(editor.processing.separate(occupied.clips.first(), false))
            assertFalse(editor.busy)
            assertEquals(occupied, editor.project)
            repeat(5) { editor.undo() }
            val original = editor.project
            assertEquals(1, original.clips.size)
            assertTrue(editor.processing.status, editor.processing.separate(original.clips.single(), true))
            editor.processing.cancel()
            assertFalse(editor.busy)
            assertEquals(original, editor.project)
        }
    }

    private fun launch(): MainActivityCore {
        context.getSharedPreferences("mp3_player_ui", 0).edit().putString("language", "ru").commit()
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        context.startActivity(Intent(context, MainActivity::class.java)
            .putExtra(BenchmarkLibrarySeeder.EXTRA_TRACK_COUNT, 1)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        val host = monitor.waitForActivityWithTimeout(15000) as MainActivityCore
        activity = host
        instrumentation.removeMonitor(monitor)
        InstrumentedTestSupport.waitFor("Library not ready", 15000) {
            host.librarySnapshotApplier.hasAppliedInitialSnapshot()
        }
        return host
    }

    private fun awaitLayout(host: MainActivityCore) {
        InstrumentedTestSupport.waitFor("Editor layout did not finish", 5000) {
            var ready = false
            instrumentation.runOnMainSync {
                ready = !host.root.isLayoutRequested && host.list.width > 0 &&
                    !host.overlayHost.isLayoutRequested
            }
            ready
        }
    }

    private fun capture(name: String) {
        val root = checkNotNull(activity).root
        val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height,
            android.graphics.Bitmap.Config.ARGB_8888)
        instrumentation.runOnMainSync { root.draw(android.graphics.Canvas(bitmap)) }
        File(context.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
    }
}
