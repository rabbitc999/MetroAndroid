package com.rabbitc.subway.data.json;

/**
 * 停站时间规则（v3：MetroLang JSON 根级 dwell_rules 数组条目）。
 *
 * <p>结构与 MetroLang 编译器输出对齐：按线路或按站点声明列车停站时间，
 * 运行时由 {@code DataParser} 展开为 "站点名|方向键 → 秒" 的停站时间表。</p>
 */
public class DwellRule {

    /** 规则作用域："line"（整条线路）或 "station"（单站）（JSON: "scope"） */
    public String scope;

    /** 线路 ID；scope 为 "line" 时有效（JSON: "line"） */
    public String line;

    /** 站点名称；scope 为 "station" 时有效（JSON: "station"） */
    public String station;

    /** 停站时间（秒）（JSON: "seconds"） */
    public double seconds;
}
