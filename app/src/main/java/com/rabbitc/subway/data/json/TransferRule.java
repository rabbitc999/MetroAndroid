package com.rabbitc.subway.data.json;

/**
 * 换乘规则数据模型（v3：MetroLang JSON 根级 transfers 数组条目）。
 *
 * <p>对应 MetroLang 的 TRANSFER_DEFINE 块或单行 TRANSFER 语句，
 * 描述两线之间在指定车站的换乘方式与耗时。</p>
 */
public class TransferRule {

    /** 换乘类型标识，取值如 "SAME" / "HALL" / "CHANNEL" / "STAIR" / "OUT" / "OUT_STATION" / "OUT_LONG" / "CHANNEL_LONG" / "PENDING" */
    public String type;

    /** 换乘发生的车站名称 */
    public String station;

    /** 换乘起始线路 ID */
    public String from;

    /** 换乘目标线路 ID */
    public String to;

    /** 换乘耗时（秒）；源码未写 TIME 时由换乘类型或全局默认值填充 */
    public double time;
}
