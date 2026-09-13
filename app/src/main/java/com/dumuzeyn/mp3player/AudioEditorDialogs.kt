package com.dumuzeyn.mp3player

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

internal class AudioEditorDialogs(private val host: MainActivityCore) {
    enum class Focus {
        POSITION,
        ANALYSIS,
        TRIM,
        VOLUME,
        SPLIT,
        REMOVE_RANGE,
        CLEAN_SPEECH,
        SEPARATE_STEMS,
        REMOVE_VOCALS,
    }

    private val controller get() = host.audioEditorController

    fun chooseTrack(lane: Int) {
        if (controller.busy) return
        val shade = host.uiFactory.shade()
        val panel = host.uiFactory.panelCard()
        panel.addView(host.uiFactory.dialogTitle(host.tr("Add audio", "Добавить аудио")))
        val search = EditText(host).apply {
            hint = host.tr("Search songs", "Поиск песен")
            setTextColor(host.primaryText)
            setHintTextColor(host.secondaryText)
            isSingleLine = true
        }
        panel.addView(search, LinearLayout.LayoutParams(-1, host.dp(48)))
        val tracks = host.libraryState.tracks.toList()
        val adapter = object : RecyclerView.Adapter<TrackHolder>() {
            var visible = tracks
            override fun getItemCount() = visible.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackHolder =
                TrackHolder(host.uiFactory.text("", 15, false).apply {
                    setPadding(host.dp(8), host.dp(6), host.dp(8), host.dp(6))
                    minHeight = host.dp(56)
                    layoutParams = RecyclerView.LayoutParams(-1, -2)
                })
            override fun onBindViewHolder(holder: TrackHolder, position: Int) {
                val track = visible[position]
                holder.label.text = "${track.title}\n${host.formatTrackDuration(track)}"
                holder.label.setOnClickListener {
                    host.overlayHost.removeView(shade)
                    controller.add(track, lane)
                }
            }
        }
        val list = RecyclerView(host).apply {
            layoutManager = LinearLayoutManager(host)
            this.adapter = adapter
        }
        panel.addView(list, LinearLayout.LayoutParams(-1, bodyHeight(280, 240)))
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.visible = tracks.filter { it.title.contains(s.toString(), ignoreCase = true) }
                adapter.notifyDataSetChanged()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        panel.addView(action(host.tr("Import files", "Импорт файлов")) {
            host.overlayHost.removeView(shade)
            host.audioImportController.openFiles()
        })
        panel.addView(action(host.tr("Close", "Закрыть")) { host.overlayHost.removeView(shade) })
        shade.addView(panel, host.centerParams(host.dp(360), -2))
        host.overlayHost.addView(shade)
    }

