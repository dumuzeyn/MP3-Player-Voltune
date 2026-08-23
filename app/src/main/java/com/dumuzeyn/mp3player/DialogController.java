package com.dumuzeyn.mp3player;

import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

final class DialogController {
    private final MainActivityCore host;

    DialogController(MainActivityCore host) {
        this.host = host;
    }

    void showConfirmation(String title, String message, Runnable yesAction) {
        showConfirmation(title, message, host.tr("No", "Нет"), host.tr("Yes", "Да"), yesAction);
    }

    void showConfirmation(String title, String message, String negativeLabel,
            String positiveLabel, Runnable yesAction) {
        showConfirmation(title, message, negativeLabel, positiveLabel, true, yesAction);
    }

    void showConfirmation(String title, String message, String negativeLabel,
            String positiveLabel, boolean emphasizePositive, Runnable yesAction) {
        final FrameLayout shade = host.uiFactory.shade();
        LinearLayout panel = host.uiFactory.panelCard();
        panel.setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16));
        panel.addView(host.uiFactory.dialogTitle(title), host.uiFactory.dialogTitleParams());
        TextView messageView = host.uiFactory.text(message, 16, false);
        messageView.setTextColor(host.muted);
        messageView.setPadding(0, host.dp(4), 0, host.dp(14));
        panel.addView(messageView, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout actions = host.uiFactory.row();
        Button no = host.uiFactory.button(negativeLabel);
        if (!emphasizePositive) {
            host.uiFactory.applyPrimaryButtonStyle(no);
        }
        no.setOnClickListener(view -> close(shade));
        actions.addView(no, new LinearLayout.LayoutParams(0, host.dp(54), 1.0f));
        Button yes = host.uiFactory.button(positiveLabel);
        if (emphasizePositive) {
            host.uiFactory.applyPrimaryButtonStyle(yes);
        }
        yes.setOnClickListener(view -> {
            close(shade);
            yesAction.run();
        });
        actions.addView(yes, new LinearLayout.LayoutParams(0, host.dp(54), 1.0f));
        panel.addView(actions);
        shade.addView(panel, host.centerParams(host.dp(330), -2));
        host.overlayHost.addView(shade);
        host.playerUiController.updateMini();
    }

    private void close(FrameLayout shade) {
        if (shade.getParent() != null) {
            host.overlayHost.removeView(shade);
        }
        host.playerUiController.updateMini();
    }
}
