package com.rabbitc.subway;

import com.google.gson.Gson;
import com.rabbitc.subway.data.MetroV2;
import com.rabbitc.subway.data.NetworkMileage;
import com.rabbitc.subway.data.json.MetroData;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NetworkMileageTest {
    @Test
    public void onlyDeclaredSharedTrackCountsOnce() {
        String json = "{\"stations\":[],\"lines\":{"
                + "\"L1\":{\"direction\":[{\"id\":\"L1_0\",\"station\":[{\"name\":\"A\",\"next\":1.8},{\"name\":\"B\"}]}]},"
                + "\"L2\":{\"direction\":[{\"id\":\"L2_0\",\"via_directions\":[\"L1_0\"],\"station\":["
                + "{\"name\":\"A\",\"next\":1.7},{\"name\":\"B\",\"next\":2},{\"name\":\"C\"}]}]},"
                + "\"L3\":{\"direction\":[{\"id\":\"L3_0\",\"station\":[{\"name\":\"A\",\"next\":1.9},{\"name\":\"B\"}]}]},"
                + "\"L4\":{\"direction\":[{\"id\":\"L4_0\",\"is_future\":true,\"station\":[{\"name\":\"C\",\"next\":3},{\"name\":\"D\"}]}]}"
                + "}}";
        MetroData data = new Gson().fromJson(MetroV2.forRouting(json), MetroData.class);
        assertEquals(5.7, NetworkMileage.total(data, false), 0.001);
        assertEquals(8.7, NetworkMileage.total(data, true), 0.001);
    }
}
