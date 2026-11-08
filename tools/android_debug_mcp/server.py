"""Closed-loop Android debugging MCP server."""
from __future__ import annotations

import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any

from mcp.server.fastmcp import FastMCP

ROOT = Path(os.environ.get("ANDROID_PROJECT_ROOT", Path(__file__).resolve().parents[2])).resolve()
ARTIFACTS = ROOT / ".android-debug-artifacts"
ARTIFACTS.mkdir(exist_ok=True)
mcp = FastMCP("android-closed-loop-debug")


def run(cmd: list[str], timeout: int = 120, check: bool = True) -> dict[str, Any]:
    proc = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True, timeout=timeout)
    result = {"command": cmd, "returncode": proc.returncode, "stdout": proc.stdout[-20000:], "stderr": proc.stderr[-20000:]}
    if check and proc.returncode:
        raise RuntimeError(result)
    return result


def adb(serial: str | None, *args: str, timeout: int = 60, check: bool = True) -> dict[str, Any]:
    cmd = ["adb"]
    if serial:
        cmd += ["-s", serial]
    return run(cmd + list(args), timeout=timeout, check=check)


def choose_serial(serial: str | None) -> str:
    if serial:
        return serial
    out = adb(None, "devices", check=True)["stdout"]
    devices = [line.split("\\t")[0] for line in out.splitlines()[1:] if "\\tdevice" in line]
    if not devices:
        raise RuntimeError("没有发现处于 device 状态的 Android 设备或模拟器")
    return devices[0]


def stamp() -> str:
    return time.strftime("%Y%m%d-%H%M%S")


def save(serial: str, name: str, content: str | bytes) -> str:
    folder = ARTIFACTS / stamp()
    folder.mkdir(parents=True, exist_ok=True)
    path = folder / name
    path.write_bytes(content if isinstance(content, bytes) else content.encode("utf-8"))
    return str(path)


@mcp.tool()
def android_devices() -> dict[str, Any]:
    """列出 ADB 设备及其状态。"""
    return adb(None, "devices", "-l")


@mcp.tool()
def android_build(variant: str = "debug", module: str = "app") -> dict[str, Any]:
    """构建 Android APK，并返回 APK 候选路径。"""
    task = f":{module}:assemble{variant[:1].upper() + variant[1:]}"
    result = run([".\\gradlew.bat", task], timeout=600)
    apks = [str(p) for p in (ROOT / module / "build" / "outputs" / "apk").rglob("*.apk")]
    result["apks"] = apks
    return result


@mcp.tool()
def android_install(apk: str, serial: str | None = None, grant_runtime_permissions: bool = True) -> dict[str, Any]:
    """安装 APK。apk 必须位于项目目录内。"""
    path = (ROOT / apk).resolve() if not Path(apk).is_absolute() else Path(apk).resolve()
    if ROOT not in path.parents and path != ROOT:
        raise ValueError("为避免误操作，APK 路径必须位于项目目录内")
    args = ["install", "-r"] + (["-g"] if grant_runtime_permissions else []) + [str(path)]
    return adb(choose_serial(serial), *args, timeout=180)


@mcp.tool()
def android_launch(package_name: str, activity: str | None = None, serial: str | None = None) -> dict[str, Any]:
    """启动应用并返回 am start 输出。activity 可省略以使用 monkey。"""
    device = choose_serial(serial)
    if activity:
        return adb(device, "shell", "am", "start", "-W", "-n", f"{package_name}/{activity}")
    return adb(device, "shell", "monkey", "-p", package_name, "1")


@mcp.tool()
def android_screenshot(serial: str | None = None) -> dict[str, Any]:
    """抓取当前屏幕截图并保存为 PNG。"""
    device = choose_serial(serial)
    proc = subprocess.run(["adb", "-s", device, "exec-out", "screencap", "-p"], cwd=ROOT, capture_output=True, timeout=30)
    if proc.returncode:
        raise RuntimeError(proc.stderr.decode(errors="replace"))
    return {"serial": device, "path": save(device, "screen.png", proc.stdout)}


@mcp.tool()
def android_ui_dump(serial: str | None = None) -> dict[str, Any]:
    """导出当前窗口 UI hierarchy，并返回可定位节点摘要。"""
    device = choose_serial(serial)
    raw = adb(device, "exec-out", "uiautomator", "dump", "/dev/tty", timeout=30)["stdout"]
    path = save(device, "window.xml", raw)
    nodes = []
    for node in ET.fromstring(raw[raw.find("<hierarchy"):]):
        pass
    for match in re.finditer(r'<node[^>]+>', raw):
        tag = match.group(0)
        attrs = dict(re.findall(r'(resource-id|text|content-desc|class|bounds)="([^"]*)"', tag))
        if any(attrs.get(k) for k in ("resource-id", "text", "content-desc")):
            nodes.append(attrs)
    return {"serial": device, "path": path, "nodes": nodes[:300]}


