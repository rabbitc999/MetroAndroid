package com.rabbitc.subway.data;

import com.rabbitc.subway.data.json.*;
import com.rabbitc.subway.utils.GraphKeyUtils;
import com.rabbitc.subway.utils.StationUtils;
import com.google.gson.Gson;

import java.util.*;

/**
 * 地图数据解析器（v3：MetroLang JSON），使用 Gson 将原始 JSON 字符串解析为
 * 结构化数据并存入 {@link DataCache}。
 *
 * <p>输入格式以 metro-lang 仓库 {@code doc/02-json-format.md} 为准：
 * 站点为数组（自带 name 身份字段）、无障碍设施内联在站点上、
 * 换乘为根级 transfers 数组、编组与链接为数组。不再兼容旧版
 * metro_editor 的 v1/v2 写法（字典站点、字符串编组、站点内联换乘状态）。</p>
 */
public final class DataParser {

    private static final String TAG = "DataParser";
    private static final Gson GSON = new Gson();

    private DataParser() {
        // 工具类禁止实例化
    }

    // ======================== 公共入口 ========================

    /**
     * 将缓存中的 JSON 解析为运行时模型，并构建路线规划索引。
     *
     * @throws Exception 解析失败时抛出
     */
    public static void parse() throws Exception {
        DataCache cache = DataCache.getInstance();
        String jsonData = cache.getJsonData();
        if (jsonData == null) {
            cache.clear();
            return;
        }

        // 1. 使用 Gson 解析整个 JSON 结构；后续步骤只处理内存对象，不再访问原始字符串。
        MetroData data = GSON.fromJson(jsonData, MetroData.class);
        cache.setMetroData(data);

        // 2. 基础运营参数：缺省值保证字段不全的数据也能参与时间计算。
        String keyCity = data.keyCity != null ? data.keyCity : "未知";
        cache.setKeyCity(keyCity);
        if (data.operationParams != null) {
            cache.setAvgTransferTime(data.operationParams.innerTransferTime);
            cache.setAvgStopTime(data.operationParams.dwellTime);
            cache.setOutStationTransferTime(data.operationParams.outStationTransferTime);
            cache.setAvgEntryTime(data.operationParams.entryTime);
            if (data.operationParams.avgBoardTime > 0) {
                cache.setDefaultBoardTime(data.operationParams.avgBoardTime);
            }
        } else {
            cache.setAvgTransferTime(120.0);
            cache.setAvgStopTime(45.0);
            cache.setOutStationTransferTime(300.0);
            cache.setAvgEntryTime(120.0);
        }

        // 3. 解析站点：坐标、站名索引；站点线路状态与换乘表从 directions / 根级 transfers 构建。
        Map<String, double[]> stationCoords = new HashMap<>();
        Map<String, Map<String, String>> stationLineStatus = new HashMap<>();
        Map<String, Station> stationIndex = new HashMap<>();
        if (data.stations != null) {
            parseStations(data.stations, stationCoords, stationIndex);
        }
        cache.setStationCoords(stationCoords);
        cache.setStationIndex(stationIndex);

        Map<String, Map<String, String>> transferStatus = new HashMap<>();
        Map<String, Double> specialTransferTimeMap = new HashMap<>();
        if (data.transfers != null) {
            parseTransfers(data.transfers, transferStatus, specialTransferTimeMap);
        }
        cache.setTransferStatusMap(transferStatus);
        cache.setSpecialTransferTimeMap(specialTransferTimeMap);

        // 4. 解析线路相关：方向键是路径图的主键，同时保存线路级和方向级属性。
        Map<String, List<String>> dirStations = new HashMap<>();
        Map<String, String> lineNameMap = new HashMap<>();
        Map<String, String> lineColorMap = new HashMap<>();
        Map<String, String> lineCityMap = new HashMap<>();
        Map<String, Boolean> hasLoopMap = new HashMap<>();
        Map<String, Double> lineTotalLengthMap = new HashMap<>();
        Map<String, Boolean> lineSpecialMap = new HashMap<>();
        Map<String, Boolean> lineHasFastMap = new HashMap<>();
        Map<String, Integer> lineMaxSpeedMap = new HashMap<>();
        Map<String, String> dirDirectionTypeMap = new HashMap<>();
        Map<String, Boolean> dirFutureMap = new HashMap<>();
        Map<String, Double> lineBrakeMap = new HashMap<>();
        Map<String, Double> stationStopTimeMap = new HashMap<>();
        Map<String, String> lineFormationMap = new HashMap<>();
        Map<String, Double> linePowerKwMap = new HashMap<>();
        Map<String, Double> lineTotalLengthWithoutFutureMap = new HashMap<>();
        Map<String, Boolean> hasFutureMap = new HashMap<>();
        Map<String, Boolean> lineAllFutureMap = new HashMap<>();
        Map<String, Boolean> dirLoopMap = new HashMap<>();
        Map<String, String> dirIdIndex = new HashMap<>(); // v3：方向 ID（如 SZ1_0）→ 方向键

        if (data.lines != null) {
            parseLines(data.lines, keyCity, dirStations, lineNameMap, lineColorMap,
                    lineCityMap, hasLoopMap, lineTotalLengthMap, lineSpecialMap,
                    lineHasFastMap, lineMaxSpeedMap, dirDirectionTypeMap,
                    dirFutureMap, lineBrakeMap, stationStopTimeMap, lineFormationMap, linePowerKwMap,
                    lineTotalLengthWithoutFutureMap, hasFutureMap, lineAllFutureMap, dirLoopMap,
                    stationLineStatus, dirIdIndex);
        }
        cache.setStationLineStatusMap(stationLineStatus);

        // 5. 限速规则：把 JSON DTO 转成运行时模型，避免算法层依赖 Gson 映射类。
        if (data.speedLimits != null) {
            List<com.rabbitc.subway.data.parse.SpeedLimit> speedLimits = new ArrayList<>();
            for (SpeedLimit sl : data.speedLimits) {
                com.rabbitc.subway.data.parse.SpeedLimit cacheSl = new com.rabbitc.subway.data.parse.SpeedLimit();
                cacheSl.directionKey = sl.directionKey;
                cacheSl.fromStation = sl.fromStation;
                cacheSl.toStation = sl.toStation;
                cacheSl.speedKmh = sl.speedKmh;
                speedLimits.add(cacheSl);
            }
            cache.setSpeedLimits(speedLimits);
        }

        // 6. 存入线路与基础属性到缓存；完成后 MetroGraph 才能安全构建索引。
        cache.setStationStopTimeMap(stationStopTimeMap);
        cache.setDirDirectionTypeMap(dirDirectionTypeMap);
        cache.setDirStations(dirStations);
        cache.setLineNameMap(lineNameMap);
        cache.setLineColorMap(lineColorMap);
        cache.setLineCityMap(lineCityMap);
        cache.setHasLoopMap(hasLoopMap);
        cache.setLineTotalLengthMap(lineTotalLengthMap);
        cache.setLineSpecialMap(lineSpecialMap);
        cache.setLineHasFastMap(lineHasFastMap);
        cache.setLineMaxSpeedMap(lineMaxSpeedMap);
        cache.setLineBrakeMap(lineBrakeMap);
        cache.setLineFormationMap(lineFormationMap);
        cache.setLinePowerKwMap(linePowerKwMap);
        cache.setDirFutureMap(dirFutureMap);
        cache.setAllFutureMap(lineAllFutureMap);
        cache.setDirLoopMap(dirLoopMap);

        // 7. 计算分段运营：通过连通分量识别同一线路中的不连续区段。
        Map<String, Boolean> lineSegmentedMap = new HashMap<>();
        for (String lineId : lineNameMap.keySet()) {
            lineSegmentedMap.put(lineId, isLineSegmented(lineId, dirStations));
        }
        cache.setLineSegmentedMap(lineSegmentedMap);

        // 8. 版本信息
        cache.setVersion(data.version != null ? data.version : "未知");
        cache.setUpdateDate(data.updateDate != null ? data.updateDate : "未知");

        // 9. 票价解析：普通分段、线性延伸和特殊线路规则统一交给缓存保存。
        parsePrice(data.price, cache);

        // 10. 拥挤规则 (crowd_rules)：v3 按方向聚合，展平为逐时段规则并映射方向键。
        if (data.crowdRules != null) {
            List<com.rabbitc.subway.data.parse.CrowdRule> rules = new ArrayList<>();
            for (CrowdRule cr : data.crowdRules) {
                if (cr.id == null || cr.periods == null) continue;
                String dirKey = dirIdIndex.get(cr.id);
                if (dirKey == null) continue; // 引用了不存在的方向，跳过
                for (CrowdRule.CrowdPeriod p : cr.periods) {
                    com.rabbitc.subway.data.parse.CrowdRule rule = new com.rabbitc.subway.data.parse.CrowdRule();
                    rule.directionKey = dirKey;
                    rule.startTime = p.from;
                    rule.endTime = p.to;
                    rule.factor = p.factor;
                    rule.days = normalizeDays(p.days);
                    rules.add(rule);
                }
            }
            cache.setCrowdRules(rules);
        }

        // 11. 行车间隔 (headway_rules)：v3 按线路聚合，展平为逐时段规则。
        if (data.headwayRules != null) {
            List<com.rabbitc.subway.data.parse.HeadwayRule> rules = new ArrayList<>();
            for (HeadwayRule hr : data.headwayRules) {
                if (hr.id == null || hr.periods == null) continue;
                for (HeadwayRule.HeadwayPeriod p : hr.periods) {
                    com.rabbitc.subway.data.parse.HeadwayRule rule = new com.rabbitc.subway.data.parse.HeadwayRule();
                    rule.lineId = hr.id;
                    rule.startTime = p.from;
                    rule.endTime = p.to;
                    rule.intervalSeconds = p.seconds;
                    rule.days = normalizeDays(p.days);
                    rules.add(rule);
                }
            }
            cache.setHeadwayRules(rules);
        }

        // 11.1 停站时间规则 (dwell_rules)：按线路或站点展开为 "站点名|方向键 → 秒"。
        if (data.dwellRules != null) {
            applyDwellRules(data.dwellRules, dirStations, stationStopTimeMap);
        }

        // 11.2 特殊线路票价 (special_line_rules)：统一票价或区段附加费。
        if (data.specialLineRules != null) {
            List<com.rabbitc.subway.data.parse.SpecialLineRule> rules = new ArrayList<>();
            for (SpecialLineRule sp : data.specialLineRules) {
                if (sp.line == null || sp.line.isEmpty()) continue;
                com.rabbitc.subway.data.parse.SpecialLineRule rule = new com.rabbitc.subway.data.parse.SpecialLineRule();
                rule.lineId = sp.line;
                rule.all = sp.all ? 1.0 : 0.0;
                if (sp.all) {
                    rule.defaultFare = sp.fare;
                } else if (sp.segment != null) {
                    // 区段附加费：映射为 surcharge 列表（价格单位与里程表一致）
                    rule.surcharge = new ArrayList<>();
                    com.rabbitc.subway.data.parse.Surcharge s = new com.rabbitc.subway.data.parse.Surcharge();
                    s.node_a = sp.segment.from;
                    s.node_b = sp.segment.to;
                    s.price = sp.fare;
                    rule.surcharge.add(s);
                }
                rules.add(rule);
            }
            cache.setSpecialLineRules(rules);
        }

        // 12. 运营时间 (open_times)：路径算法据此跳过未营业线路。
        if (data.openTimes != null) {
            List<com.rabbitc.subway.data.parse.OpenTimeRule> openTimes = new ArrayList<>();
            for (OpenTimeRule ot : data.openTimes) {
                com.rabbitc.subway.data.parse.OpenTimeRule rule = new com.rabbitc.subway.data.parse.OpenTimeRule();
                rule.lineId = ot.id;
                rule.startTime = ot.from;
                rule.endTime = ot.to;
                rule.days = expandDays(ot.days);
                openTimes.add(rule);
            }
            cache.setOpenTimes(openTimes);
        }

        // 13. 虚拟换乘 (virtual_transfers)：把站外或跨站换乘映射为图上的额外边。
        if (data.virtualTransfers != null) {
            List<com.rabbitc.subway.data.parse.VirtualTransfer> virtualTransfers = new ArrayList<>();
            for (VirtualTransfer vt : data.virtualTransfers) {
                com.rabbitc.subway.data.parse.VirtualTransfer cacheVt = new com.rabbitc.subway.data.parse.VirtualTransfer();
                cacheVt.stationA = vt.stationA;
                cacheVt.stationB = vt.stationB;
                cacheVt.timeMinutes = vt.timeMinutes;
                cacheVt.securityCheck = vt.securityCheck;
                cacheVt.viaDirections = vt.viaDirections;
                virtualTransfers.add(cacheVt);
            }
            cache.setVirtualTransfers(virtualTransfers);
        }

        // 14. 设置其他信息
        cache.setHasFutureMap(hasFutureMap);
        cache.setLineTotalLengthMapWithoutFuture(lineTotalLengthWithoutFutureMap);

        // 15. 标记缓存有效
        cache.setCacheValid(true);
    }

