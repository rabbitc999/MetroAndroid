package com.rabbitc.subway.data.json;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

/**
 * 根 JSON 对象，是地铁地图数据文件的顶层结构（v3 架构：MetroLang JSON）。
 *
 * <p>数据源为 MetroLang 编译器（metro-lang）的 {@code compile} 产物，
 * 格式规范见 metro-lang 仓库 {@code doc/02-json-format.md}。
 * 使用 Gson 从 JSON 反序列化，字段名通过 {@link SerializedName} 注解映射。</p>
 *
 * <h3>元信息</h3>
 * <ul>
 *   <li>{@link #keyCity} — 主要城市名称，如 "北京"</li>
 *   <li>{@link #author} — 数据作者</li>
 *   <li>{@link #version} — 数据版本号</li>
 *   <li>{@link #createDate} — 数据创建日期</li>
 *   <li>{@link #updateDate} — 数据最后更新日期</li>
 *   <li>{@link #otherCities} — 覆盖的其他城市列表</li>
 * </ul>
 *
 * <h3>运营数据</h3>
 * <ul>
 *   <li>{@link #operationParams} — 运营参数（换乘时间、停站时间、进站时间等）</li>
 *   <li>{@link #stations} — 所有站点数组，每项自带站名与无障碍设施（v2 起为数组结构）</li>
 *   <li>{@link #lines} — 所有线路集合（线路ID → Line）</li>
 *   <li>{@link #transfers} — 换乘规则数组（v3 起为根级数组，替代旧版站点内联换乘）</li>
 * </ul>
 *
 * <h3>票价数据</h3>
 * <ul>
 *   <li>{@link #price} — 票价体系（阶梯区间、线性扩展）</li>
 * </ul>
 *
 * <h3>运行规则</h3>
 * <ul>
 *   <li>{@link #speedLimits} — 限速规则列表（MetroLang 尚未输出，保留字段以便扩展）</li>
 *   <li>{@link #headwayRules} — 行车间隔规则列表（MetroLang 尚未输出，保留字段以便扩展）</li>
 *   <li>{@link #crowdRules} — 拥挤度规则列表（MetroLang 尚未输出，保留字段以便扩展）</li>
 *   <li>{@link #openTimes} — 运营时间规则列表</li>
 * </ul>
 *
 * <h3>附加数据</h3>
 * <ul>
 *   <li>{@link #virtualTransfers} — 虚拟换乘列表（MetroLang 尚未输出，保留字段以便扩展）</li>
 * </ul>
 *
 * @see OperationParams
 * @see Station
 * @see Line
 * @see TransferRule
 * @see Price
 */
public class MetroData {
    /** 主要城市名称，如 "北京"、"上海" */
    @SerializedName("key_city")
    public String keyCity;
    /** 数据作者名称 */
    public String author;
    /** 数据版本号 */
    public String version;
    /** 数据创建日期 */
    @SerializedName("create_date")
    public String createDate;
    /** 数据最后更新日期 */
    @SerializedName("update_date")
    public String updateDate;
    /** 覆盖的其他城市名称列表（JSON: "other_cities"） */
    @SerializedName("other_cities")
    public List<String> otherCities;
    /** 运营参数（换乘时间、停站时间、进站时间等） */
    @SerializedName("operation_params")
    public OperationParams operationParams;
    /** 票价体系（包含阶梯区间、线性扩展） */
    public Price price;
    /** 换乘类型定义（id / name / time / status / security_check） */
    @SerializedName("transfer_types")
    public List<TransferType> transferTypes;
    /** 所有站点数组，每项自带 name 身份字段（v3：数组结构） */
    public Map<String, Station> stations;
    /** 所有线路集合，键为线路ID，值为线路详细信息（含方向与站点） */
    public Map<String, Line> lines;
    /** 换乘规则数组（v3：根级数组，站名 + 双向线路 + 类型 + 时间） */
    public List<TransferRule> transfers;
    /** 运营时间规则列表 */
    @SerializedName("open_times")
    public List<OpenTimeRule> openTimes;
    /** 行车间隔规则（v3：按线路聚合的 periods 嵌套结构） */
    @SerializedName("headway_rules")
    public List<HeadwayRule> headwayRules;
    /** 拥挤度规则（v3：按方向聚合的 periods 嵌套结构） */
    @SerializedName("crowd_rules")
    public List<CrowdRule> crowdRules;
    /** 停站时间规则（v3：按线路或按站点声明） */
    @SerializedName("dwell_rules")
    public List<DwellRule> dwellRules;
    /** 特殊线路票价规则（v3：根级数组，如机场线统一票价） */
    @SerializedName("special_line_rules")
    public List<SpecialLineRule> specialLineRules;
    /** 限速规则列表（MetroLang 暂未输出，保留字段） */
    @SerializedName("speed_limits")
    public List<SpeedLimit> speedLimits;
    /** 虚拟换乘列表（MetroLang 暂未输出，保留字段） */
    @SerializedName("virtual_transfers")
    public List<VirtualTransfer> virtualTransfers;
}
