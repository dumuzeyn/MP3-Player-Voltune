package com.dumuzeyn.mp3player

import android.annotation.SuppressLint
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommands

/** Central policy for the exported Media3 service. */
@SuppressLint("UnsafeOptInUsageError")
internal class Media3ControllerAccess(
    private val applicationUid: Int,
    private val applicationPackageName: String,
) {
    fun isOwn(controller: MediaSession.ControllerInfo): Boolean =
        controller.uid == applicationUid && controller.packageName == applicationPackageName

    fun sessionCommands(controller: MediaSession.ControllerInfo): SessionCommands {
        val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
            .buildUpon()
        if (isOwn(controller)) {
            Media3Commands.internalCommands.forEach(commands::add)
        }
        return commands.build()
    }

    fun playerCommands(controller: MediaSession.ControllerInfo): Player.Commands =
        if (isOwn(controller)) {
            Player.Commands.Builder().addAllCommands().build()
        } else {
            // Android Auto, Bluetooth, notifications, lock screen and external controllers only
            // need Media3's standard transport controls.
            MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
        }

    fun canUseInternalCommand(
        controller: MediaSession.ControllerInfo,
        action: String,
    ): Boolean = action in Media3Commands.internalActions && isOwn(controller)
}