    // ======================== Gson 对象转 DataCache 核心转换 ========================

    /**
     * 解析站点数据（v3：数组条目，自带 name），填充坐标表与站名索引。
     *
     * <p>同名车站按数组出现顺序解析，后出现的条目不覆盖先出现的引用；
     * 无障碍设施随站点内联保存，供站点详情页展示。
     * 站点线路状态不在此处构建——MetroLang 的 stations[].lines 摘要为空，
     * 由 {@link #parseLines} 从 direction[].station 反向推导。</p>
     *
     * @param stations       JSON 解析出的站点数组
     * @param stationCoords  输出：站点坐标表（站点名 → [经度, 纬度]）
     * @param stationIndex   输出：站名 → Station 索引（首个出现的同名车站）
     */
    private static void parseStations(List<Station> stations,
                                      Map<String, double[]> stationCoords,
                                      Map<String, Station> stationIndex) {
        for (Station st : stations) {
            String name = st.name;
            if (name == null || name.isEmpty()) continue;

            // 坐标
            stationCoords.put(name, new double[]{st.lon, st.lat});

            // 站名索引：同名车站取首个出现的条目，与 metro-lang 的引用解析口径一致
            stationIndex.putIfAbsent(name, st);
        }
    }

    /**
     * 解析根级换乘规则数组（v3），填充换乘状态表与特殊换乘时间表。
     *
     * <p>metro-lang 的 type 为大写枚举（如 "OUT_STATION" / "HALL" / "PENDING"），
     * 这里归一化为运行时小写状态：出站类（OUT / OUT_STATION / OUT_LONG）→
     * "out_station"，PENDING → "pending"，其余视为站内 "operating"。
     * 换乘时间按秒保存，同时写入正反两个方向的键。</p>
     *
     * @param transfers             JSON 解析出的换乘规则数组
     * @param transferStatus        输出：换乘状态表（站点名 → ("lineA|lineB"（字典序）→ 状态)）
     * @param specialTransferTimeMap 输出：特殊换乘时间表（"站点名|line1|line2" → 秒，含正反键）
     */
    private static void parseTransfers(List<TransferRule> transfers,
                                       Map<String, Map<String, String>> transferStatus,
                                       Map<String, Double> specialTransferTimeMap) {
        for (TransferRule tr : transfers) {
            String station = tr.station;
            String from = tr.from;
            String to = tr.to;
            if (station == null || from == null || to == null || from.equals(to)) continue;

            String status = normalizeTransferType(tr.type);
            String pairKey = from.compareTo(to) < 0 ? from + "|" + to : to + "|" + from;
            transferStatus.computeIfAbsent(station, k -> new HashMap<>()).put(pairKey, status);

            if (tr.time > 0) {
                specialTransferTimeMap.put(station + "|" + from + "|" + to, tr.time);
                specialTransferTimeMap.put(station + "|" + to + "|" + from, tr.time);
            }
        }
    }

