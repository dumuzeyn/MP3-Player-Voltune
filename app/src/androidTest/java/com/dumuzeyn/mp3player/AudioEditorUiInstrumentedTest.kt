package com.dumuzeyn.mp3player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
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
        // Exercise the document picker's content URI on every API, without broad
        // storage permissions or file URIs rejected by the production importer.
        val directory = File(context.cacheDir, "editor-test-exports").apply { mkdirs() }
        val destination = File.createTempFile("editor-saved-", ".m4a", directory)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.testexports", destination)
        savedUri = uri
        val host = launch()
        val filter = android.content.IntentFilter(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            addDataType("audio/mp4")
        }
        val monitor = instrumentation.addMonitor(filter,
            android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK,
                Intent().setData(uri).setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)), true)
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
            val workspace = host.list.findViewById<ViewGroup>(R.id.editor_workspace)
            assertNotNull(workspace)
            assertEquals(1, descendants(workspace).filterIsInstance<AudioEditorPreviewControls>().size)
            assertEquals(1, descendants(workspace).filterIsInstance<AudioEditorTimelineView>().size)
            descendants(workspace).first { it.contentDescription == "Выключить звук дорожки 1" }.performClick()
            assertTrue(host.audioEditorController.mutedPreviewLanes.contains(0))
            assertEquals(0f, host.audioEditorController.previewProject().clips.single().gain)
            val mutedWorkspace = host.list.findViewById<ViewGroup>(R.id.editor_workspace)
            val mutedButton = descendants(mutedWorkspace).filterIsInstance<Button>()
                .first { it.contentDescription == "Включить звук дорожки 1" }
            assertTrue("Muted lane is not visibly marked", mutedButton.text.toString().contains("×"))
            mutedButton.performClick()
            assertFalse(host.audioEditorController.mutedPreviewLanes.contains(0))
            val clipRow = descendants(host.list).filterIsInstance<Button>()
                .first { it.text.toString().startsWith(track.title) }
            assertEquals("Clip row is not compact", host.dp(44), clipRow.layoutParams.height)
            val commands = descendants(host.list).filterIsInstance<Button>()
                .map { it.text.toString() }.toSet()
            assertTrue(commands.containsAll(setOf(
                "Положение",
                "Обрезка",
                "Громкость",
                "Разделить",
                "Удалить отрезок",
                "Убрать шумы",
                "Разделить дорожки",
                "Удалить вокал",
                "Удалить выбранный фрагмент",
            )))
            AudioEditorDialogs(host).chooseExport()
            val formats = descendants(host.overlayHost).filterIsInstance<android.widget.Spinner>().single()
            assertEquals(listOf("M4A", "MP3", "WAV"),
                (0 until formats.adapter.count).map { formats.adapter.getItem(it).toString() })
            host.overlayHost.removeAllViews()
            descendants(host.list).filterIsInstance<Button>()
                .first { it.text.toString() == "Громкость" }.performClick()
            assertNotNull(descendants(host.overlayHost).filterIsInstance<TextView>()
                .firstOrNull { it.text.toString() == "Громкость фрагмента" })
            val volumeButtons = descendants(host.overlayHost).filterIsInstance<Button>()
                .map { it.text.toString() }
            assertTrue(volumeButtons.contains("Применить громкость"))
            val volume = descendants(host.overlayHost).filterIsInstance<SeekBar>()
                .single { it.max == 200 }
            volume.progress = 200
            descendants(host.overlayHost).filterIsInstance<Button>()
                .first { it.text.toString() == "Применить громкость" }.performClick()
            assertEquals(2f, host.audioEditorController.project.clips.single().gain)
            assertFalse("Volume menu contains split controls", volumeButtons.contains("Разделить"))
            assertFalse("Volume menu contains trim controls", volumeButtons.contains("Применить обрезку"))
            AudioEditorDialogs(host).edit(host.audioEditorController.project.clips.single(),
                AudioEditorDialogs.Focus.POSITION)
            assertNotNull(descendants(host.overlayHost).filterIsInstance<Switch>()
                .firstOrNull { it.text.toString() == "К ближайшему свободному месту" })
            host.overlayHost.removeAllViews()
            AudioEditorDialogs(host).edit(host.audioEditorController.project.clips.single(),
                AudioEditorDialogs.Focus.REMOVE_RANGE)
            val rangeOptions = descendants(host.overlayHost).filterIsInstance<Switch>()
                .associateBy { it.text.toString() }
            assertTrue(rangeOptions.getValue("Соединить оставшиеся части").isChecked)
            assertTrue(rangeOptions.getValue("Плавное соединение").isChecked)
            host.overlayHost.removeAllViews()
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
            AudioEditorDialogs(host).edit(controller.project.clips.first(), AudioEditorDialogs.Focus.TRIM)
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
                .first { it.text.toString() == "Применить обрезку" }.performClick()
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

    @Test fun longPressMovesClipBetweenLanesAndInvalidDropRestoresIt() {
        context.getSharedPreferences("audio_editor", 0).edit().clear().commit()
        context.getSharedPreferences("mp3_player_ui", 0).edit()
            .putBoolean("animations", false).putBoolean("particlesEnabled", false).commit()
        wave = InstrumentedTestSupport.createTestWave(context, "editor-drag.wav", 6)
        val first = Track(Uri.fromFile(wave).toString(), "Перемещаемый", "Voltune", "Test", "Test", 6000)
        val second = Track(first.uri + "?lane=2", "Вторая дорожка", "Voltune", "Test", "Test", 6000)
        TrackStore.save(context, listOf(first, second))
        val host = launch()
        lateinit var moving: AudioEditClip
        instrumentation.runOnMainSync {
            host.switchTabAnimated(LibraryTabs.EDITOR, 1)
            host.audioEditorController.add(first, 0)
            moving = host.audioEditorController.project.clips.single()
            host.audioEditorController.change { project ->
                project.replace(moving.copy(endMs = 2000))
            }
            moving = host.audioEditorController.project.clips.single()
            host.audioEditorController.add(second, 1)
        }
        awaitLayout(host)

        fun longDrag(targetY: Float) {
            val timeline = descendants(host.list).filterIsInstance<AudioEditorTimelineView>().single()
            val time = android.os.SystemClock.uptimeMillis()
            instrumentation.runOnMainSync {
                val down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN,
                    timeline.width / 6f, host.dp(54).toFloat(), 0)
                timeline.dispatchTouchEvent(down)
                down.recycle()
            }
            Thread.sleep(750)
            instrumentation.runOnMainSync {
                val moveTime = android.os.SystemClock.uptimeMillis()
                val move = MotionEvent.obtain(moveTime, moveTime, MotionEvent.ACTION_MOVE,
                    timeline.width / 2f, targetY, 0)
                val up = MotionEvent.obtain(moveTime, moveTime + 20,
                    MotionEvent.ACTION_UP, timeline.width / 2f, targetY, 0)
                timeline.dispatchTouchEvent(move)
                timeline.dispatchTouchEvent(up)
                move.recycle()
                up.recycle()
            }
        }

        longDrag(-host.dp(12).toFloat())
        instrumentation.runOnMainSync {
            assertEquals(0, host.audioEditorController.project.clips.first { it.id == moving.id }.lane)
        }
        longDrag(host.dp(118).toFloat())
        instrumentation.runOnMainSync {
            val moved = host.audioEditorController.project.clips.first { it.id == moving.id }
            assertEquals(1, moved.lane)
            assertEquals(6000L, moved.offsetMs)
        }
    }

    @Test fun songPropertiesOpenSelectedEditorAndLockBlocksNavigation() {
        context.getSharedPreferences("audio_editor", 0).edit().clear().commit()
        context.getSharedPreferences("mp3_player_ui", 0).edit()
            .putBoolean("animations", false).putBoolean("particlesEnabled", false).commit()
        wave = InstrumentedTestSupport.createTestWave(context, "editor-properties.wav", 4)
        val track = Track(
            Uri.fromFile(wave).toString(),
            "Песня для редактора",
            "Voltune",
            "Test",
            "Test",
            4000,
        )
        TrackStore.save(context, listOf(track))
        val host = launch()

        instrumentation.runOnMainSync {
            host.overlayController.openSongActions(track)
            descendants(host.overlayHost).filterIsInstance<TextView>()
                .first { it.text.toString() == "Редактировать аудио" }.performClick()
        }
        InstrumentedTestSupport.waitFor("Audio editor did not open from song properties", 5000) {
            host.navigationState.tabIndex == LibraryTabs.EDITOR
        }
        awaitLayout(host)

        instrumentation.runOnMainSync {
            assertEquals(track.uri, host.audioEditorController.selectedClip?.uri)
            val lock = host.root.findViewById<View>(R.id.editor_mode_lock)
            val bounds = host.root.findViewById<View>(R.id.editor_mode_bounds)
            assertNotNull(lock)
            assertNotNull(bounds)
            lock.performClick()
            assertTrue(host.audioEditorController.editingMode)
            assertEquals(View.VISIBLE, bounds.visibility)
        }
        awaitLayout(host)
        capture("audio-editor-locked.png")

        instrumentation.runOnMainSync {
            host.switchTabAnimated(LibraryTabs.SETTINGS, 1)
            host.swipeController.animateToTab(LibraryTabs.SETTINGS, 1, true, "")
            assertTrue(host.backNavigationController.handleBack())
            assertEquals(LibraryTabs.EDITOR, host.navigationState.tabIndex)

            val settingsTab = descendants(host.tabRow)
                .filterIsInstance<Button>()
                .first { it.tag == LibraryTabs.SETTINGS }
            assertFalse(settingsTab.isEnabled)
            settingsTab.performClick()
            assertEquals(LibraryTabs.EDITOR, host.navigationState.tabIndex)

            host.root.findViewById<View>(R.id.editor_mode_lock).performClick()
            assertFalse(host.audioEditorController.editingMode)
            assertEquals(View.GONE, host.root.findViewById<View>(R.id.editor_mode_bounds).visibility)
            host.switchTabAnimated(LibraryTabs.SETTINGS, 1)
            assertEquals(LibraryTabs.SETTINGS, host.navigationState.tabIndex)
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
            AudioEditorDialogs(host).edit(host.audioEditorController.project.clips.single(),
                AudioEditorDialogs.Focus.TRIM)
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
            if (scroll.canScrollVertically(1)) {
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
            }
            descendants(host.overlayHost).filterIsInstance<TextView>()
                .first { it.text.toString() == "Применить обрезку" }.performClick()
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
        InstrumentedTestSupport.waitFor("Preview control not rendered", 5000) {
            var rendered = false
            instrumentation.runOnMainSync {
                rendered = descendants(host.list).any { it.contentDescription == "Прослушать аудио" }
            }
            rendered
        }
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
        instrumentation.runOnMainSync { host.audioEditorController.togglePreviewLane(0) }
        awaitPreview(AudioEditorPreviewController.Phase.PLAYING)
        InstrumentedTestSupport.waitFor("Muted preview did not resume at the same position", 5000) {
            var resumed = false
            instrumentation.runOnMainSync {
                resumed = host.audioEditorController.mutedPreviewLanes.contains(0) &&
                    host.audioEditorController.preview.positionMs >= 900
            }
            resumed
        }
        instrumentation.runOnMainSync { host.audioEditorController.togglePreviewLane(0) }
        awaitPreview(AudioEditorPreviewController.Phase.PLAYING)
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
        lateinit var originalPreview: AudioEditProject
        instrumentation.runOnMainSync {
            val editor = host.audioEditorController
            originalPreview = editor.project
            assertNotNull(AudioEditorPreviewCache(context).get(originalPreview))
            editor.preview.start(editor.project)
            assertEquals("Unchanged project was encoded again", AudioEditorPreviewController.Phase.STARTING, editor.preview.phase)
            editor.preview.stop()
            editor.preview.start(AudioEditProject(listOf(editor.project.clips.single().copy(gain = 0.9f))))
        }
        awaitPreview(AudioEditorPreviewController.Phase.PLAYING)
        instrumentation.runOnMainSync {
            val editor = host.audioEditorController
            editor.preview.stop()
            editor.preview.start(originalPreview)
            assertEquals("Earlier preview was evicted by another tool", AudioEditorPreviewController.Phase.STARTING,
                editor.preview.phase)
            editor.preview.stop()
            editor.change { it.replace(it.clips.single().copy(endMs = 4000)) }
            assertNull(AudioEditorPreviewCache(context).get(originalPreview))
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
            AudioEditorDialogs(host).edit(host.audioEditorController.project.clips.single(),
                AudioEditorDialogs.Focus.CLEAN_SPEECH)
        }
        awaitLayout(host)
        instrumentation.runOnMainSync {
            descendants(host.overlayHost).filterIsInstance<TextView>()
                .first { it.text.toString() == "Убрать шумы" }.performClick()
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
            AudioEditorDialogs(host).edit(host.audioEditorController.project.clips.single(),
                AudioEditorDialogs.Focus.SEPARATE_STEMS)
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
