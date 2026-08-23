package com.dumuzeyn.mp3player;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.RecoverableSecurityException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.widget.Toast;
import androidx.annotation.RequiresApi;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class TrackDeletionController implements AutoCloseable {
    private static final int DELETE_REQUEST = 7315;
    private final MainActivityCore host;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Track pendingTrack;

    TrackDeletionController(MainActivityCore host) {
        this.host = host;
    }

    boolean canDeleteFile(Track track) {
        if (track == null) {
            return false;
        }
        Uri uri = track.asUri();
        if (TrackDeletionPolicy.isMediaStore(uri)) {
            return true;
        }
        return TrackDeletionPolicy.isContentUri(uri)
                && DocumentsContract.isDocumentUri(host, uri)
                && host.checkUriPermission(uri, android.os.Process.myPid(),
                android.os.Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    void deleteFile(Track track) {
        if (!canDeleteFile(track)) {
            showFailure();
            return;
        }
        pendingTrack = track;
        Uri uri = track.asUri();
        if (Build.VERSION.SDK_INT >= 30 && TrackDeletionPolicy.isMediaStore(uri)) {
            PendingIntent request = MediaStore.createDeleteRequest(
                    host.getContentResolver(), Collections.singletonList(uri));
            startRequest(request);
            return;
        }
        executor.execute(() -> deleteDirectly(track));
    }

    boolean handleActivityResult(int requestCode, int resultCode) {
        if (requestCode != DELETE_REQUEST) {
            return false;
        }
        Track track = pendingTrack;
        pendingTrack = null;
        if (resultCode == Activity.RESULT_OK && track != null) {
            host.playbackQueueController.removeDeletedFile(track);
        }
        return true;
    }

    private void deleteDirectly(Track track) {
        if (Build.VERSION.SDK_INT >= 29) {
            deleteDirectlyApi29(track);
            return;
        }
        try {
            boolean deleted = performDelete(track.asUri());
            postDeleteResult(track, deleted);
        } catch (FileNotFoundException error) {
            host.runOnUiThread(this::showFailure);
        } catch (RuntimeException error) {
            host.runOnUiThread(this::showFailure);
        }
    }

    @RequiresApi(29)
    private void deleteDirectlyApi29(Track track) {
        try {
            boolean deleted = performDelete(track.asUri());
            postDeleteResult(track, deleted);
        } catch (RecoverableSecurityException recoverable) {
            host.runOnUiThread(() -> startRequest(
                    recoverable.getUserAction().getActionIntent()));
        } catch (FileNotFoundException | RuntimeException error) {
            host.runOnUiThread(this::showFailure);
        }
    }

    private boolean performDelete(Uri uri) throws FileNotFoundException {
        return DocumentsContract.isDocumentUri(host, uri)
                ? DocumentsContract.deleteDocument(host.getContentResolver(), uri)
                : host.getContentResolver().delete(uri, null, null) > 0;
    }

    private void postDeleteResult(Track track, boolean deleted) {
        host.runOnUiThread(() -> finishDirectDelete(track, deleted));
    }

    private void finishDirectDelete(Track track, boolean deleted) {
        pendingTrack = null;
        if (deleted) {
            host.playbackQueueController.removeDeletedFile(track);
        } else {
            showFailure();
        }
    }

    private void startRequest(PendingIntent request) {
        try {
            host.startIntentSenderForResult(request.getIntentSender(), DELETE_REQUEST,
                    null, 0, 0, 0);
        } catch (android.content.IntentSender.SendIntentException error) {
            pendingTrack = null;
            showFailure();
        }
    }

    private void showFailure() {
        Toast.makeText(host, host.tr("The file could not be deleted",
                "Не удалось удалить файл"), Toast.LENGTH_LONG).show();
    }

    @Override
    public void close() {
        pendingTrack = null;
        executor.shutdownNow();
    }
}
