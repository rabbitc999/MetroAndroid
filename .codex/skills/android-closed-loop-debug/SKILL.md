---
name: android-closed-loop-debug
description: 使用 MCP + ADB + Gradle 对 Android 应用进行闭环调试，覆盖构建安装、真实 UI 操作、截图与 hierarchy 检查、日志诊断、失败取证和回归流程。
metadata:
  short-description: Android UI and runtime closed-loop debugging
---

# Android 闭环调试

当用户要求调试 Android 应用、复现 UI 问题、验证交互或分析崩溃时使用本 Skill。优先通过 `android_debug_mcp` 工具完成观察和操作，避免凭代码猜测 UI 状态。

## 工作流

1. 确认项目根目录、包名、构建变体和可用设备。先调用 `android_devices`。
2. 构建并安装目标变体。默认使用 debug，除非用户指定其他变体。
3. 启动应用，获取截图和 `android_ui_dump`。定位控件优先使用 resource id、可见文本或 content description。
4. 按用户场景逐步操作。每个关键动作后重新观察 UI 或截图，不连续假设状态已经改变。
5. 同时采集 logcat。若出现崩溃、ANR、空白页或交互无响应，保留截图、UI XML 与日志路径。
6. 根据证据修改代码。修改后重新构建、安装并重复最小复现流程。
7. 用 `android_test_flow` 跑成功路径和回归路径，返回通过步骤、失败步骤和产物。

## 证据要求

- UI 问题至少提供失败截图和 UI hierarchy。
- 崩溃问题至少提供 logcat 中的异常线索和复现步骤。
- 修复结论必须来自修改后的重新运行结果，而不是只依据编译成功。
- 设备不可用时明确说明阻塞点，仍可完成静态检查和单元测试，但不要声称完成了真实 UI 验证。

## 流程格式

`android_test_flow` 的步骤示例：

```json
[
  {"action": "tap", "resource_id": "com.example:id/start"},
  {"action": "input_text", "text": "北京"},
  {"action": "press", "key": "ENTER"},
  {"action": "assert_text", "text": "搜索结果"},
  {"action": "screenshot"}
]
```

详细 MCP 工具和环境配置见 [tools/android_debug_mcp/README.md](../../../tools/android_debug_mcp/README.md)。
