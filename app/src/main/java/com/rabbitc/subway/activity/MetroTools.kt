package com.rabbitc.subway.activity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.rabbitc.subway.activity.MetroV2ViewModel.Measurement
import com.rabbitc.subway.data.DataCache
import com.rabbitc.subway.data.DataLoader
import com.rabbitc.subway.utils.CheckInManager
import com.rabbitc.subway.utils.LocationHelper
import kotlin.math.roundToInt

class MetroTools @JvmOverloads constructor(
    private val activity: AppCompatActivity,
    private val editorModel: MetroV2ViewModel? = null,
    private val selectStation: ((String) -> Unit)? = null,
) {
    private val preferences by lazy { activity.getSharedPreferences("metro_v2", Context.MODE_PRIVATE) }
    private var pendingRadius: Double? = null
    private val permission = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val radius = pendingRadius
        pendingRadius = null
        if (radius != null && LocationHelper.hasLocationPermission(activity)) {
            handleErrors { findStations(currentModel(), radius) }
        } else if (radius != null) {
            Toast.makeText(activity, "站点探测需要定位权限", Toast.LENGTH_SHORT).show()
        }
    }

    private fun currentModel() = editorModel ?: MetroV2ViewModel().also { loadActiveMap(activity, it) }

    private fun openStation(model: MetroV2ViewModel, station: String) {
        if (selectStation != null) {
            selectStation.invoke(station)
            return
        }
        AlertDialog.Builder(activity).setTitle(station)
            .setPositiveButton("站点打卡") { _, _ ->
                activity.startActivity(Intent(activity, CheckInFormActivity::class.java)
                    .putExtra(CheckInFormActivity.EXTRA_STATION_NAME, station)
                    .putExtra(CheckInFormActivity.EXTRA_CITY, model.mapCity()))
            }.setNegativeButton("关闭", null).show()
    }

    fun chooseRadius() = handleErrors {
        val model = currentModel()
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "搜索半径（米）"
            setText("1000")
            selectAll()
        }
        val dialog = AlertDialog.Builder(activity).setTitle("站点探测器 · 搜索半径（米）")
            .setView(input).setPositiveButton("开始探测", null)
            .setNegativeButton("取消", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val radius = input.text.toString().toDoubleOrNull()
                if (radius == null || radius !in 1.0..50000.0) {
                    input.error = "请输入 1–50000 米"
                } else {
                    dialog.dismiss()
                    if (LocationHelper.hasLocationPermission(activity)) findStations(model, radius)
                    else {
                        pendingRadius = radius
                        permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                }
            }
        }
        dialog.show()
    }

    private fun findStations(model: MetroV2ViewModel, radius: Double) {
        val map = DataLoader.getActiveMapName(activity)
        Toast.makeText(activity, "正在获取当前位置…", Toast.LENGTH_SHORT).show()
        LocationHelper.getCurrentLocation(activity, LocationHelper.LOCATION_TIMEOUT_MS,
            object : LocationHelper.OnLocationResult {
                override fun onLocation(longitude: Double, latitude: Double) = activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed || DataLoader.getActiveMapName(activity) != map) return@runOnUiThread
                    handleErrors {
                        val stations = model.nearby(longitude, latitude, radius)
                        val dialog = AlertDialog.Builder(activity)
                            .setTitle("${radius.toInt()} 米内 · ${stations.size} 个站点")
                            .setPositiveButton("关闭", null)
                        if (stations.isEmpty()) dialog.setMessage("选定地图在此范围内没有站点。")
                        else dialog.setItems(stations.map { "${it.name} · ${it.distanceText}" }.toTypedArray()) { _, index ->
                            handleErrors { openStation(model, stations[index].name) }
                        }
                        dialog.show()
                    }
                }

                override fun onError(reason: String) = activity.runOnUiThread {
                    if (!activity.isFinishing && !activity.isDestroyed)
                        Toast.makeText(activity, reason, Toast.LENGTH_LONG).show()
                }
            })
    }

    fun showCoverage() = handleErrors {
        val model = currentModel()
        val coverage = model.coverage()
        val city = model.mapCity()
        val checked = CheckInManager(activity).loadRecords()
            .filter { it.city == city }.map { it.station }.toSet()
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val count = coverage.edges.count { it.samples > 0 }
        val repeated = coverage.edges.count { it.samples > 1 }
        panel.addView(TextView(activity).apply {
            text = "已测 $count / ${coverage.edges.size} 个方向区间 · 复测 $repeated\n" +
                "蓝线：已测　紫线：复测　橙点：站点打卡\n双指缩放，单指拖动"
        })
        panel.addView(CoverageMapView(activity, coverage, checked),
            LinearLayout.LayoutParams(-1, dp(320)))
        panel.addView(MaterialButton(activity).apply {
            text = "各线路测量进度"
            cornerRadius = dp(8)
            setOnClickListener {
                val measured = model.changes().associate { it.target to it.samples.size }
                val rows = model.targets(Measurement.RUN).groupBy { it.group }.map { (line, targets) ->
                    val covered = targets.count { it in measured }
                    val repeated = targets.count { (measured[it] ?: 0) > 1 }
                    "$line\n已测 $covered / ${targets.size} · 复测 $repeated"
                }
                AlertDialog.Builder(activity).setTitle("各线路测量进度")
                    .setItems(rows.toTypedArray(), null).setPositiveButton("关闭", null).show()
            }
        })
        AlertDialog.Builder(activity).setTitle("覆盖与打卡预览").setView(panel)
            .setPositiveButton("关闭", null).show()
    }

    fun chooseTheme() {
        val modes = intArrayOf(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES,
        )
        AlertDialog.Builder(activity)
            .setTitle("显示模式")
            .setSingleChoiceItems(arrayOf("跟随系统", "白天", "夜晚"), modes.indexOf(AppCompatDelegate.getDefaultNightMode())) { dialog, index ->
                dialog.dismiss()
                preferences.edit().putInt("night_mode", modes[index]).apply()
                AppCompatDelegate.setDefaultNightMode(modes[index])
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun handleErrors(action: () -> Unit) {
        try {
            action()
        } catch (error: Exception) {
            AlertDialog.Builder(activity).setTitle("无法处理地图")
                .setMessage(error.message).setPositiveButton("关闭", null).show()
        }
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).roundToInt()

    companion object {
        @JvmStatic
        fun applyTheme(context: Context) {
            val preferences = context.getSharedPreferences("metro_v2", Context.MODE_PRIVATE)
            AppCompatDelegate.setDefaultNightMode(
                preferences.getInt("night_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            )
        }

        internal fun loadActiveMap(context: Context, model: MetroV2ViewModel): String {
            check(DataLoader.loadData(context)) { "当前没有可用的地铁地图" }
            val name = requireNotNull(DataLoader.getActiveMapName(context)) { "无法识别当前地图" }
            val json = requireNotNull(DataCache.getInstance().jsonData) { "无法读取当前地图" }
            model.open(name.substringBeforeLast('.'), json)
            context.getSharedPreferences("metro_measurement", Context.MODE_PRIVATE)
                .getString("session:$name", null)
                ?.let { model.restore(Gson().fromJson(it, MetroV2ViewModel.Session::class.java)) }
            return name
        }
    }
}
