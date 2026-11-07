package com.rabbitc.subway;

import com.rabbitc.subway.algroithm.MetroGraph;
import com.rabbitc.subway.algroithm.FareCalculator;
import com.rabbitc.subway.algroithm.TimeCalculator;
import com.rabbitc.subway.data.DataCache;
import com.rabbitc.subway.data.DataParser;
import com.rabbitc.subway.data.SearchWeight;
import com.rabbitc.subway.data.MetroV2;
import com.rabbitc.subway.utils.GraphKeyUtils;
import com.rabbitc.subway.utils.StationUtils;

import org.junit.After;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MetroV2RoutingTest {
    private final DataCache cache = DataCache.getInstance();

    @After
    public void clearCache() {
        cache.clear();
    }

    @Test
    public void legacyMapCanBeEditedAndKeepsFacilitiesAndTransferTime() {
        String legacy = "{\"stations\":{\"A\":{\"transfers\":{\"L1|L2\":"
                + "{\"status\":\"operating\",\"time\":42.5}}}},"
                + "\"facilities\":{\"A\":{\"accessible\":true}},"
                + "\"lines\":{\"L1\":{\"formation\":\"6A\"}}}";
        var edited = MetroV2.INSTANCE.editableCopy(legacy);
        var station = edited.getAsJsonArray("stations").get(0).getAsJsonObject();
        assertEquals("A", station.get("name").getAsString());
        assertTrue(station.getAsJsonObject("accessibility").get("accessible").getAsBoolean());
        var transfer = edited.getAsJsonArray("transfers").get(0).getAsJsonObject();
        assertEquals(42.5, transfer.get("time").getAsDouble(), 0.001);
        assertEquals("6A", edited.getAsJsonObject("lines").getAsJsonObject("L1")
                .getAsJsonArray("formation").get(0).getAsString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyDocumentCannotReplaceActiveMap() throws Exception {
        cache.setJsonData("{\"stations\":[],\"lines\":{}}");
        DataParser.parse();
    }

    @Test
    public void measuredRunDwellAndTransferReachRouteCalculations() throws Exception {
        cache.setJsonData("{"
                + "\"key_city\":\"测试\","
                + "\"operation_params\":{\"entry_time\":0,\"dwell_time\":30},"
                + "\"stations\":[{\"name\":\"A\"},{\"name\":\"B\"},{\"name\":\"C\"}],"
                + "\"lines\":{"
                + "\"L1\":{\"direction\":[{\"start\":\"A\",\"end\":\"B\",\"station\":["
                + "{\"name\":\"A\",\"next\":1.2,\"dwell_time\":19,\"run_time_seconds\":83.5},{\"name\":\"B\"}]}]},"
                + "\"L2\":{\"direction\":[{\"start\":\"B\",\"end\":\"C\",\"station\":["
                + "{\"name\":\"B\",\"next\":1},{\"name\":\"C\"}]}]}},"
                + "\"transfers\":[{\"type\":\"OPERATING\",\"station\":\"B\",\"from\":\"L1\",\"to\":\"L2\",\"time\":47.5}]}");
        DataParser.parse();
        assertEquals(2.0, FareCalculator.calcBaseFare(1.2), 0.001);
        MetroGraph graph = new MetroGraph();
        graph.buildGraphs(cache.getMetroData(), cache.getKeyCity());

        String first = GraphKeyUtils.makeDirKey("L1", "A", "B");
        String second = GraphKeyUtils.makeDirKey("L2", "B", "C");
        assertEquals(1.2, graph.getDistanceInDirection(first, "A", "B"), 0.001);
        assertEquals(83.5, graph.computeRunTime(1.2, 80, "L1", first, "A", "B"), 0.001);
        assertEquals(19.0, cache.getStationStopTimeMap().get("A|" + first), 0.001);
        assertEquals(47.5, StationUtils.getTransferTime("B", first, second), 0.001);
        assertTrue(graph.computeRunTime(1.2, 80, "L1", first, "B", "A") != 83.5);

        long now = 1_700_000_000_000L;
        List<String> path = List.of(first + "|A", first + "|B");
        double wait = TimeCalculator.computeTransferWaitTime(first, now, cache);
        double total = TimeCalculator.computeTotalTime(
                path, graph, new SearchWeight(0, 0, 0, 1, 0), now);
        assertEquals(83.5 + 19 + wait, total, 0.001);
    }
}
