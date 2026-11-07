package com.rabbitc.subway.data.json;

/**
 * 特殊线路票价规则（v3：MetroLang JSON 根级 special_line_rules 数组条目）。
 *
 * <p>结构与 MetroLang 编译器输出对齐：一条规则声明一条特殊线路，
 * 可选全线统一票价（all + fare），或仅对指定区段加收（segment + fare）。
 * 运行时由 {@code DataParser} 映射为扁平的线路级规则与区间附加费。</p>
 */
public class SpecialLineRule {

    /** 特殊线路 ID（JSON: "line"），如 "SD"（北京大兴机场线） */
    public String line;

    /** 是否全线统一票价（JSON: "all"） */
    public boolean all;

    /** 票价金额（元）（JSON: "fare"）；配合 all 为统一票价，配合 segment 为区段附加费 */
    public double fare;

    /** 区段限定；仅在该区间内按 fare 加收（JSON: "segment"） */
    public SegmentRange segment;

    /** 特殊票价的适用区段。 */
    public static class SegmentRange {
        /** 区段起点站名（JSON: "from"） */
        public String from;
        /** 区段终点站名（JSON: "to"） */
        public String to;
    }
}
