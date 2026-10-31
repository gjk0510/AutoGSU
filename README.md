# AutoGSU

> 一个用 Jetpack Compose 写的 Android 定时 Root 命令工具：把常用的 `su -c "..."` 命令做成卡片，按设定间隔在后台自动执行，并记录每次执行日志。

AutoGSU 面向 **已 Root（如 Magisk）** 的设备，解决「定时跑一条 root 命令」这类重复劳动：例如定时备份、定时清理、定时采集信息、定时切换配置等。所有任务在本地存储与执行，**不联网、不上传任何数据**。

---

## ✨ 功能特性

### 多任务卡片管理
- 主页以卡片列表展示所有任务，一眼看到 **名称 / 命令 / 间隔 / 闹钟模式 / 下次执行时间 / 最近一条日志**。
- 右下角 `+` 一键新建任务，点击卡片进入详情编辑。
- 删除采用 **三步确认**（删除 → 确认删除 → 再点一次删除），避免误删。
- 点击空白处可一键取消删除确认状态。

### 灵活的任务配置
每个任务可独立设置：
- **名称**：自定义任务标题。
- **Shell 命令**：通过 `su -c` 以 root 权限执行。
- **间隔时间**：按时 / 分 / 秒设置执行周期（默认 1 小时）。
- **闹钟模式**：四档可选，兼顾精度与省电。

### 四种闹钟模式

| 模式 | 系统 API | 说明 |
| --- | --- | --- |
| **关闭** | — | 取消调度，任务不自动执行 |
| **非精确** | `setAndAllowWhileIdle` | Doze 下仍可触发（受系统频率限制），**无需额外权限**，默认推荐 |
| **精确** | `setExactAndAllowWhileIdle` | 尽量准时触发；Android 12+ 需「闹钟和提醒」权限，**无权限自动回退为非精确** |
| **闹钟** | `setAlarmClock` | 以系统闹钟方式调度，优先级最高，会显示闹钟图标 |

> 全部基于 `ELAPSED_REALTIME_WAKEUP`，在设备睡眠时可被唤醒执行。

### 可靠的执行保障
- **Root 执行**：通过 `ProcessBuilder("su", "-c", command)` 执行，命令超时上限 **30 秒**，超时强制结束并记录。
- **独立读取线程**：异步读取进程输出，避免输出缓冲区写满导致进程阻塞。
- **WakeLock 保护**：执行期间持有 `PARTIAL_WAKE_LOCK` 保证 CPU 唤醒，并设置硬超时自动释放，不长期占用。
- **任务链不中断**：无论单次执行成功、失败还是超时，都会自动注册该任务的下一次调度。
- **开机自恢复**：监听 `BOOT_COMPLETED` / `QUICKBOOT_POWERON`，重启后无条件重建全部定时任务。
- **启动兜底**：App 启动时检查任务的下次执行时间，仅在「未注册或已过期」时补注册，避免每次打开都重置计时。

### 日志与调试
- **每个任务独立日志文件**，详情页默认展示最近 200 行并自动滚动到底部。
- 支持 **导出日志**（通过系统分享）与 **清除日志**。
- 巨型日志自动瘦身：单文件超过 **256 KB** 时保留末尾一半。
- 日志同时输出到 Logcat（`TAG = AutoSU`），便于调试。
- 关键节点均有记录（收到广播 / 开始处理 / 执行结果 / 输出 / 超时 / 权限回退等），可清晰区分「广播未送达」与「送达但执行失败」。

### 流畅的交互动画
- 采用 **Navigation Compose** 管理页面（参考 SimpleHook / Now in Android 的架构），页面转场在路由上统一声明：
  - 主页 ↔ 详情页使用 **淡入淡出 + 轻微上/下滑**，全程只做 alpha 与位移合成，跟手不卡顿。
  - 转场结束后被覆盖的页面会移出组合树，避免整页常驻互相拖累。
- 磁盘 IO（任务列表、日志、下次执行时间）全部放到后台线程加载，**不在组合期间读磁盘**，杜绝掉帧根源。
- 日志文本在转场动画结束后再填充并排版，避免大段文本重排撞在动画中途。

---

## 📱 环境要求

