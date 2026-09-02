package com.dumuzeyn.mp3player

import android.widget.LinearLayout
import android.widget.ScrollView

/** Immutable data required to commit a tab transition. */
internal class TabTransitionRequest(
    @JvmField val targetTab: Int,
    @JvmField val direction: Int,
    @JvmField val recordHistory: Boolean,
    searchQuery: String?,
    @JvmField val scrollView: ScrollView?,
    @JvmField val content: LinearLayout?,
    @JvmField val previewState: MainRenderer.PreviewState?,
) {
    @JvmField val searchQuery: String = searchQuery.orEmpty()

    companion object {
        @JvmStatic
        fun withoutPreview(
            targetTab: Int,
            direction: Int,
            recordHistory: Boolean,
            searchQuery: String?,
        ): TabTransitionRequest = TabTransitionRequest(
            targetTab,
            direction,
            recordHistory,
            searchQuery,
            null,
            null,
            null,
        )
    }
}
