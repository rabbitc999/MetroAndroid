package com.rabbitc.subway.data.json;

/**
 * 换乘类型定义模型（v3：MetroLang JSON 根级 transfer_types 数组条目）。
 *
 * <p>对应 MetroLang 的 TRANSFER_TYPE 块，为一种换乘方式给出
 * 显示名称、默认耗时与是否需要再次安检。</p>
 */
public class TransferType {

    /** 类型标识（TRANSFER_TYPE 块的第一列关键字），如 "SAME" */
    public String id;

    /** 显示名称，如 "同台换乘" */
    public String name;

    /** 默认换乘耗时（秒），缺省为 0 */
    public double time;

    /** 运营状态，如 "OPERATING" */
    public String status;

    /** 是否需要出站安检（出现 SECURITY_CHECK 即为 true） */
    @com.google.gson.annotations.SerializedName("security_check")
    public boolean securityCheck;
}
