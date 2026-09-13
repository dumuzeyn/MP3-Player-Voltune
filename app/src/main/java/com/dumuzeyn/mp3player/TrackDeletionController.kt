package com.dumuzeyn.mp3player

import android.app.Activity
import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.RequiresApi
import java.io.FileNotFoundException
import java.util.concurrent.Executors

internal class TrackDeletionController(private val host: MainActivityCore) : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor()
    private var pendingTrack: Track? = null

    fun canDeleteFile(track: Track?): Boolean {
        if (track == null) return false
        val uri = track.asUri()
        if (TrackDeletionPolicy.isMediaStore(uri)) return true
        return TrackDeletionPolicy.isContentUri(uri) &&
            DocumentsContract.isDocumentUri(host, uri) &&
            host.checkUriPermission(
                uri,
                Process.myPid(),
                Process.myUid(),
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun deleteFile(track: Track) {
        if (!canDeleteFile(track)) {
            showFailure()
            return
        }
        pendingTrack = track
        val uri = track.asUri()
        if (Build.VERSION.SDK_INT >= 30 && TrackDeletionPolicy.isMediaStore(uri)) {
            startRequest(MediaStore.createDeleteRequest(host.contentResolver, listOf(uri)))
            return
        }
        executor.execute { deleteDirectly(track) }
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int): Boolean {
        if (requestCode != DELETE_REQUEST) return false
        val track = pendingTrack
        pendingTrack = null
        if (resultCode == Activity.RESULT_OK && track != null) {
            host.playbackQueueController.removeDeletedFile(track)
        }
        return true
    }

    private fun deleteDirectly(track: Track) {
        if (Build.VERSION.SDK_INT >= 29) {
            deleteDirectlyApi29(track)
            return
        }
        try {
            postDeleteResult(track, performDelete(track.asUri()))
        } catch (_: FileNotFoundException) {
            host.runOnUiThread(::showFailure)
        } catch (_: RuntimeException) {
            host.runOnUiThread(::showFailure)
        }
    }

    @RequiresApi(29)
    private fun deleteDirectlyApi29(track: Track) {
        try {
            postDeleteResult(track, performDelete(track.asUri()))
        } catch (recoverable: RecoverableSecurityException) {
            host.runOnUiThread { startRequest(recoverable.userAction.actionIntent) }
        } catch (_: FileNotFoundException) {
            host.runOnUiThread(::showFailure)
        } catch (_: RuntimeException) {
            host.runOnUiThread(::showFailure)
        }
    }

    @Throws(FileNotFoundException::class)
    private fun performDelete(uri: Uri): Boolean =
        if (DocumentsContract.isDocumentUri(host, uri)) {
            DocumentsContract.deleteDocument(host.contentResolver, uri)
        } else {
            host.contentResolver.delete(uri, null, null) > 0
        }

    private fun postDeleteResult(track: Track, deleted: Boolean) {
        host.runOnUiThread { finishDirectDelete(track, deleted) }
    }

    private fun finishDirectDelete(track: Track, deleted: Boolean) {
        pendingTrack = null
        if (deleted) host.playbackQueueController.removeDeletedFile(track) else showFailure()
    }

    private fun startRequest(request: PendingIntent) {
        try {
            host.startIntentSenderForResult(request.intentSender, DELETE_REQUEST, null, 0, 0, 0)
        } catch (_: IntentSender.SendIntentException) {
            pendingTrack = null
            showFailure()
        }
    }

    private fun showFailure() {
        Toast.makeText(
            host,
            host.tr("The file could not be deleted", "Не удалось удалить файл"),
            Toast.LENGTH_LONG,
        ).show()
    }

    override fun close() {
        pendingTrack = null
        executor.shutdownNow()
    }

    private companion object {
        const val DELETE_REQUEST = 7315
    }
}
