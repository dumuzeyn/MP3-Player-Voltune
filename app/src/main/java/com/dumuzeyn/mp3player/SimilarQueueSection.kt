package com.dumuzeyn.mp3player

import android.text.TextUtils
import android.view.Gravity
import android.widget.LinearLayout

/** Home action that creates a queue around the current song and its thematic group. */
internal class SimilarQueueSection(host: MainActivityCore) : LinearLayout(host) {
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL

        val count = RandomQueueCountView(
            host,
            host.libraryState.tracks.size,
            "similar",
            "похожей",
            R.id.similar_queue_count,
        )
        val create = host.uiFactory.button(
            host.tr("Create similar queue", "Создать похожую очередь"),
        ).apply {
            id = R.id.similar_queue_button
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            contentDescription = host.tr("Create similar queue", "Создать похожую очередь")
            host.uiFactory.applyPrimaryButtonStyle(this)
            setOnClickListener { host.playbackQueueController.playSimilar(count.value) }
        }

        addView(
            create,
            LayoutParams(0, host.uiFactory.libraryCardHeight(), 1f).apply {
                setMargins(0, host.dp(2), host.dp(4), host.dp(2))
            },
        )
        addView(
            count,
            LayoutParams(host.dp(68), host.uiFactory.libraryCardHeight()).apply {
                setMargins(0, host.dp(2), 0, host.dp(2))
            },
        )
    }
}