def target_bounds(serial: str, resource_id: str | None, text: str | None, content_desc: str | None) -> tuple[int, int]:
    raw = adb(serial, "exec-out", "uiautomator", "dump", "/dev/tty", timeout=30)["stdout"]
    for match in re.finditer(r'<node[^>]+>', raw):
        tag = match.group(0)
        attrs = dict(re.findall(r'(resource-id|text|content-desc|bounds)="([^"]*)"', tag))
        if resource_id and attrs.get("resource-id") != resource_id: continue
        if text and attrs.get("text") != text: continue
        if content_desc and attrs.get("content-desc") != content_desc: continue
        b = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", attrs.get("bounds", ""))
        if b:
            return (int(b.group(1)) + int(b.group(3))) // 2, (int(b.group(2)) + int(b.group(4))) // 2
    raise ValueError("UI 中没有找到匹配节点")


@mcp.tool()
def android_tap(x: int | None = None, y: int | None = None, resource_id: str | None = None, text: str | None = None, content_desc: str | None = None, serial: str | None = None) -> dict[str, Any]:
    """点击坐标或按 UI 属性定位的节点。"""
    device = choose_serial(serial)
    if resource_id or text or content_desc:
        x, y = target_bounds(device, resource_id, text, content_desc)
    if x is None or y is None: raise ValueError("必须提供 x/y 或 resource_id/text/content_desc")
    return adb(device, "shell", "input", "tap", str(x), str(y))


@mcp.tool()
def android_input_text(text: str, serial: str | None = None) -> dict[str, Any]:
    """向当前焦点输入文本。"""
    return adb(choose_serial(serial), "shell", "input", "text", text.replace(" ", "%s"))


@mcp.tool()
def android_press(key: str = "BACK", serial: str | None = None) -> dict[str, Any]:
    """发送 Android 按键，例如 BACK、HOME、ENTER。"""
    return adb(choose_serial(serial), "shell", "input", "keyevent", key)


@mcp.tool()
def android_logcat(serial: str | None = None, clear_first: bool = False, duration_seconds: int = 3, package_name: str | None = None) -> dict[str, Any]:
    """采集 logcat，并提取 FATAL EXCEPTION、ANR 和 native crash 线索。"""
    device = choose_serial(serial)
    if clear_first: adb(device, "logcat", "-c")
    time.sleep(max(0, duration_seconds))
    args = ["logcat", "-d", "-v", "threadtime"]
    if package_name: args += ["-e", package_name]
    raw = adb(device, *args, timeout=60)["stdout"]
    path = save(device, "logcat.txt", raw)
    patterns = [line for line in raw.splitlines() if re.search(r"FATAL EXCEPTION|ANR in |signal (\\d+)|AndroidRuntime", line)]
    return {"serial": device, "path": path, "crash_signals": patterns[-100:], "log": raw[-20000:]}


@mcp.tool()
def android_test_flow(package_name: str, steps: list[dict[str, Any]], serial: str | None = None, activity: str | None = None) -> dict[str, Any]:
    """执行声明式 UI 流程。失败时自动保存截图、UI XML 和日志。"""
    device = choose_serial(serial)
    android_launch(package_name, activity, device)
    completed = []
    try:
        for step in steps:
            action = step.get("action")
            if action == "tap": android_tap(serial=device, **{k: step[k] for k in ("x", "y", "resource_id", "text", "content_desc") if k in step})
            elif action == "input_text": android_input_text(step["text"], device)
            elif action == "press": android_press(step.get("key", "BACK"), device)
            elif action == "sleep": time.sleep(float(step.get("seconds", 1)))
            elif action == "screenshot": android_screenshot(device)
            elif action == "assert_text":
                ui = android_ui_dump(device)
                if not any(n.get("text") == step["text"] for n in ui["nodes"]): raise AssertionError(f"未找到文本: {step['text']}")
            else: raise ValueError(f"未知 action: {action}")
            completed.append(step)
        return {"ok": True, "completed": completed}
    except Exception as exc:
        shot = android_screenshot(device)
        ui = android_ui_dump(device)
        logs = android_logcat(device, duration_seconds=0)
        return {"ok": False, "error": str(exc), "completed": completed, "screenshot": shot, "ui": ui, "logs": logs}


@mcp.tool()
def android_collect_artifacts() -> dict[str, Any]:
    """列出最近的调试产物。"""
    return {"root": str(ARTIFACTS), "files": [str(p) for p in sorted(ARTIFACTS.rglob("*")) if p.is_file()][-200:]}


if __name__ == "__main__":
    mcp.run()
