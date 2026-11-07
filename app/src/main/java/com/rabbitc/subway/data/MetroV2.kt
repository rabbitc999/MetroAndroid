package com.rabbitc.subway.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.util.Locale

/** Bridges the v2 document and the existing station-name route index. */
object MetroV2 {
    fun parse(json: String): JsonObject {
        val root = JsonParser.parseString(json)
        if (!root.isJsonObject || root.asJsonObject.get("stations")?.isJsonArray != true) {
            throw JsonParseException("v2 地图必须包含 stations 数组")
        }
        return root.asJsonObject
    }

    /** Converts the active bundled map into an editable v2 draft. */
    fun editableCopy(json: String): JsonObject {
        val root = JsonParser.parseString(json).asJsonObject
        val previous = root.get("stations")
        if (previous?.isJsonObject != true) return parse(json)
        val stations = JsonArray()
        val facilities = root.getAsJsonObject("facilities")
        val transfers = root.getAsJsonArray("transfers") ?: JsonArray()
        for ((name, item) in previous.asJsonObject.entrySet()) {
            val station = item.asJsonObject
            station.addProperty("name", name)
            facilities?.get(name)?.let { station.add("accessibility", it) }
            for ((key, rule) in station.getAsJsonObject("transfers")?.entrySet().orEmpty()) {
                val lines = key.split('|', limit = 2)
                if (lines.size != 2) continue
                val transfer = JsonObject()
                transfer.addProperty("station", name)
                transfer.addProperty("from", lines[0])
                transfer.addProperty("to", lines[1])
                val status = if (rule.isJsonObject) rule.asJsonObject.string("status") else rule.asString
                transfer.addProperty("type", status?.uppercase(Locale.ROOT) ?: "OPERATING")
                if (rule.isJsonObject) rule.asJsonObject.get("time")?.let { transfer.add("time", it) }
                transfers.add(transfer)
            }
            stations.add(station)
        }
        root.add("stations", stations)
        if (!transfers.isEmpty) root.add("transfers", transfers)
        root.remove("facilities")
        for ((_, value) in root.getAsJsonObject("lines")?.entrySet().orEmpty()) {
            val line = value.asJsonObject
            for (field in listOf("formation", "link")) {
                val previousValue = line.get(field)
                if (previousValue?.isJsonPrimitive == true) {
                    line.add(field, JsonArray().apply { add(previousValue) })
                }
            }
        }
        return root
    }

    @JvmStatic
    fun forRouting(json: String): JsonObject {
        val root = JsonParser.parseString(json).asJsonObject
        val stationList = root.get("stations") ?: return root
        if (!stationList.isJsonArray) return root
        val stations = JsonObject()
        val facilities = JsonObject()
        for (item in stationList.asJsonArray) {
            val station = item.asJsonObject
            val name = station.string("name") ?: throw JsonParseException("站点缺少名称")
            if (stations.has(name)) {
                throw JsonParseException("当前路线索引无法区分同名车站：$name")
            }
            stations.add(name, station)
            station.get("accessibility")?.let { facilities.add(name, it) }
        }

        applyTransfers(root, stations)
        root.add("stations", stations)
        root.add("facilities", facilities)
        root.getAsJsonObject("lines")?.entrySet()?.forEach { (_, item) ->
            val line = item.asJsonObject
            for (field in listOf("formation", "link")) {
                val value = line.get(field)
                if (value?.isJsonArray == true) {
                    if (field == "link") line.add("resource_links", value.deepCopy())
                    line.addProperty(field, value.asJsonArray.joinToString(" / ") { it.asString })
                }
            }
        }
        return root
    }

    private fun applyTransfers(root: JsonObject, stations: JsonObject) {
        val transfers = root.getAsJsonArray("transfers") ?: return
        val types = root.getAsJsonArray("transfer_types")?.asList().orEmpty()
            .map { it.asJsonObject }
            .associateBy { it.string("id") }
        for (item in transfers) {
            val transfer = item.asJsonObject
            val station = stations.getAsJsonObject(transfer.string("station")) ?: continue
            val typeId = transfer.string("type").orEmpty()
            val type = types[typeId]
            val rule = JsonObject()
            val time = transfer.get("time")?.takeUnless { it.isJsonNull } ?: type?.get("time")
            if (time != null && !time.isJsonNull) rule.add("time", time)
            rule.addProperty("status", (type?.string("status") ?: typeId).lowercase(Locale.ROOT))
            val rules = station.getAsJsonObject("transfers") ?: JsonObject().also {
                station.add("transfers", it)
            }
            rules.add("${transfer.string("from")}|${transfer.string("to")}", rule)
        }
    }

    internal fun JsonObject.string(field: String): String? =
        get(field)?.takeUnless { it.isJsonNull }?.asString
}
