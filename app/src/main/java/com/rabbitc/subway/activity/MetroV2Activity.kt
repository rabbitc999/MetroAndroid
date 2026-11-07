package com.rabbitc.subway.activity

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.util.AtomicFile
import android.os.SystemClock
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.widget.Chronometer
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.color.MaterialColors
import com.google.android.material.button.MaterialButton
import com.google.android.material.divider.MaterialDivider
import com.google.android.material.snackbar.Snackbar
import com.rabbitc.subway.R
import com.rabbitc.subway.data.DataLoader
import com.rabbitc.subway.activity.MetroV2ViewModel.Measurement
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

class MetroV2Activity : AppCompatActivity() {
    private val model by lazy { ViewModelProvider(this)[MetroV2ViewModel::class.java] }
    private lateinit var tools: MetroTools
    private lateinit var drawer: DrawerLayout

    private var currentMap: String? = null
    private val sessionPrefs by lazy { getSharedPreferences("metro_measurement", MODE_PRIVATE) }

    private fun persistSession() {
        val name = currentMap ?: return
        sessionPrefs.edit().putString("session:$name", model.snapshot(name)).apply()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("editor_map", currentMap)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        persistSession()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tools = MetroTools(this, model, ::chooseStationTargets)
        if (model.mapName.value == null) {
            handleErrors { currentMap = MetroTools.loadActiveMap(this, model) }
        } else {
            currentMap = savedInstanceState?.getString("editor_map")
        }
        title = "地图编辑与实测"
        WindowCompat.setDecorFitsSystemWindows(window, false)
        drawer = DrawerLayout(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface))
        }
        drawer.addView(content, DrawerLayout.LayoutParams(-1, -1))

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            styleCard(this)
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        content.addView(header, LinearLayout.LayoutParams(-1, -2))
        squareButton(header, "←", "返回") { onBackPressedDispatcher.onBackPressed() }
        header.addView(TextView(this).apply {
            text = title
            gravity = Gravity.CENTER
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        squareButton(header, "☰", "地图操作菜单") { drawer.openDrawer(Gravity.END) }

        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(page)
        }, LinearLayout.LayoutParams(-1, 0, 1f))

        val targetCard = section(page, "测量对象")
        val map = TextView(this).apply {
            text = "当前地图不可用"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
        }
        targetCard.addView(map)
        model.mapName.observe(this) { map.text = "当前地图 · $it" }

        val selection = TextView(this).apply {
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            setPadding(0, dp(8), 0, dp(8))
        }
        targetCard.addView(selection)
        val targetButtons = LinearLayout(this)
        targetCard.addView(targetButtons, LinearLayout.LayoutParams(-1, -2))
        for ((label, kind) in listOf("区间" to Measurement.RUN, "停站" to Measurement.DWELL,
            "换乘" to Measurement.TRANSFER)) {
            button(targetButtons, label, R.style.MetroEditorOutlinedButtonTheme) {
                handleErrors { chooseTarget(kind) }
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                    if (kind != Measurement.RUN) marginStart = dp(8)
                }
                isEnabled = model.mapName.value != null
            }
        }

        val timingCard = section(page, "实测计时")
        val clock = Chronometer(this).apply {
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_DisplaySmall)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
        }
        timingCard.addView(clock, LinearLayout.LayoutParams(-1, -2))
        val timer = button(timingCard, "开始计时") { handleErrors { model.toggleTimer() } }
        val record = button(timingCard, "标记停靠点", R.style.MetroEditorOutlinedButtonTheme) {
            handleErrors { model.markLap() }
        }
        val next = button(timingCard, "选择下一段", R.style.MetroEditorTextButtonTheme) {
            handleErrors {
                val candidates = model.nextTargets()
                AlertDialog.Builder(this).setTitle("选择下一段")
                    .setItems(candidates.map { candidate ->
                        when (candidate.kind) {
                            Measurement.RUN -> "行车 · ${candidate.label}"
                            Measurement.DWELL -> "停站 · ${candidate.label}"
                            Measurement.TRANSFER -> "换乘 · ${candidate.label}"
                        }
                    }.toTypedArray()) { _, index -> model.select(candidates[index]) }
                    .setNegativeButton("取消", null).show()
            }
        }
        val reviewCard = section(page, "记录与审核")
        val history = button(reviewCard, "分段记录（0）", R.style.MetroEditorTextButtonTheme) {
            showLaps()
        }
        button(reviewCard, "审核并保存地图", R.style.MetroEditorOutlinedButtonTheme) {
            handleErrors { reviewChanges() }
        }
        val toolsCard = section(page, "地图工具")
        button(toolsCard, "覆盖与打卡预览", R.style.MetroEditorTextButtonTheme) {
            tools.showCoverage()
        }
        button(toolsCard, "站点探测器", R.style.MetroEditorTextButtonTheme) {
            tools.chooseRadius()
        }
        model.target.observe(this) { target ->
            persistSession()
            selection.text = if (target == null) "请选择要测量的区间、站点或换乘"
                else "${target.group}\n${target.label}" +
                    (target.seconds?.let { "\n地图原值 ${formatSeconds(it)} 秒" } ?: "")
            timer.isEnabled = target != null
            val upcoming = model.nextTargets()
            next.text = if (upcoming.isEmpty()) "没有建议的下一段" else "选择下一段（${upcoming.size} 项）"
            next.isEnabled = upcoming.isNotEmpty()
            record.isEnabled = model.canMark()
        }
        model.laps.observe(this) {
            history.text = "分段记录（${it.size}）"
            history.isEnabled = it.isNotEmpty()
            persistSession()
            record.isEnabled = model.canMark()
        }
        model.timing.observe(this) {
            clock.base = it.started?.minus(it.offset) ?: (SystemClock.elapsedRealtime() - (it.elapsed ?: 0))
            timer.text = when {
                it.started != null -> "停止计时"
                (it.elapsed ?: 0) > 0 -> "继续计时"
                else -> "开始计时"
            }
            record.isEnabled = model.canMark()
            if (it.started == null) clock.stop() else clock.start()
            persistSession()
        }

        val menu = column()
        val scroll = ScrollView(this).apply {
            setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface))
            addView(menu)
        }
        drawer.addView(scroll, DrawerLayout.LayoutParams(dp(280), -1, Gravity.END))
        menu.addView(TextView(this).apply {
            text = "地图操作"
            textSize = 20f
        })
        menuButton(menu, "审核并保存地图") { reviewChanges() }
        menu.addView(MaterialDivider(this))
        menuButton(menu, "覆盖与打卡预览") { tools.showCoverage() }
        menuButton(menu, "站点探测器") { tools.chooseRadius() }
        menu.addView(MaterialDivider(this))
        menuButton(menu, "白天 / 夜晚") { tools.chooseTheme() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawer.isDrawerVisible(Gravity.END)) drawer.closeDrawer(Gravity.END) else finish()
            }
        })
        ViewCompat.setOnApplyWindowInsetsListener(drawer) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            val padding = dp(16)
            content.setPadding(padding + bars.left, padding + bars.top,
                padding + bars.right, 0)
            page.setPadding(0, 0, 0, padding + maxOf(bars.bottom, keyboard.bottom))
            menu.setPadding(padding + bars.left, padding + bars.top,
                padding + bars.right, padding + bars.bottom)
            insets
        }
        setContentView(drawer)
    }

    private fun activateCopy(json: String) {
        val previous = DataLoader.getActiveMapName(this)
        val directory = DataLoader.getMapsDir(this)
        check(directory.isDirectory || directory.mkdirs()) { "无法创建地图目录" }
        val file = AtomicFile(File(directory, "实测-${System.currentTimeMillis()}.json"))
        val output = file.startWrite()
        try {
            output.write(json.toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
        if (!DataLoader.loadMapByName(this, file.baseFile.name)) {
            file.delete()
            error("地图无法用于路线计算")
        }
        val snackbar = Snackbar.make(drawer, "已保存并启用地图副本", Snackbar.LENGTH_LONG)
        if (previous != null) snackbar.setAction("撤销启用") {
            handleErrors {
                check(DataLoader.loadMapByName(this, previous)) { "无法恢复原地图" }
                Toast.makeText(this, "已恢复原地图", Toast.LENGTH_SHORT).show()
            }
        }
        snackbar.show()
    }

    private fun chooseTarget(kind: Measurement) {
        val groups = model.targets(kind).groupBy { it.group }
        check(groups.isNotEmpty()) { "该地图没有可测量的条目" }
        val names = groups.keys.toList()
        AlertDialog.Builder(this@MetroV2Activity)
            .setTitle(if (kind == Measurement.TRANSFER) "选择换乘站" else "选择线路和方向")
            .setItems(names.toTypedArray()) { _, index ->
                val targets = groups.getValue(names[index])
                AlertDialog.Builder(this@MetroV2Activity).setTitle(names[index])
                    .setItems(targets.map { it.label }.toTypedArray()) { _, position ->
                        handleErrors { model.select(targets[position]) }
                    }.setNegativeButton("取消", null).show()
            }.setNegativeButton("取消", null).show()
    }

    private fun showLaps() {
        val laps = model.laps.value.orEmpty()
        AlertDialog.Builder(this).setTitle("分段记录 · 点击修改")
            .setItems(laps.mapIndexed { index, lap ->
                "${index + 1}. ${lap.target.label} · ${formatSeconds(lap.seconds)} 秒" +
                    "\n累计 ${formatSeconds(lap.cumulativeMillis / 1000.0)} 秒"
            }.toTypedArray()) { _, index ->
                val input = EditText(this).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    setText(laps[index].seconds.toString())
                    selectAll()
                }
                AlertDialog.Builder(this).setTitle("修改分段时间（秒）").setView(input)
                    .setPositiveButton("保存") { _, _ ->
                        handleErrors {
                            val seconds = requireNotNull(input.text.toString().toDoubleOrNull()) { "请输入秒数" }
                            model.editLap(index, seconds)
                        }
                    }.setNeutralButton("删除该样本") { _, _ -> model.removeLap(index) }
                    .setNegativeButton("取消", null).show()
            }.setNegativeButton("关闭", null).show()
    }

    private fun chooseStationTargets(station: String) {
        val choices = Measurement.entries.flatMap { kind ->
            model.targets(kind).filter { it.station == station || it.arrival == station }
        }
        val dialog = AlertDialog.Builder(this).setTitle(station).setNegativeButton("关闭", null)
            .setNeutralButton("站点打卡") { _, _ ->
                startActivity(Intent(this, CheckInFormActivity::class.java)
                    .putExtra(CheckInFormActivity.EXTRA_STATION_NAME, station)
                    .putExtra(CheckInFormActivity.EXTRA_CITY, model.mapCity()))
            }
        if (choices.isEmpty()) dialog.setMessage("该站没有可测量项目")
        else dialog.setItems(choices.map { "${it.group} · ${it.label}" }.toTypedArray()) { _, index ->
            model.select(choices[index])
        }
        dialog.show()
    }

    private fun reviewChanges() {
        check(model.timing.value?.started == null) { "请先停止当前计时" }
        val changes = model.changes()
        check(changes.isNotEmpty()) { "还没有待审核的实测记录" }
        val selected = BooleanArray(changes.size) { true }
        val values = changes.map { it.median }.toMutableList()
        val labels = changes.map { change ->
            val original = change.target.seconds?.let(::formatSeconds) ?: "默认"
            "${change.target.group} · ${change.target.label} · ${change.samples.size} 次\n" +
                "$original → ${formatSeconds(change.median)} 秒（中位数） · " +
                "${formatSeconds(change.samples.min())}–${formatSeconds(change.samples.max())} 秒"
        }.toTypedArray()
        fun confirm() {
            handleErrors {
                val accepted = changes.mapIndexedNotNull { index, change ->
                    if (selected[index]) change to values[index] else null
                }
                check(accepted.isNotEmpty()) { "请至少选择一项变更" }
                activateCopy(model.export(accepted))
            }
        }
        val dialog = AlertDialog.Builder(this).setTitle("审核地图修订")
            .setMultiChoiceItems(labels, selected) { _, index, enabled -> selected[index] = enabled }
            .setPositiveButton("启用所选") { _, _ -> confirm() }
            .setNeutralButton("选择采用的样本", null)
            .setNegativeButton("取消", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                AlertDialog.Builder(this).setTitle("选择要调整的项目")
                    .setItems(changes.map { "${it.target.group} · ${it.target.label}" }.toTypedArray()) { _, index ->
                        val change = changes[index]
                        val options = listOf(change.median) + change.samples
                        AlertDialog.Builder(this).setTitle(change.target.label)
                            .setItems(options.mapIndexed { position, value ->
                                "${if (position == 0) "中位数" else "样本 $position"} · ${formatSeconds(value)} 秒"
                            }.toTypedArray()) { _, position ->
                                values[index] = options[position]
                                dialog.listView.getChildAt(index - dialog.listView.firstVisiblePosition)
                                    ?.let { (it as? TextView)?.text = "${change.target.group} · ${change.target.label} → ${formatSeconds(values[index])} 秒" }
                            }.show()
                    }.show()
            }
        }
        dialog.show()
    }

    private fun styleCard(view: android.view.View) {
        view.setBackgroundResource(R.drawable.bg_rounded_card)
        view.backgroundTintList = ColorStateList.valueOf(
            MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurfaceContainerLow))
    }

    private fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val padding = dp(16)
        setPadding(padding, padding, padding, padding)
    }

    private fun section(parent: LinearLayout, label: String) = column().apply {
        styleCard(this)
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        addView(TextView(this@MetroV2Activity).apply {
            text = label
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary))
            setPadding(0, 0, 0, dp(4))
        })
    }

    private fun button(
        parent: LinearLayout,
        label: String,
        theme: Int = 0,
        action: () -> Unit,
    ) = MaterialButton(if (theme == 0) this else ContextThemeWrapper(this, theme)).apply {
        text = label
        cornerRadius = dp(8)
        minHeight = dp(48)
        setOnClickListener { action() }
        parent.addView(this, LinearLayout.LayoutParams(-1, -2))
    }

    private fun squareButton(parent: LinearLayout, icon: String, description: String, action: () -> Unit) {
        button(parent, icon, R.style.MetroEditorTextButtonTheme, action).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            insetTop = 0
            insetBottom = 0
            setPadding(0, 0, 0, 0)
            contentDescription = description
        }
    }

    private fun menuButton(parent: LinearLayout, label: String, action: () -> Unit) {
        button(parent, label, R.style.MetroEditorTextButtonTheme) {
            drawer.closeDrawer(Gravity.END)
            handleErrors(action)
        }.gravity = Gravity.START or Gravity.CENTER_VERTICAL
    }

    private fun handleErrors(action: () -> Unit) {
        try {
            action()
        } catch (error: Exception) {
            AlertDialog.Builder(this@MetroV2Activity).setTitle("无法处理地图")
                .setMessage(error.message).setPositiveButton("关闭", null).show()
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun formatSeconds(value: Double) = String.format(Locale.ROOT, "%.3f", value)
}
