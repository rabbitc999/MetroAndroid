package com.rabbitc.subway.activity

import android.os.SystemClock
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.rabbitc.subway.data.MetroV2
import com.rabbitc.subway.data.MetroV2.string
import com.rabbitc.subway.utils.CoordinateUtils
import com.rabbitc.subway.utils.LocationHelper

class MetroV2ViewModel : ViewModel() {
    private var root: JsonObject? = null
    val mapName = MutableLiveData<String>()
    val target = MutableLiveData<Target?>()
    val timing = MutableLiveData(Timing())
    val laps = MutableLiveData<List<Lap>>(emptyList())
    private var lastLapMillis = 0L

    data class Lap(val target: Target, val seconds: Double, val cumulativeMillis: Long)

    data class Timing(val started: Long? = null, val elapsed: Long? = null, val offset: Long = 0)
    enum class Measurement(val field: String) {
        RUN("run_time_seconds"), DWELL("dwell_time"), TRANSFER("time")
    }
    data class Target(
        val kind: Measurement,
        val group: String,
        val label: String,
        val index: Int,
        internal val data: JsonObject,
        val arrival: String? = null,
        val station: String? = null,
    ) {
        val seconds: Double? get() = data.string(kind.field)?.toDoubleOrNull()
    }

    fun open(name: String, json: String) {
        root = MetroV2.editableCopy(json)
        mapName.value = name
        target.value = null
        timing.value = Timing()
        laps.value = emptyList()
        lastLapMillis = 0
    }

    private fun map() = requireNotNull(root) { "请先选择内置地图" }
    private val gson = GsonBuilder().setPrettyPrinting().create()

    data class TargetKey(val kind: Measurement, val group: String, val label: String, val index: Int)
    data class Sample(val target: TargetKey, val seconds: Double, val cumulativeMillis: Long)
    data class Session(val asset: String, val target: TargetKey?, val samples: List<Sample>,
                       val elapsed: Long, val lastMark: Long)
    data class Change(val target: Target, val samples: List<Double>) {
        val median: Double get() {
            val sorted = samples.sorted()
            return (sorted[(sorted.size - 1) / 2] + sorted[sorted.size / 2]) / 2
        }
    }

    private fun Target.key() = TargetKey(kind, group, label, index)

    fun snapshot(asset: String): String = gson.toJson(Session(asset, target.value?.key(),
        laps.value.orEmpty().map { Sample(it.target.key(), it.seconds, it.cumulativeMillis) },
        timing.value?.let { clock ->
            clock.started?.let { SystemClock.elapsedRealtime() - it + clock.offset } ?: clock.elapsed
        } ?: 0L, lastLapMillis))

    fun restore(session: Session) {
        val index = Measurement.entries.flatMap(::targets).associateBy { it.key() }
        target.value = index[session.target]
        lastLapMillis = session.lastMark
        timing.value = Timing(elapsed = session.elapsed)
        laps.value = session.samples.mapNotNull { sample ->
            index[sample.target]?.let { Lap(it, sample.seconds, sample.cumulativeMillis) }
        }
    }

    fun changes(): List<Change> = laps.value.orEmpty().groupBy { it.target.key() }.values.map {
        Change(it.first().target, it.map(Lap::seconds))
    }

    fun export(changes: List<Pair<Change, Double>>): String {
        val previous = changes.map { (change, _) -> change.target.data.get(change.target.kind.field) }
        try {
            for ((change, seconds) in changes) change.target.data.addProperty(change.target.kind.field, seconds)
            return gson.toJson(map())
        } finally {
            changes.forEachIndexed { index, (change, _) ->
                val value = previous[index]
                if (value == null) change.target.data.remove(change.target.kind.field)
                else change.target.data.add(change.target.kind.field, value)
            }
        }
    }

    fun mapCity(): String? = map().string("key_city")

    data class Coverage(val points: Map<String, Pair<Double, Double>>, val edges: List<CoverageEdge>)
    data class CoverageEdge(val start: String, val end: String, val samples: Int)

    fun coverage(): Coverage {
        val counts = laps.value.orEmpty().groupingBy { it.target.key() }.eachCount()
        val edges = targets(Measurement.RUN).mapNotNull { target ->
            val start = target.station ?: return@mapNotNull null
            val end = target.arrival ?: return@mapNotNull null
            CoverageEdge(start, end, counts[target.key()] ?: 0)
        }
        val points = map().getAsJsonArray("stations").asList().mapNotNull {
            val station = it.asJsonObject
            val name = station.string("name") ?: return@mapNotNull null
            val lon = station.string("lon")?.toDoubleOrNull() ?: return@mapNotNull null
            val lat = station.string("lat")?.toDoubleOrNull() ?: return@mapNotNull null
            if (lon.isFinite() && lat.isFinite()) name to (lon to lat) else null
        }.toMap()
        return Coverage(points, edges)
    }

