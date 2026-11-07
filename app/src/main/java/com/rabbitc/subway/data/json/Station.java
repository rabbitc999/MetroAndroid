package com.rabbitc.subway.data.json;

import java.util.List;
import java.util.Map;

/**
 * 地铁站点数据模型（v3：MetroLang JSON 数组条目），表示一个地铁车站的完整信息。
 *
 * <p>站点自带 {@link #name} 身份字段，同名车站允许跨城市共存；
 * 无障碍设施（{@link #accessibility}）直接内联在站点条目上，
 * 不再依赖旧版根级 facilities 字典。</p>
 */
public class Station {

    /** 站点名称（身份字段，数组不按站名建索引） */
    public String name;

    /** 站点经度（WGS-84 坐标系） */
    public double lon;

    /** 站点纬度（WGS-84 坐标系） */
    public double lat;

    /** 站点所属城市名称 */
    public String city;

    /** 多语言别名列表（MetroLang OTHER_NAME / OTHER_NAMES） */
    public List<String> otherNames;

    /** 该站点途经的线路方向摘要列表 */
    public List<StationLine> lines;

    /** 内联的无障碍设施信息；站点没有任何设施时该字段不存在 */
    public StationFacilities accessibility;

    public Map<String, Object> transfers;
}
