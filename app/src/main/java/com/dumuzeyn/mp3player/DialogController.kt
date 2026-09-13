package com.dumuzeyn.mp3player

import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

class DialogController(private val host: MainActivityCore) {
    fun showConfirmation(title: String, message: String, yesAction: Runnable) {
        showConfirmation(
            title,
            message,
            host.tr("No", "Нет"),
            host.tr("Yes", "Да"),
            yesAction,
        )
    }

    fun showConfirmation(
        title: String,
        message: String,
        negativeLabel: String,
        positiveLabel: String,
        yesAction: Runnable,
    ) {
        showConfirmation(title, message, negativeLabel, positiveLabel, true, yesAction)
    }

    fun showConfirmation(
        title: String,
        message: String,
        negativeLabel: String,
        positiveLabel: String,
        emphasizePositive: Boolean,
        yesAction: Runnable,
    ) {
        val shade: FrameLayout = host.uiFactory.shade()
        val panel: LinearLayout = host.uiFactory.panelCard()
        panel.setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16))
        panel.addView(host.uiFactory.dialogTitle(title), host.uiFactory.dialogTitleParams())

        val messageView = host.uiFactory.text(message, 16, false)
        messageView.setTextColor(host.muted)
        messageView.setPadding(0, host.dp(4), 0, host.dp(14))
        val messageScroll = ScrollView(host).apply {
            addView(messageView, FrameLayout.LayoutParams(-1, -2))
        }
        panel.addView(messageScroll, LinearLayout.LayoutParams(-1, -2, 1f))

        val actions = host.uiFactory.row()
        val no = host.uiFactory.button(negativeLabel)
        if (!emphasizePositive) host.uiFactory.applyPrimaryButtonStyle(no)
        no.setOnClickListener { close(shade) }
        actions.addView(no, LinearLayout.LayoutParams(0, host.dp(54), 1f))

        val yes = host.uiFactory.button(positiveLabel)
        if (emphasizePositive) host.uiFactory.applyPrimaryButtonStyle(yes)
        yes.setOnClickListener {
            close(shade)
            yesAction.run()
        }
        actions.addView(yes, LinearLayout.LayoutParams(0, host.dp(54), 1f))

        panel.addView(actions)
        shade.addView(panel, host.centerParams(host.dp(330), -2))
        host.overlayHost.addView(shade)
        host.playerUiController.updateMini()
    }

    private fun close(shade: FrameLayout) {
        if (shade.parent != null) host.overlayHost.removeView(shade)
        host.playerUiController.updateMini()
    }
}
