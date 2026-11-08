package com.rabbitc.subway;

import com.rabbitc.subway.algroithm.MetroGraph;
import com.rabbitc.subway.data.DataCache;
import com.rabbitc.subway.data.DataParser;
import com.rabbitc.subway.data.json.Line;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BuiltInMetroMapsTest {
    @Test
    public void newCitiesLoadWithRoutesAndBothEncyclopedias() throws Exception {
        DataCache cache = DataCache.getInstance();
        try {
            for (String city : List.of("杭州", "成都", "武汉", "西安")) {
                cache.clear();
                Path path = Path.of("src/main/assets/map", city + "地铁.json");
                try (InputStream input = Files.newInputStream(path)) {
                    cache.setJsonData(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
                DataParser.parse();
                assertEquals(city, cache.getKeyCity());
                assertTrue(new MetroGraph().buildGraphs(cache.getMetroData(), city).size() > 200);
                for (Line line : cache.getMetroData().lines.values()) {
                    assertTrue(line.resourceLinks.stream().anyMatch(url -> url.contains("wikipedia.org")));
                    assertTrue(line.resourceLinks.stream().anyMatch(url -> url.contains("baike.baidu.com")));
                }
            }
        } finally {
            cache.clear();
        }
    }
}
