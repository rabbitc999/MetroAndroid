# Android Closed-Loop Debug MCP

这个目录提供一个面向本仓库的 Android 闭环调试 MCP 服务。它把 Gradle、ADB、UI hierarchy、截图和 logcat 统一暴露给 MCP 客户端，适用于真实模拟器或 USB 设备。

## 运行

```powershell
python -m pip install -r tools/android_debug_mcp/requirements.txt
python tools/android_debug_mcp/server.py
```

MCP 客户端配置示例：

```json
{
  "mcpServers": {
    "android-closed-loop-debug": {
      "command": "python",
      "args": ["D:/Desktop/Workspace/Android/tools/android_debug_mcp/server.py"]
    }
  }
}
```

服务默认工作目录是项目根目录，也可以通过 `ANDROID_PROJECT_ROOT` 指定。所有命令都使用参数数组执行，不经过 shell。

## 闭环能力

- `android_devices` 发现设备并读取状态
- `android_build` 构建 APK
- `android_install` 安装 APK
- `android_launch` 启动 Activity
- `android_screenshot` 截图并返回本地文件路径
- `android_ui_dump` 导出 UI hierarchy XML
- `android_tap`、`android_input_text`、`android_press` 操作界面
- `android_logcat` 采集日志并标记崩溃线索
- `android_test_flow` 执行声明式 UI 流程并在失败时保留截图、UI XML、日志
- `android_collect_artifacts` 汇总最近一次调试产物

UI 定位优先使用 `resource_id`、`text` 或 `content_desc`，只有在确实无法定位时才使用坐标。
