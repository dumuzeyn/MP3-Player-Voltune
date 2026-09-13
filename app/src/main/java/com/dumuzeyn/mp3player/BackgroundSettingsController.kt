package com.dumuzeyn.mp3player

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Toast
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.min

internal class BackgroundSettingsController(private val host: MainActivityCore) {
    private val validatorExecutor = Executors.newSingleThreadExecutor()

    fun openDialog() {
        val shade = host.uiFactory.shade()
        val panel = host.uiFactory.panelCard().apply {
            setPadding(host.dp(14), host.dp(12), host.dp(14), host.dp(12))
        }
        val dialogTitle = host.uiFactory.dialogTitle(host.tr("Background", "Фон"))
        panel.addView(dialogTitle, host.uiFactory.dialogTitleParams())

        val scroll = ScrollView(host)
        val rows = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        addTarget(rows, true, shade)
        val divider = host.uiFactory.lineView()
        rows.addView(
            divider,
            LinearLayout.LayoutParams(-1, host.dp(1)).apply {
                setMargins(0, host.dp(10), 0, host.dp(10))
            },
        )
        addTarget(rows, false, shade)
        scroll.addView(rows, FrameLayout.LayoutParams(-1, -2))
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val done = host.uiFactory.button(host.tr("Done", "Готово"))
        host.uiFactory.applyPrimaryButtonStyle(done)
        done.setOnClickListener {
            host.saveState()
            host.overlayHost.removeView(shade)
            host.rebuildUi()
        }
        panel.addView(
            done,
            LinearLayout.LayoutParams(-1, host.dp(48)).apply {
                setMargins(0, host.dp(8), 0, 0)
            },
        )

        val maxHeight = min(
            host.dp(650),
            host.resources.displayMetrics.heightPixels - host.dp(44),
        )
        val contentWidth = host.dp(360) - panel.paddingLeft - panel.paddingRight
        rows.measure(
            View.MeasureSpec.makeMeasureSpec(contentWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        dialogTitle.measure(
            View.MeasureSpec.makeMeasureSpec(contentWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val desiredHeight = panel.paddingTop + panel.paddingBottom + dialogTitle.measuredHeight +
            rows.measuredHeight + host.dp(56)
        shade.addView(panel, host.centerParams(host.dp(360), min(maxHeight, desiredHeight)))
        host.overlayHost.addView(shade)
        host.playerUiController.updateMini()
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_MAIN_MEDIA && requestCode != REQUEST_PLAYER_MEDIA) return false
        val uri = data?.data
        if (resultCode != Activity.RESULT_OK || uri == null) return true
        val main = requestCode == REQUEST_MAIN_MEDIA
        validatorExecutor.execute {
            val result = BackgroundMediaValidator.validate(host, uri)
            host.runOnUiThread {
                if (!result.valid) {
                    Toast.makeText(
                        host,
                        host.tr(
                            "This file is not a safe supported image.",
                            "Файл не является безопасным поддерживаемым изображением.",
                        ),
                        Toast.LENGTH_LONG,
                    ).show()
                    return@runOnUiThread
                }
                try {
                    host.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (_: SecurityException) {
                    Toast.makeText(
                        host,
                        host.tr(
                            "Permanent access to the image was not granted.",
                            "Постоянный доступ к изображению не предоставлен.",
                        ),
                        Toast.LENGTH_LONG,
                    ).show()
                    return@runOnUiThread
                }
                if (result.heavy) {
                    host.showActionPanel(
                        host.tr("Heavy animated background", "Тяжёлый анимированный фон"),
                        host.tr(
                            "This image may increase memory use and battery drain. Use it anyway?",
                            "Это изображение может увеличить расход памяти и заряда. " +
                                "Всё равно использовать?",
                        ),
                        host.tr("Cancel", "Отмена"),
                        host.tr("Use", "Использовать"),
                        true,
                    ) { applyMedia(main, uri) }
                } else {
                    applyMedia(main, uri)
                }
            }
        }
        return true
    }

    fun close() {
        validatorExecutor.shutdownNow()
    }

    private fun applyMedia(main: Boolean, uri: Uri) {
        if (main) {
            host.appearanceState.mainBackgroundMediaUri = uri.toString()
            host.appearanceState.mainBackgroundMode = MODE_MEDIA
        } else {
            host.appearanceState.playerBackgroundMediaUri = uri.toString()
            host.appearanceState.playerBackgroundMode = MODE_MEDIA
        }
        host.saveState()
        host.rebuildUi()
        openDialog()
    }

    private fun addTarget(rows: LinearLayout, main: Boolean, shade: FrameLayout) {
        rows.addView(
            host.uiFactory.text(
                if (main) {
                    host.tr("Main application", "Основное приложение")
                } else {
                    host.tr("Full player", "Большой плеер")
                },
                18,
                true,
            ),
            LinearLayout.LayoutParams(-1, host.dp(38)),
        )
        val mode = if (main) {
            host.appearanceState.mainBackgroundMode
        } else {
            host.appearanceState.playerBackgroundMode
        }
        addAction(rows, host.tr("Type: ", "Тип: ") + modeName(mode)) {
            setMode(main, (mode + 1) % 3)
            saveAndReopen(shade)
        }
        when (mode) {
            MODE_SOLID -> {
                val color = if (main) {
                    resolvedSolid(host.appearanceState.mainSolidBackground)
                } else {
                    resolvedSolid(host.appearanceState.playerSolidBackground)
                }
                addColor(
                    rows,
                    host.tr("Color", "Цвет"),
                    if (main) MAIN_SOLID else PLAYER_SOLID,
                    color,
                    shade,
                )
            }
            MODE_GRADIENT -> {
                addColor(
                    rows,
                    host.tr("Color 1", "Цвет 1"),
                    if (main) MAIN_START else PLAYER_START,
                    if (main) {
                        host.appearanceState.mainGradientStart
                    } else {
                        host.appearanceState.playerGradientStart
                    },
                    shade,
                )
                addColor(
                    rows,
                    host.tr("Color 2", "Цвет 2"),
                    if (main) MAIN_END else PLAYER_END,
                    if (main) {
                        host.appearanceState.mainGradientEnd
                    } else {
                        host.appearanceState.playerGradientEnd
                    },
                    shade,
                )
            }
            else -> {
                val uri = if (main) {
                    host.appearanceState.mainBackgroundMediaUri
                } else {
                    host.appearanceState.playerBackgroundMediaUri
                }
                addAction(
                    rows,
                    if (uri.isEmpty()) {
                        host.tr("Choose visual media", "Выбрать медиафон")
                    } else {
                        host.tr("Replace visual media", "Заменить медиафон")
                    },
                ) { openMediaPicker(main) }
                addBlur(rows, main)
            }
        }
    }

    private fun addBlur(rows: LinearLayout, main: Boolean) {
        val blur = if (main) {
            host.appearanceState.mainBackgroundBlur
        } else {
            host.appearanceState.playerBackgroundBlur
        }
        val label = host.uiFactory.text(
            host.tr("Blur: ", "Размытие: ") + blur + "%",
            15,
            false,
        )
        rows.addView(label, LinearLayout.LayoutParams(-1, host.dp(30)))
        val seek = SeekBar(host).apply {
            max = 100
            progress = blur
        }
        host.uiFactory.applySeekBarColors(seek)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                label.text = host.tr("Blur: ", "Размытие: ") + progress + "%"
                if (main) {
                    host.appearanceState.mainBackgroundBlur = progress
                } else {
                    host.appearanceState.playerBackgroundBlur = progress
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                host.saveState()
            }
        })
        rows.addView(seek, LinearLayout.LayoutParams(-1, host.dp(42)))
    }

    @Suppress("DEPRECATION")
    private fun openMediaPicker(main: Boolean) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
        }
        host.startActivityForResult(
            intent,
            if (main) REQUEST_MAIN_MEDIA else REQUEST_PLAYER_MEDIA,
        )
    }

