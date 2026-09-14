package com.dumuzeyn.mp3player

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.HorizontalScrollView
import android.widget.LinearLayout

internal class AudioEditorWorkspaceView(
    private val host: MainActivityCore,
    project: AudioEditProject,
    selectedClipId: String?,
    mutedLanes: Set<Int>,
    select: (AudioEditClip) -> Unit,
    toggleLane: (Int) -> Unit,
) : LinearLayout(host) {
    init {
        id = R.id.editor_workspace
        orientation = VERTICAL
        setPadding(host.dp(10), host.dp(8), host.dp(10), host.dp(10))
        host.uiFactory.applyCardStyle(this)

        addView(
            AudioEditorPreviewControls(host, {
                project.copy(clips = project.clips.map { clip ->
                    if (clip.lane in mutedLanes) clip.copy(gain = 0f) else clip
                })
            }),
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
        val laneButtons = LinearLayout(host).apply { orientation = HORIZONTAL }
        project.clips.map(AudioEditClip::lane).distinct().sorted().forEach { lane ->
            val muted = lane in mutedLanes
            laneButtons.addView(host.uiFactory.button("${lane + 1} ${if (muted) "×" else "♪"}").apply {
                minHeight = 0
                minimumWidth = 0
                textSize = 13f
                setPadding(host.dp(5), 0, host.dp(5), 0)
                contentDescription = if (muted) {
                    host.tr("Unmute lane ${lane + 1}", "Включить звук дорожки ${lane + 1}")
                } else {
                    host.tr("Mute lane ${lane + 1}", "Выключить звук дорожки ${lane + 1}")
                }
                if (muted) {
                    setTextColor(Color.rgb(35, 28, 8))
                    background = GradientDrawable().apply {
                        cornerRadius = host.dp(8).toFloat()
                        setColor(host.yellow)
                    }
                } else {
                    host.uiFactory.applySecondaryButtonStyle(this)
                }
                setOnClickListener { toggleLane(lane) }
            }, LayoutParams(host.dp(64), host.dp(36)).apply {
                setMargins(host.dp(2), host.dp(2), host.dp(2), host.dp(2))
            })
        }
        addView(HorizontalScrollView(host).apply {
            isHorizontalScrollBarEnabled = false
            addView(laneButtons)
        }, LayoutParams(LayoutParams.MATCH_PARENT, host.dp(40)))
        addView(host.uiFactory.lineView(), LayoutParams(LayoutParams.MATCH_PARENT, host.dp(1)).apply {
            setMargins(0, host.dp(4), 0, host.dp(6))
        })
        addView(
            AudioEditorTimelineView(host, project, selectedClipId, select),
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
    }
}
