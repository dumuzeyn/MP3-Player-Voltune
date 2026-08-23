package com.dumuzeyn.mp3player;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

final class UiFactory {
    private final MainActivityCore host;
    private final ButtonFactory buttons;

    UiFactory(MainActivityCore host) {
        this.host = host;
        this.buttons = new ButtonFactory(host);
    }

    LinearLayout row() {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(16);
        return row;
    }

    TextView text(String value, int size, boolean bold) {
        TextView text = new OutlinedTextView(host);
        text.setText(value);
        text.setTextColor(host.fg);
        text.setTextSize(size);
        text.setGravity(16);
        text.setTypeface(null, bold ? Typeface.BOLD : Typeface.NORMAL);
        text.setSingleLine(false);
        host.themeController.applyTextOutline(text);
        return text;
    }

    TextView dialogTitle(String value) {
        return dialogTitle(value, 22);
    }

    TextView dialogTitle(String value, int size) {
        TextView title = text(value, size, true);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        title.setMinHeight(host.dp(46));
        title.setPadding(0, host.dp(4), 0, host.dp(10));
        return title;
    }

    LinearLayout.LayoutParams dialogTitleParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    void makeMarquee(TextView text) {
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        text.setMarqueeRepeatLimit(-1);
        text.setSelected(true);
        text.setFocusable(true);
        text.setFocusableInTouchMode(true);
    }

    Button button(String label) {
        return buttons.button(label);
    }

    Button icon(String symbol) {
        return buttons.icon(symbol);
    }

    Button shuffleButton() {
        return buttons.shuffleButton();
    }

    void applyPlainIconStyle(Button button, int color) {
        buttons.applyPlainIcon(button, color);
    }

    void applyPlainIconStyle(Button button) {
        buttons.applyPlainIcon(button,
                host.appearanceState.dark ? Color.rgb(230, 226, 236) : host.primaryText);
    }

    GradientDrawable cardBackground() {
        return cardBackground(host.appearanceState.dialogCardOpacity);
    }

    GradientDrawable cardBackground(int opacity) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(host.cardSurfaceColor(host.card, opacity));
        drawable.setCornerRadius(host.dp(16));
        drawable.setStroke(host.dp(1), host.cardStroke);
        return drawable;
    }

    void applyCardStyle(View view) {
        view.setBackground(cardBackground());
        TextOutlinePolicy.markCardSurface(view, true);
        view.setElevation(host.dp(1));
    }

    void applyCardStyle(View view, int opacity) {
        view.setBackground(cardBackground(opacity));
        TextOutlinePolicy.markCardSurface(view, true);
        view.setElevation(host.dp(1));
    }

    void applyPrimaryButtonStyle(Button button) {
        buttons.applyPrimary(button);
    }

    void applySecondaryButtonStyle(Button button) {
        buttons.applySecondary(button);
    }

    void applySecondaryButtonStyle(Button button, int opacity) {
        buttons.applySecondary(button, opacity);
    }

    void applyPlayerToolStyle(Button button, boolean active) {
        buttons.applyPlayerTool(button, active);
    }

    void applySeekBarColors(SeekBar seekBar) {
        if (Build.VERSION.SDK_INT >= 21) {
            seekBar.setProgressTintList(ColorStateList.valueOf(host.purple));
            seekBar.setThumbTintList(ColorStateList.valueOf(host.yellow));
            seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(host.purpleSoft));
        }
    }

    LinearLayout.LayoutParams square(int size) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(host.dp(size), host.dp(size));
        params.setMargins(host.dp(4), host.dp(4), host.dp(4), host.dp(4));
        return params;
    }

    View spaced(View view) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, host.dp(2), 0, host.dp(2));
        view.setLayoutParams(params);
        return view;
    }

    void setSurface(View view, int color, boolean outlined) {
        setSurface(view, color, outlined, host.appearanceState.dialogCardOpacity);
    }

    void setSurface(View view, int color, boolean outlined, int opacity) {
        int surfaceColor = color == host.card || color == host.panel
                ? host.cardSurfaceColor(color, opacity)
                : color;
        view.setBackground(rounded(surfaceColor, outlined));
        TextOutlinePolicy.markCardSurface(view, true);
    }

    View lineView() {
        View line = new View(host);
        line.setBackgroundColor(host.line);
        return line;
    }

    ImageView coverView() {
        ImageView cover = new RotatingCoverImageView(host);
        cover.setBackgroundColor(Color.TRANSPARENT);
        return cover;
    }

    FrameLayout shade() {
        SwipeDismissFrameLayout shade = new SwipeDismissFrameLayout(host);
        int channel = host.appearanceState.dark ? 0 : 255;
        shade.setBackgroundColor(Color.argb(190, channel, channel, channel));
        Runnable dismiss = () -> {
            if (shade.getParent() != null) {
                host.overlayHost.removeView(shade);
            }
            host.playerUiController.updateMini();
        };
        shade.setDismissAction(dismiss);
        shade.setOnClickListener(view -> dismiss.run());
        return shade;
    }

    LinearLayout panelCard() {
        LinearLayout panel = new LinearLayout(host);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(host.dp(12), host.dp(12), host.dp(12), host.dp(12));
        applyCardStyle(panel);
        panel.setOnClickListener(view -> { });
        return panel;
    }

    private GradientDrawable rounded(int color, boolean outlined) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(host.dp(outlined ? 16 : 14));
        drawable.setStroke(outlined ? 1 : 0, outlined ? host.cardStroke : color);
        return drawable;
    }
}
