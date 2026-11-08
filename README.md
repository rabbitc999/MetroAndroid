# Metro View

Metro View 是一个 Android 地铁线路查看与路径规划应用，支持多城市地图、线路图、站点详情、路线规划、收藏路线、历史行程、站点打卡和设备同步。

## 主要功能

- 多城市地铁地图浏览
- 起点到终点的路线规划
- 最短距离、最短时间、最少换乘、最不拥挤、随机路线和自定义权重
- 线路图例高亮与长按线路详情
- 站点列表、换乘线路、运营参数和线路示意图
- 收藏路线、历史行程和通勤距离计算
- 站点打卡、照片记录和任务链
- TCP/UDP 设备同步
- 城市信息、票价规则和运营公司展示

## 地图数据

内置地图位于 [app/src/main/assets/map](app/src/main/assets/map)，当前统一使用 UTF-8 JSON 文件：

- 北京地铁.json
- 上海地铁.json
- 广州地铁.json
- 深圳地铁.json
- 成都地铁.json
- 杭州地铁.json
- 合肥地铁.json
- 南京地铁.json
- 武汉地铁.json
- 西安地铁.json

应用启动时从 `assets/map` 读取 JSON 地图，并解析站点、线路、换乘、票价、运营时间、发车间隔、拥挤度和运营公司信息。

地图根对象常用字段包括：

- `key_city`
- `stations`
- `lines`
- `transfers`
- `price`
- `open_times`
- `headway_rules`
- `crowd_rules`
- `operator_companies`

缺失的运营规则会在界面显示“暂无数据”，不会用虚构数据代替真实运营信息。

## 路线规划

路线搜索基于 `MetroGraph`。路径节点使用线路方向和站点组合表示，换乘次数按线路 ID 变化计算。同一条线路切换正反方向不计为换乘。

路线规划会处理：

- 线路方向和环线
- 未来线路与暂缓开通线路
- 站内换乘和出站换乘
- 线路距离、时间、站点数量和拥挤度权重
- 票价和特殊线路计费

## 构建

项目使用 Gradle、Java 和 Kotlin，Android 8.0 及以上版本可运行。

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

生成的 APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 安装到 ADB 设备

```powershell
adb devices
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.rabbitc.subway 1
```

## Android 闭环调试

项目提供 [Android 闭环调试 MCP](tools/android_debug_mcp/README.md) 和全局 Skill `android-closed-loop-debug`，可执行：

- Gradle 构建
- APK 安装和启动
- 截图与 UI hierarchy 导出
- 控件点击、文本输入和按键操作
- logcat 采集
- 声明式 UI 流程回归
- 失败时自动保留截图、UI XML 和日志

安装 MCP 依赖：

```powershell
python -m pip install -r tools/android_debug_mcp/requirements.txt
python tools/android_debug_mcp/server.py
```

## 项目结构

```text
app/src/main/java/com/rabbitc/subway/
├── activity/     Android 页面和页面流程
├── algroithm/    MetroGraph、票价和时间算法
├── data/         缓存、解析器和运行时模型
├── adapter/      列表和图例适配器
├── utils/        路线、站点、地图和数据工具
└── view/         地铁地图和线路视图
```

## 许可证

本项目使用 Apache License 2.0，详见 [LICENSE](LICENSE)。