    @JvmOverloads
    fun edit(clip: AudioEditClip, focus: Focus = Focus.POSITION) {
        if (controller.busy) return
        val shade = host.uiFactory.shade()
        val panel = host.uiFactory.panelCard()
        panel.addView(host.uiFactory.centeredDialogTitle(focusTitle(focus), 18))
        val content = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(host).apply { addView(content) }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, bodyHeight(390, 210)))
        content.addView(host.uiFactory.text(clip.title, 15, true))

        fun close() = host.overlayHost.removeView(shade)
        fun preview(selected: () -> AudioEditClip) {
            content.addView(AudioEditorPreviewControls(host, {
                AudioEditProject(listOf(selected().copy(offsetMs = 0, lane = 0)))
            }, stopOnDetach = true))
        }
        fun primary(label: String, run: () -> Unit) {
            panel.addView(action(label, run).apply { host.uiFactory.applyPrimaryButtonStyle(this) })
        }
        fun lanePicker(): Spinner {
            content.addView(host.uiFactory.text(host.tr("Lane", "Дорожка"), 14, false))
            return Spinner(host).apply {
                adapter = ArrayAdapter(host, android.R.layout.simple_spinner_dropdown_item,
                    (1..AudioEditClip.MAX_LANES).map(Int::toString))
                setSelection(clip.lane)
                content.addView(this, LinearLayout.LayoutParams(-1, host.dp(48)))
            }
        }
        fun waveform(): AudioEditorWaveformView = AudioEditorWaveformView(host, clip).also {
            content.addView(it, LinearLayout.LayoutParams(-1, minOf(host.dp(120), bodyHeight(390, 210))))
        }
        fun range(waveform: AudioEditorWaveformView): Pair<EditText, EditText> {
            val from = secondsField(content, host.tr("Start, s", "Начало, с"), clip.startMs)
            val to = secondsField(content, host.tr("End, s", "Конец, с"), clip.endMs)
            var updating = false
            waveform.onSelection = { start, end ->
                updating = true
                from.setText(seconds(start))
                to.setText(seconds(end))
                updating = false
            }
            val watcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (!updating) runCatching { waveform.setSelection(millis(from), millis(to)) }
                }
                override fun afterTextChanged(s: Editable?) = Unit
            }
            from.addTextChangedListener(watcher)
            to.addTextChangedListener(watcher)
            return from to to
        }

        when (focus) {
            Focus.POSITION -> {
                val offset = secondsField(content, host.tr("Timeline position, s", "Позиция на шкале, с"), clip.offsetMs)
                val lane = lanePicker()
                preview { clip }
                primary(host.tr("Apply position", "Применить положение")) {
                    runCatching { clip.copy(offsetMs = millis(offset), lane = lane.selectedItemPosition) }
                        .onSuccess { if (controller.change { project -> project.replace(it) }) close() }
                        .onFailure { offset.error = host.tr("Check the position", "Проверьте положение") }
                }
            }
            Focus.ANALYSIS -> {
                val waveform = waveform()
                val analysis = AudioEditorAnalysisView(host, clip)
                waveform.onSelection = analysis::selection
                content.addView(analysis)
                preview { clip }
            }
            Focus.TRIM -> {
                val waveform = waveform()
                val (from, to) = range(waveform)
                preview { clip.copy(startMs = millis(from), endMs = millis(to)) }
                primary(host.tr("Apply trim", "Применить обрезку")) {
                    runCatching { clip.copy(startMs = millis(from), endMs = millis(to)) }
                        .onSuccess { if (controller.change { project -> project.replace(it) }) close() }
                        .onFailure { from.error = host.tr("Check the range", "Проверьте границы") }
                }
            }
            Focus.VOLUME -> {
                val level = host.uiFactory.text(
                    "${host.tr("Volume", "Громкость")}: ${(clip.gain * 100).toInt()}%",
                    14,
                    false,
                )
                content.addView(level)
                val gain = SeekBar(host).apply {
                    max = 100
                    progress = (clip.gain * 100).toInt()
                    contentDescription = host.tr("Clip volume", "Громкость фрагмента")
                    setOnSeekBarChangeListener(listener { value ->
                        level.text = "${host.tr("Volume", "Громкость")}: $value%"
                    })
                }
                host.uiFactory.applySeekBarColors(gain)
                content.addView(gain, LinearLayout.LayoutParams(-1, host.dp(48)))
                preview { clip.copy(gain = gain.progress / 100f) }
                primary(host.tr("Apply volume", "Применить громкость")) {
                    if (controller.change { it.replace(clip.copy(gain = gain.progress / 100f)) }) close()
                }
            }
            Focus.SPLIT -> {
                val waveform = waveform()
                val cutLabel = host.uiFactory.text("", 14, false)
                content.addView(cutLabel)
                val cut = SeekBar(host).apply {
                    max = 1000
                    progress = 500
                    contentDescription = host.tr("Split position", "Точка разделения")
                }
                fun updateCut() {
                    cutLabel.text = host.tr("Split at ", "Разделить в ") + seconds(waveform.cursorMs) +
                        host.tr(" s", " с")
                }
                host.uiFactory.applySeekBarColors(cut)
                cut.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                        if (fromUser) waveform.setCursor(clip.startMs + clip.durationMs * value / 1000)
                        updateCut()
                    }
                    override fun onStartTrackingTouch(bar: SeekBar) = Unit
                    override fun onStopTrackingTouch(bar: SeekBar) = Unit
                })
                waveform.onCursor = { value ->
                    cut.progress = ((value - clip.startMs) * 1000 / clip.durationMs).toInt()
                    updateCut()
                }
                updateCut()
                content.addView(cut, LinearLayout.LayoutParams(-1, host.dp(48)))
                preview { clip }
                primary(host.tr("Split", "Разделить")) {
                    if (controller.change { it.split(clip.id, waveform.cursorMs) }) close()
                }
            }
            Focus.REMOVE_RANGE -> {
                val waveform = waveform()
                val (from, to) = range(waveform)
                preview { clip.copy(startMs = millis(from), endMs = millis(to)) }
                primary(host.tr("Remove selected range", "Удалить выделенный отрезок")) {
                    runCatching { millis(from) to millis(to) }.onSuccess { selected ->
                        if (controller.change { it.removeRange(clip.id, selected.first, selected.second) }) close()
                    }.onFailure { from.error = host.tr("Check the range", "Проверьте границы") }
                }
            }
            Focus.CLEAN_SPEECH -> {
                preview { clip }
                primary(host.tr("Clean speech", "Очистить речь")) {
                    if (controller.processing.cleanSpeech(clip)) close()
                }
            }
            Focus.SEPARATE_STEMS, Focus.REMOVE_VOCALS -> {
                val lane = lanePicker()
                preview { clip }
                val instrumental = focus == Focus.REMOVE_VOCALS
                primary(if (instrumental) host.tr("Remove vocals", "Удалить вокал")
                    else host.tr("Separate into four stems", "Разделить на четыре дорожки")) {
                    runCatching { clip.copy(lane = lane.selectedItemPosition) }
                        .onSuccess { if (controller.processing.separate(it, instrumental)) close() }
                }
            }
        }
        panel.addView(action(host.tr("Cancel", "Отмена")) { host.overlayHost.removeView(shade) })
        shade.addView(panel, host.centerParams(host.dp(360), -2))
        host.overlayHost.addView(shade)
    }

    private fun focusTitle(focus: Focus): String = when (focus) {
        Focus.POSITION -> host.tr("Clip position", "Положение фрагмента")
        Focus.ANALYSIS -> host.tr("BPM and key", "BPM и тональность")
        Focus.TRIM -> host.tr("Trim audio", "Обрезка аудио")
        Focus.VOLUME -> host.tr("Clip volume", "Громкость фрагмента")
        Focus.SPLIT -> host.tr("Split audio", "Разделение аудио")
        Focus.REMOVE_RANGE -> host.tr("Remove a range", "Удаление отрезка")
        Focus.CLEAN_SPEECH -> host.tr("Clean speech", "Очистка речи")
        Focus.SEPARATE_STEMS -> host.tr("Separate stems", "Разделение дорожек")
        Focus.REMOVE_VOCALS -> host.tr("Remove vocals", "Удаление вокала")
    }

    private fun secondsField(parent: LinearLayout, label: String, value: Long): EditText {
        parent.addView(host.uiFactory.text(label, 14, false))
        val input = EditText(host).apply {
            setText(seconds(value))
            contentDescription = label
            setTextColor(host.primaryText)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            isSingleLine = true
        }
        parent.addView(input, LinearLayout.LayoutParams(-1, host.dp(48)))
        return input
    }

    private fun millis(input: EditText): Long {
        val value = input.text.toString().replace(',', '.').toDoubleOrNull()
        require(value != null && value.isFinite() && value >= 0 && value <= 86400)
        return kotlin.math.round(value * 1000).toLong()
    }

    private fun seconds(value: Long) = String.format(Locale.ROOT, "%.3f", value / 1000.0)
    private fun bodyHeight(preferredDp: Int, chromeDp: Int): Int = minOf(host.dp(preferredDp),
        (host.overlayHost.height - host.dp(chromeDp)).coerceAtLeast(host.dp(64)))
    private fun action(label: String, run: () -> Unit) = host.uiFactory.button(label).apply {
        layoutParams = LinearLayout.LayoutParams(-1, host.dp(48))
        setOnClickListener { run() }
    }
    private fun listener(change: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) = change(progress)
        override fun onStartTrackingTouch(bar: SeekBar) = Unit
        override fun onStopTrackingTouch(bar: SeekBar) = Unit
    }
    private class TrackHolder(val label: TextView) : RecyclerView.ViewHolder(label)
}
