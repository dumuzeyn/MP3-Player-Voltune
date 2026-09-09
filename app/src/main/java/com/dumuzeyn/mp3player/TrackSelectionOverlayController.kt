package com.dumuzeyn.mp3player

import android.graphics.Color
import android.text.Editable
import android.text.InputFilter
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Searchable multi-track chooser used by playlists, favorites, and the queue. */
internal class TrackSelectionOverlayController(private val host: MainActivityCore) {
    fun open(
        title: String,
        selected: HashSet<String>,
        done: OverlayController.SelectionDone,
    ) {
        val shade = host.uiFactory.shade()
        val owner = "selection-${Integer.toHexString(System.identityHashCode(shade))}"
        cancelSearchOnDetach(shade, owner)
        val panel = host.uiFactory.panelCard().apply {
            addView(header(title, shade, owner, selected, done))
        }
        val search = searchField()
        panel.addView(search, searchParams())
        val rows = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        renderRows(rows, selected, host.libraryState.tracks)
        search.addTextChangedListener(
            object : SimpleTextWatcher() {
                override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) {
                    host.trackSearchController.filter(
                        owner,
                        host.libraryState.tracks,
                        value?.toString().orEmpty(),
                    ) { filtered ->
                        if (shade.parent != null) renderRows(rows, selected, filtered)
                    }
                }
            },
        )
        val scroll = ScrollView(host).apply { addView(rows) }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        shade.addView(panel, host.bottomParams())
        host.overlayHost.addView(shade)
        host.playerUiController.updateMini()
    }

    private fun cancelSearchOnDetach(shade: FrameLayout, owner: String) {
        shade.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) = Unit

                override fun onViewDetachedFromWindow(view: View) {
                    host.trackSearchController.cancel(owner)
                }
            },
        )
    }

    private fun header(
        title: String,
        shade: FrameLayout,
        owner: String,
        selected: HashSet<String>,
        done: OverlayController.SelectionDone,
    ): LinearLayout = host.uiFactory.row().apply {
        addView(
            host.uiFactory.text(title, 20, true),
            LinearLayout.LayoutParams(0, host.dp(58), 1f),
        )
        val complete = host.uiFactory.icon("✔").apply {
            setOnClickListener {
                host.trackSearchController.cancel(owner)
                host.overlayHost.removeView(shade)
                done.done(selected)
                host.playerUiController.updateMini()
            }
        }
        addView(complete, host.uiFactory.square(52))
        val close = host.uiFactory.icon("×").apply {
            setOnClickListener { close(shade, owner) }
        }
        addView(close, host.uiFactory.square(52))
    }

    private fun renderRows(parent: LinearLayout, selected: HashSet<String>, tracks: List<Track>) {
        parent.removeAllViews()
        tracks.forEach { track ->
            val row = host.uiFactory.row().apply {
                setPadding(host.dp(10), host.dp(8), host.dp(10), host.dp(8))
            }
            val cover = host.uiFactory.coverView()
            host.artworkUi.loadUnregisteredCover(
                cover,
                track,
                fallbackColor(),
                CoverLoader.THUMB_SIZE,
            )
            row.addView(cover, host.uiFactory.square(58))
            val title = host.uiFactory.text(track.title, 17, true).apply {
                setSingleLine(true)
                ellipsize = TextUtils.TruncateAt.END
                setPadding(host.dp(12), 0, host.dp(8), 0)
            }
            row.addView(title, LinearLayout.LayoutParams(0, host.dp(70), 1f))
            val mark = host.uiFactory.icon("")
            row.addView(mark, host.uiFactory.square(48))
            val play = host.uiFactory.icon(
                if (host.isCurrent(track) && host.isPlaybackPlaying()) "Ⅱ" else "▶",
            ).apply {
                setOnClickListener {
                    if (host.isCurrent(track)) {
                        host.playbackQueueController.toggleOrStart()
                    } else {
                        host.playbackQueueController.playTrack(track)
                    }
                    renderRows(parent, selected, tracks)
                }
            }
            row.addView(play, host.uiFactory.square(48))
            val refresh = Runnable {
                applyAppearance(row, cover, title, mark, play, selected.contains(track.uri))
            }
            mark.setOnClickListener {
                if (!selected.add(track.uri)) selected.remove(track.uri)
                refresh.run()
            }
            refresh.run()
            parent.addView(host.uiFactory.spaced(row))
        }
    }

    private fun applyAppearance(
        row: LinearLayout,
        cover: ImageView,
        title: TextView,
        mark: Button,
        play: Button,
        selected: Boolean,
    ) {
        val selectedSurface = if (host.appearanceState.dark) host.purpleDark else host.purpleSoft
        val selectedContent = ThemeManager.readableOn(selectedSurface)
        host.uiFactory.setSurface(row, if (selected) selectedSurface else host.panel, false)
        cover.setBackgroundColor(if (selected) selectedSurface else fallbackColor())
        title.setTextColor(if (selected) selectedContent else host.fg)
        mark.text = if (selected) "✔" else "+"
        host.uiFactory.applyPlainIconStyle(mark, if (selected) selectedContent else host.purple)
        host.uiFactory.applyPlainIconStyle(play, if (selected) selectedContent else host.purple)
    }

    private fun searchField(): EditText = EditText(host).apply {
        setSingleLine(true)
        hint = host.tr("Search songs", "Поиск песен")
        setTextColor(host.fg)
        setHintTextColor(host.muted)
        textSize = 16f
        setPadding(host.dp(14), 0, host.dp(14), 0)
        filters = arrayOf(InputFilter.LengthFilter(80))
        host.uiFactory.setSurface(this, host.panel, true)
    }

    private fun searchParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, host.dp(58)).apply {
            setMargins(0, host.dp(8), 0, host.dp(12))
        }

    private fun fallbackColor(): Int {
        val channel = if (host.appearanceState.dark) 28 else 235
        return Color.rgb(channel, channel, channel)
    }

    private fun close(shade: FrameLayout, owner: String) {
        host.trackSearchController.cancel(owner)
        if (shade.parent != null) host.overlayHost.removeView(shade)
        host.playerUiController.updateMini()
    }

    private abstract class SimpleTextWatcher : TextWatcher {
        override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun afterTextChanged(text: Editable?) = Unit
    }
}