| 项目 | 要求 |
| --- | --- |
| 系统版本 | Android 8.0 (API 26) 及以上 |
| 目标版本 | Android 16 (targetSdk 36) |
| Root 权限 | **必须**（Magisk 等，`su` 已加入 `PATH`） |
| 构建工具 | Android Studio、JDK 11、Gradle（版本目录统一管理） |

**权限说明**（仅用于「定时执行」功能本身）：

- `RECEIVE_BOOT_COMPLETED`：开机后恢复定时任务。
- `WAKE_LOCK`：执行期间短暂保持 CPU 唤醒（有硬超时，及时释放）。
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`：引导加入电池优化白名单，保证 Doze/后台能唤醒。
- `SCHEDULE_EXACT_ALARM`：精确闹钟（Android 12+ 需在系统设置授予「闹钟和提醒」）。

> 建议把本应用加入 **电池优化白名单** 并允许 **自启动**，以获得最稳定的后台调度。

---

## 🚀 快速开始

### 1. 安装
从 Releases 下载 APK 安装，或自行编译（见下）。首次使用请确认设备已 Root。

### 2. 创建并配置任务
1. 打开 App，点击右下角 `+` 新建任务。
2. 填写 **名称** 与 **Shell 命令**（如 `echo hello`、`date`）。
3. 设置 **间隔时间**（时 / 分 / 秒）。
4. 选择 **闹钟模式**（推荐「非精确」；需要准点则选「精确」并授予权限）。
5. 点击 **保存并应用**，卡片会显示「下次执行时间」。

### 3. 验证与调试
- 点击 **立即执行一次** 可马上运行并查看结果输出。
- 在详情页底部查看实时日志；出现异常时用 **导出日志** 排查。
- 若精确闹钟未生效，请到系统设置中为本应用开启 **「闹钟和提醒」** 权限。

---

## 🛠 构建

```bash
# 克隆
git clone https://github.com/gjk0510/AutoGSU.git
cd AutoGSU

# Debug 构建
./gradlew assembleDebug

# Release 构建
./gradlew assembleRelease
```

产物位于 `app/build/outputs/apk/` 下。

> 说明：当前 `release` 使用 debug 签名以便直接安装体验，正式发布请替换为自有签名配置。

---

## 🧱 项目结构

```
app/src/main/java/com/gjk0510/autosu/
├── MainActivity.kt        # 入口 Activity + 导航宿主 + 任务详情页
├── HomeScreen.kt          # 主页：任务卡片列表、新建、关于弹窗
├── Task.kt                # 任务数据模型与 TaskStore（JSON/SharedPreferences 持久化）
├── AlarmScheduler.kt      # 闹钟调度：注册/取消/查询/开机与启动兜底恢复
├── CommandExecutor.kt     # su -c 执行命令，带超时与输出收集
├── CommandReceiver.kt     # 闹钟广播接收：后台执行命令并续约下一次调度
├── BootReceiver.kt        # 开机广播接收：恢复全部定时任务
├── ExecutionLog.kt        # 日志系统：全局日志 + 每任务独立日志
├── Settings.kt            # 常量定义 + 旧版单命令配置迁移
└── ui/theme/              # Compose Material3 主题（颜色 / 排版 / 主题）
```

### 核心执行流程

```
AlarmManager 到点
      │
      ▼
CommandReceiver.onReceive  ──► goAsync() 切后台线程
      │                              │
      │                              ├─ 获取 WakeLock（硬超时保护）
      │                              ├─ CommandExecutor.execute(su -c ...)
      │                              ├─ 写入任务日志
      │                              └─ AlarmScheduler.schedule() 续约下一次
      ▼
    任务链持续运行（成功/失败/超时均续约）
```

---

## ⚠️ 注意事项

- 本应用需要 **Root 权限**，非 Root 设备无法执行命令。
- 请自行确保所执行的命令安全可靠；**作者不对命令执行造成的任何后果负责**。
- 国产 ROM 对后台管控较严，请务必 **允许自启动** 并 **关闭电池优化**，否则定时可能被系统拦截。

---

## 📬 反馈

有问题欢迎邮件联系：**gjk0510@hotmail.com**

---

## 📄 许可证

本项目基于 [GNU General Public License v3.0](LICENSE) 开源。

```
AutoGSU — Android 定时 Root 命令工具
Copyright (C) 2024 gjk0510

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```
