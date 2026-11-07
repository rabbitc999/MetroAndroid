package com.rabbitc.subway;

import com.rabbitc.subway.algroithm.MetroGraph;
import com.rabbitc.subway.data.DataCache;
import com.rabbitc.subway.data.DataParser;
import com.rabbitc.subway.data.PathResult;
import com.rabbitc.subway.data.SearchWeight;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * DataParser 对 MetroLang JSON（v3 架构）的解析回归测试。
 *
 * <p>测试数据取自 metro-lang 仓库对 {@code samples/shenzhen.m} 的编译产物
 * （深圳地铁：16 条线路、354 座车站、103 条换乘规则、14 段阶梯票价）。
 * 覆盖：站点数组解析、站名索引、根级换乘数组归一化、票价阶梯、
 * 运营参数默认值以及 MetroGraph 的建图与寻路。</p>
 */
public class DataParserTest {

    private static DataCache cache;

    @BeforeClass
    public static void setUpClass() throws Exception {
        String json = readResource("/shenzhen.json");
        cache = DataCache.getInstance();
        cache.clear();
        cache.setJsonData(json);
        DataParser.parse();
    }

    private static String readResource(String name) throws Exception {
        try (InputStream is = DataParserTest.class.getResourceAsStream(name)) {
            assertNotNull("测试资源缺失: " + name, is);
            try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8).useDelimiter("\\A")) {
                return scanner.hasNext() ? scanner.next() : "";
            }
        }
    }

    @Test
    public void parse_marksCacheValid() {
        assertTrue("解析成功后缓存应标记为有效", cache.isCacheValid());
    }

    @Test
    public void parse_readsMetaAndParams() {
        assertEquals("深圳", cache.getKeyCity());
        assertEquals("站内换乘时间应来自 operation_params.inner_transfer_time", 300.0, cache.getAvgTransferTime(), 0.01);
        assertEquals("停站时间应来自 operation_params.dwell_time", 45.0, cache.getAvgStopTime(), 0.01);
        assertEquals("出站换乘时间应来自 operation_params.out_station_transfer_time", 600.0, cache.getOutStationTransferTime(), 0.01);
        assertEquals("进站时间应来自 operation_params.entry_time", 120.0, cache.getAvgEntryTime(), 0.01);
    }

    @Test
    public void parse_stationsArray() {
        Map<String, double[]> coords = cache.getStationCoords();
        assertNotNull(coords);
        assertEquals("深圳样例应有 354 座车站", 354, coords.size());
        assertNotNull("站名索引应建立", cache.getStationIndex());
        assertEquals("站名索引条目数应与坐标数一致", 354, cache.getStationIndex().size());
        assertNotNull("福田口岸应有坐标", coords.get("福田口岸"));
        assertNotNull("站点线路状态表应填充", cache.getStationLineStatusMap().get("福田口岸"));
    }

    @Test
    public void parse_lines() {
        Map<String, String> names = cache.getLineNameMap();
        assertNotNull(names);
        assertEquals("深圳样例应有 16 条线路", 16, names.size());
        assertTrue("应包含 4 号线", names.containsKey("SZ4"));
        assertEquals("编组应从数组拼接", "6A", cache.getLineFormationMap().get("SZ4"));
        assertTrue("线路颜色应以 # 开头", cache.getLineColorMap().get("SZ4").startsWith("#"));
    }

    @Test
    public void parse_transfersArray() {
        Map<String, Map<String, String>> status = cache.getTransferStatusMap();
        assertNotNull(status);
        int total = status.values().stream().mapToInt(Map::size).sum();
        // 深圳样例 103 条换乘规则，同站同线路对重复声明合并后为 100 条
        assertEquals("换乘状态条数（站+线路对）", 100, total);

        // 出站换乘类型应归一化为 out_station：车公庙 1/7/9/11 号线间存在 OUT 类换乘
        boolean hasOutStation = status.values().stream()
                .flatMap(m -> m.values().stream())
                .anyMatch("out_station"::equals);
        assertTrue("OUT/OUT_STATION 类型应归一化为 out_station", hasOutStation);

        // 特殊换乘时间：正反双向都应写入
        Map<String, Double> special = cache.getSpecialTransferTimeMap();
        assertNotNull(special);
        assertTrue("换乘时间应写入特殊时间表", special.size() >= 103);
    }

    @Test
    public void parse_fare() {
        assertNotNull(cache.getFareSegments());
        assertEquals("深圳样例应有 14 段阶梯票价", 14, cache.getFareSegments().size());
        assertNotNull("线性延伸规则应解析", cache.getLinearExtension());
        assertEquals(96.0, cache.getLinearExtension().threshold, 0.01);
    }

    @Test
    public void metroGraph_buildsAndFindsPath() {
        MetroGraph graph = new MetroGraph();
        Set<String> stations = graph.buildGraphs(cache.getMetroData(), cache.getKeyCity());
        assertTrue("建图后站点集合不应为空", stations.size() > 300);

        // 福田口岸（4号线南端）→ 深圳北站（5/6号线），跨线换乘场景
        SearchWeight weight = new SearchWeight(0, 0, 0, 1, 0, 0);
        PathResult result = graph.findTransferPath("福田口岸", "深圳北站", weight);
        assertNotNull(result);
        assertTrue("福田口岸 → 深圳北 应存在可达路径，实际 transfers=" + (result == null ? "null" : result.transfers),
                result != null && result.transfers != -1);
    }
}
