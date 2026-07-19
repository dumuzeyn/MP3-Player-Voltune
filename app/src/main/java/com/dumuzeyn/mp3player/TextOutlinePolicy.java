package com.dumuzeyn.mp3player;

import android.view.View;
import android.view.ViewParent;

/** Decides whether text is already protected by an opaque or translucent card surface. */
final class TextOutlinePolicy {
    private TextOutlinePolicy() {
    }

    static void markCardSurface(View view, boolean cardSurface) {
        view.setTag(R.id.text_card_surface, cardSurface ? Boolean.TRUE : null);
    }

    static boolean isInsideCard(View view) {
        View current = view;
        while (current != null) {
            if (Boolean.TRUE.equals(current.getTag(R.id.text_card_surface))) {
                return true;
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }
}