    /**
     * 归一化 MetroLang 换乘类型为运行时状态。
     *
     * @param type 大写类型标识，如 "OUT_STATION" / "HALL" / "PENDING"
     * @return 运行时状态："out_station" / "pending" / "operating"
     */
    private static String normalizeTransferType(String type) {
        if (type == null || type.isEmpty()) return "operating";
        switch (type) {
            case "OUT":
            case "OUT_STATION":
            case "OUT_LONG":
                return "out_station";
            case "PENDING":
                return "pending";
            default:
                // SAME / HALL / CHANNEL / CHANNEL_LONG / STAIR 等均视为站内换乘
                return "operating";
        }
    }

    /**
     * 解析线路数据，填充方向站点列表、线路属性表以及方向级属性表。
     *
     * <p>遍历每条线路的所有方向，提取以下信息：</p>
     * <ul>
     *   <li>方向站点列表（方向键 → 站点名列表）</li>
     *   <li>线路级属性：名称、颜色、城市、环线、快车、最大速度等</li>
     *   <li>方向级属性：方向类型、未来标记、环线标记等</li>
     *   <li>线路距离：通过去重边计算总长度（含/不含未来方向）</li>
     * </ul>
     *
     * @param lines                          JSON 解析出的线路集合（线路ID → Line）
     * @param keyCity                        默认城市（key_city）
     * @param dirStations                    输出：方向站点表（方向键 → 站点名列表）
     * @param lineNameMap                    输出：线路显示名称表（线路ID → 名称）
     * @param lineColorMap                   输出：线路颜色表（线路ID → #RRGGBB）
     * @param lineCityMap                    输出：线路所属城市表（线路ID → 城市名）
     * @param hasLoopMap                     输出：线路是否有环线（线路ID → boolean）
     * @param lineTotalLengthMap             输出：线路总长度表（线路ID → 公里数）
     * @param lineSpecialMap                 输出：线路是否为特殊线路（线路ID → boolean）
     * @param lineHasFastMap                 输出：线路是否有快车（线路ID → boolean）
     * @param lineMaxSpeedMap                输出：线路最高速度表（线路ID → km/h）
     * @param dirDirectionTypeMap            输出：方向类型表（方向键 → 类型字符串）
     * @param dirFutureMap                   输出：方向未来状态表（方向键 → isFuture）
     * @param lineBrakeMap                   输出：线路制动加速度表（线路ID → m/s²）
     * @param stationStopTimeMap             输出：站点停站时间表（站点名|方向键 → 秒）
     * @param lineFormationMap               输出：线路编组表（线路ID → 编组如"6B"，多条以逗号连接）
     * @param linePowerKwMap                 输出：线路功率表（线路ID → kW）
     * @param lineTotalLengthWithoutFutureMap 输出：不含未来站点的线路长度表（线路ID → 公里数）
     * @param hasFutureMap                   输出：线路是否包含未来方向（线路ID → boolean）
     * @param lineAllFutureMap               输出：线路是否全为未来方向（线路ID → boolean）
     * @param dirLoopMap                     输出：方向环线标记（方向键 → isLoop）
     * @param stationLineStatus              输出：站点线路状态表（站点名 → (线路ID → "operating"/"pending")）
     * @param dirIdIndex                     输出：方向 ID → 方向键（供拥挤规则等按方向 ID 引用的数据映射）
     */
    private static void parseLines(Map<String, Line> lines, String keyCity,
                                   Map<String, List<String>> dirStations,
                                   Map<String, String> lineNameMap,
                                   Map<String, String> lineColorMap,
                                   Map<String, String> lineCityMap,
                                   Map<String, Boolean> hasLoopMap,
                                   Map<String, Double> lineTotalLengthMap,
                                   Map<String, Boolean> lineSpecialMap,
                                   Map<String, Boolean> lineHasFastMap,
                                   Map<String, Integer> lineMaxSpeedMap,
                                   Map<String, String> dirDirectionTypeMap,
                                   Map<String, Boolean> dirFutureMap,
                                   Map<String, Double> lineBrakeMap,
                                   Map<String, Double> stationStopTimeMap,
                                   Map<String, String> lineFormationMap,
                                   Map<String, Double> linePowerKwMap,
                                   Map<String, Double> lineTotalLengthWithoutFutureMap,
                                   Map<String, Boolean> hasFutureMap,
                                   Map<String, Boolean> lineAllFutureMap,
                                   Map<String, Boolean> dirLoopMap,
                                   Map<String, Map<String, String>> stationLineStatus,
                                   Map<String, String> dirIdIndex) {
        for (Map.Entry<String, Line> lineEntry : lines.entrySet()) {
            String lineId = lineEntry.getKey();
            Line line = lineEntry.getValue();
            boolean allFuture = true;

            // 性能参数
            if (line.powerKw > 0) linePowerKwMap.put(lineId, line.powerKw);
            if (line.brake > 0) lineBrakeMap.put(lineId, line.brake);

            String city = line.city != null ? line.city : keyCity;
            String baseName = line.name != null ? line.name : lineId;
            String color = line.color != null ? line.color : "#999999";
            if (!color.startsWith("#")) color = "#" + color;
            String displayName = city.equals(keyCity) ? baseName : city + baseName;

            lineMaxSpeedMap.put(lineId, line.maxspeed);
            lineNameMap.put(lineId, displayName);
            lineColorMap.put(lineId, color);
            lineCityMap.put(lineId, city);

            lineSpecialMap.put(lineId, line.special);
            if (line.formation != null && !line.formation.isEmpty()) {
                lineFormationMap.put(lineId, String.join(",", line.formation));
            }

            // ---- 方向遍历 & 长度计算合并 ----
            if (line.direction != null) {
                boolean hasLoop = false;
                boolean hasFast = false;
                boolean hasFuture = false;

                // 用于去重边（总长度 & 不含未来方向长度）
                Set<String> edgeSetAll = new HashSet<>();
                Set<String> edgeSetWithoutFuture = new HashSet<>();
                double totalLengthAll = 0.0;
                double totalLengthWithoutFuture = 0.0;

                for (Direction dir : line.direction) {
                    if (dir.isFast) {
                        hasFast = true;
                        continue; // 快车不计入长度
                    }

                    String start = dir.start;
                    String end = dir.end;
                    String dirKey = GraphKeyUtils.makeDirKey(lineId, start, end);

                    // 方向 ID 索引（拥挤规则按 MetroLang 方向 ID 引用）
                    if (dir.id != null && !dir.id.isEmpty()) {
                        dirIdIndex.putIfAbsent(dir.id, dirKey);
                    }

                    // 未来标记
                    if (dir.isFuture) hasFuture = true;
                    else allFuture = false;
                    dirFutureMap.put(dirKey, dir.isFuture);

                    if (dir.isLoop) {
                        hasLoop = true;
                        dirLoopMap.put(dirKey, true);
                        if (dir.directionType != null && !dir.directionType.isEmpty()) {
                            dirDirectionTypeMap.put(dirKey, dir.directionType);
                        }
                    } else {
                        dirLoopMap.put(dirKey, false);
                    }

                    // 站点列表 & 停站时间 & 距离累加
                    List<String> stationNames = new ArrayList<>();
                    if (dir.station != null) {
                        int size = dir.station.size();
                        for (int i = 0; i < size; i++) {
                            DirectionStation ds = dir.station.get(i);
                            String name = ds.name;
                            stationNames.add(name);
                            if (ds.dwellTime != null && ds.dwellTime > 0) {
                                stationStopTimeMap.put(name + "|" + dirKey, ds.dwellTime);
                            }

                            // 计算边长（去重）
                            if (i < size - 1) {
                                String nextName = dir.station.get(i + 1).name;
                                double dist = ds.next != null ? ds.next : 0.0;
                                String key = name.compareTo(nextName) < 0 ?
                                        name + "|" + nextName : nextName + "|" + name;
                                if (edgeSetAll.add(key)) {
                                    totalLengthAll += dist;
                                }
                                if (!dir.isFuture && edgeSetWithoutFuture.add(key)) {
                                    totalLengthWithoutFuture += dist;
                                }
                            } else if (dir.isLoop) {
                                // 环线闭合边
                                String firstName = dir.station.get(0).name;
                                double dist = ds.next != null ? ds.next : 0.0;
                                String key = name.compareTo(firstName) < 0 ?
                                        name + "|" + firstName : firstName + "|" + name;
                                if (edgeSetAll.add(key)) {
                                    totalLengthAll += dist;
                                }
                                if (!dir.isFuture && edgeSetWithoutFuture.add(key)) {
                                    totalLengthWithoutFuture += dist;
                                }
                            }
                        }
                    }
                    dirStations.put(dirKey, stationNames);

                    // 站点线路状态（v3：由 direction[].station 反向推导）
                    // operating 优先：同一站点同一线路存在已开通方向时不受未来方向影响
                    String lineStatus = dir.isFuture ? "pending" : "operating";
                    for (String name : stationNames) {
                        Map<String, String> m = stationLineStatus.computeIfAbsent(name, k -> new HashMap<>());
                        if ("operating".equals(lineStatus) || !m.containsKey(lineId)) {
                            m.put(lineId, lineStatus);
                        }
                    }
                }

                // 线路级属性
                hasLoopMap.put(lineId, hasLoop);
                lineHasFastMap.put(lineId, hasFast);
                lineAllFutureMap.put(lineId, allFuture);

                // 存储长度结果
                lineTotalLengthMap.put(lineId, totalLengthAll);
                lineTotalLengthWithoutFutureMap.put(lineId, totalLengthWithoutFuture);
                hasFutureMap.put(lineId, hasFuture);
            }
        }
    }

