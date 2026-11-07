package com.rabbitc.subway.data

import com.rabbitc.subway.data.json.MetroData

/** Shared track is counted once in network mileage, while service mileage stays per line. */
object NetworkMileage {
    @JvmStatic
    fun total(data: MetroData?, includeFuture: Boolean): Double {
        val lines = data?.lines.orEmpty()
        val directionTracks = mutableMapOf<String, Pair<String, Set<Pair<String, String>>>>()
        for ((lineId, line) in lines) {
            for (direction in line.direction.orEmpty()) {
                val id = direction.id ?: continue
                if (direction.isFast || (!includeFuture && direction.isFuture)) continue
                val names = direction.station.orEmpty().map { it.name }
                val edges = names.zipWithNext().toMutableList()
                if (direction.isLoop && names.size > 1) edges.add(names.last() to names.first())
                directionTracks[id] = lineId to edges.mapNotNull { (a, b) ->
                    if (a == null || b == null) null else ordered(a, b)
                }.toSet()
            }
        }
        val shared = mutableMapOf<Pair<String, String>, MutableSet<Pair<String, String>>>()
        val tracks = mutableMapOf<Pair<String, String>, MutableMap<String, Double>>()
        for ((lineId, line) in lines) {
            for (direction in line.direction.orEmpty()) {
                if (direction.isFast || (!includeFuture && direction.isFuture)) continue
                val stations = direction.station.orEmpty()
                val sharedDirections = direction.viaDirections.orEmpty().mapNotNull(directionTracks::get)
                for ((index, station) in stations.withIndex()) {
                    val next = stations.getOrNull(index + 1)
                        ?: stations.firstOrNull()?.takeIf { direction.isLoop && stations.size > 1 }
                        ?: continue
                    val from = station.name ?: continue
                    val to = next.name ?: continue
                    val key = ordered(from, to)
                    val distances = tracks.getOrPut(key) { linkedMapOf() }
                    val distance = station.next ?: 0.0
                    if (distance > (distances[lineId] ?: 0.0)) distances[lineId] = distance
                    for ((otherLine, otherTracks) in sharedDirections) {
                        if (lineId != otherLine && key in otherTracks) {
                            shared.getOrPut(key) { mutableSetOf() }.add(ordered(lineId, otherLine))
                        }
                    }
                }
            }
        }
        return tracks.entries.sumOf { (edge, distances) ->
            val links = shared[edge].orEmpty().toMutableSet()
            // Bundled Shanghai maps predate VIA metadata.
            if (data?.keyCity == "上海" && edge in shanghaiShared) links.add("S3" to "S4")
            val groups = distances.keys.associateWith { mutableSetOf(it) }.toMutableMap()
            for ((a, b) in links) {
                val first = groups[a] ?: continue
                val second = groups[b] ?: continue
                if (first === second) continue
                first.addAll(second)
                for (line in second) groups[line] = first
            }
            groups.values.distinct().sumOf { group -> group.maxOf { distances.getValue(it) } }
        }
    }

    private fun ordered(a: String, b: String) = if (a <= b) a to b else b to a

    private val shanghaiShared = listOf(
        "宝山路", "上海火车站", "中潭路", "镇坪路", "曹杨路", "金沙江路",
        "中山公园", "延安西路", "虹桥路", "宜山路",
    ).zipWithNext(::ordered).toSet()
}
