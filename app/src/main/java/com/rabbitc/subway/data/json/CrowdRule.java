package com.rabbitc.subway.data.json;

import java.util.List;

/**
 * 拥挤度规则模型（v3：MetroLang JSON 根级 crowd_rules 数组条目）。
 *
 * <p>结构与 MetroLang 编译器输出对齐：按方向聚合（id 为方向 ID，如 "SZ1_0"），
 * 每条规则内嵌多个时段（periods），每个时段带起止时间、拥挤系数与适用星期
 * （英文小写缩写）。运行时由 {@code DataParser} 结合方向 ID → 方向键映射
 * 展平为逐时段的扁平规则。</p>
 */
public class CrowdRule {

    /** 方向 ID（JSON: "id"），如 "SZ1_0" */
    public String id;

    /** 拥挤时段列表 */
    public List<CrowdPeriod> periods;

    /** 单个拥挤时段。 */
    public static class CrowdPeriod {
        /** 时段开始，格式 "HH:mm"（JSON: "from"） */
        public String from;
        /** 时段结束，格式 "HH:mm"（JSON: "to"） */
        public String to;
        /** 拥挤系数，数值越大表示越拥挤（JSON: "factor"） */
        public double factor;
        /** 适用星期列表，英文小写缩写如 ["mon","tue"]（JSON: "days"） */
        public List<String> days;
    }
}
