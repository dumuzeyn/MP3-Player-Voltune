package com.dumuzeyn.mp3player

import android.os.Bundle
import android.os.Process
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Media3ControllerAccessInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val access = Media3ControllerAccess(Process.myUid(), context.packageName)

    @Test
    fun ownControllerHasFullAccess() {
        val own = controller(context.packageName, Process.myUid(), trusted = true)
        val sessionCommands = access.sessionCommands(own)

        Media3Commands.internalCommands.forEach { assertTrue(sessionCommands.contains(it)) }
        assertTrue(access.playerCommands(own).contains(Player.COMMAND_CHANGE_MEDIA_ITEMS))
        assertTrue(access.playerCommands(own).contains(Player.COMMAND_SET_MEDIA_ITEM))
    }

    @Test
    fun externalControllerHasNoInternalCommands() {
        val external = controller("com.example.player", Process.myUid() + 10000, trusted = false)
        val sessionCommands = access.sessionCommands(external)

        Media3Commands.internalCommands.forEach { assertFalse(sessionCommands.contains(it)) }
        assertFalse(access.canUseInternalCommand(external, Media3Commands.CLEAR_QUEUE))
    }

    @Test
    fun trustedAndroidAutoKeepsStandardPlaybackAndLibraryCommands() {
        val androidAuto = controller(
            "com.google.android.projection.gearhead",
            Process.myUid() + 10001,
            trusted = true,
        )
        val playerCommands = access.playerCommands(androidAuto)
        val sessionCommands = access.sessionCommands(androidAuto)

        assertTrue(playerCommands.contains(Player.COMMAND_PLAY_PAUSE))
        assertTrue(playerCommands.contains(Player.COMMAND_PREPARE))
        assertTrue(playerCommands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertTrue(playerCommands.contains(Player.COMMAND_SET_REPEAT_MODE))
        assertTrue(playerCommands.contains(Player.COMMAND_SET_SHUFFLE_MODE))
        MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.commands.forEach {
            assertTrue(sessionCommands.contains(it))
        }
        Media3Commands.internalCommands.forEach { assertFalse(sessionCommands.contains(it)) }
    }

    private fun controller(packageName: String, uid: Int, trusted: Boolean) =
        MediaSession.ControllerInfo.createTestOnlyControllerInfo(
            packageName,
            1234,
            uid,
            1,
            1,
            trusted,
            Bundle.EMPTY,
            true,
        )
}
