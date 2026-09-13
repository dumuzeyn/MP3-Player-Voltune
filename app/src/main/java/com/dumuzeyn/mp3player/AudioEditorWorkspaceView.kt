package com.dumuzeyn.mp3player

import android.widget.LinearLayout

internal class AudioEditorWorkspaceView(
    private val host: MainActivityCore,
    project: AudioEditProject,
    selectedClipId: String?,
    select: (AudioEditClip) -> Unit,
) : LinearLayout(host) {
    init {
        id = R.id.editor_workspace
        orientation = VERTICAL
        setPadding(host.dp(10), host.dp(8), host.dp(10), host.dp(10))
        host.uiFactory.applyCardStyle(this)

        addView(
            AudioEditorPreviewControls(host, { project }),
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
        addView(host.uiFactory.lineView(), LayoutParams(LayoutParams.MATCH_PARENT, host.dp(1)).apply {
            setMargins(0, host.dp(4), 0, host.dp(6))
        })
        addView(
            AudioEditorTimelineView(host, project, selectedClipId, select),
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
    }
}
