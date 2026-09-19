# Logdog 悬浮日志库设计

日期：2026-09-18  
状态：已定稿（待实现）

## 目标

提供一个 Android Library：宿主在 `Application` 中初始化后，应用内出现可拖动的悬浮日志面板（约占屏幕 1/4）；可缩小为悬浮图标；对外仅提供初始化与写日志入口；日志实时刷新。

## 约束（已确认）

- 仅本应用内悬浮，不使用 `SYSTEM_ALERT_WINDOW`
- UI：传统 View + XML（不引入 Compose）
- 对外 API 只含 `init` 与 `log`；初始化即展示，未初始化不展示
- 未初始化时 `log` 仍写入内存缓存；`init` 后把已有缓存刷到面板

## 模块结构

| Module | 类型 | 职责 |
|--------|------|------|
| `:logdog` | Android Library | 悬浮日志核心 |
| `:app` | Application | Demo：初始化并演示写日志 |

`minSdk` 与宿主一致（当前工程为 24）。

## 架构

```
任意线程 Logdog.log()
        │
        ▼
   LogBuffer（环形，上限 500）
        │  观察者回调（切主线程）
        ▼
FloatingLogController
        │  ActivityLifecycleCallbacks
        ▼
当前 Activity DecorView 上的 FloatingLogView
（展开面板 / 缩小图标）
```

### 组件职责

1. **`Logdog`（门面）**  
   唯一对外入口：`init(Application)`、`log(...)`。

2. **`LogBuffer`**  
   线程安全内存环形缓冲；条目含时间戳、可选 tag、message；超出 500 丢最旧；支持观察者订阅增量/快照。

3. **`FloatingLogController`**  
   - `init` 时注册 `ActivityLifecycleCallbacks`  
   - `onActivityResumed`：将悬浮层挂到该 Activity 的 `DecorView`  
   - `onActivityPaused`：从该 Activity 卸下（避免泄漏）  
   - 在 Controller 内存中保留：展开/缩小状态、面板位置、与 `LogBuffer` 的订阅  
   - 重复 `init`：忽略

4. **`FloatingLogView`**  
   展开态与缩小态同一自定义 View（或容器切换）：列表展示日志、拖动、缩小/展开。

## 对外 API

```kotlin
object Logdog {
    fun init(app: Application)

    fun log(message: String)
    fun log(tag: String, message: String)
}
```

### 行为约定

| 场景 | 行为 |
|------|------|
| 已 `init` | 展示悬浮层；`log` 入缓冲并实时刷新 UI |
| 未 `init` | 不展示；`log` 只入缓冲，不崩溃 |
| `init` 时缓冲非空 | 将已有条目一次性展示到面板 |
| 重复 `init` | 忽略，不重复注册/挂层 |
| 任意线程 `log` | 允许；UI 更新切主线程 |

不对外暴露 `show` / `hide` / `clear`。

## UI 行为

### 展开态

- 默认约占屏幕面积 1/4（宽约屏宽一半、高约屏高一半，或等价面积），默认落在右下角安全区内
- 半透明深色背景 + 可滚动日志列表；新日志自动滚到底
- 每行展示：时间 + 可选 tag + message
- 标题栏可拖动整块面板；标题栏提供「缩小」控件

### 缩小态

- 圆形或圆角悬浮图标，可自由拖动
- 点击图标恢复展开态

### 拖动与边界

- 展开、缩小均可拖动
- 松手后夹紧到屏幕可见安全区内，不飞出边界

### 跨 Activity

- 切页时卸载再挂到新 Activity 的 `DecorView`
- 位置与展开/缩小状态由 Controller 保持，日志内容来自同一 `LogBuffer`

## 错误处理

- Activity 销毁/重建：paused 卸层、resumed 重挂；缓冲与 UI 状态不丢（进程内）
- 进程被杀：内存缓冲清空（不做持久化，MVP 范围外）
- 挂载失败（极端 DecorView 不可用）：不影响宿主；日志仍入缓冲

## 测试（MVP）

- **单元**：未 init 写入 → init 后内容可见；环形缓冲超量丢最旧
- **Demo**：自定义 `Application` 中 `Logdog.init(this)`；主界面按钮连续 `log`；验证拖动、缩小/展开；可选第二 Activity 验证跨页保持

## 明确不做（YAGNI）

- 系统级悬浮窗权限
- 日志级别、筛选、持久化、导出
- Compose UI
- 对外的 show/hide/clear API

## 集成示例（Demo）

```kotlin
class DemoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Logdog.init(this)
    }
}

// 任意处
Logdog.log("hello")
Logdog.log("Network", "request ok")
```
