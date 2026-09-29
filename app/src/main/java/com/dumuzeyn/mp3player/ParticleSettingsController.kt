package com.dumuzeyn.mp3player

import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.ScrollView
import java.util.Locale

internal class ParticleSettingsController(private val host: MainActivityCore) {
    fun openDialog() {
        val shade = host.uiFactory.shade()
        val panel = host.uiFactory.panelCard()
        panel.setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16))
        panel.addView(
            host.uiFactory.dialogTitle(host.tr("Particle settings", "Настройка частиц")),
            host.uiFactory.dialogTitleParams(),
        )
        val content = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(host).apply { addView(content) }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, -2, 1f))
        addSlider(
            content,
            host.tr("Frequency", "Частота"),
            10,
            100,
            host.appearanceState.particleFrequency,
        ) { host.appearanceState.particleFrequency = it }
        addSlider(
            content,
            host.tr("Size", "Размер"),
            60,
            150,
            host.appearanceState.particleSize,
        ) { host.appearanceState.particleSize = it }
        addSlider(
            content,
            host.tr("Lifetime", "Время существования"),
            50,
            180,
            host.appearanceState.particleLifetime,
        ) { host.appearanceState.particleLifetime = it }
        val shape = host.uiFactory.button(shapeLabel())
        shape.setOnClickListener {
            host.overlayHost.removeView(shade)
            openShapePicker()
        }
        content.addView(shape, LinearLayout.LayoutParams(-1, host.dp(48)))
        addColorButton(content, true)
        addColorButton(content, false)

        val reset = host.uiFactory.button(host.tr("Restore defaults", "По умолчанию"))
        host.uiFactory.applySecondaryButtonStyle(reset)
        reset.setOnClickListener {
            host.appearanceState.particleFrequency = 45
            host.appearanceState.particleSize = 100
            host.appearanceState.particleLifetime = 100
            host.appearanceState.particlePrimaryColor = 0
            host.appearanceState.particleSecondaryColor = 0
            host.appearanceState.particleShape = "lightning"
            host.appearanceState.particleCustomPath = ""
            host.saveState()
            host.refreshParticleSettings()
            host.overlayHost.removeView(shade)
            openDialog()
        }
        val resetParams = LinearLayout.LayoutParams(-1, host.dp(46)).apply {
            setMargins(0, host.dp(8), 0, 0)
        }
        content.addView(reset, resetParams)

        val close = host.uiFactory.button(host.tr("Done", "Готово"))
        host.uiFactory.applyPrimaryButtonStyle(close)
        close.setOnClickListener {
            host.saveState()
            host.refreshParticleSettings()
            host.overlayHost.removeView(shade)
            host.refreshSettingsLabels()
        }
        val closeParams = LinearLayout.LayoutParams(-1, host.dp(50)).apply {
            setMargins(0, host.dp(10), 0, 0)
        }
        panel.addView(close, closeParams)
        shade.addView(panel, host.centerParams(host.dp(340), -2))
        host.overlayHost.addView(shade)
        host.playerUiController.updateMini()
    }

    private fun shapeLabel(): String = host.tr("Shape: ", "Форма: ") + when (host.appearanceState.particleShape) {
        "circle" -> host.tr("Circle", "Круг")
        "star" -> host.tr("Star", "Звезда")
        "diamond" -> host.tr("Diamond", "Ромб")
        "line" -> host.tr("Line", "Линия")
        "custom" -> host.tr("Custom", "Своя")
        else -> host.tr("Lightning", "Молния")
    }

    private fun openShapePicker() {
        val shade = host.uiFactory.shade()
        val panel = host.uiFactory.panelCard()
        panel.addView(host.uiFactory.dialogTitle(host.tr("Particle shape", "Форма частиц")))
        listOf(
            Triple("lightning", "Lightning", "Молния"),
            Triple("circle", "Circle", "Круг"),
            Triple("star", "Star", "Звезда"),
            Triple("diamond", "Diamond", "Ромб"),
            Triple("line", "Line", "Линия"),
            Triple("custom", "Draw your own", "Нарисовать свою"),
        ).forEach { (value, english, russian) ->
            val button = host.uiFactory.button(host.tr(english, russian))
            if (host.appearanceState.particleShape == value) host.uiFactory.applyPrimaryButtonStyle(button)
            button.setOnClickListener {
                host.overlayHost.removeView(shade)
                if (value == "custom") openDrawingDialog() else {
                    host.appearanceState.particleShape = value
                    host.saveState()
                    host.refreshParticleSettings()
                    openDialog()
                }
            }
            panel.addView(button, LinearLayout.LayoutParams(-1, host.dp(46)))
        }
        shade.addView(panel, host.centerParams(host.dp(340), -2))
        host.overlayHost.addView(shade)
    }

    private fun openDrawingDialog() {
        val shade = host.uiFactory.shade()
        (shade as? SwipeDismissFrameLayout)?.setSwipeDismissEnabled(false)
        val panel = host.uiFactory.panelCard()
        panel.addView(host.uiFactory.dialogTitle(host.tr("Draw a particle", "Нарисовать частицу")))
        val drawing = ParticleDrawingView(host)
        panel.addView(drawing, LinearLayout.LayoutParams(-1, host.dp(280)))
        val actions = host.uiFactory.row()
        val cancel = host.uiFactory.button(host.tr("Cancel", "Отмена"))
        cancel.setOnClickListener { host.overlayHost.removeView(shade); openDialog() }
        actions.addView(cancel, LinearLayout.LayoutParams(0, host.dp(48), 1f))
        val add = host.uiFactory.button(host.tr("Add", "Добавить"))
        host.uiFactory.applyPrimaryButtonStyle(add)
        add.setOnClickListener {
            val encoded = drawing.encodedPath()
            if (encoded.isNotEmpty()) {
                host.appearanceState.particleCustomPath = encoded
                host.appearanceState.particleShape = "custom"
                host.saveState()
                host.refreshParticleSettings()
                host.overlayHost.removeView(shade)
                openDialog()
            }
        }
        actions.addView(add, LinearLayout.LayoutParams(0, host.dp(48), 1f))
        panel.addView(actions)
        shade.addView(panel, host.centerParams(host.dp(340), -2))
        host.overlayHost.addView(shade)
    }

    private fun addColorButton(panel: LinearLayout, primary: Boolean) {
        val color = selectedColor(primary)
        val name = if (primary) {
            host.tr("Primary color", "Основной цвет")
        } else {
            host.tr("Secondary color", "Дополнительный цвет")
        }
        val button = host.uiFactory.button("$name: ${colorHex(color)}")
        button.setTextColor(ThemeManager.readableOn(color))
        host.uiFactory.setSurface(button, color, false)
        button.setOnClickListener {
            host.overlayHost.removeAllViews()
            openColorPicker(primary)
        }
        val params = LinearLayout.LayoutParams(-1, host.dp(48)).apply {
            setMargins(0, host.dp(3), 0, host.dp(3))
        }
        panel.addView(button, params)
    }

    private fun openColorPicker(primary: Boolean) {
        val originalColor = if (primary) {
            host.appearanceState.particlePrimaryColor
        } else {
            host.appearanceState.particleSecondaryColor
        }
        val shade = host.uiFactory.shade()
        val panel = host.uiFactory.panelCard()
        panel.setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16))
        panel.addView(
            host.uiFactory.dialogTitle(
                if (primary) {
                    host.tr("Primary particle color", "Основной цвет частиц")
                } else {
                    host.tr("Secondary particle color", "Дополнительный цвет частиц")
                },
                21,
            ),
            host.uiFactory.dialogTitleParams(),
        )

        val preview = View(host).apply { setBackgroundColor(selectedColor(primary)) }
        panel.addView(preview, LinearLayout.LayoutParams(-1, host.dp(32)))

        val wheel = ThemeColorWheelView(host, selectedColor(primary)) { color ->
            if (primary) {
                host.appearanceState.particlePrimaryColor = color
            } else {
                host.appearanceState.particleSecondaryColor = color
            }
            preview.setBackgroundColor(color)
            host.refreshParticleSettings()
        }
        val wheelParams = LinearLayout.LayoutParams(-1, host.dp(260)).apply {
            setMargins(0, host.dp(10), 0, host.dp(10))
        }
        panel.addView(wheel, wheelParams)

        val actions = host.uiFactory.row()
        val cancel = host.uiFactory.button(host.tr("Cancel", "Отмена"))
        cancel.setOnClickListener {
            if (primary) {
                host.appearanceState.particlePrimaryColor = originalColor
            } else {
                host.appearanceState.particleSecondaryColor = originalColor
            }
            host.refreshParticleSettings()
            host.overlayHost.removeView(shade)
            openDialog()
        }
        actions.addView(cancel, LinearLayout.LayoutParams(0, host.dp(50), 1f))

        val done = host.uiFactory.button(host.tr("Done", "Готово"))
        host.uiFactory.applyPrimaryButtonStyle(done)
        done.setOnClickListener {
            host.saveState()
            host.refreshParticleSettings()
            host.overlayHost.removeView(shade)
            openDialog()
        }
        actions.addView(done, LinearLayout.LayoutParams(0, host.dp(50), 1f))
        panel.addView(actions)

        shade.addView(panel, host.centerParams(host.dp(340), -2))
        host.overlayHost.addView(shade)
    }

    private fun selectedColor(primary: Boolean): Int {
        val configured = if (primary) {
            host.appearanceState.particlePrimaryColor
        } else {
            host.appearanceState.particleSecondaryColor
        }
        if (configured != 0) return configured
        return if (primary) host.purple else host.yellow
    }

    private fun colorHex(color: Int): String = String.format(
        Locale.ROOT,
        "#%02X%02X%02X",
        Color.red(color),
        Color.green(color),
        Color.blue(color),
    )

    private fun addSlider(
        panel: LinearLayout,
        name: String,
        minimum: Int,
        maximum: Int,
        initial: Int,
        listener: (Int) -> Unit,
    ) {
        val value = initial.coerceIn(minimum, maximum)
        val label = host.uiFactory.text("$name: $value%", 15, false)
        label.minHeight = host.dp(34)
        panel.addView(label, LinearLayout.LayoutParams(-1, -2))
        val seek = SeekBar(host).apply {
            max = maximum - minimum
            progress = value - minimum
        }
        host.uiFactory.applySeekBarColors(seek)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val selected = minimum + progress
                label.text = "$name: $selected%"
                listener(selected)
                host.refreshParticleSettings()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                host.saveState()
            }
        })
        panel.addView(seek, LinearLayout.LayoutParams(-1, host.dp(44)))
    }
}