    private fun addColor(
        parent: LinearLayout,
        label: String,
        target: Int,
        color: Int,
        parentShade: FrameLayout,
    ) {
        val button = host.uiFactory.button(label + "  " + colorHex(color)).apply {
            setTextColor(ThemeManager.readableOn(color))
        }
        host.uiFactory.setSurface(button, color, false)
        button.setOnClickListener {
            host.overlayHost.removeView(parentShade)
            openColorPicker(label, target, color)
        }
        parent.addView(
            button,
            LinearLayout.LayoutParams(-1, host.dp(46)).apply {
                setMargins(0, host.dp(2), 0, host.dp(2))
            },
        )
    }

    private fun openColorPicker(title: String, target: Int, initialColor: Int) {
        val shade = host.uiFactory.shade()
        val panel = host.uiFactory.panelCard().apply {
            setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16))
        }
        panel.addView(host.uiFactory.dialogTitle(title, 21), host.uiFactory.dialogTitleParams())
        val preview = View(host).apply { setBackgroundColor(initialColor) }
        panel.addView(preview, LinearLayout.LayoutParams(-1, host.dp(34)))
        val wheel = ThemeColorWheelView(host, initialColor) { color ->
            setColor(target, color)
            preview.setBackgroundColor(color)
        }
        panel.addView(wheel, LinearLayout.LayoutParams(-1, host.dp(280)))
        val done = host.uiFactory.button(host.tr("Done", "Готово"))
        host.uiFactory.applyPrimaryButtonStyle(done)
        done.setOnClickListener {
            host.saveState()
            host.overlayHost.removeView(shade)
            openDialog()
        }
        panel.addView(done, LinearLayout.LayoutParams(-1, host.dp(50)))
        shade.addView(panel, host.centerParams(host.dp(350), -2))
        host.overlayHost.addView(shade)
    }

    private fun addAction(parent: LinearLayout, label: String, action: () -> Unit) {
        val button = host.uiFactory.button(label)
        host.uiFactory.applySecondaryButtonStyle(button)
        button.setOnClickListener { action() }
        parent.addView(
            button,
            LinearLayout.LayoutParams(-1, host.dp(46)).apply {
                setMargins(0, host.dp(2), 0, host.dp(2))
            },
        )
    }

    private fun saveAndReopen(shade: FrameLayout) {
        host.saveState()
        host.overlayHost.removeView(shade)
        openDialog()
    }

    private fun setMode(main: Boolean, mode: Int) {
        if (main) {
            host.appearanceState.mainBackgroundMode = mode
        } else {
            host.appearanceState.playerBackgroundMode = mode
        }
    }

    private fun setColor(target: Int, color: Int) {
        when (target) {
            MAIN_SOLID -> host.appearanceState.mainSolidBackground = color
            MAIN_START -> host.appearanceState.mainGradientStart = color
            MAIN_END -> host.appearanceState.mainGradientEnd = color
            PLAYER_SOLID -> host.appearanceState.playerSolidBackground = color
            PLAYER_START -> host.appearanceState.playerGradientStart = color
            else -> host.appearanceState.playerGradientEnd = color
        }
    }

    private fun resolvedSolid(color: Int): Int = if (color == 0) host.bg else color

    private fun modeName(mode: Int): String = when (mode) {
        MODE_GRADIENT -> host.tr("gradient", "градиент")
        MODE_MEDIA -> host.tr("visual media", "медиафон")
        else -> host.tr("solid", "однотонный")
    }

    private fun colorHex(color: Int): String = String.format(
        Locale.ROOT,
        "#%02X%02X%02X",
        Color.red(color),
        Color.green(color),
        Color.blue(color),
    )

    companion object {
        const val MODE_SOLID = 0
        const val MODE_GRADIENT = 1
        const val MODE_MEDIA = 2
        private const val REQUEST_MAIN_MEDIA = 4301
        private const val REQUEST_PLAYER_MEDIA = 4302
        private const val MAIN_SOLID = 0
        private const val MAIN_START = 1
        private const val MAIN_END = 2
        private const val PLAYER_SOLID = 3
        private const val PLAYER_START = 4
        private const val PLAYER_END = 5
    }
}
