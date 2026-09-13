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

    fun edit(clip: AudioEditClip) {
        if (controller.busy) return
        val shade = host.uiFactory.shade()
        val panel = host.uiFactory.panelCard()
        panel.addView(host.uiFactory.dialogTitle(clip.title, 18))
        val content = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(host).apply { addView(content) }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, bodyHeight(340, 230)))
        val waveform = AudioEditorWaveformView(host, clip)
        content.addView(waveform, LinearLayout.LayoutParams(-1, minOf(host.dp(140), bodyHeight(340, 230))))
        val analysis = AudioEditorAnalysisView(host, clip)
        content.addView(analysis)
        val from = secondsField(content, host.tr("Start, s", "Начало, с"), clip.startMs)
        val to = secondsField(content, host.tr("End, s", "Конец, с"), clip.endMs)
        var updatingRange = false
        waveform.onSelection = { start, end ->
            updatingRange = true
            from.setText(seconds(start))
            to.setText(seconds(end))
            updatingRange = false
            analysis.selection(start, end)
        }
        val rangeWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!updatingRange) runCatching {
                    waveform.setSelection(millis(from), millis(to))
                    analysis.selection(millis(from), millis(to))
                }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        }
        from.addTextChangedListener(rangeWatcher)
        to.addTextChangedListener(rangeWatcher)
        val offset = secondsField(content, host.tr("Timeline position, s", "Позиция на шкале, с"), clip.offsetMs)
        content.addView(host.uiFactory.text(host.tr("Lane", "Дорожка"), 14, false))
        val lane = Spinner(host).apply {
            adapter = ArrayAdapter(host, android.R.layout.simple_spinner_dropdown_item,
                (1..AudioEditClip.MAX_LANES).map(Int::toString))
            setSelection(clip.lane)
        }
        content.addView(lane, LinearLayout.LayoutParams(-1, host.dp(48)))
        val level = host.uiFactory.text("${host.tr("Volume", "Громкость")}: ${(clip.gain * 100).toInt()}%", 14, false)
        content.addView(level)
        val gain = SeekBar(host).apply { max = 100; progress = (clip.gain * 100).toInt() }
        host.uiFactory.applySeekBarColors(gain)
        gain.contentDescription = host.tr("Clip volume", "Громкость фрагмента")
        gain.setOnSeekBarChangeListener(listener { value ->
            level.text = "${host.tr("Volume", "Громкость")}: $value%"
        })
        content.addView(gain, LinearLayout.LayoutParams(-1, host.dp(48)))
        content.addView(AudioEditorPreviewControls(host, {
            AudioEditProject(listOf(clip.copy(startMs = millis(from), endMs = millis(to),
                offsetMs = 0, lane = 0, gain = gain.progress / 100f)))
        }, stopOnDetach = true))
        val cutLabel = host.uiFactory.text("", 14, false)
        content.addView(cutLabel)
        val cut = SeekBar(host).apply {
            max = 1000
            progress = 500
            contentDescription = host.tr("Split position", "Точка разделения")
        }
        fun cutPosition() = waveform.cursorMs
        fun updateCut() { cutLabel.text = host.tr("Split at ", "Разделить в ") + seconds(cutPosition()) + host.tr(" s", " с") }
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
        content.addView(action(host.tr("Split", "Разделить")) {
            if (controller.change { it.split(clip.id, cutPosition()) }) host.overlayHost.removeView(shade)
        })
        content.addView(action(host.tr("Remove selected range", "Удалить выделенный отрезок")) {
            if (controller.change { it.removeRange(clip.id, millis(from), millis(to)) }) {
                host.overlayHost.removeView(shade)
            }
        })
        content.addView(action(host.tr("Remove clip", "Удалить фрагмент")) {
            controller.change { it.remove(clip.id) }
            host.overlayHost.removeView(shade)
        })
        content.addView(action(host.tr("Clean speech", "Очистить речь")) {
            runCatching {
                clip.copy(startMs = millis(from), endMs = millis(to), offsetMs = millis(offset),
                    lane = lane.selectedItemPosition, gain = gain.progress / 100f)
            }.onSuccess { selected ->
                if (controller.processing.cleanSpeech(selected)) host.overlayHost.removeView(shade)
            }.onFailure { from.error = host.tr("Check the range", "Проверьте границы") }
        })
        for (instrumental in listOf(false, true)) {
            content.addView(action(if (instrumental) host.tr("Remove vocals", "Удалить вокал")
                else host.tr("Separate into four stems", "Разделить на четыре дорожки")) {
                runCatching {
                    clip.copy(startMs = millis(from), endMs = millis(to), offsetMs = millis(offset),
                        lane = lane.selectedItemPosition, gain = gain.progress / 100f)
                }.onSuccess { selected ->
                    if (controller.processing.separate(selected, instrumental)) host.overlayHost.removeView(shade)
                }.onFailure { from.error = host.tr("Check the range", "Проверьте границы") }
            })
        }
        panel.addView(action(host.tr("Apply trim and settings", "Применить обрезку и настройки")) {
            if (controller.change { it.replace(clip.copy(startMs = millis(from), endMs = millis(to),
                    offsetMs = millis(offset), lane = lane.selectedItemPosition, gain = gain.progress / 100f)) }) {
                host.overlayHost.removeView(shade)
            }
        }.apply { host.uiFactory.applyPrimaryButtonStyle(this) })
        panel.addView(action(host.tr("Cancel", "Отмена")) { host.overlayHost.removeView(shade) })
        shade.addView(panel, host.centerParams(host.dp(360), -2))
        host.overlayHost.addView(shade)
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
