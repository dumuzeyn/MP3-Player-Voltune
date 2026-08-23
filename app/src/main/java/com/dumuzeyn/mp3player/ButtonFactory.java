package com.dumuzeyn.mp3player;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.widget.Button;
import androidx.core.widget.TextViewCompat;

final class ButtonFactory {
    private final MainActivityCore host;

    ButtonFactory(MainActivityCore host) {
        this.host = host;
    }

    Button button(String label) {
        Button button = new OutlinedButton(host);
        button.setText(label);
        button.setTextColor(host.fg);
        button.setTextSize(14.0f);
        button.setAllCaps(false);
        button.setGravity(17);
        button.setIncludeFontPadding(true);
        button.setPadding(0, 0, 0, 0);
        button.setStateListAnimator(null);
        button.setElevation(0.0f);
        button.setTranslationZ(0.0f);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setSingleLine(false);
        button.setMaxLines(2);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                button, 10, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setForeground(new RippleDrawable(
                ColorStateList.valueOf(Color.argb(42, Color.red(host.fg),
                        Color.green(host.fg), Color.blue(host.fg))),
                null, rippleMask()));
        host.themeController.applyTextOutline(button);
        return button;
    }

    Button icon(String symbol) {
        Button button = button(symbol);
        TextViewCompat.setAutoSizeTextTypeWithDefaults(
                button, TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE);
        button.setSingleLine(true);
        button.setTextSize(24.0f);
        return button;
    }

    Button shuffleButton() {
        Button button = icon("⇄");
        button.setTextSize(31.0f);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        return button;
    }

    void applyPlainIcon(Button button, int color) {
        button.setTextColor(color);
        button.setBackgroundColor(Color.TRANSPARENT);
        TextOutlinePolicy.markCardSurface(button, false);
        button.setElevation(0.0f);
        button.setTranslationZ(0.0f);
    }

    void applyPrimary(Button button) {
        button.setTextColor(Color.WHITE);
        button.setBackground(background(host.purple, false));
        TextOutlinePolicy.markCardSurface(button, true);
    }

    void applySecondary(Button button) {
        applySecondary(button, host.appearanceState.cardOpacity);
    }

    void applySecondary(Button button, int opacity) {
        button.setTextColor(host.primaryText);
        GradientDrawable drawable = background(host.cardSurfaceColor(host.card, opacity), true);
        drawable.setStroke(host.dp(1), host.cardStroke);
        button.setBackground(drawable);
        TextOutlinePolicy.markCardSurface(button, true);
    }

    void applyPlayerTool(Button button, boolean active) {
        button.setSingleLine(true);
        button.setTextSize(14.0f);
        button.setTextColor(active ? host.yellow : host.primaryText);
        GradientDrawable drawable = background(
                host.cardSurfaceColor(host.card, Math.max(68, host.appearanceState.cardOpacity)), false);
        drawable.setStroke(host.dp(1), active ? host.purple : host.cardStroke);
        button.setBackground(drawable);
        TextOutlinePolicy.markCardSurface(button, true);
    }

    private GradientDrawable background(int color, boolean outlined) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(host.dp(16));
        if (outlined) {
            drawable.setStroke(host.dp(1), host.cardStroke);
        }
        return drawable;
    }

    private GradientDrawable rippleMask() {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(host.dp(16));
        return mask;
    }
}
