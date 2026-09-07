package com.dumuzeyn.mp3player

import com.dumuzeyn.mp3player.library.SongDiagnostics

/** Owns reusable confirmations and the local-library diagnostics dialog. */
class LibraryDiagnosticsDialogController(private val host: MainActivityCore) {
    private val dialogs = DialogController(host)

    fun openSongDiagnostics() {
        val result = SongDiagnostics.inspect(host, host.libraryState.tracks)
        val problems = if (result.problemTitles.isEmpty()) {
            ""
        } else {
            "\n" + host.tr("Problem tracks:", "Проблемные треки:") + result.problemTitles
        }
        val message = host.tr("Available: ", "Доступно: ") + result.available +
            "\n" + host.tr("Unavailable: ", "Недоступно: ") + result.unavailable +
            "\n" + host.tr("With duration: ", "С длительностью: ") + result.withDuration +
            "\n" + host.tr("Without duration: ", "Без длительности: ") + result.withoutDuration +
            problems
        dialogs.showConfirmation(
            host.tr("Song check", "Проверка песен"),
            message,
            Runnable {},
        )
    }

    fun confirm(title: String, message: String, yesAction: Runnable) {
        dialogs.showConfirmation(title, message, yesAction)
    }

    fun action(
        title: String,
        message: String,
        negativeLabel: String,
        positiveLabel: String,
        action: Runnable,
    ) {
        dialogs.showConfirmation(title, message, negativeLabel, positiveLabel, action)
    }

    fun action(
        title: String,
        message: String,
        negativeLabel: String,
        positiveLabel: String,
        emphasizePositive: Boolean,
        action: Runnable,
    ) {
        dialogs.showConfirmation(
            title,
            message,
            negativeLabel,
            positiveLabel,
            emphasizePositive,
            action,
        )
    }
}
