# LogDoy

Android 应用内悬浮日志库。初始化后显示可拖动的悬浮气泡；展开后可查看实时日志、调整窗口大小，并支持清空日志。

## 特性

- **应用内悬浮**：挂在当前 Activity 的 DecorView 上，无需系统悬浮窗权限
- **默认气泡**：启动后以气泡形式展示（无日志时也会显示），点击展开
- **实时刷新**：任意线程调用 `LogDoy.log`，面板自动更新
- **预初始化缓冲**：`init` 前写入的日志会先进入内存，初始化后一并展示
- **可拖动 / 可缩放**：展开态拖边框移动，拖四角把手调整大小
- **日志滚动**：面板内部列表可上下滑动
- **清空确认**：面板内清空按钮带二次确认

## 模块

| 模块 | 说明 |
|------|------|
| `:logdoy` | 悬浮日志 Android Library |
| `:app` | Demo 应用 |

## 快速接入

### 1. 依赖

将 `:logdoy` 作为模块依赖，或发布 AAR 后引入。

```kotlin
implementation(project(":logdoy"))
```

### 2. 初始化

在 `Application.onCreate` 中调用一次：

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LogDoy.init(this)
    }
}
```

并在 `AndroidManifest.xml` 的 `<application>` 上注册：

```xml
android:name=".MyApp"
```

### 3. 写日志

```kotlin
LogDoy.log("hello")
LogDoy.log("Network", "request ok")
```

## API

```kotlin
import com.uxnhe.logdog.LogDoy

object LogDoy {
    fun init(app: Application)
    fun log(message: String)
    fun log(tag: String, message: String)
}
```

Demo 应用包名：`com.uxnhe.logdog.demo`  
Library namespace：`com.uxnhe.logdog`

| 行为 | 说明 |
|------|------|
| 已 `init` | 显示悬浮层，日志实时刷到面板 |
| 未 `init` | 不展示 UI；`log` 只写入内存缓冲，不崩溃 |
| 重复 `init` | 忽略 |

## 要求

- minSdk 24+
- 传统 View / XML（无 Compose）
- 不申请 `SYSTEM_ALERT_WINDOW`

## Demo

运行 `:app` 模块：启动后右下角出现气泡，点击展开查看日志；主界面可写日志并进入第二页验证跨 Activity 保持。

## 许可证

本项目采用 [MIT License](LICENSE) 开源。
