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

    private fun launch(): MainActivityCore {
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