    fun removeLap(index: Int) {
        laps.value = laps.value.orEmpty().toMutableList().apply { removeAt(index) }
    }

    fun select(selected: Target) {
        target.value = selected
    }

    fun toggleTimer() {
        check(target.value != null) { "请先选择测量对象" }
        val now = SystemClock.elapsedRealtime()
        val start = timing.value?.started
        if (start == null) {
            timing.value = Timing(started = now, offset = timing.value?.elapsed ?: 0)
        } else {
            timing.value = Timing(elapsed = now - start + (timing.value?.offset ?: 0))
        }
    }

    fun canMark(): Boolean {
        val clock = timing.value ?: return false
        return target.value != null && (clock.started != null || (clock.elapsed ?: 0) > lastLapMillis)
    }

    fun markLap() {
        val selected = requireNotNull(target.value) { "请先选择测量对象" }
        val clock = requireNotNull(timing.value)
        val total = clock.started?.let { SystemClock.elapsedRealtime() - it + clock.offset } ?: clock.elapsed
            ?: error("请先开始计时")
        check(total > lastLapMillis) { "没有待记录的时间" }
        val seconds = (total - lastLapMillis) / 1000.0
        lastLapMillis = total
        laps.value = laps.value.orEmpty() + Lap(selected, seconds, total)
        target.value = selected
    }

    fun editLap(index: Int, seconds: Double) {
        require(seconds.isFinite() && seconds > 0) { "请输入大于零的秒数" }
        val list = laps.value.orEmpty().toMutableList()
        val lap = list[index]
        list[index] = lap.copy(seconds = seconds)
        laps.value = list
        target.value = target.value
    }

    fun nextTargets(): List<Target> {
        val current = target.value ?: return emptyList()
        val arrival = current.arrival ?: return emptyList()
        val nextRuns = targets(Measurement.RUN).filter {
            it.group == current.group && it.index == current.index + 1
        }
        val dwell = targets(Measurement.DWELL).filter {
            it.group == current.group && it.label == arrival
        }
        val transfers = targets(Measurement.TRANSFER).filter { it.group == arrival }
        return nextRuns + dwell + transfers
    }

    fun targets(kind: Measurement): List<Target> = buildList {
        val document = map()
        if (kind == Measurement.TRANSFER) {
            for (entry in document.getAsJsonArray("transfers")?.asList().orEmpty()) {
                val rule = entry.asJsonObject
                add(Target(kind, rule.string("station").orEmpty(),
                    "${rule.string("from")} → ${rule.string("to")}", -1, rule, station = rule.string("station")))
            }
        } else {
            for ((lineId, line) in document.getAsJsonObject("lines")?.entrySet().orEmpty()) {
                val lineName = line.asJsonObject.string("name") ?: lineId
                val directions = line.asJsonObject.getAsJsonArray("direction")?.asList().orEmpty()
                for ((number, entry) in directions.withIndex()) {
                    val direction = entry.asJsonObject
                    if (direction.get("is_future")?.asBoolean == true) continue
                    val stations = direction.getAsJsonArray("station")?.asList().orEmpty()
                    val end = direction.string("end") ?: stations.lastOrNull()?.asJsonObject?.string("name")
                    val id = direction.string("id") ?: "$lineId-${number + 1}"
                    val group = "$lineName · 开往 ${end.orEmpty()}（$id）"
                    for ((index, item) in stations.withIndex()) {
                        val station = item.asJsonObject
                        var label = station.string("name") ?: continue
                        var arrival: String? = null
                        if (kind == Measurement.RUN) {
                            val next = stations.getOrNull(index + 1) ?: stations.firstOrNull()
                                ?.takeIf { stations.size > 1 && direction.get("is_loop")?.asBoolean == true }
                                ?: continue
                            if (station.string("next") == null) continue
                            arrival = next.asJsonObject.string("name") ?: continue
                            label += " → $arrival"
                        }
                        add(Target(kind, group, label, index, station, arrival, station.string("name")))
                    }
                }
            }
        }
    }

    fun nearby(longitude: Double, latitude: Double, radius: Double): List<LocationHelper.NearbyStation> {
        val center = CoordinateUtils.toGcj02IfInChina(longitude, latitude)
        return map().getAsJsonArray("stations").asList().mapNotNull { item ->
            val station = item.asJsonObject
            val name = station.string("name") ?: return@mapNotNull null
            val lon = station.string("lon")?.toDoubleOrNull() ?: return@mapNotNull null
            val lat = station.string("lat")?.toDoubleOrNull() ?: return@mapNotNull null
            val distance = CoordinateUtils.haversineDistance(center[0], center[1], lon, lat)
            if (distance <= radius) LocationHelper.NearbyStation(name, distance) else null
        }.sortedBy { it.distanceMeters }
    }
}