    // ======================== 业务计算：分段运营判断 ========================

    /**
     * 判断线路是否为分段运营（即线路的站点组成多个不连通的连通分量）。
     *
     * <p>通过构建邻接表并运行 BFS 连通分量检测来实现。
     * 若存在超过一个连通分量，则该线路为分段运营。</p>
     *
     * @param lineId       线路ID
     * @param dirStations  所有方向的站点列表
     * @return {@code true} 如果线路存在多个不连通的运营区段
     */
    private static boolean isLineSegmented(String lineId, Map<String, List<String>> dirStations) {
        Map<String, Set<String>> adj = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : dirStations.entrySet()) {
            String dirKey = entry.getKey();
            String[] parts = GraphKeyUtils.parseDirKey(dirKey);
            if (!StationUtils.isFutureDirection(dirKey)) continue;
            if (!parts[0].equals(lineId)) continue;

            List<String> stations = entry.getValue();
            for (int i = 0; i < stations.size() - 1; i++) {
                String a = stations.get(i);
                String b = stations.get(i + 1);
                adj.computeIfAbsent(a, k -> new HashSet<>()).add(b);
                adj.computeIfAbsent(b, k -> new HashSet<>()).add(a);
            }

            // 环线闭合边
            String start = parts[1];
            String end = parts[2];
            if (start.equals(end) && stations.size() > 1) {
                String first = stations.get(0);
                String last = stations.get(stations.size() - 1);
                adj.computeIfAbsent(first, k -> new HashSet<>()).add(last);
                adj.computeIfAbsent(last, k -> new HashSet<>()).add(first);
            }
        }

