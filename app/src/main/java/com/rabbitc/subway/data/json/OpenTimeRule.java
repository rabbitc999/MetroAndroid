package com.rabbitc.subway.data.json;

import com.google.gson.annotations.SerializedName;

/**
 * 运营时间规则模型（v3：MetroLang JSON 根级 open_times 数组条目），
 * 定义线路在不同日期的首末班运营时段。
 */
public class OpenTimeRule {

    /** 线路标识符，或 "GLOBAL" 表示全局默认（JSON: "id"） */
    public String id;
    @SerializedName("line_id") public String lineId;

    /** 运营开始时间，格式 "HH:mm"（JSON: "from"） */
    public String from;
    @SerializedName("start_time") public String startTime;

    /** 运营结束时间，格式 "HH:mm"（JSON: "to"） */
    public String to;
    @SerializedName("end_time") public String endTime;

    /** 适用日期修饰符原文，如 "WORKDAY"；为空表示不限（JSON: "days"） */
    public String days;
}
