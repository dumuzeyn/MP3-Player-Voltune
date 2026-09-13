package com.dumuzeyn.mp3player

import android.view.View

/** Decides whether text is already protected by an opaque or translucent card surface. */
object TextOutlinePolicy {
    @JvmStatic
    fun markCardSurface(view: View, cardSurface: Boolean) {
        view.setTag(R.id.text_card_surface, true.takeIf { cardSurface })
    }

    @JvmStatic
    fun isInsideCard(view: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current.getTag(R.id.text_card_surface) == true) return true
            current = current.parent as? View
        }
        return false
    }
}
