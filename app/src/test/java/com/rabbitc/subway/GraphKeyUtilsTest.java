package com.rabbitc.subway;

import com.rabbitc.subway.utils.GraphKeyUtils;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class GraphKeyUtilsTest {
    @Test
    public void reverseDirectionOnSameLineIsNotTransfer() {
        assertEquals(0, GraphKeyUtils.countTransfers(Arrays.asList(
                "SZ6|松岗|科学馆|上屋",
                "SZ6|科学馆|松岗|上屋")));
    }

    @Test
    public void changingLineIsTransfer() {
        assertEquals(1, GraphKeyUtils.countTransfers(Arrays.asList(
                "SZ6|松岗|科学馆|上屋",
                "SZ1|罗湖|机场东|科学馆")));
    }
}
