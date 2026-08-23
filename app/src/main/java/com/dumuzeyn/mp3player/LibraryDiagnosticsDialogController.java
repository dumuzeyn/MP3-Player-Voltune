package com.dumuzeyn.mp3player;

import com.dumuzeyn.mp3player.library.SongDiagnostics;

/** Owns reusable confirmations and the local-library diagnostics dialog. */
final class LibraryDiagnosticsDialogController {
    private final MainActivityCore host;
    private final DialogController dialogs;

    LibraryDiagnosticsDialogController(MainActivityCore host) {
        this.host = host;
        this.dialogs = new DialogController(host);
    }

    void openSongDiagnostics() {
        SongDiagnostics.Result result = SongDiagnostics.inspect(host, host.libraryState.tracks);
        String message = host.tr("Available: ", "Доступно: ") + result.available
                + "\n" + host.tr("Unavailable: ", "Недоступно: ") + result.unavailable
                + "\n" + host.tr("With duration: ", "С длительностью: ")
                + result.withDuration
                + "\n" + host.tr("Without duration: ", "Без длительности: ")
                + result.withoutDuration
                + (result.problemTitles.isEmpty() ? ""
                : "\n" + host.tr("Problem tracks:", "Проблемные треки:")
                + result.problemTitles);
        dialogs.showConfirmation(host.tr("Song check", "Проверка песен"), message, () -> { });
    }

    void confirm(String title, String message, Runnable yesAction) {
        dialogs.showConfirmation(title, message, yesAction);
    }

    void action(String title, String message, String negativeLabel, String positiveLabel,
            Runnable action) {
        dialogs.showConfirmation(title, message, negativeLabel, positiveLabel, action);
    }

    void action(String title, String message, String negativeLabel, String positiveLabel,
            boolean emphasizePositive, Runnable action) {
        dialogs.showConfirmation(title, message, negativeLabel, positiveLabel,
                emphasizePositive, action);
    }
}
