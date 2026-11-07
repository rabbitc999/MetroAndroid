package com.rabbitc.subway.data.json;

import java.util.List;

/**
 * 行车间隔规则模型（v3：MetroLang JSON 根级 headway_rules 数组条目）。
 *
 * <p>结构与 MetroLang 编译器输出对齐：按线路聚合，每条规则内嵌多个时段
 * （periods），每个时段带起止时间、发车间隔（秒）与适用星期（英文小写缩写）。
 * 运行时由 {@code DataParser} 展平为逐时段的扁平规则。</p>
 */
public class HeadwayRule {

    /** 线路 ID（JSON: "id"），如 "SZ1" */
    public String id;

    /** 发车间隔时段列表 */
    public List<HeadwayPeriod> periods;
    public String lineId; public String startTime; public String endTime; public int intervalSeconds; public List<String> days;

    /** 单个发车间隔时段。 */
    public static class HeadwayPeriod {
        /** 时段开始，格式 "HH:mm"（JSON: "from"） */
        public String from;
        /** 时段结束，格式 "HH:mm"（JSON: "to"） */
        public String to;
        /** 发车间隔（秒）（JSON: "seconds"） */
        public int seconds;
        /** 适用星期列表，英文小写缩写如 ["mon","tue"]（JSON: "days"） */
        public List<String> days;
    }
}