        if (adj.isEmpty()) return false;

        Set<String> visited = new HashSet<>();
        int components = 0;
        for (String station : adj.keySet()) {
            if (!visited.contains(station)) {
                components++;
                if (components > 1) return true;
                Stack<String> stack = new Stack<>();
                stack.push(station);
                visited.add(station);
                while (!stack.isEmpty()) {
                    String cur = stack.pop();
                    for (String neighbor : adj.getOrDefault(cur, Collections.emptySet())) {
                        if (!visited.contains(neighbor)) {
                            visited.add(neighbor);
                            stack.push(neighbor);
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * 把 MetroLang 的 days 修饰符原文展开为英文星期名列表。
     *
     * <p>MetroLang 保留源码原文（如 "WORKDAY"）；运行时消费端使用
     * {@code SimpleDateFormat("EEEE", Locale.US)} 生成的英文星期名做包含判断，
     * 因此这里把常见修饰符展开成对应集合：</p>
     * <ul>
     *   <li>WORKDAY / 工作日 → 周一至周五</li>
     *   <li>WEEKEND / 周末 → 周六与周日</li>
     *   <li>空或其他值 → null（不限日期）</li>
     * </ul>
     *
     * @param raw days 修饰符原文，可为 null
     * @return 英文星期名列表，或 null 表示不限
     */
    private static List<String> expandDays(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "WORKDAY":
            case "WORKDAYS":
                return Arrays.asList("Monday", "Tuesday", "Wednesday", "Thursday", "Friday");
            case "WEEKEND":
            case "WEEKENDS":
                return Arrays.asList("Saturday", "Sunday");
            default:
                return null;
        }
    }

    /**
     * 把 MetroLang 的星期缩写列表归一化为英文星期全称列表。
     *
     * <p>MetroLang 输出英文小写缩写（["mon","tue",...]），运行时消费端
     * （TimeCalculator / 页面展示）使用英文全称做包含判断，这里逐项展开；
     * 无法识别的项原样保留，空列表返回 null 表示不限日期。</p>
     *
     * @param days 星期缩写列表，可为 null
     * @return 英文星期全称列表，或 null 表示不限
     */
    private static List<String> normalizeDays(List<String> days) {
        if (days == null || days.isEmpty()) return null;
        Map<String, String> mapping = new HashMap<>();
        mapping.put("mon", "Monday");
        mapping.put("tue", "Tuesday");
        mapping.put("wed", "Wednesday");
        mapping.put("thu", "Thursday");
        mapping.put("fri", "Friday");
        mapping.put("sat", "Saturday");
        mapping.put("sun", "Sunday");
        List<String> result = new ArrayList<>();
        for (String d : days) {
            if (d == null || d.isEmpty()) continue;
            result.add(mapping.getOrDefault(d.trim().toLowerCase(Locale.ROOT), d));
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * 把停站时间规则展开进站点停站时间表。
     *
     * <p>scope 为 "line" 时对该线路所有方向的所有站点生效；
     * scope 为 "station" 时对经过该站的所有方向生效。
     * 已存在的逐站停站时间（方向内 DWELL_TIME）优先，不被本规则覆盖。</p>
     *
     * @param dwellRules        停站时间规则列表
     * @param dirStations       方向站点表（方向键 → 站点名列表）
     * @param stationStopTimeMap 输出：站点停站时间表（"站点名|方向键" → 秒）
     */
    private static void applyDwellRules(List<DwellRule> dwellRules,
                                        Map<String, List<String>> dirStations,
                                        Map<String, Double> stationStopTimeMap) {
        for (DwellRule dr : dwellRules) {
            if (dr.seconds <= 0) continue;
            if ("line".equals(dr.scope) && dr.line != null) {
                for (Map.Entry<String, List<String>> entry : dirStations.entrySet()) {
                    String[] parts = GraphKeyUtils.parseDirKey(entry.getKey());
                    if (parts == null || !parts[0].equals(dr.line)) continue;
                    for (String name : entry.getValue()) {
                        stationStopTimeMap.putIfAbsent(name + "|" + entry.getKey(), dr.seconds);
                    }
                }
            } else if ("station".equals(dr.scope) && dr.station != null) {
                for (Map.Entry<String, List<String>> entry : dirStations.entrySet()) {
                    if (entry.getValue().contains(dr.station)) {
                        stationStopTimeMap.putIfAbsent(dr.station + "|" + entry.getKey(), dr.seconds);
                    }
                }
            }
        }
    }

    // ======================== 票价解析 ========================

    /**
     * 解析票价数据并填充缓存。
     *
     * <p>处理票价体系中的核心部分：</p>
     * <ul>
     *   <li><b>计价类型</b>（pricingType）：如 "distance" 按里程计价</li>
     *   <li><b>阶梯区间</b>（segments）：里程范围与对应的票价</li>
     *   <li><b>线性扩展</b>（linearExtension）：超出阶梯上限后的线性计价规则</li>
     *   <li><b>特殊线路规则</b>（specialLines）：特定线路的附加费或独立计价规则（MetroLang 暂未输出）</li>
     * </ul>
     * <p>还会设置票价封顶（fareCap）和默认票价（defaultFare）。</p>
     *
     * @param price JSON 解析出的票价对象（可为 null）
     * @param cache 全局数据缓存实例
     */
    private static void parsePrice(Price price, DataCache cache) {
        if (price == null) {
            cache.setFareSegments(null);
            cache.setSpecialLineRules(null);
            cache.setDefaultFare(null);
            return;
        }

        cache.setPricingType(price.pricingType != null ? price.pricingType : "distance");

        // 阶梯区间
        if (price.segments != null) {
            List<com.rabbitc.subway.data.parse.FareSegment> segments = new ArrayList<>();
            for (FareSegment fs : price.segments) {
                com.rabbitc.subway.data.parse.FareSegment cacheFs = new com.rabbitc.subway.data.parse.FareSegment();
                cacheFs.rangeStart = fs.rangeStart;
                cacheFs.rangeEnd = fs.rangeEnd;
                cacheFs.fare = fs.fare;
                segments.add(cacheFs);
            }
            cache.setFareSegments(segments);
        }

        // 线性扩展
        if (price.linearExtension != null) {
            com.rabbitc.subway.data.parse.LinearExtension le = new com.rabbitc.subway.data.parse.LinearExtension();
            le.threshold = price.linearExtension.threshold;
            le.baseFare = price.linearExtension.baseFare;
            le.ratePerKm = price.linearExtension.ratePerKm;
            cache.setLinearExtension(le);
        }

        // 特殊线路规则已迁移至根级 special_line_rules（见 parse() 步骤 11.2）

        cache.setFareCap(price.fareCap);
        cache.setDefaultFare(price.defaultFare);
    }
}
