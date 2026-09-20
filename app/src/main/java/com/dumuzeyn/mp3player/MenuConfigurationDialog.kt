package com.dumuzeyn.mp3player

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Menu visibility and drag ordering panel shown from Settings. */
internal class MenuConfigurationDialog(private val host: MainActivityCore) {
    fun open() {
        val shade = host.uiFactory.shade()
        val panel = host.uiFactory.panelCard()
        panel.addView(header(shade))

        lateinit var helper: ItemTouchHelper
        val adapter = MenuAdapter(host.menuConfigurationController.orderedTabs()) { holder ->
            helper.startDrag(holder)
        }
        val list = RecyclerView(host).apply {
            layoutManager = LinearLayoutManager(host)
            this.adapter = adapter
            setHasFixedSize(true)
        }
        helper = ItemTouchHelper(
            object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                0,
            ) {
                override fun isLongPressDragEnabled(): Boolean = false

                override fun onMove(
                    recyclerView: RecyclerView,
                    source: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder,
                ): Boolean = adapter.move(
                    source.bindingAdapterPosition,
                    target.bindingAdapterPosition,
                )

                override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) = Unit
            },
        )
        helper.attachToRecyclerView(list)
        panel.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        shade.addView(panel, host.bottomParams())
        host.overlayHost.addView(shade)
        host.playerUiController.updateMini()
    }

    private fun header(shade: FrameLayout): LinearLayout = host.uiFactory.row().apply {
        addView(
            host.uiFactory.text(host.tr("Menu sections", "Разделы меню"), 20, true),
            LinearLayout.LayoutParams(0, host.dp(58), 1f),
        )
        addView(
            host.uiFactory.icon(StrictIcon.CLOSE).apply {
                contentDescription = host.tr("Close", "Закрыть")
                setOnClickListener {
                    if (shade.parent != null) host.overlayHost.removeView(shade)
                    host.playerUiController.updateMini()
                }
            },
            host.uiFactory.square(48),
        )
    }

    private inner class MenuAdapter(
        source: List<Int>,
        private val startDrag: (MenuHolder) -> Unit,
    ) : RecyclerView.Adapter<MenuHolder>() {
        private val tabs = source.toMutableList()

        override fun getItemCount(): Int = tabs.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuHolder {
            val row = host.uiFactory.row().apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(host.dp(4), host.dp(2), host.dp(6), host.dp(2))
                host.uiFactory.applyCardStyle(this, host.appearanceState.settingsCardOpacity)
                layoutParams = RecyclerView.LayoutParams(-1, host.dp(58)).apply {
                    setMargins(0, host.dp(2), 0, host.dp(2))
                }
            }
            val drag = host.uiFactory.icon(StrictIcon.DRAG).apply {
                contentDescription = host.tr("Move section", "Переместить раздел")
            }
            val label = host.uiFactory.text("", 17, true).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setSingleLine(true)
            }
            @Suppress("UseSwitchCompatOrMaterialCode")
            val toggle = Switch(host).apply {
                showText = false
                thumbTintList = switchColors(host.purple, host.secondaryText)
                trackTintList = switchColors(host.purpleSoft, host.cardStroke)
            }
            row.addView(drag, host.uiFactory.square(44))
            row.addView(label, LinearLayout.LayoutParams(0, -1, 1f))
            row.addView(toggle, LinearLayout.LayoutParams(host.dp(58), host.dp(48)))
            return MenuHolder(row, drag, label, toggle)
        }

        override fun onBindViewHolder(holder: MenuHolder, position: Int) {
            val tabId = tabs[position]
            val name = host.tabs[tabId]
            holder.label.text = name
            holder.drag.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) startDrag(holder)
                false
            }
            holder.itemView.setOnLongClickListener {
                startDrag(holder)
                true
            }
            holder.toggle.setOnCheckedChangeListener(null)
            holder.toggle.isChecked = host.menuConfigurationController.isVisible(tabId)
            holder.toggle.isEnabled = tabId != LibraryTabs.SETTINGS
            holder.toggle.alpha = if (holder.toggle.isEnabled) 1f else 0.55f
            holder.toggle.contentDescription = host.tr(
                "Show section $name",
                "Показывать раздел $name",
            )
            holder.toggle.setOnCheckedChangeListener { _, enabled ->
                if (host.menuConfigurationController.setEnabled(tabId, enabled)) {
                    host.refreshMenuConfiguration()
                }
            }
        }

        fun move(from: Int, to: Int): Boolean {
            if (!host.menuConfigurationController.move(from, to)) return false
            val tab = tabs.removeAt(from)
            tabs.add(to, tab)
            notifyItemMoved(from, to)
            host.refreshMenuConfiguration()
            return true
        }
    }

    private class MenuHolder(
        row: LinearLayout,
        val drag: Button,
        val label: TextView,
        val toggle: Switch,
    ) : RecyclerView.ViewHolder(row)

    private fun switchColors(checked: Int, unchecked: Int): ColorStateList = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(checked or Color.BLACK, unchecked or Color.BLACK),
    )
}
