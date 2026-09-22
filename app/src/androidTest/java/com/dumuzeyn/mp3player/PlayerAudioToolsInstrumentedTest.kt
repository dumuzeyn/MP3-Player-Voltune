package com.dumuzeyn.mp3player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent
import android.widget.SeekBar
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerAudioToolsInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var host: MainActivityCore? = null

    @After fun close() {
        host?.let {
            instrumentation.runOnMainSync {
                it.sleepTimerController.cancel()
                it.playbackController.setPlaybackSpeed(1f)
                it.playbackController.clearQueue()
            }
            InstrumentedTestSupport.finishActivity(instrumentation, it)
        }
    }

    @Test fun fullPlayerTapHoldAndSpeedEndpointsWork() {
        val activity = launch()
        onMain {
            activity.playbackQueueController.playTrack(activity.libraryState.tracks.first())
        }
        InstrumentedTestSupport.waitFor("Playback did not start", 15000) {
            var ready = false
            onMain { ready = activity.playbackStateProvider.isPlaying() }
            ready
        }
        onMain { activity.playerUiController.openFullPlayer() }
        InstrumentedTestSupport.waitFor("Speed button not rendered", 5000) {
            descendants(activity.overlayHost).any { it.contentDescription == "Скорость воспроизведения" }
        }
        onMain {
            val speed = descendants(activity.overlayHost).first {
                it.contentDescription == "Скорость воспроизведения"
            }
            val tools = speed.parent as ViewGroup
            assertEquals(3, tools.childCount)
            assertEquals(3, ((tools.parent as ViewGroup).getChildAt(3) as ViewGroup).childCount)
            assertTrue(speed.performLongClick())
            val dialog = activity.overlayHost.getChildAt(activity.overlayHost.childCount - 1)
            val slider = descendants(dialog).filterIsInstance<SeekBar>().single()
            assertEquals(75, slider.max)
            assertEquals(0.25f, PlaybackSpeedPolicy.fromProgress(0), 0f)
            assertEquals(4f, PlaybackSpeedPolicy.fromProgress(slider.max), 0f)
            slider.progress = 74
            slider.keyProgressIncrement = 1
            slider.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
            findText(dialog, "Применить")!!.performClick()
            assertEquals(4f, context.getSharedPreferences("player_tool_session", 0).getFloat("speed", 0f), 0f)
            val prefs = context.getSharedPreferences(EqualizerController.PREFS, 0)
            val equalizer = descendants(tools).first { it.contentDescription == "Эквалайзер" }
            prefs.edit().putBoolean(EqualizerController.ENABLED, false).commit()
            equalizer.performClick()
            assertTrue("Equalizer tap must enable it", prefs.getBoolean(EqualizerController.ENABLED, false))
            equalizer.performClick()
            assertFalse("Second equalizer tap must disable it", prefs.getBoolean(EqualizerController.ENABLED, true))
            assertTrue(equalizer.performLongClick())
            assertNull(findText(activity.overlayHost, "Включён"))
            assertNull(findText(activity.overlayHost, "Выключен"))
            activity.overlayHost.removeViewAt(activity.overlayHost.childCount - 1)
            val level = descendants(tools).first { it.contentDescription == "Единая громкость" }
            prefs.edit().putBoolean(VolumeLevelingController.ENABLED, false).commit()
            level.performClick()
            assertTrue(prefs.getBoolean(VolumeLevelingController.ENABLED, false))
            level.performClick()
            assertFalse(prefs.getBoolean(VolumeLevelingController.ENABLED, true))
            assertTrue(level.performLongClick())
            for (label in listOf("Громкие до уровня тихих", "Тихие до уровня громких", "Сбалансированный")) {
                assertNotNull(findTextContaining(activity.overlayHost, label))
            }
            findTextContaining(activity.overlayHost, "Тихие до уровня громких")!!.performClick()
            assertEquals("BOOST", prefs.getString(LoudnessLevelingMode.PREFERENCE, null))
            assertFalse("Choosing a mode must not turn leveling on", prefs.getBoolean(VolumeLevelingController.ENABLED, true))
            prefs.edit()
                .putBoolean(VolumeLevelingController.ENABLED, true)
                .putBoolean(EqualizerController.ENABLED, true)
                .putInt(EqualizerController.BAND_PREFIX + 0, 6)
                .commit()
            val effects = AudioEffectsManager(context)
            assertEquals("Boost mode must never reduce loud tracks", 0f,
                effects.adjustedNormalizationGainDb(3f), 0f)
            effects.release()
        }
        waitSpeed(activity, 4f)
        onMain {
            PlayerToolActions(activity).chooseSpeed()
            val dialog = activity.overlayHost.getChildAt(activity.overlayHost.childCount - 1)
            val slider = descendants(dialog).filterIsInstance<SeekBar>().single()
            slider.progress = 1
            slider.keyProgressIncrement = 1
            slider.onKeyDown(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
            findText(dialog, "Применить")!!.performClick()
        }
        waitSpeed(activity, 0.25f)
        for (coefficient in listOf(0.25f, 4f)) {
            onMain {
                context.getSharedPreferences("player_tool_session", 0).edit()
                    .putFloat("speed", coefficient).commit()
                activity.playbackController.setPlaybackSpeed(1f)
            }
            waitSpeed(activity, 1f)
            onMain { PlayerToolActions(activity).toggleSpeed() }
            waitSpeed(activity, coefficient)
            onMain { PlayerToolActions(activity).toggleSpeed() }
            waitSpeed(activity, 1f)
        }
    }

    @Test fun settingsAudioButtonsToggleAndHoldWithoutDuplicateSwitches() {
        val activity = launch()
        val playbackPreferences = context.getSharedPreferences(
            UninterruptedPlaybackController.PREFS,
            Context.MODE_PRIVATE,
        )
        val effectPreferences = context.getSharedPreferences(EqualizerController.PREFS, 0)
        val fadePreferences = context.getSharedPreferences(PlaybackFadePolicy.PREFS, 0)
        onMain {
            playbackPreferences.edit()
                .putBoolean(UninterruptedPlaybackController.ENABLED, false)
                .putBoolean(StableVolumeController.ENABLED, false)
                .commit()
            effectPreferences.edit()
                .putBoolean(VolumeLevelingController.ENABLED, false)
                .putBoolean(EqualizerController.ENABLED, false)
                .commit()
            fadePreferences.edit().putBoolean(PlaybackFadePolicy.ENABLED, false).commit()
            activity.navigationState.tabIndex = LibraryTabs.SETTINGS
            activity.render()

            val uninterrupted = findTextContaining(activity.list, "Всегда играть:")!!
            uninterrupted.performClick()
            assertTrue(playbackPreferences.getBoolean(UninterruptedPlaybackController.ENABLED, false))
            assertTrue(uninterrupted.text.toString().endsWith("вкл"))
            uninterrupted.performClick()
            assertFalse(playbackPreferences.getBoolean(UninterruptedPlaybackController.ENABLED, true))
            assertTrue(uninterrupted.text.toString().endsWith("выкл"))

            val stable = findTextContaining(activity.list, "Без приглушения громкости:")!!
            stable.performClick()
            assertTrue(playbackPreferences.getBoolean(StableVolumeController.ENABLED, false))
            stable.performClick()
            assertFalse(playbackPreferences.getBoolean(StableVolumeController.ENABLED, true))
            assertTrue(stable.text.toString().endsWith("выкл"))

            val fade = findTextContaining(activity.list, "Затихание в конце:")!!
            fade.performClick()
            assertTrue(fadePreferences.getBoolean(PlaybackFadePolicy.ENABLED, false))
            assertTrue(fade.text.toString().contains(" с"))
            fade.performClick()
            assertFalse(fadePreferences.getBoolean(PlaybackFadePolicy.ENABLED, true))
            assertTrue(fade.text.toString().endsWith("выкл"))
            assertTrue(fade.performLongClick())
            val fadeDialog = activity.overlayHost.getChildAt(activity.overlayHost.childCount - 1)
            assertNull(findText(fadeDialog, "Включено"))
            activity.overlayHost.removeView(fadeDialog)

            val volume = findTextContaining(activity.list, "Единая громкость:")!!
            volume.performClick()
            assertTrue(effectPreferences.getBoolean(VolumeLevelingController.ENABLED, false))
            volume.performClick()
            assertFalse(effectPreferences.getBoolean(VolumeLevelingController.ENABLED, true))
            assertTrue(volume.performLongClick())
            val volumeDialog = activity.overlayHost.getChildAt(activity.overlayHost.childCount - 1)
            assertNull(findTextContaining(volumeDialog, "Единая громкость:"))
            activity.overlayHost.removeView(volumeDialog)

            val equalizer = findTextContaining(activity.list, "Эквалайзер:")!!
            equalizer.performClick()
            assertTrue(effectPreferences.getBoolean(EqualizerController.ENABLED, false))
            equalizer.performClick()
            assertFalse(effectPreferences.getBoolean(EqualizerController.ENABLED, true))
            assertTrue(equalizer.performLongClick())
            assertNotNull(findText(activity.overlayHost, "Эквалайзер"))
        }
    }

    @Test fun choosingSaveDestinationDoesNotChangeMembership() {
        val activity = launch()
        onMain {
            val track = activity.libraryState.tracks.first()
            activity.libraryState.favorites.clear()
            activity.libraryState.playlists.clear()
            val playlist = Playlist("Проверочный плейлист")
            activity.libraryState.playlists.add(playlist)
            val tools = PlayerToolActions(activity)
            var changes = 0

            tools.chooseCollection { changes++ }
            findText(activity.overlayHost, playlist.name)!!.performClick()

            assertEquals(1, changes)
            assertEquals(
                playlist.name,
                context.getSharedPreferences("player_tool_session", 0)
                    .getString("collection", null),
            )
            assertFalse("Selecting a playlist must not add the song", playlist.uris.contains(track.uri))
            assertFalse(tools.isSaved(track))

            tools.toggleSaved(track)
            assertTrue(playlist.uris.contains(track.uri))
            assertTrue(tools.isSaved(track))

            tools.chooseCollection { changes++ }
            findText(activity.overlayHost, "Избранное")!!.performClick()
            assertEquals(2, changes)
            assertFalse("Selecting Favorites must not add the song", activity.libraryState.favorites.contains(track.uri))
            assertFalse("Button state must follow the newly selected destination", tools.isSaved(track))

            activity.libraryState.playlists.clear()
            activity.saveLibraryState()
            context.getSharedPreferences("player_tool_session", 0).edit().remove("collection").commit()
        }
    }

    @Test fun firstAnalyzedTrackReceivesLevelingWithoutWaitingForTransition() {
        val settings = context.getSharedPreferences(EqualizerController.PREFS, 0)
        val cache = context.getSharedPreferences(TrackLoudnessNormalizer.PREFS, 0)
        settings.edit()
            .putBoolean(VolumeLevelingController.ENABLED, true)
            .putString(LoudnessLevelingMode.PREFERENCE, LoudnessLevelingMode.BOOST.name)
            .commit()
        cache.edit().clear().commit()
        val wave = InstrumentedTestSupport.createTestWave(context, "leveling-first-track.wav", 2)
        val track = Track(
            Uri.fromFile(wave).toString(),
            "Quiet track",
            "Voltune tests",
            "Test",
            "Test",
            2_000,
        )
        val completed = CountDownLatch(1)
        val normalizer = TrackLoudnessNormalizer(context)
        try {
            normalizer.prefetch(listOf(track), 0) { completed.countDown() }
            assertTrue("Level analysis did not finish", completed.await(10, TimeUnit.SECONDS))
            assertTrue(
                "Boost mode must produce an audible gain for the first quiet track",
                normalizer.cachedGainDb(track) > 0.05f,
            )
        } finally {
            normalizer.release()
            cache.edit().clear().commit()
            settings.edit().remove(LoudnessLevelingMode.PREFERENCE).commit()
        }
    }

    private fun waitSpeed(activity: MainActivityCore, expected: Float) {
        InstrumentedTestSupport.waitFor("Speed was not applied: $expected", 5000) {
            var actual = 0f
            onMain { actual = activity.playbackController.playbackSpeed() }
            kotlin.math.abs(actual - expected) < 0.001f
        }
    }

    private fun launch(): MainActivityCore {
        context.getSharedPreferences("mp3_player_ui", 0).edit().putString("language", "ru")
            .putBoolean("particlesEnabled", false).commit()
        val wave = InstrumentedTestSupport.createTestWave(context, "audio-tools.wav", 180)
        TrackStore.save(context, listOf(Track(Uri.fromFile(wave).toString(), "Audio tools",
            "Voltune tests", "Tools", "Test", 180000)))
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        context.startActivity(Intent(context, MainActivity::class.java)
            .putExtra(BenchmarkLibrarySeeder.EXTRA_TRACK_COUNT, 1)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        val activity = monitor.waitForActivityWithTimeout(15000) as MainActivityCore
        instrumentation.removeMonitor(monitor)
        host = activity
        InstrumentedTestSupport.waitFor("Library did not load", 10000) {
            activity.librarySnapshotApplier.hasAppliedInitialSnapshot() && activity.libraryState.tracks.isNotEmpty()
        }
        return activity
    }

    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)

    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
    }

    private fun findText(view: View, label: String): TextView? =
        descendants(view).filterIsInstance<TextView>().firstOrNull { it.text.toString() == label }

    private fun findTextContaining(view: View, label: String): TextView? =
        descendants(view).filterIsInstance<TextView>().firstOrNull { label in it.text.toString() }
}
