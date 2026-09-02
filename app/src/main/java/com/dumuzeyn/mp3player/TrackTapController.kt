package com.dumuzeyn.mp3player

import android.widget.ImageView

object TrackTapController {
    @JvmStatic
    fun handle(host: MainActivityCore, track: Track?, cover: ImageView?) {
        if (track == null) return
        if (cover != null) host.artworkUi.seedFromView(cover, track)
        if (TrackTapPolicy.action(host.isCurrent(track)) == TrackTapPolicy.Action.PLAY) {
            host.playbackQueueController.playTrack(track)
            return
        }
        host.navigationState.fullPlayerOpening = true
        host.playerUiController.openFullPlayer()
    }
}
